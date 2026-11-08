package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Explicit W277 model supersession checks; older records and art remain immutable. */
final class V180W277ProvenanceAssertions {
    static final Path RECORD = Path.of("docs/provenance/v1.8.0-w277-stand-in-art.json");

    private V180W277ProvenanceAssertions() { }

    static JsonObject replacement(String target) throws IOException {
        JsonObject record = JsonParser.parseString(Files.readString(RECORD)).getAsJsonObject();
        V180OwnerReviewAssertions.assertStandInArtApproval(record.getAsJsonObject("review"));
        for (JsonElement value : record.getAsJsonArray("entries")) {
            JsonObject entry = value.getAsJsonObject();
            if (target.equals(entry.get("target_path").getAsString())) { return entry; }
        }
        return null;
    }

    static void assertCurrentOrExplicitlySuperseded(JsonObject original) throws Exception {
        String target = original.get("target_path").getAsString();
        String actual = sha(Files.readAllBytes(Path.of(target)));
        JsonObject replacement = replacement(target);
        if (replacement == null) { assertEquals(original.get("target_sha256").getAsString(), actual, target); }
        else {
            assertTrue(target.contains("/models/"), "W277 must not supersede earlier art: " + target);
            assertEquals(original.get("target_sha256"), replacement.get("supersedes_target_sha256"), target);
            assertEquals("NEW", replacement.get("status").getAsString());
            assertEquals("MIT", replacement.get("license").getAsString());
            assertEquals(actual, replacement.get("target_sha256").getAsString(), target);
        }
    }

    static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
