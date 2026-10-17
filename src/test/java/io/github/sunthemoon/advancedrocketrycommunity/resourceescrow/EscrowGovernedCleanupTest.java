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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EscrowGovernedCleanupTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @ParameterizedTest @EnumSource(value = EscrowFlightTables.Outcome.class, names = {"HANDED_OFF", "CANCELLED"})
    void journalAcknowledgementPrecedesReleaseAndExactPruning(EscrowFlightTables.Outcome outcome) throws Exception {
        try (var f = new Fixture(outcome, CheckedSavedDataFile::atomicMove, cut -> { })) {
            var results = new ArrayList<Boolean>();
            assertFalse(EscrowGovernedCleanup.absent(f.coordinator, f.journal, f.arm));
            assertFalse(EscrowGovernedCleanup.remove(f.coordinator, f.journal, f.record, () -> false));
            assertEquals(f.record, f.journal.find(f.record.transferId()).orElseThrow());
            assertTrue(EscrowGovernedCleanup.remove(f.coordinator, f.journal, f.record, () -> true));
            assertFalse(EscrowGovernedCleanup.absent(f.coordinator, f.journal, f.arm));
            assertFalse(EscrowGovernedCleanup.prune(f.coordinator, f.journal, f.arm, () -> true, results::add));
            var retained = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(f.journalFile,
                    ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow()).find(f.record.transferId()).orElseThrow();
            assertEquals(f.record.checksum(), retained.checksum());
            assertEquals(f.record.nativeTransfer().orElseThrow().encode(), retained.nativeTransfer().orElseThrow().encode());
            f.acknowledge();
            assertTrue(EscrowGovernedCleanup.absent(f.coordinator, f.journal, f.arm));
            assertFalse(EscrowGovernedCleanup.prune(f.coordinator, f.journal, f.arm, () -> false, results::add));
            var before = f.data.ledger();
            assertTrue(EscrowGovernedCleanup.prune(f.coordinator, f.journal, f.arm, () -> true, results::add));
            assertEquals(before, f.data.ledger()); assertTrue(results.isEmpty());
            pump(f.coordinator, 0, () -> !results.isEmpty()); assertEquals(List.of(true), results);
            assertTrue(f.data.ledger().armedFlights().isEmpty()); assertEquals(before.issuedLaunchSerial(), f.data.ledger().issuedLaunchSerial());
            assertEquals(before.tombstones(), f.data.ledger().tombstones()); assertEquals(before.rocketAuthorities(), f.data.ledger().rocketAuthorities());
            assertFalse(EscrowGovernedCleanup.prune(f.coordinator, f.journal, f.arm, () -> true, results::add));
        }
    }

    @ParameterizedTest @EnumSource(value = EscrowFlightTables.Outcome.class, names = {"HANDED_OFF", "CANCELLED"})
    void twoCheckedBaselinesResumeOnlyCleanupAfterJournalRemoval(EscrowFlightTables.Outcome outcome) throws Exception {
        try (var f = new Fixture(outcome, CheckedSavedDataFile::atomicMove, cut -> { })) {
            assertTrue(EscrowGovernedCleanup.remove(f.coordinator, f.journal, f.record, () -> true)); f.acknowledge();
            f.coordinator.close();
            for (int restart = 0; restart < 2; restart++) {
                var root = EscrowCodec.decode(CheckedSavedDataFile.readPayload(f.file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
                var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
                var journal = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(f.journalFile, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
                try (var writer = new EscrowLedgerWriter(f.file, root.writerGeneration()); var coordinator = new EscrowCoordinator(data, writer)) {
                    assertTrue(writer.baseline(root)); assertFalse(EscrowGovernedCleanup.absent(coordinator, journal, f.arm));
                    EscrowArmedRestartBoundaryTest.acknowledge(journal, f.journalFile);
                    assertTrue(EscrowGovernedCleanup.absent(coordinator, journal, f.arm));
                    assertEquals(EscrowGovernedDispatch.Route.CLEANUP, EscrowGovernedDispatch.decide(root, f.record).route());
                    if (restart == 1) {
                        var results = new ArrayList<Boolean>();
                        assertTrue(EscrowGovernedCleanup.prune(coordinator, journal, f.arm, () -> true, results::add));
                        pump(coordinator, 0, () -> !results.isEmpty()); assertEquals(List.of(true), results);
                    }
                } finally { journal.stop(); }
            }
        }
    }

    @Test void captureRepeatsJournalAndDependencyChecks() throws Exception {
        for (boolean journalChanged : List.of(false, true)) {
            try (var f = new Fixture(EscrowFlightTables.Outcome.CANCELLED, CheckedSavedDataFile::atomicMove, cut -> { })) {
                assertTrue(EscrowGovernedCleanup.remove(f.coordinator, f.journal, f.record, () -> true)); f.acknowledge();
                var available = new AtomicBoolean(true); var results = new ArrayList<Boolean>();
                assertTrue(EscrowGovernedCleanup.prune(f.coordinator, f.journal, f.arm, available::get, results::add));
                if (journalChanged) { f.journal.put(f.record); } else { available.set(false); }
                pump(f.coordinator, 0, () -> !results.isEmpty()); assertEquals(List.of(false), results);
                assertEquals(List.of(f.arm), f.data.ledger().armedFlights());
            }
        }
    }

    @Test void replacementRechecksAbsenceLeaseOffThread() throws Exception {
        var reached = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        try (var f = new Fixture(EscrowFlightTables.Outcome.CANCELLED, CheckedSavedDataFile::atomicMove, cut -> {
            if (!cut.equals("K8")) { return; }
            reached.countDown();
            try { assertTrue(resume.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        })) {
            assertTrue(EscrowGovernedCleanup.remove(f.coordinator, f.journal, f.record, () -> true)); f.acknowledge();
            var results = new ArrayList<Boolean>();
            assertTrue(EscrowGovernedCleanup.prune(f.coordinator, f.journal, f.arm, () -> true, results::add)); f.coordinator.tick(0);
            try { assertTrue(reached.await(5, java.util.concurrent.TimeUnit.SECONDS)); f.journal.put(f.record); }
            finally { resume.countDown(); }
            pump(f.coordinator, 1, () -> !results.isEmpty()); assertEquals(List.of(false), results);
            assertEquals(List.of(f.arm), f.data.ledger().armedFlights());
        } finally { resume.countDown(); }
    }

    @Test void unknownPruneRetainsItsExactEntryAndCapacity() throws Exception {
        var fail = new AtomicBoolean(true);
        try (var f = new Fixture(EscrowFlightTables.Outcome.HANDED_OFF, (staged, target) -> {
            if (fail.get()) { throw new java.io.IOException("injected F8 ambiguous replacement"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }, cut -> { })) {
            assertTrue(EscrowGovernedCleanup.remove(f.coordinator, f.journal, f.record, () -> true)); f.acknowledge();
            var results = new ArrayList<Boolean>();
            assertTrue(EscrowGovernedCleanup.prune(f.coordinator, f.journal, f.arm, () -> true, results::add));
            pump(f.coordinator, 0, f.writer::unknown); assertTrue(results.isEmpty());
            assertEquals(List.of(f.arm), f.data.ledger().armedFlights());
            fail.set(false); pump(f.coordinator, 201, () -> !results.isEmpty());
            assertEquals(List.of(true), results); assertTrue(f.data.ledger().armedFlights().isEmpty());
        }
    }

    @Test void failedJournalReplacementNeverAuthorizesPruning() throws Exception {
        try (var f = new Fixture(EscrowFlightTables.Outcome.CANCELLED, CheckedSavedDataFile::atomicMove, cut -> { })) {
            assertTrue(EscrowGovernedCleanup.remove(f.coordinator, f.journal, f.record, () -> true));
            var method = RocketTransferSavedData.class.getDeclaredMethod("saveChecked", Path.class, CheckedSavedDataPolicy.class);
            method.setAccessible(true);
            method.invoke(f.journal, f.journalFile, new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS,
                    (staged, target) -> { throw new java.io.IOException("injected F8 journal replacement"); }));
            assertFalse(f.journal.actionAuthorityAvailable()); assertFalse(EscrowGovernedCleanup.absent(f.coordinator, f.journal, f.arm));
            assertFalse(EscrowGovernedCleanup.prune(f.coordinator, f.journal, f.arm, () -> true, ignored -> fail("Unacknowledged cleanup callback")));
            assertEquals(List.of(f.arm), f.data.ledger().armedFlights());
        }
    }

    @ParameterizedTest @EnumSource(value = EscrowFlightTables.Outcome.class, names = {"HANDED_OFF", "CANCELLED"})
    void completedPhaseObservationsRequireExactTerminalAndCheckedAbsence(EscrowFlightTables.Outcome outcome) throws Exception {
        try (var f = new Fixture(outcome, CheckedSavedDataFile::atomicMove, cut -> { })) {
            var phases = new EscrowGovernedPhases();
            var captured = f.record.nativeTransfer().orElseThrow().armed();
            assertTrue(EscrowGovernedPhaseTracking.completedCapture(captured, f.arm));
            assertFalse(EscrowGovernedPhaseTracking.completedCapture(captured, captured));
            assertFalse(EscrowGovernedPhaseTracking.completedCapture(f.arm, f.arm));
            assertFalse(phases.releaseCompleted(f.coordinator, f.journal, f.arm));
            assertTrue(EscrowGovernedCleanup.remove(f.coordinator, f.journal, f.record, () -> true));
            assertFalse(phases.releaseCompleted(f.coordinator, f.journal, f.arm));
            f.acknowledge();
            assertFalse(phases.releaseCompleted(f.coordinator, f.journal, captured));
            assertTrue(phases.releaseCompleted(f.coordinator, f.journal, f.arm));
            f.journal.put(f.record);
            assertFalse(phases.releaseCompleted(f.coordinator, f.journal, f.arm));
            for (String field : List.of("ledger", "o", "src", "t", "d")) {
                var changed = f.arm.encode(); changed.putUUID(field, GovernedTransferFixture.id(999));
                assertFalse(EscrowGovernedPhaseTracking.completedCapture(captured, EscrowFlightTables.Armed.decode(changed)), field);
            }
            for (String field : List.of("epoch", "serial", "g")) {
                var changed = f.arm.encode(); changed.putLong(field, changed.getLong(field) + 1);
                assertFalse(EscrowGovernedPhaseTracking.completedCapture(captured, EscrowFlightTables.Armed.decode(changed)), field);
            }
            for (String field : List.of("binding", "launch", "source", "dest")) {
                var changed = f.arm.encode(); var digest = changed.getByteArray(field); digest[0] ^= 1; changed.putByteArray(field, digest);
                assertFalse(EscrowGovernedPhaseTracking.completedCapture(captured, EscrowFlightTables.Armed.decode(changed)), field);
            }
            var logicalChanged = f.arm.encode(); logicalChanged.putUUID("l", GovernedTransferFixture.id(999));
            logicalChanged.getCompound("j").putUUID("rocket_id", GovernedTransferFixture.id(999));
            assertFalse(EscrowGovernedPhaseTracking.completedCapture(captured, EscrowFlightTables.Armed.decode(logicalChanged)));
            var guardChanged = f.arm.encode(); var digest = guardChanged.getCompound("j").getByteArray("journal_digest"); digest[0] ^= 1;
            guardChanged.getCompound("j").putByteArray("journal_digest", digest);
            assertFalse(EscrowGovernedPhaseTracking.completedCapture(captured, EscrowFlightTables.Armed.decode(guardChanged)));
            var changed = f.arm.encode(); changed.putUUID("a", GovernedTransferFixture.id(999)); changed.putLong("release", 1);
            assertFalse(EscrowGovernedPhaseTracking.completedCapture(captured, EscrowFlightTables.Armed.decode(changed)));
        }
    }

    private final class Fixture implements AutoCloseable {
        final RocketTransferRecord record;
        final EscrowFlightTables.Armed arm;
        final ResourceEscrowSavedData data;
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final Path file, journalFile;
        final EscrowLedgerWriter writer;
        final EscrowCoordinator coordinator;
        Fixture(EscrowFlightTables.Outcome outcome, CheckedSavedDataFile.Committer committer,
                java.util.function.Consumer<String> cut) throws Exception {
            record = outcome == EscrowFlightTables.Outcome.HANDED_OFF ? EscrowGovernedArrivalStateTest.record() : GovernedTransferFixture.create().record();
            var original = record.nativeTransfer().orElseThrow().armed(); var root = EscrowGovernedDispatchTest.root(record);
            boolean destination = outcome == EscrowFlightTables.Outcome.HANDED_OFF;
            root = EscrowFlightAuthority.finish(root, original, outcome, destination ? original.destination() : original.source(),
                    destination ? 0 : original.epoch(), (destination ? record.destinationFlightData() : record.sourceFlightData()).fuel().resourceSignature(),
                    EscrowInvariant.rocket(destination ? original.destination() : original.source(), original.logical(), original.owner(),
                            EscrowAdapterIds.ROCKET, destination ? record.destinationSnapshot() : record.sourceSnapshot()));
            data = ResourceEscrowSavedData.load(EscrowCodec.encode(root)); arm = root.armedFlights().get(0);
            var base = java.nio.file.Files.createDirectory(directory.resolve(java.util.UUID.randomUUID().toString()));
            file = base.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); journalFile = base.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW).replace(file, () -> EscrowCodec.encode(data.ledger()));
            journal.put(record); acknowledge(); writer = new EscrowLedgerWriter(file, root.writerGeneration(), committer, cut);
            assertTrue(writer.baseline(root)); coordinator = new EscrowCoordinator(data, writer);
        }
        void acknowledge() throws Exception { EscrowArmedRestartBoundaryTest.acknowledge(journal, journalFile); }
        @Override public void close() { coordinator.close(); writer.close(); journal.stop(); }
    }

    private static void pump(EscrowCoordinator coordinator, long start, BooleanSupplier done) throws Exception {
        for (long tick = start; tick < start + 1_000 && !done.getAsBoolean(); tick++) { coordinator.tick(tick); Thread.sleep(2); }
        assertTrue(done.getAsBoolean(), "Expected bounded checked F8 completion");
    }
}
