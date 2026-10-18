package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original landing tile; concrete is a registry reference, never a copied vanilla bitmap. */
public final class V180LandingPadData implements DataProvider {
    public static final String TEXTURE = "assets/advancedrocketrycommunity/textures/block/landing_pad.png";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180LandingPadData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> clientFiles() {
        return Map.of(
                "assets/advancedrocketrycommunity/blockstates/landing_pad.json", json("""
                        {"variants":{"":{"model":"advancedrocketrycommunity:block/landing_pad"}}}
                        """),
                "assets/advancedrocketrycommunity/models/block/landing_pad.json", json("""
                        {"parent":"minecraft:block/cube_all","textures":{"all":"advancedrocketrycommunity:block/landing_pad"}}
                        """),
                "assets/advancedrocketrycommunity/models/item/landing_pad.json", json("""
                        {"parent":"advancedrocketrycommunity:block/landing_pad"}
                        """));
    }
    public static Map<String, JsonObject> serverFiles() {
        return Map.of(
                "data/advancedrocketrycommunity/loot_tables/blocks/landing_pad.json", json("""
                        {"type":"minecraft:block","pools":[{"rolls":1,"entries":[
                         {"type":"minecraft:item","name":"advancedrocketrycommunity:landing_pad"}],
                         "conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                        """),
                "data/advancedrocketrycommunity/recipes/landing_pad.json", json("""
                        {"type":"minecraft:crafting_shaped","category":"building","pattern":["III","CRC","CCC"],
                         "key":{"I":{"item":"minecraft:iron_ingot"},"C":{"item":"minecraft:gray_concrete"},
                                "R":{"item":"minecraft:redstone"}},"result":{"item":"advancedrocketrycommunity:landing_pad"}}
                        """),
                "data/advancedrocketrycommunity/advancements/recipes/building_blocks/landing_pad.json", json("""
                        {"parent":"minecraft:recipes/root","criteria":{
                         "has_concrete":{"trigger":"minecraft:inventory_changed","conditions":{"items":[
                          {"items":["minecraft:gray_concrete"],"count":{"min":1}}]}},
                         "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                          "recipe":"advancedrocketrycommunity:landing_pad"}}},
                         "requirements":[["has_concrete","has_the_recipe"]],
                         "rewards":{"recipes":["advancedrocketrycommunity:landing_pad"]}}
                        """));
    }
    public static byte[] texture() {
        return V180MaterialArt.png(new String[] {
                "5555555555555555", "5777777777777775", "5755555555555575", "5755555555555575",
                "5755995555995575", "5755995555995575", "5755995555995575", "5755999999995575",
                "5755999999995575", "5755995555995575", "5755995555995575", "5755995555995575",
                "5755555555555575", "5755555555555575", "5777777777777775", "5555555555555555"
        }, 0xF1B748);
    }
    public static Map<String, String> language(boolean chinese) {
        String prefix = "message.advancedrocketrycommunity.pad.";
        return Map.ofEntries(
                Map.entry("block.advancedrocketrycommunity.landing_pad", chinese ? "着陆平台" : "Landing Pad"),
                Map.entry(prefix + "registered", chinese ? "已注册着陆平台 %s（%s，版本 %s）。" : "Registered pad %s (%s, epoch %s)."),
                Map.entry(prefix + "removed", chinese ? "已取消注册着陆平台。" : "Landing pad unregistered."),
                Map.entry(prefix + "refused", chinese ? "操作被拒绝：需要空间站所有者或管理员权限、有效名称和可用平台名额。" : "Refused: station owner/operator, a valid name and an available pad slot are required."),
                Map.entry(prefix + "unavailable", chinese ? "平台不可用、未加载或操作过于频繁。" : "Pad unavailable, unloaded, or interaction rate exceeded."),
                Map.entry(prefix + "selected", chinese ? "已选择 %s。打开附近火箭的飞行控制台并发射。" : "Selected %s. Open the nearby rocket flight console and launch."),
                Map.entry(prefix + "console", chinese ? "请打开附近火箭的飞行控制台并发射。" : "Open the nearby rocket flight console and launch."),
                Map.entry(prefix + "list", chinese ? "%s：%s 个已注册平台。点击条目选择飞行命令。" : "%s: %s registered pads. Click an entry to select its flight command."),
                Map.entry(prefix + "entry", "%s (%s, %s): %s"),
                Map.entry(prefix + "loaded", chinese ? "已加载" : "loaded"),
                Map.entry(prefix + "unloaded", chinese ? "未加载或身份过期" : "unloaded or stale"),
                Map.entry(prefix + "reason.identity", chinese ? "平台未加载、已移除、身份过期或所有权已变更。" : "Pad unloaded, removed, stale or no longer owned by this station."),
                Map.entry(prefix + "reason.reserved", chinese ? "着陆区域已被其他转移预留。" : "Landing region reserved by another transfer."),
                Map.entry(prefix + "reason.claimed", chinese ? "平台已被其他转移占用。" : "Pad claimed by another transfer."),
                Map.entry(prefix + "reason.arithmetic", chinese ? "着陆结构或坐标超出安全范围。" : "Landing structure or arithmetic outside safe bounds."),
                Map.entry(prefix + "reason.block_budget", chinese ? "完整着陆检查超过 2,048 方块预算。" : "Full landing inspection exceeds the 2,048-block budget."),
                Map.entry(prefix + "reason.chunk_budget", chinese ? "着陆覆盖范围超过 16 区块预算。" : "Landing footprint exceeds the 16-chunk budget."),
                Map.entry(prefix + "reason.bounds", chinese ? "着陆超出空间站区域、建造高度或世界边界。" : "Landing leaves station, build-height or world-border bounds."),
                Map.entry(prefix + "reason.unloaded", chinese ? "着陆覆盖范围包含未加载区块。" : "Landing footprint includes an unloaded chunk."),
                Map.entry(prefix + "reason.too_small", chinese ? "平台太小：每一列底部都需要坚固支撑。" : "Pad too small: every footprint column needs solid support."),
                Map.entry(prefix + "reason.obstructed", chinese ? "着陆空间被方块阻挡。" : "Landing clearance is obstructed."),
                Map.entry(prefix + "reason.occupied", chinese ? "平台已被另一枚火箭占用。" : "Pad occupied by another rocket."),
                Map.entry(prefix + "reason.entity_budget", chinese ? "附近火箭的占用检查超过 64 枚预算。" : "Nearby rocket occupancy inspection exceeds the 64-rocket budget."),
                Map.entry(prefix + "reason.entities_loading", chinese ? "到达等待中：着陆区域的实体仍在加载。" : "Arrival waiting: the landing area's entities are still loading."),
                Map.entry(prefix + "landing_refused", chinese ? "着陆平台拒绝着陆：%s" : "Landing pad refused: %s"));
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            byte[] png = texture();
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(TEXTURE), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            }));
        }
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original named landing-pad resources"; }
    private static JsonObject json(String source) { return JsonParser.parseString(source).getAsJsonObject(); }
}
