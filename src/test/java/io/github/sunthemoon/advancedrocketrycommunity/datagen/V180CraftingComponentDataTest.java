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

class V180CraftingComponentDataTest {
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String DATA = "data/advancedrocketrycommunity/";
    private static final Set<String> CLIENT = Set.of(ASSETS + "models/item/carbon_brick.json",
            ASSETS + "models/item/user_interface.json");
    private static final Set<String> SERVER = Set.of(DATA + "recipes/carbon_brick.json",
            DATA + "recipes/user_interface.json", DATA + "advancements/recipes/misc/carbon_brick.json",
            DATA + "advancements/recipes/misc/user_interface.json", "data/forge/tags/items/ingots/carbon.json");
    private static final Set<String> PNG = Set.of(ASSETS + "textures/item/carbon_brick.png",
            ASSETS + "textures/item/user_interface.png");

    @Test void stableIdsAndExactlyNineScopedPathsHaveCompleteItemModelReferences() {
        assertEquals("advancedrocketrycommunity:carbon_brick", V180CraftingComponentData.CARBON_ID);
        assertEquals("advancedrocketrycommunity:user_interface", V180CraftingComponentData.UI_ID);
        assertEquals(ASSETS + "textures/item/carbon_brick.png", V180CraftingComponentData.CARBON_TEXTURE);
        assertEquals(ASSETS + "textures/item/user_interface.png", V180CraftingComponentData.UI_TEXTURE);
        var client = V180CraftingComponentData.clientFiles();
        assertEquals(CLIENT, client.keySet());
        assertEquals(SERVER, V180CraftingComponentData.serverFiles().keySet());
        for (String name : List.of("carbon_brick", "user_interface")) {
            assertEquals(json("""
                    {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/%s"}}
                    """.formatted(name)), client.get(ASSETS + "models/item/" + name + ".json"));
            assertTrue(PNG.contains(ASSETS + "textures/item/" + name + ".png"));
        }
        Set<String> all = new HashSet<>(CLIENT);
        all.addAll(SERVER);
        all.addAll(PNG);
        assertEquals(9, all.size());
        all.forEach(path -> assertTrue(!path.contains("..") && !Path.of(path).isAbsolute()
                && path.equals(path.toLowerCase(java.util.Locale.ROOT))));
    }

    @Test void carbonUsesSixSeparateCharcoalIngredientsForExactlyOneOutput() {
        assertEquals(json("""
                {"type":"minecraft:crafting_shapeless","category":"misc","group":"",
                 "ingredients":[{"item":"minecraft:charcoal"},{"item":"minecraft:charcoal"},
                  {"item":"minecraft:charcoal"},{"item":"minecraft:charcoal"},
                  {"item":"minecraft:charcoal"},{"item":"minecraft:charcoal"}],
                 "result":{"item":"advancedrocketrycommunity:carbon_brick","count":1}}
                """), V180CraftingComponentData.serverFiles().get(DATA + "recipes/carbon_brick.json"));
    }

    @Test void uiPreservesTheTwoRowShapedSelectorsAndSingleOutput() {
        assertEquals(json("""
                {"type":"minecraft:crafting_shaped","category":"misc","group":"","pattern":["lrl","fgf"],
                 "key":{"l":{"tag":"forge:dyes/lime"},"r":{"tag":"forge:dusts/redstone"},
                  "f":{"tag":"forge:dusts/glowstone"},"g":{"item":"minecraft:glass_pane"}},
                 "result":{"item":"advancedrocketrycommunity:user_interface","count":1}}
                """), V180CraftingComponentData.serverFiles().get(DATA + "recipes/user_interface.json"));
    }

    @Test void carbonTagAddsOnlyThisMaterialWithoutReplacingOtherMembers() {
        assertEquals(json("""
                {"replace":false,"values":["advancedrocketrycommunity:carbon_brick"]}
                """), V180CraftingComponentData.serverFiles().get("data/forge/tags/items/ingots/carbon.json"));
    }

