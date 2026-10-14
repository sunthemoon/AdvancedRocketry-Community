package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The W53 redraw record, the carbon brick's added C16d entry and the screen that cleared all five drawings. */
class V180ArtRedrawProvenanceTest {
    private static final Path RECORD = Path.of("docs/provenance/v1.8.0-w53-art-redraw.json");
    private static final Path C16D = Path.of("docs/provenance/v1.8.0-c16d-acquisition.json");
    private static final String ROOT = "src/generated/v1.8/resources/";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void theFourBlockFacesHaveNewMitEntriesThatSupersedeTheirFlaggedDrawings() throws IOException {
        JsonObject record = json(RECORD);
        assertEquals(2, record.get("schema_version").getAsInt());
        Map<String, JsonObject> entries = new HashMap<>();
        for (JsonElement value : record.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            String target = entry.get("target_path").getAsString();
            assertTrue(entries.put(target, entry) == null, "duplicate target " + target);
            assertEquals("NEW", entry.get("status").getAsString(), target);
            assertEquals("MIT", entry.get("license").getAsString(), target);
            assertEquals(sha256(Path.of(target)), entry.get("target_sha256").getAsString(), target);
            assertTrue(entry.get("authoring_commit").getAsString().matches("[0-9a-f]{40}"));
            assertTrue(entry.get("source_commit").getAsString().matches("[0-9a-f]{40}"));
            assertTrue(entry.get("source_sha256").getAsString().matches("[0-9a-f]{64}"));
            assertTrue(Files.isRegularFile(Path.of(entry.get("source_path").getAsString())), target);
            assertTrue(entry.get("generator_class").getAsString().startsWith(
                    "io.github.sunthemoon.advancedrocketrycommunity.datagen.V180"));
            assertTrue(entry.getAsJsonArray("transformation").size() > 0);
            JsonObject supersedes = entry.getAsJsonObject("supersedes");
            String texture = target.substring(ROOT.length());
            assertEquals(V180ArtRedrawTest.FLAGGED.get(texture), supersedes.get("target_sha256").getAsString(), target);
            assertNotEquals(supersedes.get("target_sha256").getAsString(), entry.get("target_sha256").getAsString());
            assertTrue(Files.readString(Path.of(supersedes.get("record").getAsString()))
                    .contains(supersedes.get("target_sha256").getAsString()), "the earlier record keeps the old hash");
        }
        Set<String> expected = new HashSet<>();
        for (String texture : V180ArtRedrawTest.FLAGGED.keySet()) {
            if (!texture.equals(V180ArtRedrawTest.CARBON_BRICK)) { expected.add(ROOT + texture); }
        }
        assertEquals(4, expected.size());
        assertEquals(expected, entries.keySet());
        assertEquals(1, record.getAsJsonArray("related_entries").size());
        JsonObject related = record.getAsJsonArray("related_entries").get(0).getAsJsonObject();
        assertEquals(C16D.toString().replace('\\', '/'), related.get("record").getAsString());
        assertEquals(sha256(Path.of(ROOT + V180ArtRedrawTest.CARBON_BRICK)), related.get("target_sha256").getAsString());
        assertDigest(record);
    }

    @Test void theCarbonBrickRecordKeepsItsFirstEntryAndAddsTheRedraw() throws IOException {
        JsonObject record = json(C16D);
        String target = ROOT + V180ArtRedrawTest.CARBON_BRICK;
        List<JsonObject> carbon = new ArrayList<>();
        for (JsonElement value : record.getAsJsonArray("entries")) {
            if (value.getAsJsonObject().get("target_path").getAsString().equals(target)) { carbon.add(value.getAsJsonObject()); }
        }
        assertEquals(2, carbon.size());
        String flagged = V180ArtRedrawTest.FLAGGED.get(V180ArtRedrawTest.CARBON_BRICK);
        assertEquals(flagged, carbon.get(0).get("target_sha256").getAsString());
        assertEquals("851ac7fd0745b558c90af81dedfc283ea2f5df77", carbon.get(0).get("authoring_commit").getAsString());
        JsonObject redraw = carbon.get(1);
        assertEquals(sha256(Path.of(target)), redraw.get("target_sha256").getAsString());
        assertEquals(flagged, redraw.get("supersedes_target_sha256").getAsString());
        assertEquals(RECORD.toString().replace('\\', '/'), redraw.get("correction_record").getAsString());
        assertEquals("NEW", redraw.get("status").getAsString());
        assertEquals("MIT", redraw.get("license").getAsString());
        assertEquals(json(RECORD).get("authoring_commit").getAsString(), redraw.get("authoring_commit").getAsString());
        assertDigest(record);
    }

