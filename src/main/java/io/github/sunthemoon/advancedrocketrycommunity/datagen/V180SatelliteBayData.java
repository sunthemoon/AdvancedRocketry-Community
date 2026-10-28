package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
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
 * ADR-065 C17c (W27b) rocket satellite bay: original pixel geometry drawn here, a plain cube model, a loot table that
 * drops the bay item and the extracted package once, a community recipe, the bay's rocket tag and bilingual text. No
 * upstream, LibVulpes or game art is read or copied (the upstream bay texture is still under authorship review).
 */
public final class V180SatelliteBayData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private static final String BAY = "rocket_satellite_bay";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180SatelliteBayData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(ASSETS + "blockstates/" + BAY + ".json", json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/"
                + BAY + "\"}}}"));
        files.put(ASSETS + "models/block/" + BAY + ".json", json("{\"parent\":\"minecraft:block/cube_bottom_top\","
                + "\"textures\":{\"top\":\"" + NS + ":block/" + BAY + "_top\",\"bottom\":\"" + NS + ":block/" + BAY
                + "_bottom\",\"side\":\"" + NS + ":block/" + BAY + "_side\"}}"));
        files.put(ASSETS + "models/item/" + BAY + ".json", json("{\"parent\":\"" + NS + ":block/" + BAY + "\"}"));
        return files;
    }

    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        // No survives_explosion condition: a normal break or an explosion drops the bay and its package exactly once.
        files.put(DATA + "loot_tables/blocks/" + BAY + ".json", json("{\"type\":\"minecraft:block\",\"pools\":["
                + "{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + BAY + "\"}]},"
                + "{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:dynamic\",\"name\":\""
                + NS + ":" + BAY + "_package\"}]}]}"));
        files.put(DATA + "recipes/" + BAY + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\","
                + "\"pattern\":[\"p p\",\"pcp\",\"pbp\"],\"key\":{\"p\":{\"tag\":\"forge:plates/aluminum\"},"
                + "\"c\":{\"item\":\"minecraft:chest\"},\"b\":{\"item\":\"" + NS + ":basic_circuit\"}},"
                + "\"result\":{\"item\":\"" + NS + ":" + BAY + "\",\"count\":1}}"));
        files.put(DATA + "advancements/recipes/misc/" + BAY + ".json", json("{\"parent\":\"minecraft:recipes/root\","
                + "\"criteria\":{\"has_part\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":["
                + "{\"items\":[\"" + NS + ":basic_circuit\"]}]}},\"has_the_recipe\":{\"trigger\":"
                + "\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + NS + ":" + BAY + "\"}}},"
                + "\"requirements\":[[\"has_part\",\"has_the_recipe\"]],\"rewards\":{\"recipes\":[\"" + NS + ":" + BAY
                + "\"]}}"));
        files.put(DATA + "tags/blocks/rocket_satellite_bays.json", json("{\"replace\":false,\"values\":[\"" + NS + ":"
                + BAY + "\"]}"));
        return files;
    }

    public static Map<String, String> language(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("block." + NS + "." + BAY, chinese ? "火箭卫星舱" : "Rocket Satellite Bay");
        String screen = "screen." + NS + "." + BAY + ".";
        entries.put(screen + "phase", chinese ? "部署阶段：%s" : "Deployment phase: %s");
        String[][] phases = {
                {"NONE", "None", "无"},
                {"INTENT", "Deploying", "正在部署"},
                {"REGISTERED", "Registered", "已登记"},
                {"REJECTED", "Refused", "已拒绝"},
                {"REPAIR", "Needs offline repair", "需要离线修复"}};
        for (String[] phase : phases) {
            entries.put(SatelliteBayStatus.Phase.valueOf(phase[0]).translationKey(), phase[chinese ? 2 : 1]);
        }
        // W27d1: one line per recorded reason, in the order of SatelliteBayStatus.Reason.
        String[][] reasons = {
                {"NONE", "No refusal.", "没有拒绝原因。"},
                {"DEPLOYMENT_UNAVAILABLE", "Satellite deployment is not available in this version.", "本版本尚不能部署卫星。"},
                {"BAY_SOURCE_PROTECTED", "This rocket was filled through a resource loader, so its bays cannot deploy.",
                        "这枚火箭曾通过资源装载机加注，其卫星舱不能部署。"},
                {"BAY_RECEIPTS_UNAVAILABLE", "Deployment records cannot be written now; the package stays in the bay.",
                        "目前无法写入部署记录；包裹留在卫星舱中。"},
                {"REGISTRY_BLOCKED", "The satellite registry is blocked; ask an administrator.", "卫星登记表已被锁定；请联系管理员。"},
                {"TARGET_NOT_ALLOWED", "The satellite cannot orbit the body this rocket launched from.",
                        "这颗卫星不能环绕火箭发射所在的天体运行。"},
                {"CATALOG_UNAVAILABLE", "The celestial catalog is not loaded; the package stays in the bay.",
                        "天体目录尚未加载；包裹留在卫星舱中。"},
                {"ASSEMBLY_JOURNAL_PENDING", "A rocket assembly record of this rocket is still unresolved.",
                        "这枚火箭的组装记录尚未处理完毕。"},
                {"ASSEMBLY_JOURNAL_UNPROVED", "The rocket assembly journal could not be confirmed on disk.",
                        "无法确认火箭组装日志已写入磁盘。"},
                {"ROCKET_DEPLOYMENT_LIMIT", "A rocket deploys from at most 8 bays per flight.", "一枚火箭每次飞行最多从 8 个卫星舱部署。"},
                {"UNSUPPORTED_DATA", "The package was made by a newer version and is kept unchanged.", "包裹由更新的版本制作，已原样保留。"},
                {"PACKAGE_UNAVAILABLE", "The package item is not installed; the package is kept unchanged.", "包裹物品未安装，包裹已原样保留。"},
                {"INVALID_PACKAGE", "The bay holds an invalid package, kept unchanged.", "卫星舱中的包裹无效，已原样保留。"},
                {"BAY_OWNER_MISMATCH", "The bay, the package and the rocket must have the same owner.", "卫星舱、包裹和火箭必须属于同一所有者。"},
                {"BAY_CONFLICT", "Another deployment record names this bay or satellite.", "另一条部署记录涉及这个卫星舱或卫星。"},
                {"RECEIPT_PENDING", "An earlier deployment record of this satellite is still being written.",
                        "这颗卫星先前的部署记录仍在写入。"},
                {"DEPLOYMENT_CAPACITY", "The server's pending deployments are full; try a later flight.", "服务器的待处理部署已满；请在之后的飞行中再试。"},
                {"ROCKET_PENDING_FULL", "This rocket's pending operations are full.", "这枚火箭的待处理操作已满。"},
                {"RECEIPTS_FULL", "The server's deployment records are full; ask an administrator.", "服务器的部署记录已满；请联系管理员。"},
                {"OWNER_RECEIPTS_FULL", "Your deployment records are full; ask an administrator to retire refused ones.",
                        "你的部署记录已满；请管理员清理已拒绝的记录。"},
                {"RECEIPT_IDENTITY_CONFLICT", "The deployment record's identity conflicts with another; ask an administrator.",
                        "部署记录的身份与另一条记录冲突；请联系管理员。"},
                {"RECEIPT_WRITE_FAILED", "The deployment record could not be written; the package stays in the bay.",
                        "部署记录写入失败；包裹留在卫星舱中。"},
                {"INTERRUPTED", "The server stopped before the deployment finished; the next flight retries it.",
                        "部署完成前服务器已停止；下次飞行会重试。"},
                {"REGISTRY_SIGNATURE_CONFLICT", "A different satellite with this identity is registered; ask an administrator.",
                        "已登记了一颗身份相同但内容不同的卫星；请联系管理员。"},
                {"TRANSFER_AUTHORITY_CONFLICT", "The rocket's flight record does not match the deployment; ask an administrator.",
                        "火箭的飞行记录与部署不一致；请联系管理员。"},
                {"UNAUTHORIZED", "The package's owner did not launch this rocket.", "包裹的所有者没有发射这枚火箭。"},
                {"DEPLOYMENT_PENDING", "Another deployment of this satellite is unresolved.", "这颗卫星的另一次部署尚未完成。"},
                {"IDENTITY_CONFLICT", "A different satellite with this identity already exists.", "已存在一颗身份相同但内容不同的卫星。"},
                {"DEFINITION_NOT_FOUND", "This satellite type is not available on this server.", "这个服务器上没有这种卫星类型。"},
                {"RESEARCH_LOCKED", "This satellite needs more research before it can launch.", "这颗卫星需要更多研究才能发射。"},
                {"CAPACITY_REACHED", "The server's satellite or mission limit is reached.", "服务器的卫星或任务数量已达上限。"},
                {"OWNER_LIMIT", "Your satellite limit is reached.", "你的卫星数量已达上限。"},
                {"STORAGE_BUDGET", "The satellite registry is full.", "卫星登记表已满。"},
                {"INVALID_COMPONENTS", "The satellite's components are not valid.", "卫星的组件无效。"},
                {"LAUNCH_REFUSED", "The satellite launch was refused.", "卫星发射被拒绝。"}};
        for (String[] reason : reasons) {
            entries.put(SatelliteBayStatus.Reason.valueOf(reason[0]).translationKey(), reason[chinese ? 2 : 1]);
        }
        String message = "message." + NS + "." + BAY + ".";
        entries.put(message + "unauthorized", chinese
                ? "只有卫星舱的所有者或管理员可以在 8 格内打开它。"
                : "Only the bay's owner or an operator can open it, from within 8 blocks.");
        String[][] guards = {
                {"QUARANTINED", "This bay's data is unreadable and kept unchanged; ask an administrator.",
                        "这个卫星舱的数据无法读取，已原样保留；请联系管理员。"},
                {"UNSUPPORTED_ROOT", "This bay was saved by a newer version and is kept unchanged.",
                        "这个卫星舱由更新的版本保存，已原样保留。"},
                {"UNSUPPORTED_DATA", "This bay's package was made by a newer version and is kept unchanged.",
                        "这个卫星舱中的卫星包裹由更新的版本制作，已原样保留。"},
                {"PACKAGE_UNAVAILABLE", "This bay's package item is not installed; the package is kept unchanged.",
                        "这个卫星舱中的包裹物品未安装，包裹已原样保留。"},
                {"INVALID_PACKAGE", "This bay holds an invalid package, kept unchanged; ask an administrator.",
                        "这个卫星舱中的包裹无效，已原样保留；请联系管理员。"},
                {"CLAIMED", "A rocket assembly or disassembly holds this bay; wait for it to finish.",
                        "火箭组装或拆解正在占用这个卫星舱；请等待其完成。"}};
        for (String[] guard : guards) {
            entries.put(SatelliteBayStatus.Guard.valueOf(guard[0]).translationKey(), guard[chinese ? 2 : 1]);
        }
        entries.put("jei." + NS + "." + BAY, chinese
                ? "放入一个属于卫星舱所有者的卫星包裹。漏斗和投掷器可以放入和取出包裹；火箭组装或拆解期间不可存取。"
                        + "卫星舱随火箭飞行时包裹被锁定。本版本尚不能在轨部署卫星。"
                        + "正常破坏或爆炸时掉落卫星舱和其中的包裹各一次。"
                : "Holds one satellite package owned by the bay's owner. Hoppers and droppers can insert and extract it, "
                        + "except while a rocket assembly or disassembly holds the bay. In a flying rocket the package is "
                        + "locked. Deployment in orbit is not available in this version. Breaking or blowing up the bay "
                        + "drops the bay and its package once each.");
        return Map.copyOf(entries);
    }

    /** The bay's three faces, drawn on the 16 x 16 grid encoder. */
    public static String[] grid(String face) {
        char[][] pixels = new char[16][16];
        for (char[] row : pixels) {
            Arrays.fill(row, '4');
        }
        rect(pixels, 0, 0, 15, 0, '6');
        rect(pixels, 0, 0, 0, 15, '6');
        rect(pixels, 0, 15, 15, 15, '2');
        rect(pixels, 15, 0, 15, 15, '2');
        switch (face) {
            case "top" -> {
                // Two hatch doors over a dark recess, with handles and four corner bolts.
                rect(pixels, 2, 2, 13, 13, '1');
                rect(pixels, 3, 3, 7, 12, '5');
                rect(pixels, 8, 3, 12, 12, '5');
                rect(pixels, 7, 3, 7, 12, '2');
                rect(pixels, 8, 3, 8, 12, '7');
                rect(pixels, 5, 7, 6, 8, '8');
                rect(pixels, 9, 7, 10, 8, '8');
                for (int[] bolt : new int[][] {{1, 1}, {14, 1}, {1, 14}, {14, 14}}) {
                    pixels[bolt[1]][bolt[0]] = '9';
                }
            }
            case "side" -> {
                // A recessed panel crossed by a striped band, rivets and a small status lamp.
                rect(pixels, 2, 3, 13, 12, '3');
                for (int x = 2; x <= 13; x++) {
                    for (int y = 6; y <= 7; y++) {
                        pixels[y][x] = (x + y) % 4 < 2 ? '7' : '2';
                    }
                }
                for (int[] rivet : new int[][] {{2, 3}, {13, 3}, {2, 12}, {13, 12}}) {
                    pixels[rivet[1]][rivet[0]] = '8';
                }
                rect(pixels, 10, 9, 11, 10, '9');
            }
            case "bottom" -> {
                rect(pixels, 2, 2, 13, 13, '3');
                for (int i = 2; i <= 13; i++) {
                    pixels[i][i] = '5';
                    pixels[i][15 - i] = '5';
                }
            }
            default -> throw new IllegalArgumentException("Unknown satellite bay face " + face);
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
        files.put(ASSETS + "textures/block/" + BAY + "_top.png", V180MaterialArt.png(grid("top"), 0xE2C79A));
        files.put(ASSETS + "textures/block/" + BAY + "_side.png", V180MaterialArt.png(grid("side"), 0xC8D0D8));
        files.put(ASSETS + "textures/block/" + BAY + "_bottom.png", V180MaterialArt.png(grid("bottom"), 0xA9B3BD));
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
        return "Advanced Rocketry v1.8 rocket satellite bay";
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
