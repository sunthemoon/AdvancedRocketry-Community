package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * W32 chemical reactor special recipes (ADR-064 §9.2). Inputs, times and FE are the pinned legacy numbers:
 * {@code TileChemicalReactor.registerRecipe} (armor, pipe sealer, four {@code sheetTitaniumAluminide}, and for a
 * chestplate the meta-3 Super High Pressure Tank; 100 ticks, 10 FE/t) and {@code carboncartridgerefresh.json}
 * (a fully spent cartridge into a fresh one and one charcoal; 40 ticks, 20 FE/t).
 */
public final class V180ChemicalSpecialData implements DataProvider {
    public static final String NS = "advancedrocketrycommunity";
    /** Recipe suffix, legacy armor slot, Forge armor tag of that slot. */
    public static final List<String[]> ARMOR = List.of(new String[]{"helmet", "head", "forge:armors/helmets"},
            new String[]{"chestplate", "chest", "forge:armors/chestplates"},
            new String[]{"leggings", "legs", "forge:armors/leggings"}, new String[]{"boots", "feet", "forge:armors/boots"});
    private final PackOutput output;
    public V180ChemicalSpecialData(PackOutput output) { this.output = output; }

    public static Map<String, JsonObject> serverFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        for (String[] piece : ARMOR) {
            var inputs = new ArrayList<JsonObject>();
            inputs.add(input("tag", piece[2], 1));
            inputs.add(input("item", NS + ":pipe_seal", 1));
            inputs.add(input("tag", NS + ":sheets/titanium_aluminide", 4));
            if (piece[1].equals("chest")) { inputs.add(input("item", NS + ":super_high_pressure_tank", 1)); }
            files.put(data("recipes/chemical_reacting_space_breathing_" + piece[0]),
                    recipe("space_breathing_" + piece[1], inputs, List.of(), 100, 10));
        }
        files.put(data("recipes/chemical_reacting_carbon_cartridge_refresh"), recipe("carbon_cartridge_refresh",
                List.of(input("item", NS + ":carbon_scrubber_cartridge", 1)), List.of(item("minecraft:charcoal", 1)), 40, 20));
        return files;
    }

    public static Map<String, String> language(boolean chinese) {
        var result = new LinkedHashMap<String, String>();
        result.put("jei." + NS + ".special_subject", chinese ? "返回同一物品，其余数据不变" : "The same item returns with its other data kept");
        return result;
    }

    private static JsonObject recipe(String special, List<JsonObject> inputs, List<JsonObject> outputs, int ticks, int energy) {
        var root = object("type", NS + ":chemical_reacting"); root.addProperty("schema_version", 1);
        root.addProperty("special", special);
        root.add("item_inputs", array(inputs)); root.add("item_outputs", array(outputs));
        root.add("fluid_inputs", new JsonArray()); root.add("fluid_outputs", new JsonArray());
        root.addProperty("processing_time", ticks); root.addProperty("energy_per_tick", energy); return root;
    }
    private static JsonObject input(String key, String id, int count) {
        var row = new JsonObject(); row.add("ingredient", object(key, id)); row.addProperty("count", count); return row;
    }
    private static JsonObject item(String id, int count) { var row = object("item", id); row.addProperty("count", count); return row; }
    private static JsonObject object(String key, String value) { var root = new JsonObject(); root.addProperty(key, value); return root; }
    private static JsonArray array(List<JsonObject> rows) { var array = new JsonArray(); rows.forEach(array::add); return array; }
    private static String data(String path) { return "data/" + NS + "/" + path + ".json"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        serverFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 W32 chemical special recipes"; }
}
