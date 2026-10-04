package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;

/** NEW crafting candidate, not a legacy recipe: casing, basic motor, bucket, steel plates, iron rods, redstone. */
public final class PumpDataFiles {
    public static final String NAMESPACE = "advancedrocketrycommunity";
    public static Map<String, JsonObject> client() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(asset("models/block/pump"), json("""
                {"parent":"minecraft:block/cube_bottom_top","textures":{
                  "side":"advancedrocketrycommunity:block/pump_side",
                  "top":"advancedrocketrycommunity:block/pump_top",
                  "bottom":"advancedrocketrycommunity:block/pump_bottom"}}
                """));
        files.put(asset("models/item/pump"), json("{\"parent\":\"advancedrocketrycommunity:block/pump\"}"));
        files.put(asset("blockstates/pump"), json("{\"variants\":{\"\":{\"model\":\"advancedrocketrycommunity:block/pump\"}}}"));
        return files;
    }
    public static Map<String, JsonObject> server() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(data("recipes/pump"), json("""
                {"type":"minecraft:crafting_shapeless","category":"misc","ingredients":[
                  {"item":"advancedrocketrycommunity:machine_casing"},{"item":"advancedrocketrycommunity:motor"},
                  {"item":"minecraft:bucket"},{"tag":"forge:plates/steel"},{"tag":"forge:plates/steel"},
                  {"tag":"forge:rods/iron"},{"tag":"forge:rods/iron"},{"item":"minecraft:redstone"}],
                 "result":{"item":"advancedrocketrycommunity:pump","count":1}}
                """));
        files.put(data("advancements/recipes/misc/pump"), json("""
                {"parent":"minecraft:recipes/root","criteria":{
                  "has_input":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["advancedrocketrycommunity:motor"]}]}},
                  "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:pump"}}},
                 "requirements":[["has_input","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:pump"]}}
                """));
        // PumpBlock attaches its validated resource root once to the single normal drop.
        files.put(data("loot_tables/blocks/pump"), json("""
                {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[{"condition":"minecraft:survives_explosion"}],
                  "entries":[{"type":"minecraft:item","name":"advancedrocketrycommunity:pump"}]}]}
                """));
        return files;
    }
    private static JsonObject json(String input) { return JsonParser.parseString(input).getAsJsonObject(); }
    private static String asset(String path) { return "assets/" + NAMESPACE + "/" + path + ".json"; }
    private static String data(String path) { return "data/" + NAMESPACE + "/" + path + ".json"; }
    private PumpDataFiles() { }
}
