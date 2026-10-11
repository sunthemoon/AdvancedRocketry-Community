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

class V180IoBoardDataTest {
    private static final String DATA = "data/advancedrocketrycommunity/";
    private static final String MODEL = "assets/advancedrocketrycommunity/models/item/item_io_board.json";
    private static final Set<String> SERVER = Set.of(DATA + "recipes/item_io_board.json",
            DATA + "recipes/precision_item_io_board.json", DATA + "advancements/recipes/misc/item_io_board.json");

    @Test void stableIdsAndExactlyFiveScopedResources() {
        assertEquals("advancedrocketrycommunity:item_io_board", V180IoBoardData.ITEM_ID);
        assertEquals("advancedrocketrycommunity:precision_item_io_board", V180IoBoardData.PRECISION_ID);
        assertEquals(Set.of(MODEL), V180IoBoardData.clientFiles().keySet());
        assertEquals(SERVER, V180IoBoardData.serverFiles().keySet());
        assertEquals("assets/advancedrocketrycommunity/textures/item/item_io_board.png", V180IoBoardData.TEXTURE);
        assertEquals(json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/item_io_board"}}
                """), V180IoBoardData.clientFiles().get(MODEL));
    }

    @Test void craftingPreservesAllNineSlotsAndFiveSelectors() {
        assertEquals(json("""
                {"type":"minecraft:crafting_shaped","category":"misc","group":"","pattern":["rvr","dwd","dpd"],
                 "key":{"r":{"tag":"forge:dusts/redstone"},"v":{"tag":"forge:gems/quartz"},
                  "d":{"tag":"forge:dusts/gold"},"w":{"tag":"minecraft:wooden_slabs"},
                  "p":{"tag":"forge:plates/iron"}},
                 "result":{"item":"advancedrocketrycommunity:item_io_board","count":1}}
                """), V180IoBoardData.serverFiles().get(DATA + "recipes/item_io_board.json"));
    }

    @Test void precisionPreservesOrderedChannelsAndExactlyTwoThousandFE() {
        var actual = V180IoBoardData.serverFiles().get(DATA + "recipes/precision_item_io_board.json");
        assertEquals(json("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:dusts/redstone"},"count":1},
                  {"ingredient":{"tag":"forge:plates/gold"},"count":1},
                  {"ingredient":{"tag":"forge:plates/steel"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:item_io_board","count":1}],
                 "processing_time":200,"energy_per_tick":10}
                """), actual);
        assertEquals(2000, actual.get("processing_time").getAsInt() * actual.get("energy_per_tick").getAsInt());
    }

    @Test void goldDustOrRecipeUnlockRewardsOnlyCrafting() {
        assertEquals(json("""
                {"parent":"minecraft:recipes/root","criteria":{
                 "has_gold_dust":{"trigger":"minecraft:inventory_changed","conditions":{
                  "items":[{"tag":"forge:dusts/gold","count":{"min":1}}]}},
                 "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{
                  "recipe":"advancedrocketrycommunity:item_io_board"}}},
                 "requirements":[["has_gold_dust","has_the_recipe"]],
                 "rewards":{"recipes":["advancedrocketrycommunity:item_io_board"]}}
                """), V180IoBoardData.serverFiles().get(DATA + "advancements/recipes/misc/item_io_board.json"));
    }

    @Test void soleLanguageAdditionsAndJsonValuesAreFresh() {
        assertEquals(Map.of("item.advancedrocketrycommunity.item_io_board", "Item IO Circuit Board"),
                V180IoBoardData.language(false));
        assertEquals(Map.of("item.advancedrocketrycommunity.item_io_board", "物品 IO 电路板"),
                V180IoBoardData.language(true));
        assertThrows(UnsupportedOperationException.class, () -> V180IoBoardData.language(false).clear());
        var before = V180IoBoardData.serverFiles();
        var expected = before.get(DATA + "recipes/item_io_board.json").deepCopy();
        before.get(DATA + "recipes/item_io_board.json").entrySet().clear();
        assertEquals(expected, V180IoBoardData.serverFiles().get(DATA + "recipes/item_io_board.json"));
        var model = V180IoBoardData.clientFiles().get(MODEL);
        model.entrySet().clear();
        assertFalse(V180IoBoardData.clientFiles().get(MODEL).entrySet().isEmpty());
    }

    @Test void newDrawingIsDeterministicValidTransparentAndIndependent() throws Exception {
        String[] grid = V180IoBoardData.grid();
        assertEquals(16, grid.length);
        for (String row : grid) { assertTrue(row.matches("[.0-9]{16}")); }
        byte[] png = V180IoBoardData.texture();
        assertTrue(png.length > 64 && png.length < 4096);
        assertArrayEquals(png, V180IoBoardData.texture());
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        int[] pixels = image.getRGB(0, 0, 16, 16, null, 0, 16);
        assertArrayEquals(V180MaterialArt.pixels(grid, 0xC5C987), pixels);
        assertEquals(0, pixels[0]);
        assertTrue(java.util.Arrays.stream(pixels).anyMatch(value -> (value >>> 24) == 255));
        grid[0] = "9999999999999999";
        png[0] = 0;
        assertEquals("................", V180IoBoardData.grid()[0]);
        assertEquals((byte) 0x89, V180IoBoardData.texture()[0]);
    }

    @Test void sideFlagsWriteOnlyOwnedFiles(@TempDir Path temp) throws Exception {
        for (int flags = 0; flags < 4; flags++) {
            Path output = temp.resolve("flags" + flags);
            boolean client = (flags & 1) != 0, server = (flags & 2) != 0;
            new V180IoBoardData(new PackOutput(output), client, server).run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
            Set<String> expected = new java.util.HashSet<>();
            if (client) { expected.add(MODEL); expected.add(V180IoBoardData.TEXTURE); }
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
        for (var files : List.of(V180IoBoardData.clientFiles(), V180IoBoardData.serverFiles())) {
            for (var entry : files.entrySet()) {
                assertEquals(entry.getValue(), JsonParser.parseString(Files.readString(root.resolve(entry.getKey()))));
            }
        }
        assertArrayEquals(V180IoBoardData.texture(), Files.readAllBytes(root.resolve(V180IoBoardData.TEXTURE)));
        for (String locale : List.of("en_us", "zh_cn")) {
            var actual = JsonParser.parseString(Files.readString(root.resolve("assets/advancedrocketrycommunity/lang/" + locale + ".json"))).getAsJsonObject();
            V180IoBoardData.language(locale.equals("zh_cn")).forEach((key, value) -> assertEquals(value, actual.get(key).getAsString()));
        }
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
