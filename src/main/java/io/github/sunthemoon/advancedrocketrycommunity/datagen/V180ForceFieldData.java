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

/** Original emitter aperture and sealed grid artwork; no imported bitmap or model body. */
public final class V180ForceFieldData implements DataProvider {
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180ForceFieldData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        String assets = "assets/advancedrocketrycommunity/";
        files.put(assets + "blockstates/force_field.json", json("""
                {"variants":{"":{"model":"advancedrocketrycommunity:block/force_field"}}}
                """));
        files.put(assets + "models/block/force_field.json", json("""
                {"parent":"minecraft:block/cube_all","textures":{"all":"advancedrocketrycommunity:block/force_field"}}
                """));
        files.put(assets + "blockstates/force_field_projector.json", json("""
                {"variants":{
                 "facing=north":{"model":"advancedrocketrycommunity:block/force_field_projector"},
                 "facing=east":{"model":"advancedrocketrycommunity:block/force_field_projector","y":90},
                 "facing=south":{"model":"advancedrocketrycommunity:block/force_field_projector","y":180},
                 "facing=west":{"model":"advancedrocketrycommunity:block/force_field_projector","y":270},
                 "facing=up":{"model":"advancedrocketrycommunity:block/force_field_projector","x":270},
                 "facing=down":{"model":"advancedrocketrycommunity:block/force_field_projector","x":90}}}
                """));
        files.put(assets + "models/block/force_field_projector.json", json("""
                {"parent":"minecraft:block/orientable","textures":{
                 "top":"advancedrocketrycommunity:block/force_field_projector_side",
                 "side":"advancedrocketrycommunity:block/force_field_projector_side",
                 "front":"advancedrocketrycommunity:block/force_field_projector_front"}}
                """));
        files.put(assets + "models/item/force_field_projector.json", json("""
                {"parent":"advancedrocketrycommunity:block/force_field_projector"}
                """));
        return Map.copyOf(files);
    }
    public static Map<String, JsonObject> serverFiles() {
        return Map.of("data/advancedrocketrycommunity/loot_tables/blocks/force_field_projector.json", json("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item",
                 "name":"advancedrocketrycommunity:force_field_projector"}],
                 "conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                """), "data/advancedrocketrycommunity/recipes/force_field_projector.json", json("""
                {"type":"minecraft:crafting_shaped","category":"redstone","pattern":["TGT","CMC","TRT"],
                 "key":{"T":{"tag":"forge:plates/titanium"},"G":{"item":"minecraft:glass"},
                  "C":{"item":"advancedrocketrycommunity:control_board"},
                  "M":{"item":"advancedrocketrycommunity:machine_casing"},
                  "R":{"item":"minecraft:redstone"}},
                 "result":{"item":"advancedrocketrycommunity:force_field_projector"}}
                """), "data/advancedrocketrycommunity/advancements/recipes/redstone/force_field_projector.json", json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_board":{"trigger":"minecraft:inventory_changed","conditions":{
                  "items":[{"items":["advancedrocketrycommunity:control_board"]}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                  "recipe":"advancedrocketrycommunity:force_field_projector"}}},
                 "requirements":[["has_board","has_the_recipe"]],
                 "rewards":{"recipes":["advancedrocketrycommunity:force_field_projector"]}}
                """));
    }
    public static Map<String, byte[]> textures() {
        return Map.of("force_field", V180MaterialArt.png(new String[]{
                "9999999999999999", "9555555955555559", "9595555955555959", "9559555955559559",
                "9555955955595559", "9555595955955559", "9555559999555559", "9999999999999999",
                "9555559999555559", "9555595955955559", "9555955955595559", "9559555955559559",
                "9595555955555959", "9555555955555559", "9555555955555559", "9999999999999999"
        }, 0x55C9E8), "force_field_projector_front", V180MaterialArt.png(new String[]{
                "3333333333333333", "3988888888888893", "3866666666666683", "3863333333333683",
                "3863599999953683", "3863955555593683", "3863953993593683", "3863959339593683",
                "3863959339593683", "3863953993593683", "3863955555593683", "3863599999953683",
                "3863333333333683", "3866666666666683", "3988888888888893", "3333333333333333"
        }, 0x62BFD0), "force_field_projector_side", V180MaterialArt.png(new String[]{
                "3333333333333333", "3988888888888893", "3866666666666683", "3865555555555683",
                "3865333333335683", "3865388888835683", "3865355555535683", "3865388888835683",
                "3865355555535683", "3865388888835683", "3865355555535683", "3865388888835683",
                "3865333333335683", "3865555555555683", "3988888888888893", "3333333333333333"
        }, 0x8A9DA8));
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            textures().forEach((name, png) -> writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve("assets/advancedrocketrycommunity/textures/block/" + name + ".png"),
                        png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            })));
        }
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original force-field resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
