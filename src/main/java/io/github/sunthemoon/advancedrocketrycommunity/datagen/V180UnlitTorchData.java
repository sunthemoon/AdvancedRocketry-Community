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

/**
 * NEW ADR-066 section 3.2 unlit torch resources: six client files and one original grid, and the two combustion tags
 * and two loot tables on the server. Raw JSON with literal IDs; no item model, recipe or language writer.
 */
public final class V180UnlitTorchData implements DataProvider {
    private static final String TEXTURE = "assets/advancedrocketrycommunity/textures/block/unlit_torch.png";
    /** The one muted wood RGB tint multiplied into the encoder's grey levels. */
    private static final int TINT = 0xB08A5C;
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180UnlitTorchData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of(
                "assets/advancedrocketrycommunity/blockstates/unlit_torch.json", json("""
                        {"variants":{"":{"model":"advancedrocketrycommunity:block/unlit_torch"}}}
                        """),
                "assets/advancedrocketrycommunity/blockstates/unlit_wall_torch.json", json("""
                        {"variants":{
                         "facing=east":{"model":"advancedrocketrycommunity:block/unlit_wall_torch"},
                         "facing=south":{"model":"advancedrocketrycommunity:block/unlit_wall_torch","y":90},
                         "facing=west":{"model":"advancedrocketrycommunity:block/unlit_wall_torch","y":180},
                         "facing=north":{"model":"advancedrocketrycommunity:block/unlit_wall_torch","y":270}}}
                        """),
                "assets/advancedrocketrycommunity/models/block/unlit_torch.json", json("""
                        {"parent":"minecraft:block/template_torch","render_type":"minecraft:cutout",
                         "textures":{"torch":"advancedrocketrycommunity:block/unlit_torch"}}
                        """),
                "assets/advancedrocketrycommunity/models/block/unlit_wall_torch.json", json("""
                        {"parent":"minecraft:block/template_torch_wall","render_type":"minecraft:cutout",
                         "textures":{"torch":"advancedrocketrycommunity:block/unlit_torch"}}
                        """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of(
                "data/advancedrocketrycommunity/tags/blocks/combustion_torches.json", json("""
                        {"replace":false,"values":["minecraft:torch","minecraft:wall_torch"]}
                        """),
                "data/advancedrocketrycommunity/tags/items/combustion_fire_starters.json", json("""
                        {"replace":false,"values":["minecraft:flint_and_steel","minecraft:fire_charge",
                         "minecraft:blaze_rod","minecraft:blaze_powder"]}
                        """),
                "data/advancedrocketrycommunity/loot_tables/blocks/unlit_torch.json", sourceLoot(),
                "data/advancedrocketrycommunity/loot_tables/blocks/unlit_wall_torch.json", sourceLoot());
    }

    /** Original two-pixel shaft: a dark burnt-out tip over a plain wood shaft; no bright flame pixel. */
    public static String[] torchGrid() {
        return new String[] {
                "................", "................", "................", "................",
                "................", "................", ".......21.......", ".......32.......",
                ".......65.......", ".......76.......", ".......65.......", ".......76.......",
                ".......65.......", ".......54.......", ".......54.......", ".......43......."
        };
    }

    public static byte[] torchTexture() { return V180MaterialArt.png(torchGrid(), TINT); }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            byte[] png = torchTexture();
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

    @Override public String getName() { return "v1.8 unlit torch original resources"; }

    /** The saved source item through the block's dynamic entry, under the native explosion-survival rule. */
    private static JsonObject sourceLoot() {
        return json("""
                {"type":"minecraft:block","pools":[{"rolls":1.0,"bonus_rolls":0.0,
                 "entries":[{"type":"minecraft:dynamic","name":"advancedrocketrycommunity:unlit_torch_source"}],
                 "conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                """);
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
