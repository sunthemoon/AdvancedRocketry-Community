package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * W363: the gated end-readiness seam holds only the one logical rocket its start property names, and only with the
 * release-test hooks, a dedicated server and an open release-test gate. Every other input is inert and never consults
 * the gate; without the property the seam returns before it touches the server.
 */
final class ReleaseTestBayEndHoldTest {
    private final String property = System.getProperty(ReleaseTestBayEndHold.PROPERTY);
    private final String hooks = System.getProperty(EscrowTestEndpoints.HOOKS);

    @AfterEach void restore() {
        set(ReleaseTestBayEndHold.PROPERTY, property); set(EscrowTestEndpoints.HOOKS, hooks);
    }

    private static void set(String key, String value) {
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }

    @Test void theDecisionHoldsOnlyTheNamedRocketBehindTheWholeGate() {
        UUID logical = UUID.randomUUID(), other = UUID.randomUUID();
        var consulted = new AtomicInteger();
        assertTrue(ReleaseTestBayEndHold.decide(logical.toString(), logical, true, true, () -> true));
        assertFalse(ReleaseTestBayEndHold.decide(logical.toString(), logical, true, true, () -> false), "a closed gate held");
        for (String value : new String[] {null, "", other.toString(), logical.toString().toUpperCase(java.util.Locale.ROOT),
                " " + logical, "true"}) {
            assertFalse(ReleaseTestBayEndHold.decide(value, logical, true, true,
                    () -> { consulted.incrementAndGet(); return true; }), String.valueOf(value));
        }
        assertFalse(ReleaseTestBayEndHold.decide(logical.toString(), logical, false, true,
                () -> { consulted.incrementAndGet(); return true; }), "held without the hooks");
        assertFalse(ReleaseTestBayEndHold.decide(logical.toString(), logical, true, false,
                () -> { consulted.incrementAndGet(); return true; }), "held off a dedicated server");
        assertEquals(0, consulted.get(), "an unselected decision consulted the gate");
    }

    @Test void withoutThePropertyTheSeamNeverTouchesTheServer() {
        System.clearProperty(ReleaseTestBayEndHold.PROPERTY);
        System.setProperty(EscrowTestEndpoints.HOOKS, "true");
        // A null server proves the early return: production reads one absent property and nothing else.
        assertFalse(ReleaseTestBayEndHold.holds(null, UUID.randomUUID()));
        System.setProperty(ReleaseTestBayEndHold.PROPERTY, "");
        assertFalse(ReleaseTestBayEndHold.holds(null, UUID.randomUUID()));
    }
}
