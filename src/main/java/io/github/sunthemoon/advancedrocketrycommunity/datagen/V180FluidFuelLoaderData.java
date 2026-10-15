package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem;
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
 * W82 (W11b3 leaves B3-A and B3-D): the fluid fuel loader and the linker. Original pixel geometry drawn here, a plain
 * cube model, a loot table that drops one loader item (the carrier is added by the block), two new community recipes
 * with the empty-tank ingredient, their unlock advancements and bilingual text. No upstream, LibVulpes or game art is
 * read or copied; see {@code docs/provenance/v1.8.0-c17a-fluid-fuel-loader-preauthoring.md}.
 */
public final class V180FluidFuelLoaderData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    public static final String LOADER = "fluid_fuel_loader";
    public static final String LINKER = "linker";
    public static final String EMPTY_TANK = NS + ":empty_pressurized_tank";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180FluidFuelLoaderData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(ASSETS + "blockstates/" + LOADER + ".json", json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/"
                + LOADER + "\"}}}"));
        files.put(ASSETS + "models/block/" + LOADER + ".json", json("{\"parent\":\"minecraft:block/cube_bottom_top\","
                + "\"textures\":{\"top\":\"" + NS + ":block/" + LOADER + "_top\",\"bottom\":\"" + NS + ":block/" + LOADER
                + "_bottom\",\"side\":\"" + NS + ":block/" + LOADER + "_side\"}}"));
        files.put(ASSETS + "models/item/" + LOADER + ".json", json("{\"parent\":\"" + NS + ":block/" + LOADER + "\"}"));
        files.put(ASSETS + "models/item/" + LINKER + ".json", json("{\"parent\":\"minecraft:item/generated\","
                + "\"textures\":{\"layer0\":\"" + NS + ":item/" + LINKER + "\"}}"));
        return files;
    }

    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        // No survives_explosion condition: a break or an explosion of an unprotected loader drops one carrier.
        files.put(DATA + "loot_tables/blocks/" + LOADER + ".json", json("{\"type\":\"minecraft:block\",\"pools\":["
                + "{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + LOADER + "\"}]}]}"));
        files.put(DATA + "recipes/" + LOADER + ".json", json("{\"type\":\"minecraft:crafting_shapeless\","
                + "\"category\":\"misc\",\"ingredients\":[{\"type\":\"" + EMPTY_TANK + "\"},"
                + "{\"item\":\"" + NS + ":liquid_io_board\"},{\"item\":\"" + NS + ":control_board\"},"
                + "{\"item\":\"" + NS + ":machine_casing\"}],\"result\":{\"item\":\"" + NS + ":" + LOADER + "\"}}"));
        files.put(DATA + "recipes/" + LINKER + ".json", json("{\"type\":\"minecraft:crafting_shapeless\","
                + "\"category\":\"misc\",\"ingredients\":[{\"item\":\"" + NS + ":tracking_circuit\"},"
                + "{\"item\":\"minecraft:iron_ingot\"},{\"item\":\"minecraft:redstone\"}],"
                + "\"result\":{\"item\":\"" + NS + ":" + LINKER + "\"}}"));
        files.put(DATA + "advancements/recipes/misc/" + LOADER + ".json", unlock(LOADER, NS + ":liquid_io_board"));
        files.put(DATA + "advancements/recipes/misc/" + LINKER + ".json", unlock(LINKER, NS + ":tracking_circuit"));
        return files;
    }

    private static JsonObject unlock(String recipe, String part) {
        return json("{\"parent\":\"minecraft:recipes/root\",\"criteria\":{\"has_part\":{\"trigger\":"
                + "\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":[\"" + part + "\"]}]}},"
                + "\"has_the_recipe\":{\"trigger\":\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + NS + ":"
                + recipe + "\"}}},\"requirements\":[[\"has_part\",\"has_the_recipe\"]],\"rewards\":{\"recipes\":[\"" + NS
                + ":" + recipe + "\"]}}");
    }

    public static Map<String, String> language(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("block." + NS + "." + LOADER, chinese ? "流体燃料装载机" : "Fluid Fuel Loader");
        entries.put("item." + NS + "." + LINKER, chinese ? "链接器" : "Linker");
        String[][] statuses = {
                {"ADMISSION_DISABLED", "Rocket filling is not enabled in this version; no fuel moved.",
                        "本版本尚未启用火箭加注；未移动燃料。"},
                {"ESCROW_FULL", "Escrow capacity full; no fuel moved.", "托管容量已满；未移动燃料。"},
                {"LEDGER_UNAVAILABLE", "The escrow ledger is unavailable; no fuel moved.", "托管账本不可用；未移动燃料。"},
                {"TRANSFER_JOURNAL_UNPROVED", "The rocket's transfer record is not confirmed yet; no fuel moved.",
                        "火箭的转移记录尚未确认；未移动燃料。"},
                {"ESCROW_PENDING", "Resources are locked until escrow confirms them durably.",
                        "资源已锁定，等待托管的持久确认。"},
                {"UNBOUND", "The loader is not bound; use a linker on the loader, then on its rocket.",
                        "装载器尚未绑定；请先用链接器点击装载器，再点击它的火箭。"},
                {"STALE_BINDING", "Binding is stale; bind the loader again.", "绑定已失效；请重新绑定装载器。"},
                {"WAITING_UNLOADED", "The bound rocket is in an unloaded area; waiting.", "绑定的火箭位于未加载区域；正在等待。"},
                {"OUT_OF_RANGE", "The rocket is more than 6 blocks from the loader.", "火箭距离装载器超过 6 格。"},
                {"NOT_OWNER", "Only the loader's owner can do this.", "只有装载器的所有者可以这样做。"},
                {"ROCKET_BUSY", "The rocket is moving, occupied or ridden; no fuel moved.",
                        "火箭正在移动、有乘客或被骑乘；未移动燃料。"},
                {"EMPTY", "The loader's tank is empty.", "装载器的储罐是空的。"},
                {"BANK_FULL", "The rocket's fuel bank for this fluid is full.", "火箭中对应这种流体的燃料舱已满。"},
                {"INCOMPATIBLE_FLUID", "The rocket cannot take this fluid.", "火箭无法使用这种流体。"},
                {"AMBIGUOUS_BANK", "More than one fuel bank could take this fluid; no fuel moved.",
                        "有多个燃料舱可以接收这种流体；未移动燃料。"},
                {"UNSUPPORTED_DATA", "This loader's data is from a newer version or names a missing fluid; it is kept unchanged.",
                        "这个装载器的数据来自更新的版本或引用了缺失的流体；已原样保留。"},
                {"INVALID_DATA", "This loader's data is invalid or duplicated and is kept unchanged; ask an administrator.",
                        "这个装载器的数据无效或重复，已原样保留；请联系管理员。"},
                {"READY", "Ready to fill.", "可以加注。"},
                {"PREPARING", "Preparing escrow; no fuel has moved yet.", "正在准备托管；尚未移动燃料。"},
                {"TRANSFERRING", "Transferring fuel.", "正在输送燃料。"}};
        for (String[] status : statuses) {
            entries.put(FluidFuelLoaderStatus.valueOf(status[0]).translationKey(), status[chinese ? 2 : 1]);
        }
        entries.put(FluidFuelLoaderItem.EXCHANGE_REFUSED, chinese
                ? "无法与装载器交换完整的 1,000 mB：请检查流体、其数据、容量和物品栏空间。"
                : "Cannot exchange a full 1,000 mB with the loader: check the fluid, its data, the space and your inventory.");
        entries.put(FluidFuelLoaderItem.CARRIER_REFUSED, chinese
                ? "这个装载器物品的数据不受支持；请联系管理员。" : "This loader item's data is unsupported; ask an administrator.");
        entries.put(FluidFuelLoaderItem.TOOLTIP_CONTENTS, chinese ? "%s：%s mB" : "%s: %s mB");
        entries.put(FluidFuelLoaderItem.TOOLTIP_EMPTY, chinese ? "储罐为空" : "Empty tank");
        entries.put(LinkerItem.SELECTED, chinese ? "已选择装载器；现在用链接器点击它的火箭。"
                : "Loader selected; now use the linker on its rocket.");
        entries.put(LinkerItem.CLEARED, chinese ? "已清除链接器的选择。" : "Linker selection cleared.");
        entries.put(LinkerItem.BOUND, chinese ? "装载器已绑定到这枚火箭。" : "Loader bound to this rocket.");
        entries.put(LinkerItem.UNBOUND, chinese ? "已清除装载器的绑定。" : "Loader binding cleared.");
        entries.put(LinkerItem.NO_SELECTION, chinese ? "请先用链接器选择一个装载器。" : "Select a loader with the linker first.");
        entries.put(LinkerItem.ALREADY_BOUND, chinese ? "装载器已经绑定到这枚火箭。" : "The loader is already bound to this rocket.");
        entries.put(LinkerItem.NOT_BOUND, chinese ? "装载器没有可以清除的绑定。" : "The loader has no binding to clear.");
        entries.put(LinkerItem.TOO_FAR, chinese ? "请站在装载器和火箭的 8 格以内。"
                : "Stand within 8 blocks of the loader and the rocket.");
        entries.put(LinkerItem.TOOLTIP_SELECTION, chinese ? "已选择位于 %s, %s, %s 的装载器" : "Selected loader at %s, %s, %s");
        entries.put(LinkerItem.TOOLTIP_NONE, chinese ? "未选择装载器" : "No loader selected");
        entries.put(LinkerItem.TOOLTIP_INVALID, chinese ? "选择数据无法读取；潜行时对空使用即可清除。"
                : "Unreadable selection; sneak-use in the air to clear it.");
        String screen = "screen." + NS + "." + LOADER + ".";
        entries.put(screen + "contents", chinese ? "%s：%s / %s mB" : "%s: %s / %s mB");
        entries.put(screen + "empty", chinese ? "空：0 / %s mB" : "Empty: 0 / %s mB");
        entries.put(screen + "unknown_fluid", chinese ? "未知流体" : "Unknown fluid");
        entries.put(screen + "bound", chinese ? "已绑定火箭" : "Bound to a rocket");
        entries.put(screen + "unbound", chinese ? "未绑定" : "Not bound");
        entries.put(screen + "status_unknown", chinese ? "未知状态" : "Unknown status");
        return Map.copyOf(entries);
    }

    /** The loader's three faces and the linker icon, drawn on the 16 x 16 grid encoder. */
    public static String[] grid(String face) {
        char[][] pixels = new char[16][16];
        for (char[] row : pixels) {
            Arrays.fill(row, face.equals(LINKER) ? '.' : '4');
        }
        switch (face) {
            case "side" -> {
                edges(pixels);
                // A sight glass on the left with a liquid line and graduation ticks beside it.
                rect(pixels, 2, 2, 5, 13, '1');
                rect(pixels, 3, 3, 4, 7, '7');
                rect(pixels, 3, 8, 4, 12, '5');
                pixels[8][3] = '9';
                pixels[8][4] = '9';
                for (int y : new int[] {3, 6, 9, 12}) {
                    pixels[y][6] = '8';
                }
                // Two hoops across the shell, riveted, and a feed pipe leaving at the lower right.
                rect(pixels, 8, 4, 14, 4, '3');
                rect(pixels, 8, 11, 14, 11, '3');
                pixels[4][9] = '8';
                pixels[4][13] = '8';
                pixels[11][9] = '8';
                pixels[11][13] = '8';
                rect(pixels, 9, 6, 12, 8, '5');
                rect(pixels, 10, 7, 11, 7, '9');
                rect(pixels, 11, 13, 14, 14, '2');
                rect(pixels, 12, 13, 13, 13, '6');
            }
            case "top" -> {
                edges(pixels);
                // An off-centre coupling flange with a valve wheel, four bolts and a fill-cap hinge.
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        double distance = Math.hypot(x - 6.5D, y - 7.5D);
                        if (distance <= 5.4D && distance > 4.4D) {
                            pixels[y][x] = '2';
                        } else if (distance <= 4.4D && distance > 1.6D) {
                            pixels[y][x] = '5';
                        } else if (distance <= 1.6D) {
                            pixels[y][x] = '1';
                        }
                    }
                }
                rect(pixels, 3, 7, 10, 8, '8');
                rect(pixels, 6, 4, 7, 11, '7');
                for (int[] bolt : new int[][] {{3, 3}, {10, 3}, {3, 12}, {10, 12}}) {
                    pixels[bolt[1]][bolt[0]] = '9';
                }
                rect(pixels, 13, 2, 14, 5, '6');
                rect(pixels, 13, 10, 14, 13, '3');
            }
            case "bottom" -> {
                edges(pixels);
                rect(pixels, 1, 1, 14, 14, '3');
                rect(pixels, 2, 3, 13, 4, '1');
                rect(pixels, 2, 11, 13, 12, '1');
                rect(pixels, 9, 6, 11, 8, '0');
                pixels[7][10] = '5';
                pixels[13][3] = '7';
            }
            case LINKER -> {
                // A handheld unit: antenna, a screen with a link mark, keys and a trailing coupling cable.
                rect(pixels, 10, 1, 10, 3, '9');
                pixels[1][11] = '9';
                rect(pixels, 4, 4, 10, 4, '8');
                rect(pixels, 3, 5, 11, 11, '7');
                rect(pixels, 3, 5, 3, 11, '8');
                rect(pixels, 11, 5, 11, 11, '8');
                rect(pixels, 4, 12, 10, 12, '8');
                rect(pixels, 4, 6, 9, 9, '1');
                rect(pixels, 5, 7, 6, 7, '5');
                rect(pixels, 7, 8, 8, 8, '5');
                pixels[10][4] = '3';
                pixels[10][6] = '3';
                pixels[10][8] = '3';
                rect(pixels, 6, 13, 7, 13, '2');
                rect(pixels, 7, 14, 8, 14, '2');
                pixels[15][9] = '2';
            }
            default -> throw new IllegalArgumentException("Unknown fluid fuel loader face " + face);
        }
        return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
    }

    private static void edges(char[][] pixels) {
        rect(pixels, 0, 0, 15, 0, '6');
        rect(pixels, 0, 0, 0, 15, '6');
        rect(pixels, 0, 15, 15, 15, '2');
        rect(pixels, 15, 0, 15, 15, '2');
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
        files.put(ASSETS + "textures/block/" + LOADER + "_side.png", V180MaterialArt.png(grid("side"), 0xB9C8D4));
        files.put(ASSETS + "textures/block/" + LOADER + "_top.png", V180MaterialArt.png(grid("top"), 0xD9C08E));
        files.put(ASSETS + "textures/block/" + LOADER + "_bottom.png", V180MaterialArt.png(grid("bottom"), 0x9AA5AF));
        files.put(ASSETS + "textures/item/" + LINKER + ".png", V180MaterialArt.png(grid(LINKER), 0x8FC6E6));
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
        return "Advanced Rocketry v1.8 fluid fuel loader and linker";
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
