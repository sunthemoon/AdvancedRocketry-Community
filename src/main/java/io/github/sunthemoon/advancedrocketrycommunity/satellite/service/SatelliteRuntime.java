package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Narrow lifecycle bridge used by the terminal Forge adapter. */
public final class SatelliteRuntime {
    private static volatile SatelliteManager manager;

    private SatelliteRuntime() {
    }

    public static void install(SatelliteManager installed) {
        manager = Objects.requireNonNull(installed, "installed");
    }

    public static List<ResourceLocation> targets(ResourceLocation definitionId) {
        SatelliteManager current = manager;
        return current == null ? List.of() : current.targets(definitionId);
    }

    public static Optional<SatelliteCatalog> catalog() {
        SatelliteManager current = manager;
        return current == null ? Optional.empty() : current.catalog();
    }

    public static long catalogGeneration() {
        SatelliteManager current = manager;
        return current == null ? 0L : current.catalogGeneration();
    }

    public static SatelliteOperationResult launch(
            ServerPlayer player,
            SatelliteIdentity identity,
            ResourceLocation target
    ) {
        SatelliteManager current = manager;
        return current == null ? unavailable() : current.launch(player, identity, target);
    }

    public static SatelliteOperationResult startMission(
            ServerPlayer player,
            SatelliteIdentity identity,
            ResourceLocation target
    ) {
        SatelliteManager current = manager;
        return current == null ? unavailable() : current.startMission(player, identity, target);
    }

    public static SatelliteOperationResult claim(ServerPlayer player, SatelliteIdentity identity) {
        SatelliteManager current = manager;
        return current == null ? unavailable() : current.claimCurrent(player, identity);
    }

    public static SatelliteOperationResult cancel(ServerPlayer player, SatelliteIdentity identity) {
        SatelliteManager current = manager;
        return current == null
                ? unavailable()
                : current.cancelCurrent(player, identity, player.hasPermissions(2));
    }

    public static SatelliteOperationResult decommission(ServerPlayer player, SatelliteIdentity identity) {
        SatelliteManager current = manager;
        return current == null ? unavailable() : current.decommission(player, identity, player.hasPermissions(2));
    }

    public static Optional<MissionState> currentMission(MinecraftServer server, SatelliteIdentity identity) {
        SatelliteManager current = manager;
        return current == null
                ? Optional.empty()
                : current.currentMission(server, identity.satelliteId());
    }

    public static Optional<io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState> satellite(
            MinecraftServer server, java.util.UUID satelliteId) {
        SatelliteManager current = manager;
        return current == null ? Optional.empty() : current.satellite(server, satelliteId);
    }

    public static int researchBalance(MinecraftServer server, java.util.UUID ownerId) {
        SatelliteManager current = manager;
        return current == null ? 0 : current.researchBalance(server, ownerId);
    }

    /** Monotonic lifetime research, the unlock measure of ADR-049 section 7. */
    public static long lifetimeResearch(MinecraftServer server, java.util.UUID ownerId) {
        SatelliteManager current = manager;
        return current == null ? 0L : current.lifetimeResearch(server, ownerId);
    }

    public static boolean discovered(MinecraftServer server, ResourceLocation target) {
        SatelliteManager current = manager;
        return current != null && current.discovered(server, target);
    }

    public static SatelliteOperationCode requestScan(ServerPlayer player, SatelliteIdentity identity) {
        SatelliteManager current = manager;
        return current == null ? SatelliteOperationCode.SERVER_ERROR : current.requestScan(player, identity);
    }

    /** Whether a client intent from this player may run now; without a running service nothing is limited. */
    public static boolean allowIntent(ServerPlayer player, boolean selection) {
        SatelliteManager current = manager;
        return current == null || current.allowIntent(player, selection);
    }

    public static long[] postTickNanos() {
        SatelliteManager current = manager;
        return current == null ? new long[100] : current.postTickNanos();
    }

    public static long coalescedFlushes() {
        SatelliteManager current = manager;
        return current == null ? 0L : current.coalescedFlushes();
    }

    public static boolean scanRunning(java.util.UUID playerId) {
        SatelliteManager current = manager;
        return current != null && current.scanRunning(playerId);
    }

    public static Optional<io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog> celestialCatalog() {
        SatelliteManager current = manager;
        return current == null ? Optional.empty() : current.celestialCatalog();
    }

    /** The receiver's 20-tick link check; empty when the satellite service is not running. */
    public static Optional<SolarLinks.ReceiverCheck> checkReceiver(
            MinecraftServer server,
            java.util.UUID receiverId,
            List<Optional<SatelliteIdentity>> chips
    ) {
        SatelliteManager current = manager;
        return current == null ? Optional.empty() : Optional.of(current.checkReceiver(server, receiverId, chips));
    }

    public static void registerReceiver(java.util.UUID receiverId,
                                        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level,
                                        net.minecraft.core.BlockPos position) {
        SatelliteManager current = manager;
        if (current != null) {
            current.registerReceiver(receiverId, level, position);
        }
    }

    public static void releaseReceiver(MinecraftServer server, java.util.UUID receiverId) {
        SatelliteManager current = manager;
        if (current != null) {
            current.releaseReceiver(server, receiverId);
        }
    }

    public static SatelliteOperationResult unlink(ServerPlayer player, SatelliteIdentity identity) {
        SatelliteManager current = manager;
        return current == null ? unavailable() : current.unlink(player, identity, player.hasPermissions(2));
    }

    public static void clear() {
        manager = null;
    }

    private static SatelliteOperationResult unavailable() {
        return new SatelliteOperationResult(
                SatelliteOperationCode.SERVER_ERROR,
                false,
                Optional.empty(),
                Optional.empty(),
                0
        );
    }
}
