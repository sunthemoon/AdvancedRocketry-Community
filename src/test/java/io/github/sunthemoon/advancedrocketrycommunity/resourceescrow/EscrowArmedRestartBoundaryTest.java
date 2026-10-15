package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A0 durable-state boundary only: synthetic envelope hashes are not native F1 or F6 proofs. */
class EscrowArmedRestartBoundaryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void replacedArmSurvivesTwoCheckedReloadsWithoutItsCallbackOrPreparedJournal() throws Exception {
        exercise(false);
    }

    @Test void acknowledgedArmSurvivesTwoCheckedReloadsBeforePreparedJournal() throws Exception {
        exercise(true);
    }

    private void exercise(boolean publishCallback) throws Exception {
        var data = ResourceEscrowSavedData.create();
        var initial = data.ledger();
        var launch = EscrowFlightAuthorityTest.launch(initial, 1);
        var escrowFile = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var journalFile = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        // The no-record state must itself be acknowledged; decoding an empty journal is insufficient.
        var journal = new RocketTransferSavedData();
        try {
            acknowledge(journal, journalFile);
            assertNull(journal.acknowledgedGuard(launch.logical()).orElseThrow().transfer());
        } finally { journal.stop(); }

        var replaced = new CountDownLatch(1);
        var callbacks = new AtomicInteger();
        var writer = new EscrowLedgerWriter(escrowFile, 0, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target);
            replaced.countDown();
        });
        try (var coordinator = new EscrowCoordinator(data, writer)) {
            assertTrue(coordinator.flightWrite(launch.logical(),
                    (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation),
                    () -> true, committed -> {
                        assertTrue(committed);
                        callbacks.incrementAndGet();
                    }));
            coordinator.tick(0);
            assertTrue(replaced.await(5, TimeUnit.SECONDS), "Expected the bounded writer replacement");
            assertEquals(0, callbacks.get());
            assertEquals(initial, coordinator.ledger());
            if (publishCallback) {
                for (int i = 1; i <= 1_000 && callbacks.get() == 0; i++) {
                    coordinator.tick(i);
                    Thread.sleep(2);
                }
                assertEquals(1, callbacks.get());
            } else {
                // abort joins IO but deliberately does not publish the queued owner-thread callback.
                coordinator.abort();
            }
        }
        assertEquals(publishCallback ? 1 : 0, callbacks.get());
        var durable = CheckedSavedDataFile.readPayload(escrowFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow();
        for (int restart = 0; restart < 2; restart++) {
            var loaded = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(
                    escrowFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            assertTrue(loaded.operational());
            try (var baseline = new EscrowLedgerWriter(escrowFile, loaded.ledger().writerGeneration())) {
                assertTrue(baseline.baseline(loaded.ledger()));
            }
            var root = loaded.ledger();
            assertEquals(1, root.armedFlights().size());
            var arm = root.armedFlights().get(0);
            assertEquals(EscrowFlightTables.Outcome.ARMED, arm.outcome());
            assertEquals(launch.source(), arm.source());
            assertEquals(launch.destination(), arm.destination());
            assertEquals(launch.sourceEnvelope(), arm.sourceEnvelope());
            assertEquals(launch.destinationEnvelope(), arm.destinationEnvelope());
            assertNull(arm.journal().transfer());
            assertEquals(1, arm.serial());
            assertEquals(1, root.issuedLaunchSerial());
            assertTrue(root.records().isEmpty());
            assertTrue(root.floors().isEmpty());
            assertTrue(root.tombstones().isEmpty());
            assertTrue(root.rocketAuthorities().isEmpty());

            var restartedJournal = RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(
                    journalFile, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
            try {
                assertTrue(restartedJournal.operational());
                assertFalse(restartedJournal.acknowledgedCurrent());
                assertFalse(restartedJournal.acknowledgedLease().getAsBoolean());
                assertTrue(restartedJournal.acknowledgedGuard(launch.logical()).isEmpty());
                acknowledge(restartedJournal, journalFile);
                var guard = restartedJournal.acknowledgedGuard(launch.logical()).orElseThrow();
                assertNull(guard.transfer());
                assertTrue(restartedJournal.acknowledgedLease().getAsBoolean());
                assertTrue(restartedJournal.entries().isEmpty());
                assertTrue(restartedJournal.find(launch.transfer()).isEmpty());
            } finally { restartedJournal.stop(); }

            var source = new EscrowRecoveryFence.Rocket(launch.source(), launch.wrapper().epoch(), false, true);
            for (var action : RocketTransferRecoveryAction.values()) {
                assertEquals("ARMED_AUTHORITY_REQUIRED", EscrowRecoveryFence.reason(root, launch.logical(),
                        launch.transfer(), action, List.of(source), List.of()));
            }
            assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.prune(root, arm));
            assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(root, launch, 2));
            assertEquals(durable, EscrowCodec.encode(root));
            assertEquals(durable, CheckedSavedDataFile.readPayload(
                    escrowFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
        }
    }

    private static void acknowledge(RocketTransferSavedData journal, Path file) throws InterruptedException {
        journal.setDirty();
        journal.save(file.toFile());
        for (int tick = 0; tick < 1_000 && !journal.acknowledgedCurrent(); tick++) {
            journal.pollWriter(tick);
            Thread.sleep(2);
        }
        assertTrue(journal.acknowledgedCurrent(), "Expected exact checked journal acknowledgement");
    }
}
