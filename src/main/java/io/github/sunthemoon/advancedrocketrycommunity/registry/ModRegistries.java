package io.github.sunthemoon.advancedrocketrycommunity.registry;

import net.minecraftforge.eventbus.api.IEventBus;

/** Central registration boundary for the current vertical slice. */
public final class ModRegistries {
    private ModRegistries() {
    }

    public static void register(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenuTypes.register(modBus);
        ModRecipes.register(modBus);
        ModSounds.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTabs.register(modBus);
    }
}
