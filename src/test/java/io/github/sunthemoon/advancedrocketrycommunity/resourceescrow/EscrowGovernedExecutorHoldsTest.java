package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
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

    @Test void diagnosticsAreBoundedAndReleasedWithTheJournalOrRuntime() {
        var holds = new EscrowGovernedExecutorHolds(notice -> { });
        for (int index = 0; index < 64; index++) { holds.hold(new UUID(0, index), EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT); }
        assertThrows(IllegalArgumentException.class, () -> holds.hold(new UUID(0, 64), EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT));
        holds.retain(java.util.List.of()); assertFalse(holds.held(new UUID(0, 0)));
        holds.hold(new UUID(0, 64), EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT);
        holds.clear(); assertFalse(holds.held(new UUID(0, 64)));
    }
}
