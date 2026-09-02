package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Server reload listener that preserves the last complete valid definition set. */
public final class SatelliteDefinitionReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "satellite_definitions";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final SatelliteCatalogManager manager;
    private final CelestialCatalogManager celestialCatalogs;

    public SatelliteDefinitionReloadListener(
            SatelliteCatalogManager manager,
            CelestialCatalogManager celestialCatalogs
    ) {
        super(GSON, DIRECTORY);
        this.manager = manager;
        this.celestialCatalogs = celestialCatalogs;
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        boolean hadValidCatalog = manager.current().isPresent();
        CelestialCatalog celestial = celestialCatalogs.current().orElse(null);
        DataResult<SatelliteCatalog> candidate = celestial == null
                ? DataResult.error(() -> "No valid celestial catalog is active")
                : SatelliteCatalogDecoder.decode(
                        resources,
                        celestial.definitions().stream().map(definition -> definition.id()).toList()
                );
        if (manager.applyCandidate(candidate)) {
            SatelliteCatalogManager.ReloadStatus status = manager.status();
            AdvancedRocketryCommunity.LOGGER.info(
                    "Accepted satellite catalog generation {} with {} definitions",
                    status.generation(),
                    status.definitionCount()
            );
            return;
        }
        String message = manager.status().message();
        AdvancedRocketryCommunity.LOGGER.error(
                "Rejected satellite catalog; last valid generation remains active: {}",
                message
        );
        if (!hadValidCatalog) {
            throw new IllegalStateException("Initial satellite catalog is invalid: " + message);
        }
    }
}
