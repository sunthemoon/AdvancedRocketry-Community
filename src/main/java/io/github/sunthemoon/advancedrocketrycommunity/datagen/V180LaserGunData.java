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

/** New MIT acquisition recipe and original code-drawn icon; no legacy or official art copied. */
public final class V180LaserGunData implements DataProvider {
    public static final String ID = "advancedrocketrycommunity:basic_laser_gun";
    public static final String TEXTURE = "assets/advancedrocketrycommunity/textures/item/basic_laser_gun.png";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180LaserGunData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> clientFiles() {
        return Map.of("assets/advancedrocketrycommunity/models/item/basic_laser_gun.json", json("""
                {"parent":"minecraft:item/handheld","textures":{"layer0":"advancedrocketrycommunity:item/basic_laser_gun"}}
                """));
    }
    public static Map<String, JsonObject> serverFiles() {
        return Map.of("data/advancedrocketrycommunity/recipes/basic_laser_gun.json", json("""
                {"type":"minecraft:crafting_shaped","category":"equipment","pattern":["ppd","rcp"," r "],
                 "key":{"p":{"tag":"forge:plates/steel"},"r":{"tag":"forge:rods/iron"},
                 "d":{"tag":"forge:gems/diamond"},"c":{"item":"advancedrocketrycommunity:basic_circuit_plate"}},
                 "result":{"item":"advancedrocketrycommunity:basic_laser_gun","count":1}}
                """), "data/advancedrocketrycommunity/advancements/recipes/tools/basic_laser_gun.json", json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_circuit":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["advancedrocketrycommunity:basic_circuit_plate"]}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:basic_laser_gun"}}},
                 "requirements":[["has_circuit","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:basic_laser_gun"]}}
                """));
    }
    public static Map<String, String> translations(boolean chinese) {
        return Map.of("item.advancedrocketrycommunity.basic_laser_gun", chinese ? "基础激光枪" : "Basic Laser Gun",
                "tooltip.advancedrocketrycommunity.basic_laser_gun.use", chinese ? "主手按住使用：连续瞄准 20 刻挖掘，冷却 5 刻" : "Hold use in the main hand: aim for 20 ticks to mine, then cool down for 5 ticks",
                "tooltip.advancedrocketrycommunity.basic_laser_gun.limits", chinese ? "射程 50 格；钻石级镐采收；拒绝方块实体；实体伤害 1 点" : "50-block range; diamond pickaxe harvest; no block entities; 1 point of entity damage",
                "message.advancedrocketrycommunity.basic_laser_gun.main_hand", chinese ? "激光枪需要放在主手" : "The laser gun requires the main hand",
                "message.advancedrocketrycommunity.basic_laser_gun.disabled", chinese ? "基础激光枪：已禁用" : "Basic laser gun: disabled");
    }
    public static String[] grid() {
        return new String[] {"................", "................", "......777.......", "..77778887777...",
                ".79998888899675.", ".78887777788675.", "..77776667777...", ".....76667......",
                ".....76677......", "....7667........", "....7667........", "...7667.........",
                "...7777.........", "................", "................", "................"};
    }
    public static byte[] texture() { return V180MaterialArt.png(grid(), 0x8EE5D6); }
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
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 basic laser gun resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
