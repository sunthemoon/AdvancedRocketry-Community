package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * The v1.8 copies of the Moon, Mars and Venus dimension files and of the Mars and Venus noise settings (ADR-063
 * sections 5 and 8). The Moon becomes a noise Level with a two-biome source; Mars and Venus keep their v1.4 noise
 * router, base block and heights, copied from the v1.4 files without change, and only their biome sources and
 * surface rules are new. The earlier copies are excluded from the build as superseded.
 */
public final class V180PlanetDimensions implements DataProvider {
    /** The Venus patch layout's fixed salt: a biome source receives no world seed, so every world shares it. */
    public static final long VENUS_PATCH_SALT = 0x5645_4E55_5331_3830L;
    public static final int VENUS_PATCH_CELL = 32;

    private final PackOutput output;
    private final ExistingFileHelper existingFiles;

    public V180PlanetDimensions(PackOutput output, ExistingFileHelper existingFiles) {
        this.output = output;
        this.existingFiles = existingFiles;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        PackOutput.PathProvider dimensions = output.createPathProvider(PackOutput.Target.DATA_PACK, "dimension");
        PackOutput.PathProvider settings = output.createPathProvider(PackOutput.Target.DATA_PACK,
                "worldgen/noise_settings");
        List<CompletableFuture<?>> writes = new ArrayList<>();
        writes.add(DataProvider.saveStable(cache, moonDimension(), dimensions.json(ModIdentity.id("moon"))));
        writes.add(DataProvider.saveStable(cache, dimension("mars", fixed(V180PlanetWorldgen.FERRIC_REGOLITH)),
                dimensions.json(ModIdentity.id("mars"))));
        writes.add(DataProvider.saveStable(cache, dimension("venus", venusSource()),
                dimensions.json(ModIdentity.id("venus"))));
        writes.add(DataProvider.saveStable(cache, resurfaced("mars", V180PlanetWorldgen.marsSurface()),
                settings.json(ModIdentity.id("mars"))));
        writes.add(DataProvider.saveStable(cache, resurfaced("venus", V180PlanetWorldgen.venusSurface()),
                settings.json(ModIdentity.id("venus"))));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "v1.8 planetary dimensions";
    }

    private static JsonObject dimension(String body, JsonObject biomeSource) {
        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:noise");
        generator.add("biome_source", biomeSource);
        generator.addProperty("settings", ModIdentity.id(body).toString());
        JsonObject dimension = new JsonObject();
        dimension.addProperty("type", ModIdentity.id(body).toString());
        dimension.add("generator", generator);
        return dimension;
    }

    /**
     * The Moon's multi-noise source reads only continentalness, which the Moon router sets to its terrain noise:
     * below 0 the lowlands, from 0 the highlands; every other parameter spans its whole range.
     */
    static JsonObject moonDimension() {
        JsonArray biomes = new JsonArray();
        biomes.add(entry(V180PlanetWorldgen.REGOLITH_LOWLANDS, -1.0, 0.0));
        biomes.add(entry(V180PlanetWorldgen.REGOLITH_HIGHLANDS, 0.0, 1.0));
        JsonObject source = new JsonObject();
        source.addProperty("type", "minecraft:multi_noise");
        source.add("biomes", biomes);
        return dimension("moon", source);
    }

    private static JsonObject entry(ResourceKey<Biome> biome, double min, double max) {
        JsonObject parameters = new JsonObject();
        for (String name : List.of("temperature", "humidity", "erosion", "weirdness")) {
            parameters.add(name, range(-1.0, 1.0));
        }
        parameters.add("continentalness", range(min, max));
        parameters.addProperty("depth", 0.0);
        parameters.addProperty("offset", 0.0);
        JsonObject entry = new JsonObject();
        entry.addProperty("biome", biome.location().toString());
        entry.add("parameters", parameters);
        return entry;
    }

    private static JsonArray range(double min, double max) {
        JsonArray range = new JsonArray();
        range.add(min);
        range.add(max);
        return range;
    }

    private static JsonObject fixed(ResourceKey<Biome> biome) {
        JsonObject source = new JsonObject();
        source.addProperty("type", "minecraft:fixed");
        source.addProperty("biome", biome.location().toString());
        return source;
    }

    static JsonObject venusSource() {
        JsonArray biomes = new JsonArray();
        biomes.add(V180PlanetWorldgen.VOLCANIC.location().toString());
        biomes.add(V180PlanetWorldgen.VOLCANIC_LOWLANDS.location().toString());
        JsonObject source = new JsonObject();
        source.addProperty("type", ModIdentity.id("patches").toString());
        source.add("biomes", biomes);
        source.addProperty("cell_size", VENUS_PATCH_CELL);
        source.addProperty("salt", VENUS_PATCH_SALT);
        return source;
    }

    /** The v1.4 noise settings with only the surface rule replaced: the noise router is copied unchanged. */
    private JsonObject resurfaced(String body, SurfaceRules.RuleSource surface) {
        ResourceLocation id = ModIdentity.id(body);
        JsonObject earlier;
        try (Reader reader = new InputStreamReader(existingFiles.getResource(id, PackType.SERVER_DATA, ".json",
                "worldgen/noise_settings").open(), StandardCharsets.UTF_8)) {
            earlier = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read the v1.4 noise settings of " + id, exception);
        }
        JsonElement rule = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, surface)
                .getOrThrow(false, message -> { });
        JsonObject copy = earlier.deepCopy();
        copy.add("surface_rule", rule);
        return copy;
    }
}
