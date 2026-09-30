package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Generates the ADR-043 example star system and the v1.5 data-satellite definition. */
public final class V150StarSystemProvider implements DataProvider {
    private final PackOutput output;

    public V150StarSystemProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        var bodies = output.createPathProvider(PackOutput.Target.DATA_PACK, "celestial_bodies");
        var satellites = output.createPathProvider(PackOutput.Target.DATA_PACK, "satellite_definitions");
        var survey = StarSystemContent.surveySatellite();
        writes.add(DataProvider.saveStable(cache, SatelliteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, survey)
                .getOrThrow(false, message -> { }), satellites.json(survey.id())));
        for (var body : StarSystemContent.definitions()) {
            writes.add(DataProvider.saveStable(cache, CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, body)
                    .getOrThrow(false, message -> { }), bodies.json(body.id())));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "ARCE v1.5 example star system and data satellite";
    }
}
