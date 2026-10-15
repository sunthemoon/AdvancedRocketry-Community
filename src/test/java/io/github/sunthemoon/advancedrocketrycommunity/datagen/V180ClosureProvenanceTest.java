package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180ClosureProvenanceTest {
    private static final Path RECORD = Path.of("docs/provenance/v1.8.0-c18d-new-art.json");
    private static final String TEXTURES = "src/generated/v1.8/resources/assets/advancedrocketrycommunity/textures/";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void allSixteenImagesAndThirteenBindingsHaveNewMitEntriesWithActualHashes() throws IOException {
        JsonObject record = record();
        assertEquals(2, record.get("schema_version").getAsInt());
        Map<String, JsonObject> entries = new HashMap<>();
        for (JsonElement value : record.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            String target = entry.get("target_path").getAsString();
            assertTrue(entries.put(target, entry) == null, "duplicate target " + target);
            assertEquals("NEW", entry.get("status").getAsString(), target);
            assertEquals("MIT", entry.get("license").getAsString(), target);
            assertEquals(sha256(Path.of(target)), entry.get("target_sha256").getAsString(), target);
            assertEquals(sha256(Path.of(entry.get("source_path").getAsString())), entry.get("source_sha256").getAsString(), target);
            assertTrue(entry.get("authoring_commit").getAsString().matches("[0-9a-f]{40}"));
            assertTrue(entry.get("source_commit").getAsString().matches("[0-9a-f]{40}"));
            assertTrue(entry.get("generator_class").getAsString().startsWith(
                    "io.github.sunthemoon.advancedrocketrycommunity.datagen.V180"));
            assertTrue(entry.getAsJsonArray("transformation").size() > 0);
        }
        Set<String> expected = new HashSet<>();
        V180ClosureArtTest.textures().forEach(texture -> expected.add(TEXTURES + texture + ".png"));
        V180ClosureModels.bindings().keySet().forEach(path -> expected.add("src/generated/v1.8/resources/" + path));
        assertEquals(29, expected.size());
        assertEquals(expected, entries.keySet());
    }

    @Test void eachOfTheEighteenClassDRowsNamesAFunctionalNewReplacement() throws IOException {
        Set<String> expected = new HashSet<>();
        // The first eight CSV fields are unquoted on class D rows; later evidence fields may contain commas.
        try (var lines = Files.lines(Path.of("docs/work/v1.8.0-c18d-asset-closure/AUDIT-01.csv"))) {
            lines.skip(1).forEach(line -> {
                String[] fields = line.split(",", 9);
                if (fields.length >= 8 && fields[7].equals("D")) { expected.add(fields[0]); }
            });
        }
        Set<String> actual = new HashSet<>();
        Set<String> images = new HashSet<>();
        for (JsonElement value : record().getAsJsonArray("replacements")) {
            JsonObject replacement = value.getAsJsonObject();
            assertTrue(actual.add(replacement.get("audit_source_path").getAsString()), "duplicate audit row");
            String target = replacement.get("target_path").getAsString();
            assertTrue(Files.isRegularFile(Path.of(target)), target);
            assertEquals(sha256(Path.of(target)), replacement.get("target_sha256").getAsString());
            images.add(target);
        }
        assertEquals(18, expected.size());
        assertEquals(expected, actual);
        assertEquals(15, images.size()); // three merged overlay/base pairs; leggings is the companion icon
    }

    @Test void everyNewImageHasAClearScreenAgainstBothPinnedClientsAndHumanReviewStaysOpen() throws IOException {
        JsonObject record = record();
        JsonObject screening = record.getAsJsonObject("screening");
        assertEquals(sha256(Path.of(screening.get("tool_path").getAsString())), screening.get("tool_sha256").getAsString());
        assertEquals(sha256(Path.of(screening.get("derivation_tool_path").getAsString())),
                screening.get("derivation_tool_sha256").getAsString());
        Path reportPath = Path.of(screening.get("report_path").getAsString());
        assertEquals(sha256(reportPath), screening.get("report_sha256").getAsString());
        JsonObject report = JsonParser.parseString(Files.readString(reportPath)).getAsJsonObject();
        assertEquals(16, report.getAsJsonObject("counts").get("CLEAR").getAsInt());
        assertEquals(1, report.getAsJsonObject("counts").size());
        assertEquals(Set.of("1.12.2", "1.20.1"), report.getAsJsonObject("vanilla").keySet());
        Set<String> clients = new HashSet<>();
        report.getAsJsonObject("vanilla").entrySet().forEach(client ->
                clients.add(client.getValue().getAsJsonObject().get("sha256").getAsString()));
        assertEquals(Set.of("8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f",
                "56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f"), clients);
        JsonObject files = report.getAsJsonObject("files");
        assertEquals(16, files.size());
        for (String texture : V180ClosureArtTest.textures()) {
            JsonObject file = files.getAsJsonObject("assets/advancedrocketrycommunity/textures/" + texture + ".png");
            assertNotNull(file, texture);
            assertEquals("CLEAR", file.get("verdict").getAsString(), texture);
        }
        JsonObject review = record.getAsJsonObject("review");
        assertEquals("PENDING_HUMAN_REVIEW", review.get("status").getAsString());
        assertTrue(review.get("reviewer").isJsonNull());
        assertTrue(review.get("reviewed_at").isJsonNull());
        assertEquals(0, review.getAsJsonArray("findings").size());
        JsonObject core = record.deepCopy();
        core.remove("review");
        byte[] canonical = (new GsonBuilder().disableHtmlEscaping().create().toJson(core) + "\n")
                .getBytes(StandardCharsets.UTF_8); // the record uses recursively sorted keys
        assertEquals(sha256(canonical), review.get("content_digest").getAsString());
    }

    private static JsonObject record() throws IOException {
        return JsonParser.parseString(Files.readString(RECORD)).getAsJsonObject();
    }

    private static String sha256(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
