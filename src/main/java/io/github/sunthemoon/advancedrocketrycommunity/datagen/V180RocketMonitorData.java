package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original instrument artwork, data and bilingual text; no external bitmap inputs. */
public final class V180RocketMonitorData implements DataProvider {
    public static final String ID = "rocket_monitoring_station";
    private static final String NS = "advancedrocketrycommunity";
    private final PackOutput output;
    private final boolean client, server;
    public V180RocketMonitorData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> clientFiles() {
        String base = "assets/" + NS + "/";
        return Map.of(base + "blockstates/" + ID + ".json", json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/" + ID + "\"}}}"),
                base + "models/block/" + ID + ".json", json("{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"" + NS + ":block/" + ID + "\"}}"),
                base + "models/item/" + ID + ".json", json("{\"parent\":\"" + NS + ":block/" + ID + "\"}"));
    }
    public static Map<String, JsonObject> serverFiles() {
        String base = "data/" + NS + "/";
        return Map.of(base + "loot_tables/blocks/" + ID + ".json", json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + ID + "\"}]}]}"),
                base + "recipes/" + ID + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"pattern\":[\" T \",\"UCU\",\" M \"],\"key\":{\"T\":{\"item\":\"" + NS + ":tracking_circuit\"},\"U\":{\"item\":\"" + NS + ":user_interface\"},\"C\":{\"item\":\"" + NS + ":control_board\"},\"M\":{\"item\":\"" + NS + ":machine_casing\"}},\"result\":{\"item\":\"" + NS + ":" + ID + "\"}}"),
                base + "advancements/recipes/misc/" + ID + ".json", json("{\"parent\":\"minecraft:recipes/root\",\"criteria\":{\"has_board\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":[\"" + NS + ":tracking_circuit\"]}]}},\"has_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + NS + ":" + ID + "\"}}},\"requirements\":[[\"has_board\",\"has_recipe\"]],\"rewards\":{\"recipes\":[\"" + NS + ":" + ID + "\"]}}"));
    }
    public static byte[] texture() {
        return V180MaterialArt.png(new String[] {
                "7777777777777777", "6444444444444441", "6494444444444941", "6411111111111141",
                "6412222222222141", "6412222282222141", "6412222888222141", "6412222282222141",
                "6412222282222141", "6412222282222141", "6412999999992141", "6411111111111141",
                "6444444444444441", "6494488849944941", "6444444444444441", "0000000000000000"}, 0x7ECFBA);
    }
    public static Map<String, String> language(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("block." + NS + "." + ID, chinese ? "火箭监测站" : "Rocket Monitoring Station");
        entries.put("monitor." + NS + ".link_refused", chinese ? "连接被拒绝：需要已加载且有权限的停靠火箭和设备。" : "Link refused: an owned, loaded docked rocket and device are required.");
        String[] en = {"Not linked", "Rocket available", "Waiting for loaded chunks", "Stale binding", "Owner mismatch", "Rocket busy", "No destination", "Unsupported saved data", "Launch requested"};
        String[] zh = {"未连接", "火箭可用", "等待区块加载", "连接已失效", "所有者不匹配", "火箭忙碌", "未选择目的地", "不支持的存档数据", "已请求发射"};
        for (var status : RocketMonitorStatus.values()) { entries.put(status.translationKey(), (chinese ? zh : en)[status.ordinal()]); }
        String prefix = "screen." + NS + ".monitor.";
        String[][] text = {
                {"previous", "Previous", "上一项"}, {"next", "Next", "下一项"}, {"quote", "Quote", "计算费用"},
                {"arm", "Arm", "启用红石"}, {"disarm", "Disarm", "解除待发"}, {"launch", "Launch", "发射"}, {"mode", "Mode", "模式"},
                {"flight", "State: %s", "状态：%s"}, {"motion", "Height %s m | Vertical %s m/t", "相对高度 %s 米 | 垂直速度 %s 米/刻"},
                {"destination", "To: %s", "目的地：%s"}, {"bank", "%s: %s", "%s：%s"},
                {"bank_amount", "%s / %s | Cost %s", "%s / %s | 费用 %s"}, {"cost", "Primary cost: %s", "主燃料费用：%s"},
                {"armed_mode", "%s | Comparator: %s", "%s | 比较器：%s"},
                {"armed.0", "Disarmed", "未待发"}, {"armed.1", "Armed", "已待发"},
                {"mode.0", "Height", "高度"}, {"mode.1", "Fuel", "燃料"}, {"role.-1", "No bank", "无燃料仓"},
                {"role.0", "Abstract fuel", "抽象燃料"}, {"role.1", "Monopropellant", "单组元燃料"},
                {"role.2", "Bipropellant fuel", "双组元燃料"}, {"role.3", "Oxidizer", "氧化剂"}, {"role.4", "Working fluid", "工质"},
                {"flight.-1", "Unavailable", "不可用"}, {"flight.0", "Assembled", "已组装"}, {"flight.1", "Fueled", "已加注"},
                {"flight.2", "Countdown", "倒计时"}, {"flight.3", "Ascent", "上升"}, {"flight.4", "Transit", "转移"},
                {"flight.5", "Descent", "下降"}, {"flight.6", "Landed", "已着陆"}, {"flight.7", "Recoverable failure", "可恢复故障"}, {"flight.8", "Disassembled", "已拆解"}};
        for (var line : text) { entries.put(prefix + line[0], line[chinese ? 2 : 1]); }
        return Map.copyOf(entries);
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            writes.add(CompletableFuture.runAsync(() -> {
                var png = texture();
                try { cache.writeIfNeeded(output.getOutputFolder().resolve("assets/" + NS + "/textures/block/" + ID + ".png"), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            }));
        }
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 rocket monitoring station"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
