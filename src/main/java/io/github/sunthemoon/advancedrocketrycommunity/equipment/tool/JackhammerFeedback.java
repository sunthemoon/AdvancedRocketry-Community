package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Early informational feedback; final native item hooks still enforce disabled use. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JackhammerFeedback {
    static final int NOTICE_COOLDOWN_TICKS = 20;
    // Informational only, never saved. Logout/server-stop cleanup and weak keys bound its lifetime to live players.
    private static final Map<Player, Integer> LAST_NOTICE = new WeakHashMap<>();

    private JackhammerFeedback() { }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMiningStart(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getSide() == LogicalSide.SERVER
                && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && event.getItemStack().getItem() instanceof JackhammerItem
                && !CommonConfig.classicEquipmentEnabled()
                && ready(LAST_NOTICE.get(event.getEntity()), event.getEntity().tickCount)) {
            Player player = event.getEntity();
            LAST_NOTICE.put(player, player.tickCount);
            player.displayClientMessage(
                    Component.translatable("message.advancedrocketrycommunity.jackhammer.disabled"), true);
        }
    }

    static boolean ready(Integer previous, int now) {
        if (previous == null) { return true; }
        int elapsed = now - previous;
        return elapsed < 0 || elapsed >= NOTICE_COOLDOWN_TICKS;
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) { LAST_NOTICE.remove(event.getEntity()); }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { LAST_NOTICE.clear(); }
}
