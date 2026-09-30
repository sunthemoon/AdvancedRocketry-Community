package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * ADR-040/041 actor rule for station management: a connected player, issuing the command
 * from their own source, standing in the station's committed region with their chunk loaded,
 * who is its owner or a permission-level-2 operator.
 */
final class StationLocalActor {
    private StationLocalActor() {
    }

    /** Checks the actor against the station at their position; {@code null} means allowed. */
    static StationManagementCode check(
            StationAccessService access,
            boolean authorityAvailable,
            boolean localPlayer,
            boolean inSpace,
            boolean chunkLoaded,
            Optional<StationState> stationAtPosition,
            UUID actorId,
            boolean operator
    ) {
        if (!authorityAvailable) {
            return StationManagementCode.AUTHORITY_UNAVAILABLE;
        }
        if (!localPlayer) {
            return StationManagementCode.NOT_LOCAL_PLAYER;
        }
        if (!inSpace) {
            return StationManagementCode.NOT_IN_SPACE;
        }
        if (!chunkLoaded) {
            return StationManagementCode.CHUNK_UNLOADED;
        }
        if (stationAtPosition.isEmpty()) {
            return StationManagementCode.NOT_IN_STATION;
        }
        return access.allowed(stationAtPosition.orElseThrow(), actorId, operator, StationAccessAction.MANAGE_STATION)
                ? null : StationManagementCode.UNAUTHORIZED;
    }

    /** A connected player (not a FakePlayer or detached entity) who issued the command directly. */
    static boolean localPlayer(MinecraftServer server, ServerPlayer player, boolean issuedByPlayer) {
        return issuedByPlayer && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    static Located locate(StationAccessService access, ServerPlayer player, boolean issuedByPlayer) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return new Located(StationManagementCode.AUTHORITY_UNAVAILABLE, null, null);
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        ServerLevel level = player.serverLevel();
        boolean inSpace = level.dimension().equals(CelestialIds.SPACE_LEVEL)
                && server.getLevel(CelestialIds.SPACE_LEVEL) == level;
        BlockPos position = player.blockPosition();
        // hasChunkAt only inspects already-loaded chunks; it never loads or generates one.
        boolean chunkLoaded = inSpace && level.hasChunkAt(position);
        Optional<StationState> station = inSpace
                ? data.findAt(position.getX(), position.getZ())
                : Optional.empty();
        StationManagementCode code = check(access, data.operational() && !data.updatesQuarantined(),
                localPlayer(server, player, issuedByPlayer), inSpace, chunkLoaded, station,
                player.getUUID(), player.hasPermissions(2));
        return new Located(code, station.orElse(null), data);
    }

    static StationManagementResult audit(String kind, String action, ServerPlayer player,
                                         StationManagementResult result) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_STATION_{} action={} code={} station={} actor={} width={} gravity_milli={}",
                kind,
                action,
                result.code(),
                result.station().map(StationState::stationId).orElse(null),
                player.getUUID(),
                result.station().map(station -> station.region().width()).orElse(null),
                result.station().map(station -> station.environment().gravityMilli()).orElse(null)
        );
        return result;
    }

    record Located(StationManagementCode code, StationState station, StationRegistrySavedData authority) {
    }
}
