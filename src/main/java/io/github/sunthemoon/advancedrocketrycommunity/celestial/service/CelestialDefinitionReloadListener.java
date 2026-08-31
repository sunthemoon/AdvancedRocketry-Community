package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Server reload listener that publishes only fully validated catalogs. */
public final class CelestialDefinitionReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "celestial_bodies";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final CelestialCatalogManager manager;

    public CelestialDefinitionReloadListener(CelestialCatalogManager manager) {
        super(GSON, DIRECTORY);
        this.manager = manager;
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        boolean hadValidCatalog = manager.current().isPresent();
        DataResult<CelestialCatalog> candidate = CelestialCatalogDecoder.decode(resources);
        if (manager.applyCandidate(candidate)) {
            CelestialCatalogManager.ReloadStatus status = manager.status();
            AdvancedRocketryCommunity.LOGGER.info(
                    "Accepted celestial catalog generation {} with {} bodies",
                    status.generation(),
                    status.bodyCount()
            );
            return;
        }

        String message = manager.status().message();
        AdvancedRocketryCommunity.LOGGER.error(
                "Rejected celestial catalog; last valid generation remains active: {}",
                message
        );
        if (!hadValidCatalog) {
            throw new IllegalStateException("Initial celestial catalog is invalid: " + message);
        }
    }
}
