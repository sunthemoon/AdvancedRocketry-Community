package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketMonitorCostsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void insufficientPrimaryKeepsBothBankRequirementsWithoutAPlanOrDebit() {
        refused(fuel(10, 1000), RocketFluidFuelCatalog.defaults(), 101, 101);
    }
    @Test void insufficientOxidizerKeepsBothBankRequirementsWithoutAPlanOrDebit() {
        refused(fuel(1000, 10), RocketFluidFuelCatalog.defaults(), 101, 101);
    }
    @Test void absentPrimaryStillShowsTheKnownOxidizerRequirement() {
        refused(fuel(0, 1000), RocketFluidFuelCatalog.defaults(), -1, 101);
    }
    @Test void absentOxidizerIsUnavailableRatherThanFree() {
        refused(fuel(1000, 0), RocketFluidFuelCatalog.defaults(), 101, -1);
    }
    @Test void missingDefinitionDoesNotHideTheOtherBanksKnownRequirement() {
        var catalog = new RocketFluidFuelCatalog(Map.of(
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.OXIDIZER, ModIdentity.id("oxygen")),
                new RocketFluidFuelCatalog.Efficiency(1, 1)));
        refused(fuel(1000, 1000), catalog, -1, 101);
    }
    @Test void noQuoteDoesNotClaimFreeFuel() {
        var values = RocketMonitorView.fuelValues(fuel(1000, 1000), null);
        assertEquals(-1, values.get(4)); assertEquals(-1, values.get(9)); assertEquals(-1, values.get(11));
    }
    private static void refused(RocketFuelState fuel, RocketFluidFuelCatalog catalog, int primary, int oxidizer) {
        var before = io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPropulsionFuelCodec
                .encode(fuel.propulsion().orElseThrow());
        var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.MOON.dimensionId(), 101, 10);
        var result = RocketPropulsionQuoter.quote(fuel, plan, catalog);
        assertEquals(RocketFlightPlanCode.INSUFFICIENT_FUEL, result.code()); assertNull(result.plan());
        var quote = RocketMonitorQuote.planned(plan.destinationTarget(), result, null, "authority");
        assertFalse(quote.ready());
        var values = RocketMonitorView.fuelValues(fuel, quote);
        assertEquals(2, values.get(0));
        assertEquals(primary, values.get(4), "primary required cost");
        assertEquals(oxidizer, values.get(9), "oxidizer required cost");
        assertEquals(primary, values.get(11), "primary summary");
        assertEquals(before, io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPropulsionFuelCodec
                .encode(fuel.propulsion().orElseThrow()));
        assertEquals(List.of(), fuel.committedDebits());
    }
    private static RocketFuelState fuel(int primary, int oxidizer) {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1000, 1000, 1, 1,
                RocketPropulsion.Settings.DEFAULTS);
        var fuel = RocketFuelState.empty(descriptor);
        if (primary > 0) { fuel = fuel.withFluidBank(false, ModIdentity.id("hydrogen"), null, primary); }
        if (oxidizer > 0) { fuel = fuel.withFluidBank(true, ModIdentity.id("oxygen"), null, oxidizer); }
        return fuel;
    }
}
