package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V180CircuitPlateDataTest {
    private static final String DATA = "data/advancedrocketrycommunity/recipes/";
    private static final String BASIC_MODEL = "assets/advancedrocketrycommunity/models/item/basic_circuit_plate.json";
    private static final String ADVANCED_MODEL = "assets/advancedrocketrycommunity/models/item/advanced_circuit_plate.json";
    private static final Set<String> SERVER = Set.of(DATA + "precision_basic_circuit_plate.json",
            DATA + "precision_advanced_circuit_plate.json");

    @Test void stableIdsAndExactlySixScopedResourcesWithoutCraftingAdvancements() {
        assertEquals("advancedrocketrycommunity:basic_circuit_plate", V180CircuitPlateData.BASIC_ID);
        assertEquals("advancedrocketrycommunity:advanced_circuit_plate", V180CircuitPlateData.ADVANCED_ID);
        assertEquals("advancedrocketrycommunity:precision_basic_circuit_plate", V180CircuitPlateData.BASIC_PRECISION_ID);
        assertEquals("advancedrocketrycommunity:precision_advanced_circuit_plate", V180CircuitPlateData.ADVANCED_PRECISION_ID);
        assertEquals(Set.of(BASIC_MODEL, ADVANCED_MODEL), V180CircuitPlateData.clientFiles().keySet());
        assertEquals(SERVER, V180CircuitPlateData.serverFiles().keySet());
        assertEquals("assets/advancedrocketrycommunity/textures/item/basic_circuit_plate.png", V180CircuitPlateData.BASIC_TEXTURE);
        assertEquals("assets/advancedrocketrycommunity/textures/item/advanced_circuit_plate.png", V180CircuitPlateData.ADVANCED_TEXTURE);
        assertEquals(json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/basic_circuit_plate"}}
                """), V180CircuitPlateData.clientFiles().get(BASIC_MODEL));
        assertEquals(json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/advanced_circuit_plate"}}
                """), V180CircuitPlateData.clientFiles().get(ADVANCED_MODEL));
    }

    @Test void basicPrecisionPreservesAllOrderedSelectorsAndNinetyThousandFE() {
        JsonObject actual = V180CircuitPlateData.serverFiles().get(DATA + "precision_basic_circuit_plate.json");
        assertEquals(json("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:ingots/gold"},"count":1},
                  {"ingredient":{"tag":"forge:dusts/redstone"},"count":1},
                  {"ingredient":{"item":"advancedrocketrycommunity:silicon_wafer"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:basic_circuit_plate","count":1}],
                 "processing_time":900,"energy_per_tick":100}
                """), actual);
        assertEquals(90000, actual.get("processing_time").getAsInt() * actual.get("energy_per_tick").getAsInt());
    }

    @Test void advancedPrecisionPreservesBlockSelectorAndNinetyThousandFE() {
        JsonObject actual = V180CircuitPlateData.serverFiles().get(DATA + "precision_advanced_circuit_plate.json");
        assertEquals(json("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:ingots/gold"},"count":1},
                  {"ingredient":{"item":"minecraft:redstone_block"},"count":1},
                  {"ingredient":{"item":"advancedrocketrycommunity:silicon_wafer"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:advanced_circuit_plate","count":1}],
                 "processing_time":900,"energy_per_tick":100}
                """), actual);
        assertEquals(90000, actual.get("processing_time").getAsInt() * actual.get("energy_per_tick").getAsInt());
    }

    @Test void soleLanguageAdditionsAndMutableJsonValuesAreFresh() {
        assertEquals(Map.of("item.advancedrocketrycommunity.basic_circuit_plate", "Basic Circuit Plate",
                "item.advancedrocketrycommunity.advanced_circuit_plate", "Advanced Circuit Plate"), V180CircuitPlateData.language(false));
        assertEquals(Map.of("item.advancedrocketrycommunity.basic_circuit_plate", "基础电路板",
                "item.advancedrocketrycommunity.advanced_circuit_plate", "高级电路板"), V180CircuitPlateData.language(true));
        assertThrows(UnsupportedOperationException.class, () -> V180CircuitPlateData.language(false).clear());
        for (boolean client : new boolean[] {false, true}) {
            var first = client ? V180CircuitPlateData.clientFiles() : V180CircuitPlateData.serverFiles();
            var key = client ? BASIC_MODEL : DATA + "precision_basic_circuit_plate.json";
            var expected = first.get(key).deepCopy();
            first.get(key).entrySet().clear();
            assertEquals(expected, (client ? V180CircuitPlateData.clientFiles() : V180CircuitPlateData.serverFiles()).get(key));
        }
    }

    @Test void basicDrawingIsDeterministicTransparentAndFresh() throws Exception {
        checkDrawing(V180CircuitPlateData.basicGrid(), V180CircuitPlateData.basicTexture(), 0xB8A865);
        String[] grid = V180CircuitPlateData.basicGrid();
        grid[0] = "9999999999999999";
        byte[] png = V180CircuitPlateData.basicTexture(); png[0] = 0;
        assertEquals("................", V180CircuitPlateData.basicGrid()[0]);
        assertArrayEquals(V180CircuitPlateData.basicTexture(), V180CircuitPlateData.basicTexture());
        assertEquals((byte) 0x89, V180CircuitPlateData.basicTexture()[0]);
    }

    @Test void advancedDrawingHasIndependentGeometryNotRecolouredBoards() throws Exception {
        checkDrawing(V180CircuitPlateData.advancedGrid(), V180CircuitPlateData.advancedTexture(), 0x5B9FAB);
        assertArrayEquals(V180CircuitPlateData.advancedTexture(), V180CircuitPlateData.advancedTexture());
        for (String[] other : List.of(V180CircuitPlateData.basicGrid(), V180ControlBoardData.grid(),
                V180IoBoardData.grid(), V180LiquidIoBoardData.grid())) {
            assertFalse(Arrays.equals(other, V180CircuitPlateData.advancedGrid()));
            assertFalse(Arrays.equals(V180MaterialArt.pixels(other, 0xFFFFFF),
                    V180MaterialArt.pixels(V180CircuitPlateData.advancedGrid(), 0xFFFFFF)));
        }
        for (String[] other : List.of(V180ControlBoardData.grid(), V180IoBoardData.grid(), V180LiquidIoBoardData.grid())) {
            assertFalse(Arrays.equals(V180MaterialArt.pixels(other, 0xFFFFFF),
                    V180MaterialArt.pixels(V180CircuitPlateData.basicGrid(), 0xFFFFFF)));
        }
        String[] grid = V180CircuitPlateData.advancedGrid(); grid[0] = "9999999999999999";
        byte[] png = V180CircuitPlateData.advancedTexture(); png[0] = 0;
        assertEquals("................", V180CircuitPlateData.advancedGrid()[0]);
        assertEquals((byte) 0x89, V180CircuitPlateData.advancedTexture()[0]);
    }

    @Test void sideFlagsWriteOnlyTheSixOwnedFiles(@TempDir Path temp) throws Exception {
        for (int flags = 0; flags < 4; flags++) {
            Path output = temp.resolve("flags" + flags);
            boolean client = (flags & 1) != 0, server = (flags & 2) != 0;
            new V180CircuitPlateData(new PackOutput(output), client, server).run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
            Set<String> expected = new java.util.HashSet<>();
            if (client) { expected.addAll(Set.of(BASIC_MODEL, ADVANCED_MODEL, V180CircuitPlateData.BASIC_TEXTURE, V180CircuitPlateData.ADVANCED_TEXTURE)); }
            if (server) { expected.addAll(SERVER); }
            Set<String> actual = new java.util.HashSet<>();
            if (Files.exists(output)) {
                try (var files = Files.walk(output)) {
                    files.filter(Files::isRegularFile).forEach(path -> actual.add(output.relativize(path).toString().replace('\\', '/')));
                }
            }
            assertEquals(expected, actual);
        }
    }

    @Test void committedGeneratedResourcesAndLanguageMatchActualProvider() throws Exception {
        Path root = Path.of("src/generated/v1.8/resources");
        for (var files : List.of(V180CircuitPlateData.clientFiles(), V180CircuitPlateData.serverFiles())) {
            for (var entry : files.entrySet()) {
                assertEquals(entry.getValue(), JsonParser.parseString(Files.readString(root.resolve(entry.getKey()))));
            }
        }
        assertArrayEquals(V180CircuitPlateData.basicTexture(), Files.readAllBytes(root.resolve(V180CircuitPlateData.BASIC_TEXTURE)));
        assertArrayEquals(V180CircuitPlateData.advancedTexture(), Files.readAllBytes(root.resolve(V180CircuitPlateData.ADVANCED_TEXTURE)));
        for (String locale : List.of("en_us", "zh_cn")) {
            var actual = JsonParser.parseString(Files.readString(root.resolve("assets/advancedrocketrycommunity_v180/lang/" + locale + ".json"))).getAsJsonObject();
            V180CircuitPlateData.language(locale.equals("zh_cn")).forEach((key, value) -> assertEquals(value, actual.get(key).getAsString()));
        }
    }

    private static void checkDrawing(String[] grid, byte[] png, int tint) throws Exception {
        assertEquals(16, grid.length);
        for (String row : grid) { assertTrue(row.matches("[.0-9]{16}")); }
        assertTrue(png.length > 64 && png.length < 4096);
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image); assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
        int[] pixels = image.getRGB(0, 0, 16, 16, null, 0, 16);
        assertArrayEquals(V180MaterialArt.pixels(grid, tint), pixels);
        assertEquals(0, pixels[0]);
        assertTrue(Arrays.stream(pixels).anyMatch(value -> (value >>> 24) == 255));
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
