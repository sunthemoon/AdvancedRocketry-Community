package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EscrowFloorsTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void countReservationAt255RefusesTheSecondDistinctKeyButCoalescesOneKey() {
        var floors = syntheticFloors(255);
        var first = named(1, 1, 0); var second = named(2, 2, 0); var chain = named(2, 1, 1);
        var one = ledger(List.of(first), floors);
        EscrowFloors.preflight(one); assertEquals(new EscrowFloors.Usage(255, 1), EscrowFloors.usage(one, List.of()));
        assertThrows(IllegalArgumentException.class, () -> EscrowFloors.preflight(ledger(List.of(first, second), floors)));
        var coalesced = ledger(List.of(first, chain), floors); EscrowFloors.preflight(coalesced);
        assertEquals(256, EscrowFloors.usage(coalesced, List.of()).total());
        var full = ledger(List.of(), syntheticFloors(256));
        assertThrows(IllegalArgumentException.class, () -> EscrowFloors.preflight(ledger(List.of(first), full.floors())));
        assertEquals(256, EscrowFloors.usage(full, List.of()).used());
    }

    @Test void failedAdmissionDoesNotIssueASequenceOrLoseReservations() {
        var root = ledger(List.of(), syntheticFloors(255));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("ledger.dat"), 1);
             var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.prepareTest(named(3, 1, 0), ignored -> { });
            assertEquals(new EscrowFloors.Usage(255, 1), coordinator.floorUsage());
            assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(named(4, 2, 0), ignored -> { }));
            assertEquals(4, coordinator.nextSequence()); assertEquals(256, coordinator.floorUsage().total());
        }
    }

    @Test void restartRebuildsDurableReservationsAndNoneGuardsReserveNothing() {
        var op = named(1, 1, 0); var ledger = ledger(List.of(op), List.of());
        var restored = EscrowCodec.decode(EscrowCodec.encode(ledger));
        assertEquals(new EscrowFloors.Usage(0, 1), EscrowFloors.usage(restored, List.of()));
        assertEquals(new EscrowFloors.Usage(0, 0), EscrowFloors.usage(EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)), List.of()));
        assertEquals(new EscrowFloors.Usage(0, 1), EscrowFloors.usage(ledger, List.of(named(2, 1, 1))));
    }

    @Test void l2InstallsTheLatestProvedPrefixFloorAndNeverInstallsANoneFloor() {
        var first = named(1, 1, 0); var second = named(2, 1, 1); var root = ledger(List.of(first, second), List.of());
        var state = new EscrowPrefix.State(root.id(), 2, second.destination().afterSignature(), List.of());
        var partial = EscrowFloors.retire(root, Set.of(first.id()), Map.of(first.destination().key(), state));
        assertEquals(1, partial.records().size()); assertEquals(1, partial.floors().size());
        assertEquals(2, partial.floors().get(0).getLong("resource_epoch")); assertEquals(2, partial.floors().get(0).getLong("highest_seq"));
        var complete = EscrowFloors.retire(partial, Set.of(second.id()), Map.of(second.destination().key(), state));
        assertEquals(1, complete.floors().size()); assertEquals(0, EscrowFloors.usage(complete, List.of()).reserved());
        assertEquals(complete, EscrowCodec.decode(EscrowCodec.encode(complete)));
        var none = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1));
        assertTrue(EscrowFloors.retire(none, Set.of(none.records().get(0).id()), Map.of()).floors().isEmpty());
    }

    @Test void collectionNeedsAbsenceAndRetainsEveryQueuedAndDurableReference() {
        var op = named(1, 1, 0); var floor = floor(op); var idle = ledger(List.of(), List.of(floor));
        assertEquals(1, EscrowFloors.collect(idle, List.of(), ignored -> false).floors().size());
        assertEquals(0, EscrowFloors.collect(idle, List.of(), ignored -> true).floors().size());
        assertEquals(1, EscrowFloors.collect(idle, List.of(op), ignored -> true).floors().size());
        assertEquals(1, EscrowFloors.collect(ledger(List.of(op), List.of(floor)), List.of(), ignored -> true).floors().size());
        var noneFloor = EscrowTestFixtures.floor(44);
        assertEquals(1, EscrowFloors.collect(ledger(List.of(), List.of(noneFloor)), List.of(), ignored -> true).floors().size());
    }

    @Test void collectionWaitsForBothJournalAndLedgerAcknowledgementsAndFailureKeepsTheFloor() throws Exception {
        var root = ledger(List.of(), List.of(floor(named(1, 1, 0))));
        for (boolean fail : List.of(false, true)) {
            var journal = new RocketTransferSavedData();
            var failing = new java.util.concurrent.atomic.AtomicBoolean(fail);
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            var directory = java.nio.file.Files.createDirectory(temp.resolve("case-" + fail));
            var file = directory.resolve(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            var ledgerIo = new AwaitedLedgerIo();
            try (var writer = ledgerIo.writer(file, root.writerGeneration(), (staged, target) -> {
                if (failing.get()) { throw new java.io.IOException("Injected floor collection failure"); }
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.atomicMove(staged, target);
            }, ignored -> { }); var coordinator = new EscrowCoordinator(data, writer)) {
                var unchecked = new RocketTransferSavedData(); coordinator.collectFloors(unchecked);
                assertFalse(data.isDirty()); assertEquals(1, coordinator.floorUsage().used());
                journal.setDirty(); journal.save(directory.resolve(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName()).toFile());
                assertFalse(journal.acknowledgedCurrent(), "Autosave must not wait for the journal writer");
                RocketJournalTestAccess.awaitWriter(journal);
                assertTrue(journal.acknowledgedCurrent()); coordinator.collectFloors(journal);
                assertEquals(1, coordinator.floorUsage().used(), "Queued collection is not retirement");
                long tick = 1;
                try {
                    tick = ledgerIo.tickUntil(coordinator::tick, tick, () -> writer.idle() || writer.unavailable());
                    assertEquals(fail, writer.unavailable());
                    assertEquals(fail ? 1 : 0, coordinator.floorUsage().used());
                } finally {
                    // Resolve injected ambiguous IO before closing the real writer; no test leaves a stuck drain.
                    failing.set(false);
                    ledgerIo.tickUntil(coordinator::tick, tick, writer::idle);
                }
                assertTrue(writer.idle()); assertEquals(0, coordinator.floorUsage().used());
            } finally { journal.stop(); }
        }
    }

    private static EscrowOperation named(long sequence, long rocket, long epoch) {
        var op = EscrowTestFixtures.operation(sequence, rocket, epoch);
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), new EscrowGuard(op.guard().rocket(), EscrowTestFixtures.id(9_000 + rocket), 1, op.guard().journalDigest()));
    }
    private static EscrowLedger ledger(List<EscrowOperation> records, List<CompoundTag> floors) {
        return new EscrowLedger(EscrowTestFixtures.id(500), 2, 1, records, floors, List.of());
    }
    private static CompoundTag floor(EscrowOperation op) {
        var tag = EscrowCodec.guard(op.guard()); tag.putInt("schema_version", 1); tag.putUUID("entity_id", op.destination().identity());
        tag.putLong("resource_epoch", op.destination().beforeEpoch()); tag.putString("signature", op.destination().beforeSignature());
        tag.putLong("highest_seq", 1); return tag;
    }
    private static List<CompoundTag> syntheticFloors(int count) {
        var floors = new ArrayList<CompoundTag>();
        for (int i = 0; i < count; i++) {
            var tag = EscrowTestFixtures.floor(10_000 + i); tag.putUUID("transfer_id", EscrowTestFixtures.id(20_000 + i)); floors.add(tag);
        }
        return floors;
    }
}
