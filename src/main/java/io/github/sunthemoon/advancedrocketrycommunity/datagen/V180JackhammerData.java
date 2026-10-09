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

/** Registered MIT recipe conversion and a new community-authored development icon. */
public final class V180JackhammerData implements DataProvider {
    public static final String ID = "advancedrocketrycommunity:jackhammer";
    public static final String TEXTURE = "assets/advancedrocketrycommunity/textures/item/jackhammer.png";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180JackhammerData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of("assets/advancedrocketrycommunity/models/item/jackhammer.json", json("""
                {"parent":"minecraft:item/handheld","textures":{"layer0":"advancedrocketrycommunity:item/jackhammer"}}
                """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of("data/advancedrocketrycommunity/recipes/jackhammer.json", json("""
                {"type":"minecraft:crafting_shaped","category":"equipment","pattern":[" pt","imp","di "],
                 "key":{"d":{"tag":"forge:gems/diamond"},"t":{"tag":"forge:rods/titanium"},
                 "i":{"tag":"forge:rods/iron"},"p":{"tag":"forge:plates/aluminum"},
                 "m":{"tag":"advancedrocketrycommunity:motors"}},
                 "result":{"item":"advancedrocketrycommunity:jackhammer","count":1}}
                """), "data/advancedrocketrycommunity/advancements/recipes/tools/jackhammer.json", json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_motor":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"tag":"advancedrocketrycommunity:motors"}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:jackhammer"}}},
                 "requirements":[["has_motor","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:jackhammer"]}}
                """), "data/minecraft/tags/items/pickaxes.json", json("""
                {"replace":false,"values":["advancedrocketrycommunity:jackhammer"]}
                """));
    }

    public static String[] grid() {
        return new String[] {
                "................", "..222222222222..", "..299222222992..", "..222267762222..",
                ".....269962.....", ".....268862.....", ".....267762.....", ".....266662.....",
                "......2772......", "......2992......", "......2992......", "......2882......",
                ".......99.......", ".......77.......", ".......55.......", "................"
        };
    }

    public static byte[] texture() { return V180MaterialArt.png(grid(), 0xBDD0DE); }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
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

    @Override public String getName() { return "v1.8 jackhammer resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
