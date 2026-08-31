package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** Generates the fixed Moon and Space dynamic-registry data selected by ADR-001. */
public final class FixedDimensionProvider implements DataProvider {
    private final PackOutput.PathProvider dimensionTypes;
    private final PackOutput.PathProvider dimensions;

    public FixedDimensionProvider(PackOutput output) {
        dimensionTypes = output.createPathProvider(PackOutput.Target.DATA_PACK, "dimension_type");
        dimensions = output.createPathProvider(PackOutput.Target.DATA_PACK, "dimension");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        CompletableFuture<?> moonType = DataProvider.saveStable(
                output,
                dimensionType(true, 0.1D, "minecraft:overworld"),
                dimensionTypes.json(CelestialIds.MOON_TYPE.location())
        );
        CompletableFuture<?> spaceType = DataProvider.saveStable(
                output,
                dimensionType(false, 0.0D, "minecraft:the_end"),
                dimensionTypes.json(CelestialIds.SPACE_TYPE.location())
        );
        CompletableFuture<?> moon = DataProvider.saveStable(
                output,
                flatDimension(CelestialIds.MOON_TYPE.location(), "minecraft:plains", moonLayers()),
                dimensions.json(CelestialIds.MOON_LEVEL.location())
        );
        CompletableFuture<?> space = DataProvider.saveStable(
                output,
                flatDimension(CelestialIds.SPACE_TYPE.location(), "minecraft:the_void", new JsonArray()),
                dimensions.json(CelestialIds.SPACE_LEVEL.location())
        );
        return CompletableFuture.allOf(moonType, spaceType, moon, space);
    }

    @Override
    public String getName() {
        return "ARCE v0.3 fixed Moon and Space dimensions";
    }

    private static JsonObject dimensionType(boolean skylight, double ambientLight, String effects) {
        JsonObject type = new JsonObject();
        type.addProperty("ultrawarm", false);
        type.addProperty("natural", false);
        type.addProperty("piglin_safe", false);
        type.addProperty("respawn_anchor_works", false);
        type.addProperty("bed_works", true);
        type.addProperty("has_raids", false);
        type.addProperty("has_skylight", skylight);
        type.addProperty("has_ceiling", false);
        type.addProperty("coordinate_scale", 1.0D);
        type.addProperty("ambient_light", ambientLight);
        type.addProperty("fixed_time", 18_000L);
        type.addProperty("effects", effects);
        type.addProperty("min_y", 0);
        type.addProperty("height", 256);
        type.addProperty("logical_height", 256);
        type.addProperty("infiniburn", "#minecraft:infiniburn_overworld");
        type.addProperty("monster_spawn_block_light_limit", 0);
        type.addProperty("monster_spawn_light_level", 0);
        return type;
    }

    private static JsonObject flatDimension(
            ResourceLocation dimensionType,
            String biome,
            JsonArray layers
    ) {
        JsonObject settings = new JsonObject();
        settings.addProperty("biome", biome);
        settings.addProperty("lakes", false);
        settings.addProperty("features", false);
        settings.add("layers", layers);
        settings.add("structure_overrides", new JsonArray());

        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:flat");
        generator.add("settings", settings);

        JsonObject dimension = new JsonObject();
        dimension.addProperty("type", dimensionType.toString());
        dimension.add("generator", generator);
        return dimension;
    }

    private static JsonArray moonLayers() {
        JsonArray layers = new JsonArray();
        layers.add(layer("minecraft:bedrock", 1));
        layers.add(layer("minecraft:end_stone", 3));
        return layers;
    }

    private static JsonObject layer(String block, int height) {
        JsonObject layer = new JsonObject();
        layer.addProperty("block", block);
        layer.addProperty("height", height);
        return layer;
    }
}
