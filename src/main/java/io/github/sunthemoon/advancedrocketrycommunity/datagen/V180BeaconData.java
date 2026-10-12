package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconStatus;
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

/**
 * ADR-065 C17c planetary beacon and beacon finder: the 5-by-3-by-3 pattern (the legacy layout with project-owned
 * casing for the LibVulpes structure block), original pixel geometry drawn here, an orientable controller model,
 * self-drop loot, community recipes from C16 interface and circuit parts, and bilingual text. No upstream, LibVulpes
 * or game art is read or copied.
 */
public final class V180BeaconData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private static final String BEACON = "planetary_beacon";
    private static final String FINDER = "beacon_finder";
    /**
     * The pattern from the top layer down; rows run from the controller's row (z 0) backwards. {@code C} controller,
     * {@code B} machine casing, {@code R} redstone block, {@code .} clearance (air or a thin decoration).
     */
    private static final String[][] LAYERS = {
            {"...", ".R.", "..."},
            {"...", ".B.", "..."},
            {"...", ".B.", "..."},
            {"...", ".B.", "..."},
            {"BCB", "BBB", "BBB"}};
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180BeaconData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(ASSETS + "blockstates/" + BEACON + ".json", json("{\"variants\":{"
                + "\"facing=north\":{\"model\":\"" + NS + ":block/" + BEACON + "\"},"
                + "\"facing=east\":{\"model\":\"" + NS + ":block/" + BEACON + "\",\"y\":90},"
                + "\"facing=south\":{\"model\":\"" + NS + ":block/" + BEACON + "\",\"y\":180},"
                + "\"facing=west\":{\"model\":\"" + NS + ":block/" + BEACON + "\",\"y\":270}}}"));
        files.put(ASSETS + "models/block/" + BEACON + ".json", json("{\"parent\":\"minecraft:block/orientable\","
                + "\"textures\":{\"top\":\"" + NS + ":block/" + BEACON + "_top\",\"front\":\"" + NS + ":block/" + BEACON
                + "_front\",\"side\":\"" + NS + ":block/" + BEACON + "_side\"}}"));
        files.put(ASSETS + "models/item/" + BEACON + ".json", json("{\"parent\":\"" + NS + ":block/" + BEACON + "\"}"));
        files.put(ASSETS + "models/item/" + FINDER + ".json", json("{\"parent\":\"minecraft:item/generated\","
                + "\"textures\":{\"layer0\":\"" + NS + ":item/" + FINDER + "\"}}"));
        return files;
    }

    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(DATA + "machine_patterns/" + BEACON + ".json", pattern());
        files.put(DATA + "loot_tables/blocks/" + BEACON + ".json", json("{\"type\":\"minecraft:block\",\"pools\":[{"
                + "\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + BEACON + "\"}],"
                + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
        files.put(DATA + "recipes/" + BEACON + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\","
                + "\"pattern\":[\"pap\",\"ubu\",\"ppp\"],\"key\":{\"p\":{\"tag\":\"forge:plates/steel\"},"
                + "\"a\":{\"item\":\"" + NS + ":advanced_circuit\"},\"u\":{\"item\":\"" + NS + ":user_interface\"},"
                + "\"b\":{\"item\":\"" + NS + ":control_board\"}},\"result\":{\"item\":\"" + NS + ":" + BEACON
                + "\",\"count\":1}}"));
        files.put(DATA + "recipes/" + FINDER + ".json", json("{\"type\":\"minecraft:crafting_shaped\","
                + "\"category\":\"equipment\",\"pattern\":[\" p \",\"ucu\",\" p \"],\"key\":{"
                + "\"p\":{\"tag\":\"forge:plates/aluminum\"},\"u\":{\"item\":\"" + NS + ":user_interface\"},"
                + "\"c\":{\"item\":\"" + NS + ":basic_circuit\"}},\"result\":{\"item\":\"" + NS + ":" + FINDER
                + "\",\"count\":1}}"));
        files.put(DATA + "advancements/recipes/misc/" + BEACON + ".json", unlock(BEACON, NS + ":control_board"));
        files.put(DATA + "advancements/recipes/equipment/" + FINDER + ".json", unlock(FINDER, NS + ":user_interface"));
        return files;
    }

    private static JsonObject unlock(String id, String item) {
        return json("{\"parent\":\"minecraft:recipes/root\",\"criteria\":{\"has_part\":{\"trigger\":"
                + "\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":[\"" + item + "\"]}]}},"
                + "\"has_the_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + NS + ":"
                + id + "\"}}},\"requirements\":[[\"has_part\",\"has_the_recipe\"]],\"rewards\":{\"recipes\":[\"" + NS
                + ":" + id + "\"]}}");
    }

    /** The 3 x 5 x 3 pattern, controller at local (1, 0, 0), all four rotations and no mirror. */
    public static JsonObject pattern() {
        JsonObject root = new JsonObject();
        root.addProperty("id", NS + ":" + BEACON);
        root.addProperty("schema_version", 1);
        root.add("size", position(3, LAYERS.length, 3));
        root.add("controller_anchor", position(1, 0, 0));
        JsonArray rotations = new JsonArray();
        for (int angle : new int[] {0, 90, 180, 270}) {
            rotations.add(angle);
        }
        root.add("allowed_rotations", rotations);
        root.addProperty("allow_mirror_local_x", false);
        JsonArray cells = new JsonArray();
        for (int layer = 0; layer < LAYERS.length; layer++) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    JsonObject cell = position(column, LAYERS.length - layer - 1, row);
                    JsonObject matcher = new JsonObject();
                    switch (LAYERS[layer][row].charAt(column)) {
                        case 'C' -> matcher.addProperty("controller", true);
                        case 'B' -> matcher.addProperty("block", NS + ":machine_casing");
                        case 'R' -> matcher.addProperty("block", "minecraft:redstone_block");
                        case '.' -> matcher.addProperty("tag", V180ArcCuttingData.CLEARANCE_TAG);
                        default -> throw new IllegalStateException("Unknown beacon pattern cell");
                    }
                    cell.add("matcher", matcher);
                    cells.add(cell);
                }
            }
        }
        root.add("cells", cells);
        return root;
    }

    private static JsonObject position(int x, int y, int z) {
        JsonObject position = new JsonObject();
        position.addProperty("x", x);
        position.addProperty("y", y);
        position.addProperty("z", z);
        return position;
    }

    public static Map<String, String> language(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("block." + NS + "." + BEACON, chinese ? "行星信标" : "Planetary Beacon");
        entries.put("item." + NS + "." + FINDER, chinese ? "信标探测器" : "Beacon Finder");
        entries.put("tooltip." + NS + "." + FINDER, chinese
                ? "手持或装入宇航服头盔时，显示你在当前星球上的已激活信标。"
                : "Shows your active beacons on this body while held or installed in a space suit helmet.");
        String hud = "hud." + NS + "." + FINDER + ".";
        entries.put(hud + "marker", chinese ? "%s · %s" : "%s · %s");
        entries.put(hud + "unnamed", chinese ? "信标" : "Beacon");
        entries.put(hud + "last_known", chinese ? "%s（最后已知）" : "%s (last known)");
        entries.put(hud + "metres", chinese ? "%s 米" : "%s m");
        entries.put(hud + "kilometres", chinese ? "%s 千米" : "%s km");
        String message = "message." + NS + ".beacon.";
        entries.put(message + "not_owner", chinese ? "这座信标属于其他玩家。" : "This beacon belongs to another player.");
        entries.put(message + "name_invalid", chinese ? "信标名称最多 %s 个字符，且不能包含控制或格式字符。"
                : "A beacon name has at most %s characters and no control or formatting characters.");
        entries.put(message + "renamed", chinese ? "信标已命名为“%s”。%s" : "Beacon renamed to \"%s\". %s");
        String[][] statuses = {
                {"Beacon active.", "信标已激活。"},
                {"Beacon switched off.", "信标已关闭。"},
                {"Beacon checking its structure.", "信标正在检查结构。"},
                {"Beacon structure incomplete: a 3 x 3 casing base with the controller at the front, a casing column of "
                        + "three and a redstone block on top, with clear space around the column.",
                        "信标结构不完整：需要以控制器位于前排中央的 3×3 机器外壳底座、三格外壳立柱和顶端的红石块，立柱周围保持空旷。"},
                {"Part of the beacon structure is not loaded.", "信标结构的一部分未加载。"},
                {"The beacon pattern is not available on this server.", "本服务器没有可用的信标结构定义。"},
                {"This beacon has no owner and cannot activate.", "这座信标没有所有者，无法激活。"},
                {"This beacon's data is unreadable and kept unchanged; ask an administrator.",
                        "这座信标的数据无法读取，已原样保留；请联系管理员。"},
                {"The beacon index is unavailable; ask an administrator.", "信标索引不可用；请联系管理员。"},
                {"Another copy of this beacon is registered elsewhere.", "这座信标的另一个副本已在别处登记。"},
                {"This position has no known celestial body; the beacon waits.", "此位置没有已知天体；信标正在等待。"},
                {"Beacon limit reached (32 per owner, 256 per body).", "信标数量已达上限（每位所有者 32 座，每个天体 256 座）。"}};
        BeaconStatus[] all = BeaconStatus.values();
        if (statuses.length != all.length) {
            throw new IllegalStateException("Every beacon status needs two strings");
        }
        for (int index = 0; index < all.length; index++) {
            entries.put(all[index].translationKey(), statuses[index][chinese ? 1 : 0]);
        }
        return Map.copyOf(entries);
    }

    /** The controller's three faces and the finder item, drawn on the 16 x 16 grid encoder. */
    public static String[] grid(String face) {
        char[][] pixels = new char[16][16];
        if (face.equals(FINDER)) {
            for (char[] row : pixels) {
                Arrays.fill(row, '.');
            }
            rect(pixels, 4, 1, 11, 14, '2');
            rect(pixels, 5, 2, 10, 13, '5');
            rect(pixels, 5, 3, 10, 8, '1');
            rect(pixels, 7, 4, 8, 4, '9');
            rect(pixels, 6, 5, 9, 5, '9');
            rect(pixels, 7, 6, 8, 7, '7');
            rect(pixels, 6, 10, 6, 11, '8');
            rect(pixels, 9, 10, 9, 11, '8');
            rect(pixels, 7, 12, 8, 12, '3');
            rect(pixels, 7, 0, 7, 0, '6');
            return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
        }
        for (char[] row : pixels) {
            Arrays.fill(row, '4');
        }
        rect(pixels, 0, 0, 15, 0, '6');
        rect(pixels, 0, 15, 15, 15, '2');
        rect(pixels, 0, 0, 0, 15, '6');
        rect(pixels, 15, 0, 15, 15, '2');
        switch (face) {
            case "front" -> {
                rect(pixels, 2, 2, 13, 12, '1');
                rect(pixels, 5, 4, 10, 4, '8');
                rect(pixels, 5, 10, 10, 10, '8');
                rect(pixels, 4, 5, 4, 9, '8');
                rect(pixels, 11, 5, 11, 9, '8');
                rect(pixels, 7, 6, 8, 8, '9');
                rect(pixels, 3, 13, 12, 13, '7');
            }
            case "side" -> {
                rect(pixels, 2, 2, 13, 13, '3');
                for (int x = 3; x <= 12; x += 3) {
                    rect(pixels, x, 3, x, 12, '6');
                }
            }
            case "top" -> {
                rect(pixels, 3, 3, 12, 12, '3');
                rect(pixels, 6, 6, 9, 9, '7');
                rect(pixels, 7, 7, 8, 8, '9');
            }
            default -> throw new IllegalArgumentException("Unknown beacon face " + face);
        }
        return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
    }

    private static void rect(char[][] pixels, int x1, int y1, int x2, int y2, char colour) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                pixels[y][x] = colour;
            }
        }
    }

    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(ASSETS + "textures/block/" + BEACON + "_front.png", V180MaterialArt.png(grid("front"), 0xB8E4FF));
        files.put(ASSETS + "textures/block/" + BEACON + "_side.png", V180MaterialArt.png(grid("side"), 0xC8D0D8));
        files.put(ASSETS + "textures/block/" + BEACON + "_top.png", V180MaterialArt.png(grid("top"), 0xFFB8B8));
        files.put(ASSETS + "textures/item/" + FINDER + ".png", V180MaterialArt.png(grid(FINDER), 0xB8E4FF));
        return files;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            textures().forEach((path, png) -> writes.add(CompletableFuture.runAsync(() -> {
                try {
                    cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png));
                } catch (IOException failed) {
                    throw new UncheckedIOException(failed);
                }
            })));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Advanced Rocketry v1.8 planetary beacon and beacon finder";
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
