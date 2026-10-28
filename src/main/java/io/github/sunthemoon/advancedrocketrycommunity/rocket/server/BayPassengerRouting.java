package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** A synchronous, coordinator-owned MOVE_ASSIGNED_PASSENGER scope; no settlement or manifest mutation. */
final class BayPassengerRouting {
    private final MinecraftServer server;
    private ServerPlayer permittedPlayer;
    private RocketEntity permittedSource;

    BayPassengerRouting(MinecraftServer server) { this.server = server; }

    boolean mountAllowed(Entity passenger, RocketEntity source) {
        return server.isSameThread() && passenger == permittedPlayer && source == permittedSource
                && RocketEscrowGate.escrowIdle(source) && !RocketReleaseAccess.fenced(source);
    }

    /** Every physical passenger must be an online assigned player; offline manifest entries need no action. */
    boolean emptyCopy(BayClaim claim, RocketTransferRecord record, RocketEntity copy, RocketEntity survivor) {
        for (Entity entity : copy.getPassengers()) {
            if (!(entity instanceof ServerPlayer player) || !player.isAlive()
                    || server.getPlayerList().getPlayer(player.getUUID()) != player
                    || record.sourceFlightData().passengers().assignment(player.getUUID()).isEmpty()) { return false; }
        }
        for (Entity entity : java.util.List.copyOf(copy.getPassengers())) {
            if (!move(claim, record, (ServerPlayer) entity, survivor)) { return false; }
        }
        return copy.getPassengers().isEmpty();
    }

    /** Online assigned players may rejoin the exact consumed source; offline seats remain untouched. */
    void reconnect(BayClaim claim, RocketTransferRecord record, ServerPlayer player) {
        if (claim.group().isEmpty() || BayConsumptionSteps.half(record, claim.group().orElseThrow())
                != io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup.Half.AFTER
                || record.sourceFlightData().passengers().assignment(player.getUUID()).isEmpty()) { return; }
        var survivor = BayConsumptionSteps.exactSource(server, record, record.sourceEntityId(), claim.group().orElseThrow());
        if (survivor != null && player.getVehicle() != survivor) { move(claim, record, player, survivor); }
    }

    private boolean move(BayClaim claim, RocketTransferRecord record, ServerPlayer player, RocketEntity survivor) {
        var journal = RocketTransferSavedData.get(server);
        if (!server.isSameThread() || permittedPlayer != null || claim.hold() == BayClaim.Hold.REPAIR
                || !player.isAlive() || server.getPlayerList().getPlayer(player.getUUID()) != player
                || record.sourceFlightData().passengers().assignment(player.getUUID()).isEmpty()
                || !journal.actionable(claim.logical()) || journal.find(claim.transfer()).orElse(null) != record
                || !survivor.getUUID().equals(record.sourceEntityId()) || !BayConsumptionSteps.inTransit(survivor, record)
                || survivor.snapshot().filter(value -> value.contentHash().equals(record.sourceSnapshot().contentHash())).isEmpty()
                || !RocketTransferEntities.loadedEntity(survivor) || !RocketEscrowGate.bayNeutral(survivor, record.transferId())
                || RocketReleaseAccess.fenced(survivor)) { return false; }
        try {
            claim.group().orElseThrow().invalidate();
            permittedPlayer = player; permittedSource = survivor;
            RocketTransferEntities.movePassenger(player, survivor, record.sourceSnapshot().sourceOrigin());
            return player.getVehicle() == survivor;
        } finally { permittedPlayer = null; permittedSource = null; }
    }
}
