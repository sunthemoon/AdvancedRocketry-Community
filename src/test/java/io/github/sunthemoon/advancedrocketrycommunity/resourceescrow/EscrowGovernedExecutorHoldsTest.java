package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedExecutorHoldsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void repeatedPlacementObservationsWarnOncePerFlightEvenAcrossRecovery() {
        var notices = new ArrayList<EscrowGovernedExecutorHolds.Notice>();
        var holds = new EscrowGovernedExecutorHolds(notices::add); var flight = UUID.randomUUID();
        for (int tick = 0; tick < 2000; tick++) { holds.hold(flight, EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT); }
        assertTrue(holds.held(flight)); assertEquals(1, notices.size());
        holds.healthy(flight); assertFalse(holds.held(flight));
        holds.hold(flight, EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT);
        assertTrue(holds.held(flight)); assertEquals(1, notices.size());
        holds.healthy(flight); holds.hold(flight, EscrowGovernedExecutorHolds.Reason.SOURCE_ENVELOPE);
        assertEquals(2, notices.size());
        holds.hold(UUID.randomUUID(), EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT);
        assertEquals(3, notices.size());
    }

    @Test void aLandedCleanedRocketMovedAfterwardsLogsNoPlacementHoldAndKeepsNoEntry() {
        var notices = new ArrayList<EscrowGovernedExecutorHolds.Notice>();
        var holds = new EscrowGovernedExecutorHolds(notices::add);
        var retired = GovernedTransferFixture.create().record();
        var journal = new RocketTransferSavedData();
        // F8 removed T. A stale binding observes the landed, idle rocket after something moved it.
        assertFalse(EscrowGovernedFrame.placementHold(journal, retired, current -> fail("A retired T was classified"), hold(holds, journal)));
        assertTrue(notices.isEmpty()); assertFalse(holds.held(retired.transferId()));
        // Between F7 and F8 the journal still holds T, but its route is CLEANUP, not ACTIVE.
        journal.put(retired);
        for (var route : List.of(EscrowGovernedDispatch.Route.CLEANUP, EscrowGovernedDispatch.Route.HOLD)) {
            assertFalse(EscrowGovernedFrame.placementHold(journal, retired, current -> route, hold(holds, journal)));
        }
        assertTrue(notices.isEmpty()); assertFalse(holds.held(retired.transferId()));
        // Inside the ACTIVE route a persistent placement is still one WARN and one hold, for the journal's instance of T.
        var later = retired.destinationSpawned(retired.nativeTransfer().orElseThrow().armed().destination());
        var advanced = new RocketTransferSavedData(); advanced.put(later);
        assertTrue(EscrowGovernedFrame.placementHold(advanced, retired, current -> {
            assertSame(later, current); return EscrowGovernedDispatch.Route.ACTIVE;
        }, hold(holds, advanced)));
        assertEquals(List.of(new EscrowGovernedExecutorHolds.Notice(retired.transferId(),
                EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT)), notices);
        assertTrue(holds.held(retired.transferId()));
        // After F8 the entry leaves with the journal, and a later move logs nothing new.
        advanced.remove(retired.transferId()); holds.retain(advanced.entries());
        assertFalse(EscrowGovernedFrame.placementHold(advanced, later, current -> fail("A cleaned T was classified"), hold(holds, advanced)));
        assertEquals(1, notices.size()); assertFalse(holds.held(retired.transferId()));
    }

    private static java.util.function.Consumer<RocketTransferRecord> hold(EscrowGovernedExecutorHolds holds, RocketTransferSavedData journal) {
        return current -> { holds.retain(journal.entries()); holds.hold(current.transferId(), EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT); };
    }

    @Test void diagnosticsAreBoundedAndReleasedWithTheJournalOrRuntime() {
        var holds = new EscrowGovernedExecutorHolds(notice -> { });
        for (int index = 0; index < 64; index++) { holds.hold(new UUID(0, index), EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT); }
        assertThrows(IllegalArgumentException.class, () -> holds.hold(new UUID(0, 64), EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT));
        holds.retain(java.util.List.of()); assertFalse(holds.held(new UUID(0, 0)));
        holds.hold(new UUID(0, 64), EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT);
        holds.clear(); assertFalse(holds.held(new UUID(0, 64)));
    }
}
