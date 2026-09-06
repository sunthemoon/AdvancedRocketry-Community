package io.github.sunthemoon.advancedrocketrycommunity.travel.route.service;

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

/** Server reload listener that atomically publishes validated travel routes. */
public final class RouteDefinitionReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "travel_routes";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final RouteCatalogManager manager;
    private final CelestialCatalogManager celestialCatalogs;

    public RouteDefinitionReloadListener(
            RouteCatalogManager manager,
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
        DataResult<RouteCatalog> candidate = celestial == null
                ? DataResult.error(() -> "No valid celestial catalog is active")
                : RouteCatalogDecoder.decode(
                        resources,
                        celestial.definitions().stream().map(definition -> definition.id()).toList()
                );
        if (manager.applyCandidate(candidate)) {
            RouteCatalogManager.ReloadStatus status = manager.status();
            AdvancedRocketryCommunity.LOGGER.info(
                    "Accepted route catalog generation {} with {} routes and {} anchors",
                    status.generation(),
                    status.routeCount(),
                    status.anchorCount()
            );
            return;
        }

        String message = manager.status().message();
        AdvancedRocketryCommunity.LOGGER.error(
                "Rejected route catalog; last valid generation remains active: {}",
                message
        );
        if (!hadValidCatalog) {
            throw new IllegalStateException("Initial route catalog is invalid: " + message);
        }
    }
}
