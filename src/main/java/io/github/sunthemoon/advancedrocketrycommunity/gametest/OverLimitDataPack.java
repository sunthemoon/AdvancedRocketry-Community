package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeJsonSignature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * W179 (VDOC 12.4): the world data pack that {@link DataPackOverLimitReloadGameTests} generates. Every classic recipe
 * type gets each over-limit shape and one invalid shape; some recipes load but name a tag over the 32-variant bound,
 * a tag that vanilla refuses, or an unknown item; item tags are oversized, refused or unreadable; and the pattern
 * directory holds more files than a catalog may define.
 */
final class OverLimitDataPack {
    static final String NAME = "arce_w179_overlimit";
    static final String ID = "file/" + NAME;
    static final String NAMESPACE = "arce_w179";
    static final List<String> CLASSIC_TYPES = List.of("lathe", "arc_furnace", "cutting", "crystallizing",
            "chemical_reacting", "laser_etching", "centrifuging");
    /** More pattern files than {@link MultiblockPatternCatalog#MAX_DEFINITIONS}, before the mod's own patterns. */
    static final int PATTERN_FILES = MultiblockPatternCatalog.MAX_DEFINITIONS + 44;
    static final ResourceLocation OVERSIZED_TAG = id("oversized");
    static final ResourceLocation MISSING_REFERENCE_TAG = id("missing_reference");
    static final ResourceLocation UNREADABLE_TAG = id("unreadable");
    static final ResourceLocation MISSING_ITEM = id("does_not_exist");
    private static final List<String> COLOURS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private record Shape(String name, String reason, UnaryOperator<JsonObject> apply) { }

    /** Over the codec's text, depth, node and row bounds; each classic type gets all four. */
    private static final List<Shape> OVER_LIMIT = List.of(
            new Shape("oversized", "Recipe text bound", recipe -> {
                first(recipe, "item_outputs").addProperty("pad", "x".repeat(RecipeJsonSignature.MAX_BYTES));
                return recipe;
            }),
            new Shape("deep", "Recipe structural bound", recipe -> {
                JsonElement nested = item("minecraft:iron_ingot");
                for (int depth = 0; depth < RecipeJsonSignature.MAX_DEPTH; depth++) {
                    JsonArray wrapper = new JsonArray();
                    wrapper.add(nested);
                    nested = wrapper;
                }
                first(recipe, "item_inputs").add("ingredient", nested);
                return recipe;
            }),
            new Shape("wide", "Recipe structural bound", recipe -> {
                JsonArray pad = new JsonArray();
                for (int node = 0; node <= RecipeJsonSignature.MAX_NODES; node++) {
                    pad.add(0);
                }
                recipe.add("pad", pad);
                return recipe;
            }),
            new Shape("rows", "Recipe row type/count", recipe -> {
                JsonArray rows = new JsonArray();
                for (int row = 0; row < 5; row++) {
                    rows.add(row(item("minecraft:iron_ingot"), 1));
                }
                recipe.add("item_inputs", rows);
                return recipe;
            }));
    /** Invalid within the bounds; the classic types take one each, in order. */
    private static final List<Shape> INVALID = List.of(
            new Shape("unknown_field", "Recipe missing/unknown field", recipe -> {
                recipe.addProperty("extra", 1);
                return recipe;
            }),
            new Shape("schema", "Recipe number range", recipe -> {
                recipe.addProperty("schema_version", 2);
                return recipe;
            }),
            new Shape("fraction", "Recipe number is not an exact int", recipe -> {
                recipe.addProperty("processing_time", 1.5D);
                return recipe;
            }),
            new Shape("null", "Null or unexpected recipe token", recipe -> {
                recipe.add("energy_per_tick", JsonNull.INSTANCE);
                return recipe;
            }),
            new Shape("noncanonical", "Noncanonical identifier", recipe -> {
                first(recipe, "item_outputs").addProperty("item", "minecraft:Iron_Nugget");
                return recipe;
            }),
            new Shape("count", "Recipe number range", recipe -> {
                first(recipe, "item_inputs").addProperty("count", 65);
                return recipe;
            }),
            new Shape("fluid_rows", "Recipe row type/count", recipe -> {
                JsonArray rows = new JsonArray();
                for (int row = 0; row < 3; row++) {
                    JsonObject fluid = new JsonObject();
                    fluid.addProperty("fluid", "minecraft:water");
                    fluid.addProperty("amount", 1_000);
                    rows.add(fluid);
                }
                recipe.add("fluid_inputs", rows);
                return recipe;
            }));

    /** Recipes the classic codec refuses while loading, with the refusal's message. */
    final Map<ResourceLocation, String> refusedAtLoad = new TreeMap<>();
    /** Recipe files that are not JSON objects at all; vanilla refuses them before any serializer. */
    final Set<ResourceLocation> unreadable = new TreeSet<>();
    /** Recipes that load and are refused when a machine resolves them, with the refusal's message. */
    final Map<ResourceLocation, String> refusedAtResolution = new TreeMap<>();
    private final Map<String, String> files = new TreeMap<>();

