package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lease review LR2: F6 keeps L's checked journal hold across its barrier callback, not a quiet journal. */
class EscrowGovernedTerminalJournalTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    private Path file() { return directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()); }

    private static RocketTransferRecord unrelated() {
        return EscrowPreF2CancellationTest.record(GovernedTransferFixture.id(900), GovernedTransferFixture.id(901),
                GovernedTransferFixture.id(902));
    }

    @Test void anUnrelatedPendingChangeDuringTheBarrierKeepsTheProofButLsOwnChangeDiscardsIt() throws Exception {
        var record = GovernedTransferFixture.create().record(); var other = unrelated(); var journal = new RocketTransferSavedData();
        try {
            journal.put(record); journal.put(other); EscrowArmedRestartBoundaryTest.acknowledge(journal, file());
            // The F6 capture takes L's hold before its barrier starts; the callback checks the same hold.
            var hold = new EscrowGovernedTerminal.JournalHold(journal, record);
            assertTrue(hold.holds(journal));

            journal.put(other.destinationSpawned(GovernedTransferFixture.id(903)));
            assertFalse(journal.actionAuthorityAvailable(), "The journal-wide gate that LR2 replaces refuses here");
            assertTrue(hold.holds(journal), "Another rocket's pending phase change discarded L's healthy F6 proof");
            assertTrue(new EscrowGovernedTerminal.JournalHold(journal, record).holds(journal), "F6 entry waited for another rocket");
            journal.remove(other.transferId());
            assertTrue(hold.holds(journal), "Another rocket's pending removal discarded L's healthy F6 proof");
            EscrowArmedRestartBoundaryTest.acknowledge(journal, file());
            assertTrue(hold.holds(journal));
            assertFalse(hold.holds(new RocketTransferSavedData()), "Another journal is not L's checked journal");

            var spawned = record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination());
            journal.put(spawned);
            assertFalse(hold.holds(journal), "L's own pending change kept the F6 proof");
            assertFalse(new EscrowGovernedTerminal.JournalHold(journal, spawned).holds(journal), "F6 entry over L's unacknowledged change");
            EscrowArmedRestartBoundaryTest.acknowledge(journal, file());
            assertFalse(hold.holds(journal), "The superseded record's F6 proof came back after acknowledgement");
            assertTrue(new EscrowGovernedTerminal.JournalHold(journal, spawned).holds(journal));
        } finally { journal.stop(); }
    }

    @Test void aFailedWriteOrAStoppedJournalDiscardsTheProof() throws Exception {
        var record = GovernedTransferFixture.create().record(); var other = unrelated(); var journal = new RocketTransferSavedData();
        try {
            journal.put(record); journal.put(other); EscrowArmedRestartBoundaryTest.acknowledge(journal, file());
            var hold = new EscrowGovernedTerminal.JournalHold(journal, record);
            journal.remove(other.transferId());
            var method = RocketTransferSavedData.class.getDeclaredMethod("saveChecked", Path.class, CheckedSavedDataPolicy.class);
            method.setAccessible(true);
            method.invoke(journal, file(), new CheckedSavedDataPolicy(ManagedSavedDataType.ROCKET_TRANSFERS,
                    (staged, target) -> { throw new java.io.IOException("injected journal failure during the F6 barrier"); }));
            assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, journal.checkedWriteResult().outcome());
            assertFalse(hold.holds(journal), "A failed journal write kept the F6 proof");
            assertFalse(new EscrowGovernedTerminal.JournalHold(journal, record).holds(journal));
            EscrowArmedRestartBoundaryTest.acknowledge(journal, file());
            var after = new EscrowGovernedTerminal.JournalHold(journal, record); assertTrue(after.holds(journal));
            journal.stop();
            assertFalse(after.holds(journal), "A stopped journal kept the F6 proof");
        } finally { journal.stop(); }
    }
}
