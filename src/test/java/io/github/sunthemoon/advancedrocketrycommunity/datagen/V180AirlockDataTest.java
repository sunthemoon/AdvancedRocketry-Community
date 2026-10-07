package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V180AirlockDataTest {
    private static final String A = "assets/advancedrocketrycommunity/";
    private static final String D = "data/advancedrocketrycommunity/";

    @Test void allThirtyTwoVariantsReferenceExactlyEightNativeTemplateModels() {
        var files = V180AirlockData.clientFiles();
        assertEquals(10, files.size());
        var variants = files.get(A + "blockstates/airlock_door.json").getAsJsonObject("variants");
        assertEquals(32, variants.size());
        Set<String> used = new HashSet<>();
        String[] directions = {"east", "south", "west", "north"};
        for (String half : List.of("lower", "upper")) {
            for (String hinge : List.of("left", "right")) {
                for (boolean open : new boolean[] {false, true}) {
                    String name = "airlock_door_" + (half.equals("lower") ? "bottom" : "top") + "_" + hinge + (open ? "_open" : "");
                    used.add(name);
                    JsonObject model = files.get(A + "models/block/" + name + ".json");
                    assertEquals("minecraft:block/" + name.substring("airlock_".length()), model.get("parent").getAsString());
                    assertEquals(json("{\"bottom\":\"advancedrocketrycommunity:block/airlock_door_bottom\",\"top\":\"advancedrocketrycommunity:block/airlock_door_top\"}"), model.getAsJsonObject("textures"));
                    for (int i = 0; i < directions.length; i++) {
                        String key = "facing=" + directions[i] + ",half=" + half + ",hinge=" + hinge + ",open=" + open;
                        JsonObject variant = variants.getAsJsonObject(key);
                        assertEquals("advancedrocketrycommunity:block/" + name, variant.get("model").getAsString());
                        assertEquals(Math.floorMod(i * 90 + (open ? (hinge.equals("left") ? 90 : -90) : 0), 360),
                                variant.has("y") ? variant.get("y").getAsInt() : 0);
                        assertFalse(key.contains("powered"));
                    }
                }
            }
        }
        assertEquals(8, used.size());
        assertEquals(json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"advancedrocketrycommunity:item/airlock_door\"}}"),
                files.get(A + "models/item/airlock_door.json"));
    }

    @Test void acquisitionUnlockAndLowerHalfExplosionLootAreLiteral() {
        var files = V180AirlockData.serverFiles();
        assertEquals(Set.of(D + "recipes/airlock_door.json", D + "loot_tables/blocks/airlock_door.json",
                D + "advancements/recipes/redstone/airlock_door.json"), files.keySet());
        assertEquals(json("""
                {"type":"minecraft:crafting_shaped","category":"redstone","pattern":["II","II","II"],
                 "key":{"I":{"item":"minecraft:iron_ingot"}},"result":{"item":"advancedrocketrycommunity:airlock_door","count":3}}
                """), files.get(D + "recipes/airlock_door.json"));
        assertEquals(json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_iron":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["minecraft:iron_ingot"]}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"advancedrocketrycommunity:airlock_door"}}},
                 "requirements":[["has_iron","has_the_recipe"]],"rewards":{"recipes":["advancedrocketrycommunity:airlock_door"]}}
                """), files.get(D + "advancements/recipes/redstone/airlock_door.json"));
        assertEquals(json("""
                {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item",
                 "name":"advancedrocketrycommunity:airlock_door","conditions":[{"condition":"minecraft:block_state_property",
                 "block":"advancedrocketrycommunity:airlock_door","properties":{"half":"lower"}}],
                 "functions":[{"function":"minecraft:explosion_decay"}]}]}]}
                """), files.get(D + "loot_tables/blocks/airlock_door.json"));
    }

    @Test void bilingualNamesAreImmutableAndJsonAndGridsAreFresh() {
        assertEquals(Map.of("block.advancedrocketrycommunity.airlock_door", "Airlock Door"), V180AirlockData.language(false));
        assertEquals(Map.of("block.advancedrocketrycommunity.airlock_door", "气闸门"), V180AirlockData.language(true));
        assertThrows(UnsupportedOperationException.class, () -> V180AirlockData.language(false).clear());
        for (boolean client : new boolean[] {false, true}) {
            var first = client ? V180AirlockData.clientFiles() : V180AirlockData.serverFiles();
            var expected = new HashMap<String, JsonObject>(); first.forEach((path, value) -> expected.put(path, value.deepCopy()));
            first.values().forEach(value -> value.addProperty("changed", true));
            assertEquals(expected, client ? V180AirlockData.clientFiles() : V180AirlockData.serverFiles());
        }
        String[] grid = V180AirlockData.itemGrid(); String row = grid[1]; grid[1] = "................";
        assertEquals(row, V180AirlockData.itemGrid()[1]);
        var textures = V180AirlockData.textures(); byte[] before = textures.get(V180AirlockData.ITEM_TEXTURE).clone();
        Arrays.fill(textures.get(V180AirlockData.ITEM_TEXTURE), (byte) 0);
        assertArrayEquals(before, V180AirlockData.textures().get(V180AirlockData.ITEM_TEXTURE));
    }

    @Test void threeOriginalGridsHaveExactRgbaPixelsAndPngCrc() throws Exception {
        var grids = Map.of(V180AirlockData.BOTTOM_TEXTURE, V180AirlockData.bottomGrid(),
                V180AirlockData.TOP_TEXTURE, V180AirlockData.topGrid(), V180AirlockData.ITEM_TEXTURE, V180AirlockData.itemGrid());
        for (var entry : grids.entrySet()) {
            assertEquals(16, entry.getValue().length);
            for (String row : entry.getValue()) { assertTrue(row.matches("[.0-9]{16}")); }
            byte[] png = V180AirlockData.textures().get(entry.getKey());
            assertTrue(png.length <= 4096);
            assertArrayEquals(png, V180AirlockData.textures().get(entry.getKey()));
            ByteBuffer buffer = ByteBuffer.wrap(png);
            assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, Arrays.copyOf(png, 8));
            buffer.position(8); List<String> kinds = new ArrayList<>(); byte[] compressed = null;
            while (buffer.hasRemaining()) {
                int size = buffer.getInt(); assertTrue(size >= 0 && size <= 4096 && size <= buffer.remaining() - 8);
                byte[] kind = new byte[4]; buffer.get(kind); byte[] body = new byte[size]; buffer.get(body);
                CRC32 crc = new CRC32(); crc.update(kind); crc.update(body);
                assertEquals(crc.getValue(), Integer.toUnsignedLong(buffer.getInt()));
                String name = new String(kind, StandardCharsets.US_ASCII); kinds.add(name);
                if (name.equals("IHDR")) { assertArrayEquals(ByteBuffer.allocate(13).putInt(16).putInt(16)
                        .put((byte) 8).put((byte) 6).put(new byte[3]).array(), body); }
                if (name.equals("IDAT")) { compressed = body; }
                if (name.equals("IEND")) { assertEquals(0, size); }
            }
            assertEquals(List.of("IHDR", "IDAT", "IEND"), kinds); assertNotNull(compressed);
            try (var inflater = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
                byte[] pixels = inflater.readNBytes(1041); assertEquals(1040, pixels.length); assertEquals(-1, inflater.read());
                int[] grey = {20, 44, 72, 93, 117, 141, 169, 191, 210, 238};
                for (int y = 0; y < 16; y++) {
                    assertEquals(0, pixels[y * 65]);
                    for (int x = 0; x < 16; x++) {
                        char value = entry.getValue()[y].charAt(x); int at = y * 65 + x * 4 + 1;
                        for (int channel = 0; channel < 3; channel++) {
                            int tint = (V180AirlockData.TINT >> (16 - channel * 8)) & 255;
                            assertEquals(value == '.' ? 0 : grey[value - '0'] * tint / 255, Byte.toUnsignedInt(pixels[at + channel]));
                        }
                        assertEquals(value == '.' ? 0 : 255, Byte.toUnsignedInt(pixels[at + 3]));
                    }
                }
            }
        }
    }

    @Test void allFourProviderModesOwnOnlySixteenBoundedRepeatablePaths(@TempDir Path directory) throws Exception {
        for (boolean client : new boolean[] {false, true}) {
            for (boolean server : new boolean[] {false, true}) {
                Path root = directory.resolve(client + "-" + server);
                var provider = new V180AirlockData(new PackOutput(root), client, server);
                provider.run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
                Set<String> expected = new HashSet<>();
                if (client) { expected.addAll(V180AirlockData.clientFiles().keySet()); expected.addAll(V180AirlockData.textures().keySet()); }
                if (server) { expected.addAll(V180AirlockData.serverFiles().keySet()); }
                Set<String> actual = new HashSet<>();
                if (Files.exists(root)) { try (var paths = Files.walk(root)) {
                    paths.filter(Files::isRegularFile).forEach(path -> actual.add(root.relativize(path).toString().replace('\\', '/')));
                } }
                assertEquals(expected, actual); if (client && server) { assertEquals(16, actual.size()); }
                Map<String, byte[]> before = new HashMap<>();
                for (String path : actual) {
                    assertFalse(Path.of(path).isAbsolute()); assertFalse(path.contains(".."));
                    byte[] raw = Files.readAllBytes(root.resolve(path)); before.put(path, raw);
                    assertTrue(raw.length <= 16384);
                    if (path.endsWith(".png")) { assertArrayEquals(V180AirlockData.textures().get(path), raw); }
                }
                provider.run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
                for (String path : actual) { assertArrayEquals(before.get(path), Files.readAllBytes(root.resolve(path))); }
            }
        }
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
