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

/** NEW cubes, crafting and original four opaque grids. No upstream bitmap or model body is imported. */
public final class V180SolarData implements DataProvider {
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String DATA = "data/advancedrocketrycommunity/";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180SolarData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String id : List.of("solar_generator", "solar_panel")) {
            files.put(ASSETS + "blockstates/" + id + ".json", json("{\"variants\":{\"\":{\"model\":\"advancedrocketrycommunity:block/" + id + "\"}}}"));
            files.put(ASSETS + "models/item/" + id + ".json", json("{\"parent\":\"advancedrocketrycommunity:block/" + id + "\"}"));
        }
        files.put(ASSETS + "models/block/solar_generator.json", json("""
                {"parent":"minecraft:block/cube","textures":{
                 "particle":"advancedrocketrycommunity:block/solar_generator_side",
                 "down":"advancedrocketrycommunity:block/solar_generator_bottom",
                 "up":"advancedrocketrycommunity:block/solar_generator_top",
                 "north":"advancedrocketrycommunity:block/solar_generator_side",
                 "south":"advancedrocketrycommunity:block/solar_generator_side",
                 "east":"advancedrocketrycommunity:block/solar_generator_side",
                 "west":"advancedrocketrycommunity:block/solar_generator_side"}}
                """));
        files.put(ASSETS + "models/block/solar_panel.json", json("""
                {"parent":"minecraft:block/cube_all","textures":{"all":"advancedrocketrycommunity:block/solar_panel"}}
                """));
        return files;
    }
    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String id : List.of("solar_generator", "solar_panel")) {
            files.put(DATA + "loot_tables/blocks/" + id + ".json", json("""
                    {"type":"minecraft:block","pools":[{"rolls":1.0,"bonus_rolls":0.0,
                     "entries":[{"type":"minecraft:item","name":"advancedrocketrycommunity:%s"}],
                     "functions":[{"function":"minecraft:explosion_decay"}]}]}
                    """.formatted(id)));
        }
        files.put(DATA + "recipes/solar_panel.json", json("""
                {"type":"minecraft:crafting_shaped","category":"building","pattern":["GGG","SSS","CCC"],
                 "key":{"G":{"tag":"forge:glass"},"S":{"item":"advancedrocketrycommunity:silicon_wafer"},
                        "C":{"tag":"forge:ingots/copper"}},"result":{"item":"advancedrocketrycommunity:solar_panel","count":1}}
                """));
        files.put(DATA + "recipes/solar_generator.json", json("""
                {"type":"minecraft:crafting_shaped","category":"building","pattern":["PPP","CGC","CCC"],
                 "key":{"P":{"item":"advancedrocketrycommunity:solar_panel"},"G":{"tag":"forge:glass"},
                        "C":{"tag":"forge:ingots/copper"}},"result":{"item":"advancedrocketrycommunity:solar_generator","count":1}}
                """));
        files.put(DATA + "advancements/recipes/building_blocks/solar_panel.json", unlock("solar_panel", "silicon_wafer"));
        files.put(DATA + "advancements/recipes/building_blocks/solar_generator.json", unlock("solar_generator", "solar_panel"));
        return files;
    }
    private static JsonObject unlock(String id, String ingredient) {
        return json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_ingredient":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{
                  "items":["advancedrocketrycommunity:%s"],"count":{"min":1}}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:%s"}}},
                 "requirements":[["has_ingredient","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:%s"]}}
                """.formatted(ingredient, id, id));
    }
    public static Map<String, String[]> grids() {
        Map<String, String[]> grids = new LinkedHashMap<>();
        grids.put("solar_generator_top", new String[]{
                "7777777777777777", "7911111111111197", "7118881888188817", "7115551555155517",
                "7113331333133317", "7118881888188817", "7115551555155517", "7199999999999917",
                "7118881888188817", "7115551555155517", "7113331333133317", "7118881888188817",
                "7115551555155517", "7113331333133317", "7911111111111197", "7777777777777777"});
        grids.put("solar_generator_side", new String[]{
                "7777777777777777", "7988888888888897", "7866666666666687", "7855555555555587",
                "7855511111155587", "7855519999155587", "7855511111155587", "7855555555555587",
                "7855555555555587", "7855222222255587", "7855222222255587", "7855222222255587",
                "7855555555555587", "7866666666666687", "7988888888888897", "7777777777777777"});
        grids.put("solar_generator_bottom", new String[]{
                "6666666666666666", "6911111111111196", "6133333333333316", "6137777777773316",
                "6137222222273316", "6137233333273316", "6137237773273316", "6137237773273316",
                "6137237773273316", "6137237773273316", "6137233333273316", "6137222222273316",
                "6137777777773316", "6133333333333316", "6911111111111196", "6666666666666666"});
        grids.put("solar_panel", new String[]{
                "8888888888888888", "8911111111111198", "8138313831383188", "8135313531353188",
                "8133313331333188", "8138313831383188", "8135313531353188", "8199999999999918",
                "8138313831383188", "8135313531353188", "8133313331333188", "8138313831383188",
                "8135313531353188", "8133313331333188", "8911111111111198", "8888888888888888"});
        return grids;
    }
    public static int tint(String name) {
        return switch (name) {
            case "solar_generator_top", "solar_panel" -> 0x78B5ED;
            case "solar_generator_side", "solar_generator_bottom" -> 0xAFBAC4;
            default -> throw new IllegalArgumentException("Unknown solar texture");
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
    @Override public String getName() { return "v1.8 single solar generator original resources"; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
