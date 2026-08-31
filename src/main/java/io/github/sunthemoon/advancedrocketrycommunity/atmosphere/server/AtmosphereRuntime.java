package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Narrow lifecycle bridge used by ticking BlockEntities; world state remains manager-owned. */
public final class AtmosphereRuntime {
    private static volatile AtmosphereManager manager;

    private AtmosphereRuntime() {
    }

    public static void install(AtmosphereManager installedManager) {
        manager = Objects.requireNonNull(installedManager, "installedManager");
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
