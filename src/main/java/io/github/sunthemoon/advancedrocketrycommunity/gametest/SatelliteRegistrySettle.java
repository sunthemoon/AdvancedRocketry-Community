package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.TerminalPendingEffects;
import net.minecraft.server.MinecraftServer;

/**
 * W129c GameTest-only drain (ADR-074 item 6): await the registry writer on the owner thread, complete the covered
 * terminal effects and run the discovery replay, a few rounds, so a test asserts only acknowledged state. It requires a
 * GameTestServer; the tick-driven path is covered by its own GameTests.
 */
public final class SatelliteRegistrySettle {
    private SatelliteRegistrySettle() { }

    public static void settle(MinecraftServer server) {
        var data = SatelliteMissionSavedData.get(server);
        for (int round = 0; round < 4; round++) {
            // A failed write leaves no pending request; production retries with the coalesced request or autosave.
            if (data.isDirty()) { data.request(server); }
            data.awaitForTests(server);
            TerminalPendingEffects.complete(server);
            SatelliteRuntime.replayDiscoveriesForTests(server);
        }
        data.awaitForTests(server);
        TerminalPendingEffects.complete(server);
    }
}
