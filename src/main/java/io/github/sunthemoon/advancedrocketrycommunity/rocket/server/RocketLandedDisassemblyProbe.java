package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;

/**
 * W85 section 7 S2: the gated {@code LANDED_DISASSEMBLY:<boundary>} pauses on the server thread. The harness kills
 * the process during the fixed wait. K1 pauses on the journal writer instead (RocketJournalFailurePolicy). Absent
 * properties leave every ordinary server untouched, and no pause forces a save.
 */
final class RocketLandedDisassemblyProbe {
    static final String PROPERTY = "advancedrocketrycommunity.landedDisassemblyProbe";
    private static final Set<String> SERVER_BOUNDARIES = Set.of("K2", "K4", "K5");

    private RocketLandedDisassemblyProbe() { }

    static void pause(MinecraftServer server, String boundary, RocketTransferRecord record) {
        String selected = System.getProperty(PROPERTY);
        if (selected == null || !SERVER_BOUNDARIES.contains(boundary)
                || !selected.equals("LANDED_DISASSEMBLY:" + boundary)) {
            return;
        }
        if (!Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks") || !server.isDedicatedServer()
                || !server.isSameThread()) {
            throw new IllegalStateException("Landed disassembly probe requires a gated dedicated test server");
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LANDED_DISASSEMBLY_PAUSED boundary={} transfer={} logical={}",
                boundary, record.transferId(), record.logicalRocketId());
        try {
            TimeUnit.SECONDS.sleep(20L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        AdvancedRocketryCommunity.LOGGER.warn("ARCE_LANDED_DISASSEMBLY_PROBE_EXPIRED boundary={} transfer={}",
                boundary, record.transferId());
        throw new IllegalStateException("External landed disassembly kill did not arrive within the fixed wait");
    }
}