    OverLimitDataPack() {
        files.put("pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"W179 over-limit reload fixture\"}}");
        for (int index = 0; index < CLASSIC_TYPES.size(); index++) {
            String type = CLASSIC_TYPES.get(index);
            for (Shape shape : OVER_LIMIT) {
                refused(type + "_" + shape.name(), shape.reason(), shape.apply().apply(base(type)).toString());
            }
            Shape invalid = INVALID.get(index);
            refused(type + "_" + invalid.name(), invalid.reason(), invalid.apply().apply(base(type)).toString());
            JsonObject oversized = base(type);
            first(oversized, "item_inputs").add("ingredient", tag(OVERSIZED_TAG));
            resolution(type + "_oversized_tag", "machine ingredient must resolve to 1..32 variants", oversized);
        }
        // The identifier bound: the classic codec refuses an id longer than 128 characters.
        refused("long_" + "x".repeat(120), "Identifier exceeds limit", base("lathe").toString());
        // Vanilla refuses a top-level array before the serializer runs.
        refused("top_level_array", "Expected top element to be a JsonObject, was an array ([1])", "[1]");
        recipeFile("truncated", "{\"type\": ");
        unreadable.add(id("truncated"));
        recipeFile("not_json", "not json at all");
        unreadable.add(id("not_json"));
        JsonObject refusedTag = base("lathe");
        first(refusedTag, "item_inputs").add("ingredient", tag(MISSING_REFERENCE_TAG));
        resolution("lathe_refused_tag", "machine ingredient has no bound alternatives", refusedTag);
        JsonObject unknownOutput = base("lathe");
        first(unknownOutput, "item_outputs").addProperty("item", MISSING_ITEM.toString());
        resolution("lathe_unknown_output", "Unknown recipe Item: " + MISSING_ITEM, unknownOutput);

        JsonArray values = new JsonArray();
        for (String colour : COLOURS) {
            values.add("minecraft:" + colour + "_wool");
            values.add("minecraft:" + colour + "_concrete");
        }
        values.add("minecraft:stone");
        if (values.size() != ProcessInput.MAX_VARIANTS + 1) {
            throw new IllegalStateException("The oversized tag must be one over the variant bound");
        }
        files.put(itemTag(OVERSIZED_TAG), tagFile(values));
        JsonArray missing = new JsonArray();
        missing.add("minecraft:stone");
        missing.add(MISSING_ITEM.toString());
        files.put(itemTag(MISSING_REFERENCE_TAG), tagFile(missing));
        files.put(itemTag(UNREADABLE_TAG), "{ not json");
        for (int pattern = 0; pattern < PATTERN_FILES; pattern++) {
            files.put("data/" + NAMESPACE + "/machine_patterns/" + String.format("bad_%03d.json", pattern), "{}");
        }
    }

    static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(NAME);
    }

    void write(Path root) throws IOException {
        delete(root);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
    }

    static void delete(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Every recipe id the pack names, loadable or not. */
    Set<ResourceLocation> recipes() {
        Set<ResourceLocation> all = new TreeSet<>(refusedAtLoad.keySet());
        all.addAll(unreadable);
        all.addAll(refusedAtResolution.keySet());
        return all;
    }

    /** The loadable recipes of one classic type. */
    long loadable(String type) {
        return refusedAtResolution.keySet().stream().filter(id -> id.getPath().startsWith(type + "_")).count();
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation(NAMESPACE, path);
    }

    private void refused(String path, String reason, String text) {
        recipeFile(path, text);
        refusedAtLoad.put(id(path), reason);
    }

    private void resolution(String path, String reason, JsonObject recipe) {
        recipeFile(path, recipe.toString());
        refusedAtResolution.put(id(path), reason);
    }

    private void recipeFile(String path, String text) {
        files.put("data/" + NAMESPACE + "/recipes/" + path + ".json", text);
    }

    private static String itemTag(ResourceLocation tag) {
        return "data/" + tag.getNamespace() + "/tags/items/" + tag.getPath() + ".json";
    }

    private static String tagFile(JsonArray values) {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", values);
        return tag.toString();
    }

    private static JsonObject base(String type) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", ModIdentity.MOD_ID + ":" + type);
        recipe.addProperty("schema_version", 1);
        JsonArray inputs = new JsonArray();
        inputs.add(row(item("minecraft:iron_ingot"), 1));
        recipe.add("item_inputs", inputs);
        JsonArray outputs = new JsonArray();
        JsonObject output = new JsonObject();
        output.addProperty("item", "minecraft:iron_nugget");
        output.addProperty("count", 1);
        outputs.add(output);
        recipe.add("item_outputs", outputs);
        recipe.add("fluid_inputs", new JsonArray());
        recipe.add("fluid_outputs", new JsonArray());
        recipe.addProperty("processing_time", 100);
        recipe.addProperty("energy_per_tick", 10);
        return recipe;
    }

    private static JsonObject row(JsonElement ingredient, int count) {
        JsonObject row = new JsonObject();
        row.add("ingredient", ingredient);
        row.addProperty("count", count);
        return row;
    }

    private static JsonObject item(String id) {
        JsonObject item = new JsonObject();
        item.addProperty("item", id);
        return item;
    }

    private static JsonObject tag(ResourceLocation id) {
        JsonObject tag = new JsonObject();
        tag.addProperty("tag", id.toString());
        return tag;
    }

    private static JsonObject first(JsonObject recipe, String rows) {
        return recipe.getAsJsonArray(rows).get(0).getAsJsonObject();
    }
}
