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

/** Ordinary component acquisition data and two NEW drawings; no imported bitmap or legacy JSON. */
public final class V180CraftingComponentData implements DataProvider {
    public static final String CARBON_ID = "advancedrocketrycommunity:carbon_brick";
    public static final String UI_ID = "advancedrocketrycommunity:user_interface";
    public static final String CARBON_TEXTURE = "assets/advancedrocketrycommunity/textures/item/carbon_brick.png";
    public static final String UI_TEXTURE = "assets/advancedrocketrycommunity/textures/item/user_interface.png";

    // Audited recipe facts only. These identities are not bitmap provenance or an originality verdict.
    public static final String SOURCE_REPOSITORY = "Advanced-Rocketry/AdvancedRocketry";
    public static final String SOURCE_COMMIT = "c5cd5af62fc07cd4e0d24f06a16033f181c47c04";
    public static final String CARBON_SOURCE_PATH = "src/main/resources/assets/advancedrocketry/recipes/charcoalbrick.json";
    public static final int CARBON_SOURCE_BYTES = 586;
    public static final String CARBON_SOURCE_SHA256 = "28ec6e6ce7aaf732dc166765795844a56aefdaa85acb409670fbf1b693bb62d2";
    public static final String UI_SOURCE_PATH = "src/main/resources/assets/advancedrocketry/recipes/userinterface.json";
    public static final int UI_SOURCE_BYTES = 580;
    public static final String UI_SOURCE_SHA256 = "dc07f355dc95072ba3a5d57fde2948a7dd6209ca69dd3277c30a3c6beb64e599";

    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180CraftingComponentData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of(
                "assets/advancedrocketrycommunity/models/item/carbon_brick.json", json("""
                        {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/carbon_brick"}}
                        """),
                "assets/advancedrocketrycommunity/models/item/user_interface.json", json("""
                        {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/user_interface"}}
                        """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of(
                "data/advancedrocketrycommunity/recipes/carbon_brick.json", json("""
                        {"type":"minecraft:crafting_shapeless","category":"misc","group":"",
                         "ingredients":[{"item":"minecraft:charcoal"},{"item":"minecraft:charcoal"},
                          {"item":"minecraft:charcoal"},{"item":"minecraft:charcoal"},
                          {"item":"minecraft:charcoal"},{"item":"minecraft:charcoal"}],
                         "result":{"item":"advancedrocketrycommunity:carbon_brick","count":1}}
                        """),
                "data/advancedrocketrycommunity/recipes/user_interface.json", json("""
                        {"type":"minecraft:crafting_shaped","category":"misc","group":"","pattern":["lrl","fgf"],
                         "key":{"l":{"tag":"forge:dyes/lime"},"r":{"tag":"forge:dusts/redstone"},
                          "f":{"tag":"forge:dusts/glowstone"},"g":{"item":"minecraft:glass_pane"}},
                         "result":{"item":"advancedrocketrycommunity:user_interface","count":1}}
                        """),
                "data/advancedrocketrycommunity/advancements/recipes/misc/carbon_brick.json", json("""
                        {"parent":"minecraft:recipes/root","criteria":{
                         "has_charcoal":{"trigger":"minecraft:inventory_changed","conditions":{
                          "items":[{"items":["minecraft:charcoal"],"count":{"min":1}}]}},
                         "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                          "recipe":"advancedrocketrycommunity:carbon_brick"}}},
                         "requirements":[["has_charcoal","has_the_recipe"]],
                         "rewards":{"recipes":["advancedrocketrycommunity:carbon_brick"]}}
                        """),
                "data/advancedrocketrycommunity/advancements/recipes/misc/user_interface.json", json("""
                        {"parent":"minecraft:recipes/root","criteria":{
                         "has_glowstone_dust":{"trigger":"minecraft:inventory_changed","conditions":{
                          "items":[{"items":["minecraft:glowstone_dust"],"count":{"min":1}}]}},
                         "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                          "recipe":"advancedrocketrycommunity:user_interface"}}},
                         "requirements":[["has_glowstone_dust","has_the_recipe"]],
                         "rewards":{"recipes":["advancedrocketrycommunity:user_interface"]}}
                        """),
                "data/forge/tags/items/ingots/carbon.json", json("""
                        {"replace":false,"values":["advancedrocketrycommunity:carbon_brick"]}
                        """));
    }

    /** Sole language provider consumes these additions; this provider never writes language files. */
    public static Map<String, String> language(boolean chinese) {
        return chinese ? Map.of(
                "item.advancedrocketrycommunity.carbon_brick", "碳砖",
                "item.advancedrocketrycommunity.user_interface", "用户界面") : Map.of(
                "item.advancedrocketrycommunity.carbon_brick", "Carbon Brick",
                "item.advancedrocketrycommunity.user_interface", "User Interface");
    }

    /** Newly drawn compact compressed block, with a stepped top and irregular dark face facets. */
    public static String[] carbonGrid() {
        return new String[] {
                "................", "......4444......", "....44555544....", "..44565555443...",
                "..456554454432..", "..355443344322..", "..344334443222..", "..344433332222..",
                "..333443222112..", "..322333221112..", "..322223211112..", "..222332211112..",
                "..22122211112...", "...211111112....", ".....11112......", "................"
        };
    }

    /** Newly drawn control panel: inset display, three controls, and exposed top/bottom connectors. */
    public static String[] uiGrid() {
        return new String[] {
                "................", ".....6..6.......", ".....5..5.......", "..77777777777...",
                "..74222222247...", "..74988888947...", "..74826666847...", "..74882266847...",
                "..74444444447...", "..74373473447...", "..74262462447...", "..74333333347...",
                "..75555555557...", "....5..5..5.....", "....6..6..6.....", "................"
        };
    }

    public static byte[] carbonTexture() { return V180MaterialArt.png(carbonGrid(), 0x747D88); }
    public static byte[] uiTexture() { return V180MaterialArt.png(uiGrid(), 0x8ECBC0); }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            writes.add(writeTexture(cache, CARBON_TEXTURE, carbonTexture()));
            writes.add(writeTexture(cache, UI_TEXTURE, uiTexture()));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> writeTexture(CachedOutput cache, String path, byte[] png) {
        return CompletableFuture.runAsync(() -> {
            try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png)); }
            catch (IOException failed) { throw new UncheckedIOException(failed); }
        });
    }

    @Override public String getName() { return "v1.8 ordinary crafting components original resources"; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
