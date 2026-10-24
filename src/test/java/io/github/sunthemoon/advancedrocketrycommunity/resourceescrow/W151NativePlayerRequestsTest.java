package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class W151NativePlayerRequestsTest {
    private W151NativePlayerRequests requests() { return new W151NativePlayerRequests(n -> new UUID(0, n + 1)); }
    @Test void failedPlayerRequestCanBeRequestedAgainWithAFreshId() {
        var requests = requests(); var first = requests.next(true, null);
        var next = requests.next(true, RocketOperationLedger.Outcome.FAILED);
        assertNotNull(next); assertNotEquals(first, next); assertEquals(2, requests.attempts());
    }
    @Test void activeAndSuccessfulRequestsAreNeverReplaced() {
        var requests = requests(); var first = requests.next(true, null);
        assertNull(requests.next(true, RocketOperationLedger.Outcome.ACTIVE));
        assertNull(requests.next(true, RocketOperationLedger.Outcome.SUCCEEDED));
        assertNull(requests.next(true, null)); assertEquals(first, requests.current()); assertEquals(1, requests.attempts());
    }
    @Test void failedRequestStillWaitsForAuthorityAndRetryReadiness() {
        var requests = requests(); assertNull(requests.next(false, null));
        var first = requests.next(true, null);
        assertNull(requests.next(false, RocketOperationLedger.Outcome.FAILED));
        assertEquals(first, requests.current()); assertEquals(1, requests.attempts());
    }
}
