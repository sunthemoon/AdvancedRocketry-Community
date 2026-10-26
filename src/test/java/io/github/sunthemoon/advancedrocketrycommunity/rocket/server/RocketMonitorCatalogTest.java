package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketMonitorCatalogTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void aFuelOnlySemanticChangeInvalidatesTheEffectiveAuthority() throws Exception {
        var previous = RocketFluidFuelRuntime.capture();
        try {
            publish(catalog(2));
            var before = RocketMonitorFlights.effectiveCatalogAuthority("a".repeat(64));
            publish(catalog(3));
            assertNotEquals(before, RocketMonitorFlights.effectiveCatalogAuthority("a".repeat(64)));
        } finally { publish(previous); }
    }
    @Test void anEquivalentFuelReloadKeepsAuthorityButAPlanetaryChangeDoesNot() throws Exception {
        var previous = RocketFluidFuelRuntime.capture();
        try {
            publish(catalog(2));
            var before = RocketMonitorFlights.effectiveCatalogAuthority("a".repeat(64));
            publish(catalog(2));
            assertEquals(before, RocketMonitorFlights.effectiveCatalogAuthority("a".repeat(64)));
            assertNotEquals(before, RocketMonitorFlights.effectiveCatalogAuthority("b".repeat(64)));
        } finally { publish(previous); }
    }
    private static RocketFluidFuelCatalog catalog(int numerator) {
        return new RocketFluidFuelCatalog(Map.of(new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.MONO,
                ModIdentity.id("rocket_fuel")), new RocketFluidFuelCatalog.Efficiency(numerator, 1)));
    }
    private static void publish(RocketFluidFuelCatalog catalog) throws Exception {
        var field = RocketFluidFuelRuntime.class.getDeclaredField("catalog"); field.setAccessible(true); field.set(null, catalog);
    }
}
