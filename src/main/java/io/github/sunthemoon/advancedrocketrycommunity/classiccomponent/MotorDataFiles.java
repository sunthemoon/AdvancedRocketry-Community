package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry-independent JSON blueprints; DataGen owns writing them into the v1.8 root. */
public final class MotorDataFiles {
    private MotorDataFiles() { }

    public static Map<String, JsonObject> client() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (MotorDefinition tier : MotorDefinition.values()) {
            JsonObject model = object("parent", "minecraft:block/cube_bottom_top");
            JsonObject textures = new JsonObject();
            for (String face : MotorArt.FACES) { textures.addProperty(face, texture(tier, face)); }
            model.add("textures", textures);
            files.put(asset("models/block/" + tier.id()), model);
            files.put(asset("models/item/" + tier.id()), object("parent", tier.fullId().replace(":", ":block/")));
            JsonObject variants = new JsonObject();
            variants.add("", object("model", tier.fullId().replace(":", ":block/")));
            JsonObject state = new JsonObject();
            state.add("variants", variants);
            files.put(asset("blockstates/" + tier.id()), state);
        }
        return files;
    }

    public static Map<String, JsonObject> server() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        JsonArray values = new JsonArray();
        for (MotorDefinition tier : MotorDefinition.values()) { values.add(tier.fullId()); }
        tag.add("values", values);
        files.put(data("tags/blocks/motors"), tag.deepCopy());
        files.put(data("tags/items/motors"), tag.deepCopy());
        for (MotorDefinition tier : MotorDefinition.values()) {
            JsonObject recipe = object("type", "minecraft:crafting_shapeless");
            recipe.addProperty("category", "misc");
            JsonArray ingredients = new JsonArray();
            for (var ingredient : tier.ingredients()) {
                for (int count = 0; count < ingredient.count(); count++) {
                    ingredients.add(object(ingredient.tag() ? "tag" : "item", ingredient.id()));
                }
            }
            recipe.add("ingredients", ingredients);
            JsonObject result = object("item", tier.fullId());
            result.addProperty("count", 1);
            recipe.add("result", result);
            files.put(data("recipes/" + tier.id()), recipe);
            files.put(data("advancements/recipes/misc/" + tier.id()), advancement(tier));
            files.put(data("loot_tables/blocks/" + tier.id()), loot(tier));
        }
        return files;
    }

    public static String texture(MotorDefinition tier, String face) {
        if (!MotorArt.FACES.contains(face)) { throw new IllegalArgumentException("Unknown motor texture face"); }
        return MotorDefinition.NAMESPACE + ":block/" + tier.id() + "_" + face;
    }

    private static JsonObject advancement(MotorDefinition tier) {
        var input = tier.ingredients().get(0);
        JsonObject predicate = new JsonObject();
        if (input.tag()) { predicate.addProperty("tag", input.id()); }
        else {
            JsonArray items = new JsonArray();
            items.add(input.id());
            predicate.add("items", items);
        }
        JsonArray items = new JsonArray();
        items.add(predicate);
        JsonObject conditions = new JsonObject();
        conditions.add("items", items);
        JsonObject inputCriterion = object("trigger", "minecraft:inventory_changed");
        inputCriterion.add("conditions", conditions);
        JsonObject unlocked = object("trigger", "minecraft:recipe_unlocked");
        unlocked.add("conditions", object("recipe", tier.fullId()));
        JsonObject criteria = new JsonObject();
        criteria.add("has_input", inputCriterion);
        criteria.add("has_the_recipe", unlocked);
        JsonArray requirement = new JsonArray();
        requirement.add("has_input");
        requirement.add("has_the_recipe");
        JsonArray requirements = new JsonArray();
        requirements.add(requirement);
        JsonArray recipes = new JsonArray();
        recipes.add(tier.fullId());
        JsonObject rewards = new JsonObject();
        rewards.add("recipes", recipes);
        JsonObject advancement = object("parent", "minecraft:recipes/root");
        advancement.add("criteria", criteria);
        advancement.add("requirements", requirements);
        advancement.add("rewards", rewards);
        return advancement;
    }

    private static JsonObject loot(MotorDefinition tier) {
        JsonObject entry = object("type", "minecraft:item");
        entry.addProperty("name", tier.fullId());
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonArray conditions = new JsonArray();
        conditions.add(object("condition", "minecraft:survives_explosion"));
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", entries);
        pool.add("conditions", conditions);
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject loot = object("type", "minecraft:block");
        loot.add("pools", pools);
        return loot;
    }

    private static JsonObject object(String key, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static String asset(String path) { return "assets/" + MotorDefinition.NAMESPACE + "/" + path + ".json"; }
    private static String data(String path) { return "data/" + MotorDefinition.NAMESPACE + "/" + path + ".json"; }
}
