package io.github.sunthemoon.advancedrocketrycommunity.celestial.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogDecoder;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalogDecoder;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Prepares both winning resource sets off-thread; applies one complete pair on the reload executor. */
public final class PlanetaryDefinitionReloadListener extends SimplePreparableReloadListener<DataResult<PlanetaryCatalog>> {
    public static final String BODY_DIRECTORY = "celestial_bodies";
    public static final String ROUTE_DIRECTORY = "travel_routes";
    private final PlanetaryCatalogManager manager;

    public PlanetaryDefinitionReloadListener(PlanetaryCatalogManager manager) {
        this.manager = manager;
    }

    @Override
    protected DataResult<PlanetaryCatalog> prepare(ResourceManager resources, ProfilerFiller profiler) {
        ReloadDiagnostics errors = new ReloadDiagnostics();
        var bodies = read(resources, BODY_DIRECTORY, CelestialCatalog.MAX_BODIES, BoundedDefinitionJson.MAX_BODY_BYTES, errors);
        var routes = read(resources, ROUTE_DIRECTORY, RouteLimits.MAX_ROUTES, BoundedDefinitionJson.MAX_ROUTE_BYTES, errors);
        if (!errors.empty()) {
            return DataResult.error(errors::message);
        }
        return CelestialCatalogDecoder.decode(bodies).flatMap(celestial -> RouteCatalogDecoder.decode(routes,
                        celestial.definitions().stream().map(body -> body.id()).toList())
                .flatMap(graph -> PlanetaryCatalog.create(celestial, graph)))
                .mapError(ReloadDiagnostics::bound);
    }

    private static Map<ResourceLocation, JsonElement> read(ResourceManager resources, String directory,
            int maxCount, int maxBytes, ReloadDiagnostics errors) {
        Map<ResourceLocation, JsonElement> decoded = new LinkedHashMap<>();
        if (errors.full()) {
            return decoded;
        }
        try {
            Map<ResourceLocation, Resource> found = resources.listResources(directory,
                    id -> id.getPath().endsWith(".json"));
            if (found.size() > maxCount) {
                errors.add(directory + ": resource count exceeds " + maxCount);
                return decoded;
            }
            for (var entry : found.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (errors.full()) {
                    break;
                }
                try {
                    String path = entry.getKey().getPath();
                    if (!path.startsWith(directory + "/") || !path.endsWith(".json")) {
                        throw new IOException("invalid definition resource path");
                    }
                    String relative = path.substring(directory.length() + 1, path.length() - 5);
                    var id = new ResourceLocation(entry.getKey().getNamespace(), relative);
                    if (relative.isEmpty() || id.toString().length() > BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS) {
                        throw new IOException("definition resource ID exceeds its bound or is empty");
                    }
                    try (var input = entry.getValue().open()) {
                        if (decoded.putIfAbsent(id, BoundedDefinitionJson.read(input, maxBytes)) != null) {
                            throw new IOException("duplicate definition resource ID");
                        }
                    }
                } catch (IOException | IllegalArgumentException exception) {
                    errors.add(entry.getKey() + ": " + exception.getMessage());
                }
            }
        } catch (IllegalArgumentException exception) {
            errors.add(directory + ": " + exception.getMessage());
        }
        return decoded;
    }

    @Override
    protected void apply(DataResult<PlanetaryCatalog> candidate, ResourceManager resources, ProfilerFiller profiler) {
        boolean hadValidPair = manager.capture().isPresent();
        if (manager.applyCandidate(candidate)) {
            var status = manager.status();
            AdvancedRocketryCommunity.LOGGER.info("Accepted planetary catalog generation {} with {} bodies, {} routes and {} anchors",
                    status.generation(), status.bodyCount(), status.routeCount(), status.anchorCount());
            return;
        }
        String message = manager.status().message();
        AdvancedRocketryCommunity.LOGGER.error("Rejected planetary catalog; last valid generation remains active: {}", message);
        if (!hadValidPair) {
            throw new IllegalStateException("Initial planetary catalog is invalid: " + message);
        }
    }
}
