package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The station bridge is cleared when a server stops. On an integrated server the mod instance
 * outlives the world, so the next world's start must reinstall it, or the deployment item reports
 * the service as unavailable in every later session.
 */
final class StationRuntimeLifecycleTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void aSecondServerStartReinstallsTheBridge() {
        StationManager manager = new StationManager(new CelestialCatalogManager());
        manager.onServerAboutToStart(null);
        assertTrue(StationRuntime.installed(), "The first start did not install the bridge");
        StationRuntime.clear(); // What the mod's ServerStoppedEvent handler does.
        assertFalse(StationRuntime.installed());
        manager.onServerAboutToStart(null);
        assertTrue(StationRuntime.installed(), "The next world's start did not reinstall the bridge");
        StationRuntime.clear();
    }
}
