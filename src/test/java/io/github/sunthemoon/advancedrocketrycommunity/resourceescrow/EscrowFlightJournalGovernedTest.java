package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowFlightJournalGovernedTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void noTransferScansGovernedSerialsAndStillRequiresTheExactCheckedBaseline() throws Exception {
        var record = GovernedTransferFixture.create().record();
        var logical = GovernedTransferFixture.id(800); var intent = GovernedTransferFixture.id(801);
        var journal = new RocketTransferSavedData(); journal.put(record);
        try {
            assertFalse(EscrowFlightJournal.noTransfer(journal, logical, intent, 2).getAsBoolean());
            EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            assertFalse(EscrowFlightJournal.noTransfer(journal, logical, intent, 1).getAsBoolean(), "Another logical rocket names the same serial");
            assertFalse(EscrowFlightJournal.noTransfer(journal, record.logicalRocketId(), intent, 2).getAsBoolean());
            assertFalse(EscrowFlightJournal.noTransfer(journal, logical, record.transferId(), 2).getAsBoolean());
            var lease = EscrowFlightJournal.noTransfer(journal, logical, intent, 2); assertTrue(lease.getAsBoolean());
            journal.put(record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination()));
            // R17 item 3 reverses the journal-wide rule: another rocket's phase change keeps L's absence lease.
            assertTrue(lease.getAsBoolean(), "Another rocket's superseding phase revoked L's captured absence lease");
            assertFalse(EscrowFlightJournal.noTransfer(journal, record.logicalRocketId(), intent, 2).getAsBoolean(),
                    "The changed rocket's own pending phase is not checked");
            assertFalse(EscrowFlightJournal.noTransfer(journal, logical, intent, 1).getAsBoolean(), "Another logical rocket names the same serial");
        } finally { journal.stop(); }
    }

    @Test void checkedRecordDigestBindsPhaseWhileImmutableChecksumIsStable() throws Exception {
        var record = GovernedTransferFixture.create().record(); var journal = new RocketTransferSavedData();
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        try {
            journal.put(record); EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            var previous = journal.acknowledgedGuard(record.logicalRocketId()).orElseThrow();
            String checksum = record.checksum();
            for (var phase : RocketTransferPhase.values()) {
                if (phase == RocketTransferPhase.PREPARED) { continue; }
                record = phase == RocketTransferPhase.DESTINATION_SPAWNED
                        ? record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination()) : record.advance(phase);
                journal.put(record); assertTrue(journal.acknowledgedGuard(record.logicalRocketId()).isEmpty());
                EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
                var next = journal.acknowledgedGuard(record.logicalRocketId()).orElseThrow();
                assertEquals(checksum, record.checksum()); assertNotEquals(previous.recordDigest(), next.recordDigest());
                assertNotEquals(previous.rootDigest(), next.rootDigest()); assertTrue(next.sequence() > previous.sequence()); previous = next;
            }
        } finally { journal.stop(); }
    }

    @Test void noTransferRetainsPeerHistoryButKeepsUnrelatedLaunchAndCleanupChecksIndependent() throws Exception {
        var peer = GovernedTransferFixture.create().record(); var journal = new RocketTransferSavedData();
        var logical = GovernedTransferFixture.id(800); var intent = GovernedTransferFixture.id(801);
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        try {
            EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            var named = EscrowFlightJournal.noTransfer(journal, logical, peer.transferId(), 2);
            var unrelated = EscrowFlightJournal.noTransfer(journal, logical, intent, 2);
            assertTrue(named.getAsBoolean() && unrelated.getAsBoolean());
            journal.put(peer); EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            journal.remove(peer.transferId()); EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            assertFalse(named.getAsBoolean());
            assertTrue(unrelated.getAsBoolean());
            assertTrue(EscrowFlightJournal.noTransfer(journal, logical, peer.transferId(), 2).getAsBoolean());
        } finally { journal.stop(); }
    }

    @Test void reconciliationAndSettlementMembersRetainEachNamedTransferHistory() throws Exception {
        var peer = GovernedTransferFixture.create().record(); var journal = new RocketTransferSavedData();
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var named = member(peer.transferId()); var unrelated = member(GovernedTransferFixture.id(801));
        try {
            EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            assertNotEquals(named.guard().rocket(), peer.logicalRocketId());
            var group = List.of(named, unrelated, named);
            var lease = EscrowFlightJournal.members(journal, group);
            var independent = EscrowFlightJournal.members(journal, List.of(unrelated));
            var noTransfer = EscrowFlightJournal.members(journal, List.of(member(null)));
            assertTrue(lease.getAsBoolean() && independent.getAsBoolean() && noTransfer.getAsBoolean());
            assertFalse(EscrowFlightJournal.members(journal, List.of()).getAsBoolean());
            journal.put(peer); EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            journal.remove(peer.transferId()); EscrowArmedRestartBoundaryTest.acknowledge(journal, file);
            assertFalse(lease.getAsBoolean(), "One changed member's T refuses the whole retained group");
            assertTrue(independent.getAsBoolean()); assertTrue(noTransfer.getAsBoolean());
            assertTrue(EscrowFlightJournal.members(journal, group).getAsBoolean());
        } finally { journal.stop(); }
    }

    private static EscrowOperation member(UUID transfer) {
        var op = EscrowTestFixtures.operation(1);
        var guard = new EscrowGuard(op.guard().rocket(), transfer, op.guard().journalGeneration(), op.guard().journalDigest());
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                op.payload(), op.payloadHash(), op.source(), op.destination(), guard);
    }
}
