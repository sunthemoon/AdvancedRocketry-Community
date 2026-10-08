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
 * NEW ordinary thermite material and standing/wall torch resources: eight client and six server files, raw JSON
 * with literal IDs and two original grids. No asset import, tag or language writer.
 */
public final class V180ThermiteData implements DataProvider {
    private static final String DUST_TEXTURE = "assets/advancedrocketrycommunity/textures/item/thermite.png";
    private static final String TORCH_TEXTURE = "assets/advancedrocketrycommunity/textures/block/thermite_torch.png";
    /** The one warm RGB tint multiplied into the encoder's grey levels of both grids. */
    private static final int TINT = 0xF0B080;
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180ThermiteData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of(
                "assets/advancedrocketrycommunity/blockstates/thermite_torch.json", json("""
                        {"variants":{"":{"model":"advancedrocketrycommunity:block/thermite_torch"}}}
                        """),
                "assets/advancedrocketrycommunity/blockstates/thermite_wall_torch.json", json("""
                        {"variants":{
                         "facing=east":{"model":"advancedrocketrycommunity:block/thermite_wall_torch"},
                         "facing=south":{"model":"advancedrocketrycommunity:block/thermite_wall_torch","y":90},
                         "facing=west":{"model":"advancedrocketrycommunity:block/thermite_wall_torch","y":180},
                         "facing=north":{"model":"advancedrocketrycommunity:block/thermite_wall_torch","y":270}}}
                        """),
                "assets/advancedrocketrycommunity/models/block/thermite_torch.json", json("""
                        {"parent":"minecraft:block/template_torch","render_type":"minecraft:cutout",
                         "textures":{"torch":"advancedrocketrycommunity:block/thermite_torch"}}
                        """),
                "assets/advancedrocketrycommunity/models/block/thermite_wall_torch.json", json("""
                        {"parent":"minecraft:block/template_torch_wall","render_type":"minecraft:cutout",
                         "textures":{"torch":"advancedrocketrycommunity:block/thermite_torch"}}
                        """),
                "assets/advancedrocketrycommunity/models/item/thermite.json", json("""
                        {"parent":"minecraft:item/generated",
                         "textures":{"layer0":"advancedrocketrycommunity:item/thermite"}}
                        """),
                "assets/advancedrocketrycommunity/models/item/thermite_torch.json", json("""
                        {"parent":"minecraft:item/generated",
                         "textures":{"layer0":"advancedrocketrycommunity:block/thermite_torch"}}
                        """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of(
                "data/advancedrocketrycommunity/recipes/thermite.json", json("""
                        {"type":"minecraft:crafting_shapeless","category":"misc",
                         "ingredients":[{"tag":"forge:dusts/aluminum"},{"tag":"forge:dusts/iron"}],
                         "result":{"item":"advancedrocketrycommunity:thermite"}}
                        """),
                "data/advancedrocketrycommunity/recipes/thermite_torch.json", json("""
                        {"type":"minecraft:crafting_shapeless","category":"building",
                         "ingredients":[{"tag":"forge:rods/wooden"},{"tag":"forge:dusts/thermite"}],
                         "result":{"item":"advancedrocketrycommunity:thermite_torch","count":4}}
                        """),
                "data/advancedrocketrycommunity/advancements/recipes/misc/thermite.json", json("""
                        {"parent":"minecraft:recipes/root","criteria":{
                         "has_aluminum_dust":{"trigger":"minecraft:inventory_changed","conditions":{
                          "items":[{"tag":"forge:dusts/aluminum"}]}},
                         "has_iron_dust":{"trigger":"minecraft:inventory_changed","conditions":{
                          "items":[{"tag":"forge:dusts/iron"}]}},
                         "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                          "recipe":"advancedrocketrycommunity:thermite"}}},
                         "requirements":[["has_aluminum_dust","has_iron_dust","has_the_recipe"]],
                         "rewards":{"recipes":["advancedrocketrycommunity:thermite"]}}
                        """),
                "data/advancedrocketrycommunity/advancements/recipes/building_blocks/thermite_torch.json", json("""
                        {"parent":"minecraft:recipes/root","criteria":{
                         "has_thermite":{"trigger":"minecraft:inventory_changed","conditions":{
                          "items":[{"tag":"forge:dusts/thermite"}]}},
                         "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                          "recipe":"advancedrocketrycommunity:thermite_torch"}}},
                         "requirements":[["has_thermite","has_the_recipe"]],
                         "rewards":{"recipes":["advancedrocketrycommunity:thermite_torch"]}}
                        """),
                "data/advancedrocketrycommunity/loot_tables/blocks/thermite_torch.json", torchLoot(),
                "data/advancedrocketrycommunity/loot_tables/blocks/thermite_wall_torch.json", torchLoot());
    }

    /** Original low powder heap with two loose grains; lit from the upper left, transparent margin. */
    public static String[] dustGrid() {
        return new String[] {
                "................", "................", "................", "................",
                "................", "................", "..........8.....", "....7...........",
                ".......98.......", ".....9988877....", "....998887766...", "...99888776665..",
                "..9888877766554.", ".88877766655544.", ".66655544433332.", "................"
        };
    }

    /** Original two-pixel shaft: bright tip, lighter cap band, then a shaft darkening downward. */
    public static String[] torchGrid() {
        return new String[] {
                "................", "................", "................", "................",
                "................", "................", ".......99.......", ".......98.......",
                ".......77.......", ".......54.......", ".......54.......", ".......43.......",
                ".......43.......", ".......32.......", ".......32.......", ".......21......."
        };
    }

    public static byte[] dustTexture() { return V180MaterialArt.png(dustGrid(), TINT); }

    public static byte[] torchTexture() { return V180MaterialArt.png(torchGrid(), TINT); }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            writes.add(png(cache, DUST_TEXTURE, dustTexture()));
            writes.add(png(cache, TORCH_TEXTURE, torchTexture()));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 ordinary thermite original resources"; }

    private CompletableFuture<?> png(CachedOutput cache, String path, byte[] png) {
        return CompletableFuture.runAsync(() -> {
            try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png)); }
            catch (IOException failed) { throw new UncheckedIOException(failed); }
        });
    }

    /** Each block keeps its own default table; both drop one paired torch item unless destroyed by explosion. */
    private static JsonObject torchLoot() {
        return json("""
                {"type":"minecraft:block","pools":[{"rolls":1.0,"bonus_rolls":0.0,
                 "entries":[{"type":"minecraft:item","name":"advancedrocketrycommunity:thermite_torch"}],
                 "conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                """);
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
