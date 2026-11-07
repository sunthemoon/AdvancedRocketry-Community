package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Position;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Save an ordinary stationary rider's native exit instead of its high seat; never release membership. */
public final class RocketPassengerLogout {
    private RocketPassengerLogout() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasDisconnected()
                || !player.getServer().isSameThread() || !player.isAlive() || player.isRemoved()
                || !(player.getVehicle() instanceof RocketEntity rocket) || player.level() != rocket.level()
                || !rocket.operational() || !RocketTransferEntities.loadedEntity(rocket)) { return; }
        var flight = rocket.flightData().orElseThrow();
        if (!RocketFlightService.stationary(flight.state()) || flight.activeTransferId().isPresent()
                || BayPassengerExitPermit.guarded(player) || RocketReleaseAccess.fenced(rocket)
                || BayReceipts.holdsRocket(player.getServer(), rocket.assemblyTransactionId().orElseThrow())) { return; }
        var exits = RocketRuntime.passengerExits().orElse(null);
        if (exits == null || exits.state.internal(player)) { return; }

        var target = rocket.getDismountLocationForPassenger(player);
        var size = player.getDimensions(Pose.STANDING);
        var box = new Box(new Position(player.serverLevel().dimension().location().toString(), target.x, target.y, target.z),
                size.width, size.height);
        var probe = exits.budget.openProbe().orElse(null);
        if (probe == null) { return; }
        try (probe) {
            if (BayExitSafety.loaded(new BayPassengerExitWorld(player)).inspect(box, probe, (candidate, work) -> true)
                    != BayExitSafety.Verdict.SAFE) { return; }
        }
        // PlayerList.remove calls this same native path after saving. Move it before the save, not through LEAVE.
        // Native mount-event cancellation is respected; no internal-move capability or roster mutation is needed.
        player.unRide();
    }
}
