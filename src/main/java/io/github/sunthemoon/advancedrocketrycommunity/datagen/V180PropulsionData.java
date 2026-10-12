package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.*;

/** Original propulsion blueprints and strict default source-Fluid role definitions. */
public final class V180PropulsionData implements DataProvider {
    public static final List<String> IDS = List.of("advanced_rocket_motor", "bipropellant_rocket_motor",
            "advanced_bipropellant_rocket_motor", "bipropellant_fuel_tank", "oxidizer_fuel_tank",
            "nuclear_rocket_motor", "nuclear_fuel_tank", "nuclear_core");
    private static final String NS = ModIdentity.MOD_ID;
    private final PackOutput output;
    private final boolean client, server;
    public V180PropulsionData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> files(boolean client, boolean server) {
        var files = new LinkedHashMap<String, JsonObject>();
        for (String id : IDS) {
            String full = NS + ":" + id;
            if (client) {
                JsonObject model = json("{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{}}");
                for (String face : List.of("side", "top", "bottom")) {
                    model.getAsJsonObject("textures").addProperty(face, NS + ":block/" + id + "_" + face);
                }
                files.put(asset("models/block/" + id), model);
                files.put(asset("models/item/" + id), json("{\"parent\":\"" + NS + ":block/" + id + "\"}"));
                JsonObject state = json("{\"variants\":{}}");
                if (id.equals("nuclear_rocket_motor")) {
                    for (String facing : List.of("down", "up", "north", "south", "east", "west")) {
                        JsonObject variant = json("{\"model\":\"" + NS + ":block/" + id + "\"}");
                        int rotation = switch (facing) { case "up" -> 180; case "down" -> 0; default -> 90; };
                        variant.addProperty("x", rotation);
                        variant.addProperty("y", switch (facing) { case "east" -> 90; case "south" -> 180; case "west" -> 270; default -> 0; });
                        state.getAsJsonObject("variants").add("facing=" + facing, variant);
                    }
                } else { state.getAsJsonObject("variants").add("", json("{\"model\":\"" + NS + ":block/" + id + "\"}")); }
                files.put(asset("blockstates/" + id), state);
            }
            if (server) {
                files.put(data("loot_tables/blocks/" + id), json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,"
                        + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + full + "\"}],"
                        + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
                files.put(data("recipes/" + id), recipe(id));
                files.put(data("advancements/recipes/misc/" + id), json("{\"parent\":\"minecraft:recipes/root\","
                        + "\"criteria\":{\"has_iron\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":[\"minecraft:iron_ingot\"]}]}},"
                        + "\"has_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + full + "\"}}},"
                        + "\"requirements\":[[\"has_iron\",\"has_recipe\"]],\"rewards\":{\"recipes\":[\"" + full + "\"]}}"));
            }
        }
        if (server) {
            var tag = json("{\"replace\":false,\"values\":[]}");
            IDS.forEach(id -> tag.getAsJsonArray("values").add(NS + ":" + id));
            files.put(data("tags/blocks/rocket_propulsion"), tag);
            String[][] fuels = {{"mono", "MONO", "rocket_fuel", "2"}, {"bipropellant", "BIPROPELLANT_FUEL", "hydrogen", "1"},
                    {"oxidizer", "OXIDIZER", "oxygen", "1"}, {"nuclear", "WORKING_FLUID", "hydrogen", "1"}};
            for (var fuel : fuels) {
                files.put(data("rocket_fluid_fuels/" + fuel[0]), json("{\"schema\":1,\"role\":\"" + fuel[1]
                        + "\",\"fluid\":\"" + NS + ":" + fuel[2] + "\",\"numerator\":" + fuel[3] + ",\"denominator\":1}"));
            }
        }
        return files;
    }
    private static JsonObject recipe(String id) {
        String base = switch (id) {
            case "advanced_rocket_motor", "bipropellant_rocket_motor", "nuclear_rocket_motor" -> "rocket_motor";
            case "advanced_bipropellant_rocket_motor" -> "bipropellant_rocket_motor";
            case "nuclear_core" -> "machine_casing"; default -> "rocket_fuel_tank";
        };
        var root = json("{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":[],"
                + "\"result\":{\"item\":\"" + NS + ":" + id + "\",\"count\":1}}");
        var ingredients = root.getAsJsonArray("ingredients");
        ingredients.add(json("{\"item\":\"" + NS + ":" + base + "\"}"));
        String metal = switch (id) {
            case "advanced_rocket_motor", "advanced_bipropellant_rocket_motor" -> "titanium_aluminide_plate";
            case "nuclear_core" -> "iridium_ingot";
            case "nuclear_rocket_motor" -> "iridium_plate";
            case "nuclear_fuel_tank" -> "titanium_plate";
            case "oxidizer_fuel_tank" -> "aluminum_plate";
            default -> "steel_plate";
        };
        for (int count = 0; count < (id.contains("advanced") || id.equals("nuclear_core") ? 4 : 2); count++) {
            ingredient(ingredients, NS + ":" + metal);
        }
        switch (id) {
            case "advanced_rocket_motor" -> { ingredient(ingredients, NS + ":enhanced_motor"); ingredient(ingredients, NS + ":iridium_plate"); }
            case "advanced_bipropellant_rocket_motor", "nuclear_core" -> { ingredient(ingredients, NS + ":elite_motor"); ingredient(ingredients, "minecraft:blaze_powder"); }
            case "nuclear_rocket_motor" -> ingredient(ingredients, NS + ":enhanced_motor");
            case "bipropellant_rocket_motor" -> ingredient(ingredients, NS + ":advanced_motor");
            case "oxidizer_fuel_tank" -> ingredient(ingredients, "minecraft:lapis_lazuli");
            case "nuclear_fuel_tank" -> ingredient(ingredients, NS + ":iridium_plate");
            default -> { }
        }
        ingredient(ingredients, "minecraft:redstone");
        return root;
    }
    private static void ingredient(JsonArray ingredients, String id) { ingredients.add(json("{\"item\":\"" + id + "\"}")); }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static String asset(String path) { return "assets/" + NS + "/" + path + ".json"; }
    private static String data(String path) { return "data/" + NS + "/" + path + ".json"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        files(client, server).forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 propulsion hardware and Fluid catalogs"; }
}
