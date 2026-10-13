package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** Original controllers, recipes and shell tag. Pattern output must match the frozen W21a fixtures. */
public final class V180ResearchData implements DataProvider {
    public static final List<String> IDS = List.of("observatory", "astrobody_data_processor");
    private static final String NS = ModIdentity.MOD_ID;
    private final PackOutput output;
    private final boolean client, server;
    public V180ResearchData(PackOutput output, boolean client, boolean server) { this.output = output; this.client = client; this.server = server; }
    public static Map<String, JsonObject> clientFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        for (String id : IDS) {
            var model = object("parent", "minecraft:block/orientable"); var textures = object("front", NS + ":block/" + id);
            textures.addProperty("side", NS + ":block/machine_casing_side"); textures.addProperty("top", NS + ":block/machine_casing_top"); model.add("textures", textures);
            files.put(asset("models/block/" + id), model); files.put(asset("models/item/" + id), object("parent", NS + ":block/" + id));
            var variants = new JsonObject(); String[] facings = {"north", "east", "south", "west"};
            for (int i = 0; i < facings.length; i++) { var row = object("model", NS + ":block/" + id); row.addProperty("y", i * 90); variants.add("facing=" + facings[i], row); }
            var state = new JsonObject(); state.add("variants", variants); files.put(asset("blockstates/" + id), state);
        }
        return files;
    }
    public static Map<String, JsonObject> serverFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        files.put(data("machine_patterns/observatory"), ResearchPatterns.json(false));
        files.put(data("machine_patterns/astrobody_data_processor"), ResearchPatterns.json(true));
        var shell = new JsonObject(); shell.addProperty("replace", false);
        shell.add("values", strings(NS + ":machine_casing", "minecraft:iron_block", NS + ":power_input_plug"));
        files.put(data("tags/blocks/observatory_shell_parts"), shell);
        for (String id : IDS) {
            var recipe = object("type", "minecraft:crafting_shaped"); recipe.add("pattern", strings("PLP", "BCB", "PMP"));
            var key = new JsonObject(); key.add("P", object("tag", "forge:plates/steel")); key.add("L", object("item", NS + ":laser_lens"));
            key.add("B", object("item", id.equals("observatory") ? "minecraft:glass" : NS + ":control_board"));
            key.add("C", object("item", NS + ":machine_casing")); key.add("M", object("item", NS + ":motor")); recipe.add("key", key);
            recipe.add("result", object("item", NS + ":" + id)); files.put(data("recipes/" + id), recipe);
            var entry = object("type", "minecraft:item"); entry.addProperty("name", NS + ":" + id); var entries = new JsonArray(); entries.add(entry);
            var pool = new JsonObject(); pool.addProperty("rolls", 1); pool.add("entries", entries);
            var conditions = new JsonArray(); conditions.add(object("condition", "minecraft:survives_explosion")); pool.add("conditions", conditions);
            var pools = new JsonArray(); pools.add(pool); var loot = object("type", "minecraft:block"); loot.add("pools", pools);
            files.put(data("loot_tables/blocks/" + id), loot);
        }
        return files;
    }
    private static JsonObject object(String key, String value) { var object = new JsonObject(); object.addProperty(key, value); return object; }
    private static JsonArray strings(String... values) { var array = new JsonArray(); for (String value : values) { array.add(value); } return array; }
    private static String asset(String path) { return "assets/" + NS + "/" + path + ".json"; }
    private static String data(String path) { return "data/" + NS + "/" + path + ".json"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) { clientFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        if (server) { serverFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 ground research content"; }
}
