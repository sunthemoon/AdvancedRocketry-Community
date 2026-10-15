package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W82: the fluid fuel loader's JSON provenance record against its generated targets and its CLEAR art screen. */
class V180FluidFuelLoaderProvenanceTest {
    private static final Path RECORD = Path.of("docs/provenance/v1.8.0-c17a-fluid-fuel-loader.json");
    private static final String ROOT = "src/generated/v1.8/resources/";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyGeneratedTargetHasANewMitEntryWithItsHash() throws IOException {
        JsonObject record = json(RECORD);
        assertEquals(2, record.get("schema_version").getAsInt());
        assertTrue(Files.isRegularFile(Path.of(record.get("preauthoring_record").getAsString())));
        Set<String> expected = new HashSet<>();
        V180FluidFuelLoaderData.clientFiles().keySet().forEach(path -> expected.add(ROOT + path));
        V180FluidFuelLoaderData.serverFiles().keySet().forEach(path -> expected.add(ROOT + path));
        V180FluidFuelLoaderData.textures().keySet().forEach(path -> expected.add(ROOT + path));
        Set<String> named = new HashSet<>();
        for (JsonElement value : record.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            String target = entry.get("target_path").getAsString();
            assertTrue(named.add(target), "duplicate target " + target);
            assertEquals("NEW", entry.get("status").getAsString(), target);
            assertEquals("MIT", entry.get("license").getAsString(), target);
            assertTrue(entry.get("authoring_commit").getAsString().matches("[0-9a-f]{40}"), target);
            assertTrue(entry.get("source_commit").getAsString().matches("[0-9a-f]{40}"), target);
            assertTrue(entry.get("source_sha256").getAsString().matches("[0-9a-f]{64}"), target);
            assertTrue(Files.isRegularFile(Path.of(entry.get("source_path").getAsString())), target);
            assertTrue(entry.get("generator_class").getAsString().startsWith(
                    "io.github.sunthemoon.advancedrocketrycommunity.datagen.V180"), target);
            assertTrue(entry.getAsJsonArray("transformation").size() > 0, target);
            if (target.endsWith("/lang/en_us.json") || target.endsWith("/lang/zh_cn.json")) {
                // The shared language files change with later merges; the record vouches for its own keys.
                Map<String, String> language = V180FluidFuelLoaderData.language(target.endsWith("zh_cn.json"));
                Set<String> keys = new TreeSet<>();
                entry.getAsJsonArray("new_keys").forEach(key -> keys.add(key.getAsString()));
                assertEquals(new TreeSet<>(language.keySet()), keys, target);
                JsonObject current = json(Path.of(target));
                language.forEach((key, text) -> assertEquals(text, current.get(key).getAsString(), key));
            } else {
                assertTrue(expected.contains(target), "unexpected target " + target);
                assertEquals(V180ArtRedrawTest.sha256(Files.readAllBytes(Path.of(target))),
                        entry.get("target_sha256").getAsString(), target);
            }
        }
        named.removeIf(target -> target.contains("/lang/"));
        assertEquals(expected, named);
        assertDigest(record);
    }

    @Test void theNewTexturesScreenedClearAgainstBothPinnedClients() throws IOException {
        JsonObject screening = json(RECORD).getAsJsonObject("screening");
        assertEquals(sha256(Path.of(screening.get("tool_path").getAsString())), screening.get("tool_sha256").getAsString());
        assertEquals(sha256(Path.of(screening.get("derivation_tool_path").getAsString())),
                screening.get("derivation_tool_sha256").getAsString());
        Path reportPath = Path.of(screening.get("report_path").getAsString());
        assertEquals(sha256(reportPath), screening.get("report_sha256").getAsString());
        JsonObject report = json(reportPath);
        assertEquals(Set.of("CLEAR"), report.getAsJsonObject("counts").keySet(), "no HIT, SUSPECT or other verdict");
        JsonObject files = report.getAsJsonObject("files");
        assertEquals(files.size(), report.getAsJsonObject("counts").get("CLEAR").getAsInt());
        assertEquals(files.size(), screening.get("screened_files").getAsInt());
        Set<String> clients = new HashSet<>();
        report.getAsJsonObject("vanilla").entrySet().forEach(client ->
                clients.add(client.getValue().getAsJsonObject().get("sha256").getAsString()));
        assertEquals(Set.of("1.12.2", "1.20.1"), report.getAsJsonObject("vanilla").keySet());
        assertEquals(Set.of("8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f",
                "56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f"), clients);
        JsonObject targets = screening.getAsJsonObject("screened_targets");
        assertEquals(V180FluidFuelLoaderData.textures().size(), targets.size());
        for (String texture : V180FluidFuelLoaderData.textures().keySet()) {
            assertEquals("CLEAR", files.getAsJsonObject(texture).get("verdict").getAsString(), texture);
            assertEquals(sha256(Path.of(ROOT + texture)), targets.get(ROOT + texture).getAsString(), texture);
        }
    }

    private static void assertDigest(JsonObject record) {
        JsonObject core = record.deepCopy();
        core.remove("review");
        byte[] canonical = (new GsonBuilder().disableHtmlEscaping().create().toJson(core) + "\n")
                .getBytes(StandardCharsets.UTF_8); // the record uses recursively sorted keys
        assertEquals(V180ArtRedrawTest.sha256(canonical), record.getAsJsonObject("review").get("content_digest").getAsString());
        assertEquals("PENDING_HUMAN_REVIEW", record.getAsJsonObject("review").get("status").getAsString());
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String sha256(Path file) throws IOException { return V180ArtRedrawTest.sha256(Files.readAllBytes(file)); }
}
