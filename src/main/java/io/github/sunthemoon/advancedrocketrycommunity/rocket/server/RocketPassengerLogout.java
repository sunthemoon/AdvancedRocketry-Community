package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Position;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.LogoutExitSafety;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Save an ordinary stationary rider at its native exit instead of its high seat; never release membership.
 *
 * <p>W312F (R375): the save keeps R364's place, where LEAVE SEAT puts the rider. The logout's own proof
 * ({@link LogoutExitSafety}) only asks whether the player would be hurt there after logging in.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class RocketPassengerLogout {
    private RocketPassengerLogout() { }

    /** PlayerList.remove fires this before it saves the player, then detaches it natively after the save. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasDisconnected()) { attempt(player); }
    }

    /**
     * W312F (R375, review F2): stopServer saves every online player before removeAll, and that disconnect normally never
     * reaches PlayerList.remove. Detach riders before that save. No rocket or seat input ticks after this event, so the
     * seat stays assigned exactly as at a logout.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (!server.isSameThread()) { return; }
        int riders = 0, detached = 0;
        for (var player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!(player.getVehicle() instanceof RocketEntity)) { continue; }
            riders++;
            if (attempt(player)) { detached++; }
        }
        if (riders > 0) { AdvancedRocketryCommunity.LOGGER.info("ARCE_ROCKET_STOP_EXIT riders={} detached={}", riders, detached); }
    }

    /** W312F (review F5): an unexpected failure logs one WARN and leaves this player's save as it was before W312. */
    static boolean attempt(ServerPlayer player) {
        Entity vehicle = null;
        try {
            vehicle = player.getVehicle();
            return vehicle instanceof RocketEntity rocket && detach(player, rocket);
        } catch (RuntimeException unexpected) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_ROCKET_LOGOUT_EXIT_FAILED player={} uuid={} rocket={}; the save is unchanged",
                    player.getGameProfile().getName(), player.getUUID(), vehicle == null ? "none" : vehicle.getUUID(), unexpected);
            return false;
        }
    }

    private static boolean detach(ServerPlayer player, RocketEntity rocket) {
        if (!player.getServer().isSameThread() || !player.isAlive() || player.isRemoved() || player.level() != rocket.level()
                || !rocket.operational() || !RocketTransferEntities.loadedEntity(rocket)) { return false; }
        var flight = rocket.flightData().orElseThrow();
        if (!RocketFlightService.stationary(flight.state()) || flight.activeTransferId().isPresent()
                || BayPassengerExitPermit.guarded(player) || RocketReleaseAccess.fenced(rocket)
                || BayReceipts.holdsRocket(player.getServer(), rocket.assemblyTransactionId().orElseThrow())) { return false; }
        var exits = RocketRuntime.passengerExits().orElse(null);
        if (exits == null || exits.state.internal(player)) { return false; }

        var target = rocket.getDismountLocationForPassenger(player);
        var size = player.getDimensions(Pose.STANDING);
        var box = new Box(new Position(player.serverLevel().dimension().location().toString(), target.x, target.y, target.z),
                size.width, size.height);
        var probe = exits.budget.openProbe().orElse(null);
        if (probe == null) { return false; }
        try (probe) {
            if (LogoutExitSafety.inspect(new RocketLogoutExitWorld(player, rocket, probe), box, probe).verdict()
                    != BayExitSafety.Verdict.SAFE) { return false; }
        }
        // PlayerList.remove calls this same native path after saving. Move it before the save, not through LEAVE.
        // Native mount-event cancellation is respected; no internal-move capability or roster mutation is needed.
        player.unRide();
        return player.getVehicle() == null;
    }
}
