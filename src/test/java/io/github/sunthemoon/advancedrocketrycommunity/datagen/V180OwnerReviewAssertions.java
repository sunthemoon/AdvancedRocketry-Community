package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Set;

/** Exact D7 decision metadata; content-digest and resource checks remain in each caller. */
final class V180OwnerReviewAssertions {
    private V180OwnerReviewAssertions() { }

    static void assertD7Approval(JsonObject review) {
        assertEquals(Set.of("status", "content_digest", "reviewer", "reviewed_at", "findings", "basis"), review.keySet());
        assertEquals("APPROVED", review.get("status").getAsString());
        assertEquals("sunthemoon", review.get("reviewer").getAsString());
        assertEquals("2026-11-04", review.get("reviewed_at").getAsString());
        assertEquals(0, review.getAsJsonArray("findings").size());
        assertTrue(review.get("content_digest").getAsString().matches("[0-9a-f]{64}"));
        String basis = review.get("basis").getAsString();
        assertTrue(basis.contains("docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md"));
        assertTrue(basis.contains("D7"));
        assertTrue(basis.contains("521cb9b95"));
    }
}
