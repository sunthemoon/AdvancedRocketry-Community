package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
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

/** Original pixel geometry and community recipes; no copied upstream or game art. */
public final class V180EquipmentData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180EquipmentData(PackOutput output, boolean client, boolean server) { this.output = output; this.client = client; this.server = server; }
    public static List<String> moduleIds() {
        return Arrays.stream(ModuleKind.values()).filter(kind -> kind != ModuleKind.BEACON_FINDER)
                .flatMap(kind -> kind.itemPaths().stream()).toList();
    }
    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String id : moduleIds()) {
            files.put(ASSETS + "models/item/" + id + ".json", json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + NS + ":item/" + id + "\"}}"));
        }
        files.put(ASSETS + "blockstates/suit_workstation.json", json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/suit_workstation\"}}}"));
        files.put(ASSETS + "models/item/suit_workstation.json", json("{\"parent\":\"" + NS + ":block/suit_workstation\"}"));
        files.put(ASSETS + "models/block/suit_workstation.json", json("{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{"
                + "\"top\":\"" + NS + ":block/suit_workstation_top\",\"bottom\":\"" + NS + ":block/suit_workstation_bottom\","
                + "\"side\":\"" + NS + ":block/suit_workstation_side\"}}"));
        return files;
    }
    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(DATA + "loot_tables/blocks/suit_workstation.json", json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,"
                + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":suit_workstation\"}],"
                + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
        recipe(files, "suit_workstation", "ppp", "ici", "i i", "forge:plates/steel", NS + ":user_interface", "forge:ingots/iron");
        String[] metals = {"tin", "aluminum", "steel", "titanium"};
        List<String> tanks = ModuleKind.PRESSURE_TANK.itemPaths();
        for (int i = 0; i < tanks.size(); i++) {
            // Each tier is crafted independently: a gas-bearing old tank is never consumed into an empty upgrade.
            recipe(files, tanks.get(i), " p ", "pcp", " p ", "forge:plates/" + metals[i], "minecraft:glass_bottle", "forge:ingots/iron");
        }
        recipe(files, "jetpack", "pip", "pcp", "i i", "forge:plates/steel", NS + ":basic_circuit_plate", "forge:rods/titanium");
        recipe(files, "hover_upgrade", " p ", "ici", " p ", "forge:plates/aluminum", "minecraft:feather", "forge:ingots/iron");
        recipe(files, "flight_speed_upgrade", " p ", "ici", " p ", "forge:plates/aluminum", "minecraft:redstone", "forge:ingots/iron");
        recipe(files, "bionic_leg_upgrade", " p ", "ici", " p ", "forge:plates/steel", NS + ":basic_circuit_plate", "forge:ingots/iron");
        recipe(files, "padded_landing_boots", " p ", "ici", " p ", "forge:plates/tin", "minecraft:white_wool", "forge:ingots/iron");
        recipe(files, "anti_fog_visor", " p ", "ici", " p ", "forge:plates/aluminum", "minecraft:glass", "forge:ingots/iron");
        recipe(files, "earthbright_visor", " p ", "ici", " p ", "forge:plates/titanium", "minecraft:glowstone", "forge:ingots/iron");
        return files;
    }
    private static void recipe(Map<String, JsonObject> files, String id, String a, String b, String c, String plate, String center, String iron) {
        String key = "\"p\":{\"tag\":\"" + plate + "\"},\"c\":{\"item\":\"" + center + "\"}";
        if ((a + b + c).contains("i")) { key += ",\"i\":{\"tag\":\"" + iron + "\"}"; }
        files.put(DATA + "recipes/" + id + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"equipment\","
                + "\"pattern\":[\"" + a + "\",\"" + b + "\",\"" + c + "\"],\"key\":{" + key + "},"
                + "\"result\":{\"item\":\"" + NS + ":" + id + "\",\"count\":1}}"));
        files.put(DATA + "advancements/recipes/equipment/" + id + ".json", json("{\"parent\":\"minecraft:recipes/root\","
                + "\"criteria\":{\"has_plate\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"tag\":\"" + plate + "\"}]}},"
                + "\"has_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + NS + ":" + id + "\"}}},"
                + "\"requirements\":[[\"has_plate\",\"has_recipe\"]],\"rewards\":{\"recipes\":[\"" + NS + ":" + id + "\"]}}"));
    }
    public static Map<String, String> language(boolean chinese) {
        Map<String, String> names = new LinkedHashMap<>();
        String[] en = {"Low Pressure Tank", "Pressure Tank", "High Pressure Tank", "Super High Pressure Tank", "Jetpack",
                "Hover Upgrade", "Flight Speed Upgrade", "Bionic Leg Upgrade", "Padded Landing Boots", "Anti-fog Visor", "Earthbright Visor"};
        String[] zh = {"低压罐", "压力罐", "高压罐", "超高压罐", "喷气背包", "悬停升级", "飞行速度升级", "仿生腿升级", "缓冲着陆靴", "防雾面罩", "地照面罩"};
        List<String> ids = moduleIds();
        for (int i = 0; i < ids.size(); i++) { names.put("item." + NS + "." + ids.get(i), chinese ? zh[i] : en[i]); }
        names.put("block." + NS + ".suit_workstation", chinese ? "宇航服工作站" : "Suit Workstation");
        names.put("enchantment." + NS + ".space_breathing", chinese ? "太空呼吸" : "Space Breathing");
        names.put("screen." + NS + ".suit.install", chinese ? "安装" : "Install");
        names.put("screen." + NS + ".suit.remove_hint", chinese ? "−：取出；Shift：移入背包" : "-: remove; Shift: move to inventory");
        return Map.copyOf(names);
    }
    /** Distinct silhouettes for tanks, harness, arrows, brace, sole and visors, all drawn here. */
    public static String[] grid(String id) {
        char[][] pixels = new char[16][16];
        for (char[] row : pixels) { Arrays.fill(row, id.startsWith("suit_workstation") ? '3' : '.'); }
        if (id.startsWith("suit_workstation")) {
            rect(pixels, 1, 1, 14, 3, '8'); rect(pixels, 1, 12, 14, 14, '8');
            for (int x = 2; x < 14; x += 3) { rect(pixels, x, 5, x + 1, 10, id.endsWith("top") ? '9' : '1'); }
        } else if (id.endsWith("pressure_tank")) {
            rect(pixels, 6, 1, 9, 2, '8'); rect(pixels, 4, 3, 11, 13, '7');
            rect(pixels, 5, 4, 6, 12, '9'); rect(pixels, 4, 14, 11, 14, '3');
            int bands = ModuleKind.PRESSURE_TANK.itemPaths().indexOf(id) + 1;
            for (int i = 0; i < bands; i++) { rect(pixels, 7, 4 + 2 * i, 11, 4 + 2 * i, '2'); }
        } else if (id.equals("jetpack")) {
            rect(pixels, 2, 3, 5, 12, '8'); rect(pixels, 10, 3, 13, 12, '8');
            rect(pixels, 6, 4, 9, 10, '4'); rect(pixels, 3, 13, 4, 14, '2'); rect(pixels, 11, 13, 12, 14, '2');
        } else if (id.endsWith("visor")) {
            rect(pixels, 1, 4, 14, 10, '8'); rect(pixels, 2, 5, 13, 9, '3');
            rect(pixels, 3, 5, 5, 6, '9'); rect(pixels, 6, 11, 9, 12, '8');
        } else if (id.equals("padded_landing_boots")) {
            rect(pixels, 3, 2, 7, 11, '8'); rect(pixels, 4, 9, 12, 12, '6'); rect(pixels, 2, 13, 13, 14, '3');
        } else if (id.equals("bionic_leg_upgrade")) {
            rect(pixels, 5, 1, 9, 5, '8'); rect(pixels, 6, 6, 10, 9, '4'); rect(pixels, 8, 10, 11, 14, '8');
            rect(pixels, 4, 5, 11, 6, '9');
        } else {
            rect(pixels, 2, 12, 13, 14, '5');
            for (int y = 2; y <= 10; y++) { int half = Math.min(4, (y - 2) / 2); rect(pixels, 7 - half, y, 8 + half, y, '8'); }
            if (id.equals("hover_upgrade")) { rect(pixels, 3, 2, 12, 3, '3'); rect(pixels, 6, 7, 9, 9, '.'); }
        }
        return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
    }
    private static void rect(char[][] pixels, int x1, int y1, int x2, int y2, char color) {
        for (int y = y1; y <= y2; y++) { for (int x = x1; x <= x2; x++) { pixels[y][x] = color; } }
    }
    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String id : moduleIds()) {
            int tint = id.contains("visor") ? (id.startsWith("earth") ? 0xF4D885 : 0x8CD8E2)
                    : id.contains("pressure") ? 0xB5D0D2 : 0xDCA567;
            files.put(ASSETS + "textures/item/" + id + ".png", V180MaterialArt.png(grid(id), tint));
        }
        for (String face : List.of("top", "side", "bottom")) {
            files.put(ASSETS + "textures/block/suit_workstation_" + face + ".png",
                    V180MaterialArt.png(grid("suit_workstation_" + face), 0xB8CFBD));
        }
        return files;
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
    @Override public String getName() { return "v1.8 suit equipment original resources"; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
