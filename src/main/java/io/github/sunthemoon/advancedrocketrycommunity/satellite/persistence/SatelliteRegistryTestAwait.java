package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import java.io.IOException;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/**
 * ADR-074 item 6: a live registry never waits for its writer on a normal running server. Only a GameTestServer
 * (test-only) and a dedicated server started with the release-test property (release-only) may pump the writer on
 * the owner thread; production callers request and continue.
 */
public final class SatelliteRegistryTestAwait {
    static final String PROPERTY = "advancedrocketrycommunity.releaseTestHooks";

    private SatelliteRegistryTestAwait() { }

    public static boolean allowed(MinecraftServer server) {
        return server instanceof GameTestServer || Boolean.getBoolean(PROPERTY) && server.isDedicatedServer();
    }

    static void require(MinecraftServer server) {
        if (server != null && !allowed(server)) {
            throw new IllegalStateException("A live satellite registry never awaits its writer on a normal server");
        }
    }

    /** The committer failure that the release-test and GameTest seam injects on the bound target. */
    static final class InjectedFailure extends IOException {
        InjectedFailure() { super("Release-test injected registry write failure before replacement"); }
    }
}
