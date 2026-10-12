package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ADR-066 section 5.2 visors, physical client only. The state is recomputed every client tick from the local
 * player's correctly worn helmet with the bounded equipment codec and server authorization, and cleared at logout, Level change and resource
 * reload. Anti-fog removes only ARCE's atmospheric fog distance; Earthbright brightens the lightmap only in the
 * Space Level. Neither touches potion effects, vanilla fog or server hazards.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SuitVisorClient {
    private static final SuitVisorState state = new SuitVisorState();

    /** Whether ARCE's atmospheric fog-distance override applies; the Space Level has no atmosphere to clear. */
    public static boolean atmosphericFogShown(ClientLevel level) {
        return level == null || level.dimension().equals(CelestialIds.SPACE_LEVEL)
                || ClientConfig.atmosphericNausea() && !state.antiFog();
    }

    public static boolean earthbright(ClientLevel level) {
        return state.earthbright() && level != null && level.dimension().equals(CelestialIds.SPACE_LEVEL);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        var player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().level == null) { clear(); return; }
        if (!state.enabled()) { clear(); return; }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        var modules = ArmorEquipment.part(helmet) == SuitPart.HEAD ? ArmorEquipment.modules(helmet) : null;
        state.observe(modules != null && modules.stream().anyMatch(module -> module.kind() == ModuleKind.ANTI_FOG),
                modules != null && modules.stream().anyMatch(module -> module.kind() == ModuleKind.EARTHBRIGHT));
    }

    @SubscribeEvent public static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) { authorize(false); }

    @SubscribeEvent public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) { authorize(false); }

    @SubscribeEvent public static void levelChanged(ClientPlayerNetworkEvent.Clone event) { clear(); }

    public static void clear() { state.clear(); }

    public static void authorize(boolean enabled) { state.authorize(enabled); }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
        }

        private Setup() { }
    }

    private SuitVisorClient() { }
}
