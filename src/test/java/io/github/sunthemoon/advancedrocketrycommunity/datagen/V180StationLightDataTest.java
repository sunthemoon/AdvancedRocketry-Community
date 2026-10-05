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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V180StationLightDataTest {
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String DATA = "data/advancedrocketrycommunity/";
    private static final Set<String> CLIENT = Set.of(ASSETS + "blockstates/station_light.json",
            ASSETS + "models/block/station_light.json", ASSETS + "models/item/station_light.json");
    private static final Set<String> SERVER = Set.of(DATA + "recipes/station_light.json",
            DATA + "loot_tables/blocks/station_light.json",
            DATA + "advancements/recipes/building_blocks/station_light.json");

    @Test void literalIdentityAndModelReferencesHaveExactlySevenOwnedPaths() {
        assertEquals("advancedrocketrycommunity:station_light", V180StationLightData.ID);
        assertEquals(ASSETS + "textures/block/station_light.png", V180StationLightData.TEXTURE);
        var client = V180StationLightData.clientFiles();
        assertEquals(CLIENT, client.keySet());
        assertEquals(SERVER, V180StationLightData.serverFiles().keySet());
        assertEquals(json("""
                {"variants":{"":{"model":"advancedrocketrycommunity:block/station_light"}}}
                """), client.get(ASSETS + "blockstates/station_light.json"));
        assertEquals(json("""
                {"parent":"minecraft:block/cube_all","textures":{"all":"advancedrocketrycommunity:block/station_light"}}
                """), client.get(ASSETS + "models/block/station_light.json"));
        assertEquals(json("""
                {"parent":"advancedrocketrycommunity:block/station_light"}
                """), client.get(ASSETS + "models/item/station_light.json"));
        var paths = new HashSet<>(CLIENT);
        paths.addAll(SERVER);
        paths.add(V180StationLightData.TEXTURE);
        assertEquals(7, paths.size());
        paths.forEach(path -> assertTrue(!path.contains("..") && !Path.of(path).isAbsolute()));
    }

    @Test void acquisitionUsesOnlyExactVanillaItemsAndFourUntaggedOutputs() {
        assertEquals(json("""
                {"type":"minecraft:crafting_shaped","category":"building","pattern":["IGI","GLG","IGI"],
                 "key":{"I":{"item":"minecraft:iron_ingot"},"G":{"item":"minecraft:glass"},
                        "L":{"item":"minecraft:glowstone"}},
                 "result":{"item":"advancedrocketrycommunity:station_light","count":4}}
                """), V180StationLightData.serverFiles().get(DATA + "recipes/station_light.json"));
    }

    @Test void vanillaRecipeBookUnlockIsExactOrWithOnlyRecipeReward() {
        assertEquals(json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_glowstone":{"trigger":"minecraft:inventory_changed","conditions":{
                  "items":[{"items":["minecraft:glowstone"],"count":{"min":1}}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                  "recipe":"advancedrocketrycommunity:station_light"}}},
                 "requirements":[["has_glowstone","has_the_recipe"]],
                 "rewards":{"recipes":["advancedrocketrycommunity:station_light"]}}
                """), V180StationLightData.serverFiles().get(DATA
                        + "advancements/recipes/building_blocks/station_light.json"));
    }

    @Test void selfLootHasOneEntryAndOnlyNativeExplosionDecay() {
        assertEquals(json("""
                {"type":"minecraft:block","pools":[{"rolls":1.0,"bonus_rolls":0.0,
                 "entries":[{"type":"minecraft:item","name":"advancedrocketrycommunity:station_light"}],
                 "functions":[{"function":"minecraft:explosion_decay"}]}]}
                """), V180StationLightData.serverFiles().get(DATA + "loot_tables/blocks/station_light.json"));
    }

    @Test void jsonGridAndTextureResultsAreFreshAndAllJsonIsBounded() {
        for (var original : List.of(V180StationLightData.clientFiles(), V180StationLightData.serverFiles())) {
            var expected = new java.util.HashMap<String, JsonObject>();
            original.forEach((path, value) -> {
                assertTrue(value.toString().getBytes(StandardCharsets.UTF_8).length <= 16384);
                expected.put(path, value.deepCopy());
                value.addProperty("changed", true);
                value.entrySet().removeIf(entry -> !entry.getKey().equals("changed"));
            });
            var fresh = original.keySet().equals(CLIENT)
                    ? V180StationLightData.clientFiles() : V180StationLightData.serverFiles();
            assertEquals(expected, fresh);
        }
        String[] grid = V180StationLightData.grid();
        assertEquals(16, grid.length);
        for (String row : grid) { assertTrue(row.matches("[0-9]{16}")); }
        String first = grid[0];
        grid[0] = "................";
        assertEquals(first, V180StationLightData.grid()[0]);
        byte[] png = V180StationLightData.texture();
        byte[] expected = png.clone();
        Arrays.fill(png, (byte) 0);
        assertArrayEquals(expected, V180StationLightData.texture());
    }

    @Test void bilingualNamesAreSingletonImmutableMapsWithoutCompetingWriters() {
        String key = "block.advancedrocketrycommunity.station_light";
        assertEquals(java.util.Map.of(key, "Station Light"), V180StationLightLanguage.translations(false));
        assertEquals(java.util.Map.of(key, "空间站灯"), V180StationLightLanguage.translations(true));
        assertThrows(UnsupportedOperationException.class,
                () -> V180StationLightLanguage.translations(false).put(key, "changed"));
        assertThrows(UnsupportedOperationException.class,
                () -> V180StationLightLanguage.translations(true).clear());
    }

    @Test void originalPngHasExactChunksCrcRgbaOpaquePixelsAndDeterminism() throws Exception {
        byte[] png = V180StationLightData.texture();
        assertTrue(png.length <= 4096);
        assertArrayEquals(png, V180StationLightData.texture());
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'},
                Arrays.copyOf(png, 8));
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
        for (int y = 0; y < 16; y++) {
            assertEquals(0, pixels[y * 65]);
            for (int x = 0; x < 16; x++) { assertEquals(255, Byte.toUnsignedInt(pixels[y * 65 + x * 4 + 4])); }
        }
        // Independent fixed pixel controls for the new tint and pale centre.
        assertEquals(128, Byte.toUnsignedInt(pixels[1]));
        int centre = 8 * 65 + 8 * 4 + 1;
        assertEquals(216, Byte.toUnsignedInt(pixels[centre]));
        assertEquals(221, Byte.toUnsignedInt(pixels[centre + 1]));
        assertEquals(225, Byte.toUnsignedInt(pixels[centre + 2]));
    }

    @Test void providerFlagsWriteOnlyTheirOwnedPartitionAndRepeatExactBytes(@TempDir Path directory) throws Exception {
        for (boolean client : List.of(false, true)) {
            for (boolean server : List.of(false, true)) {
                Path output = directory.resolve(client + "-" + server);
                var provider = new V180StationLightData(new PackOutput(output), client, server);
                provider.run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
                Set<String> expected = new HashSet<>();
                if (client) { expected.addAll(CLIENT); expected.add(V180StationLightData.TEXTURE); }
                if (server) { expected.addAll(SERVER); }
                Set<String> actual = new HashSet<>();
                if (Files.exists(output)) {
                    try (var paths = Files.walk(output)) {
                        paths.filter(Files::isRegularFile).forEach(path ->
                                actual.add(output.relativize(path).toString().replace('\\', '/')));
                    }
                }
                assertEquals(expected, actual);
                var before = new java.util.HashMap<String, byte[]>();
                for (String path : actual) {
                    byte[] raw = Files.readAllBytes(output.resolve(path));
                    before.put(path, raw);
                    if (path.endsWith(".json")) { assertTrue(raw.length <= 16384); }
                    else { assertArrayEquals(V180StationLightData.texture(), raw); }
                }
                provider.run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
                for (String path : actual) { assertArrayEquals(before.get(path), Files.readAllBytes(output.resolve(path))); }
            }
        }
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
