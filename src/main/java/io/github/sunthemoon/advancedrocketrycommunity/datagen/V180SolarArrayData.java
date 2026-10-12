package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original array art and reachable crafting grids. Vanilla parents are referenced, never copied. */
public final class V180SolarArrayData implements DataProvider {
    public static final List<String> IDS = List.of("solar_array", "solar_array_panel", "power_output_plug");
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String DATA = "data/advancedrocketrycommunity/";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180SolarArrayData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String id : IDS) {
            String variants = id.equals("solar_array")
                    ? "\"facing=north\":{\"model\":\"advancedrocketrycommunity:block/solar_array\"},"
                      + "\"facing=east\":{\"model\":\"advancedrocketrycommunity:block/solar_array\",\"y\":90},"
                      + "\"facing=south\":{\"model\":\"advancedrocketrycommunity:block/solar_array\",\"y\":180},"
                      + "\"facing=west\":{\"model\":\"advancedrocketrycommunity:block/solar_array\",\"y\":270}"
                    : "\"\":{\"model\":\"advancedrocketrycommunity:block/" + id + "\"}";
            files.put(ASSETS + "blockstates/" + id + ".json", json("{\"variants\":{" + variants + "}}"));
            files.put(ASSETS + "models/item/" + id + ".json", json("{\"parent\":\"advancedrocketrycommunity:block/" + id + "\"}"));
        }
        files.put(ASSETS + "models/block/solar_array.json", json("""
                {"parent":"minecraft:block/cube","textures":{
                 "particle":"advancedrocketrycommunity:block/solar_array_side",
                 "north":"advancedrocketrycommunity:block/solar_array_front",
                 "up":"advancedrocketrycommunity:block/solar_array_top",
                 "down":"advancedrocketrycommunity:block/solar_array_side",
                 "east":"advancedrocketrycommunity:block/solar_array_side",
                 "west":"advancedrocketrycommunity:block/solar_array_side",
                 "south":"advancedrocketrycommunity:block/solar_array_side"}}
                """));
        files.put(ASSETS + "models/block/solar_array_panel.json", json("""
                {"parent":"minecraft:block/cube_bottom_top","textures":{
                 "top":"advancedrocketrycommunity:block/solar_array_panel",
                 "bottom":"advancedrocketrycommunity:block/solar_array_side",
                 "side":"advancedrocketrycommunity:block/solar_array_side"}}
                """));
        files.put(ASSETS + "models/block/power_output_plug.json", json("""
                {"parent":"minecraft:block/cube_all","textures":{"all":"advancedrocketrycommunity:block/power_output_plug"}}
                """));
        return files;
    }
    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String id : IDS) {
            files.put(DATA + "loot_tables/blocks/" + id + ".json", json("""
                    {"type":"minecraft:block","pools":[{"rolls":1.0,"bonus_rolls":0.0,
                     "entries":[{"type":"minecraft:item","name":"advancedrocketrycommunity:%s"}],
                     "functions":[{"function":"minecraft:explosion_decay"}]}]}
                    """.formatted(id)));
            files.put(DATA + "advancements/recipes/building_blocks/" + id + ".json", unlock(id,
                    id.equals("solar_array_panel") ? "silicon_wafer" : "machine_casing"));
        }
        files.put(DATA + "tags/blocks/solar_array_cells.json", json("""
                {"replace":false,"values":["advancedrocketrycommunity:solar_array_panel","advancedrocketrycommunity:machine_casing"]}
                """));
        recipe(files, "solar_array_panel", "[\"GGG\",\"SSS\",\"CCC\"]", """
                {"G":{"tag":"forge:glass"},"S":{"item":"advancedrocketrycommunity:silicon_wafer"},"C":{"tag":"forge:ingots/copper"}}
                """, 1);
        recipe(files, "solar_array", "[\"PGP\",\"CMC\",\"III\"]", """
                {"P":{"item":"advancedrocketrycommunity:solar_array_panel"},"G":{"tag":"forge:glass"},
                 "C":{"tag":"forge:ingots/copper"},"M":{"item":"advancedrocketrycommunity:machine_casing"},"I":{"tag":"forge:plates/iron"}}
                """, 1);
        recipe(files, "power_output_plug", "[\"ICI\",\"CMC\",\"ICI\"]", """
                {"I":{"tag":"forge:plates/iron"},"C":{"tag":"forge:ingots/copper"},"M":{"item":"advancedrocketrycommunity:machine_casing"}}
                """, 2);
        return files;
    }
    private static void recipe(Map<String, JsonObject> files, String id, String pattern, String key, int count) {
        files.put(DATA + "recipes/" + id + ".json", json("""
                {"type":"minecraft:crafting_shaped","category":"building","pattern":%s,"key":%s,
                 "result":{"item":"advancedrocketrycommunity:%s","count":%d}}
                """.formatted(pattern, key, id, count)));
    }
    private static JsonObject unlock(String id, String ingredient) {
        return json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_ingredient":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["advancedrocketrycommunity:%s"]}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:%s"}}},
                 "requirements":[["has_ingredient","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:%s"]}}
                """.formatted(ingredient, id, id));
    }
    public static Map<String, String[]> grids() {
        Map<String, String[]> grids = new LinkedHashMap<>();
        for (String name : List.of("solar_array_front", "solar_array_top", "solar_array_side", "solar_array_panel", "power_output_plug")) {
            String[] rows = new String[16];
            for (int y = 0; y < 16; y++) {
                StringBuilder row = new StringBuilder(16);
                for (int x = 0; x < 16; x++) {
                    char shade = x == 0 || y == 0 || x == 15 || y == 15 ? '8' : '3';
                    if (x > 1 && x < 14 && y > 1 && y < 14) {
                        shade = switch (name) {
                            case "solar_array_panel", "solar_array_top" -> x % 4 == 1 || y % 4 == 1 ? '7' : (char) ('2' + (x + y) % 3);
                            case "solar_array_front" -> y < 8 ? (x % 3 == 0 ? '9' : '1') : (y == 11 && x > 4 && x < 11 ? '8' : '4');
                            case "power_output_plug" -> x >= 5 && x <= 10 && y >= 4 && y <= 11 ? '9' : (x == y || x + y == 15 ? '6' : '1');
                            default -> y % 4 == 0 ? '1' : '5';
                        };
                    }
                    row.append(shade);
                }
                rows[y] = row.toString();
            }
            grids.put(name, rows);
        }
        return grids;
    }
    public static int tint(String name) {
        return switch (name) {
            case "solar_array_panel", "solar_array_top" -> 0x5B9FCC;
            case "power_output_plug" -> 0xDDAE64;
            default -> 0xA5B6C2;
        };
    }
    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        grids().forEach((name, grid) -> files.put(ASSETS + "textures/block/" + name + ".png", V180MaterialArt.png(grid, tint(name))));
        return files;
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            textures().forEach((path, png) -> writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            })));
        }
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 solar array original resources"; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
