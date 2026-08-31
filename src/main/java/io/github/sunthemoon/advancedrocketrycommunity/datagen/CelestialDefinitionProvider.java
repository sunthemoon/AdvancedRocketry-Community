package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialDefinitionReloadListener;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Generates the canonical built-in Earth, Moon, and Space definitions. */
public final class CelestialDefinitionProvider implements DataProvider {
    private final PackOutput.PathProvider paths;

    public CelestialDefinitionProvider(PackOutput output) {
        paths = output.createPathProvider(
                PackOutput.Target.DATA_PACK,
                CelestialDefinitionReloadListener.DIRECTORY
        );
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        CompletableFuture<?>[] writes = CelestialDefaults.definitions().stream()
                .map(definition -> DataProvider.saveStable(
                        output,
                        encode(definition),
                        paths.json(definition.id())
                ))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(writes);
    }

    @Override
    public String getName() {
        return "ARCE v0.3 celestial definitions";
    }

    private static JsonElement encode(CelestialBodyDefinition definition) {
        return CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .getOrThrow(false, message -> {
                    throw new IllegalStateException(message);
                });
    }
}
