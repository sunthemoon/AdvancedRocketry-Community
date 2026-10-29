package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * A synchronous, coordinator-owned MOVE_ASSIGNED_PASSENGER scope; no settlement or manifest mutation. W185-R4 (Root's
 * scope): it moves only an online assigned passenger riding a recognized copy that D5 removes, into the exact survivor,
 * never one who dismounted, died or rides elsewhere. That mount alone changes no proved image (a player is never saved
 * with the rocket), so it does not invalidate the group; removing the copy does.
 */
final class BayPassengerRouting {
    private final MinecraftServer server;
    private ServerPlayer permittedPlayer;
    private RocketEntity permittedSource;
    private java.util.function.BooleanSupplier permittedHold = () -> true;

    BayPassengerRouting(MinecraftServer server) { this.server = server; }

    boolean mountAllowed(Entity passenger, RocketEntity source) {
        return server.isSameThread() && passenger == permittedPlayer && source == permittedSource
                && permittedHold.getAsBoolean()
                && RocketEscrowGate.escrowIdle(source) && !RocketReleaseAccess.fenced(source);
    }

    /** Every physical passenger must be an online assigned player; offline manifest entries need no action. */
    boolean emptyCopy(BayClaim claim, RocketTransferRecord record, RocketEntity copy, RocketEntity survivor) {
        return emptyCopy(claim, record, copy, survivor, () -> true);
    }

    boolean emptyCopy(BayClaim claim, RocketTransferRecord record, RocketEntity copy, RocketEntity survivor,
                      java.util.function.BooleanSupplier allowed) {
        if (!allowed.getAsBoolean()) { return false; }
        for (Entity entity : copy.getPassengers()) {
            if (!(entity instanceof ServerPlayer player) || !player.isAlive()
                    || server.getPlayerList().getPlayer(player.getUUID()) != player
                    || record.sourceFlightData().passengers().assignment(player.getUUID()).isEmpty()) { return false; }
        }
        for (Entity entity : java.util.List.copyOf(copy.getPassengers())) {
            if (!allowed.getAsBoolean() || !move(claim, record, (ServerPlayer) entity, survivor, allowed)) { return false; }
        }
        return allowed.getAsBoolean() && copy.getPassengers().isEmpty();
    }

    private boolean move(BayClaim claim, RocketTransferRecord record, ServerPlayer player, RocketEntity survivor,
                         java.util.function.BooleanSupplier allowed) {
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
            permittedPlayer = player; permittedSource = survivor; permittedHold = allowed;
            RocketTransferEntities.movePassenger(player, survivor, record.sourceSnapshot().sourceOrigin());
            return allowed.getAsBoolean() && player.getVehicle() == survivor;
        } finally { permittedPlayer = null; permittedSource = null; permittedHold = () -> true; }
    }
}
