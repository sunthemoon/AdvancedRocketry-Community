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

/** NEW station-light geometry, acquisition and face grid; no asset import. */
public final class V180StationLightData implements DataProvider {
    public static final String ID = "advancedrocketrycommunity:station_light";
    public static final String TEXTURE = "assets/advancedrocketrycommunity/textures/block/station_light.png";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180StationLightData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of(
                "assets/advancedrocketrycommunity/blockstates/station_light.json", json("""
                        {"variants":{"":{"model":"advancedrocketrycommunity:block/station_light"}}}
                        """),
                "assets/advancedrocketrycommunity/models/block/station_light.json", json("""
                        {"parent":"minecraft:block/cube_all","textures":{"all":"advancedrocketrycommunity:block/station_light"}}
                        """),
                "assets/advancedrocketrycommunity/models/item/station_light.json", json("""
                        {"parent":"advancedrocketrycommunity:block/station_light"}
                        """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of(
                "data/advancedrocketrycommunity/loot_tables/blocks/station_light.json", json("""
                        {"type":"minecraft:block","pools":[{"rolls":1.0,"bonus_rolls":0.0,
                         "entries":[{"type":"minecraft:item","name":"advancedrocketrycommunity:station_light"}],
                         "functions":[{"function":"minecraft:explosion_decay"}]}]}
                        """),
                "data/advancedrocketrycommunity/recipes/station_light.json", json("""
                        {"type":"minecraft:crafting_shaped","category":"building","pattern":["IGI","GLG","IGI"],
                         "key":{"I":{"item":"minecraft:iron_ingot"},"G":{"item":"minecraft:glass"},
                                "L":{"item":"minecraft:glowstone"}},
                         "result":{"item":"advancedrocketrycommunity:station_light","count":4}}
                        """),
                "data/advancedrocketrycommunity/advancements/recipes/building_blocks/station_light.json", json("""
                        {"parent":"minecraft:recipes/root","criteria":{
                         "has_glowstone":{"trigger":"minecraft:inventory_changed","conditions":{
                          "items":[{"items":["minecraft:glowstone"],"count":{"min":1}}]}},
                         "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                          "recipe":"advancedrocketrycommunity:station_light"}}},
                         "requirements":[["has_glowstone","has_the_recipe"]],
                         "rewards":{"recipes":["advancedrocketrycommunity:station_light"]}}
                        """));
    }

    /** Original inset light panel and stepped metal frame; every pixel is opaque. */
    public static String[] grid() {
        return new String[] {
                "5555555555555555", "5766666666666675", "5677777777777765", "5678888888888765",
                "5678999999998765", "5678999999998765", "5678999999998765", "5678999999998765",
                "5678999999998765", "5678999999998765", "5678999999998765", "5678999999998765",
                "5678888888888765", "5677777777777765", "5766666666666675", "5555555555555555"
        };
    }

    public static byte[] texture() { return V180MaterialArt.png(grid(), 0xE8EDF2); }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            byte[] png = texture();
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(TEXTURE), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            }));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 ordinary station light original resources"; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
