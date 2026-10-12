package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-065 C17b controller resources: exactly the four stable IDs, cube models over original textures, self-drop
 * loot, recipes from C16 interface and circuit parts, and a stable key for every station code in both languages.
 */
class V180StationControlDataTest {
    private static final String NS = "advancedrocketrycommunity";
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");
    private static final List<String> IDS = List.of("warp_controller", "station_gravity_controller",
            "station_altitude_controller", "station_orientation_controller");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theFourStableIdsOwnExactlyTheirPaths() {
        assertEquals(IDS, V180StationControlData.ids());
        Set<String> client = new HashSet<>();
        Set<String> server = new HashSet<>();
        for (String id : IDS) {
            client.add("assets/" + NS + "/blockstates/" + id + ".json");
            client.add("assets/" + NS + "/models/block/" + id + ".json");
            client.add("assets/" + NS + "/models/item/" + id + ".json");
            server.add("data/" + NS + "/loot_tables/blocks/" + id + ".json");
            server.add("data/" + NS + "/recipes/" + id + ".json");
            server.add("data/" + NS + "/advancements/recipes/misc/" + id + ".json");
        }
        assertEquals(client, V180StationControlData.clientFiles().keySet());
        assertEquals(server, V180StationControlData.serverFiles().keySet());
        assertEquals(json("{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{\"top\":\"" + NS
                        + ":block/station_controller_top\",\"bottom\":\"" + NS + ":block/station_controller_bottom\","
                        + "\"side\":\"" + NS + ":block/warp_controller_side\"}}"),
                V180StationControlData.clientFiles().get("assets/" + NS + "/models/block/warp_controller.json"));
    }

    @Test
    void recipesUseOnlyC16InterfaceAndCircuitPartsAndDiffer() {
        Set<String> centres = new HashSet<>();
        for (String id : IDS) {
            JsonObject recipe = V180StationControlData.serverFiles().get("data/" + NS + "/recipes/" + id + ".json");
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            JsonObject key = recipe.getAsJsonObject("key");
            assertEquals("forge:plates/steel", key.getAsJsonObject("p").get("tag").getAsString());
            assertEquals(NS + ":user_interface", key.getAsJsonObject("u").get("item").getAsString());
            assertEquals(NS + ":control_board", key.getAsJsonObject("b").get("item").getAsString());
            assertTrue(centres.add(key.getAsJsonObject("c").get("item").getAsString()), "recipes must differ");
            assertEquals(NS + ":" + id, recipe.getAsJsonObject("result").get("item").getAsString());
            JsonObject loot = V180StationControlData.serverFiles().get("data/" + NS + "/loot_tables/blocks/" + id + ".json");
            assertTrue(loot.toString().contains("\"" + NS + ":" + id + "\"") && loot.toString().contains("survives_explosion"));
        }
    }

    @Test
    void texturesAreOriginalOpaqueGridsWithADistinctFacePerController() {
        Map<String, byte[]> textures = V180StationControlData.textures();
        assertEquals(6, textures.size());
        Set<String> faces = new HashSet<>();
        for (String id : IDS) {
            String[] grid = V180StationControlData.grid(id);
            assertEquals(16, grid.length);
            assertTrue(List.of(grid).stream().allMatch(row -> row.length() == 16 && !row.contains(".")), id);
            assertTrue(faces.add(String.join("", grid)), "every controller face differs: " + id);
        }
        assertNotEquals(String.join("", V180StationControlData.grid("top")),
                String.join("", V180StationControlData.grid("bottom")));
        textures.values().forEach(png -> assertTrue(png.length > 8 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G'));
    }

    @Test
    void everyStationCodeHasAStableKeyInBothLanguagesAndScreensUseTokens() throws Exception {
        Map<String, String> english = V180StationControlData.language(false);
        Map<String, String> chinese = V180StationControlData.language(true);
        assertEquals(english.keySet(), chinese.keySet());
        for (StationManagementCode code : StationManagementCode.values()) {
            assertEquals("station." + NS + ".code." + code.name().toLowerCase(java.util.Locale.ROOT), code.translationKey());
            assertTrue(english.containsKey(code.translationKey()) && chinese.containsKey(code.translationKey()), code.name());
            assertFalse(chinese.get(code.translationKey()).isBlank());
        }
        for (String id : IDS) {
            assertTrue(english.containsKey("block." + NS + "." + id) && chinese.containsKey("block." + NS + "." + id));
        }
        // The committed DataGen output carries every key in both languages.
        for (String locale : List.of("en_us", "zh_cn")) {
            JsonObject lang = JsonParser.parseString(Files.readString(GENERATED.resolve("assets/" + NS
                    + "_v180/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : english.keySet()) {
                assertTrue(lang.has(key), locale + " lacks " + key);
            }
        }
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
