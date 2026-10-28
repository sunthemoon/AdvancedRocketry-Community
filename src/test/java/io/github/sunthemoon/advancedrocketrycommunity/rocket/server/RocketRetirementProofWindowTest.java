package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketRetirementProofWindowTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void leavesBothTheProofDeadlineAndTheExistingRetainedUseBeforeAutosave() {
        int window = RocketOrdinaryRetirement.ATTEMPT_TICKS + 40;
        assertFalse(RocketRetirementCoverageGameTests.sharedProofWindowAvailable(6_000 - window));
        assertTrue(RocketRetirementCoverageGameTests.sharedProofWindowAvailable(6_000 - window - 1));
        assertFalse(RocketRetirementCoverageGameTests.sharedProofWindowAvailable(29_946));
        assertFalse(RocketRetirementCoverageGameTests.sharedProofWindowAvailable(30_000));
    }

    @Test
    void admitsAfterThePeriodicSaveWithoutChangingTheClock() {
        assertTrue(RocketRetirementCoverageGameTests.sharedProofWindowAvailable(6_001));
        assertTrue(RocketRetirementCoverageGameTests.sharedProofWindowAvailable(30_001));
    }
}
