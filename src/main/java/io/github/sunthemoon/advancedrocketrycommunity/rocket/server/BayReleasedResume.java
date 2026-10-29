package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** W196: released receipt recovery uses the checked phase, never ordinary stationary-source rollback. */
final class BayReleasedResume {
    private final MinecraftServer server;
    private final BaySourceResume sources;
    private net.minecraft.server.level.ServerPlayer permittedPlayer;
    private RocketEntity permittedRocket;
    private BooleanSupplier permittedHold;

    BayReleasedResume(MinecraftServer server) { this.server = server; sources = new BaySourceResume(server); }

    /** No new end or ticket bound. The caller has checked T and the exact released receipt generation. */
    String resume(RocketTransferRecord record, boolean alreadyReleased, java.util.List<BayReceipt> receipts, BooleanSupplier allowed) {
        if (!allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
        if (BayDeploymentChecks.censusRefusal(record).isPresent()) { return "END_UNAVAILABLE"; }
        if (alreadyReleased) { BayCensusColumns.renewRestart(server, record); }
        if (!BaySourceProof.endsLoaded(server, record)) { return "END_LOADING"; }
        var sourceLevel = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        var destinationLevel = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        if (sourceLevel == null || destinationLevel == null) { return "END_UNAVAILABLE"; }
        var registered = receipts.stream().filter(r -> r.phase() == BayReceipt.Phase.REGISTERED).findFirst().orElse(null);
        var scan = BayCopyCensus.inspectReleased(server, record, registered);
        if (scan.refusal() != null) { return scan.refusal(); }
        var source = exact(sourceLevel, record.sourceEntityId());
        var candidates = new java.util.ArrayList<BayCopyCensus.Copy>(scan.copies());
        if (source != null) { candidates.add(new BayCopyCensus.Copy(source, false)); }
        for (var copy : candidates) {
            var rocket = copy.rocket();
            if (!RocketEscrowGate.bayNeutral(rocket, record.transferId())) { return "BAY_SOURCE_PROTECTED"; }
            var before = rocket.snapshot().orElseThrow();
            var after = copy.destination() ? record.destinationSnapshot() : record.sourceSnapshot();
            if (!before.contentHash().equals(after.contentHash())) {
                if (registered == null || !BayReleasedAuthority.payloads(before, receipts, false)) { return "CONSUMPTION_MISMATCH"; }
                if (!reserve()) { return "SOURCE_LOADING"; }
                if (!allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
                rocket.applyBayConsumption(new BaySourceConsumption(rocket.getUUID(), record.logicalRocketId(), record.ownerId(), before, after));
                if (!allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
            }
            if (!BayReleasedAuthority.payloads(rocket.snapshot().orElseThrow(), receipts)) { return "CONSUMPTION_MISMATCH"; }
        }
        RocketEntity authority;
        if (record.phase() == RocketTransferPhase.PREPARED) {
            if (source == null) {
                if (!alreadyReleased) { return "SOURCE_LOADING"; }
                if (sourceLevel.getEntity(record.sourceEntityId()) != null) { return "SOURCE_AUTHORITY_CONFLICT"; }
                if (!reserve()) { return "SOURCE_LOADING"; }
                if (!allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
                String refusal = sources.rebuild(sourceLevel, record);
                if (!allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
                if (refusal != null) { return refusal; }
                source = exact(sourceLevel, record.sourceEntityId());
            }
            if (!allowed.getAsBoolean() || !RocketEscrowGate.bayNeutral(source, record.transferId())
                    || !BaySourceResume.establishTransit(source, record, sourceLevel)
                    || !allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
            authority = source;
        } else {
            if (!alreadyReleased) { return "TRANSFER_AUTHORITY_CONFLICT"; }
            var uuid = record.destinationEntityId().orElseThrow();
            var destination = exact(destinationLevel, uuid);
            if (destination == null) {
                if (destinationLevel.getEntity(uuid) != null) { return "SOURCE_AUTHORITY_CONFLICT"; }
                if (!reserve()) { return "SOURCE_LOADING"; }
                destination = ModEntities.ROCKET.get().create(destinationLevel);
                if (destination == null) { return "SOURCE_REBUILD_REFUSED"; }
                destination.setUUID(uuid);
                destination.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(),
                        RocketTransferEntities.currentArrival(record, destinationLevel.getGameTime()));
                RocketTransferEntities.positionAtAltitude(destination, record.destinationSnapshot().sourceOrigin());
                if (!allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
                if (!destinationCurrent(destination, record) || !destinationLevel.addFreshEntity(destination)) { return "SOURCE_REBUILD_REFUSED"; }
                if (!allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
            }
            if (!destinationCurrent(destination, record)) { return "SOURCE_AUTHORITY_CONFLICT"; }
            authority = destination;
            if (!allowed.getAsBoolean() || source != null && !BaySourceResume.establishTransit(source, record, sourceLevel)
                    || !allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
        }
        if (source != null && record.phase().ordinal() >= RocketTransferPhase.PASSENGERS_TRANSFERRED.ordinal()) {
            for (var passenger : java.util.List.copyOf(source.getPassengers())) {
                if (!(passenger instanceof net.minecraft.server.level.ServerPlayer player) || !move(player, authority, record, allowed)) {
                    return "DUPLICATE_PASSENGERS";
                }
            }
        }
        // The phase is durable before its effect. Reconcile every online assignment, including a rider who
        // dismounted during that write; an empty source alone does not prove that the passenger effect completed.
        if (record.phase() == RocketTransferPhase.PASSENGERS_TRANSFERRED) {
            for (var seat : record.destinationFlightData().passengers().assignments()) {
                var player = server.getPlayerList().getPlayer(seat.passengerId());
                if (player != null && player.getVehicle() != authority && !move(player, authority, record, allowed)) {
                    return "DUPLICATE_PASSENGERS";
                }
            }
            if (!passengersTransferred(server, record, authority)) { return "DUPLICATE_PASSENGERS"; }
        }
        for (var copy : candidates) {
            var rocket = copy.rocket();
            if (rocket == authority || rocket == source && record.phase().ordinal() < RocketTransferPhase.SOURCE_REMOVED.ordinal()) { continue; }
            for (var passenger : rocket.getPassengers()) {
                if (!(passenger instanceof net.minecraft.server.level.ServerPlayer player)
                        || !assigned(player, record)) { return "DUPLICATE_PASSENGERS"; }
            }
            for (var passenger : java.util.List.copyOf(rocket.getPassengers())) {
                if (!move((net.minecraft.server.level.ServerPlayer) passenger, authority, record, allowed)) { return "DUPLICATE_PASSENGERS"; }
            }
            if (!allowed.getAsBoolean() || !RocketEscrowGate.bayNeutral(rocket, record.transferId()) || !RocketTransferEntities.loadedEntity(authority)
                    || !rocket.getPassengers().isEmpty()) { return "SOURCE_AUTHORITY_CONFLICT"; }
            rocket.discard();
            if (!allowed.getAsBoolean()) { return "SOURCE_AUTHORITY_CONFLICT"; }
        }
        return allowed.getAsBoolean() ? null : "SOURCE_AUTHORITY_CONFLICT";
    }

    boolean mountAllowed(net.minecraft.world.entity.Entity player, RocketEntity rocket) {
        return server.isSameThread() && player == permittedPlayer && rocket == permittedRocket
                && permittedHold != null && permittedHold.getAsBoolean()
                && RocketEscrowGate.escrowIdle(rocket) && !RocketReleaseAccess.fenced(rocket);
    }

    boolean move(net.minecraft.server.level.ServerPlayer player, RocketEntity rocket, RocketTransferRecord record, BooleanSupplier allowed) {
        if (!allowed.getAsBoolean() || permittedPlayer != null || !assigned(player, record) || !RocketTransferEntities.loadedEntity(rocket)
                || !RocketEscrowGate.bayNeutral(rocket, record.transferId()) || RocketReleaseAccess.fenced(rocket)
                || !RocketTransferEntities.currentEntity(rocket, record, record.phase().destinationAuthoritative())) { return false; }
        try {
            permittedPlayer = player; permittedRocket = rocket; permittedHold = allowed;
            RocketTransferEntities.movePassenger(player, rocket, (record.phase().destinationAuthoritative()
                    ? record.destinationSnapshot() : record.sourceSnapshot()).sourceOrigin());
            return allowed.getAsBoolean() && player.getVehicle() == rocket;
        } finally { permittedPlayer = null; permittedRocket = null; permittedHold = null; }
    }

    private boolean assigned(net.minecraft.server.level.ServerPlayer player, RocketTransferRecord record) {
        return server.isSameThread() && player.isAlive() && server.getPlayerList().getPlayer(player.getUUID()) == player
                && record.sourceFlightData().passengers().assignment(player.getUUID()).isPresent();
    }

    private boolean reserve() {
        return BayCopySteps.reserveCopyWork(RocketRuntime.cargoPreparations(server).budget(), server.getTickCount());
    }

    static RocketEntity exact(ServerLevel level, java.util.UUID identity) {
        return level != null && level.getEntity(identity) instanceof RocketEntity rocket ? rocket : null;
    }

    static boolean passengersTransferred(MinecraftServer server, RocketTransferRecord record, RocketEntity destination) {
        return record.destinationFlightData().passengers().assignments().stream().allMatch(seat -> {
            var player = server.getPlayerList().getPlayer(seat.passengerId());
            return player == null || player.isAlive() && player.getVehicle() == destination;
        });
    }

    static boolean destinationCurrent(RocketEntity rocket, RocketTransferRecord record) {
        if (!RocketEscrowGate.bayNeutral(rocket, record.transferId()) || !rocket.operational() || rocket.isPassenger()
                || rocket.ownerId().filter(record.ownerId()::equals).isEmpty()
                || rocket.assemblyTransactionId().filter(record.logicalRocketId()::equals).isEmpty()
                || rocket.snapshot().filter(snapshot -> snapshot.snapshotId().equals(record.destinationSnapshot().snapshotId())
                        && snapshot.contentHash().equals(record.destinationSnapshot().contentHash())).isEmpty()) { return false; }
        var flight = rocket.flightData().orElseThrow();
        var expected = record.destinationFlightData();
        var origin = record.destinationSnapshot().sourceOrigin();
        return (flight.state() == RocketFlightState.DESCENT || flight.state() == RocketFlightState.LANDED)
                && (flight.state() == RocketFlightState.DESCENT
                    ? flight.activeTransferId().filter(record.transferId()::equals).isPresent() && flight.plan().equals(expected.plan())
                    : flight.activeTransferId().isEmpty() && flight.plan().isEmpty())
                && flight.fuel().equals(expected.fuel()) && flight.passengers().equals(expected.passengers())
                && flight.currentBody().equals(expected.currentBody()) && flight.currentDimension().equals(expected.currentDimension())
                && flight.currentOrigin().equals(expected.currentOrigin()) && flight.currentTarget().equals(expected.currentTarget())
                && rocket.getX() == origin.x() + .5D && rocket.getZ() == origin.z() + .5D
                && rocket.getY() >= origin.y() && rocket.getY() <= origin.y()
                        + io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS;
    }
}
