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
        // v1.8 (ADR-063): the classic material set, its ores and the small plate press.
        io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent.register(modBus);
        // v1.8 (ADR-063 section 5): the Moon, Mars and Venus surface blocks and world generators.
        io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent.register(modBus);
        io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.SurfaceWorldgen.register(modBus);
        // v1.8 (ADR-063 section 6): the Tau Ceti f and g blocks and world features.
        io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks.register(modBus);
        io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.ExoplanetWorldgen.register(modBus);
    }
}
