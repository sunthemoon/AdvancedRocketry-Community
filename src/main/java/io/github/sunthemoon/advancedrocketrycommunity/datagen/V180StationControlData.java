package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * ADR-065 C17b station controllers: original pixel geometry drawn here, cube models, self-drop loot, community
 * recipes from C16 interface and circuit parts, and the bilingual names, screen strings and stable station code
 * keys (section 5.3). No upstream, LibVulpes or game art is read or copied.
 */
public final class V180StationControlData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private static final String SCREEN = "screen." + NS + ".station_controller.";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180StationControlData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static List<String> ids() {
        return Arrays.stream(StationControllerKind.values()).map(StationControllerKind::id).toList();
    }

    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String id : ids()) {
            files.put(ASSETS + "blockstates/" + id + ".json",
                    json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/" + id + "\"}}}"));
            files.put(ASSETS + "models/block/" + id + ".json", json("{\"parent\":\"minecraft:block/cube_bottom_top\","
                    + "\"textures\":{\"top\":\"" + NS + ":block/station_controller_top\",\"bottom\":\"" + NS
                    + ":block/station_controller_bottom\",\"side\":\"" + NS + ":block/" + id + "_side\"}}"));
            files.put(ASSETS + "models/item/" + id + ".json", json("{\"parent\":\"" + NS + ":block/" + id + "\"}"));
        }
        return files;
    }

    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        String[] centres = {NS + ":advanced_circuit", NS + ":basic_circuit", NS + ":basic_circuit_plate",
                NS + ":advanced_circuit_plate"};
        List<String> ids = ids();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            files.put(DATA + "loot_tables/blocks/" + id + ".json", json("{\"type\":\"minecraft:block\",\"pools\":[{"
                    + "\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + id + "\"}],"
                    + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
            files.put(DATA + "recipes/" + id + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\","
                    + "\"pattern\":[\"pup\",\"cbc\",\"ppp\"],\"key\":{\"p\":{\"tag\":\"forge:plates/steel\"},"
                    + "\"u\":{\"item\":\"" + NS + ":user_interface\"},\"b\":{\"item\":\"" + NS + ":control_board\"},"
                    + "\"c\":{\"item\":\"" + centres[i] + "\"}},\"result\":{\"item\":\"" + NS + ":" + id + "\",\"count\":1}}"));
            files.put(DATA + "advancements/recipes/misc/" + id + ".json", json("{\"parent\":\"minecraft:recipes/root\","
                    + "\"criteria\":{\"has_control_board\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":"
                    + "{\"items\":[{\"items\":[\"" + NS + ":control_board\"]}]}},\"has_the_recipe\":{\"trigger\":"
                    + "\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + NS + ":" + id + "\"}}},"
                    + "\"requirements\":[[\"has_control_board\",\"has_the_recipe\"]],"
                    + "\"rewards\":{\"recipes\":[\"" + NS + ":" + id + "\"]}}"));
        }
        return files;
    }

    /** Names, screen strings and one stable key per station management code, in both languages. */
    public static Map<String, String> language(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        String[] english = {"Warp Controller", "Station Gravity Controller", "Station Altitude Controller",
                "Station Orientation Controller"};
        String[] translated = {"跃迁控制器", "空间站重力控制器", "空间站高度控制器", "空间站姿态控制器"};
        List<String> ids = ids();
        for (int i = 0; i < ids.size(); i++) {
            entries.put("block." + NS + "." + ids.get(i), chinese ? translated[i] : english[i]);
        }
        String[][] screen = {
                {"minus_ten", "-10", "-10"}, {"minus_one", "-1", "-1"}, {"plus_one", "+1", "+1"},
                {"plus_ten", "+10", "+10"}, {"geo", "GEO (179)", "地球同步 (179)"},
                {"stop", "Stop rotation", "停止旋转"}, {"phase_down", "-15°", "-15°"}, {"phase_up", "+15°", "+15°"},
                {"previous", "<", "<"}, {"next", ">", ">"}, {"request", "Quote", "报价"},
                {"confirm", "Confirm", "确认"}, {"cancel", "Cancel", "取消"},
                {"no_station", "Not in a station you can view", "不在你可查看的空间站内"},
                {"gravity", "Gravity: %s%%", "重力：%s%%"},
                {"distance", "Orbital distance: %s (4..190)", "轨道距离：%s（4..190）"},
                {"altitude", "Altitude: %s km", "高度：%s 千米"},
                {"geo_yes", "Geostationary: yes", "地球同步：是"}, {"geo_no", "Geostationary: no", "地球同步：否"},
                {"axis", "%s: rate %s, phase %s°", "%s：速率 %s，相位 %s°"},
                {"axis.east", "East", "东"}, {"axis.up", "Up", "上"}, {"axis.north", "North", "北"},
                {"orbit", "Orbit: %s", "轨道：%s"}, {"unknown", "unknown", "未知"},
                {"target", "Target: %s", "目标：%s"}, {"no_target", "No known target", "没有已知目标"},
                {"balance", "Warp energy: %s FE (pending %s FE)", "跃迁能量：%s FE（待入账 %s FE）"},
                {"cost", "Cost: %s FE, %s", "费用：%s FE，%s"},
                {"class.in_system", "in-system", "星系内"}, {"class.interstellar", "interstellar", "星际"},
                {"countdown", "Warp in %s s", "%s 秒后跃迁"}, {"status", "%s [%s]", "%s [%s]"}};
        for (String[] row : screen) {
            entries.put(SCREEN + row[0], chinese ? row[2] : row[1]);
        }
        Map<StationManagementCode, String> codes = chineseCodes();
        for (StationManagementCode code : StationManagementCode.values()) {
            String english1 = code.description();
            entries.put(code.translationKey(), chinese ? codes.get(code)
                    : english1.substring(0, 1).toUpperCase(Locale.ROOT) + english1.substring(1) + ".");
        }
        return Map.copyOf(entries);
    }

    private static Map<StationManagementCode, String> chineseCodes() {
        Map<StationManagementCode, String> codes = new LinkedHashMap<>();
        String[] values = {
                "已发出确认。", "空间站已扩建。", "空间站重力已设定。", "空间站已在使用该重力。",
                "空间站的重力或轨道刚刚更改；请等待 5 秒。", "空间站已使用最大区域。", "空间站权威数据不可用。",
                "请以已连接玩家的身份亲自执行；命令方块、函数、告示牌、控制台以及他人执行的 /execute 不能管理空间站。",
                "空间站注册表正在保存其他更改；请几秒后重试。", "请站在太空中的空间站内。", "请站在空间站当前区域内。",
                "你所在的区块未加载。", "只有站在空间站内的所有者或管理员可以管理它。", "待处理的扩建确认过多；请稍后重试。",
                "没有待处理的扩建确认；请先执行 /arce station expand。", "确认已过期；请再次执行 /arce station expand。",
                "该确认属于其他空间站或服务器会话。", "空间站在此期间已改变；请重试。", "无法保存空间站文件；空间站未改变。",
                "保存失败且结果未知；在重启前禁止更改空间站。", "已发出跃迁确认。", "跃迁倒计时已开始。", "跃迁倒计时已取消。",
                "空间站已跃迁。", "跃迁状态。", "本服务器已禁用空间站跃迁。", "请在 5 格内看向本空间站的跃迁核心。",
                "天体目录不可用。", "目标天体不存在或无法环绕。", "目标天体尚未被发现。", "空间站已在环绕该天体。",
                "空间站的跃迁能量不足以支付费用。", "空间站刚刚发生变化；请等待 5 秒。",
                "有仍可移动的火箭正在使用本空间站，或火箭状态尚未可知；请等待其着陆或离开。",
                "本空间站已有跃迁倒计时在进行。", "本空间站没有进行中的跃迁倒计时。", "待处理的跃迁过多；请稍后重试。",
                "没有待处理的跃迁确认；请先发起跃迁报价。", "确认已过期；请重新发起跃迁报价。",
                "跃迁费用或其类别已改变；请重新发起跃迁。", "确认的玩家已不再是所有者或管理员。",
                "有太空电梯绑定到本空间站或有搭乘正在进行；请先解除绑定。",
                "终局数据不可用，因此在其加载前拒绝空间站跃迁和轨道更改。", "空间站轨道距离已设定。",
                "空间站已在使用该轨道距离。", "空间站姿态已设定。", "空间站已在使用该姿态。",
                "空间站的重力或轨道刚刚更改；请等待 5 秒。", "空间站轨道时钟已耗尽；只能停止旋转。",
                "请先停止旋转再选择固定姿态。", "请站在控制器 8 格内并位于其所属空间站内。",
                "请在控制器 5 格内放置本空间站已加载的跃迁核心。", "每半秒只能进行一次控制更改；请重试。",
                "世界首次记录暂时不可用；请稍后再尝试跃迁。", "玩家过多或空间站存储空间不足，无法记录首次跃迁。"};
        StationManagementCode[] all = StationManagementCode.values();
        if (values.length != all.length) {
            throw new IllegalStateException("Every station management code needs a Chinese string");
        }
        for (int i = 0; i < all.length; i++) {
            codes.put(all[i], values[i]);
        }
        return codes;
    }

    /** A framed panel with a screen and one glyph per controller, or the shared top and bottom plates. */
    public static String[] grid(String face) {
        char[][] pixels = new char[16][16];
        for (char[] row : pixels) {
            Arrays.fill(row, '4');
        }
        rect(pixels, 0, 0, 15, 0, '6');
        rect(pixels, 0, 15, 15, 15, '2');
        rect(pixels, 0, 0, 0, 15, '6');
        rect(pixels, 15, 0, 15, 15, '2');
        if (face.equals("top")) {
            rect(pixels, 3, 3, 12, 12, '3');
            for (int x = 4; x <= 11; x += 2) {
                rect(pixels, x, 5, x, 10, '7');
            }
        } else if (face.equals("bottom")) {
            rect(pixels, 2, 2, 13, 13, '3');
            rect(pixels, 7, 7, 8, 8, '6');
        } else {
            rect(pixels, 2, 2, 13, 10, '1');
            rect(pixels, 3, 12, 5, 13, '7');
            rect(pixels, 7, 12, 8, 13, '8');
            rect(pixels, 10, 12, 12, 13, '7');
            glyph(pixels, face);
        }
        return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
    }

    private static void glyph(char[][] pixels, String id) {
        switch (id) {
            case "warp_controller" -> {
                for (int i = 0; i < 4; i++) {
                    rect(pixels, 4 + i, 3 + i, 5 + i, 3 + i, '9');
                    rect(pixels, 4 + i, 9 - i, 5 + i, 9 - i, '9');
                }
                rect(pixels, 9, 6, 11, 6, '9');
            }
            case "station_gravity_controller" -> {
                rect(pixels, 7, 3, 8, 7, '9');
                rect(pixels, 4, 6, 11, 6, '8');
                rect(pixels, 5, 7, 10, 7, '9');
                rect(pixels, 6, 8, 9, 8, '9');
                rect(pixels, 7, 9, 8, 9, '9');
            }
            case "station_altitude_controller" -> {
                rect(pixels, 7, 3, 8, 9, '9');
                rect(pixels, 6, 4, 9, 4, '9');
                rect(pixels, 5, 5, 10, 5, '9');
                for (int y = 6; y <= 9; y += 3) {
                    rect(pixels, 4, y, 5, y, '8');
                    rect(pixels, 10, y, 11, y, '8');
                }
            }
            default -> {
                rect(pixels, 6, 3, 9, 3, '9');
                rect(pixels, 6, 9, 9, 9, '9');
                rect(pixels, 4, 5, 4, 7, '9');
                rect(pixels, 11, 5, 11, 7, '9');
                rect(pixels, 5, 4, 5, 4, '9');
                rect(pixels, 10, 4, 10, 4, '9');
                rect(pixels, 5, 8, 5, 8, '9');
                rect(pixels, 10, 8, 10, 8, '9');
                rect(pixels, 7, 5, 8, 7, '8');
            }
        }
    }

    private static void rect(char[][] pixels, int x1, int y1, int x2, int y2, char color) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                pixels[y][x] = color;
            }
        }
    }

    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(ASSETS + "textures/block/station_controller_top.png", V180MaterialArt.png(grid("top"), 0xC8D0D8));
        files.put(ASSETS + "textures/block/station_controller_bottom.png",
                V180MaterialArt.png(grid("bottom"), 0xB0B8C0));
        int[] tints = {0xC4A8FF, 0xA8CCFF, 0xA8FFCC, 0xFFDCA8};
        List<String> ids = ids();
        for (int i = 0; i < ids.size(); i++) {
            files.put(ASSETS + "textures/block/" + ids.get(i) + "_side.png",
                    V180MaterialArt.png(grid(ids.get(i)), tints[i]));
        }
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
        return "v1.8 station controller original resources";
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
