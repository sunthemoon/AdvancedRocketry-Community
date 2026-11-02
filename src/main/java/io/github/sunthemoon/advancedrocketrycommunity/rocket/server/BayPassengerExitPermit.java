package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Anchor;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinProtection;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Non-serializable self-only capability captured before detach; no escrow or mount privilege. */
final class BayPassengerExitPermit {
    final MinecraftServer server;
    final ServerPlayer player;
    final RocketEntity rocket;
    final ServerLevel level;
    final Vec3 before;
    final long tick;
    final BayPassengerExitMotion.Generation generation;
    final UUID logical;
    final RocketTransferRecord transfer;
    final int seat;
    final Anchor anchor;
    final BooleanSupplier lease;
    final BayPassengerExitState.Hint hint;

    private BayPassengerExitPermit(ServerPlayer player, RocketEntity rocket, BayPassengerExitMotion.Generation generation,
            RocketTransferRecord record, int seat, Anchor anchor, BooleanSupplier lease) {
        this.player = player; this.rocket = rocket; level = player.serverLevel(); server = level.getServer();
        before = player.position(); tick = server.getTickCount(); this.generation = generation;
        logical = rocket.assemblyTransactionId().orElse(null); transfer = record; this.seat = seat;
        this.anchor = anchor; this.lease = lease;
        hint = record == null ? null : new BayPassengerExitState.Hint(record.transferId(), rocket.getUUID(),
                rocket.snapshot().orElseThrow().contentHash(), level);
    }
    static boolean guarded(ServerPlayer player) {
        return RocketRejoinProtection.guarded(player) || RocketRejoinProtection.required(player.getServer(), player.getUUID());
    }
    static BayPassengerExitPermit capture(ServerPlayer player, RocketEntity rocket, BayPassengerExitMotion.Generation generation) {
        if (!player.getServer().isSameThread() || player.getVehicle() != rocket || player.level() != rocket.level()
                || !online(player) || guarded(player) || RocketReleaseAccess.fenced(rocket)
                || !RocketTransferEntities.loadedEntity(rocket) || !BayPassengerExitMotion.finite(player.position())) { return null; }
        var server = player.getServer();
        var journal = RocketTransferSavedData.existing(server);
        UUID logical = rocket.assemblyTransactionId().orElse(null);
        var record = journal == null || logical == null ? null : journal.findByLogicalRocket(logical).orElse(null);
        var bays = RocketRuntime.installedBays().orElse(null);
        BooleanSupplier claim = bays == null || record == null ? () -> false : bays.exitLease(record);
        if (record != null && journal.operational() && journal.actionable(logical) && claim.getAsBoolean()
                && journal.logicalGuard(logical).filter(guard -> record.transferId().equals(guard.transfer())
                    && guard.recordDigest() != null).isPresent()
                && rocket.ownerId().filter(record.ownerId()::equals).isPresent()) {
            boolean source = rocket.getUUID().equals(record.sourceEntityId());
            boolean destination = record.phase().destinationAuthoritative()
                    && record.destinationEntityId().filter(rocket.getUUID()::equals).isPresent();
            var snapshot = source ? record.sourceSnapshot() : record.destinationSnapshot();
            var manifest = source ? record.sourceFlightData().passengers() : record.destinationFlightData().passengers();
            var assigned = manifest.assignment(player.getUUID()).orElse(null);
            if ((source || destination) && assigned != null && rocket.snapshot().filter(current ->
                    current.snapshotId().equals(snapshot.snapshotId()) && current.contentHash().equals(snapshot.contentHash())).isPresent()
                    && levelMatches(player.serverLevel(), snapshot.sourceDimension())
                    && rocket.flightData().filter(flight -> flight.passengers().equals(manifest)).isPresent()) {
                var origin = snapshot.sourceOrigin();
                var actionable = journal.actionableLease(logical);
                var logicalLease = journal.logicalLease(logical, record.transferId(), 0);
                return new BayPassengerExitPermit(player, rocket, generation, record, assigned.seatIndex(),
                        new Anchor(snapshot.sourceDimension().toString(), origin.x(), origin.y(), origin.z()),
                        () -> actionable.getAsBoolean() && logicalLease.getAsBoolean() && claim.getAsBoolean()
                            && journal.find(record.transferId()).orElse(null) == record
                            && rocket.snapshot().filter(current -> current.snapshotId().equals(snapshot.snapshotId())
                                && current.contentHash().equals(snapshot.contentHash())).isPresent()
                            && rocket.flightData().filter(flight -> flight.passengers().equals(manifest)).isPresent());
            }
        }
        return new BayPassengerExitPermit(player, rocket, generation, null, -1, null, () -> true);
    }
    private static boolean levelMatches(ServerLevel level, net.minecraft.resources.ResourceLocation dimension) {
        return level.dimension().location().equals(dimension);
    }
    boolean current(BayPassengerExitMotion observer, boolean detached) {
        return authority() && (detached ? !player.isPassenger() : player.getVehicle() == rocket)
                && generation != null && generation.equals(observer.generation(player)) && lease.getAsBoolean();
    }
    boolean authority() {
        return server.isSameThread() && online(player) && player.serverLevel() == level && rocket.level() == level
                && RocketTransferEntities.loadedEntity(rocket) && !guarded(player) && !RocketReleaseAccess.fenced(rocket)
                && java.util.Objects.equals(logical, rocket.assemblyTransactionId().orElse(null))
                && (transfer == null || rocket.ownerId().filter(transfer.ownerId()::equals).isPresent()) && lease.getAsBoolean();
    }
    static boolean online(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && player.connection != null && player.connection.connection.isConnected()
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }
}
