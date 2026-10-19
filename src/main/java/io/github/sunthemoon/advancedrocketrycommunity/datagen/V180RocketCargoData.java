package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** W109 fluid cargo resources. MIT original code/text; see the pre-authoring provenance declaration. */
public final class V180RocketCargoData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180RocketCargoData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (RocketCargoRole role : RocketCargoRole.values()) {
            String id = role.id(), base = "assets/" + NS + "/";
            files.put(base + "blockstates/" + id + ".json", json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/" + id + "\"}}}"));
            files.put(base + "models/block/" + id + ".json", json("{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\""
                    + NS + ":block/" + id + "\"}}"));
            files.put(base + "models/item/" + id + ".json", json("{\"parent\":\"" + NS + ":block/" + id + "\"}"));
        }
        return files;
    }

    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (RocketCargoRole role : RocketCargoRole.values()) {
            String id = role.id(), base = "data/" + NS + "/";
            // The block supplies one carrier, including on explosion; protected devices suppress all loot.
            files.put(base + "loot_tables/blocks/" + id + ".json", json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,"
                    + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + id + "\"}]}]}"));
            String pattern = role == RocketCargoRole.LOAD ? "[\" H \",\"LMT\"]" : "[\"LMT\",\" H \"]";
            files.put(base + "recipes/" + id + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\","
                    + "\"pattern\":" + pattern + ",\"key\":{\"H\":{\"item\":\"minecraft:hopper\"},\"L\":{\"item\":\""
                    + NS + ":liquid_io_board\"},\"M\":{\"item\":\"" + NS + ":machine_casing\"},\"T\":{\"type\":\""
                    + V180FluidFuelLoaderData.EMPTY_TANK + "\"}},\"result\":{\"item\":\"" + NS + ":" + id + "\"}}"));
            files.put(base + "advancements/recipes/misc/" + id + ".json", json("{\"parent\":\"minecraft:recipes/root\","
                    + "\"criteria\":{\"has_board\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":[\""
                    + NS + ":liquid_io_board\"]}]}},\"has_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\""
                    + NS + ":" + id + "\"}}},\"requirements\":[[\"has_board\",\"has_recipe\"]],\"rewards\":{\"recipes\":[\""
                    + NS + ":" + id + "\"]}}"));
        }
        return files;
    }

    public static Map<String, String> language(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("block." + NS + ".rocket_fluid_loader", chinese ? "火箭流体装载器" : "Rocket Fluid Loader");
        entries.put("block." + NS + ".rocket_fluid_unloader", chinese ? "火箭流体卸载器" : "Rocket Fluid Unloader");
        String[][] statuses = {
            {"ADMISSION_DISABLED", "Cargo transfer is not enabled; no cargo moved.", "货物转移尚未启用；未移动货物。"},
            {"ESCROW_FULL", "Escrow capacity is full; no cargo moved.", "托管容量已满；未移动货物。"},
            {"LEDGER_UNAVAILABLE", "The escrow ledger is unavailable; no cargo moved.", "托管账本不可用；未移动货物。"},
            {"TRANSFER_JOURNAL_UNPROVED", "The rocket's transfer record is not confirmed; no cargo moved.", "火箭的转移记录尚未确认；未移动货物。"},
            {"ESCROW_PENDING", "Resources are locked pending durable escrow confirmation.", "资源已锁定，等待托管的持久确认。"},
            {"UNBOUND", "Use a linker on this device, then its rocket.", "请先用链接器点击此设备，再点击它的火箭。"},
            {"STALE_BINDING", "Binding is stale; bind this device again.", "绑定已失效；请重新绑定此设备。"},
            {"WAITING_UNLOADED", "The bound rocket's area is not loaded; waiting.", "绑定火箭所在区域未加载；正在等待。"},
            {"OUT_OF_RANGE", "The rocket is more than 6 blocks away.", "火箭距离设备超过 6 格。"},
            {"NOT_OWNER", "Only the device's owner can do this.", "只有设备所有者可以这样做。"},
            {"ROCKET_BUSY", "The rocket is moving, occupied or ridden; no cargo moved.", "火箭正在移动、有乘客或被骑乘；未移动货物。"},
            {"UNSUPPORTED_DATA", "Unsupported data is kept unchanged; ask an administrator.", "不受支持的数据已原样保留；请联系管理员。"},
            {"INVALID_DATA", "Invalid or duplicate data is kept unchanged; ask an administrator.", "无效或重复的数据已原样保留；请联系管理员。"},
            {"READY", "Ready to transfer cargo.", "可以转移货物。"},
            {"PREPARING", "Preparing escrow; no cargo has moved yet.", "正在准备托管；尚未移动货物。"},
            {"TRANSFERRING", "Transferring cargo.", "正在转移货物。"},
            {"DEVICE_EMPTY", "The device's tank is empty.", "设备的储罐为空。"},
            {"DEVICE_FULL", "The device's tank is full.", "设备的储罐已满。"},
            {"NO_CARGO", "The rocket has no cargo to unload.", "火箭没有可卸载的货物。"},
            {"CARGO_FULL", "The rocket's cargo storage is full.", "火箭的货物储存空间已满。"},
            {"NO_CARGO_STORAGE", "The rocket has no compatible cargo storage.", "火箭没有相容的货物储存空间。"},
            {"INCOMPATIBLE_CARGO", "The stored fluids do not match; no cargo moved.", "储存的流体不匹配；未移动货物。"},
            {"CARGO_TOO_LARGE", "Cargo data exceeds the transfer limit; no cargo moved.", "货物数据超过转移限制；未移动货物。"},
            {"UNRESOLVED_LOOT", "Cargo contains unresolved loot; no cargo moved.", "货物包含尚未生成的战利品；未移动货物。"},
            {"OTHER_TRANSFER_PENDING", "Another transfer on this rocket is pending; nothing moved.", "该火箭有另一项转移待完成；未移动任何货物。"},
            {"RECEIPT_BACKED", "This rocket is receipt-backed; cargo transfer is unavailable.", "该火箭已有结算凭据；无法转移货物。"},
            {"LEGACY_ROCKET", "This legacy rocket needs migration before cargo transfer.", "此旧版火箭需要迁移后才能转移货物。"}
        };
        for (String[] status : statuses) {
            entries.put(RocketCargoStatus.valueOf(status[0]).translationKey(), status[chinese ? 2 : 1]);
        }
        String screen = "screen." + NS + ".rocket_fluid_cargo.";
        entries.put(screen + "contents", chinese ? "%s：%s / %s mB" : "%s: %s / %s mB");
        entries.put(screen + "empty", chinese ? "空：0 / %s mB" : "Empty: 0 / %s mB");
        entries.put(screen + "unknown_fluid", chinese ? "未知流体" : "Unknown fluid");
        entries.put(screen + "bound", chinese ? "已绑定火箭" : "Bound to a rocket");
        entries.put(screen + "unbound", chinese ? "未绑定" : "Not bound");
        entries.put(screen + "status_unknown", chinese ? "未知状态" : "Unknown status");
        entries.put(screen + "cursor", chinese ? "储存位置：%s, %s, %s" : "Storage: %s, %s, %s");
        return Map.copyOf(entries);
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            V180RocketCargoArt.textures().forEach((path, png) -> writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            })));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "Advanced Rocketry v1.8 rocket fluid cargo devices"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
