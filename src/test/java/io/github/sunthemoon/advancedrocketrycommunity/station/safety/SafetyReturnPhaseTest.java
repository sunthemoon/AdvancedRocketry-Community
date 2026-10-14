package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** ADR-066 section 3.3: 100 consecutive adrift ticks, one attempt per 200 ticks, and the clearing rules. */
final class SafetyReturnPhaseTest {
    @Test
    void anAttemptNeedsOneHundredConsecutiveAdriftTicks() {
        SafetyReturnPhase phase = new SafetyReturnPhase();
        for (int tick = 1; tick < SafetyReturnPhase.ADRIFT_TICKS; tick++) {
            assertFalse(phase.tick(true, tick), "tick " + tick);
        }
        assertTrue(phase.tick(true, SafetyReturnPhase.ADRIFT_TICKS));
        assertEquals(SafetyReturnPhase.ADRIFT_TICKS, phase.adriftTicks());
    }

    @Test
    void aTickThatIsNotAdriftRestartsTheCount() {
        SafetyReturnPhase phase = new SafetyReturnPhase();
        for (int tick = 1; tick <= 99; tick++) {
            phase.tick(true, tick);
        }
        assertFalse(phase.tick(false, 100), "Entering a region, a vehicle or another Level clears the phase");
        assertEquals(0, phase.adriftTicks());
        for (int tick = 101; tick < 200; tick++) {
            assertFalse(phase.tick(true, tick));
        }
        assertTrue(phase.tick(true, 200));
    }

    @Test
    void attemptsAreSpacedByTwoHundredTicksEvenAcrossAClear() {
        SafetyReturnPhase phase = new SafetyReturnPhase();
        long now = 0L;
        for (int tick = 0; tick < SafetyReturnPhase.ADRIFT_TICKS; tick++) {
            now++;
            phase.tick(true, now);
        }
        assertTrue(phase.tick(true, now));
        phase.attempted(now);
        long attempt = now;
        while (now < attempt + SafetyReturnPhase.ATTEMPT_SPACING_TICKS - 1) {
            now++;
            assertFalse(phase.tick(true, now), "Too early at " + (now - attempt));
        }
        phase.clear();
        now++;
        assertFalse(phase.tick(true, now), "A clear restarts the 100 ticks");
        for (int tick = 1; tick < SafetyReturnPhase.ADRIFT_TICKS; tick++) {
            now++;
            phase.tick(true, now);
        }
        assertTrue(now - attempt >= SafetyReturnPhase.ATTEMPT_SPACING_TICKS);
        assertTrue(phase.tick(true, now));
    }

    @Test
    void theSpacingHoldsWhileTheAdriftPhaseContinues() {
        SafetyReturnPhase phase = new SafetyReturnPhase();
        for (int tick = 1; tick <= 100; tick++) {
            phase.tick(true, tick);
        }
        phase.attempted(100);
        assertFalse(phase.tick(true, 299));
        assertTrue(phase.tick(true, 300));
        assertTrue(phase.tick(true, 50), "A clock that moved backwards does not block forever");
    }
}
