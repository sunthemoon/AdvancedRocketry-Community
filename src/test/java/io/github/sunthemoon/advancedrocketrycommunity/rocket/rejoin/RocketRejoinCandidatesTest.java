package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class RocketRejoinCandidatesTest {
    @Test void fixedOrderAndExactBudgetOutsideFootprint() {
        var min = new RocketRejoinRoot.Position(-5, 60, -4);
        var max = new RocketRejoinRoot.Position(5, 75, 4);
        var columns = RocketRejoinCandidates.columns(min, max, 80, 90);
        assertEquals(64, columns.size()); assertEquals(64, new HashSet<>(columns).size());
        assertEquals(columns, RocketRejoinCandidates.columns(min, max, 80, 90));
        for (int i = 0; i < 32; i++) {
            var c = columns.get(i); assertEquals(RocketRejoinRoot.Target.PAD, c.target());
            assertTrue(c.x() < -5 || c.x() > 5 || c.z() < -4 || c.z() > 4);
        }
        for (int i = 32; i < 64; i++) { assertEquals(RocketRejoinRoot.Target.OVERWORLD_SPAWN, columns.get(i).target()); }
    }
    @Test void ticketBudgetIsPerPlayerAndGlobalAndReleasesIdempotently() {
        var budget = new RocketRejoinTicketBudget();
        for (int i = 0; i < 8; i++) {
            var player = new java.util.UUID(0, i);
            assertTrue(budget.acquire(player, "pad")); assertTrue(budget.acquire(player, "spawn"));
            assertTrue(budget.acquire(player, "pad")); assertFalse(budget.acquire(player, "third"));
        }
        assertEquals(16, budget.size()); assertFalse(budget.acquire(new java.util.UUID(1, 1), "pad"));
        budget.release(new java.util.UUID(0, 0)); budget.release(new java.util.UUID(0, 0));
        assertEquals(14, budget.size()); assertTrue(budget.acquire(new java.util.UUID(1, 1), "pad"));
    }
}
