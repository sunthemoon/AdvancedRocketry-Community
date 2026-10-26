package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Stop-command entry followed by a hard kill while the native cargo writer is in flight/UNKNOWN. */
final class EscrowCargoShutdownProbe {
    static final String L1 = "FC_LOAD_G3_L1_INFLIGHT", L2 = "FC_LOAD_G3_L2_UNKNOWN";
    private final String name;
    private volatile boolean ready;
    static boolean selected(boolean hooks, String name, boolean recovery) {
        return hooks && !recovery && (L1.equals(name) || L2.equals(name));
    }
    EscrowCargoShutdownProbe(MinecraftServer server, String name) { EscrowTestEndpoints.requireEnabled(server); this.name = name; }
    void checkpoint(String label, Consumer<String> pause) {
        if (ready && label.equals("G3_STOPPING")) { pause.accept(label); return; }
        if (ready || !(name.equals(L1) && label.equals("K1") || name.equals(L2) && label.equals("WRITE_UNKNOWN"))) { return; }
        ready = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_DRAIN_READY cut={} boundary={}", name, label);
        if (name.equals(L1)) {
            try { java.util.concurrent.TimeUnit.SECONDS.sleep(30); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            throw new IllegalStateException("L1 shutdown forced stop did not arrive");
        }
    }
}
