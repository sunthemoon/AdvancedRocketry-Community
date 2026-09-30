package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewCache;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SurveyScanResultCache;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = AdvancedRocketryCommunity.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class ClientSatelliteEvents {
    private ClientSatelliteEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SatelliteTerminalViewCache.clear();
        SurveyScanResultCache.clear();
    }

    /** A finished survey scan opens once no other screen is open. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && minecraft.player != null && minecraft.screen == null) {
            SurveyScanResultCache.take().ifPresent(result -> minecraft.setScreen(new SurveyScanScreen(result)));
        }
    }
}
