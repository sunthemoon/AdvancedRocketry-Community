package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * ADR-040/041 actor rule for station management: a connected player, issuing the command
 * from their own source, standing in the station's committed region with their chunk loaded,
 * who is its owner or a permission-level-2 operator. ADR-065 section 5.1 adds the controller
 * screen as the shared context validator's other entry: the actor is within 8 blocks of the
 * exact loaded controller block, and both stand in the same committed station region.
 */
public final class StationLocalActor {
    private StationLocalActor() {
    }

    /** Checks the actor against the station at their position; {@code null} means allowed. */
    public static StationManagementCode check(
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
    public static boolean localPlayer(MinecraftServer server, ServerPlayer player, boolean issuedByPlayer) {
        return issuedByPlayer && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    public static Located locate(StationAccessService access, ServerPlayer player, boolean issuedByPlayer) {
        return locate(access, player, StationActorContext.command(issuedByPlayer));
    }

    /**
     * The shared context validator. A command uses the player's own position; a controller uses the exact
     * controller block, which must still stand loaded at its server-side position, within
     * {@link StationLimits#CONTROLLER_REACH} of a living, non-spectator connected player in the same Level and
     * the same committed station region. Nothing here loads a chunk.
     */
    public static Located locate(StationAccessService access, ServerPlayer player, StationActorContext context) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return new Located(StationManagementCode.AUTHORITY_UNAVAILABLE, null, null);
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        ServerLevel level = player.serverLevel();
        boolean inSpace = level.dimension().equals(CelestialIds.SPACE_LEVEL)
                && server.getLevel(CelestialIds.SPACE_LEVEL) == level;
        BlockPos position = context.controller().map(StationActorContext.Controller::position)
                .orElse(player.blockPosition());
        // hasChunkAt only inspects already-loaded chunks; it never loads or generates one.
        boolean chunkLoaded = inSpace && level.hasChunkAt(position);
        Optional<StationState> station = inSpace
                ? data.findAt(position.getX(), position.getZ())
                : Optional.empty();
        StationManagementCode code = check(access, data.operational() && !data.updatesQuarantined(),
                localPlayer(server, player, context.issuedByPlayer()), inSpace, chunkLoaded, station,
                player.getUUID(), player.hasPermissions(2));
        if (code == null && context.viaController()
                && !reachesController(player, context.controller().orElseThrow(), station.orElseThrow(), data)) {
            code = StationManagementCode.CONTROLLER_OUT_OF_REACH;
        }
        return new Located(code, station.orElse(null), data);
    }

    /**
     * True when the exact controller block still stands loaded in the player's Level and within
     * {@link StationLimits#CONTROLLER_REACH} of a living, non-spectator player who stands in the same committed
     * station region. Only loaded chunks are read.
     */
    public static boolean reachesController(ServerPlayer player, StationActorContext.Controller controller,
                                            StationState station, StationRegistrySavedData data) {
        ServerLevel level = player.serverLevel();
        BlockPos position = controller.position();
        if (!level.dimension().equals(controller.level()) || !level.hasChunkAt(position)
                || !level.getBlockState(position).is(controller.block())) {
            return false;
        }
        if (!player.isAlive() || player.isSpectator()
                || player.distanceToSqr(Vec3.atCenterOf(position))
                > StationLimits.CONTROLLER_REACH * StationLimits.CONTROLLER_REACH) {
            return false;
        }
        return data.findAt(position.getX(), position.getZ()).map(StationState::stationId)
                .filter(station.stationId()::equals).isPresent()
                && data.findAt(player.getBlockX(), player.getBlockZ()).map(StationState::stationId)
                .filter(station.stationId()::equals).isPresent();
    }

    /**
     * The station whose region holds {@code position} in Space, when the player may look at it: the owner, a
     * member or an operator, while the registry is operational. Read-only screens use it; every change
     * rechecks the full actor rule through {@link #locate}.
     */
    public static Optional<StationState> visibleStationAt(StationAccessService access, ServerPlayer player,
                                                          BlockPos position) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.serverLevel().dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return Optional.empty();
        }
        return StationRegistrySavedData.get(server).findAt(position.getX(), position.getZ())
                .filter(state -> access.allowed(state, player.getUUID(), player.hasPermissions(2),
                        StationAccessAction.VISIT));
    }

    public static StationManagementResult audit(String kind, String action, ServerPlayer player,
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

    public record Located(StationManagementCode code, StationState station, StationRegistrySavedData authority) {
    }
}
