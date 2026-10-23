package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.ChunkPos;

/** Test-only bridge for the retirement port's real monitor admission checks. */
public final class EscrowPinTestAccess {
    private EscrowPinTestAccess() { }
    public static Runnable protect(EscrowSaveMonitor monitor, String level, ChunkPos slot) {
        return monitor.captureWatch(level, true, slot, new AtomicBoolean(true))::release;
    }
}
