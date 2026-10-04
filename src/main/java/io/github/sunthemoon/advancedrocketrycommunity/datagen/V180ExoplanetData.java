package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfile;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * The C15c celestial data (ADR-063 section 6 with revision 6): Tau Ceti f and g, their three routes, the v1.8 data
 * satellite that supersedes the v1.5 copy (section 8), and the two sky profiles from the canonical builtin set.
 * Earlier version outputs remain immutable; their providers do not run during v1.8 data generation.
 */
public final class V180ExoplanetData implements DataProvider {
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180ExoplanetData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (server) {
            var bodies = output.createPathProvider(PackOutput.Target.DATA_PACK, "celestial_bodies");
            for (CelestialBodyDefinition body : ExoplanetContent.definitions()) {
                writes.add(DataProvider.saveStable(cache, CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE,
                        body).getOrThrow(false, message -> { }), bodies.json(body.id())));
            }
            var routes = output.createPathProvider(PackOutput.Target.DATA_PACK, "travel_routes");
            for (RouteDefinition route : ExoplanetContent.routes()) {
                writes.add(DataProvider.saveStable(cache, RouteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, route)
                        .getOrThrow(false, message -> { }), routes.json(route.id())));
            }
            var satellites = output.createPathProvider(PackOutput.Target.DATA_PACK, "satellite_definitions");
            SatelliteDefinition satellite = ExoplanetContent.dataSatellite();
            writes.add(DataProvider.saveStable(cache, SatelliteDefinition.CODEC.encodeStart(JsonOps.INSTANCE,
                    satellite).getOrThrow(false, message -> { }), satellites.json(satellite.id())));
        }
        if (client) {
            var profiles = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, SkyProfile.DIRECTORY);
            ExoplanetContent.skyProfiles().forEach((id, profile) ->
                    writes.add(DataProvider.saveStable(cache, profile.encode(), profiles.json(id))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "v1.8 exoplanet celestial data";
    }
}
