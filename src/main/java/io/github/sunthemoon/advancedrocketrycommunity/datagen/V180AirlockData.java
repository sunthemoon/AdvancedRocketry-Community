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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original NEW/MIT door grids and data; native templates are referenced by ID only. */
public final class V180AirlockData implements DataProvider {
    public static final String ID = "advancedrocketrycommunity:airlock_door";
    public static final String BOTTOM_TEXTURE = "assets/advancedrocketrycommunity/textures/block/airlock_door_bottom.png";
    public static final String TOP_TEXTURE = "assets/advancedrocketrycommunity/textures/block/airlock_door_top.png";
    public static final String ITEM_TEXTURE = "assets/advancedrocketrycommunity/textures/item/airlock_door.png";
    public static final int TINT = 0xCED4D9;
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180AirlockData(PackOutput output, boolean client, boolean server) {
        this.output = Objects.requireNonNull(output, "output");
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        JsonObject variants = new JsonObject();
        String[] facing = {"east", "south", "west", "north"};
        for (String half : List.of("lower", "upper")) {
            String part = half.equals("lower") ? "bottom" : "top";
            for (String hinge : List.of("left", "right")) {
                for (boolean open : new boolean[] {false, true}) {
                    String name = "airlock_door_" + part + "_" + hinge + (open ? "_open" : "");
                    JsonObject model = new JsonObject();
                    model.addProperty("parent", "minecraft:block/door_" + part + "_" + hinge + (open ? "_open" : ""));
                    model.add("textures", json("{\"bottom\":\"advancedrocketrycommunity:block/airlock_door_bottom\","
                            + "\"top\":\"advancedrocketrycommunity:block/airlock_door_top\"}"));
                    files.put("assets/advancedrocketrycommunity/models/block/" + name + ".json", model);
                    for (int index = 0; index < facing.length; index++) {
                        JsonObject variant = new JsonObject();
                        variant.addProperty("model", "advancedrocketrycommunity:block/" + name);
                        int rotation = Math.floorMod(index * 90 + (open ? (hinge.equals("left") ? 90 : -90) : 0), 360);
                        if (rotation != 0) { variant.addProperty("y", rotation); }
                        variants.add("facing=" + facing[index] + ",half=" + half + ",hinge=" + hinge + ",open=" + open, variant);
                    }
                }
            }
        }
        JsonObject state = new JsonObject(); state.add("variants", variants);
        files.put("assets/advancedrocketrycommunity/blockstates/airlock_door.json", state);
        files.put("assets/advancedrocketrycommunity/models/item/airlock_door.json", json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/airlock_door"}}
                """));
        return Map.copyOf(files);
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of("data/advancedrocketrycommunity/loot_tables/blocks/airlock_door.json", json("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item",
                 "name":"advancedrocketrycommunity:airlock_door","conditions":[{"condition":"minecraft:block_state_property",
                 "block":"advancedrocketrycommunity:airlock_door","properties":{"half":"lower"}}],
                 "functions":[{"function":"minecraft:explosion_decay"}]}]}]}
                """), "data/advancedrocketrycommunity/recipes/airlock_door.json", json("""
                {"type":"minecraft:crafting_shaped","category":"redstone","pattern":["II","II","II"],
                 "key":{"I":{"item":"minecraft:iron_ingot"}},"result":{"item":"advancedrocketrycommunity:airlock_door","count":3}}
                """), "data/advancedrocketrycommunity/advancements/recipes/redstone/airlock_door.json", json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_iron":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["minecraft:iron_ingot"]}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:airlock_door"}}},
                 "requirements":[["has_iron","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:airlock_door"]}}
                """));
    }

    public static Map<String, String> language(boolean chinese) {
        return Map.of("block.advancedrocketrycommunity.airlock_door", chinese ? "气闸门" : "Airlock Door");
    }

    /** Independently drawn riveted lower panel; digits select the existing encoder's grey palette. */
    public static String[] bottomGrid() {
        return new String[] {
                "6666666666666666", "6833333333333386", "6334444444444336", "6346666666666436",
                "6346555555556436", "6346555555556436", "6346555555556436", "6346666666666436",
                "6334444444444336", "6346666666666436", "6346555555556436", "6346555555556436",
                "6346555555556436", "6346666666666436", "6833333333333386", "6666666666666666"
        };
    }

    /** Independently drawn upper panel with a narrow inspection slit and paired hinge rivets. */
    public static String[] topGrid() {
        return new String[] {
                "6666666666666666", "6833333333333386", "6334444444444336", "6346666666666436",
                "6346222222226436", "6346299999926436", "6346288888826436", "6346222222226436",
                "6346666666666436", "6334444444444336", "6335555555554336", "6335555555554336",
                "6335555555554336", "6334444444444336", "6833333333333386", "6666666666666666"
        };
    }

    /** Independently drawn complete-door inventory silhouette, not a resized block grid. */
    public static String[] itemGrid() {
        return new String[] {
                "................", "....66666666....", "....63333336....", "....63999936....",
                "....63888836....", "....63333336....", "....63444436....", "....63666636....",
                "....63555586....", "....63666636....", "....63333336....", "....63666636....",
                "....63555536....", "....63666636....", "....66666666....", "................"
        };
    }

    public static Map<String, byte[]> textures() {
        return Map.of(BOTTOM_TEXTURE, V180MaterialArt.png(bottomGrid(), TINT),
                TOP_TEXTURE, V180MaterialArt.png(topGrid(), TINT), ITEM_TEXTURE, V180MaterialArt.png(itemGrid(), TINT));
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            textures().forEach((path, png) -> writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            })));
        }
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 original airlock door resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
