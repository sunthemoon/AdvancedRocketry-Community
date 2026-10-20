package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerSeat;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

/**
 * W116 (P1-N3 leaf N3-A2, W98-R10): the ordinary source retirement's static guards over live rockets, players and the
 * server, apart from its coordinator's state machine (W98, Root decision R34).
 */
final class RocketRetirementGuards {
    private RocketRetirementGuards() { }

    /**
     * R4: null when the survivor and every other candidate are on their end's origin column, otherwise the hold's reason
     * (the last off-column candidate). Nothing has changed yet, so a refusal changes nothing (N2).
     */
    static String offColumn(RocketTransferRecord record, RocketEntity survivor, List<RocketEntity> sources, List<RocketEntity> destinations) {
        String reason = null;
        var candidates = new ArrayList<RocketEntity>(sources);
        if (survivor != null) { candidates.add(survivor); }
        for (var candidate : candidates) {
            if (!onOriginColumn(candidate, record.sourceSnapshot())) { reason = "CANDIDATE_OFF_COLUMN entity=" + candidate.getUUID(); }
        }
        for (var candidate : destinations) {
            if (!onOriginColumn(candidate, record.destinationSnapshot())) { reason = "CANDIDATE_OFF_COLUMN entity=" + candidate.getUUID(); }
        }
        return reason;
    }

    static boolean onOriginColumn(RocketEntity candidate, RocketStructureSnapshot snapshot) {
        var origin = snapshot.sourceOrigin();
        return candidate.level().dimension().location().equals(snapshot.sourceDimension())
                && candidate.chunkPosition().equals(new ChunkPos(origin.x() >> 4, origin.z() >> 4));
    }

    /** R3's crew rule against T's source manifest, without remembering any placement. */
    static boolean crewReady(MinecraftServer server, RocketTransferRecord record, RocketEntity survivor, boolean offlineAllowed) {
        return crewReady(server, record, survivor, offlineAllowed, new HashSet<>());
    }

    /** R3's crew rule; each seat placed now is added to {@code delivered}, so a later dismount or logout never undoes it. */
    static boolean crewReady(MinecraftServer server, RocketTransferRecord record, RocketEntity survivor,
                             boolean offlineAllowed, Set<UUID> delivered) {
        var seats = record.sourceFlightData().passengers().assignments().stream().map(RocketPassengerSeat::passengerId).toList();
        return RocketRetirementProof.crewReady(seats, id -> {
            var player = server.getPlayerList().getPlayer(id);
            return RocketRetirementProof.seat(player != null, player != null && player.getServer() == server && !player.isRemoved()
                            && player.isAlive() && !player.isSpectator() && player.connection != null && player.connection.connection.isConnected(),
                    player != null && player.level() == survivor.level(), player != null && player.getVehicle() == survivor,
                    player != null && player.getVehicle() == null);
        }, delivered, offlineAllowed);
    }

    /**
     * The survivor's frozen projection, or null unless it is T's loaded source identity, stationary at its snapshot
     * origin with T's source fuel and debits, and saved as a world entity (not a passenger).
     */
    static CompoundTag projection(RocketEntity survivor, RocketTransferRecord record) {
        if (!RocketTransferEntities.loadedEntity(survivor) || !survivor.operational() || survivor.isPassenger()) { return null; }
        var snapshot = record.sourceSnapshot(); var origin = snapshot.sourceOrigin();
        var flight = survivor.flightData().orElse(null);
        if (!survivor.level().dimension().location().equals(snapshot.sourceDimension())
                || survivor.assemblyTransactionId().filter(record.logicalRocketId()::equals).isEmpty()
                || survivor.ownerId().filter(record.ownerId()::equals).isEmpty()
                || survivor.snapshot().filter(value -> value.snapshotId().equals(snapshot.snapshotId())
                        && value.contentHash().equals(snapshot.contentHash())).isEmpty()
                || !RocketTransferRecoveryService.recoveredStationarySource(true, false, flight,
                        survivor.getX() == origin.x() + 0.5D && survivor.getY() == origin.y() && survivor.getZ() == origin.z() + 0.5D)
                || !flight.fuel().equals(record.sourceFlightData().fuel())) { return null; }
        var tag = new CompoundTag();
        return survivor.save(tag) ? tag : null;
    }

    /** A publication refusal's DESTINATION_SPAWNED T may return only when no destination entity was admitted. */
    static boolean destinationAdmitted(MinecraftServer server, RocketTransferRecord record) {
        if (record.phase() == RocketTransferPhase.PREPARED) { return false; }
        var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        return level == null || record.destinationEntityId().map(level::getEntity).isPresent()
                || !RocketTransferEntities.loadedMatches(server, record, true).isEmpty();
    }
}
