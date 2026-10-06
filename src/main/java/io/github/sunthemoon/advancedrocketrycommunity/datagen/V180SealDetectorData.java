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

/** NEW task-frozen probe grid and ordinary recipe; provenance was published before authoring. */
public final class V180SealDetectorData implements DataProvider {
    public static final String ID = "advancedrocketrycommunity:seal_detector";
    public static final String TEXTURE = "assets/advancedrocketrycommunity/textures/item/seal_detector.png";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180SealDetectorData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of("assets/advancedrocketrycommunity/models/item/seal_detector.json", json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/seal_detector"}}
                """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of("data/advancedrocketrycommunity/recipes/seal_detector.json", json("""
                {"type":"minecraft:crafting_shapeless","category":"misc","ingredients":[
                 {"item":"minecraft:iron_ingot"},{"item":"minecraft:redstone"},{"item":"minecraft:glass_pane"}],
                 "result":{"item":"advancedrocketrycommunity:seal_detector","count":1}}
                """), "data/advancedrocketrycommunity/advancements/recipes/misc/seal_detector.json", json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_input":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["minecraft:redstone"]}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:seal_detector"}}},
                 "requirements":[["has_input","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:seal_detector"]}}
                """));
    }

    public static String[] grid() {
        return new String[] {
                "................", "..22........22..", "..29........92..", "..299......992..",
                "...299....992...", "....22222222....", "....26777762....", "....26799762....",
                "....26733762....", "....26777762....", ".....266662.....", "......2442......",
                "......2442......", "......2442......", "......2222......", "................"
        };
    }

    public static byte[] texture() { return V180MaterialArt.png(grid(), 0xE0BD72); }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            byte[] png = texture();
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(TEXTURE), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 seal detector original resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
