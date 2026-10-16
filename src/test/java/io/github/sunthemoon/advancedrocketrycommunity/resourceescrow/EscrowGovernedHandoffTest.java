package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Real checked F7 writes with explicit A0 slot/passenger tokens, not native F6 or release evidence. */
class EscrowGovernedHandoffTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @ParameterizedTest @EnumSource(value = EscrowFlightTables.Outcome.class, names = {"HANDED_OFF", "CANCELLED"})
    void exactAcknowledgementCompletesOnlyItsSerialWithoutInventingAHead(EscrowFlightTables.Outcome outcome) throws Exception {
        try (var f = new Fixture(outcome, false)) {
            var before = f.data.ledger(); var results = new ArrayList<Boolean>();
            try (var writer = f.writer(CheckedSavedDataFile::atomicMove, cut -> { });
                 var coordinator = new EscrowCoordinator(f.data, writer)) {
                assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add));
                assertEquals(before, coordinator.ledger()); assertTrue(results.isEmpty());
                assertTrue(f.proof.valid()); assertTrue(coordinator.logicalLocked(f.record.logicalRocketId()));
                assertFalse(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add));
                pump(coordinator, 0, () -> !results.isEmpty());
                assertEquals(List.of(true), results); assertFalse(f.proof.valid());
                assertEquals(f.slots.size(), f.releases.get());
                var terminal = coordinator.ledger(); var arm = terminal.armedFlights().get(0);
                assertEquals(outcome, arm.outcome()); assertEquals(before.issuedLaunchSerial(), terminal.issuedLaunchSerial());
                assertEquals(before.writerGeneration() + 1, terminal.writerGeneration());
                assertTrue(terminal.rocketAuthorities().isEmpty()); assertEquals(before.tombstones(), terminal.tombstones());
                assertEquals(f.record, f.journal.find(f.record.transferId()).orElseThrow());
                assertEquals(EscrowGovernedDispatch.Route.CLEANUP, EscrowGovernedDispatch.decide(terminal, f.record).route());
                assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.finish(terminal, arm, outcome,
                        arm.source(), arm.epoch(), f.signature(), f.invariant()));
                assertEquals(terminal, f.disk());
                // The retained entry still charges capacity. Live successor release is a separate integration boundary.
                assertTrue(coordinator.logicalLocked(arm.logical()));
            }
            for (int restart = 0; restart < 2; restart++) {
                var loaded = f.disk();
                try (var baseline = new EscrowLedgerWriter(f.file, loaded.writerGeneration())) { assertTrue(baseline.baseline(loaded)); }
                assertEquals(EscrowGovernedDispatch.Route.CLEANUP, EscrowGovernedDispatch.decide(loaded, f.record).route());
            }
        }
    }

    @ParameterizedTest @EnumSource(value = EscrowFlightTables.Outcome.class, names = {"HANDED_OFF", "CANCELLED"})
    void existingReadyHeadAdvancesButPermanentReceiptsStayImmutable(EscrowFlightTables.Outcome outcome) throws Exception {
        try (var f = new Fixture(outcome, true)) {
            var receipts = f.data.ledger().tombstones(); var results = new ArrayList<Boolean>();
            try (var writer = f.writer(CheckedSavedDataFile::atomicMove, cut -> { }); var coordinator = new EscrowCoordinator(f.data, writer)) {
                assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add));
                pump(coordinator, 0, () -> !results.isEmpty()); assertEquals(List.of(true), results);
                var head = coordinator.ledger().rocketAuthorities().get(0); var arm = coordinator.ledger().armedFlights().get(0);
                assertEquals(outcome == EscrowFlightTables.Outcome.HANDED_OFF ? arm.destination() : arm.source(), head.physical());
                assertEquals(outcome == EscrowFlightTables.Outcome.HANDED_OFF ? 0 : arm.epoch(), head.epoch());
                assertEquals(arm.serial(), head.issued()); assertEquals(arm.serial(), head.completed());
                assertEquals(outcome, head.outcome()); assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, head.kind());
                assertEquals(f.signature(), head.signature()); assertEquals(f.invariant(), head.invariant());
                assertEquals(receipts, coordinator.ledger().tombstones()); assertEquals(coordinator.ledger(), f.disk());
            }
        }
    }

    @Test void captureRepeatsPassengerAndCompleteSurvivorChecks() throws Exception {
        for (int changed = 0; changed < 3; changed++) {
            try (var f = new Fixture(EscrowFlightTables.Outcome.CANCELLED, false);
                 var writer = f.writer(CheckedSavedDataFile::atomicMove, cut -> { }); var coordinator = new EscrowCoordinator(f.data, writer)) {
                var before = coordinator.ledger(); var results = new ArrayList<Boolean>();
                assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add));
                if (changed == 0) { f.passengers.set(false); }
                else if (changed == 1) { f.observed.putBoolean("invulnerable", true); }
                else { f.observationInvalid = true; }
                pump(coordinator, 0, () -> !results.isEmpty());
                assertEquals(List.of(false), results); assertEquals(before, coordinator.ledger()); assertEquals(before, f.disk());
                assertEquals(f.slots.size(), f.releases.get());
            }
        }
    }

    @ParameterizedTest @EnumSource(value = EscrowFlightTables.Outcome.class, names = {"HANDED_OFF", "CANCELLED"})
    void replacementRechecksEveryJointTokenAndNeverReadsOwnerThreadObservations(EscrowFlightTables.Outcome outcome) throws Exception {
        for (int invalidation = 0; invalidation < 4; invalidation++) {
            try (var f = new Fixture(outcome, false)) {
                int change = invalidation; var results = new ArrayList<Boolean>(); var moved = new AtomicInteger();
                try (var writer = f.writer((staged, target) -> { moved.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, target); }, cut -> {
                    if (!cut.equals("K8")) { return; }
                    if (change < 2) { f.slots.values().stream().skip(change).findFirst().orElseThrow().capture().generation().incrementAndGet(); }
                    else if (change == 2) { f.nativeAndPassengers.set(false); }
                    else { f.slots.values().forEach(proof -> proof.lifetime().set(false)); }
                }); var coordinator = new EscrowCoordinator(f.data, writer)) {
                    var before = coordinator.ledger();
                    assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add));
                    pump(coordinator, 0, () -> !results.isEmpty());
                    assertEquals(List.of(false), results); assertEquals(0, moved.get());
                    assertEquals(before, coordinator.ledger()); assertEquals(before, f.disk());
                }
            }
        }
    }

    @Test void journalLeaseInvalidationBeforeCaptureRefusesWithoutReplacingEitherAuthority() throws Exception {
        try (var f = new Fixture(EscrowFlightTables.Outcome.CANCELLED, false);
             var writer = f.writer(CheckedSavedDataFile::atomicMove, cut -> { }); var coordinator = new EscrowCoordinator(f.data, writer)) {
            var before = coordinator.ledger(); var results = new ArrayList<Boolean>();
            assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add));
            f.journal.remove(f.record.transferId());
            pump(coordinator, 0, () -> !results.isEmpty());
            assertEquals(List.of(false), results); assertEquals(before, coordinator.ledger()); assertEquals(before, f.disk());
        }
    }

    @Test void obsoleteCallbackCannotReleaseButDiskTerminalNeverResumes() throws Exception {
        try (var f = new Fixture(EscrowFlightTables.Outcome.CANCELLED, false)) {
            var results = new ArrayList<Boolean>(); var replaced = new AtomicBoolean();
            try (var writer = f.writer((staged, target) -> { CheckedSavedDataFile.atomicMove(staged, target); replaced.set(true); }, cut -> { });
                 var coordinator = new EscrowCoordinator(f.data, writer)) {
                assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add));
                coordinator.tick(0);
                await(replaced::get);
                assertTrue(results.isEmpty());
                f.journal.remove(f.record.transferId());
                pump(coordinator, 1, () -> !results.isEmpty());
                assertEquals(List.of(false), results);
                assertEquals(EscrowGovernedDispatch.Route.CLEANUP, EscrowGovernedDispatch.decide(f.disk(), f.record).route());
            }
        }
    }

    @Test void replacementRechecksTheJournalLeaseAfterOwnerThreadCapture() throws Exception {
        try (var f = new Fixture(EscrowFlightTables.Outcome.CANCELLED, false)) {
            var reached = new java.util.concurrent.CountDownLatch(1);
            var resume = new java.util.concurrent.CountDownLatch(1);
            var results = new ArrayList<Boolean>(); var moved = new AtomicInteger();
            try (var writer = f.writer((staged, target) -> { moved.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, target); }, cut -> {
                if (!cut.equals("K8")) { return; }
                reached.countDown();
                try { assertTrue(resume.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException(stopped); }
            }); var coordinator = new EscrowCoordinator(f.data, writer)) {
                var before = coordinator.ledger();
                assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add)); coordinator.tick(0);
                try {
                    assertTrue(reached.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    f.journal.remove(f.record.transferId());
                } finally { resume.countDown(); }
                pump(coordinator, 1, () -> !results.isEmpty());
                assertEquals(List.of(false), results); assertEquals(0, moved.get());
                assertEquals(before, coordinator.ledger()); assertEquals(before, f.disk());
            } finally { resume.countDown(); }
        }
    }

    @Test void acceptedR13MotionIntervalKeepsDiskAuthorityAndReleasesOnlyAfterCallback() throws Exception {
        try (var f = new Fixture(EscrowFlightTables.Outcome.HANDED_OFF, false)) {
            var reached = new java.util.concurrent.CountDownLatch(1);
            var resume = new java.util.concurrent.CountDownLatch(1);
            var results = new ArrayList<Boolean>();
            try (var writer = f.writer(CheckedSavedDataFile::atomicMove, cut -> {
                if (!cut.equals("K8")) { return; }
                reached.countDown();
                try { assertTrue(resume.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException(stopped); }
            }); var coordinator = new EscrowCoordinator(f.data, writer)) {
                assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add)); coordinator.tick(0);
                try {
                    assertTrue(reached.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    f.observed.put("motion", GovernedTransferFixture.vector(0.125, 0, 0));
                    assertTrue(f.proof.valid()); assertTrue(results.isEmpty());
                    assertFalse(EscrowTerminalSuccessor.matches(coordinator.ledger(), f.record, f.observed));
                } finally { resume.countDown(); }
                pump(coordinator, 1, () -> !results.isEmpty());
                assertEquals(List.of(true), results);
                assertTrue(EscrowTerminalSuccessor.matches(coordinator.ledger(), f.record, f.observed));
                assertEquals(coordinator.ledger(), f.disk());
                assertEquals(f.record, f.journal.find(f.record.transferId()).orElseThrow());
                assertEquals(GovernedTransferFixture.vector(0, 0, 0), f.survivor.getList("motion", 6));
            } finally { resume.countDown(); }
        }
    }

    @Test void unknownKeepsExactCandidateTokensAndCapacityUntilItsAcknowledgement() throws Exception {
        try (var f = new Fixture(EscrowFlightTables.Outcome.CANCELLED, false)) {
            var results = new ArrayList<Boolean>(); var fail = new AtomicBoolean(true);
            try (var writer = f.writer((staged, target) -> {
                if (fail.get()) { throw new java.io.IOException("injected ambiguous F7 replacement"); }
                CheckedSavedDataFile.atomicMove(staged, target);
            }, cut -> { }); var coordinator = new EscrowCoordinator(f.data, writer)) {
                var before = coordinator.ledger();
                assertTrue(EscrowGovernedHandoff.request(coordinator, f.journal, f.proof, results::add)); coordinator.tick(0);
                await(() -> { coordinator.tick(1); return writer.unknown(); });
                assertTrue(results.isEmpty()); assertEquals(before, coordinator.ledger()); assertTrue(f.proof.valid());
                assertEquals(0, f.releases.get()); assertTrue(coordinator.logicalLocked(f.record.logicalRocketId()));
                fail.set(false); pump(coordinator, 201, () -> !results.isEmpty());
                assertEquals(List.of(true), results); assertFalse(f.proof.valid()); assertEquals(f.slots.size(), f.releases.get());
                assertEquals(before.writerGeneration() + 1, coordinator.ledger().writerGeneration());
                assertEquals(before.issuedLaunchSerial(), coordinator.ledger().issuedLaunchSerial());
                assertEquals(coordinator.ledger(), f.disk());
            }
        }
    }

    @Test void incompleteOrWrongSlotFrontierAndUnacknowledgedRecordCannotBecomeF7Proofs() throws Exception {
        try (var f = new Fixture(EscrowFlightTables.Outcome.HANDED_OFF, false)) {
            var incomplete = new LinkedHashMap<>(f.slots); incomplete.remove(incomplete.keySet().iterator().next());
            assertThrows(IllegalArgumentException.class, () -> f.proof(incomplete));
            var wrong = new LinkedHashMap<>(f.slots); var endpoint = wrong.keySet().iterator().next(); var proof = wrong.get(endpoint);
            wrong.put(endpoint, new EscrowBarrier.Proof(new EscrowPrefix.State(f.data.ledger().id(), 99,
                    endpoint.beforeSignature(), List.of()), proof.capture(), proof.active(), proof.lifetime()));
            assertThrows(IllegalArgumentException.class, () -> f.proof(wrong));
            f.journal.remove(f.record.transferId());
            assertThrows(IllegalArgumentException.class, () -> f.proof(f.slots));
        }
    }

    private final class Fixture implements AutoCloseable {
        final EscrowFlightTables.Outcome outcome;
        final RocketTransferRecord record;
        final ResourceEscrowSavedData data;
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final CompoundTag survivor;
        final CompoundTag observed;
        final AtomicBoolean passengers = new AtomicBoolean(true), nativeAndPassengers = new AtomicBoolean(true);
        final AtomicInteger releases = new AtomicInteger();
        final Map<EscrowEndpoint, EscrowBarrier.Proof> slots = new LinkedHashMap<>();
        final Path file;
        final EscrowGovernedHandoff.Proof proof;
        final Thread owner = Thread.currentThread();
        boolean observationInvalid;

        Fixture(EscrowFlightTables.Outcome outcome, boolean headed) throws Exception {
            this.outcome = outcome;
            var fixture = GovernedTransferFixture.create();
            var original = outcome == EscrowFlightTables.Outcome.HANDED_OFF ? EscrowGovernedArrivalStateTest.record() : fixture.record();
            var root = EscrowGovernedDispatchTest.root(original);
            if (headed) {
                var tag = original.nativeTransfer().orElseThrow().armed().encode(); tag.putUUID("a", GovernedTransferFixture.id(9));
                tag.putLong("release", root.writerGeneration()); var arm = EscrowFlightTables.Armed.decode(tag);
                var envelope = EscrowTransferEnvelope.capture(arm, original.nativeTransfer().orElseThrow().sourceEnvelope(original),
                        original.nativeTransfer().orElseThrow().destinationEnvelope(original), original.sourceSnapshot(), original.destinationSnapshot(),
                        original.sourceFlightData(), original.destinationFlightData());
                var next = RocketTransferRecord.createGoverned(original.transferId(), original.logicalRocketId(), original.ownerId(), original.sourceEntityId(),
                        original.sourceSnapshot(), original.destinationSnapshot(), original.sourceFlightData(), original.destinationFlightData(),
                        original.requiredFuel(), original.createdAtGameTime(), envelope);
                original = outcome == EscrowFlightTables.Outcome.HANDED_OFF ? EscrowGovernedDispatchTest.committed(next) : next;
                var receipt = EscrowTestFixtures.receipt(1); receipt.putUUID("op_id", arm.anchor()); receipt.putUUID("anchor_op", arm.anchor());
                receipt.getList("retired", 10).getCompound(0).putUUID("op_id", arm.anchor());
                var guards = new ListTag(); guards.add(EscrowCodec.guard(new EscrowGuard(arm.logical(), null, 1, "3".repeat(64))));
                receipt.put("guards", guards); receipt.putBoolean("released", true); receipt.putLong("release_generation", arm.releaseGeneration());
                var head = new EscrowFlightTables.Head(arm.logical(), arm.owner(), arm.anchor(), arm.releaseGeneration(), arm.source(), arm.epoch(),
                        EscrowSignatureKind.FUEL_VECTOR_V1, original.sourceFlightData().fuel().resourceSignature(),
                        EscrowInvariant.rocket(arm.source(), arm.logical(), arm.owner(), EscrowAdapterIds.ROCKET, original.sourceSnapshot()),
                        arm.serial(), 0, EscrowFlightTables.Outcome.READY);
                root = new EscrowLedger(root.id(), 1, root.writerGeneration(), List.of(), List.of(), List.of(receipt),
                        arm.serial(), List.of(head), List.of(arm));
            }
            record = original; data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            survivor = outcome == EscrowFlightTables.Outcome.HANDED_OFF ? EscrowGovernedTerminalDiskTest.landed(record)
                    : EscrowGovernedTerminalDiskTest.returned(fixture);
            observed = survivor.copy();
            var base = java.nio.file.Files.createDirectory(directory.resolve(java.util.UUID.randomUUID().toString()));
            file = base.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW).replace(file, () -> EscrowCodec.encode(data.ledger()));
            journal.put(record); EscrowArmedRestartBoundaryTest.acknowledge(journal, base.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            for (var endpoint : EscrowGovernedTerminalDisk.slots(record, outcome)) {
                var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, base,
                        new ChunkPos(endpoint.x(), endpoint.z()), new AtomicLong(1), 1, 64, () -> { }, releases::incrementAndGet);
                slots.put(endpoint, new EscrowBarrier.Proof(new EscrowPrefix.State(root.id(), endpoint.beforeEpoch(), endpoint.beforeSignature(), List.of()),
                        capture, new AtomicBoolean(true), new AtomicBoolean(true)));
            }
            proof = proof(slots);
        }
        EscrowGovernedHandoff.Proof proof(Map<EscrowEndpoint, EscrowBarrier.Proof> values) {
            return new EscrowGovernedHandoff.Proof(journal, record, outcome, survivor, values,
                    () -> {
                        assertSame(owner, Thread.currentThread());
                        if (observationInvalid) { throw new IllegalStateException("injected malformed native observation"); }
                        return observed;
                    },
                    () -> { assertSame(owner, Thread.currentThread()); return passengers.get(); }, nativeAndPassengers::get);
        }
        EscrowLedgerWriter writer(CheckedSavedDataFile.Committer move, java.util.function.Consumer<String> cut) {
            var writer = new EscrowLedgerWriter(file, data.ledger().writerGeneration(), move, cut);
            assertTrue(writer.baseline(data.ledger())); return writer;
        }
        String signature() { return EscrowGovernedArrivalState.flight(survivor).fuel().resourceSignature(); }
        String invariant() { return EscrowInvariant.rocket(survivor.getUUID("physical"), record.logicalRocketId(), record.ownerId(),
                EscrowAdapterIds.ROCKET, outcome == EscrowFlightTables.Outcome.HANDED_OFF ? record.destinationSnapshot() : record.sourceSnapshot()); }
        EscrowLedger disk() throws Exception { return EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()); }
        @Override public void close() { proof.close(); journal.stop(); }
    }

    private static void pump(EscrowCoordinator coordinator, long start, BooleanSupplier done) throws Exception {
        for (long tick = start; tick < start + 1_000 && !done.getAsBoolean(); tick++) { coordinator.tick(tick); Thread.sleep(2); }
        assertTrue(done.getAsBoolean(), "Expected exact F7 completion within the bounded test pump");
    }
    private static void await(BooleanSupplier done) throws Exception {
        for (int attempt = 0; attempt < 1_000 && !done.getAsBoolean(); attempt++) { Thread.sleep(2); }
        assertTrue(done.getAsBoolean(), "Expected asynchronous writer progress within the bounded test wait");
    }
}
