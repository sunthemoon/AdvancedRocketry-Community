package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-054 section 11 against the accepted C10 reference vectors ({@code docs/work/v1.7.0-preparation/examples.json}):
 * the Java ports of the reference models take every protocol decision from {@link TransitRules}, enumerate the same
 * crash cuts and lost writes, and must reach exactly the pinned state counts and outcome classes.
 */
final class TransitModelTest {
    private static final JsonObject EXAMPLES = examples();

    private static JsonObject examples() {
        try {
            return JsonParser.parseString(Files.readString(Path.of("docs", "work", "v1.7.0-preparation",
                    "examples.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("The C10 reference vectors are not readable", exception);
        }
    }

    private static JsonObject transit() {
        return EXAMPLES.getAsJsonObject("transit");
    }

    private static List<String> events(JsonArray array) {
        List<String> events = new ArrayList<>();
        array.forEach(element -> events.add(element.getAsString()));
        return events;
    }

    @Test
    void transitIsExactlyOnceWithoutFaults() {
        TransitSourceModel.Exploration result = TransitSourceModel.explore(2, 0, 2);
        for (List<Object> key : result.outcomes().keySet()) {
            int delivered = (Integer) key.get(0);
            int destroyed = (Integer) key.get(1);
            assertFalse((Boolean) key.get(2) || (Boolean) key.get(3), key.toString());
            assertTrue(delivered <= 2, "a duplicate: " + key);
            assertEquals(2, delivered + destroyed, key.toString());
        }
        assertEquals(transit().get("states_two_payloads_two_crashes").getAsInt(), result.states());
    }

    @Test
    void transitLostWriteResidualIsDetected() {
        TransitSourceModel.tombstoneRemoval = false;
        TransitSourceModel.Exploration result;
        try {
            result = TransitSourceModel.explore(2, 1, 1);
        } finally {
            TransitSourceModel.tombstoneRemoval = true;
        }
        for (List<Object> key : result.outcomes().keySet()) {
            int delivered = (Integer) key.get(0);
            boolean rollback = (Boolean) key.get(2);
            boolean faulted = (Boolean) key.get(3);
            assertTrue(delivered + (Integer) key.get(1) >= 1, key.toString());
            if (delivered > 1) {
                assertTrue(faulted && rollback, "an unaudited duplicate: " + key);
            }
            if (!faulted) {
                assertTrue(delivered <= 1 && !rollback, key.toString());
            }
        }
        assertTrue(result.outcomes().containsKey(List.of(2, 0, true, true)), "the documented residual");
        assertEquals(transit().get("states_one_payload_two_crashes_one_lost_write").getAsInt(), result.states());
    }

    @Test
    void transitTombstoneRemovalNeedsAReloadedAbsence() {
        TransitSourceModel.Exploration result = TransitSourceModel.explore(2, 1, 1);
        for (List<Object> key : result.outcomes().keySet()) {
            assertFalse((Integer) key.get(0) > 1 && !(Boolean) key.get(2), "an unaudited duplicate: " + key);
        }
        assertEquals(transit().get("states_tombstone_removal_one_lost_write").getAsInt(), result.states());
    }

    @Test
    void transitCapEvictionKeepsTheLostWriteGuarantee() {
        TransitSourceModel.capEviction = true;
        TransitSourceModel.Exploration clean;
        TransitSourceModel.Exploration faulted;
        try {
            clean = TransitSourceModel.explore(2, 0, 1);
            faulted = TransitSourceModel.explore(2, 1, 1);
            for (JsonElement element : transit().getAsJsonArray("cap_eviction_cuts")) {
                JsonObject cut = element.getAsJsonObject();
                assertCut(cut);
            }
        } finally {
            TransitSourceModel.capEviction = false;
        }
        for (List<Object> key : clean.outcomes().keySet()) {
            assertEquals(List.of(1, false, false), List.of((Integer) key.get(0) + (Integer) key.get(1), key.get(2),
                    key.get(3)), key.toString());
        }
        for (List<Object> key : faulted.outcomes().keySet()) {
            assertTrue((Integer) key.get(0) + (Integer) key.get(1) >= 1, key.toString());
            if ((Integer) key.get(0) > 1) {
                assertTrue((Boolean) key.get(3) && (Boolean) key.get(2), key.toString());
            }
        }
        JsonArray pinned = transit().getAsJsonArray("states_cap_eviction");
        assertEquals(List.of(pinned.get(0).getAsInt(), pinned.get(1).getAsInt()),
                List.of(clean.states(), faulted.states()));
    }

    @Test
    void transitNamedCutsMatchTheReferenceVectors() {
        for (JsonElement element : transit().getAsJsonArray("named_cuts")) {
            assertCut(element.getAsJsonObject());
        }
    }

    private static JsonObject delivery() {
        return EXAMPLES.getAsJsonObject("delivery");
    }

    @Test
    void deliveryGateLeavesOnlyTheContainerTornSaveClass() {
        TransitDeliveryModel.Exploration result = TransitDeliveryModel.explore(true, 2, 0);
        for (List<Object> key : result.outcomes().keySet()) {
            if ((Integer) key.get(0) != 1) {
                assertTrue((Boolean) key.get(1), "a duplicate or loss without a torn container save: " + key);
            }
        }
        assertEquals(delivery().get("states_gated_two_crashes").getAsInt(), result.states());
    }

    @Test
    void deliveryLostWriteResidual() {
        TransitDeliveryModel.Exploration result = TransitDeliveryModel.explore(true, 2, 1);
        for (List<Object> key : result.outcomes().keySet()) {
            if ((Integer) key.get(0) != 1 && !(Boolean) key.get(1)) {
                assertTrue((Boolean) key.get(2), "a non-torn loss or duplicate without a lost write: " + key);
            }
        }
        assertTrue(result.outcomes().containsKey(List.of(0, false, true)), "the documented destination-side loss");
        assertEquals(delivery().get("states_gated_two_crashes_one_lost_write").getAsInt(), result.states());
    }

    @Test
    void deliveryRemovalSettlesOnce() {
        for (JsonElement element : delivery().getAsJsonArray("removal_cuts")) {
            JsonObject cut = element.getAsJsonObject();
            TransitDeliveryModel.State state = TransitDeliveryModel.follow(events(cut.getAsJsonArray("events")), true);
            TransitDeliveryModel.Outcome outcome = TransitDeliveryModel.outcome(state, true);
            JsonArray expected = cut.getAsJsonArray("expected");
            assertEquals(List.of(expected.get(0).getAsInt(), expected.get(1).getAsBoolean()),
                    List.of(outcome.total(), outcome.vanilla()), cut.get("name").getAsString());
        }
    }

    /** Finding F02 of the v1.3-v1.6 deep test: without the gate a withdrawal before a save duplicates. */
    @Test
    void deliveryWithoutTheGateReproducesF02() {
        assertTrue(TransitDeliveryModel.explore(false, 2, 0).outcomes().containsKey(List.of(2, false, false)));
        TransitDeliveryModel.State ungated = TransitDeliveryModel.follow(events(delivery().getAsJsonArray("f02_path")),
                false);
        assertEquals(new TransitDeliveryModel.Outcome(2, false), TransitDeliveryModel.outcome(ungated, false));
        TransitDeliveryModel.State gated = TransitDeliveryModel.follow(
                events(delivery().getAsJsonArray("f02_path_gated")), true);
        assertFalse(TransitDeliveryModel.labels(gated, true).contains("WITHDRAW"),
                "a withdrawal before a chunk save captured the claim");
    }

    private static JsonObject redirect() {
        return EXAMPLES.getAsJsonObject("redirect");
    }

    private static final TransitRedirectModel.Rules FULL = new TransitRedirectModel.Rules(true, true, true);

    /**
     * R2-H1, R3-H1, R3-M1: removal, vanishing, MISSING, a mover, crash restores, redirect, an adversary evicting the
     * tombstone, re-registration and operator resolve: always exactly one delivery, never stuck.
     */
    @Test
    void redirectWithRetirementAndFreezePaysOnce() {
        TransitRedirectModel.Exploration result = TransitRedirectModel.explore(FULL, 2);
        assertEquals(java.util.Set.of(1), result.outcomes().keySet(), result.outcomes().toString());
        assertEquals(redirect().get("states_full_rules_two_crashes").getAsInt(), result.states());
        // R3-L6: D moved its payload, the acknowledgement is durable, D is broken and the server stops before D's
        // chunk saved the move; the retired copy still finds the record, so resolve keeps the payload.
        TransitRedirectModel.State state = TransitRedirectModel.follow(events(redirect().getAsJsonArray(
                "own_move_path")), FULL, 1);
        assertEquals(1, TransitRedirectModel.outcome(state, FULL));
    }

    @Test
    void redirectWithoutTheFreezeReproducesR3H1() {
        TransitRedirectModel.Exploration result = TransitRedirectModel.explore(
                new TransitRedirectModel.Rules(true, false, true), 0);
        assertTrue(result.outcomes().keySet().stream().anyMatch(key -> key != 1), result.outcomes().toString());
    }

    @Test
    void redirectWithoutRetirementReproducesR2H1() {
        TransitRedirectModel.Rules none = new TransitRedirectModel.Rules(false, false, false);
        TransitRedirectModel.Exploration result = TransitRedirectModel.explore(none, 0);
        assertTrue(result.outcomes().keySet().stream().anyMatch(key -> key > 1), result.outcomes().toString());
        TransitRedirectModel.State state = TransitRedirectModel.follow(events(redirect().getAsJsonArray(
                "mover_path")), none, 0);
        assertEquals(2, TransitRedirectModel.outcome(state, none));
    }

    private static void assertCut(JsonObject cut) {
        TransitSourceModel.State state = TransitSourceModel.follow(events(cut.getAsJsonArray("events")));
        TransitSourceModel.Outcome outcome = TransitSourceModel.delivered(TransitSourceModel.drain(state));
        JsonArray expected = cut.getAsJsonArray("expected");
        assertEquals(List.of(expected.get(0).getAsInt(), expected.get(1).getAsInt(), expected.get(2).getAsBoolean()),
                List.of(outcome.delivered(), outcome.destroyed(), outcome.rollback()), cut.get("name").getAsString());
    }
}
