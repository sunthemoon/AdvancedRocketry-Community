package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketRequiredCostsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void displayVectorAcceptsOnlyUnavailableOrBoundedCosts() {
        assertEquals(-1, RocketRequiredCosts.UNKNOWN.primary());
        assertEquals(new RocketRequiredCosts(0, 2048000), new RocketRequiredCosts(0, RocketFlightLimits.MAX_TRAVEL_FUEL));
        for (long invalid : new long[] {-2, Long.MIN_VALUE, 2048001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new RocketRequiredCosts(invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> new RocketRequiredCosts(0, invalid));
        }
    }
    @Test void overflowingBankIsUnavailableWithoutHidingTheOtherBank() {
        var catalog = new RocketFluidFuelCatalog(Map.of(
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.BIPROPELLANT_FUEL, ModIdentity.id("hydrogen")),
                new RocketFluidFuelCatalog.Efficiency(1, 1000),
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.OXIDIZER, ModIdentity.id("oxygen")),
                new RocketFluidFuelCatalog.Efficiency(1, 1)));
        var fuel = RocketFuelState.empty(descriptor(true)).withFluidBank(false, ModIdentity.id("hydrogen"), null, 1000)
                .withFluidBank(true, ModIdentity.id("oxygen"), null, 1000);
        var result = RocketPropulsionQuoter.quote(fuel, plan(2048000), catalog);
        assertEquals(RocketFlightPlanCode.INSUFFICIENT_CAPACITY, result.code()); assertNull(result.plan());
        assertEquals(0, result.requiredFuel(), "Existing scalar refusal contract changed");
        assertEquals(new RocketRequiredCosts(-1, 2048000), result.requiredCosts());
    }
    @Test void waivedFuelIsKnownZeroEvenWhenBothBanksAreEmpty() {
        var result = RocketPropulsionQuoter.quote(RocketFuelState.empty(descriptor(false)), plan(101), RocketFluidFuelCatalog.defaults());
        assertTrue(result.success()); assertEquals(new RocketRequiredCosts(0, 0), result.requiredCosts());
        assertEquals(101, result.requiredFuel()); assertEquals(0, result.plan().propulsionDebit().primary());
    }
    @Test void successfulDisplayUsesTheExecutableVectorWithoutBecomingIt() {
        var fuel = RocketFuelState.empty(descriptor(true)).withFluidBank(false, ModIdentity.id("hydrogen"), null, 1000)
                .withFluidBank(true, ModIdentity.id("oxygen"), null, 1000);
        var result = RocketPropulsionQuoter.quote(fuel, plan(101), RocketFluidFuelCatalog.defaults());
        assertTrue(result.success());
        assertEquals(result.plan().propulsionDebit().primary(), result.requiredCosts().primary());
        assertEquals(result.plan().propulsionDebit().oxidizer(), result.requiredCosts().oxidizer());
        var refused = RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_FUEL, 0)
                .withRequiredCosts(new RocketRequiredCosts(0, 0));
        assertFalse(refused.success()); assertNull(refused.plan());
    }
    @Test void screenUsesLocalizedUnavailableButKeepsKnownZeroDistinct() {
        var text = assertInstanceOf(TranslatableContents.class, RocketMonitorView.costText(-1).getContents());
        assertEquals("screen.advancedrocketrycommunity.monitor.flight.-1", text.getKey());
        for (boolean chinese : new boolean[] {false, true}) {
            assertEquals(chinese ? "不可用" : "Unavailable",
                    io.github.sunthemoon.advancedrocketrycommunity.datagen.V180RocketMonitorData.language(chinese).get(text.getKey()));
        }
        assertEquals("0", RocketMonitorView.costText(0).getString());
        assertEquals("2048000", RocketMonitorView.costText(2048000).getString());
    }
    private static RocketPropulsion descriptor(boolean requiresFuel) {
        return new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1000, 1000, 1, 1,
                RocketPropulsion.Settings.capture(1, 1, 1, true, requiresFuel, true));
    }
    private static RocketFlightPlan plan(long cost) {
        return new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.MOON.dimensionId(), cost, 10);
    }
}
