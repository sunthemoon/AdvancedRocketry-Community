package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** New Item IO board data; both original producers, with no imported JSON or bitmap. */
public final class V180IoBoardData implements DataProvider {
    public static final String ITEM_ID = "advancedrocketrycommunity:item_io_board";
    public static final String PRECISION_ID = "advancedrocketrycommunity:precision_item_io_board";
    public static final String TEXTURE = "assets/advancedrocketrycommunity/textures/item/item_io_board.png";
    private static final String DATA = "data/advancedrocketrycommunity/";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180IoBoardData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of("assets/advancedrocketrycommunity/models/item/item_io_board.json", json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/item_io_board"}}
                """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of(DATA + "recipes/item_io_board.json", json("""
                {"type":"minecraft:crafting_shaped","category":"misc","group":"","pattern":["rvr","dwd","dpd"],
                 "key":{"r":{"tag":"forge:dusts/redstone"},"v":{"tag":"forge:gems/quartz"},
                  "d":{"tag":"forge:dusts/gold"},"w":{"tag":"minecraft:wooden_slabs"},
                  "p":{"tag":"forge:plates/iron"}},
                 "result":{"item":"advancedrocketrycommunity:item_io_board","count":1}}
                """), DATA + "recipes/precision_item_io_board.json", json("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:dusts/redstone"},"count":1},
                  {"ingredient":{"tag":"forge:plates/gold"},"count":1},
                  {"ingredient":{"tag":"forge:plates/steel"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:item_io_board","count":1}],
                 "processing_time":200,"energy_per_tick":10}
                """), DATA + "advancements/recipes/misc/item_io_board.json", json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_gold_dust":{"trigger":"minecraft:inventory_changed","conditions":{
                  "items":[{"tag":"forge:dusts/gold","count":{"min":1}}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                  "recipe":"advancedrocketrycommunity:item_io_board"}}},
                 "requirements":[["has_gold_dust","has_the_recipe"]],
                 "rewards":{"recipes":["advancedrocketrycommunity:item_io_board"]}}
                """));
    }

    /** The existing sole language writer consumes these additions. */
    public static Map<String, String> language(boolean chinese) {
        return Map.of("item.advancedrocketrycommunity.item_io_board", chinese ? "物品 IO 电路板" : "Item IO Circuit Board");
    }

    /** Original asymmetric connector board: split inputs, a central package, and a comb output. */
    public static String[] grid() {
        return new String[] {
                "................", "................", "...3333333333...", "...3555555553...",
                ".993577555753...", ".663577555753...", "...3555777753...", "...357722775369.",
                "...357211275366.", "...357211275369.", ".99357222275366.", ".663577777753...",
                "...3555555553...", "...3333333333...", "................", "................"
        };
    }

    public static byte[] texture() { return V180MaterialArt.png(grid(), 0xC5C987); }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, json) -> writes.add(
                    DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
            writes.add(CompletableFuture.runAsync(() -> {
                byte[] png = texture();
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(TEXTURE), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            }));
        }
        if (server) {
            serverFiles().forEach((path, json) -> writes.add(
                    DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 Item IO board original resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
