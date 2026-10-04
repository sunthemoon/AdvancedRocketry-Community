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

/** NEW project recipe/model/grid, pre-recorded in the C18a analyzer provenance. No asset import. */
public final class V180AtmosphereAnalyzerData implements DataProvider {
    public static final String ID = "advancedrocketrycommunity:atmosphere_analyzer";
    public static final String TEXTURE = "assets/advancedrocketrycommunity/textures/item/atmosphere_analyzer.png";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180AtmosphereAnalyzerData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of("assets/advancedrocketrycommunity/models/item/atmosphere_analyzer.json", json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/atmosphere_analyzer"}}
                """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of("data/advancedrocketrycommunity/recipes/atmosphere_analyzer.json", json("""
                {"type":"minecraft:crafting_shapeless","category":"misc","ingredients":[
                 {"item":"advancedrocketrycommunity:basic_circuit"},{"item":"minecraft:glass_pane"},{"item":"minecraft:iron_ingot"}],
                 "result":{"item":"advancedrocketrycommunity:atmosphere_analyzer","count":1}}
                """), "data/advancedrocketrycommunity/advancements/recipes/misc/atmosphere_analyzer.json", json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_input":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["advancedrocketrycommunity:basic_circuit"]}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:atmosphere_analyzer"}}},
                 "requirements":[["has_input","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:atmosphere_analyzer"]}}
                """));
    }

    /** Original silhouette, bright readout, dark probe and low-contrast housing in a teal-grey palette. */
    public static String[] grid() {
        return new String[] {
                "................", ".........77.....", ".........55.....", "........3553....",
                "....666666666...", "...64444444446..", "...64999999446..", "...64922229446..",
                "...64929929446..", "...64922229446..", "...64444444446..", "...64488688446..",
                "...64444444446..", "...63333333336..", "....666666666...", "................"
        };
    }

    public static byte[] texture() { return V180MaterialArt.png(grid(), 0x8AD2C2); }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            byte[] png = texture();
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(TEXTURE), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            }));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 atmosphere analyzer original resources"; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
