package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * ADR-066 §6.2 completion points. Callers invoke these after the server has committed the event; nothing here
 * accepts a client claim, loads a chunk, consumes an item or changes the world.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class ClassicAdvancementEvents {
    public static final ClassicEventTrigger TRIGGER = new ClassicEventTrigger();
    /** Players this close to the controller witness a formation (an open ADR choice, see the W12 record). */
    public static final double FORMATION_WITNESS_RADIUS = 16.0D;

    private ClassicAdvancementEvents() {
    }

    /** A new binding generation of a classic multiblock reached FORMED at {@code controller}. */
    public static void multiblockFormed(ServerLevel level, BlockPos controller) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(controller, "controller");
        Vec3 centre = Vec3.atCenterOf(controller);
        double reach = FORMATION_WITNESS_RADIUS * FORMATION_WITNESS_RADIUS;
        for (ServerPlayer player : List.copyOf(level.getServer().getPlayerList().getPlayers())) {
            if (player.serverLevel() == level && eligible(player) && player.distanceToSqr(centre) <= reach) {
                TRIGGER.trigger(player, ClassicEvent.FORMED_MULTIBLOCK);
            }
        }
    }

    /** A committed rocket descent completed in {@code level}; {@code aboard} rode the landed authority. */
    public static void rocketLanded(ServerLevel level, Collection<ServerPlayer> aboard) {
        Objects.requireNonNull(level, "level");
        if (!level.dimension().equals(CelestialIds.MOON_LEVEL)) {
            return;
        }
        for (ServerPlayer player : List.copyOf(aboard)) {
            if (player.serverLevel() == level && eligible(player)) {
                TRIGGER.trigger(player, ClassicEvent.MOON_LANDING);
            }
        }
    }

    /** A checked station warp committed; {@code present} were in the station region with VISIT access. */
    public static void stationWarped(Collection<ServerPlayer> present) {
        for (ServerPlayer player : List.copyOf(present)) {
            if (eligible(player)) {
                TRIGGER.trigger(player, ClassicEvent.STATION_WARP);
            }
        }
    }

    /**
     * Runs after vanilla validated the use packet (reach, teleport acknowledgement, spawn protection). Canceled
     * events never arrive here; this neither cancels the event nor changes its result, so vanilla still places
     * the seat or does nothing.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)
                || event.getUseItem() == Event.Result.DENY || !eligible(player)
                || !event.getItemStack().is(ModItems.ROCKET_SEAT.get())
                || !event.getLevel().isLoaded(event.getPos())
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.TNT)) {
            return;
        }
        TRIGGER.trigger(player, ClassicEvent.SEAT_ON_TNT);
    }

    /**
     * A real, connected, living, non-spectator player that the server's player list currently owns. W44 section 5:
     * the one shared predicate for world-first capture and deferred grants, so it is deliberately public.
     */
    public static boolean eligible(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return !(player instanceof FakePlayer) && server != null && !player.hasDisconnected()
                && player.isAlive() && !player.isSpectator()
                && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        /** Vanilla's trigger table is a plain map, so registration runs once on the main thread. */
        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> CriteriaTriggers.register(TRIGGER));
        }
    }
}
