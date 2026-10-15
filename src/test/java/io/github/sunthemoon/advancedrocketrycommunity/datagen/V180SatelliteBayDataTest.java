package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
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
 * W27b resources: exactly the bay's paths, original textures, a loot table that drops the bay and its package once
 * (also on explosions), a community recipe, the rocket tag, every status and guard message in both languages, and the
 * committed DataGen output equal to the provider.
 */
class V180SatelliteBayDataTest {
    private static final String NS = "advancedrocketrycommunity";
    private static final String BAY = "rocket_satellite_bay";
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theStableIdOwnsExactlyItsPaths() {
        assertEquals(Set.of("assets/" + NS + "/blockstates/" + BAY + ".json",
                "assets/" + NS + "/models/block/" + BAY + ".json",
                "assets/" + NS + "/models/item/" + BAY + ".json"), V180SatelliteBayData.clientFiles().keySet());
        assertEquals(Set.of("data/" + NS + "/loot_tables/blocks/" + BAY + ".json",
                "data/" + NS + "/recipes/" + BAY + ".json",
                "data/" + NS + "/advancements/recipes/misc/" + BAY + ".json",
                "data/" + NS + "/tags/blocks/rocket_satellite_bays.json"), V180SatelliteBayData.serverFiles().keySet());
        assertEquals(Set.of("assets/" + NS + "/textures/block/" + BAY + "_top.png",
                "assets/" + NS + "/textures/block/" + BAY + "_side.png",
                "assets/" + NS + "/textures/block/" + BAY + "_bottom.png"), V180SatelliteBayData.textures().keySet());
    }

    @Test
    void theLootDropsTheBayAndTheExtractedPackageEvenFromExplosions() {
        String loot = V180SatelliteBayData.serverFiles().get("data/" + NS + "/loot_tables/blocks/" + BAY + ".json")
                .toString();
        assertTrue(loot.contains("\"" + NS + ":" + BAY + "\""));
        assertTrue(loot.contains("\"minecraft:dynamic\"") && loot.contains("\"" + SatelliteBayBlock.PACKAGE_DROP + "\""));
        assertFalse(loot.contains("survives_explosion") || loot.contains("explosion_decay"));
        String tag = V180SatelliteBayData.serverFiles().get("data/" + NS + "/tags/blocks/rocket_satellite_bays.json")
                .toString();
        assertEquals("{\"replace\":false,\"values\":[\"" + NS + ":" + BAY + "\"]}", tag);
    }

    @Test
    void theRecipeUsesReachableAluminumPlatesAChestAndABasicCircuit() {
        JsonObject recipe = V180SatelliteBayData.serverFiles().get("data/" + NS + "/recipes/" + BAY + ".json");
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("forge:plates/aluminum", key.getAsJsonObject("p").get("tag").getAsString());
        assertEquals("minecraft:chest", key.getAsJsonObject("c").get("item").getAsString());
        assertEquals(NS + ":basic_circuit", key.getAsJsonObject("b").get("item").getAsString());
        assertEquals(NS + ":" + BAY, recipe.getAsJsonObject("result").get("item").getAsString());
    }

    @Test
    void texturesAreThreeDistinctOpaqueOriginalGrids() {
        Set<String> faces = new HashSet<>();
        for (String face : List.of("top", "side", "bottom")) {
            String[] grid = V180SatelliteBayData.grid(face);
            assertEquals(16, grid.length);
            assertTrue(List.of(grid).stream().allMatch(row -> row.length() == 16 && !row.contains(".")), face);
            assertTrue(faces.add(String.join("", grid)), face);
        }
        V180SatelliteBayData.textures().values().forEach(png -> assertTrue(png.length > 8 && png[1] == 'P'));
    }

    @Test
    void everyStatusAndGuardMessageExistsInBothLanguagesAndTheOutputIsCommitted() throws Exception {
        Map<String, String> english = V180SatelliteBayData.language(false);
        Map<String, String> chinese = V180SatelliteBayData.language(true);
        assertEquals(english.keySet(), chinese.keySet());
        for (SatelliteBayStatus.Phase phase : SatelliteBayStatus.Phase.values()) {
            assertFalse(chinese.get(phase.translationKey()).isBlank(), phase.name());
        }
        for (SatelliteBayStatus.Reason reason : SatelliteBayStatus.Reason.values()) {
            assertFalse(chinese.get(reason.translationKey()).isBlank(), reason.name());
        }
        for (SatelliteBayStatus.Guard guard : SatelliteBayStatus.Guard.values()) {
            if (guard != SatelliteBayStatus.Guard.NONE) {
                assertTrue(english.containsKey(guard.translationKey()), guard.name());
                assertFalse(chinese.get(guard.translationKey()).isBlank(), guard.name());
            }
        }
        for (String key : List.of("block." + NS + "." + BAY, "screen." + NS + "." + BAY + ".phase",
                "message." + NS + "." + BAY + ".unauthorized", "jei." + NS + "." + BAY)) {
            assertTrue(english.containsKey(key), key);
        }
        for (String locale : List.of("en_us", "zh_cn")) {
            JsonObject lang = JsonParser.parseString(Files.readString(GENERATED.resolve("assets/" + NS
                    + "_v180/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> expected = locale.equals("en_us") ? english : chinese;
            expected.forEach((key, value) -> assertEquals(value, lang.has(key) ? lang.get(key).getAsString() : null,
                    locale + " " + key));
        }
        for (Map.Entry<String, JsonObject> file : V180SatelliteBayData.serverFiles().entrySet()) {
            assertEquals(file.getValue(), JsonParser.parseString(Files.readString(GENERATED.resolve(file.getKey()),
                    StandardCharsets.UTF_8)), file.getKey());
        }
        for (Map.Entry<String, JsonObject> file : V180SatelliteBayData.clientFiles().entrySet()) {
            assertEquals(file.getValue(), JsonParser.parseString(Files.readString(GENERATED.resolve(file.getKey()),
                    StandardCharsets.UTF_8)), file.getKey());
        }
        for (Map.Entry<String, byte[]> file : V180SatelliteBayData.textures().entrySet()) {
            assertTrue(java.util.Arrays.equals(file.getValue(), Files.readAllBytes(GENERATED.resolve(file.getKey()))),
                    file.getKey());
        }
    }
}
