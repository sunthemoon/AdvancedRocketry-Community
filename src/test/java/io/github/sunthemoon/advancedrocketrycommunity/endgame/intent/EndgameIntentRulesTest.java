package io.github.sunthemoon.advancedrocketrycommunity.endgame.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-054 section 4: the intent checks in order, settlement while disabled, and per-player spacing. */
final class EndgameIntentRulesTest {
    private static final EndgameAuthority.Decision ALLOWED = new EndgameAuthority.Decision(true, true, EndgameCode.OK);
    private static final EndgameAuthority.Decision REFUSED = new EndgameAuthority.Decision(false, false,
            EndgameCode.UNAUTHORIZED);

    @Test
    void theFirstFailingCheckWins() {
        List<EndgameCode> expected = List.of(EndgameCode.NOT_A_PLAYER, EndgameCode.ROOT_UNAVAILABLE,
                EndgameCode.WRONG_LEVEL, EndgameCode.CHUNK_UNLOADED, EndgameCode.DEVICE_CHANGED, EndgameCode.TOO_FAR,
                EndgameCode.DEVICE_QUARANTINED, EndgameCode.UNAUTHORIZED, EndgameCode.SYSTEM_DISABLED);
        boolean[] fail = new boolean[expected.size()];
        assertEquals(EndgameCode.OK, EndgameIntentRules.check(snapshot(fail, true)));
        List<EndgameCode> observed = new ArrayList<>();
        for (int step = expected.size() - 1; step >= 0; step--) {
            fail[step] = true;
            observed.add(0, EndgameIntentRules.check(snapshot(fail, true)));
        }
        assertEquals(expected, observed);
    }

    @Test
    void settlementIntentsWorkWhileTheSystemIsDisabled() {
        boolean[] fail = new boolean[9];
        fail[8] = true;
        assertEquals(EndgameCode.SYSTEM_DISABLED, EndgameIntentRules.check(snapshot(fail, true)));
        assertEquals(EndgameCode.OK, EndgameIntentRules.check(snapshot(fail, false)));
    }

    @Test
    void stateAndSelectionIntentsHaveSeparateIntervals() {
        EndgameRateLimiter rates = new EndgameRateLimiter();
        UUID player = UUID.randomUUID();
        assertTrue(rates.allow(player, IntentKind.STATE, 100, 10));
        assertFalse(rates.allow(player, IntentKind.STATE, 109, 10));
        assertTrue(rates.allow(player, IntentKind.SELECTION, 109, 2), "selections have their own interval");
        assertFalse(rates.allow(player, IntentKind.SELECTION, 110, 2));
        assertTrue(rates.allow(player, IntentKind.STATE, 110, 10));
        assertTrue(rates.allow(UUID.randomUUID(), IntentKind.STATE, 110, 10), "intervals are per player");
        rates.forget(player);
        assertTrue(rates.allow(player, IntentKind.STATE, 111, 10), "logout forgets the player");
        assertEquals(2, rates.size());
        rates.clear();
        assertEquals(0, rates.size());
    }

    private static EndgameIntentRules.Snapshot snapshot(boolean[] fail, boolean startsOperation) {
        return new EndgameIntentRules.Snapshot(!fail[0], !fail[1], !fail[2], !fail[3], !fail[4], !fail[5], fail[6],
                fail[7] ? REFUSED : ALLOWED, startsOperation, !fail[8]);
    }
}
