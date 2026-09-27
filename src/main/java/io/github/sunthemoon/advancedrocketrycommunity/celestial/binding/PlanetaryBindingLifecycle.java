package io.github.sunthemoon.advancedrocketrycommunity.celestial.binding;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import java.io.IOException;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

/** Attach before Levels and gameplay exist; initial resources alone cannot identify a world. */
public final class PlanetaryBindingLifecycle {
    private final PlanetaryCatalogManager catalogs;

    public PlanetaryBindingLifecycle(PlanetaryCatalogManager catalogs) {
        this.catalogs = catalogs;
    }

    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            int count = catalogs.bindWorld(event.getServer().getWorldPath(LevelResource.ROOT));
            AdvancedRocketryCommunity.LOGGER.info("Planetary bindings ready: {} recorded bodies", count);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Planetary bindings blocked startup; preserve the world and its data packs", exception);
        }
    }
}