    @Test void ingredientOrRecipeUnlocksRewardOnlyTheirOwnRecipe() {
        var files = V180CraftingComponentData.serverFiles();
        assertEquals(json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_charcoal":{"trigger":"minecraft:inventory_changed","conditions":{
                  "items":[{"items":["minecraft:charcoal"],"count":{"min":1}}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                  "recipe":"advancedrocketrycommunity:carbon_brick"}}},
                 "requirements":[["has_charcoal","has_the_recipe"]],
                 "rewards":{"recipes":["advancedrocketrycommunity:carbon_brick"]}}
                """), files.get(DATA + "advancements/recipes/misc/carbon_brick.json"));
        assertEquals(json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_glowstone_dust":{"trigger":"minecraft:inventory_changed","conditions":{
                  "items":[{"items":["minecraft:glowstone_dust"],"count":{"min":1}}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                  "recipe":"advancedrocketrycommunity:user_interface"}}},
                 "requirements":[["has_glowstone_dust","has_the_recipe"]],
                 "rewards":{"recipes":["advancedrocketrycommunity:user_interface"]}}
                """), files.get(DATA + "advancements/recipes/misc/user_interface.json"));
    }

    @Test void auditedRecipeFactIdentitiesRemainSeparateFromNewBitmapAuthoring() {
        assertEquals("Advanced-Rocketry/AdvancedRocketry", V180CraftingComponentData.SOURCE_REPOSITORY);
        assertEquals("c5cd5af62fc07cd4e0d24f06a16033f181c47c04", V180CraftingComponentData.SOURCE_COMMIT);
        assertEquals("src/main/resources/assets/advancedrocketry/recipes/charcoalbrick.json",
                V180CraftingComponentData.CARBON_SOURCE_PATH);
        assertEquals(586, V180CraftingComponentData.CARBON_SOURCE_BYTES);
        assertEquals("28ec6e6ce7aaf732dc166765795844a56aefdaa85acb409670fbf1b693bb62d2",
                V180CraftingComponentData.CARBON_SOURCE_SHA256);
        assertEquals("src/main/resources/assets/advancedrocketry/recipes/userinterface.json",
                V180CraftingComponentData.UI_SOURCE_PATH);
        assertEquals(580, V180CraftingComponentData.UI_SOURCE_BYTES);
        assertEquals("dc07f355dc95072ba3a5d57fde2948a7dd6209ca69dd3277c30a3c6beb64e599",
                V180CraftingComponentData.UI_SOURCE_SHA256);
    }

    @Test void bilingualAdditionsAreExactImmutableMapsForTheSoleLanguageWriter() {
        assertEquals(Map.of("item.advancedrocketrycommunity.carbon_brick", "Carbon Brick",
                "item.advancedrocketrycommunity.user_interface", "User Interface"),
                V180CraftingComponentData.language(false));
        assertEquals(Map.of("item.advancedrocketrycommunity.carbon_brick", "碳砖",
                "item.advancedrocketrycommunity.user_interface", "用户界面"),
                V180CraftingComponentData.language(true));
        for (boolean chinese : List.of(false, true)) {
            assertThrows(UnsupportedOperationException.class,
                    () -> V180CraftingComponentData.language(chinese).put("changed", "changed"));
            assertThrows(UnsupportedOperationException.class, () -> V180CraftingComponentData.language(chinese).clear());
        }
    }

    @Test void outputJsonGridsAndPngArraysAreFreshIndependentAndBounded() {
        for (boolean client : List.of(false, true)) {
            var original = client ? V180CraftingComponentData.clientFiles() : V180CraftingComponentData.serverFiles();
            var expected = new HashMap<String, JsonObject>();
            original.forEach((path, value) -> {
                assertTrue(value.toString().getBytes(StandardCharsets.UTF_8).length <= 16384);
                expected.put(path, value.deepCopy());
                value.entrySet().clear();
                value.addProperty("changed", true);
            });
            assertEquals(expected, client ? V180CraftingComponentData.clientFiles() : V180CraftingComponentData.serverFiles());
        }
        String[] carbon = V180CraftingComponentData.carbonGrid();
        String[] ui = V180CraftingComponentData.uiGrid();
        assertNotSame(carbon, V180CraftingComponentData.carbonGrid());
        assertNotSame(ui, V180CraftingComponentData.uiGrid());
        assertFalse(Arrays.equals(carbon, ui));
        for (String[] grid : List.of(carbon, ui)) {
            assertEquals(16, grid.length);
            for (String row : grid) { assertTrue(row.matches("[0-9.]{16}")); }
        }
        String carbonControl = carbon[3], uiControl = ui[3];
        carbon[3] = "................";
        ui[3] = "................";
        assertEquals(carbonControl, V180CraftingComponentData.carbonGrid()[3]);
        assertEquals(uiControl, V180CraftingComponentData.uiGrid()[3]);
        byte[] carbonPng = V180CraftingComponentData.carbonTexture();
        byte[] uiPng = V180CraftingComponentData.uiTexture();
        byte[] expectedCarbon = carbonPng.clone(), expectedUi = uiPng.clone();
        assertNotSame(carbonPng, V180CraftingComponentData.carbonTexture());
        assertNotSame(uiPng, V180CraftingComponentData.uiTexture());
        assertFalse(Arrays.equals(carbonPng, uiPng));
        Arrays.fill(carbonPng, (byte) 0);
        Arrays.fill(uiPng, (byte) 0);
        assertArrayEquals(expectedCarbon, V180CraftingComponentData.carbonTexture());
        assertArrayEquals(expectedUi, V180CraftingComponentData.uiTexture());
    }

    @Test void carbonPngHasValidDeterministic16By16RgbaChunksAndAllExpectedPixels() throws Exception {
        byte[] png = V180CraftingComponentData.carbonTexture();
        assertArrayEquals(png, V180CraftingComponentData.carbonTexture());
        assertPng(png, V180CraftingComponentData.carbonGrid(), 0x747D88);
    }

    @Test void uiPngHasValidDeterministic16By16RgbaChunksAndAllExpectedPixels() throws Exception {
        byte[] png = V180CraftingComponentData.uiTexture();
        assertArrayEquals(png, V180CraftingComponentData.uiTexture());
        assertPng(png, V180CraftingComponentData.uiGrid(), 0x8ECBC0);
    }

    @Test void actualProviderFlagsWriteExactlyTheirOwnedPartitionAndRepeatExactBytes(@TempDir Path directory)
            throws Exception {
        for (boolean client : List.of(false, true)) {
            for (boolean server : List.of(false, true)) {
                Path output = directory.resolve(client + "-" + server);
                var provider = new V180CraftingComponentData(new PackOutput(output), client, server);
                assertEquals("v1.8 ordinary crafting components original resources", provider.getName());
                provider.run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
                Set<String> expected = new HashSet<>();
                if (client) { expected.addAll(CLIENT); expected.addAll(PNG); }
                if (server) { expected.addAll(SERVER); }
                assertEquals(expected, emittedPaths(output));
                var before = new HashMap<String, byte[]>();
                for (String path : expected) {
                    byte[] raw = Files.readAllBytes(output.resolve(path));
                    before.put(path, raw);
                    if (path.endsWith(".json")) {
                        assertTrue(raw.length <= 16384);
                        JsonObject actual = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                        assertEquals(CLIENT.contains(path) ? V180CraftingComponentData.clientFiles().get(path)
                                : V180CraftingComponentData.serverFiles().get(path), actual);
                    } else {
                        assertArrayEquals(path.equals(V180CraftingComponentData.CARBON_TEXTURE)
                                ? V180CraftingComponentData.carbonTexture() : V180CraftingComponentData.uiTexture(), raw);
                    }
                }
                provider.run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
                assertEquals(expected, emittedPaths(output));
                for (String path : expected) { assertArrayEquals(before.get(path), Files.readAllBytes(output.resolve(path))); }
            }
        }
    }

    private static Set<String> emittedPaths(Path output) throws Exception {
        Set<String> paths = new HashSet<>();
        if (Files.exists(output)) {
            try (var files = Files.walk(output)) {
                files.filter(Files::isRegularFile).forEach(path ->
                        paths.add(output.relativize(path).toString().replace('\\', '/')));
            }
        }
        return paths;
    }

    private static void assertPng(byte[] png, String[] grid, int tint) throws Exception {
        assertTrue(png.length <= 4096);
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, Arrays.copyOf(png, 8));
        ByteBuffer bytes = ByteBuffer.wrap(png);
        bytes.position(8);
        List<String> chunks = new ArrayList<>();
        byte[] compressed = null;
        while (bytes.hasRemaining()) {
            assertTrue(bytes.remaining() >= 12);
            int length = bytes.getInt();
            assertTrue(length >= 0 && length <= 4096 && length <= bytes.remaining() - 8);
            byte[] kind = new byte[4]; bytes.get(kind);
            byte[] body = new byte[length]; bytes.get(body);
            CRC32 crc = new CRC32(); crc.update(kind); crc.update(body);
            assertEquals(crc.getValue(), Integer.toUnsignedLong(bytes.getInt()));
            String type = new String(kind, StandardCharsets.US_ASCII);
            chunks.add(type);
            if (type.equals("IHDR")) {
                assertArrayEquals(ByteBuffer.allocate(13).putInt(16).putInt(16).put((byte) 8)
                        .put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0).array(), body);
            } else if (type.equals("IDAT")) { compressed = body; }
            else if (type.equals("IEND")) { assertEquals(0, body.length); }
        }
        assertEquals(List.of("IHDR", "IDAT", "IEND"), chunks);
        assertNotNull(compressed);
        byte[] pixels;
        try (var inflated = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            pixels = inflated.readNBytes(1041);
            assertEquals(1040, pixels.length);
            assertEquals(-1, inflated.read());
        }
        int[] greys = {20, 44, 72, 93, 117, 141, 169, 191, 210, 238};
        int opaque = 0;
        for (int y = 0; y < 16; y++) {
            assertEquals(0, pixels[y * 65]);
            for (int x = 0; x < 16; x++) {
                char cell = grid[y].charAt(x);
                int start = y * 65 + x * 4 + 1;
                if (cell == '.') {
                    for (int channel = 0; channel < 4; channel++) { assertEquals(0, pixels[start + channel]); }
                } else {
                    opaque++;
                    int grey = greys[cell - '0'];
                    assertEquals(grey * ((tint >> 16) & 255) / 255, Byte.toUnsignedInt(pixels[start]));
                    assertEquals(grey * ((tint >> 8) & 255) / 255, Byte.toUnsignedInt(pixels[start + 1]));
                    assertEquals(grey * (tint & 255) / 255, Byte.toUnsignedInt(pixels[start + 2]));
                    assertEquals(255, Byte.toUnsignedInt(pixels[start + 3]));
                }
            }
        }
        assertTrue(opaque > 0 && opaque < 256);
        // Fixed controls are independent of a mutable grid result: cube facet and panel connector/display.
        if (tint == 0x747D88) {
            assertEquals(76, Byte.toUnsignedInt(pixels[3 * 65 + 5 * 4 + 1]));
            assertEquals(82, Byte.toUnsignedInt(pixels[3 * 65 + 5 * 4 + 2]));
            assertEquals(90, Byte.toUnsignedInt(pixels[3 * 65 + 5 * 4 + 3]));
        } else {
            assertEquals(94, Byte.toUnsignedInt(pixels[1 * 65 + 5 * 4 + 1]));
            assertEquals(132, Byte.toUnsignedInt(pixels[5 * 65 + 4 * 4 + 1]));
            assertEquals(189, Byte.toUnsignedInt(pixels[5 * 65 + 4 * 4 + 2]));
            assertEquals(179, Byte.toUnsignedInt(pixels[5 * 65 + 4 * 4 + 3]));
        }
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
