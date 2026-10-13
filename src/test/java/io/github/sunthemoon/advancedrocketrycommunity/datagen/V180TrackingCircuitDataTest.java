package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V180TrackingCircuitDataTest {
    private static final String MODEL = "assets/advancedrocketrycommunity/models/item/tracking_circuit.json";
    private static final String RECIPE = "data/advancedrocketrycommunity/recipes/precision_tracking_circuit.json";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void stableIdsAndLegacyMachineOnlyProducer() {
        assertEquals("advancedrocketrycommunity:tracking_circuit", V180TrackingCircuitData.ITEM_ID);
        assertEquals("advancedrocketrycommunity:precision_tracking_circuit", V180TrackingCircuitData.RECIPE_ID);
        assertEquals(Set.of(MODEL), V180TrackingCircuitData.clientFiles().keySet());
        assertEquals(Set.of(RECIPE), V180TrackingCircuitData.serverFiles().keySet());
        assertEquals(JsonParser.parseString("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:dusts/redstone"},"count":1},
                  {"ingredient":{"item":"minecraft:ender_eye"},"count":1},
                  {"ingredient":{"item":"advancedrocketrycommunity:basic_circuit_plate"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:tracking_circuit","count":1}],
                 "processing_time":900,"energy_per_tick":50}
                """), V180TrackingCircuitData.serverFiles().get(RECIPE));
    }

    @Test void localizedLegacyRoleAndFreshJson() {
        String key = "item.advancedrocketrycommunity.tracking_circuit";
        assertEquals(Map.of(key, "Tracking Circuit"), V180TrackingCircuitData.language(false));
        assertEquals(Map.of(key, "追踪电路"), V180TrackingCircuitData.language(true));
        var recipe = V180TrackingCircuitData.serverFiles().get(RECIPE);
        recipe.entrySet().clear();
        assertFalse(V180TrackingCircuitData.serverFiles().get(RECIPE).entrySet().isEmpty());
    }

    @Test void modelUsesOnlyTheOriginalTrackingTexture() {
        assertEquals(JsonParser.parseString("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/tracking_circuit"}}
                """), V180TrackingCircuitData.clientFiles().get(MODEL));
    }

    @Test void originalDrawingIsDeterministicTransparentAndDistinct() throws Exception {
        String[] grid = V180TrackingCircuitData.grid();
        assertEquals(16, grid.length);
        for (String row : grid) { assertTrue(row.matches("[.0-9]{16}")); }
        byte[] png = V180TrackingCircuitData.texture();
        assertArrayEquals(png, V180TrackingCircuitData.texture());
        assertFalse(java.util.Arrays.equals(png, V180ControlBoardData.texture()));
        assertFalse(java.util.Arrays.equals(png, V180IoBoardData.texture()));
        assertFalse(java.util.Arrays.equals(png, V180LiquidIoBoardData.texture()));
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
        int[] pixels = image.getRGB(0, 0, 16, 16, null, 0, 16);
        assertArrayEquals(V180MaterialArt.pixels(grid, 0x4AAE99), pixels);
        assertEquals(0, pixels[0]);
        assertTrue(java.util.Arrays.stream(pixels).anyMatch(value -> (value >>> 24) == 255));
        grid[0] = "9999999999999999";
        assertEquals("................", V180TrackingCircuitData.grid()[0]);
    }

    @Test void sideFlagsWriteOnlyScopedFiles(@TempDir Path temporary) throws Exception {
        for (int flags = 0; flags < 4; flags++) {
            Path output = temporary.resolve("flags" + flags);
            boolean client = (flags & 1) != 0, server = (flags & 2) != 0;
            new V180TrackingCircuitData(new PackOutput(output), client, server)
                    .run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
            Set<String> expected = new HashSet<>();
            if (client) { expected.add(MODEL); expected.add(V180TrackingCircuitData.TEXTURE); }
            if (server) { expected.add(RECIPE); }
            Set<String> actual = new HashSet<>();
            if (Files.exists(output)) {
                try (var files = Files.walk(output)) {
                    files.filter(Files::isRegularFile).forEach(file ->
                            actual.add(output.relativize(file).toString().replace('\\', '/')));
                }
            }
            assertEquals(expected, actual);
        }
    }

    @Test void committedResourcesAndTranslationsMatchTheProvider() throws Exception {
        Path root = Path.of("src/generated/v1.8/resources");
        for (var files : List.of(V180TrackingCircuitData.clientFiles(), V180TrackingCircuitData.serverFiles())) {
            for (var file : files.entrySet()) {
                assertEquals(file.getValue(), JsonParser.parseString(Files.readString(root.resolve(file.getKey()))));
            }
        }
        assertArrayEquals(V180TrackingCircuitData.texture(), Files.readAllBytes(root.resolve(V180TrackingCircuitData.TEXTURE)));
        for (String locale : List.of("en_us", "zh_cn")) {
            var language = JsonParser.parseString(Files.readString(root.resolve(
                    "assets/advancedrocketrycommunity_v180/lang/" + locale + ".json"))).getAsJsonObject();
            V180TrackingCircuitData.language(locale.equals("zh_cn")).forEach((key, value) ->
                    assertEquals(value, language.get(key).getAsString()));
        }
    }
}
