package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The five NEW textures W53 redrew after the generated-art screen flagged their first drawings. */
class V180ArtRedrawTest {
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final Path ROOT = Path.of("src/generated/v1.8/resources");
    static final String STATION_LIGHT = ASSETS + "textures/block/station_light.png";
    static final String PROJECTOR_SIDE = ASSETS + "textures/block/force_field_projector_side.png";
    static final String ARRAY_SIDE = ASSETS + "textures/block/solar_array_side.png";
    static final String GENERATOR_SIDE = ASSETS + "textures/block/solar_generator_side.png";
    static final String CARBON_BRICK = ASSETS + "textures/item/carbon_brick.png";
    /** SHA-256 of each flagged first drawing (HIT for the station light, SUSPECT for the others). */
    static final Map<String, String> FLAGGED = Map.of(
            STATION_LIGHT, "b0bcfe326ce7192703cebffb095ead73f0212c62263e0af500ef97f7525ec251",
            PROJECTOR_SIDE, "bda31a9d7d9362768d2a44f6822c31bc217402adbd10100d3e3ac695345ac6aa",
            ARRAY_SIDE, "79fe126fdbcfb67e99eafaa12454e1cd5221571e0a61c70ed6401c789ac95efa",
            GENERATOR_SIDE, "dde24fae2f0bdf8fcde04e919c373c425203b0ad6023e74f22c58c9e426b44cc",
            CARBON_BRICK, "2891ed4284903c9da5e5d328a0016c414f59cd03ab25c58af9c169198c3fce2f");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static Map<String, Supplier<byte[]>> providers() {
        Map<String, Supplier<byte[]>> providers = new LinkedHashMap<>();
        providers.put(STATION_LIGHT, V180StationLightData::texture);
        providers.put(PROJECTOR_SIDE, () -> V180ForceFieldData.textures().get("force_field_projector_side"));
        providers.put(ARRAY_SIDE, () -> V180SolarArrayData.textures().get(ARRAY_SIDE));
        providers.put(GENERATOR_SIDE, () -> V180SolarData.textures().get(GENERATOR_SIDE));
        providers.put(CARBON_BRICK, V180CraftingComponentData::carbonTexture);
        return providers;
    }

    @Test void eachRedrawnTextureIsTheCommittedGeneratedFileAndNotItsFlaggedDrawing() throws IOException {
        assertEquals(FLAGGED.keySet(), providers().keySet());
        for (var provider : providers().entrySet()) {
            String path = provider.getKey();
            byte[] png = provider.getValue().get();
            assertArrayEquals(png, provider.getValue().get(), path);
            assertArrayEquals(png, Files.readAllBytes(ROOT.resolve(path)), path);
            assertNotEquals(FLAGGED.get(path), sha256(png), path + " is still the flagged drawing");
        }
    }

    @Test void blockFacesStayOpaqueAndTheCarbonBrickKeepsItsTransparentMargin() {
        Map<String, int[]> pixels = Map.of(
                STATION_LIGHT, V180MaterialArt.pixels(V180StationLightData.grid(), 0xE8EDF2),
                ARRAY_SIDE, V180MaterialArt.pixels(V180SolarArrayData.grids().get("solar_array_side"),
                        V180SolarArrayData.tint("solar_array_side")),
                GENERATOR_SIDE, V180MaterialArt.pixels(V180SolarData.grids().get("solar_generator_side"),
                        V180SolarData.tint("solar_generator_side")));
        pixels.forEach((path, face) -> {
            assertEquals(256, face.length, path);
            for (int pixel : face) { assertEquals(255, pixel >>> 24, path); }
        });
        int[] carbon = V180MaterialArt.pixels(V180CraftingComponentData.carbonGrid(), 0x747D88);
        int opaque = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int alpha = carbon[y * 16 + x] >>> 24;
                if (x == 0 || y == 0 || x == 15 || y == 15) { assertEquals(0, alpha, "margin " + x + "," + y); }
                if (alpha == 255) { opaque++; } else { assertEquals(0, alpha); }
            }
        }
        assertTrue(opaque >= 96 && opaque < 196, "the bar fills a readable share of the icon: " + opaque);
    }

    @Test void theSolarArraySideGridIsFixedWhileTheOtherArrayTexturesKeepTheirFormula() {
        String[] side = V180SolarArrayData.grids().get("solar_array_side");
        side[9] = "................";
        assertEquals("2222332222233222", V180SolarArrayData.grids().get("solar_array_side")[9]);
        assertEquals(List.of("solar_array_front", "solar_array_top", "solar_array_side", "solar_array_panel",
                "power_output_plug"), List.copyOf(V180SolarArrayData.grids().keySet()));
        // The conduit rows run edge to edge, so neighbouring faces of a row of panels join up.
        for (String row : List.of(side).subList(9, 13)) { assertEquals(row.charAt(0), row.charAt(15)); }
    }

    @Test void everyModelStillBindsTheRedrawnTexturesInTheirFormerRoles() {
        assertEquals("advancedrocketrycommunity:block/station_light", textures(V180StationLightData.clientFiles()
                .get(ASSETS + "models/block/station_light.json")).get("all").getAsString());
        JsonObject projector = textures(V180ForceFieldData.clientFiles().get(ASSETS + "models/block/force_field_projector.json"));
        for (String face : List.of("top", "side")) {
            assertEquals("advancedrocketrycommunity:block/force_field_projector_side", projector.get(face).getAsString());
        }
        JsonObject array = textures(V180SolarArrayData.clientFiles().get(ASSETS + "models/block/solar_array.json"));
        for (String face : List.of("particle", "down", "east", "west", "south")) {
            assertEquals("advancedrocketrycommunity:block/solar_array_side", array.get(face).getAsString(), face);
        }
        JsonObject panel = textures(V180SolarArrayData.clientFiles().get(ASSETS + "models/block/solar_array_panel.json"));
        for (String face : List.of("bottom", "side")) {
            assertEquals("advancedrocketrycommunity:block/solar_array_side", panel.get(face).getAsString(), face);
        }
        JsonObject generator = textures(V180SolarData.clientFiles().get(ASSETS + "models/block/solar_generator.json"));
        for (String face : List.of("particle", "north", "south", "east", "west")) {
            assertEquals("advancedrocketrycommunity:block/solar_generator_side", generator.get(face).getAsString(), face);
        }
        assertEquals("advancedrocketrycommunity:item/carbon_brick", textures(V180CraftingComponentData.clientFiles()
                .get(ASSETS + "models/item/carbon_brick.json")).get("layer0").getAsString());
    }

    private static JsonObject textures(JsonObject model) { return model.getAsJsonObject("textures"); }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
