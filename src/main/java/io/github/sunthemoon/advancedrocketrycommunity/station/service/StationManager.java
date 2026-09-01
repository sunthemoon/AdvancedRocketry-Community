package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Lifecycle-owned station creation, mutation, protection, and recovery authority. */
public final class StationManager implements StationOperationService {
    private static final int MAX_DENIAL_TRACKING_PLAYERS = 128;
    private static final int DENIAL_NOTICE_TICKS = 20;

    private final StationPlatformGenerator platforms = new StationPlatformGenerator();
    private final StationCreationService creation = new StationCreationService(platforms);
    private final StationAccessService access = new StationAccessService();
    private final Map<UUID, Long> lastDenialNotice = new LinkedHashMap<>();

    @Override
    public StationCreationResult createForPlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = player.getServer();
        if (server == null) {
            return StationCreationResult.failure(StationCreationCode.SERVICE_UNAVAILABLE);
        }
        ResourceLocation orbitBody;
        if (player.level().dimension() == Level.OVERWORLD) {
            orbitBody = CelestialIds.EARTH_ID;
        } else if (player.level().dimension().equals(CelestialIds.MOON_LEVEL)) {
            orbitBody = CelestialIds.MOON_ID;
        } else {
            return StationCreationResult.failure(StationCreationCode.INVALID_SOURCE);
        }
        String name = player.getScoreboardName() + " Station";
        return creation.create(server, player.getUUID(), name, orbitBody, true);
    }

    public StationCreationResult createForOperator(
            MinecraftServer server,
            UUID ownerId,
            String name,
            ResourceLocation orbitBody
    ) {
        return creation.create(server, ownerId, name, orbitBody, false);
    }

    public Optional<StationState> station(MinecraftServer server, UUID stationId) {
        return StationRegistrySavedData.get(server).find(stationId);
    }

    public List<StationState> stations(MinecraftServer server) {
        return StationRegistrySavedData.get(server).stations();
    }

    public StationState invite(
            MinecraftServer server,
            UUID actorId,
            boolean operator,
            UUID stationId,
            UUID playerId
    ) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationState state = requireAllowed(data, stationId, actorId, operator,
                StationAccessAction.MANAGE_MEMBERS);
        StationState updated = data.invite(state.stationId(), playerId);
        data.flush(server);
        auditAccess("invite", stationId, actorId, playerId, true);
        return updated;
    }

    public StationState accept(MinecraftServer server, UUID stationId, UUID playerId) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationState state = data.find(stationId).orElseThrow(
                () -> new IllegalArgumentException("Station is missing")
        );
        if (!state.invitations().contains(playerId)) {
            throw new SecurityException("Station invitation is missing");
        }
        StationState updated = data.acceptInvitation(stationId, playerId);
        data.flush(server);
        auditAccess("accept", stationId, playerId, playerId, true);
        return updated;
    }

    public StationState decline(MinecraftServer server, UUID stationId, UUID playerId) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationState state = data.find(stationId).orElseThrow(
                () -> new IllegalArgumentException("Station is missing")
        );
        if (!state.invitations().contains(playerId)) {
            throw new SecurityException("Station invitation is missing");
        }
        StationState updated = data.declineInvitation(stationId, playerId);
        data.flush(server);
        auditAccess("decline", stationId, playerId, playerId, true);
        return updated;
    }

    public StationState removeMember(
            MinecraftServer server,
            UUID actorId,
            boolean operator,
            UUID stationId,
            UUID memberId
    ) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationState state = requireAllowed(data, stationId, actorId, operator,
                StationAccessAction.MANAGE_MEMBERS);
        StationState updated = data.removeMember(state.stationId(), memberId);
        data.flush(server);
        auditAccess("remove", stationId, actorId, memberId, true);
        return updated;
    }

    public StationState transferOwnership(
            MinecraftServer server,
            UUID actorId,
            boolean operator,
            UUID stationId,
            UUID ownerId
    ) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationState state = requireAllowed(data, stationId, actorId, operator,
                StationAccessAction.TRANSFER_OWNERSHIP);
        StationState updated = data.transferOwnership(state.stationId(), ownerId);
        data.flush(server);
        auditAccess("transfer", stationId, actorId, ownerId, true);
        return updated;
    }

    public StationState delete(
            MinecraftServer server,
            UUID actorId,
            boolean operator,
            UUID stationId,
            String confirmation
    ) {
        if (!"confirm".equals(confirmation)) {
            throw new IllegalArgumentException("Station deletion requires the literal confirmation token");
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationState state = requireAllowed(data, stationId, actorId, operator,
                StationAccessAction.DELETE);
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        if (space == null) {
            throw new IllegalStateException("Fixed Space Level is unavailable");
        }
        if (hasRocketAuthority(server, space, state)) {
            throw new IllegalStateException("Station deletion is blocked while a rocket authority uses its region");
        }
        AdvancedRocketryCommunity.LOGGER.warn(
                "ARCE_STATION_DELETE_BACKUP station={} owner={} members={} cell={},{} region={},{},{},{}",
                state.stationId(),
                state.ownerId(),
                state.sortedMembers(),
                state.cell().x(),
                state.cell().z(),
                state.region().minimumX(),
                state.region().minimumZ(),
                state.region().maximumX(),
                state.region().maximumZ()
        );
        StationPlatformResult removed = platforms.removeTemplate(space, state.cell());
        StationState deleted = data.delete(stationId).orElseThrow(
                () -> new IllegalStateException("Station changed during deletion")
        );
        data.flush(server);
        AdvancedRocketryCommunity.LOGGER.warn(
                "ARCE_STATION_DELETED station={} actor={} inspected={} removed={} chunks={}",
                stationId, actorId, removed.inspected(), removed.changed(), removed.chunksLoaded()
        );
        return deleted;
    }

    private static boolean hasRocketAuthority(
            MinecraftServer server,
            ServerLevel space,
            StationState station
    ) {
        AABB region = new AABB(
                station.region().minimumX(),
                space.getMinBuildHeight(),
                station.region().minimumZ(),
                (double) station.region().maximumX() + 1.0D,
                space.getMaxBuildHeight(),
                (double) station.region().maximumZ() + 1.0D
        );
        if (!space.getEntitiesOfClass(RocketEntity.class, region).isEmpty()) {
            return true;
        }
        return RocketTransferSavedData.get(server).entries().stream().anyMatch(record ->
                usesStationRegion(station, record.sourceSnapshot())
                        || usesStationRegion(station, record.destinationSnapshot())
        );
    }

    private static boolean usesStationRegion(
            StationState station,
            RocketStructureSnapshot snapshot
    ) {
        return snapshot.sourceDimension().equals(CelestialIds.SPACE_LEVEL.location())
                && overlaps(station, RocketRegion.fromSnapshot(snapshot));
    }

    private static boolean overlaps(StationState station, RocketRegion rocket) {
        return station.region().minimumX() <= rocket.maximum().x()
                && station.region().maximumX() >= rocket.minimum().x()
                && station.region().minimumZ() <= rocket.maximum().z()
                && station.region().maximumZ() >= rocket.minimum().z();
    }

    public int recoverReservations(MinecraftServer server) {
        return creation.recoverReservations(server);
    }

    public void onServerStarted(ServerStartedEvent event) {
        int recovered = recoverReservations(event.getServer());
        if (recovered > 0) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_STATION_STARTUP_RECOVERY reservations={}", recovered
            );
        }
    }

    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getPlayer() instanceof ServerPlayer player) {
            protect(event, level, event.getPos(), player);
        }
    }

    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
            for (BlockSnapshot snapshot : multiple.getReplacedBlockSnapshots()) {
                if (!allowedBuild(level, snapshot.getPos(), player)) {
                    deny(event, level, snapshot.getPos(), player);
                    return;
                }
            }
            return;
        }
        protect(event, level, event.getPos(), player);
    }

    public void clear() {
        lastDenialNotice.clear();
    }

    private void protect(
            BlockEvent event,
            ServerLevel level,
            BlockPos position,
            ServerPlayer player
    ) {
        if (!allowedBuild(level, position, player)) {
            deny(event, level, position, player);
        }
    }

    private boolean allowedBuild(ServerLevel level, BlockPos position, ServerPlayer player) {
        if (!level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return true;
        }
        Optional<StationState> station = StationRegistrySavedData.get(level.getServer())
                .findAt(position.getX(), position.getZ());
        return station.isEmpty() || access.allowed(
                station.orElseThrow(),
                player.getUUID(),
                player.hasPermissions(2),
                StationAccessAction.BUILD
        );
    }

    private void deny(
            BlockEvent event,
            ServerLevel level,
            BlockPos position,
            ServerPlayer player
    ) {
        event.setCanceled(true);
        long time = level.getGameTime();
        Long previous = lastDenialNotice.put(player.getUUID(), time);
        if (lastDenialNotice.size() > MAX_DENIAL_TRACKING_PLAYERS) {
            UUID eldest = lastDenialNotice.keySet().iterator().next();
            lastDenialNotice.remove(eldest);
        }
        if (previous == null || time - previous >= DENIAL_NOTICE_TICKS) {
            player.displayClientMessage(
                    Component.translatable("station.advancedrocketrycommunity.access.denied"),
                    true
            );
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_STATION_ACCESS_DENIED player={} position={} dimension={}",
                    player.getUUID(), position.toShortString(), level.dimension().location()
            );
        }
    }

    private StationState requireAllowed(
            StationRegistrySavedData data,
            UUID stationId,
            UUID actorId,
            boolean operator,
            StationAccessAction action
    ) {
        StationState state = data.find(stationId).orElseThrow(
                () -> new IllegalArgumentException("Station is missing")
        );
        if (!access.allowed(state, actorId, operator, action)) {
            auditAccess(action.name().toLowerCase(java.util.Locale.ROOT), stationId, actorId, actorId, false);
            throw new SecurityException("Station action is unauthorized");
        }
        return state;
    }

    private static void auditAccess(
            String action,
            UUID stationId,
            UUID actorId,
            UUID subjectId,
            boolean allowed
    ) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_STATION_ACCESS action={} station={} actor={} subject={} allowed={}",
                action, stationId, actorId, subjectId, allowed
        );
    }
}
