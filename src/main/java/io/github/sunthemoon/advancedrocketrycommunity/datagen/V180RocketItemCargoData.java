package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * W120 item cargo resources (D-D's resource part). MIT original code/text; see the pre-authoring provenance
 * declaration. The shared cargo status messages are W109's and are not repeated here.
 */
public final class V180RocketItemCargoData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180RocketItemCargoData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (RocketCargoRole role : RocketCargoRole.values()) {
            String id = RocketItemCargoBlock.id(role), base = "assets/" + NS + "/";
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
            String id = RocketItemCargoBlock.id(role), base = "data/" + NS + "/";
            // The plain device item; the block spills the buffer itself, and a protected device suppresses all loot.
            files.put(base + "loot_tables/blocks/" + id + ".json", json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,"
                    + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + id + "\"}]}]}"));
            String pattern = role == RocketCargoRole.LOAD ? "[\" H \",\"IMC\"]" : "[\"IMC\",\" H \"]";
            files.put(base + "recipes/" + id + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\","
                    + "\"pattern\":" + pattern + ",\"key\":{\"H\":{\"item\":\"minecraft:hopper\"},\"I\":{\"item\":\""
                    + NS + ":item_io_board\"},\"M\":{\"item\":\"" + NS + ":machine_casing\"},\"C\":{\"item\":\""
                    + NS + ":control_board\"}},\"result\":{\"item\":\"" + NS + ":" + id + "\"}}"));
            files.put(base + "advancements/recipes/misc/" + id + ".json", json("{\"parent\":\"minecraft:recipes/root\","
                    + "\"criteria\":{\"has_board\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":[\""
                    + NS + ":item_io_board\"]}]}},\"has_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\""
                    + NS + ":" + id + "\"}}},\"requirements\":[[\"has_board\",\"has_recipe\"]],\"rewards\":{\"recipes\":[\""
                    + NS + ":" + id + "\"]}}"));
        }
        return files;
    }

    public static Map<String, String> language(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("block." + NS + ".rocket_item_loader", chinese ? "火箭物品装载器" : "Rocket Item Loader");
        entries.put("block." + NS + ".rocket_item_unloader", chinese ? "火箭物品卸载器" : "Rocket Item Unloader");
        entries.put(RocketItemCargoItem.BLOCK_DATA_REFUSED, chinese ? "此设备物品带有方块数据，无法放置。"
                : "This device item carries block data and cannot be placed.");
        String screen = "screen." + NS + ".rocket_item_cargo.";
        entries.put(screen + "bound", chinese ? "已绑定火箭" : "Bound to a rocket");
        entries.put(screen + "unbound", chinese ? "未绑定" : "Not bound");
        entries.put(screen + "status_unknown", chinese ? "未知状态" : "Unknown status");
        entries.put(screen + "cursor", chinese ? "储存位置：%s, %s, %s，槽位 %s" : "Storage: %s, %s, %s, slot %s");
        return Map.copyOf(entries);
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            V180RocketItemCargoArt.textures().forEach((path, png) -> writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            })));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "Advanced Rocketry v1.8 rocket item cargo devices"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
