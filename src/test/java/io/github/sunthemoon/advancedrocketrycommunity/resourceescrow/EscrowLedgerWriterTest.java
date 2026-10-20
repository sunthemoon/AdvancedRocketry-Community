package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class EscrowLedgerWriterTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    private Path file() { return directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); }

    @Test void aFailedGenerationDoesNotCancelAnAdmissionInTheCapturedSuccessor() throws Exception {
        var op = EscrowTestFixtures.operation(1); var base = EscrowTestFixtures.ledger(op);
        CountDownLatch captured = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger stages = new AtomicInteger(); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file(), 1, CheckedSavedDataFile::atomicMove, cut -> {
            if (cut.equals("K1") && stages.incrementAndGet() == 1) {
                captured.countDown(); await(release); throw new IllegalStateException("Injected definite pre-replace failure");
            }
        })) {
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of(op.id())), result -> {
                assertTrue(writer.containsCaptured(op.id()), "Failed predecessor cancelled a captured successor's admission"); outcomes.add(result);
            }));
            assertTrue(captured.await(5, TimeUnit.SECONDS)); AtomicInteger captures = new AtomicInteger();
            assertTrue(writer.request(() -> {
                captures.incrementAndGet(); return EscrowLedgerWriter.Snapshot.of(base.generation(3), Set.of(op.id()));
            }, outcomes::add));
            assertEquals(0, captures.get(), "Successor must be an uncaptured request"); release.countDown();
            poll(ledgerIo, writer, () -> writer.idle());
            assertEquals(1, captures.get()); assertEquals(2, outcomes.size());
            assertEquals(EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE, outcomes.get(0).outcome());
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, outcomes.get(1).outcome());
            assertEquals(3, CheckedSavedDataFile.readPayload(file(), ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow().getLong("writer_generation"));
        } finally { release.countDown(); }
    }
    @Test void unknownOutcomeRetainsTheExactCandidateAndBlocksDependentGenerations() throws Exception {
        var base = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)); AtomicInteger attempts = new AtomicInteger();
        var outcomes = new ArrayList<EscrowLedgerWriter.Completion>(); AtomicInteger successorCaptures = new AtomicInteger();
        var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file(), 0, (staged, target) -> {
            if (attempts.incrementAndGet() <= 3) { throw new IOException("Unknown replacement outcome"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        })) {
            writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(1), Set.of()), outcomes::add);
            writer.request(() -> {
                successorCaptures.incrementAndGet(); return EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of());
            }, outcomes::add);
            ledgerIo.tickUntil(tick -> {
                writer.tick(tick);
                if (outcomes.size() == 3) { assertTrue(writer.unavailable()); assertEquals(0, successorCaptures.get()); }
            }, 0, writer::idle);
            assertTrue(writer.idle()); assertFalse(writer.unavailable()); assertEquals(1, successorCaptures.get());
            assertEquals(List.of(1L, 1L, 1L, 1L, 2L), outcomes.stream().map(EscrowLedgerWriter.Completion::generation).toList());
            assertEquals(1, outcomes.stream().filter(c -> c.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN).map(EscrowLedgerWriter.Completion::digest).distinct().count());
        }
    }
    @Test void replacedDespiteErrorIsAcknowledgedByExactReadbackAndShutdownDrainsOnlyWriterCallbacks() {
        var base = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        var writer = new EscrowLedgerWriter(file(), 0, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("Move completed before error");
        });
        writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(1), Set.of()), outcomes::add);
        writer.close(); assertEquals(1, outcomes.size()); assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, outcomes.get(0).outcome());
        assertFalse(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of()), outcomes::add));
    }
    @Test void autosaveNeverPublishesQueuedAdmissionsAndTheFirstContainingAcknowledgementIsL1() throws Exception {
        var data = ResourceEscrowSavedData.create(); var op = EscrowTestFixtures.operation(1); var replies = new ArrayList<Boolean>();
        var ledgerIo = new AwaitedLedgerIo();
        var writer = ledgerIo.writer(file(), 0, CheckedSavedDataFile::atomicMove); var coordinator = new EscrowCoordinator(data, writer);
        try {
            coordinator.prepareTest(op, replies::add); coordinator.autosave();
            poll(ledgerIo, writer, writer::idle); assertTrue(data.ledger().records().isEmpty()); assertTrue(replies.isEmpty()); assertTrue(data.isDirty());
            coordinator.tick(10); coordinator.commitPreparations(); poll(ledgerIo, writer, writer::idle);
            assertEquals(List.of(true), replies); assertEquals(List.of(op), data.ledger().records()); assertFalse(data.isDirty());
        } finally { coordinator.close(); }
    }
    @Test void immutableSnapshotAndPerTypeJournalQuotaArePreserved() {
        var snapshot = EscrowLedgerWriter.Snapshot.of(EscrowTestFixtures.ledger(), Set.of());
        snapshot.payload().putLong("writer_generation", 999); assertEquals(0, snapshot.payload().getLong("writer_generation"));
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
        assertEquals(ManagedSavedDataType.ROCKET_TRANSFERS.maxUncompressedBytes(), policy.maxUncompressedBytes());
        assertTrue(policy.maxUncompressedBytes() >= 140L * 1_024 * 1_024);
        assertThrows(IllegalArgumentException.class, () -> EscrowLedgerWriter.Snapshot.of(
                EscrowTestFixtures.ledger(), Set.of(EscrowTestFixtures.id(999))));
    }
    @Test void periodicReasonPersistenceCannotStarveAnExplicitPreparation() throws Exception {
        var data = ResourceEscrowSavedData.create(); var op = EscrowTestFixtures.operation(1); var replies = new ArrayList<Boolean>();
        var ledgerIo = new AwaitedLedgerIo(); var writer = ledgerIo.writer(file(), 0, CheckedSavedDataFile::atomicMove);
        try (var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.prepareTest(op, replies::add);
            ledgerIo.tickUntil(tick -> {
                coordinator.tick(tick); coordinator.persistTransitions(); coordinator.commitPreparations();
            }, 0, () -> !replies.isEmpty());
            assertEquals(List.of(true), replies); assertEquals(List.of(op), coordinator.ledger().records());
        }
    }
    @Test void delayedSuccessorCannotBurstTwoActualLedgerStartsInsideTenTicks() throws Exception {
        var base = EscrowTestFixtures.ledger(); CountDownLatch staged = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger stages = new AtomicInteger();
        var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file(), 0, (pending, target) -> {
            if (stages.incrementAndGet() == 1) { staged.countDown(); await(release); }
            CheckedSavedDataFile.atomicMove(pending, target);
        })) {
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(1), Set.of()), result -> { }));
            assertTrue(staged.await(5, TimeUnit.SECONDS)); writer.tick(10);
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of()), result -> { }));
            writer.tick(100); release.countDown();
            // The owner clock stays at 100, then 101, as before; only the wait for each write changed.
            ledgerIo.tickUntil(ignored -> writer.tick(100), 0, () -> writer.lastGeneration() >= 2);
            assertEquals(100, writer.lastIoStart());
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(3), Set.of()), result -> { }));
            ledgerIo.tickUntil(ignored -> writer.tick(101), 0, () -> writer.lastGeneration() >= 3);
            assertEquals(3, writer.lastGeneration()); assertEquals(100, writer.lastIoStart());
            writer.tick(109); assertEquals(100, writer.lastIoStart()); writer.tick(110); assertEquals(110, writer.lastIoStart());
        } finally { release.countDown(); }
    }
    @Test void anInvalidatedProofAtTheStagingBoundaryKeepsThePreviousLedger() throws Exception {
        var base = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW);
        policy.replace(file(), () -> EscrowCodec.encode(base));
        AtomicBoolean valid = new AtomicBoolean(true); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file(), 1, CheckedSavedDataFile::atomicMove, cut -> {
            if (cut.equals("K8")) { valid.set(false); }
        })) {
            writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.retire(Set.of(base.records().get(0).id())).generation(2), Set.of()),
                    outcomes::add, valid::get);
            poll(ledgerIo, writer, writer::idle);
            // R17 item 4: the guard's own refusal before any replacement attempt is not an IO failure.
            assertEquals(EscrowLedgerWriter.Outcome.GUARD_REFUSED, outcomes.get(0).outcome());
            assertFalse(writer.unavailable());
            assertEquals(base, EscrowCodec.decode(policy.read(file()).orElseThrow()));
        }
    }
    /** R17 item 4: guard refusals never make the writer unavailable; definite pre-replacement IO failures still do. */
    @Test @Timeout(60) void threeConsecutiveGuardRefusalsLeaveTheWriterAvailable() throws Exception {
        var base = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW);
        policy.replace(file(), () -> EscrowCodec.encode(base));
        var outcomes = new ArrayList<EscrowLedgerWriter.Completion>(); AtomicBoolean ioFails = new AtomicBoolean();
        AtomicInteger moves = new AtomicInteger(); var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file(), 1, (staged, target) -> {
            moves.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, target);
        }, cut -> { if (ioFails.get() && cut.equals("K1")) { throw new IllegalStateException("Injected definite pre-replace failure"); } })) {
            for (long generation = 2; generation <= 4; generation++) {
                var snapshot = EscrowLedgerWriter.Snapshot.of(base.generation(generation), Set.of());
                assertTrue(writer.request(() -> snapshot, outcomes::add, () -> false));
                poll(ledgerIo, writer, writer::idle);
                assertEquals(EscrowLedgerWriter.Outcome.GUARD_REFUSED, outcomes.get(outcomes.size() - 1).outcome());
                assertFalse(writer.unavailable(), "Guard refusal " + (generation - 1) + " counted as a writer failure");
            }
            assertEquals(0, moves.get()); assertEquals(base, EscrowCodec.decode(policy.read(file()).orElseThrow()));
            var accepted = EscrowLedgerWriter.Snapshot.of(base.generation(5), Set.of());
            assertTrue(writer.request(() -> accepted, outcomes::add, () -> true)); poll(ledgerIo, writer, writer::idle);
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, outcomes.get(outcomes.size() - 1).outcome());
            assertEquals(5, EscrowCodec.decode(policy.read(file()).orElseThrow()).writerGeneration());

            ioFails.set(true);
            for (long generation = 6; generation <= 8; generation++) {
                var snapshot = EscrowLedgerWriter.Snapshot.of(base.generation(generation), Set.of());
                assertTrue(writer.request(() -> snapshot, outcomes::add, () -> true)); poll(ledgerIo, writer, writer::idle);
                assertEquals(EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE, outcomes.get(outcomes.size() - 1).outcome());
            }
            assertTrue(writer.unavailable(), "Three definite IO failures must still make the writer unavailable");
        }
    }
    @Test void queuedRetirementCopiesCallerOwnedIdsAndProofMapBeforeSuccessorCapture() throws Exception {
        var op = EscrowTestFixtures.operation(1); var base = EscrowTestFixtures.ledger(op).generation(1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(base)); data.setDirty();
        CountDownLatch staged = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger stages = new AtomicInteger(); var outcomes = new ArrayList<Boolean>();
        var ledgerIo = new AwaitedLedgerIo();
        var writer = ledgerIo.writer(file(), 1, CheckedSavedDataFile::atomicMove, cut -> {
            if (cut.equals("K1") && stages.incrementAndGet() == 1) { staged.countDown(); await(release); }
        });
        var coordinator = new EscrowCoordinator(data, writer);
        try {
            var proofs = new java.util.HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
            for (var endpoint : List.of(op.source(), op.destination())) {
                var chain = EscrowPrefix.chains(base.records()).get(endpoint.key());
                var frontier = new EscrowPrefix.State(base.id(), endpoint.afterEpoch(), endpoint.afterSignature(),
                        chain.stream().map(EscrowPrefix.Link::marker).toList());
                var capture = new EscrowChunkStore.Capture(java.util.concurrent.CompletableFuture.completedFuture(null),
                        null, directory, new net.minecraft.world.level.ChunkPos(0, 0),
                        new java.util.concurrent.atomic.AtomicLong(1), 1, 0, () -> { }, () -> { });
                proofs.put(endpoint.key(), new EscrowBarrier.Proof(frontier, capture,
                        new AtomicBoolean(true), new AtomicBoolean(true)));
                coordinator.freeze(endpoint.key(), frontier);
            }
            coordinator.autosave(); assertTrue(staged.await(5, TimeUnit.SECONDS)); coordinator.tick(10);
            var ids = new java.util.HashSet<>(Set.of(op.id()));
            assertTrue(coordinator.retire(ids, proofs, outcomes::add));
            ids.clear(); proofs.clear(); release.countDown(); poll(ledgerIo, writer, writer::idle);
            assertEquals(List.of(true), outcomes); assertTrue(data.ledger().records().isEmpty());
            assertEquals(3, data.ledger().writerGeneration());
            assertEquals(data.ledger(), EscrowCodec.decode(CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW)
                    .read(file()).orElseThrow()));
        } finally { release.countDown(); coordinator.close(); }
    }
    /** Review W11b1 finding 1, probe "wedge": ambiguous L2, then a newer endpoint save before the 200-tick retry. */
    @Test @Timeout(60) void anAmbiguousRetirementWhoseProofIsLaterInvalidatedResolvesWithoutWedgingTheWriter() throws Exception {
        var op = EscrowTestFixtures.operation(1); var base = EscrowTestFixtures.ledger(op).generation(1);
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW);
        policy.replace(file(), () -> EscrowCodec.encode(base));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(base));
        AtomicInteger moves = new AtomicInteger(), unknown = new AtomicInteger();
        var ledgerIo = new AwaitedLedgerIo();
        var writer = ledgerIo.writer(file(), 1, (staged, target) -> {
            // Windows backup/antivirus/indexer handle: the replacement is refused without moving.
            if (moves.incrementAndGet() == 1) { throw new java.nio.file.AccessDeniedException(target.toString()); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }, cut -> { if (cut.equals("WRITE_UNKNOWN")) { unknown.incrementAndGet(); } });
        var coordinator = new EscrowCoordinator(data, writer); var callbacks = new ArrayList<Boolean>();
        var savedGenerations = new ArrayList<java.util.concurrent.atomic.AtomicLong>();
        var proofs = frozenProofs(coordinator, base, op, savedGenerations);
        coordinator.tick(10); assertTrue(coordinator.retire(Set.of(op.id()), proofs, callbacks::add));
        long tick = ledgerIo.tickUntil(coordinator::tick, 11, () -> unknown.get() != 0);
        assertEquals(1, unknown.get(), "The first replacement must be ambiguous");
        savedGenerations.get(0).incrementAndGet(); // Vanilla saves one endpoint chunk before the retry.
        tick = ledgerIo.tickUntil(coordinator::tick, tick, writer::idle);
        assertTrue(writer.idle(), "The ledger writer wedged on an invalidated uncertain retirement");
        assertFalse(writer.unavailable()); assertEquals(1, unknown.get());
        assertEquals(1, moves.get(), "An invalidated proof authorized another replacement");
        assertEquals(List.of(false), callbacks, "Retirement callbacks must be final");
        assertEquals(List.of(op), coordinator.ledger().records()); assertEquals(base, EscrowCodec.decode(policy.read(file()).orElseThrow()));
        assertTrue(coordinator.frozen(op.source().key()) && coordinator.frozen(op.destination().key()) && coordinator.locked(op.source().key()));
        // The writer accepts and commits a later non-retiring generation.
        tick += 10; coordinator.tick(tick); assertTrue(data.isDirty()); coordinator.autosave(); assertFalse(writer.idle());
        ledgerIo.tickUntil(coordinator::tick, tick + 1, writer::idle);
        assertEquals(3, EscrowCodec.decode(policy.read(file()).orElseThrow()).writerGeneration());
        assertEquals(List.of(op), EscrowCodec.decode(policy.read(file()).orElseThrow()).records());
        long started = System.nanoTime(); coordinator.close();
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5), "Shutdown drain did not finish");
    }
    /** Review W11b1 finding 6, probe "retire": an ambiguous first move, then a commit, reports one final callback. */
    @Test @Timeout(60) void anAmbiguousRetirementThatLaterCommitsCallsBackOnceWithTrue() throws Exception {
        var op = EscrowTestFixtures.operation(1); var base = EscrowTestFixtures.ledger(op).generation(1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(base)); AtomicInteger moves = new AtomicInteger();
        var ledgerIo = new AwaitedLedgerIo();
        var writer = ledgerIo.writer(file(), 1, (staged, target) -> {
            if (moves.incrementAndGet() == 1) { throw new IOException("Ambiguous replacement outcome"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        var callbacks = new ArrayList<Boolean>();
        try (var coordinator = new EscrowCoordinator(data, writer)) {
            var proofs = frozenProofs(coordinator, base, op, new ArrayList<>());
            coordinator.tick(10); assertTrue(coordinator.retire(Set.of(op.id()), proofs, callbacks::add));
            ledgerIo.tickUntil(coordinator::tick, 11, writer::idle);
            assertTrue(writer.idle()); assertEquals(2, moves.get());
            assertEquals(List.of(true), callbacks); assertTrue(coordinator.ledger().records().isEmpty());
            assertFalse(coordinator.frozen(op.source().key()) || coordinator.frozen(op.destination().key()));
        }
    }
    private Map<EscrowEndpoint.Key, EscrowBarrier.Proof> frozenProofs(EscrowCoordinator coordinator, EscrowLedger base, EscrowOperation op,
                                                                      List<java.util.concurrent.atomic.AtomicLong> savedGenerations) {
        var proofs = new java.util.HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
        for (var endpoint : List.of(op.source(), op.destination())) {
            var frontier = new EscrowPrefix.State(base.id(), endpoint.afterEpoch(), endpoint.afterSignature(),
                    EscrowPrefix.chains(base.records()).get(endpoint.key()).stream().map(EscrowPrefix.Link::marker).toList());
            var generation = new java.util.concurrent.atomic.AtomicLong(1); savedGenerations.add(generation);
            var capture = new EscrowChunkStore.Capture(java.util.concurrent.CompletableFuture.completedFuture(null), null, directory,
                    new net.minecraft.world.level.ChunkPos(0, 0), generation, 1, 0, () -> { }, () -> { });
            proofs.put(endpoint.key(), new EscrowBarrier.Proof(frontier, capture, new AtomicBoolean(true), new AtomicBoolean(true)));
            coordinator.freeze(endpoint.key(), frontier);
        }
        return proofs;
    }
    /** Shutdown always finishes: a persistently unknown outcome stops after a bounded number of drain attempts. */
    @Test @Timeout(60) void shutdownStopsRetryingAPersistentlyUnknownGenerationAndLeavesTheTargetFile() throws Exception {
        var base = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW);
        policy.replace(file(), () -> EscrowCodec.encode(base));
        AtomicInteger moves = new AtomicInteger(); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        var ledgerIo = new AwaitedLedgerIo();
        var writer = ledgerIo.writer(file(), 1, (staged, target) -> {
            moves.incrementAndGet(); throw new java.nio.file.AccessDeniedException(target.toString());
        });
        writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.retire(Set.of(base.records().get(0).id())).generation(2), Set.of()),
                outcomes::add, () -> true);
        poll(ledgerIo, writer, () -> !outcomes.isEmpty());
        long started = System.nanoTime(); writer.close();
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(30), "Shutdown drain did not finish");
        assertEquals(1 + EscrowLedgerWriter.DRAIN_UNKNOWN_ATTEMPTS, moves.get());
        assertTrue(outcomes.stream().allMatch(c -> c.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN));
        assertEquals(base, EscrowCodec.decode(policy.read(file()).orElseThrow()));
        assertFalse(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(3), Set.of()), outcomes::add));
    }
    /** A foreign target is not mistaken for the last durable generation; the candidate stays unknown. */
    @Test @Timeout(60) void anInvalidatedUncertainRetryAgainstAForeignTargetStaysUnknown() throws Exception {
        var base = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW);
        policy.replace(file(), () -> EscrowCodec.encode(base));
        AtomicBoolean valid = new AtomicBoolean(true); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        var ledgerIo = new AwaitedLedgerIo();
        var writer = ledgerIo.writer(file(), 1, (staged, target) -> { throw new IOException("Unknown replacement outcome"); });
        writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.retire(Set.of(base.records().get(0).id())).generation(2), Set.of()),
                outcomes::add, valid::get);
        poll(ledgerIo, writer, () -> !outcomes.isEmpty());
        var foreign = base.generation(7); policy.replace(file(), () -> EscrowCodec.encode(foreign)); valid.set(false);
        ledgerIo.tickUntil(writer::tick, 1_000, () -> outcomes.size() >= 3);
        assertEquals(3, outcomes.size()); assertFalse(writer.idle());
        assertTrue(outcomes.stream().allMatch(c -> c.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN));
        writer.close(); assertEquals(foreign, EscrowCodec.decode(policy.read(file()).orElseThrow()));
    }
    /** Review W11b1 finding 3: about 14 hours of uptime; a queued successor must not wait out the cadence from tick 0. */
    @Test @Timeout(60) void shutdownDrainAtAHighServerTickDoesNotRestartTheWriterClock() throws Exception {
        var base = EscrowTestFixtures.ledger(); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        var writer = new EscrowLedgerWriter(file(), 0); writer.tick(1_000_000);
        assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(1), Set.of()), outcomes::add));
        assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of()), outcomes::add));
        assertEquals(1_000_000, writer.lastIoStart());
        long started = System.nanoTime(); writer.close(); long elapsed = System.nanoTime() - started;
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(5), "Shutdown drain took " + TimeUnit.NANOSECONDS.toMillis(elapsed) + " ms");
        assertEquals(List.of(1L, 2L), outcomes.stream().map(EscrowLedgerWriter.Completion::generation).toList());
        assertTrue(outcomes.stream().allMatch(c -> c.outcome() == EscrowLedgerWriter.Outcome.COMMITTED));
        assertEquals(2, CheckedSavedDataFile.readPayload(file(), ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow().getLong("writer_generation"));
    }
    /** Review W11b1 finding 5: no autosave after the server stops, and the crash path joins IO without callbacks. */
    @Test @Timeout(60) void crashPathStartsNoGenerationAndJoinsTheWriterWithoutCallbacks() throws Exception {
        var idleData = ResourceEscrowSavedData.create(); var idleWriter = new EscrowLedgerWriter(directory.resolve("idle").resolve(file().getFileName()), 0);
        try (var idle = new EscrowCoordinator(idleData, idleWriter)) {
            idleData.attach(idle, () -> false); idleData.setDirty(); idleData.save(file().toFile());
            assertTrue(idleWriter.idle(), "A stopped server started a ledger generation from saveAllChunks");
        }
        CountDownLatch staged = new CountDownLatch(1), release = new CountDownLatch(1); AtomicBoolean moved = new AtomicBoolean();
        var writer = new EscrowLedgerWriter(file(), 0, (pending, target) -> {
            staged.countDown(); await(release); CheckedSavedDataFile.atomicMove(pending, target); moved.set(true);
        });
        var data = ResourceEscrowSavedData.create(); var coordinator = new EscrowCoordinator(data, writer);
        data.attach(coordinator, () -> true); data.setDirty(); data.save(file().toFile());
        assertFalse(writer.idle()); assertTrue(staged.await(5, TimeUnit.SECONDS));
        var releaser = new Thread(() -> {
            try { Thread.sleep(200); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            release.countDown();
        });
        releaser.start(); coordinator.abort(); releaser.join();
        assertTrue(moved.get(), "World ownership could be released while the ledger IO thread was active");
        assertEquals(0, data.ledger().writerGeneration(), "The crash path pumped a writer callback");
        assertEquals(1, CheckedSavedDataFile.readPayload(file(), ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow().getLong("writer_generation"));
        assertFalse(writer.request(() -> EscrowLedgerWriter.Snapshot.of(EscrowTestFixtures.ledger().generation(2), Set.of()), result -> { }));
        coordinator.autosave(); coordinator.close(); assertTrue(writer.idle());
    }
    /** W27C1-R6, the review's sibling 1: a refused generation leaves no request behind, so the shutdown drain ends. */
    @Test @Timeout(60) void aRefusedGenerationLeavesNoActiveRequestSoTheShutdownDrainEnds() throws Exception {
        var base = EscrowTestFixtures.ledger(); var writer = new EscrowLedgerWriter(file(), 2);
        assertThrows(IllegalArgumentException.class, () -> writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of()),
                result -> fail("A refused generation published a completion")), "a generation never repeats");
        assertTrue(writer.idle(), "the refused generation left no active request");
        writer.close();
        assertFalse(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(3), Set.of()), result -> { }));
    }
    /** W27C1-R6, sibling 2: an Error on the IO thread is an unknown outcome of the exact candidate, and shutdown still ends. */
    @Test @Timeout(60) void anErrorOnTheIoThreadIsAnUnknownOutcomeAndTheShutdownDrainStillEnds() throws Exception {
        var base = EscrowTestFixtures.ledger();
        CheckedSavedDataFile.Committer error = (staged, target) -> { throw new IOError(new IOException("Injected IO error")); };
        var mode = new AtomicReference<>(error); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file(), 0, (staged, target) -> mode.get().commit(staged, target))) {
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(1), Set.of()), outcomes::add));
            long tick = ledgerIo.tickUntil(writer::tick, 1, () -> !outcomes.isEmpty());
            assertEquals(EscrowLedgerWriter.Outcome.UNKNOWN, outcomes.get(0).outcome()); assertTrue(writer.unknown());
            mode.set(CheckedSavedDataFile::atomicMove);
            ledgerIo.tickUntil(writer::tick, tick, writer::idle);
            assertTrue(writer.idle(), "Bounded writer did not complete");
            var last = outcomes.get(outcomes.size() - 1);
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, last.outcome()); assertEquals(outcomes.get(0).digest(), last.digest());
        }
        byte[] durable = Files.readAllBytes(file()); var drained = new ArrayList<EscrowLedgerWriter.Outcome>();
        // The production IO thread with an Error on every attempt: the drain gives up after three unknown outcomes.
        var writer = new EscrowLedgerWriter(file(), 1, error);
        assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of()), result -> drained.add(result.outcome())));
        writer.close();
        assertTrue(drained.size() >= EscrowLedgerWriter.DRAIN_UNKNOWN_ATTEMPTS
                && drained.stream().allMatch(EscrowLedgerWriter.Outcome.UNKNOWN::equals), drained::toString);
        assertArrayEquals(durable, Files.readAllBytes(file()), "the target file stays authoritative");
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) { throw new AssertionError("Writer test gate timed out"); } }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }
    static void poll(AwaitedLedgerIo ledgerIo, EscrowLedgerWriter writer, java.util.function.BooleanSupplier done) {
        ledgerIo.tickUntil(writer::tick, 0, done);
        assertTrue(done.getAsBoolean(), "Bounded writer did not complete");
    }
}
