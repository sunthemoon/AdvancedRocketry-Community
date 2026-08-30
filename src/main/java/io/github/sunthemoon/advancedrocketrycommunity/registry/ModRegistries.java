package io.github.sunthemoon.advancedrocketrycommunity.registry;

import net.minecraftforge.eventbus.api.IEventBus;

/** Central registration boundary for the current vertical slice. */
public final class ModRegistries {
    private ModRegistries() {
    }

    public static void register(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModSounds.register(modBus);
        ModCreativeTabs.register(modBus);
    }
}
