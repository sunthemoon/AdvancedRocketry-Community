package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Generates v1.4 bodies/routes and the current data-satellite target definition. */
public final class PlanetaryContentProvider implements DataProvider {
    private final PackOutput output;

    public PlanetaryContentProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        var bodies = output.createPathProvider(PackOutput.Target.DATA_PACK, "celestial_bodies");
        var routes = output.createPathProvider(PackOutput.Target.DATA_PACK, "travel_routes");
        var satellites = output.createPathProvider(PackOutput.Target.DATA_PACK, "satellite_definitions");
        var survey = PlanetaryContent.surveySatellite();
        writes.add(DataProvider.saveStable(cache,
                io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition.CODEC
                        .encodeStart(JsonOps.INSTANCE, survey).getOrThrow(false, message -> {}), satellites.json(survey.id())));
        for (var body : PlanetaryContent.definitions()) {
            writes.add(DataProvider.saveStable(cache, CelestialBodyDefinition.CODEC
                    .encodeStart(JsonOps.INSTANCE, body).getOrThrow(false, message -> {}), bodies.json(body.id())));
        }
        for (var route : PlanetaryContent.routes()) {
            writes.add(DataProvider.saveStable(cache, RouteDefinition.CODEC
                    .encodeStart(JsonOps.INSTANCE, route).getOrThrow(false, message -> {}), routes.json(route.id())));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "ARCE v1.4 planetary definitions and routes";
    }
}