    @Test void theWholeGeneratedRootScreenedClearAgainstBothPinnedClients() throws IOException {
        JsonObject screening = json(RECORD).getAsJsonObject("screening");
        assertEquals(sha256(Path.of(screening.get("tool_path").getAsString())), screening.get("tool_sha256").getAsString());
        assertEquals(sha256(Path.of(screening.get("derivation_tool_path").getAsString())),
                screening.get("derivation_tool_sha256").getAsString());
        assertEquals(ROOT, screening.get("root").getAsString() + "/");
        Path reportPath = Path.of(screening.get("report_path").getAsString());
        assertEquals(sha256(reportPath), screening.get("report_sha256").getAsString());
        JsonObject report = json(reportPath);
        JsonObject files = report.getAsJsonObject("files");
        assertEquals(Set.of("CLEAR"), report.getAsJsonObject("counts").keySet(), "no HIT, SUSPECT or other verdict");
        assertEquals(files.size(), report.getAsJsonObject("counts").get("CLEAR").getAsInt());
        assertEquals(files.size(), screening.get("screened_files").getAsInt());
        assertTrue(files.size() >= 187, "the screen covered the whole root");
        Set<String> clients = new HashSet<>();
        report.getAsJsonObject("vanilla").entrySet().forEach(client ->
                clients.add(client.getValue().getAsJsonObject().get("sha256").getAsString()));
        assertEquals(Set.of("1.12.2", "1.20.1"), report.getAsJsonObject("vanilla").keySet());
        assertEquals(Set.of("8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f",
                "56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f"), clients);
        JsonObject targets = screening.getAsJsonObject("screened_targets");
        assertEquals(5, targets.size());
        for (String texture : V180ArtRedrawTest.FLAGGED.keySet()) {
            JsonObject file = files.getAsJsonObject(texture);
            assertNotNull(file, texture);
            assertEquals("CLEAR", file.get("verdict").getAsString(), texture);
            assertEquals(sha256(Path.of(ROOT + texture)), targets.get(ROOT + texture).getAsString(), "screened bytes " + texture);
        }
        // The earlier report reproduces the governance run's five flags on the first drawings.
        Path earlierPath = Path.of(screening.get("earlier_report_path").getAsString());
        assertEquals(sha256(earlierPath), screening.get("earlier_report_sha256").getAsString());
        JsonObject earlier = json(earlierPath);
        assertEquals(1, earlier.getAsJsonObject("counts").get("HIT").getAsInt());
        assertEquals(4, earlier.getAsJsonObject("counts").get("SUSPECT").getAsInt());
        assertEquals("HIT", earlier.getAsJsonObject("files").getAsJsonObject(V180ArtRedrawTest.STATION_LIGHT)
                .get("verdict").getAsString());
        JsonObject review = json(RECORD).getAsJsonObject("review");
        assertEquals("PENDING_HUMAN_REVIEW", review.get("status").getAsString());
        assertTrue(review.get("reviewer").isJsonNull());
        assertEquals(0, review.getAsJsonArray("findings").size());
    }

    private static void assertDigest(JsonObject record) {
        JsonObject core = record.deepCopy();
        core.remove("review");
        byte[] canonical = (new GsonBuilder().disableHtmlEscaping().create().toJson(core) + "\n")
                .getBytes(StandardCharsets.UTF_8); // the records use recursively sorted keys
        assertEquals(V180ArtRedrawTest.sha256(canonical),
                record.getAsJsonObject("review").get("content_digest").getAsString());
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String sha256(Path file) throws IOException { return V180ArtRedrawTest.sha256(Files.readAllBytes(file)); }
}
