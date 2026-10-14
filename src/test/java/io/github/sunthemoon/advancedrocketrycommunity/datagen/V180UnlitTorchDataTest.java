package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A0 checks of the unlit torch resources, written from ADR-066 section 3.2 rather than the provider's constants. */
class V180UnlitTorchDataTest {
    private static final String NS = "advancedrocketrycommunity:";
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String DATA = "data/advancedrocketrycommunity/";
    private static final String TEXTURE = ASSETS + "textures/block/unlit_torch.png";

    @Test
    void partitionIsExactlyFourClientAndFourServerFiles() {
        assertEquals(Set.of(ASSETS + "blockstates/unlit_torch.json", ASSETS + "blockstates/unlit_wall_torch.json",
                ASSETS + "models/block/unlit_torch.json", ASSETS + "models/block/unlit_wall_torch.json"),
                V180UnlitTorchData.clientFiles().keySet());
        assertEquals(Set.of(DATA + "tags/blocks/combustion_torches.json",
                DATA + "tags/items/combustion_fire_starters.json", DATA + "loot_tables/blocks/unlit_torch.json",
                DATA + "loot_tables/blocks/unlit_wall_torch.json"), V180UnlitTorchData.serverFiles().keySet());
    }

    @Test
    void tagsHoldExactlyTheVanillaTorchesAndTheFourFireStarters() {
        var server = V180UnlitTorchData.serverFiles();
        assertEquals(element("{\"replace\":false,\"values\":[\"minecraft:torch\",\"minecraft:wall_torch\"]}"),
                server.get(DATA + "tags/blocks/combustion_torches.json"));
        assertEquals(element("{\"replace\":false,\"values\":[\"minecraft:flint_and_steel\",\"minecraft:fire_charge\","
                        + "\"minecraft:blaze_rod\",\"minecraft:blaze_powder\"]}"),
                server.get(DATA + "tags/items/combustion_fire_starters.json"));
        assertTrue(!server.get(DATA + "tags/blocks/combustion_torches.json").toString().contains("thermite"));
    }

    @Test
    void bothBlocksDropTheSavedSourceThroughOneDynamicEntryThatSurvivesExplosion() {
        for (String block : List.of("unlit_torch", "unlit_wall_torch")) {
            assertEquals(element("""
                    {"type":"minecraft:block","pools":[{"rolls":1.0,"bonus_rolls":0.0,
                     "entries":[{"type":"minecraft:dynamic","name":"advancedrocketrycommunity:unlit_torch_source"}],
                     "conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                    """), V180UnlitTorchData.serverFiles().get(DATA + "loot_tables/blocks/" + block + ".json"));
        }
    }

    @Test
    void modelsUseTheVanillaTorchParentsAndTheOneNewTexture() {
        var client = V180UnlitTorchData.clientFiles();
        for (var model : Map.of("unlit_torch", "minecraft:block/template_torch",
                "unlit_wall_torch", "minecraft:block/template_torch_wall").entrySet()) {
            JsonObject json = client.get(ASSETS + "models/block/" + model.getKey() + ".json");
            assertEquals(Set.of("parent", "textures", "render_type"), json.keySet());
            assertEquals(model.getValue(), json.get("parent").getAsString());
            assertEquals("minecraft:cutout", json.get("render_type").getAsString());
            assertEquals(element("{\"torch\":\"" + NS + "block/unlit_torch\"}"), json.get("textures"));
        }
        assertEquals(element("{\"variants\":{\"\":{\"model\":\"" + NS + "block/unlit_torch\"}}}"),
                client.get(ASSETS + "blockstates/unlit_torch.json"));
        JsonObject variants = client.get(ASSETS + "blockstates/unlit_wall_torch.json").getAsJsonObject("variants");
        assertEquals(Set.of("facing=east", "facing=south", "facing=west", "facing=north"), variants.keySet());
        for (var yaw : Map.of("east", 0, "south", 90, "west", 180, "north", 270).entrySet()) {
            JsonObject variant = variants.getAsJsonObject("facing=" + yaw.getKey());
            assertEquals(NS + "block/unlit_wall_torch", variant.get("model").getAsString());
            assertEquals((int) yaw.getValue(), variant.has("y") ? variant.get("y").getAsInt() : 0);
        }
    }

    @Test
    void gridIsATwoPixelShaftWithADarkTipAndNoFlame() {
        String[] grid = V180UnlitTorchData.torchGrid();
        assertEquals(16, grid.length);
        for (int y = 0; y < 16; y++) {
            assertTrue(grid[y].matches("[.0-9]{16}"), grid[y]);
            for (int x = 0; x < 16; x++) {
                boolean shaft = (x == 7 || x == 8) && y >= 6;
                assertEquals(shaft, grid[y].charAt(x) != '.', "pixel " + x + "," + y);
            }
        }
        int tipMax = 0;
        int shaftMin = 9;
        for (int x = 7; x <= 8; x++) {
            tipMax = Math.max(tipMax, Math.max(grid[6].charAt(x), grid[7].charAt(x)) - '0');
            for (int y = 8; y < 13; y++) {
                shaftMin = Math.min(shaftMin, grid[y].charAt(x) - '0');
            }
        }
        assertTrue(tipMax < shaftMin, "the tip is darker than the shaft below it: " + tipMax + " < " + shaftMin);
        assertTrue(String.join("", grid).indexOf('9') < 0, "no bright flame pixel");
        assertArrayEquals(V180UnlitTorchData.torchTexture(), V180UnlitTorchData.torchTexture());
    }

    @Test
    void committedTextureIsTheProviderOutput() throws Exception {
        Path committed = Path.of("src/generated/v1.8/resources").resolve(TEXTURE);
        assertArrayEquals(V180UnlitTorchData.torchTexture(), Files.readAllBytes(committed));
    }

    @Test
    void languageMapsShareOneKeySet() {
        Map<String, String> english = V180UnlitTorchLanguage.translations(false);
        Map<String, String> chinese = V180UnlitTorchLanguage.translations(true);
        assertEquals(english.keySet(), chinese.keySet());
        assertEquals("Unlit Torch", english.get("block.advancedrocketrycommunity.unlit_torch"));
        assertEquals("Unlit Wall Torch", english.get("block.advancedrocketrycommunity.unlit_wall_torch"));
        assertEquals(Set.of("no_combustion", "unsupported", "busy", "quarantined", "unknown"),
                english.keySet().stream().filter(key -> key.startsWith("message."))
                        .map(key -> key.substring("message.advancedrocketrycommunity.torch.".length()))
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private static JsonElement element(String text) {
        return JsonParser.parseString(text);
    }
}
