package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.ReleaseTestServers;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W332: the gated load-failure seam injects only when its property names the file's type, the release-test hooks are
 * set, the server is dedicated and the complete release-test gate is open. A closed gate refuses without throwing, so the
 * file loads normally; every other input does nothing and never consults the gate.
 */
final class ReleaseTestLoadFailureTest {
    private static final String HOOKS = EscrowTestEndpoints.HOOKS;
    @TempDir Path directory;
    private final ReleaseTestServers servers = new ReleaseTestServers();
    private final Map<String, String> saved = new HashMap<>();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @AfterEach void restore() {
        servers.close();
        saved.forEach((key, value) -> { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } });
    }

    private void property(String key, String value) {
        if (!saved.containsKey(key)) { saved.put(key, System.getProperty(key)); }
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }

    @Test void theDecisionInjectsOnlyWhenEveryInputSelectsTheType() {
        var consulted = new AtomicInteger();
        for (var type : List.of(ManagedSavedDataType.BAY_RECEIPTS, ManagedSavedDataType.RESOURCE_ESCROW)) {
            var other = type == ManagedSavedDataType.BAY_RECEIPTS ? ManagedSavedDataType.RESOURCE_ESCROW : ManagedSavedDataType.BAY_RECEIPTS;
            assertEquals(ReleaseTestLoadFailure.INJECT, ReleaseTestLoadFailure.decide(type.name(), type, true, true, () -> true));
            assertEquals(ReleaseTestLoadFailure.REFUSED, ReleaseTestLoadFailure.decide(type.name(), type, true, true, () -> false));
            // Every other input is inert and never reaches the gate.
            for (String value : new String[] {null, "", other.name(), type.name().toLowerCase(java.util.Locale.ROOT), " " + type.name()}) {
                assertEquals(ReleaseTestLoadFailure.NONE, ReleaseTestLoadFailure.decide(value, type, true, true,
                        () -> { consulted.incrementAndGet(); return true; }), String.valueOf(value));
            }
            assertEquals(ReleaseTestLoadFailure.NONE, ReleaseTestLoadFailure.decide(type.name(), type, false, true,
                    () -> { consulted.incrementAndGet(); return true; }));
            assertEquals(ReleaseTestLoadFailure.NONE, ReleaseTestLoadFailure.decide(type.name(), type, true, false,
                    () -> { consulted.incrementAndGet(); return true; }));
        }
        assertEquals(0, consulted.get(), "an unselected decision consulted the gate");
    }

    @Test void aClosedGateRefusesAndTheFileLoadsWhileAnOpenGateThrows() throws Exception {
        property(ReleaseTestLoadFailure.PROPERTY, "BAY_RECEIPTS");
        property(HOOKS, "true");
        MinecraftServer rcon = servers.server(directory, true);
        MinecraftServer foreign = servers.server(directory, false);
        servers.join(foreign, UUID.randomUUID());
        for (MinecraftServer closed : List.of(rcon, foreign)) {
            assertFalse(EscrowTestEndpoints.enabled(closed));
            assertDoesNotThrow(() -> ReleaseTestLoadFailure.beforeLoad(closed, ManagedSavedDataType.BAY_RECEIPTS),
                    "a closed gate must refuse the injection, not fail the load");
        }
        MinecraftServer open = servers.server(directory, false);
        assertTrue(EscrowTestEndpoints.enabled(open));
        var injected = assertThrows(IllegalStateException.class,
                () -> ReleaseTestLoadFailure.beforeLoad(open, ManagedSavedDataType.BAY_RECEIPTS));
        assertTrue(injected.getMessage().contains(ManagedSavedDataType.BAY_RECEIPTS.dataName()), injected.getMessage());
        assertDoesNotThrow(() -> ReleaseTestLoadFailure.beforeLoad(open, ManagedSavedDataType.RESOURCE_ESCROW),
                "the seam injected into a type its property does not name");
    }

    @Test void withoutThePropertyOrTheHooksNothingHappensEvenWithAnOpenGate() throws Exception {
        MinecraftServer open = servers.server(directory, false);
        property(HOOKS, "true");
        property(ReleaseTestLoadFailure.PROPERTY, null);
        assertDoesNotThrow(() -> ReleaseTestLoadFailure.beforeLoad(open, ManagedSavedDataType.BAY_RECEIPTS));
        assertDoesNotThrow(() -> ReleaseTestLoadFailure.beforeLoad(open, ManagedSavedDataType.RESOURCE_ESCROW));
        property(ReleaseTestLoadFailure.PROPERTY, "RESOURCE_ESCROW");
        property(HOOKS, "false");
        assertDoesNotThrow(() -> ReleaseTestLoadFailure.beforeLoad(open, ManagedSavedDataType.RESOURCE_ESCROW),
                "the seam injected without the release-test hooks");
    }
}
