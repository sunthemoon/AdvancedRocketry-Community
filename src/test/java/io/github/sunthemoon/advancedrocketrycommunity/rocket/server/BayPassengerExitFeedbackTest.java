package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** W242-R6 and R10: outcome mapping, grants and the two languages' exit messages. */
final class BayPassengerExitFeedbackTest {
    private static final boolean[] BOTH = {false, true};
    private static final Map<Status, String> REFUSALS = new EnumMap<>(Map.of(Status.EXIT_BUDGET_EXHAUSTED, "budget_exhausted",
            Status.REENTRANT_REFUSED, "reentrant_refused", Status.CAPACITY_REFUSED, "capacity_refused",
            Status.STALE, "stale", Status.PENDING, "pending"));
    private static final Set<String> NO_SAFE_EXIT_KEYS = Set.of("fall_offer", "authority_unavailable", "capacity_or_used", "no_safe_exit");

    @Test void onlyNoSafeExitWithAnAdmittedRowAndTheLandingObserversGrants() {
        for (Status status : Status.values()) { for (boolean admitted : BOTH) { for (boolean landing : BOTH) {
            assertEquals(status == Status.NO_SAFE_EXIT && admitted && landing,
                    BayPassengerExitFeedback.grants(status, admitted, landing), status + " admitted=" + admitted + " landing=" + landing);
        } } }
    }

    @Test void everyOtherStatusHasItsOwnKeyWhateverTheAuthorityRowOrOffer() {
        assertEquals(Set.of(Status.values()).size() - 2, REFUSALS.size());
        for (var refusal : REFUSALS.entrySet()) { for (boolean authority : BOTH) { for (boolean admitted : BOTH) { for (boolean offered : BOTH) {
            assertEquals(refusal.getValue(), BayPassengerExitFeedback.outcome(refusal.getKey(), authority, admitted, offered),
                    refusal.getKey() + " authority=" + authority + " admitted=" + admitted + " offered=" + offered);
        } } } }
        assertEquals(REFUSALS.size(), new HashSet<>(REFUSALS.values()).size());
        assertTrue(REFUSALS.values().stream().noneMatch(key -> key.equals("safe") || NO_SAFE_EXIT_KEYS.contains(key)));
        assertEquals("reentrant_refused", BayPassengerExitFeedback.REENTRANT_REFUSED);
    }

    @Test void safeAndNoSafeExitKeepTheirExplainedVariants() {
        for (boolean authority : BOTH) { for (boolean admitted : BOTH) { for (boolean offered : BOTH) {
            assertEquals("safe", BayPassengerExitFeedback.outcome(Status.SAFE, authority, admitted, offered));
        } } }
        assertEquals("fall_offer", BayPassengerExitFeedback.outcome(Status.NO_SAFE_EXIT, true, true, true));
        assertEquals("authority_unavailable", BayPassengerExitFeedback.outcome(Status.NO_SAFE_EXIT, false, false, false));
        assertEquals("capacity_or_used", BayPassengerExitFeedback.outcome(Status.NO_SAFE_EXIT, true, false, false));
        assertEquals("no_safe_exit", BayPassengerExitFeedback.outcome(Status.NO_SAFE_EXIT, true, true, false));
    }

    @Test void everyOutcomeHasEnglishAndChineseTextAndOnlyNoSafeExitSaysSo() throws Exception {
        var keys = new TreeSet<String>(List.of(BayPassengerExitFeedback.EXIT_CAPACITY,
                BayPassengerExitFeedback.AUTHORITY_UNAVAILABLE, BayPassengerExitFeedback.REENTRANT_REFUSED));
        for (Status status : Status.values()) { for (boolean authority : BOTH) { for (boolean admitted : BOTH) { for (boolean offered : BOTH) {
            keys.add(BayPassengerExitFeedback.outcome(status, authority, admitted, offered));
        } } } }
        JsonObject english = language("en_us"), chinese = language("zh_cn");
        for (String key : keys) {
            String id = "message.advancedrocketrycommunity.bay_exit." + key;
            assertTrue(english.has(id) && chinese.has(id), "Missing translation: " + id);
            boolean noSafe = Set.of("fall_offer", "capacity_or_used", "no_safe_exit").contains(key);
            assertEquals(noSafe, english.get(id).getAsString().startsWith("No safe position found"), id);
            assertEquals(noSafe, chinese.get(id).getAsString().startsWith("未找到安全位置"), id);
            assertFalse(chinese.get(id).getAsString().contains(" 已有座位"), "ASCII space before the seat sentence: " + id);
            assertFalse(chinese.get(id).getAsString().contains("尖刺"), "Pointed dripstone is 滴水石锥: " + id);
        }
        assertTrue(chinese.get("message.advancedrocketrycommunity.bay_exit.fall_offer").getAsString().contains("滴水石锥"));
        assertEquals(english.keySet(), chinese.keySet());
        assertEquals(keys, new TreeSet<>(english.keySet().stream().map(id -> id.substring(id.lastIndexOf('.') + 1)).toList()),
                "Each language entry is reachable and each reachable key has an entry");
    }

    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/generated/v1.8/resources/assets/"
                + "advancedrocketrycommunity_v180_bay_exit/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
