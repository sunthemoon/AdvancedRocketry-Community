package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
        modid = AdvancedRocketryCommunity.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(
                ModMenuTypes.ELECTROLYZER.get(),
                ElectrolyzerScreen::new
        ));
        AdvancedRocketryCommunity.LOGGER.debug("Client bootstrap initialized");
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0xB7D5DD : 0xFFFFFF,
                ModItems.EMPTY_CANISTER.get()
        );
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0x71E5EE : 0xFFFFFF,
                ModItems.HYDROGEN_CANISTER.get()
        );
        event.register(
                (stack, tintIndex) -> tintIndex == 0 ? 0x79AFFF : 0xFFFFFF,
                ModItems.OXYGEN_CANISTER.get()
        );
    }
}
