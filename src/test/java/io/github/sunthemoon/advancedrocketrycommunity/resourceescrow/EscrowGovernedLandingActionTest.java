package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedLandingActionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void finalLandingNeedsItsAcknowledgedRecordAndPermitCannotReplay() throws Exception {
        var record = EscrowGovernedArrivalStateTest.record(); var journal = new RocketTransferSavedData();
        try {
            var root = EscrowGovernedDispatchTest.root(record); journal.put(record);
            assertNull(EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, record));
            acknowledge(journal);
            var authority = EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, record);
            var before = EscrowGovernedArrivalStateTest.descent(record, 500, 80);
            var permit = EscrowGovernedLandingAction.issue(authority, before, 580, () -> true);
            var landed = permit.consume(before::copy);
            assertEquals(RocketFlightState.LANDED, landed.state()); assertEquals(record.destinationFlightData().fuel(), landed.fuel());
            assertThrows(IllegalArgumentException.class, () -> permit.consume(before::copy));
            assertEquals(EscrowFlightTables.Outcome.ARMED, root.armedFlights().get(0).outcome());
        } finally { journal.stop(); }
    }

    @Test void deferredLandingRefusesChangedJournalLedgerBoundaryRidersAndNativeFields() throws Exception {
        for (String fault : java.util.List.of("journal", "ledger", "boundary", "rider", "motion", "stop")) {
            var record = EscrowGovernedArrivalStateTest.record(); var journal = new RocketTransferSavedData();
            try {
                var root = new AtomicReference<>(EscrowGovernedDispatchTest.root(record)); var boundary = new AtomicBoolean(true);
                journal.put(record); acknowledge(journal);
                var authority = EscrowGovernedDispatch.acquire(root::get, () -> true, journal, record);
                var before = EscrowGovernedArrivalStateTest.descent(record, 500, 80);
                var permit = EscrowGovernedLandingAction.issue(authority, before, 580, boundary::get);
                if (fault.equals("journal")) { journal.setDirty(); }
                if (fault.equals("ledger")) { root.set(root.get().generation(root.get().writerGeneration() + 1)); }
                if (fault.equals("boundary")) { boundary.set(false); }
                if (fault.equals("rider")) { before.getList("passengers", 10).add(new net.minecraft.nbt.CompoundTag()); }
                if (fault.equals("motion")) { before.remove("motion"); }
                if (fault.equals("stop")) { journal.stop(); }
                assertThrows(IllegalArgumentException.class, () -> permit.consume(before::copy), fault);
                assertThrows(IllegalArgumentException.class, () -> permit.consume(before::copy), fault);
            } finally { journal.stop(); }
        }
    }

    @Test void aCompletedSerialOrUnfinishedDescentCannotLandAgain() throws Exception {
        var record = EscrowGovernedArrivalStateTest.record(); var journal = new RocketTransferSavedData();
        try {
            var root = new AtomicReference<>(EscrowGovernedDispatchTest.root(record)); journal.put(record); acknowledge(journal);
            var authority = EscrowGovernedDispatch.acquire(root::get, () -> true, journal, record);
            var unfinished = EscrowGovernedArrivalStateTest.descent(record, 500, 79);
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedLandingAction.issue(authority, unfinished, 579, () -> true));
            root.set(EscrowGovernedDispatchTest.arm(root.get(), tag -> tag.putString("outcome", "HANDED_OFF")));
            var terminal = EscrowGovernedDispatch.acquire(root::get, () -> true, journal, record);
            var ground = EscrowGovernedArrivalStateTest.descent(record, 500, 80);
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedLandingAction.issue(terminal, ground, 580, () -> true));
        } finally { journal.stop(); }
    }

    private void acknowledge(RocketTransferSavedData journal) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
    }
}
