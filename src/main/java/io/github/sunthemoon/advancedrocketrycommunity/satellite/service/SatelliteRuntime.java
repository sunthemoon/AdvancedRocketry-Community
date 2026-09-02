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

    public static Optional<MissionState> currentMission(MinecraftServer server, SatelliteIdentity identity) {
        SatelliteManager current = manager;
        return current == null
                ? Optional.empty()
                : current.currentMission(server, identity.satelliteId());
    }

    public static int researchBalance(MinecraftServer server, java.util.UUID ownerId) {
        SatelliteManager current = manager;
        return current == null ? 0 : current.researchBalance(server, ownerId);
    }

    public static boolean discovered(MinecraftServer server, ResourceLocation target) {
        SatelliteManager current = manager;
        return current != null && current.discovered(server, target);
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
