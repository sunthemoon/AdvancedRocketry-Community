package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.BeaconMarkerCache;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewCache;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ADR-065 section 11.1: the {@code classic_controls} client caches end with the session and the Level. */
@Mod.EventBusSubscriber(
        modid = AdvancedRocketryCommunity.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class ClientClassicControlEvents {
    private ClientClassicControlEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClassicControlViewCache.clear();
        BeaconMarkerCache.clear();
    }

    @SubscribeEvent
    public static void onLevelChanged(ClientPlayerNetworkEvent.Clone event) {
        ClassicControlViewCache.clear();
        BeaconMarkerCache.clear();
    }
}
