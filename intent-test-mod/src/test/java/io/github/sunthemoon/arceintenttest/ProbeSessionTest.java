package io.github.sunthemoon.arceintenttest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProbeSessionTest {
    @Test void disabledAndExpiredCapturesNeverConsumeBudget() {
        var session = new ProbeSession();
        assertThrows(IllegalStateException.class, () -> session.claim(0, 0));
        session.enable();
        assertThrows(IllegalStateException.class, () -> session.claim(1, 2));
        assertThrows(IllegalStateException.class, () -> session.claim(ProbeSession.CAPTURE_LIFETIME_NANOS + 1, 0));
        assertEquals(0, session.sent());
        session.claim(ProbeSession.CAPTURE_LIFETIME_NANOS, 0);
        assertEquals(1, session.sent());
    }

    @Test void exactCooldownIsRequiredAcrossAllCasesAndEnableDoesNotResetIt() {
        var session = new ProbeSession();
        session.enable();
        session.claim(0, 0);
        session.disable();
        assertThrows(IllegalStateException.class, () -> session.claim(0, 0));
        session.enable();
        assertThrows(IllegalStateException.class, () -> session.claim(ProbeSession.COOLDOWN_NANOS - 1, 0));
        assertThrows(IllegalStateException.class, () -> session.claim(-1, -1));
        assertEquals(1, session.sent());
        session.claim(ProbeSession.COOLDOWN_NANOS, 0);
        assertEquals(2, session.sent());
    }

    @Test void connectionBudgetIsFiniteAndNewConnectionStartsDisabled() {
        var session = new ProbeSession();
        session.enable();
        for (int i = 0; i < 32; i++) {
            session.claim(i * ProbeSession.COOLDOWN_NANOS, i * ProbeSession.COOLDOWN_NANOS);
        }
        session.disable(); session.enable();
        assertEquals(32, session.sent());
        assertThrows(IllegalStateException.class, () -> session.claim(64_000_000_000L, 64_000_000_000L));
        assertThrows(IllegalStateException.class, () -> new ProbeSession().claim(0, 0));
    }
}
