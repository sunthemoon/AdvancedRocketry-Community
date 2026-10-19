package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerSeat;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferAuthorityOrder;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
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

    /** A delayed continuation must not act on a captured object that unloaded or ceased to be this record's entity. */
    static boolean currentEntity(RocketEntity entity, RocketTransferRecord record, boolean destination) {
        return loadedEntity(entity)
                && (destination ? record.destinationEntityId().filter(entity.getUUID()::equals).isPresent()
                        : record.sourceEntityId().equals(entity.getUUID()))
                && entity.assemblyTransactionId().filter(record.logicalRocketId()::equals).isPresent()
                && entity.ownerId().filter(record.ownerId()::equals).isPresent();
    }

    static boolean loadedEntity(RocketEntity entity) {
        return entity != null && entity.isAlive() && entity.isAddedToWorld()
                && entity.level() instanceof ServerLevel level && level.areEntitiesLoaded(entity.chunkPosition().toLong())
                && level.getEntity(entity.getUUID()) == entity;
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
        RocketEntity destination = createDestination(server, record);
        return destination != null && ((ServerLevel) destination.level()).addFreshEntity(destination) ? destination : null;
    }

    /** Recovery can acknowledge the new UUID before exposing its materialized entity. */
    static RocketFlightData currentArrival(RocketTransferRecord record, long gameTime) {
        return record.sourceFlightData().arriveAtDestination(record.destinationFlightData().fuel(),
                record.destinationFlightData().currentBody(), record.destinationFlightData().currentDimension(),
                record.destinationSnapshot().sourceOrigin(), gameTime);
    }

    /** Recovery can acknowledge the new UUID before exposing its materialized entity. */
    static RocketEntity createDestination(MinecraftServer server, RocketTransferRecord record) {
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
        return destination;
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

    static boolean authorityEntityChunkLoaded(MinecraftServer server, RocketTransferRecord record) {
        return entityChunkLoaded(server, record.phase().destinationAuthoritative()
                ? record.destinationSnapshot() : record.sourceSnapshot(), record.transferId());
    }

    static List<RocketEntity> findMatches(
            MinecraftServer server,
            RocketTransferRecord record,
            boolean destination
    ) {
        ServerLevel level = level(server, (destination ? record.destinationSnapshot() : record.sourceSnapshot()).sourceDimension());
        if (level != null) { loadOrigin(level, destination ? record.destinationSnapshot() : record.sourceSnapshot()); }
        return loadedMatches(server, record, destination);
    }

    /** Reads the visible entity index only; never acquires a ticket or invokes a chunk getter. */
    static List<RocketEntity> loadedMatches(MinecraftServer server, RocketTransferRecord record, boolean destination) {
        RocketStructureSnapshot snapshot = destination
                ? record.destinationSnapshot()
                : record.sourceSnapshot();
        ServerLevel level = level(server, snapshot.sourceDimension());
        if (level == null) {
            return List.of();
        }
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
        matches.sort(Comparator.comparing(Entity::getUUID, RocketTransferAuthorityOrder.preferred(expectedId)));
        return List.copyOf(matches);
    }

    /** Both election regions, including the query margin, must be loaded and entity-ticking. */
    static boolean recoveryEndsAlreadyLoaded(MinecraftServer server, RocketTransferRecord record) {
        return endAlreadyLoaded(server, record.sourceSnapshot()) && endAlreadyLoaded(server, record.destinationSnapshot());
    }

    /** W90: the checked column set, every column loaded and entity-ticking. Neither predicate alone suffices (R22 S5). */
    static boolean endAlreadyLoaded(MinecraftServer server, RocketStructureSnapshot snapshot) {
        ServerLevel level = level(server, snapshot.sourceDimension());
        return level != null && marginReady(snapshot, (x, z) -> columnReady(level, x, z, snapshot.sourceOrigin().y()));
    }

    /** The readiness predicate over the checked set; the same answer as the pre-W90 formula for every snapshot. */
    static boolean marginReady(RocketStructureSnapshot snapshot, java.util.function.BiPredicate<Integer, Integer> ready) {
        var columns = RocketDiscoveryColumns.of(snapshot);
        return columns.valid() && columnsAlreadyLoaded(columns.minColumnX(), columns.maxColumnX(), columns.minColumnZ(),
                columns.maxColumnZ(), ready);
    }

    static boolean columnReady(ServerLevel level, int x, int z, int y) {
        return level.areEntitiesLoaded(ChunkPos.asLong(x, z)) && level.isPositionEntityTicking(new BlockPos(x << 4, y, z << 4));
    }

    /** Read-only {@code k/n} over the recorded rectangle, or {@code invalid}; never loads or tickets. */
    static String marginStatus(MinecraftServer server, RocketStructureSnapshot snapshot) {
        var columns = RocketDiscoveryColumns.of(snapshot);
        if (!columns.valid()) { return "invalid"; }
        ServerLevel level = level(server, snapshot.sourceDimension());
        int ready = 0;
        for (var column : columns.columns()) {
            if (level != null && columnReady(level, column.x, column.z, snapshot.sourceOrigin().y())) { ready++; }
        }
        return ready + "/" + columns.count();
    }

    /** Skinny snapshots can have a large footprint despite the block bound. Never scan it here. */
    static boolean columnsAlreadyLoaded(int minX, int maxX, int minZ, int maxZ,
                                        java.util.function.BiPredicate<Integer, Integer> loaded) {
        long width = (long) maxX - minX + 1, depth = (long) maxZ - minZ + 1;
        if (width <= 0 || depth <= 0 || width > RocketFlightLimits.MAX_LANDING_CHUNKS
                || depth > RocketFlightLimits.MAX_LANDING_CHUNKS || width * depth > RocketFlightLimits.MAX_LANDING_CHUNKS) { return false; }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (!loaded.test(minX + x, minZ + z)) { return false; }
            }
        }
        return true;
    }

    static RocketEntity loadedSettledAuthority(MinecraftServer server, RocketTransferRecord record) {
        ServerLevel level = level(server, record.destinationSnapshot().sourceDimension());
        UUID expected = record.destinationEntityId().orElse(null);
        // Read existing entities only: unrelated passenger login must not activate chunks.
        return level != null && expected != null && level.getEntity(expected) instanceof RocketEntity rocket
                && isReplaceableLandedAuthority(rocket, record) ? rocket : null;
    }

    static boolean nearbyArrivalStillLoading(ServerPlayer player, RocketTransferRecord record) {
        var snapshot = record.destinationSnapshot();
        var origin = snapshot.sourceOrigin();
        return record.phase() == RocketTransferPhase.COMMITTED
                && player.level().dimension().location().equals(snapshot.sourceDimension())
                && player.distanceToSqr(origin.x() + 0.5D, origin.y() + 1.0D, origin.z() + 0.5D)
                        <= RocketManager.MAX_INTERACTION_DISTANCE_SQUARED
                && !player.serverLevel().areEntitiesLoaded(ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4));
    }

    static boolean isReplaceableLandedAuthority(RocketEntity rocket, RocketTransferRecord record) {
        if (!isCommittedAuthority(rocket, record)) {
            return false;
        }
        RocketFlightState state = authorityState(rocket);
        return isStationaryArrival(state);
    }

    static boolean isStationaryArrival(RocketFlightState state) {
        return state.acceptsFuel();
    }

    static boolean isCommittedAuthority(RocketEntity rocket, RocketTransferRecord record) {
        return record.phase() == RocketTransferPhase.COMMITTED
                && record.destinationEntityId().filter(rocket.getUUID()::equals).isPresent()
                && rocket.operational()
                && rocket.ownerId().filter(record.ownerId()::equals).isPresent()
                && rocket.assemblyTransactionId().filter(record.logicalRocketId()::equals).isPresent()
                && recognizes(rocket, record.destinationSnapshot());
    }

    /**
     * W11c leaf C-I (W11CD-DESIGN section 2.5): {@code sameSnapshotId && (sameContentHash || (sameStructureDigest
     * && currentLedgerBinding && epoch > 0))}. Recognition only: owner, logical, physical and state checks stay with the
     * caller, and every fence, floor, veto and governed route still decides the action. A neutral rocket keeps the
     * exact-hash rule and never reads the escrow ledger.
     */
    static boolean recognizes(RocketStructureSnapshot recorded, RocketStructureSnapshot current,
                              java.util.function.BooleanSupplier currentLedgerBinding, long epoch) {
        return current.snapshotId().equals(recorded.snapshotId())
                && (current.contentHash().equals(recorded.contentHash())
                        || epoch > 0 && current.structureDigest().equals(recorded.structureDigest())
                        && currentLedgerBinding.getAsBoolean());
    }

    static boolean recognizes(RocketEntity rocket, RocketStructureSnapshot recorded) {
        return rocket.snapshot().filter(current -> recognizes(recorded, current, () -> currentLedgerBinding(rocket),
                rocket.escrowSection().epoch())).isPresent();
    }

    /** The section names the loaded escrow ledger; read on the server thread only, as {@code RocketEscrowGate} does. */
    static boolean currentLedgerBinding(RocketEntity rocket) {
        UUID bound = rocket.escrowSection().ledger();
        if (bound == null || !(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return false;
        }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        return data != null && bound.equals(data.ledger().id());
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
        return failed.recover(failed.fuel().availableForLaunch(), gameTime);
    }

    static void remountOnlinePassengers(
            MinecraftServer server,
            RocketTransferRecord record,
            RocketEntity authority,
            RocketPosition origin
    ) {
        var passengers = isReplaceableLandedAuthority(authority, record)
                ? authority.flightData().orElseThrow().passengers()
                : record.sourceFlightData().passengers();
        for (RocketPassengerSeat seat : passengers.assignments()) {
            ServerPlayer player = server.getPlayerList().getPlayer(seat.passengerId());
            if (player != null) {
                movePassenger(player, authority, origin);
            }
        }
    }

    /** Players riding the rocket who hold a seat in its current manifest (at most the manifest size). */
    static List<ServerPlayer> aboard(RocketEntity rocket) {
        var manifest = rocket.flightData().orElseThrow().passengers();
        return rocket.getPassengers().stream()
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast)
                .filter(player -> manifest.assignment(player.getUUID()).isPresent())
                .toList();
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
        refreshFlightTicket(level, snapshot, transferId);
        loadOrigin(level, snapshot);
    }

    /** W83b (Root decision R32 item 2): the destination end's flight tickets over its ticket set, then today's origin load. */
    static void keepDestinationLoaded(ServerLevel level, RocketStructureSnapshot destination, UUID transferId) {
        addDestinationTickets(transferId, destination, levelTickets(level.getServer()));
        loadOrigin(level, destination);
    }

    /**
     * R32 item 2, an explicit change of the legacy one-origin budget: one flight ticket on each column of the
     * destination's {@link #destinationTicketSet}, in the destination's own Level. At most 16 per transfer, so 1,024 at
     * 64 transfers. Returns the number of columns ticketed; none when that Level is absent.
     */
    static int addDestinationTickets(UUID transfer, RocketStructureSnapshot destination, FlightTickets tickets) {
        if (!tickets.present(destination.sourceDimension())) { return 0; }
        var columns = destinationTicketSet(destination);
        columns.forEach(column -> tickets.add(destination.sourceDimension(), column, transfer));
        return columns.size();
    }

    /**
     * The destination's ticket and arrival-readiness set: every column of an admissible checked set (the footprint and
     * the occupancy census, the origin among them), otherwise the origin alone as before. It is exactly the destination
     * end's {@link RocketDiscoveryColumns#releaseSet}, so {@link #releaseMargins} removes what was added.
     */
    static List<ChunkPos> destinationTicketSet(RocketStructureSnapshot destination) {
        return RocketDiscoveryColumns.of(destination).releaseSet();
    }

    /** R32 item 1: how many of the columns have their entity sections loaded. Reads only; never loads or tickets. */
    static int entitiesLoadedColumns(List<ChunkPos> columns, java.util.function.LongPredicate entitiesLoaded) {
        int loaded = 0;
        for (var column : columns) {
            if (entitiesLoaded.test(column.toLong())) { loaded++; }
        }
        return loaded;
    }

    /**
     * Called only after checked ACTIVE classification of an unheld record; never synchronously loads an endpoint.
     * Returns the number of distinct columns ticketed: at most 16 per end and 32 per transfer.
     */
    static int refreshGovernedFlightTickets(MinecraftServer server, RocketTransferRecord record) {
        if (!server.isSameThread() || record.nativeTransfer().isEmpty()) { return 0; }
        return refreshMargins(record.transferId(), record.sourceSnapshot(), record.destinationSnapshot(), levelTickets(server));
    }

    /**
     * W90 (R21 item 1), an explicit change of the two-origin budget: one ticket on each column of each recorded end's
     * checked set, the origin among them. Nothing unless both Levels exist and both ends are admissible; a recorded end
     * that is not is a {@code DISCOVERY_MARGIN} hold, which never reaches a refresh.
     */
    static int refreshMargins(UUID transfer, RocketStructureSnapshot source, RocketStructureSnapshot destination,
                              FlightTickets tickets) {
        var ends = List.of(source, destination);
        for (var snapshot : ends) {
            if (!tickets.present(snapshot.sourceDimension()) || !RocketDiscoveryColumns.of(snapshot).admissible()) { return 0; }
        }
        var refreshed = new java.util.LinkedHashSet<java.util.Map.Entry<ResourceLocation, ChunkPos>>();
        for (var snapshot : ends) {
            for (var column : RocketDiscoveryColumns.of(snapshot).refreshSet()) {
                if (refreshed.add(java.util.Map.entry(snapshot.sourceDimension(), column))) {
                    tickets.add(snapshot.sourceDimension(), column, transfer);
                }
            }
        }
        return refreshed.size();
    }

    /** The flight-ticket calls of one Level, keyed by its dimension; a recording sink in unit tests. */
    interface FlightTickets {
        boolean present(ResourceLocation dimension);
        void add(ResourceLocation dimension, ChunkPos column, UUID transfer);
        void remove(ResourceLocation dimension, ChunkPos column, UUID transfer);
    }

    private static FlightTickets levelTickets(MinecraftServer server) {
        return new FlightTickets() {
            @Override public boolean present(ResourceLocation dimension) { return level(server, dimension) != null; }
            @Override public void add(ResourceLocation dimension, ChunkPos column, UUID transfer) {
                level(server, dimension).getChunkSource().addRegionTicket(FLIGHT_TICKET, column, FLIGHT_TICKET_LEVEL, transfer);
            }
            @Override public void remove(ResourceLocation dimension, ChunkPos column, UUID transfer) {
                level(server, dimension).getChunkSource().removeRegionTicket(FLIGHT_TICKET, column, FLIGHT_TICKET_LEVEL, transfer);
            }
        };
    }

    private static void refreshFlightTicket(ServerLevel level, RocketStructureSnapshot snapshot, UUID transferId) {
        RocketPosition origin = snapshot.sourceOrigin();
        ChunkPos chunk = new ChunkPos(origin.x() >> 4, origin.z() >> 4);
        level.getChunkSource().addRegionTicket(
                FLIGHT_TICKET,
                chunk,
                FLIGHT_TICKET_LEVEL,
                transferId
        );
    }

    static boolean destinationEntityChunkReady(
            ServerLevel level,
            RocketStructureSnapshot snapshot
    ) {
        RocketPosition origin = snapshot.sourceOrigin();
        BlockPos position = new BlockPos(origin.x(), origin.y(), origin.z());
        return level.areEntitiesLoaded(ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4))
                && level.isPositionEntityTicking(position);
    }

    /** F8's explicit release; expiry is only a backstop, never its acknowledgement. No chunk loads. */
    static void releaseFlightTickets(MinecraftServer server, RocketTransferRecord record) {
        if (!server.isSameThread()) { throw new IllegalStateException("Flight ticket release requires the server thread"); }
        releaseMargins(record.transferId(), record.sourceSnapshot(), record.destinationSnapshot(), levelTickets(server));
    }

    /** Each end's release set in its own Level: the origin always, plus the checked set of an admissible end. */
    static void releaseMargins(UUID transfer, RocketStructureSnapshot source, RocketStructureSnapshot destination,
                               FlightTickets tickets) {
        for (var snapshot : List.of(source, destination)) {
            // An absent Level holds no tickets; the other end is still released.
            if (!tickets.present(snapshot.sourceDimension())) { continue; }
            for (var column : RocketDiscoveryColumns.of(snapshot).releaseSet()) {
                tickets.remove(snapshot.sourceDimension(), column, transfer);
            }
        }
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
                || rocket.ownerId().filter(record.ownerId()::equals).isEmpty()
                || rocket.assemblyTransactionId().filter(record.logicalRocketId()::equals).isEmpty()
                || !recognizes(rocket, snapshot)) {
            return false;
        }
        // The current entity owns its resources even after draining clears fuel/state/plan.
        // Snapshot and logical identity distinguish the two ends; state is not identity.
        return true;
    }
}
