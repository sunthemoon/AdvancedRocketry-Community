package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** NEW pixel geometry and device resources, with no external bitmap inputs. */
public final class V180LifeSupportData implements DataProvider {
    public static final List<String> BLOCKS = List.of("co2_scrubber", "pipe_seal", "atmosphere_detector");
    public static final String CARTRIDGE = "carbon_scrubber_cartridge";
    private static final String NS = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180LifeSupportData(PackOutput output, boolean client, boolean server) { this.output = output; this.client = client; this.server = server; }
    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String id : BLOCKS) {
            files.put(ASSETS + "blockstates/" + id + ".json", json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/" + id + "\"}}}"));
            files.put(ASSETS + "models/block/" + id + ".json", json("{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"" + NS + ":block/" + id + "\"}}"));
            files.put(ASSETS + "models/item/" + id + ".json", json("{\"parent\":\"" + NS + ":block/" + id + "\"}"));
        }
        files.put(ASSETS + "models/item/" + CARTRIDGE + ".json", json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + NS + ":item/" + CARTRIDGE + "\"}}"));
        return files;
    }
    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String id : BLOCKS) {
            files.put(DATA + "loot_tables/blocks/" + id + ".json", json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + id + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
        }
        recipe(files, "co2_scrubber", "ppp", "ici", "ppp", "forge:plates/steel", NS + ":machine_casing", "forge:ingots/iron");
        recipe(files, "pipe_seal", " p ", "pcp", " p ", "forge:plates/steel", "minecraft:glass", null);
        recipe(files, "atmosphere_detector", "ppp", "ici", "ppp", "forge:plates/aluminum", NS + ":basic_circuit_plate", "forge:ingots/iron");
        // Creates a NEW cartridge, never consumes an old cartridge or erases its data.
        recipe(files, CARTRIDGE, " p ", "pcp", " p ", "forge:plates/iron", NS + ":carbon_brick", null);
        return files;
    }
    private static void recipe(Map<String, JsonObject> files, String id, String a, String b, String c, String plate, String center, String iron) {
        String keys = "\"p\":{\"tag\":\"" + plate + "\"},\"c\":{\"item\":\"" + center + "\"}";
        if (iron != null) { keys += ",\"i\":{\"tag\":\"" + iron + "\"}"; }
        files.put(DATA + "recipes/" + id + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"pattern\":[\"" + a + "\",\"" + b + "\",\"" + c + "\"],\"key\":{" + keys + "},\"result\":{\"item\":\"" + NS + ":" + id + "\",\"count\":1}}"));
        files.put(DATA + "advancements/recipes/misc/" + id + ".json", json("{\"parent\":\"minecraft:recipes/root\",\"criteria\":{\"has_plate\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"tag\":\"" + plate + "\"}]}},\"has_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + NS + ":" + id + "\"}}},\"requirements\":[[\"has_plate\",\"has_recipe\"]],\"rewards\":{\"recipes\":[\"" + NS + ":" + id + "\"]}}"));
    }
    public static Map<String, String> language(boolean zh) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("block." + NS + ".co2_scrubber", zh ? "二氧化碳洗涤器" : "CO2 Scrubber");
        names.put("block." + NS + ".pipe_seal", zh ? "管线密封器" : "Pipe Seal");
        names.put("block." + NS + ".atmosphere_detector", zh ? "大气探测器" : "Atmosphere Detector");
        names.put("item." + NS + "." + CARTRIDGE, zh ? "碳洗涤滤芯" : "Carbon Scrubber Cartridge");
        names.put("message." + NS + ".detector.mode", zh ? "探测模式：%s（潜行右键切换）" : "Detector: %s (sneak-use to cycle)");
        names.put("message." + NS + ".vent.scrubber_cartridge_recovered", zh ? "已取回失去关联的洗涤滤芯。"
                : "Recovered an orphaned scrubber cartridge.");
        names.put("message." + NS + ".vent.scrubber_association_released", zh ? "已解除一个过期的洗涤器关联；它没有滤芯，未移动任何物品。"
                : "Released a stale scrubber association; it held no cartridge, so no item moved.");
        names.put("message." + NS + ".vent.scrubber_recovery_refused", zh ? "没有可取回的内容：未发现失去关联的滤芯或过期关联。未加载或不可读的洗涤器保持不变。"
                : "Nothing to recover: no orphaned cartridge or stale association was found. Unloaded or unreadable scrubbers are left unchanged.");
        String[] ids = {"breathable", "vacuum", "non_breathable_atmosphere", "high_pressure", "hot", "cold"};
        String[] en = {"Breathable", "Vacuum", "Non-breathable atmosphere", "High pressure", "Hot", "Cold"};
        String[] cn = {"可呼吸", "真空", "不可呼吸的大气", "高压", "高温", "低温"};
        for (int i = 0; i < ids.length; i++) { names.put("mode." + NS + ".detector." + ids[i], zh ? cn[i] : en[i]); }
        return names;
    }
    public static String[] grid(String id) {
        char[][] pixels = new char[16][16];
        for (char[] row : pixels) { Arrays.fill(row, id.equals(CARTRIDGE) ? '.' : '3'); }
        if (id.equals(CARTRIDGE)) {
            rect(pixels, 4, 2, 11, 13, '8'); rect(pixels, 5, 4, 10, 11, '1');
            for (int y = 5; y < 11; y += 2) { rect(pixels, 6, y, 9, y, '5'); }
            rect(pixels, 6, 1, 9, 2, '9'); rect(pixels, 6, 13, 9, 14, '5');
        } else {
            rect(pixels, 1, 1, 14, 14, '8'); rect(pixels, 3, 3, 12, 12, '1');
            if (id.equals("co2_scrubber")) {
                for (int y = 4; y <= 10; y += 2) { rect(pixels, 4, y, 11, y, '5'); }
                rect(pixels, 5, 13, 7, 13, '9');
            } else if (id.equals("pipe_seal")) {
                rect(pixels, 5, 5, 10, 10, '3'); rect(pixels, 6, 6, 9, 9, '5');
                rect(pixels, 7, 1, 8, 4, '9'); rect(pixels, 7, 11, 8, 14, '9');
                rect(pixels, 1, 7, 4, 8, '9'); rect(pixels, 11, 7, 14, 8, '9');
            } else {
                rect(pixels, 4, 4, 11, 9, '5'); rect(pixels, 5, 5, 10, 8, '3');
                rect(pixels, 5, 7, 6, 7, '9'); rect(pixels, 7, 5, 8, 7, '9'); rect(pixels, 9, 6, 10, 6, '9');
                rect(pixels, 5, 11, 6, 12, '5'); rect(pixels, 9, 11, 10, 12, '9');
            }
        }
        return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
    }
    private static void rect(char[][] p, int x1, int y1, int x2, int y2, char color) {
        for (int y = y1; y <= y2; y++) { for (int x = x1; x <= x2; x++) { p[y][x] = color; } }
    }
    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String id : BLOCKS) { files.put(ASSETS + "textures/block/" + id + ".png", V180MaterialArt.png(grid(id), id.equals("co2_scrubber") ? 0xB2CDAA : id.equals("pipe_seal") ? 0xCBB88E : 0x84C7D6)); }
        files.put(ASSETS + "textures/item/" + CARTRIDGE + ".png", V180MaterialArt.png(grid(CARTRIDGE), 0xAAB9A1)); return files;
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            textures().forEach((path, png) -> writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            })));
        }
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 life-support devices original resources"; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
