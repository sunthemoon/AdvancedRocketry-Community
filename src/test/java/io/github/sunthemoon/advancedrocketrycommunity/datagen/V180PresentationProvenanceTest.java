package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ADR-066 section 7.3 per-asset closure for the W16 presentation assets: every committed sound file, the v1.8
 * {@code sounds.json} and the suit component texture has a NEW provenance entry whose target hash matches the
 * committed bytes, and every new language key the record lists exists in both committed language files.
 */
class V180PresentationProvenanceTest {
    private static final Path RECORD = Path.of("docs", "provenance", "v1.8.0-c18d-presentation.json");
    private static final Path ASSETS = Path.of("src", "generated", "v1.8", "resources", "assets");

    @Test
    void everyPresentationAssetHasANewEntryWithItsCommittedHash() throws IOException {
        Map<String, JsonObject> entries = entries();
        Set<String> assets = new TreeSet<>();
        try (Stream<Path> sounds = Files.list(ASSETS.resolve("advancedrocketrycommunity").resolve("sounds"))) {
            sounds.forEach(path -> assets.add(relative(path)));
        }
        assets.add(relative(ASSETS.resolve("advancedrocketrycommunity").resolve("sounds.json")));
        assets.add(relative(ASSETS.resolve("advancedrocketrycommunity").resolve("textures").resolve("entity")
                .resolve("suit_components.png")));
        for (ClassicSound sound : ClassicSound.values()) {
            assertTrue(assets.contains("src/generated/v1.8/resources/assets/advancedrocketrycommunity/sounds/"
                    + sound.id() + ".ogg"), sound.id());
        }
        for (String asset : assets) {
            JsonObject entry = entries.get(asset);
            assertTrue(entry != null, "no provenance entry for " + asset);
            assertEquals("NEW", entry.get("status").getAsString(), asset);
            assertEquals("MIT", entry.get("license").getAsString(), asset);
            assertEquals(sha256(Files.readAllBytes(Path.of(asset))), entry.get("target_sha256").getAsString(), asset);
            assertTrue(Files.isRegularFile(Path.of(entry.get("source_path").getAsString())), asset);
        }
    }

    @Test
    void everyRecordedLanguageKeyIsInBothCommittedFiles() throws IOException {
        Map<String, JsonObject> entries = entries();
        for (String locale : new String[] {"en_us", "zh_cn"}) {
            Path file = ASSETS.resolve("advancedrocketrycommunity_v180").resolve("lang").resolve(locale + ".json");
            JsonObject entry = entries.get(relative(file));
            assertTrue(entry != null, "no provenance entry for " + file);
            JsonObject language = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Set<String> keys = new TreeSet<>();
            entry.getAsJsonArray("new_keys").forEach(key -> keys.add(key.getAsString()));
            assertEquals(new TreeSet<>(V180PresentationLanguage.translations(false).keySet()), keys);
            for (String key : keys) {
                assertTrue(language.has(key), locale + " lacks " + key);
            }
        }
    }

    @Test
    void theReviewStaysPendingWithoutAPrefilledDecision() throws IOException {
        JsonObject review = record().getAsJsonObject("review");
        assertEquals("PENDING_HUMAN_REVIEW", review.get("status").getAsString());
        assertTrue(review.get("reviewer").isJsonNull());
        assertTrue(review.get("reviewed_at").isJsonNull());
        assertEquals(64, review.get("content_digest").getAsString().length());
    }

    private static Map<String, JsonObject> entries() throws IOException {
        Map<String, JsonObject> entries = new HashMap<>();
        for (JsonElement element : record().getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            assertTrue(entries.put(entry.get("target_path").getAsString(), entry) == null, "duplicate entry");
        }
        return entries;
    }

    private static JsonObject record() throws IOException {
        return JsonParser.parseString(Files.readString(RECORD, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** The repository-relative path with forward slashes, as the record writes it. */
    private static String relative(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
