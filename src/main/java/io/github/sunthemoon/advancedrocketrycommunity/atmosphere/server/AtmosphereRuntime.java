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

    /** Fail-closed, loaded-position landing query, using the same exposure and equipment policy as ticking. */
    public static boolean rejoinSurvivable(ServerLevel level, BlockPos eye, net.minecraft.server.level.ServerPlayer player) {
        AtmosphereManager current = manager;
        if (current == null || !level.getServer().isSameThread() || !level.hasChunkAt(eye)) { return false; }
        var air = current.breathabilityAt(level, eye);
        if (air == io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState.PENDING) { return false; }
        if (player.isCreative() || player.isSpectator()) { return true; }
        if (air != io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState.BREATHABLE
                && !SuitEquipmentRuntime.hasRejoinAir(player)) { return false; }
        return io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalExposure.hazards(
                current.surfaceExposure(level),
                io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalProtection.read(player),
                current.controlledAt(level, eye),
                io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.PlayerEnvironmentalService.directSunlight(level, eye)) == 0;
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

    /** Server-thread read of the Level's supplied volumes after its last atmosphere tick. */
    public static java.util.Map<io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeId,
            io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.AtmosphereVolume> suppliedVolumes(ServerLevel level) {
        AtmosphereManager current = manager;
        return current == null || !level.getServer().isSameThread() ? java.util.Map.of()
                : current.suppliedVolumes(level.dimension());
    }

    /** Read-only diagnostics for commands and integration tests. */
    public static Optional<AtmosphereLevelMetrics> metrics(ServerLevel level) {
        AtmosphereManager current = manager;
        return current == null ? Optional.empty() : current.metrics(level.dimension());
    }
    /** Server-thread diagnostics used to verify physical boundary commit ordering. */
    public static Optional<io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState>
            breathabilityAt(ServerLevel level, BlockPos position) {
        AtmosphereManager current = manager;
        return current == null || !level.getServer().isSameThread() || !level.hasChunkAt(position)
                ? Optional.empty() : Optional.of(current.breathabilityAt(level, position));
    }
}
