package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowLedgerWriterTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    private Path file() { return directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); }

    @Test void aFailedGenerationDoesNotCancelAnAdmissionInTheCapturedSuccessor() throws Exception {
        var op = EscrowTestFixtures.operation(1); var base = EscrowTestFixtures.ledger(op);
        CountDownLatch captured = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger stages = new AtomicInteger(); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        try (var writer = new EscrowLedgerWriter(file(), 1, CheckedSavedDataFile::atomicMove, cut -> {
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
            poll(writer, () -> writer.idle());
            assertEquals(1, captures.get()); assertEquals(2, outcomes.size());
            assertEquals(EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE, outcomes.get(0).outcome());
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, outcomes.get(1).outcome());
            assertEquals(3, CheckedSavedDataFile.readPayload(file(), ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow().getLong("writer_generation"));
        } finally { release.countDown(); }
    }
    @Test void unknownOutcomeRetainsTheExactCandidateAndBlocksDependentGenerations() throws Exception {
        var base = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)); AtomicInteger attempts = new AtomicInteger();
        var outcomes = new ArrayList<EscrowLedgerWriter.Completion>(); AtomicInteger successorCaptures = new AtomicInteger();
        try (var writer = new EscrowLedgerWriter(file(), 0, (staged, target) -> {
            if (attempts.incrementAndGet() <= 3) { throw new IOException("Unknown replacement outcome"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        })) {
            writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.generation(1), Set.of()), outcomes::add);
            writer.request(() -> {
                successorCaptures.incrementAndGet(); return EscrowLedgerWriter.Snapshot.of(base.generation(2), Set.of());
            }, outcomes::add);
            long tick = 0; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!writer.idle() && System.nanoTime() < deadline) {
                writer.tick(tick++);
                if (outcomes.size() == 3) { assertTrue(writer.unavailable()); assertEquals(0, successorCaptures.get()); }
                Thread.sleep(1);
            }
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
        var writer = new EscrowLedgerWriter(file(), 0); var coordinator = new EscrowCoordinator(data, writer);
        try {
            coordinator.prepareTest(op, replies::add); coordinator.autosave();
            poll(writer, writer::idle); assertTrue(data.ledger().records().isEmpty()); assertTrue(replies.isEmpty()); assertTrue(data.isDirty());
            coordinator.tick(10); coordinator.commitPreparations(); poll(writer, writer::idle);
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
    @Test void anInvalidatedProofAtTheStagingBoundaryKeepsThePreviousLedger() throws Exception {
        var base = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW);
        policy.replace(file(), () -> EscrowCodec.encode(base));
        AtomicBoolean valid = new AtomicBoolean(true); var outcomes = new ArrayList<EscrowLedgerWriter.Completion>();
        try (var writer = new EscrowLedgerWriter(file(), 1, CheckedSavedDataFile::atomicMove, cut -> {
            if (cut.equals("K8")) { valid.set(false); }
        })) {
            writer.request(() -> EscrowLedgerWriter.Snapshot.of(base.retire(Set.of(base.records().get(0).id())).generation(2), Set.of()),
                    outcomes::add, valid::get);
            poll(writer, writer::idle);
            assertEquals(EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE, outcomes.get(0).outcome());
            assertEquals(base, EscrowCodec.decode(policy.read(file()).orElseThrow()));
        }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) { throw new AssertionError("Writer test gate timed out"); } }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }
    private static void poll(EscrowLedgerWriter writer, java.util.function.BooleanSupplier done) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); long tick = 0;
        while (!done.getAsBoolean() && System.nanoTime() < end) { writer.tick(tick++); Thread.sleep(1); }
        assertTrue(done.getAsBoolean(), "Bounded writer did not complete");
    }
}
