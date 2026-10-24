package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowPreparationLeaseTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    private final UUID logical = GovernedTransferFixture.id(800), transfer = GovernedTransferFixture.id(801);

    @Test void aPeerMayPublishTheProspectiveSerialWhileThisSourceProofWaits() throws Exception {
        var journal = new RocketTransferSavedData();
        try {
            acknowledge(journal);
            var lease = new EscrowFlightJournal.PreparationLease(journal, logical, transfer, 1);
            assertTrue(lease.getAsBoolean());
            journal.put(GovernedTransferFixture.create(1, 1, false).record());
            assertTrue(lease.getAsBoolean(), "A peer consumed a prospective serial, not this launch's assigned serial");
            assertTrue(lease.capture(2));
            assertTrue(lease.getAsBoolean());
        } finally { journal.stop(); }
    }

    @Test void theActualSerialIsCheckedAtCaptureAndUntilReplacement() throws Exception {
        var journal = new RocketTransferSavedData();
        try {
            acknowledge(journal);
            var lease = new EscrowFlightJournal.PreparationLease(journal, logical, transfer, 1);
            assertTrue(lease.capture(2));
            journal.put(GovernedTransferFixture.create(2, 1, false).record());
            assertFalse(lease.getAsBoolean(), "The assigned serial must remain exclusive before replacement");
            assertFalse(lease.capture(2));
            assertFalse(lease.capture(0));
            assertFalse(lease.capture(-1));
        } finally { journal.stop(); }
    }

    @Test void anUnacknowledgedRemovalStillNamesTheAssignedSerial() throws Exception {
        var journal = new RocketTransferSavedData();
        try {
            acknowledge(journal);
            var lease = new EscrowFlightJournal.PreparationLease(journal, logical, transfer, 1);
            var peer = GovernedTransferFixture.create(2, 1, false).record();
            journal.put(peer); acknowledge(journal); journal.remove(peer.transferId());
            assertFalse(lease.capture(2));
            acknowledge(journal);
            assertTrue(lease.capture(2));
        } finally { journal.stop(); }
    }

    @Test void captureCannotHideAnInterveningChangeOfTheLogicalRocket() throws Exception {
        var journal = new RocketTransferSavedData(); var own = GovernedTransferFixture.create().record();
        try {
            acknowledge(journal);
            var lease = new EscrowFlightJournal.PreparationLease(journal, own.logicalRocketId(), transfer, 1);
            journal.put(own); acknowledge(journal); journal.remove(own.transferId()); acknowledge(journal);
            assertFalse(lease.capture(2), "Reacquiring a serial must not renew the original absence lease");
        } finally { journal.stop(); }
    }

    @Test void theInitialPreflightAndJournalLifecycleStillRefuse() throws Exception {
        var journal = new RocketTransferSavedData();
        try {
            assertFalse(new EscrowFlightJournal.PreparationLease(journal, logical, transfer, 1).getAsBoolean());
            acknowledge(journal);
            var peer = GovernedTransferFixture.create().record(); journal.put(peer); acknowledge(journal);
            assertFalse(new EscrowFlightJournal.PreparationLease(journal, logical, transfer, 1).getAsBoolean());
            assertFalse(new EscrowFlightJournal.PreparationLease(journal, logical, peer.transferId(), 2).getAsBoolean());
            assertFalse(new EscrowFlightJournal.PreparationLease(journal, peer.logicalRocketId(), transfer, 2).getAsBoolean());
            var lease = new EscrowFlightJournal.PreparationLease(journal, logical, transfer, 2);
            assertTrue(lease.capture(3)); journal.stop(); assertFalse(lease.getAsBoolean());
        } finally { journal.stop(); }
    }

    private void acknowledge(RocketTransferSavedData journal) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
    }
}
