package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerSeat;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded entity lookup, placement, movement, and temporary chunk activation for transfers. */
final class RocketTransferEntities {
    private static final TicketType<UUID> FLIGHT_TICKET = TicketType.create(
            "arce_rocket_flight",
            Comparator.<UUID>naturalOrder(),
            RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS
    );
    private static final int FLIGHT_TICKET_LEVEL = 2;

    private RocketTransferEntities() {
    }

    static RocketEntity rebuildSource(MinecraftServer server, RocketTransferRecord record) {
        ServerLevel level = level(server, record.sourceSnapshot().sourceDimension());
        if (level == null) {
            return null;
        }
        loadOrigin(level, record.sourceSnapshot());
        RocketEntity source = ModEntities.ROCKET.get().create(level);
        if (source == null) {
            return null;
        }
        source.initializeTransferred(
                record.sourceSnapshot(),
                record.logicalRocketId(),
                record.ownerId(),
                stationarySource(record, level.getGameTime())
        );
        return level.addFreshEntity(source) ? source : null;
    }

    static RocketEntity rebuildDestination(MinecraftServer server, RocketTransferRecord record) {
        ServerLevel level = level(server, record.destinationSnapshot().sourceDimension());
        if (level == null) {
            return null;
        }
        loadOrigin(level, record.destinationSnapshot());
        RocketEntity destination = ModEntities.ROCKET.get().create(level);
        if (destination == null) {
            return null;
        }
        destination.initializeTransferred(
                record.destinationSnapshot(),
                record.logicalRocketId(),
                record.ownerId(),
                record.destinationFlightData()
        );
        positionAtAltitude(destination, record.destinationSnapshot().sourceOrigin());
        return level.addFreshEntity(destination) ? destination : null;
    }

    static RocketEntity authorityEntity(MinecraftServer server, RocketTransferRecord record) {
        return record.phase().destinationAuthoritative()
                ? findDestination(server, record)
                : findSource(server, record);
    }

    static RocketEntity findSource(MinecraftServer server, RocketTransferRecord record) {
        List<RocketEntity> matches = findMatches(server, record, false);
        return matches.isEmpty() ? null : matches.get(0);
    }

    static RocketEntity findDestination(MinecraftServer server, RocketTransferRecord record) {
        List<RocketEntity> matches = findMatches(server, record, true);
        return matches.isEmpty() ? null : matches.get(0);
    }

    static boolean recoveryEntityChunksLoaded(MinecraftServer server, RocketTransferRecord record) {
        boolean sourceLoaded = entityChunkLoaded(
                server,
                record.sourceSnapshot(),
                record.transferId()
        );
        boolean destinationLoaded = entityChunkLoaded(
                server,
                record.destinationSnapshot(),
                record.transferId()
        );
        return sourceLoaded && destinationLoaded;
    }

    static List<RocketEntity> findMatches(
            MinecraftServer server,
            RocketTransferRecord record,
            boolean destination
    ) {
        RocketStructureSnapshot snapshot = destination
                ? record.destinationSnapshot()
                : record.sourceSnapshot();
        ServerLevel level = level(server, snapshot.sourceDimension());
        if (level == null) {
            return List.of();
        }
        loadOrigin(level, snapshot);
        RocketRegion region = RocketRegion.fromSnapshot(snapshot);
        AABB box = new AABB(
                region.minimum().x() - 1.0D,
                level.getMinBuildHeight(),
                region.minimum().z() - 1.0D,
                region.maximum().x() + 2.0D,
                level.getMaxBuildHeight(),
                region.maximum().z() + 2.0D
        );
        ArrayList<RocketEntity> matches = new ArrayList<>();
        UUID expectedId = destination
                ? record.destinationEntityId().orElse(null)
                : record.sourceEntityId();
        if (expectedId != null
                && level.getEntity(expectedId) instanceof RocketEntity exact
                && matches(record, snapshot, exact, destination)) {
            matches.add(exact);
        }
        ArrayList<RocketEntity> nearbyMatches = new ArrayList<>();
        level.getEntities().get(
                EntityTypeTest.forClass(RocketEntity.class),
                box,
                rocket -> {
                    if (matches(record, snapshot, rocket, destination)
                            && !matches.contains(rocket)
                            && !nearbyMatches.contains(rocket)) {
                        nearbyMatches.add(rocket);
                    }
                    return matches.size() + nearbyMatches.size()
                            >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                            ? AbortableIterationConsumer.Continuation.ABORT
                            : AbortableIterationConsumer.Continuation.CONTINUE;
                }
        );
        for (RocketEntity nearby : nearbyMatches) {
            if (!matches.contains(nearby)) {
                matches.add(nearby);
            }
        }
        matches.sort(Comparator.comparing(Entity::getUUID));
        return List.copyOf(matches);
    }

    static boolean isLandedAuthority(RocketEntity rocket, RocketTransferRecord record) {
        return isCommittedAuthority(rocket, record)
                && authorityState(rocket) == RocketFlightState.LANDED;
    }

    static boolean isReplaceableLandedAuthority(RocketEntity rocket, RocketTransferRecord record) {
        if (!isCommittedAuthority(rocket, record)) {
            return false;
        }
        RocketFlightState state = authorityState(rocket);
        return state == RocketFlightState.LANDED || state == RocketFlightState.FUELED;
    }

