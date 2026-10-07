package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Identity/lifecycle tests only; native GameTests exercise world callbacks and guards. */
final class AtmosphereRuntimeLifecycleTest {
    private AtmosphereManager first;
    private AtmosphereManager second;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @AfterEach
    void disconnectOwnedManagers() {
        if (first != null) {
            AtmosphereRuntime.uninstall(first);
        }
        if (second != null) {
            AtmosphereRuntime.uninstall(second);
        }
    }

    @Test
    void exactOwnerDisconnectsBeforeClearAndAbsentCallsDoNotTouchAWorld() throws Exception {
        first = newManager();
        AtmosphereRuntime.install(first);
        assertSame(first, installed());
        AtmosphereRuntime.uninstall(first);
        first.clear();
        assertNull(installed());
        assertTrue(AtmosphereRuntime.metrics(null).isEmpty());
        AtmosphereRuntime.invalidateDoorBoundary(null, null, null);
        AtmosphereRuntime.observe(null, null);
        AtmosphereRuntime.remove(null, null);
        assertNull(installed());
    }

    @Test
    void staleOwnerDisconnectCannotRemoveAReplacement() throws Exception {
        first = newManager();
        second = newManager();
        AtmosphereRuntime.install(first);
        AtmosphereRuntime.install(second);
        AtmosphereRuntime.uninstall(first);
        assertSame(second, installed());
    }

    @Test
    void secondWorldStartCanReinstallTheSameClearedManager() throws Exception {
        first = newManager();
        AtmosphereRuntime.install(first);
        AtmosphereRuntime.uninstall(first);
        first.clear();
        AtmosphereRuntime.install(first);
        assertSame(first, installed());
    }

    @Test
    void duplicateStopIsIdempotent() throws Exception {
        first = newManager();
        AtmosphereRuntime.install(first);
        AtmosphereRuntime.uninstall(first);
        AtmosphereRuntime.uninstall(first);
        assertNull(installed());
    }

    @Test
    void nullOwnerDoesNotReplaceOrRemoveTheInstallation() throws Exception {
        first = newManager();
        AtmosphereRuntime.install(first);
        assertThrows(NullPointerException.class, () -> AtmosphereRuntime.install(null));
        assertThrows(NullPointerException.class, () -> AtmosphereRuntime.uninstall(null));
        assertSame(first, installed());
    }

    private static AtmosphereManager newManager() {
        return new AtmosphereManager(new CelestialEnvironmentService(new CelestialCatalogManager()));
    }

    private static AtmosphereManager installed() throws ReflectiveOperationException {
        // Read-only test inspection avoids adding a public mutable-manager accessor.
        Field field = AtmosphereRuntime.class.getDeclaredField("manager");
        field.setAccessible(true);
        return (AtmosphereManager) field.get(null);
    }
}
