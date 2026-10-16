package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JackhammerFeedbackTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void firstStartIsImmediateAndRepeatsWaitTwentyTicks() {
        assertTrue(JackhammerFeedback.ready(null, 0));
        for (int tick = 100; tick < 120; tick++) { assertFalse(JackhammerFeedback.ready(100, tick)); }
        assertTrue(JackhammerFeedback.ready(100, 120));
        assertTrue(JackhammerFeedback.ready(100, 121));
    }

    @Test void playerClockResetOrIntegerWrapDoesNotPermanentlySuppressFeedback() {
        assertTrue(JackhammerFeedback.ready(100, 0));
        assertFalse(JackhammerFeedback.ready(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertTrue(JackhammerFeedback.ready(Integer.MAX_VALUE, Integer.MIN_VALUE + 19));
    }
}