    private static boolean isCommittedAuthority(RocketEntity rocket, RocketTransferRecord record) {
        return record.phase() == RocketTransferPhase.COMMITTED
                && record.destinationEntityId().filter(rocket.getUUID()::equals).isPresent()
                && rocket.operational()
                && rocket.assemblyTransactionId().filter(record.logicalRocketId()::equals).isPresent()
                && rocket.snapshot().filter(snapshot -> snapshot.snapshotId()
                        .equals(record.destinationSnapshot().snapshotId()))
                        .filter(snapshot -> snapshot.contentHash()
                                .equals(record.destinationSnapshot().contentHash()))
                        .isPresent();
    }

    private static RocketFlightState authorityState(RocketEntity rocket) {
        return rocket.flightData().map(RocketFlightData::state)
                .orElse(RocketFlightState.FAILED_RECOVERABLE);
    }

    static RocketEntity keepOne(List<RocketEntity> matches) {
        RocketEntity keeper = matches.get(0);
        for (int index = 1; index < matches.size(); index++) {
            matches.get(index).discard();
        }
        return keeper;
    }

    static void discardAll(List<RocketEntity> rockets) {
        rockets.forEach(Entity::discard);
    }

    static RocketFlightData stationarySource(RocketTransferRecord record, long gameTime) {
        RocketFlightData failed = record.sourceFlightData().markFailed(gameTime);
        return failed.recover(failed.fuel().amount() > 0L, gameTime);
    }

    static void remountOnlinePassengers(
            MinecraftServer server,
            RocketTransferRecord record,
            RocketEntity authority,
            RocketPosition origin
    ) {
        for (RocketPassengerSeat seat : record.sourceFlightData().passengers().assignments()) {
            ServerPlayer player = server.getPlayerList().getPlayer(seat.passengerId());
            if (player != null) {
                movePassenger(player, authority, origin);
            }
        }
    }

    static void movePassenger(
            ServerPlayer passenger,
            RocketEntity destination,
            RocketPosition safeOrigin
    ) {
        passenger.stopRiding();
        passenger.setDeltaMovement(Vec3.ZERO);
        passenger.fallDistance = 0.0F;
        passenger.teleportTo(
                (ServerLevel) destination.level(),
                safeOrigin.x() + 0.5D,
                Math.max(safeOrigin.y() + 1.0D, destination.getY()),
                safeOrigin.z() + 0.5D,
                passenger.getYRot(),
                passenger.getXRot()
        );
        passenger.startRiding(destination, true);
        passenger.setDeltaMovement(Vec3.ZERO);
        passenger.fallDistance = 0.0F;
    }

    static void positionAscent(RocketEntity rocket, RocketPosition origin, long elapsed) {
        double progress = Math.min(1.0D, (double) elapsed / RocketFlightLimits.ASCENT_TICKS);
        rocket.setPos(
                origin.x() + 0.5D,
                origin.y() + progress * RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                origin.z() + 0.5D
        );
    }

    static void positionDescent(RocketEntity rocket, RocketPosition origin, long elapsed) {
        double progress = Math.min(1.0D, (double) elapsed / RocketFlightLimits.DESCENT_TICKS);
        rocket.setPos(
                origin.x() + 0.5D,
                origin.y() + (1.0D - progress) * RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                origin.z() + 0.5D
        );
    }

    static void positionAtAltitude(RocketEntity rocket, RocketPosition origin) {
        rocket.setPos(
                origin.x() + 0.5D,
                origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                origin.z() + 0.5D
        );
    }

    static void positionAtOrigin(RocketEntity rocket, RocketPosition origin) {
        rocket.setPos(origin.x() + 0.5D, origin.y(), origin.z() + 0.5D);
    }

    static void loadOrigin(ServerLevel level, RocketStructureSnapshot snapshot) {
        RocketPosition origin = snapshot.sourceOrigin();
        level.getChunkAt(new BlockPos(origin.x(), origin.y(), origin.z()));
    }

    static void keepLoaded(
            ServerLevel level,
            RocketStructureSnapshot snapshot,
            UUID transferId
    ) {
        RocketPosition origin = snapshot.sourceOrigin();
        ChunkPos chunk = new ChunkPos(origin.x() >> 4, origin.z() >> 4);
        level.getChunkSource().addRegionTicket(
                FLIGHT_TICKET,
                chunk,
                FLIGHT_TICKET_LEVEL,
                transferId
        );
        loadOrigin(level, snapshot);
    }

    private static boolean entityChunkLoaded(
            MinecraftServer server,
            RocketStructureSnapshot snapshot,
            UUID transferId
    ) {
        ServerLevel level = level(server, snapshot.sourceDimension());
        if (level == null) {
            return false;
        }
        keepLoaded(level, snapshot, transferId);
        RocketPosition origin = snapshot.sourceOrigin();
        return level.areEntitiesLoaded(ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4));
    }

    static ServerLevel level(MinecraftServer server, ResourceLocation dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static boolean matches(
            RocketTransferRecord record,
            RocketStructureSnapshot snapshot,
            RocketEntity rocket,
            boolean destination
    ) {
        if (!rocket.operational()
                || rocket.assemblyTransactionId().filter(record.logicalRocketId()::equals).isEmpty()
                || rocket.snapshot().filter(value -> value.snapshotId().equals(snapshot.snapshotId()))
                        .filter(value -> value.contentHash().equals(snapshot.contentHash())).isEmpty()) {
            return false;
        }
        if (!destination) {
            return true;
        }
        return rocket.flightData().flatMap(RocketFlightData::activeTransferId)
                .filter(record.transferId()::equals)
                .isPresent()
                || rocket.flightData().map(RocketFlightData::state).orElse(RocketFlightState.FAILED_RECOVERABLE)
                == RocketFlightState.LANDED;
    }
}
