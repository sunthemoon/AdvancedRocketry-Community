package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconStatus;
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
 * ADR-065 C17c beacon resources: exactly the stable IDs' paths, an orientable controller over original textures, a
 * self-drop loot table, recipes from C16 interface and circuit parts, the generated pattern, and every status key in
 * both languages. The committed DataGen output equals the provider.
 */
class V180BeaconDataTest {
    private static final String NS = "advancedrocketrycommunity";
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theStableIdsOwnExactlyTheirPaths() {
        assertEquals(Set.of("assets/" + NS + "/blockstates/planetary_beacon.json",
                "assets/" + NS + "/models/block/planetary_beacon.json",
                "assets/" + NS + "/models/item/planetary_beacon.json",
                "assets/" + NS + "/models/item/beacon_finder.json"), V180BeaconData.clientFiles().keySet());
        assertEquals(Set.of("data/" + NS + "/machine_patterns/planetary_beacon.json",
                "data/" + NS + "/loot_tables/blocks/planetary_beacon.json",
                "data/" + NS + "/recipes/planetary_beacon.json",
                "data/" + NS + "/recipes/beacon_finder.json",
                "data/" + NS + "/advancements/recipes/misc/planetary_beacon.json",
                "data/" + NS + "/advancements/recipes/equipment/beacon_finder.json"), V180BeaconData.serverFiles().keySet());
        assertEquals(Set.of("assets/" + NS + "/textures/block/planetary_beacon_front.png",
                "assets/" + NS + "/textures/block/planetary_beacon_side.png",
                "assets/" + NS + "/textures/block/planetary_beacon_top.png",
                "assets/" + NS + "/textures/item/beacon_finder.png"), V180BeaconData.textures().keySet());
        JsonObject states = V180BeaconData.clientFiles().get("assets/" + NS + "/blockstates/planetary_beacon.json")
                .getAsJsonObject("variants");
        assertEquals(Set.of("facing=north", "facing=east", "facing=south", "facing=west"), states.keySet());
        assertEquals(270, states.getAsJsonObject("facing=west").get("y").getAsInt());
        assertEquals("minecraft:block/orientable", V180BeaconData.clientFiles()
                .get("assets/" + NS + "/models/block/planetary_beacon.json").get("parent").getAsString());
    }

    @Test
    void recipesUseC16InterfaceAndCircuitPartsAndTheLootDropsItself() {
        JsonObject beacon = V180BeaconData.serverFiles().get("data/" + NS + "/recipes/planetary_beacon.json");
        JsonObject key = beacon.getAsJsonObject("key");
        assertEquals(NS + ":user_interface", key.getAsJsonObject("u").get("item").getAsString());
        assertEquals(NS + ":control_board", key.getAsJsonObject("b").get("item").getAsString());
        assertEquals(NS + ":advanced_circuit", key.getAsJsonObject("a").get("item").getAsString());
        assertEquals(NS + ":planetary_beacon", beacon.getAsJsonObject("result").get("item").getAsString());
        JsonObject finder = V180BeaconData.serverFiles().get("data/" + NS + "/recipes/beacon_finder.json");
        assertEquals(NS + ":user_interface", finder.getAsJsonObject("key").getAsJsonObject("u").get("item").getAsString());
        assertEquals(NS + ":basic_circuit", finder.getAsJsonObject("key").getAsJsonObject("c").get("item").getAsString());
        assertEquals(NS + ":beacon_finder", finder.getAsJsonObject("result").get("item").getAsString());
        // No module recipe of the equipment slice has this shape and these ingredients.
        assertFalse(V180EquipmentData.serverFiles().values().stream().anyMatch(recipe ->
                recipe.has("key") && recipe.getAsJsonObject("key").equals(finder.getAsJsonObject("key"))));
        String loot = V180BeaconData.serverFiles().get("data/" + NS + "/loot_tables/blocks/planetary_beacon.json")
                .toString();
        assertTrue(loot.contains("\"" + NS + ":planetary_beacon\"") && loot.contains("survives_explosion"));
    }

    @Test
    void texturesAreOriginalGridsAndTheFinderHasATransparentOutline() {
        Set<String> faces = new HashSet<>();
        for (String face : List.of("front", "side", "top")) {
            String[] grid = V180BeaconData.grid(face);
            assertEquals(16, grid.length);
            assertTrue(List.of(grid).stream().allMatch(row -> row.length() == 16 && !row.contains(".")), face);
            assertTrue(faces.add(String.join("", grid)), face);
        }
        String[] finder = V180BeaconData.grid("beacon_finder");
        assertTrue(List.of(finder).stream().allMatch(row -> row.length() == 16));
        assertEquals('.', finder[0].charAt(0));
        assertEquals('.', finder[15].charAt(15));
        V180BeaconData.textures().values().forEach(png -> assertTrue(png.length > 8 && png[1] == 'P'));
    }

    @Test
    void everyStatusAndHudStringExistsInBothLanguagesAndTheOutputIsCommitted() throws Exception {
        Map<String, String> english = V180BeaconData.language(false);
        Map<String, String> chinese = V180BeaconData.language(true);
        assertEquals(english.keySet(), chinese.keySet());
        for (BeaconStatus status : BeaconStatus.values()) {
            assertTrue(english.containsKey(status.translationKey()), status.name());
            assertFalse(chinese.get(status.translationKey()).isBlank(), status.name());
        }
        for (String key : List.of("block." + NS + ".planetary_beacon", "item." + NS + ".beacon_finder",
                "hud." + NS + ".beacon_finder.marker", "hud." + NS + ".beacon_finder.last_known",
                "message." + NS + ".beacon.not_owner")) {
            assertTrue(english.containsKey(key), key);
        }
        for (String locale : List.of("en_us", "zh_cn")) {
            JsonObject lang = JsonParser.parseString(Files.readString(GENERATED.resolve("assets/" + NS
                    + "_v180/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> expected = locale.equals("en_us") ? english : chinese;
            expected.forEach((key, value) -> assertEquals(value, lang.has(key) ? lang.get(key).getAsString() : null,
                    locale + " " + key));
        }
        for (Map.Entry<String, JsonObject> file : V180BeaconData.serverFiles().entrySet()) {
            assertEquals(file.getValue(), JsonParser.parseString(Files.readString(GENERATED.resolve(file.getKey()),
                    StandardCharsets.UTF_8)), file.getKey());
        }
        for (Map.Entry<String, JsonObject> file : V180BeaconData.clientFiles().entrySet()) {
            assertEquals(file.getValue(), JsonParser.parseString(Files.readString(GENERATED.resolve(file.getKey()),
                    StandardCharsets.UTF_8)), file.getKey());
        }
        for (Map.Entry<String, byte[]> file : V180BeaconData.textures().entrySet()) {
            assertTrue(java.util.Arrays.equals(file.getValue(), Files.readAllBytes(GENERATED.resolve(file.getKey()))),
                    file.getKey());
        }
    }
}
