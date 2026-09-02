package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteDefinitionReloadListener;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Generates the one complete, bounded v0.8 data-satellite definition. */
public final class SatelliteDefinitionProvider implements DataProvider {
    private final PackOutput.PathProvider paths;

    public SatelliteDefinitionProvider(PackOutput output) {
        paths = output.createPathProvider(
                PackOutput.Target.DATA_PACK,
                SatelliteDefinitionReloadListener.DIRECTORY
        );
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        SatelliteDefinition definition = new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                120,
                100,
                List.of(CelestialIds.EARTH_ID, CelestialIds.MOON_ID, CelestialIds.SPACE_ID)
        );
        return DataProvider.saveStable(output, encode(definition), paths.json(definition.id()));
    }

    @Override
    public String getName() {
        return "ARCE v0.8 satellite definitions";
    }

    private static JsonElement encode(SatelliteDefinition definition) {
        return SatelliteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .getOrThrow(false, message -> {
                    throw new IllegalStateException(message);
                });
    }
}
