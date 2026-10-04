package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original registry-independent blueprints. Central language/mining tags remain root-owned. */
public final class TankDataFiles {
    public static final String ID = "advancedrocketrycommunity:pressurized_tank";
    private static final String NS = "advancedrocketrycommunity";

    public static Map<String, JsonObject> client() {
        var files = new LinkedHashMap<String, JsonObject>();
        JsonObject model = object("parent", "minecraft:block/cube_bottom_top");
        JsonObject textures = new JsonObject();
        for (String face : new String[]{"side", "top", "bottom"}) {
            textures.addProperty(face, NS + ":block/pressurized_tank_" + face);
        }
        model.add("textures", textures);
        files.put(asset("models/block/pressurized_tank"), model);
        files.put(asset("models/item/pressurized_tank"), object("parent", NS + ":block/pressurized_tank"));
        JsonObject variants = new JsonObject(); variants.add("", object("model", NS + ":block/pressurized_tank"));
        JsonObject state = new JsonObject(); state.add("variants", variants);
        files.put(asset("blockstates/pressurized_tank"), state);
        return files;
    }

    public static Map<String, JsonObject> server() {
        var files = new LinkedHashMap<String, JsonObject>();
        JsonObject recipe = object("type", "minecraft:crafting_shaped");
        recipe.addProperty("category", "misc");
        JsonArray pattern = new JsonArray(); pattern.add("PPP"); pattern.add("PBP"); pattern.add("PPP");
        recipe.add("pattern", pattern);
        JsonObject keys = new JsonObject();
        keys.add("P", object("tag", "forge:plates/steel")); keys.add("B", object("item", "minecraft:bucket"));
        recipe.add("key", keys);
        JsonObject result = object("item", ID); result.addProperty("count", 1); recipe.add("result", result);
        files.put(data("recipes/pressurized_tank"), recipe);

        JsonObject entry = object("type", "minecraft:item"); entry.addProperty("name", ID);
        JsonArray entries = new JsonArray(); entries.add(entry);
        JsonObject pool = new JsonObject(); pool.addProperty("rolls", 1); pool.add("entries", entries);
        JsonArray pools = new JsonArray(); pools.add(pool);
        JsonObject loot = object("type", "minecraft:block"); loot.add("pools", pools);
        // The Block adapter attaches exactly one native root to the self drop; no independent fluid drop.
        files.put(data("loot_tables/blocks/pressurized_tank"), loot);
        JsonObject tag = new JsonObject(); tag.addProperty("replace", false);
        JsonArray values = new JsonArray(); values.add(ID); tag.add("values", values);
        files.put(data("tags/blocks/pressurized_tanks"), tag.deepCopy());
        files.put(data("tags/items/pressurized_tanks"), tag.deepCopy());
        files.put(data("advancements/recipes/misc/pressurized_tank"), advancement());
        return files;
    }

    private static JsonObject advancement() {
        JsonObject ingredient = object("tag", "forge:plates/steel");
        JsonArray ingredients = new JsonArray(); ingredients.add(ingredient);
        JsonObject conditions = new JsonObject(); conditions.add("items", ingredients);
        JsonObject criterion = object("trigger", "minecraft:inventory_changed"); criterion.add("conditions", conditions);
        JsonObject unlocked = object("trigger", "minecraft:recipe_unlocked");
        unlocked.add("conditions", object("recipe", ID));
        JsonObject criteria = new JsonObject(); criteria.add("has_steel", criterion); criteria.add("has_recipe", unlocked);
        JsonArray any = new JsonArray(); any.add("has_steel"); any.add("has_recipe");
        JsonArray requirements = new JsonArray(); requirements.add(any);
        JsonArray recipes = new JsonArray(); recipes.add(ID);
        JsonObject rewards = new JsonObject(); rewards.add("recipes", recipes);
        JsonObject advancement = object("parent", "minecraft:recipes/root");
        advancement.add("criteria", criteria); advancement.add("requirements", requirements); advancement.add("rewards", rewards);
        return advancement;
    }

    private static JsonObject object(String key, String value) {
        JsonObject object = new JsonObject(); object.addProperty(key, value); return object;
    }
    private static String asset(String path) { return "assets/" + NS + "/" + path + ".json"; }
    private static String data(String path) { return "data/" + NS + "/" + path + ".json"; }
    private TankDataFiles() { }
}
