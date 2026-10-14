package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The K6 item models, imported PNGs, plan and schema-2 provenance agree in source and in the runtime JAR. */
class V180ItemIconResourcesTest {
    private static final String NAMESPACE = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NAMESPACE + "/";
    private static final String UPSTREAM_COMMIT = "c5cd5af62fc07cd4e0d24f06a16033f181c47c04";
    private static final Path PLAN = Path.of("tools/import/v180-k6-plan.json");
    private static final Path RECORD = Path.of("docs/provenance/v1.8.0-c18d-k6.json");
    private static final Map<String, String> MODEL_VERSIONS = Map.of(
            "laser_lens", "v1.8",
            "solar_transmitter_module", "v1.8",
            "data_satellite_package", "v1.8",
            "survey_scanner_module", "v1.8",
            "satellite_package", "v1.8",
            "satellite_control_chip", "v1.8");
    private static final Map<String, String> SOURCES = Map.of(
            "laser_lens", "basiclens",
            "solar_transmitter_module", "microwavetransmitter",
            "data_satellite_package", "opticalsensor",
            "survey_scanner_module", "oremapper",
            "satellite_package", "satellite",
            "satellite_control_chip", "satelliteidchip");

    @ParameterizedTest
    @ValueSource(strings = {"laser_lens", "solar_transmitter_module", "data_satellite_package",
            "survey_scanner_module", "satellite_package", "satellite_control_chip"})
    void eachGeneratedItemModelResolvesExactlyOnePlannedTexture(String item) throws IOException {
        Map<String, JsonObject> planned = entries(json(PLAN));
        assertEquals(MODEL_VERSIONS.size(), planned.size());
        JsonObject model = json(modelPath(item));
        assertEquals("minecraft:item/generated", model.get("parent").getAsString(), item);
        JsonObject textures = model.getAsJsonObject("textures");
        assertEquals(Set.of("layer0"), textures.keySet(), item);
        assertEquals(NAMESPACE + ":item/" + item, textures.get("layer0").getAsString(), item);
        String target = targetPath(item).toString().replace('\\', '/');
        assertNotNull(planned.get(target), "model texture is outside the import plan: " + item);
        assertTrue(Files.isRegularFile(targetPath(item)), item);
    }

    @Test
    void everyImportedTextureHasMatchingPinnedProvenance() throws IOException {
        JsonObject record = json(RECORD);
        assertEquals(2, record.get("schema_version").getAsInt());
        assertEquals("v1.8.0-c18d-k6", record.get("batch_id").getAsString());
        assertEquals(sha256(Files.readAllBytes(PLAN)), record.get("plan_sha256").getAsString());
        Map<String, JsonObject> planned = entries(json(PLAN));
        Map<String, JsonObject> recorded = entries(record);
        assertEquals(planned.keySet(), recorded.keySet());
        for (String item : MODEL_VERSIONS.keySet()) {
            String target = targetPath(item).toString().replace('\\', '/');
            JsonObject entry = recorded.get(target);
            JsonObject planEntry = planned.get(target);
            assertEquals("UPSTREAM_AR_MIT", entry.get("status").getAsString(), item);
            assertEquals("MIT", entry.get("license").getAsString(), item);
            assertEquals("Copyright (c) 2017", entry.get("copyright_notice").getAsString(), item);
            assertEquals("https://github.com/Advanced-Rocketry/AdvancedRocketry",
                    entry.get("source_repository").getAsString(), item);
            assertEquals("1.12", entry.get("source_branch").getAsString(), item);
            assertEquals(UPSTREAM_COMMIT, entry.get("source_commit").getAsString(), item);
            assertEquals("src/main/resources/assets/advancedrocketry/textures/items/" + SOURCES.get(item) + ".png",
                    entry.get("source_path").getAsString(), item);
            assertEquals(planEntry.get("source_sha256"), entry.get("source_sha256"), item);
            assertEquals(entry.get("source_sha256"), entry.get("target_sha256"), "rename changed bytes: " + item);
            assertEquals(sha256(Files.readAllBytes(targetPath(item))), entry.get("target_sha256").getAsString(), item);
            assertEquals(units(planEntry), units(entry), item);
            assertEquals(planEntry.get("asset_coverage_row"), entry.get("asset_coverage_row"), item);
            assertTrue(!entry.getAsJsonArray("upstream_history").isEmpty(), "no upstream history: " + item);
            BufferedImage image = ImageIO.read(targetPath(item).toFile());
            assertNotNull(image, "undecodable PNG: " + item);
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0, item);
        }
    }

    @Test
    void everyDerivationVerdictIsClear() throws IOException {
        for (JsonObject entry : entries(json(RECORD)).values()) {
            JsonObject derivation = entry.getAsJsonObject("derivation").getAsJsonObject("verdicts");
            assertEquals("CLEAR", derivation.get("source").getAsString());
            assertEquals("CLEAR", derivation.get("recorded").getAsString());
        }
    }

    @Test
    void reviewDoesNotClaimApprovalWithoutAReviewer() throws IOException {
        JsonObject review = json(RECORD).getAsJsonObject("review");
        String status = review.get("status").getAsString();
        assertTrue(Set.of("PENDING_HUMAN_REVIEW", "APPROVED").contains(status));
        assertEquals(64, review.get("content_digest").getAsString().length());
        if (status.equals("PENDING_HUMAN_REVIEW")) {
            assertTrue(review.get("reviewer").isJsonNull());
            assertTrue(review.get("reviewed_at").isJsonNull());
        } else {
            assertTrue(!review.get("reviewer").getAsString().isBlank());
            assertTrue(!review.get("reviewed_at").getAsString().isBlank());
        }
    }

    @Test
    void runtimeJarShipsTheSameSixModelsAndTextures() throws IOException {
        String runtimeJar = System.getProperty("arce.runtimeJar");
        assertNotNull(runtimeJar, "Gradle must supply the tested runtime JAR");
        try (JarFile jar = new JarFile(runtimeJar)) {
            for (String item : MODEL_VERSIONS.keySet()) {
                assertJarBytes(jar, ASSETS + "textures/item/" + item + ".png", targetPath(item));
                assertJarBytes(jar, ASSETS + "models/item/" + item + ".json", modelPath(item));
            }
        }
    }

    private static void assertJarBytes(JarFile jar, String name, Path source) throws IOException {
        assertEquals(1, jar.stream().filter(entry -> entry.getName().equals(name)).count(),
                "duplicate or missing JAR resource: " + name);
        var entry = jar.getJarEntry(name);
        assertNotNull(entry, "missing JAR resource: " + name);
        try (var input = jar.getInputStream(entry)) {
            assertArrayEquals(Files.readAllBytes(source), input.readAllBytes(), name);
        }
    }

    private static Path targetPath(String item) {
        return Path.of("src/main/resources", ASSETS, "textures/item", item + ".png");
    }

    private static Path modelPath(String item) {
        return Path.of("src/generated", MODEL_VERSIONS.get(item), "resources", ASSETS, "models/item", item + ".json");
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> units(JsonObject entry) {
        Set<String> units = new TreeSet<>();
        entry.getAsJsonArray("units").forEach(unit -> assertTrue(units.add(unit.getAsString()), "duplicate unit"));
        return units;
    }

    private static Map<String, JsonObject> entries(JsonObject document) {
        Map<String, JsonObject> entries = new HashMap<>();
        for (JsonElement element : document.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            assertTrue(entries.put(entry.get("target_path").getAsString(), entry) == null, "duplicate target");
        }
        return entries;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
