package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Narrow bridge from the warp core block entity to the warp service. Installed once at mod
 * construction and kept across integrated-server sessions; the service clears its own state at stop.
 */
public final class StationWarpRuntime {
    private static volatile StationWarpService service;

    private StationWarpRuntime() {
    }

    public static void install(StationWarpService installed) {
        service = Objects.requireNonNull(installed, "installed");
    }

    public static Optional<StationWarpService> service() {
        return Optional.ofNullable(service);
    }

    public static int receiveEnergy(ServerLevel level, BlockPos position, int maxReceive, boolean simulate) {
        StationWarpService current = service;
        return current == null ? 0 : current.receiveEnergy(level, position, maxReceive, simulate);
    }
}
