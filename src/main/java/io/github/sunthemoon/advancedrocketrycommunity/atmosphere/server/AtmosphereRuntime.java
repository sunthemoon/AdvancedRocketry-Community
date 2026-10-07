package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Narrow lifecycle bridge used by ticking BlockEntities; world state remains manager-owned. */
public final class AtmosphereRuntime {
    private static volatile AtmosphereManager manager;

    private AtmosphereRuntime() {
    }

    public static synchronized void install(AtmosphereManager installedManager) {
        manager = Objects.requireNonNull(installedManager, "installedManager");
    }

    /** Disconnect before clearing the exact manager; another installation is left intact. */
    public static synchronized void uninstall(AtmosphereManager installedManager) {
        if (manager == Objects.requireNonNull(installedManager, "installedManager")) {
            manager = null;
        }
    }

    /** Synchronous, bounded revocation around both door halves, never a scan or chunk request. */
    public static void invalidateDoorBoundary(ServerLevel level, BlockPos local, BlockPos counterpart) {
        AtmosphereManager current = manager;
        if (current == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread() || server.isStopped()
                || server.getLevel(level.dimension()) != level) {
            return;
        }
        invalidateLoaded(current, level, local);
        if (!local.equals(counterpart)) {
            invalidateLoaded(current, level, counterpart);
        }
    }

    private static void invalidateLoaded(AtmosphereManager current, ServerLevel level, BlockPos position) {
        if (!level.isOutsideBuildHeight(position) && level.hasChunkAt(position)) {
            current.markDirty(level, position);
        }
    }

    public static void observe(ServerLevel level, OxygenVentBlockEntity vent) {
        AtmosphereManager current = manager;
        if (current != null) {
            current.observeVent(level, vent);
        }
    }

    public static void remove(ServerLevel level, BlockPos position) {
        AtmosphereManager current = manager;
        if (current != null) {
            current.removeVent(level, position);
        }
    }

    /** Read-only diagnostics for commands and integration tests. */
    public static Optional<AtmosphereLevelMetrics> metrics(ServerLevel level) {
        AtmosphereManager current = manager;
        return current == null ? Optional.empty() : current.metrics(level.dimension());
    }
}
