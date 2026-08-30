package io.github.sunthemoon.advancedrocketrycommunity.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;

@Mod.EventBusSubscriber(
        modid = AdvancedRocketryCommunity.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.DEDICATED_SERVER
)
public final class ServerBootstrap {
    private ServerBootstrap() {
    }

    @SubscribeEvent
    public static void onDedicatedServerSetup(FMLDedicatedServerSetupEvent event) {
        AdvancedRocketryCommunity.LOGGER.debug("Dedicated server bootstrap initialized");
    }
}
