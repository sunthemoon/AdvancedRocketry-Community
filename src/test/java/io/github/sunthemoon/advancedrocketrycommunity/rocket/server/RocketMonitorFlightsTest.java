package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketMonitorFlightsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void primaryCostUsesTheNativeDebitRatherThanAbstractRouteUnits() {
        var plan = new RocketFlightPlan(3, java.util.UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.MOON.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.MOON.dimensionId(),
                null, 317, 0, new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.BodySurface(RocketFlightPlanner.MOON.bodyId()));
        assertEquals(317, RocketMonitorFlights.primaryCost(new RocketFlightPlanResult(RocketFlightPlanCode.SUCCESS, 317, plan)));
        var nativePlan = plan.withPropulsionDebit(new RocketPropulsionDebit(159, 80, 0, "a".repeat(64), "b".repeat(64)));
        assertEquals(159, RocketMonitorFlights.primaryCost(new RocketFlightPlanResult(RocketFlightPlanCode.SUCCESS, 317, nativePlan)));
        assertEquals(159, RocketMonitorFlights.primaryCost(RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_FUEL, 159)));
        var free = plan.withPropulsionDebit(new RocketPropulsionDebit(0, 0, 0, "a".repeat(64), "b".repeat(64)));
        assertEquals(0, RocketMonitorFlights.primaryCost(new RocketFlightPlanResult(RocketFlightPlanCode.SUCCESS, 317, free)));
    }
    @Test void asynchronousDisplayFollowsTheSharedRequestOutcome() {
        var ledger = new io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger();
        var monitor = new RocketMonitorFlights(null, ledger, null);
        var accepted = java.util.UUID.randomUUID(); var refused = java.util.UUID.randomUUID();
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.ENTITY_UNAVAILABLE,
                monitor.completion(accepted));
        ledger.begin(accepted); ledger.begin(refused);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.JOURNAL_PENDING,
                monitor.completion(accepted));
        ledger.finish(accepted, true); ledger.finish(refused, false);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.SUCCESS,
                monitor.completion(accepted));
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                monitor.completion(refused));
    }
    @Test void catalogFingerprintIsIndependentOfMapInsertionOrderButNotContent() {
        var left = JsonParser.parseString("{\"z\":[{\"b\":2,\"a\":1}],\"a\":true}");
        var right = JsonParser.parseString("{\"a\":true,\"z\":[{\"a\":1,\"b\":2}]}");
        assertEquals(RocketMonitorFlights.canonical(left).toString(), RocketMonitorFlights.canonical(right).toString());
        right.getAsJsonObject().addProperty("a", false);
        assertNotEquals(RocketMonitorFlights.canonical(left).toString(), RocketMonitorFlights.canonical(right).toString());
        assertEquals("{\"z\":[{\"b\":2,\"a\":1}],\"a\":true}", left.toString(), "Fingerprinting mutated catalog input");
    }
}
