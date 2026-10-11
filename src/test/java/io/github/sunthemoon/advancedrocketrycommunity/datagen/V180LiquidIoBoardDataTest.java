package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V180LiquidIoBoardDataTest {
    private static final String DATA = "data/advancedrocketrycommunity/";
    private static final String MODEL = "assets/advancedrocketrycommunity/models/item/liquid_io_board.json";
    private static final Set<String> SERVER = Set.of(DATA + "recipes/liquid_io_board.json",
            DATA + "recipes/precision_liquid_io_board.json", DATA + "advancements/recipes/misc/liquid_io_board.json");

    @Test void stableIdsAndExactlyFiveScopedResources() {
        assertEquals("advancedrocketrycommunity:liquid_io_board", V180LiquidIoBoardData.ITEM_ID);
        assertEquals("advancedrocketrycommunity:precision_liquid_io_board", V180LiquidIoBoardData.PRECISION_ID);
        assertEquals(Set.of(MODEL), V180LiquidIoBoardData.clientFiles().keySet());
        assertEquals(SERVER, V180LiquidIoBoardData.serverFiles().keySet());
        assertEquals("assets/advancedrocketrycommunity/textures/item/liquid_io_board.png", V180LiquidIoBoardData.TEXTURE);
        assertEquals(json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/liquid_io_board"}}
                """), V180LiquidIoBoardData.clientFiles().get(MODEL));
    }

    @Test void craftingPreservesAllNineSlotsAndFiveSelectors() {
        assertEquals(json("""
                {"type":"minecraft:crafting_shaped","category":"misc","group":"","pattern":["rvr","dwd","dpd"],
                 "key":{"r":{"tag":"forge:dusts/redstone"},"v":{"tag":"forge:gems/quartz"},
                  "d":{"tag":"forge:gems/lapis"},"w":{"tag":"minecraft:wooden_slabs"},
                  "p":{"tag":"forge:plates/iron"}},
                 "result":{"item":"advancedrocketrycommunity:liquid_io_board","count":1}}
                """), V180LiquidIoBoardData.serverFiles().get(DATA + "recipes/liquid_io_board.json"));
    }

    @Test void precisionPreservesOrderedChannelsAndExactlyTwoThousandFE() {
        var actual = V180LiquidIoBoardData.serverFiles().get(DATA + "recipes/precision_liquid_io_board.json");
        assertEquals(json("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:dusts/redstone"},"count":1},
                  {"ingredient":{"tag":"forge:gems/lapis"},"count":1},
                  {"ingredient":{"tag":"forge:plates/steel"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:liquid_io_board","count":1}],
                 "processing_time":200,"energy_per_tick":10}
                """), actual);
        assertEquals(2000, actual.get("processing_time").getAsInt() * actual.get("energy_per_tick").getAsInt());
    }

    @Test void lapisOrRecipeUnlockRewardsOnlyCrafting() {
        assertEquals(json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_lapis":{"trigger":"minecraft:inventory_changed","conditions":{
                  "items":[{"tag":"forge:gems/lapis","count":{"min":1}}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                  "recipe":"advancedrocketrycommunity:liquid_io_board"}}},
                 "requirements":[["has_lapis","has_the_recipe"]],
                 "rewards":{"recipes":["advancedrocketrycommunity:liquid_io_board"]}}
                """), V180LiquidIoBoardData.serverFiles().get(DATA + "advancements/recipes/misc/liquid_io_board.json"));
    }

    @Test void soleLanguageAdditionsAndJsonValuesAreFresh() {
        assertEquals(Map.of("item.advancedrocketrycommunity.liquid_io_board", "Liquid IO Circuit Board"),
                V180LiquidIoBoardData.language(false));
        assertEquals(Map.of("item.advancedrocketrycommunity.liquid_io_board", "流体 IO 电路板"),
                V180LiquidIoBoardData.language(true));
        assertThrows(UnsupportedOperationException.class, () -> V180LiquidIoBoardData.language(false).clear());
        var before = V180LiquidIoBoardData.serverFiles();
        var expected = before.get(DATA + "recipes/liquid_io_board.json").deepCopy();
        before.get(DATA + "recipes/liquid_io_board.json").entrySet().clear();
        assertEquals(expected, V180LiquidIoBoardData.serverFiles().get(DATA + "recipes/liquid_io_board.json"));
        var model = V180LiquidIoBoardData.clientFiles().get(MODEL);
        model.entrySet().clear();
        assertFalse(V180LiquidIoBoardData.clientFiles().get(MODEL).entrySet().isEmpty());
    }

    @Test void newDrawingIsDeterministicValidTransparentAndIndependent() throws Exception {
        String[] grid = V180LiquidIoBoardData.grid();
        assertEquals(16, grid.length);
        for (String row : grid) { assertTrue(row.matches("[.0-9]{16}")); }
        byte[] png = V180LiquidIoBoardData.texture();
        assertTrue(png.length > 64 && png.length < 4096);
        assertArrayEquals(png, V180LiquidIoBoardData.texture());
        assertFalse(java.util.Arrays.equals(png, V180IoBoardData.texture()));
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        int[] pixels = image.getRGB(0, 0, 16, 16, null, 0, 16);
        assertArrayEquals(V180MaterialArt.pixels(grid, 0x68B7CF), pixels);
        assertEquals(0, pixels[0]);
        assertTrue(java.util.Arrays.stream(pixels).anyMatch(value -> (value >>> 24) == 255));
        grid[0] = "9999999999999999";
        png[0] = 0;
        assertEquals("................", V180LiquidIoBoardData.grid()[0]);
        assertEquals((byte) 0x89, V180LiquidIoBoardData.texture()[0]);
    }

    @Test void sideFlagsWriteOnlyOwnedFiles(@TempDir Path temp) throws Exception {
        for (int flags = 0; flags < 4; flags++) {
            Path output = temp.resolve("flags" + flags);
            boolean client = (flags & 1) != 0, server = (flags & 2) != 0;
            new V180LiquidIoBoardData(new PackOutput(output), client, server).run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
            Set<String> expected = new java.util.HashSet<>();
            if (client) { expected.add(MODEL); expected.add(V180LiquidIoBoardData.TEXTURE); }
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

    @Test void committedGeneratedFilesAndNamesMatchActualProvider() throws Exception {
        Path root = Path.of("src/generated/v1.8/resources");
        for (var files : List.of(V180LiquidIoBoardData.clientFiles(), V180LiquidIoBoardData.serverFiles())) {
            for (var entry : files.entrySet()) {
                assertEquals(entry.getValue(), JsonParser.parseString(Files.readString(root.resolve(entry.getKey()))));
            }
        }
        assertArrayEquals(V180LiquidIoBoardData.texture(), Files.readAllBytes(root.resolve(V180LiquidIoBoardData.TEXTURE)));
        for (String locale : List.of("en_us", "zh_cn")) {
            var actual = JsonParser.parseString(Files.readString(root.resolve("assets/advancedrocketrycommunity_v180/lang/" + locale + ".json"))).getAsJsonObject();
            V180LiquidIoBoardData.language(locale.equals("zh_cn")).forEach((key, value) -> assertEquals(value, actual.get(key).getAsString()));
        }
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
