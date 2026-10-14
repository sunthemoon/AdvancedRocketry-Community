package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Publish v1.8 copies of AUDIT-01's model bindings without changing historical resource inputs. */
public final class V180ClosureModels implements DataProvider {
    private static final String MODELS = "assets/" + ModIdentity.MOD_ID + "/models/";
    private final Path outputRoot;

    public V180ClosureModels(PackOutput output) {
        this.outputRoot = output.getOutputFolder();
    }

    /** Asset-relative paths for the complete set of thirteen W49 bindings in this provider's output. */
    public static Map<String, JsonObject> bindings() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(MODELS + "block/electrolyzer.json", orientable("electrolyzer_front", null));
        files.put(MODELS + "block/electrolyzer_active.json", orientable("electrolyzer_front_active", null));
        files.put(MODELS + "block/precision_assembler.json", orientable("precision_assembler_front", null));
        JsonObject drill = orientable("orbital_laser_front", "orbital_laser_bottom");
        drill.getAsJsonObject("textures").addProperty("top", "minecraft:block/obsidian");
        files.put(MODELS + "block/orbital_laser_drill.json", drill);
        JsonObject railgun = orientable("railgun_front", null);
        railgun.addProperty("parent", "minecraft:block/orientable");
        railgun.getAsJsonObject("textures").remove("bottom");
        railgun.getAsJsonObject("textures").addProperty("top", "minecraft:block/iron_block");
        files.put(MODELS + "block/railgun.json", railgun);
        JsonObject builder = orientable("satellite_builder_front", null);
        builder.addProperty("parent", "minecraft:block/orientable");
        builder.getAsJsonObject("textures").remove("bottom");
        builder.getAsJsonObject("textures").addProperty("top", "minecraft:block/smithing_table_top");
        files.put(MODELS + "block/satellite_builder.json", builder);
        JsonObject core = model("minecraft:block/cube_bottom_top");
        JsonObject sides = new JsonObject();
        sides.addProperty("bottom", texture("block/machine_casing_top"));
        sides.addProperty("side", texture("block/warp_core_side"));
        sides.addProperty("top", "minecraft:block/crying_obsidian");
        core.add("textures", sides);
        files.put(MODELS + "block/warp_core.json", core);
        files.put(MODELS + "item/satellite_solar_module.json", item("satellite_solar_module"));
        files.put(MODELS + "item/advanced_solar_panel.json", item("advanced_solar_panel"));
        for (String part : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
            files.put(MODELS + "item/space_suit_" + part + ".json", item("space_suit_" + part));
        }
        return Map.copyOf(files);
    }

    private static JsonObject orientable(String front, String bottom) {
        JsonObject model = model("minecraft:block/orientable_with_bottom");
        JsonObject textures = new JsonObject();
        textures.addProperty("front", texture("block/" + front));
        textures.addProperty("bottom", texture("block/" + (bottom == null ? "machine_casing_top" : bottom)));
        textures.addProperty("side", texture("block/machine_casing_side"));
        textures.addProperty("top", texture("block/machine_casing_top"));
        model.add("textures", textures);
        return model;
    }

    private static JsonObject item(String item) {
        JsonObject model = model("minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", texture("item/" + item));
        model.add("textures", textures);
        return model;
    }

    private static JsonObject model(String parent) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        return model;
    }

    private static String texture(String path) { return ModIdentity.MOD_ID + ":" + path; }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.allOf(bindings().entrySet().stream().map(entry ->
                DataProvider.saveStable(cache, entry.getValue(), outputRoot.resolve(entry.getKey())))
                .toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 audited model texture bindings"; }
}
