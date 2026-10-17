package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A0 checked writers with synthetic E digests; native source storage has separate tests. */
class EscrowPreF2CancellationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void replacementAndCallbackCutsCancelAcrossTwoRestarts(boolean callback) throws Exception { restart(callback, false); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void writtenTRefusesPreF2CancellationAcrossTwoRestarts(boolean callback) throws Exception { restart(callback, true); }

    @Test void stopBeforeArmedReplacementRetainsNoSerialOrFlightAcrossTwoRestarts() throws Exception {
        Path ledgerFile = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        Path journalFile = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var root = ResourceEscrowSavedData.create().ledger().generation(1);
        io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy
                .atomic(ManagedSavedDataType.RESOURCE_ESCROW).replace(ledgerFile, () -> EscrowCodec.encode(root));
        var launch = EscrowFlightAuthorityTest.launch(root, 1);
        var journal = new RocketTransferSavedData();
        EscrowArmedRestartBoundaryTest.acknowledge(journal, journalFile); journal.stop();
        var beforeMove = new CountDownLatch(1); var release = new CountDownLatch(1); var callbacks = new AtomicInteger();
        var writer = new EscrowLedgerWriter(ledgerFile, 1, (staged, target) -> {
            beforeMove.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) { throw new java.io.IOException("Before-replacement cut did not release"); }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
            }
            throw new java.io.IOException("Injected stop before ARMED replacement");
        });
        assertTrue(writer.baseline(root));
        var coordinator = new EscrowCoordinator(ResourceEscrowSavedData.load(EscrowCodec.encode(root)), writer);
        try {
            assertTrue(coordinator.flightWrite(launch.logical(), (before, generation) -> EscrowFlightAuthority.arm(before, launch, generation),
                    () -> true, committed -> callbacks.incrementAndGet()));
            coordinator.tick(10); assertTrue(beforeMove.await(5, TimeUnit.SECONDS));
            assertEquals(root, EscrowCodec.decode(CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
        } finally { release.countDown(); coordinator.abort(); }
        assertEquals(0, callbacks.get());
        for (int restart = 0; restart < 2; restart++) {
            var loaded = EscrowCodec.decode(CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            try (var baseline = new EscrowLedgerWriter(ledgerFile, loaded.writerGeneration())) { assertTrue(baseline.baseline(loaded)); }
            assertEquals(root, loaded); assertTrue(loaded.armedFlights().isEmpty()); assertEquals(0, loaded.issuedLaunchSerial());
            var resumedJournal = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(journalFile, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
            try {
                assertFalse(EscrowFlightJournal.noTransfer(resumedJournal, launch.logical(), launch.transfer(), 1).getAsBoolean());
                EscrowArmedRestartBoundaryTest.acknowledge(resumedJournal, journalFile);
                assertTrue(EscrowFlightJournal.noTransfer(resumedJournal, launch.logical(), launch.transfer(), 1).getAsBoolean());
                assertTrue(resumedJournal.entries().isEmpty());
            } finally { resumedJournal.stop(); }
        }
    }

    private void restart(boolean callback, boolean prepared) throws Exception {
        Path ledgerFile = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        Path journalFile = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var data = ResourceEscrowSavedData.create(); var launch = EscrowFlightAuthorityTest.launch(data.ledger(), 1);
        var journal = new RocketTransferSavedData();
        EscrowArmedRestartBoundaryTest.acknowledge(journal, journalFile); journal.stop();
        var moved = new CountDownLatch(1); var callbacks = new AtomicInteger();
        try (var coordinator = new EscrowCoordinator(data, new EscrowLedgerWriter(ledgerFile, 0, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target); moved.countDown();
        }))) {
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation),
                    () -> true, committed -> { assertTrue(committed); callbacks.incrementAndGet(); }));
            coordinator.tick(0); assertTrue(moved.await(5, TimeUnit.SECONDS));
            if (callback) { await(coordinator, callbacks); } else { coordinator.abort(); }
        }
        assertEquals(callback ? 1 : 0, callbacks.get());
        if (prepared) {
            // A checked startup baseline can replace a lost ARMED callback before F2 writes T.
            var durable = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            try (var baseline = new EscrowLedgerWriter(ledgerFile, durable.ledger().writerGeneration())) { assertTrue(baseline.baseline(durable.ledger())); }
            var afterArm = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(journalFile, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
            try {
                afterArm.put(record(launch.transfer(), launch.logical(), launch.source()));
                EscrowArmedRestartBoundaryTest.acknowledge(afterArm, journalFile);
            } finally { afterArm.stop(); }
        }
        for (int restart = 0; restart < 2; restart++) {
            var loaded = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            var writer = new EscrowLedgerWriter(ledgerFile, loaded.ledger().writerGeneration());
            assertTrue(writer.baseline(loaded.ledger()));
            var resumedJournal = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(journalFile, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
            try (var coordinator = new EscrowCoordinator(loaded, writer)) {
                var root = coordinator.ledger(); var arm = root.armedFlights().get(0);
                assertFalse(EscrowFlightJournal.noTransfer(resumedJournal, arm.logical(), arm.transfer(), arm.serial()).getAsBoolean());
                EscrowArmedRestartBoundaryTest.acknowledge(resumedJournal, journalFile);
                var noTransfer = EscrowFlightJournal.noTransfer(resumedJournal, arm.logical(), arm.transfer(), arm.serial());
                assertEquals(!prepared, noTransfer.getAsBoolean());
                boolean eligible = EscrowPreF2Cancellation.eligible(root, arm, launch.sourceEnvelope(), noTransfer);
                assertEquals(!prepared && restart == 0, eligible);
                if (eligible) {
                    callbacks.set(0);
                    assertTrue(coordinator.flightWrite(arm.logical(), (before, generation) -> {
                        assertTrue(EscrowPreF2Cancellation.eligible(before, arm, launch.sourceEnvelope(), noTransfer));
                        return EscrowFlightAuthority.finish(before, arm, EscrowFlightTables.Outcome.CANCELLED,
                                launch.source(), launch.wrapper().epoch(), launch.signature(), launch.invariant());
                    }, noTransfer, committed -> { assertTrue(committed); callbacks.incrementAndGet(); }));
                    await(coordinator, callbacks);
                }
                var terminal = coordinator.ledger().armedFlights().get(0);
                assertEquals(prepared ? EscrowFlightTables.Outcome.ARMED : EscrowFlightTables.Outcome.CANCELLED, terminal.outcome());
                assertEquals(1, terminal.serial()); assertEquals(1, coordinator.ledger().issuedLaunchSerial());
                assertEquals(launch.source(), terminal.source()); assertEquals(launch.destination(), terminal.destination());
                assertEquals(launch.sourceEnvelope(), terminal.sourceEnvelope());
                assertEquals(launch.wrapper().epoch(), terminal.epoch());
                assertEquals(prepared ? 1 : 0, resumedJournal.entries().size());
            } finally { resumedJournal.stop(); }
        }
    }

    @Test void changedSourceAndUnacknowledgedOrSupersededJournalNeverAuthorizeCancellation() throws Exception {
        var data = ResourceEscrowSavedData.create(); var launch = EscrowFlightAuthorityTest.launch(data.ledger(), 1);
        var root = EscrowFlightAuthority.arm(data.ledger(), launch, 1); var arm = root.armedFlights().get(0);
        var journal = new RocketTransferSavedData();
        try {
            assertFalse(EscrowPreF2Cancellation.eligible(root, arm, launch.sourceEnvelope(),
                    EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial())));
            EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            var lease = EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial());
            assertTrue(EscrowPreF2Cancellation.eligible(root, arm, launch.sourceEnvelope(), lease));
            assertFalse(EscrowPreF2Cancellation.eligible(root, arm, EscrowTestFixtures.hash(999), lease));
            journal.put(record(launch.transfer(), launch.logical(), launch.source()));
            assertFalse(lease.getAsBoolean());
            assertFalse(EscrowPreF2Cancellation.eligible(root, arm, launch.sourceEnvelope(), lease));
        } finally { journal.stop(); }
    }

    @Test void transferIdUnderAnotherLogicalRocketAlsoRefusesNoT() throws Exception {
        var journal = new RocketTransferSavedData(); UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID();
        try {
            journal.put(record(transfer, UUID.randomUUID(), UUID.randomUUID()));
            EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            assertNull(journal.acknowledgedGuard(logical).orElseThrow().transfer());
            assertFalse(EscrowFlightJournal.noTransfer(journal, logical, transfer, 1).getAsBoolean());
        } finally { journal.stop(); }
    }

    private static void await(EscrowCoordinator coordinator, AtomicInteger callbacks) throws InterruptedException {
        for (int tick = 1; tick <= 1_000 && callbacks.get() == 0; tick++) { coordinator.tick(tick); Thread.sleep(2); }
        assertEquals(1, callbacks.get());
    }

    static RocketTransferRecord record(UUID transfer, UUID logical, UUID physical) {
        var source = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(64, 100, 64),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(1, 100, 1_000, 1_000, 1, 1, 1, 0), 0);
        var destination = source.relocated(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(), new RocketPosition(80, 100, 64), 160);
        var fuel = RocketFuelState.empty(1_000).fill(1_000).state();
        var plan = RocketFlightPlanner.plan(source.stats(), fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, transfer, 0).plan();
        var flight = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(), source.sourceOrigin(), 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(), destination.sourceOrigin(), 160);
        return RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), physical, source, destination, flight, arrival, plan.requiredFuel(), 0);
    }
}
