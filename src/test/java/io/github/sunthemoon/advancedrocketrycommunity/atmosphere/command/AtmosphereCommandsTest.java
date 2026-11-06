package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W294 (finding 3-7): the status command uses ADR-066 section 3.1's names, not the internal state's. */
class AtmosphereCommandsTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void aNonBreathableCellIsVacuumOnlyAtZeroAmbientPressure() {
        assertEquals("VACUUM", AtmosphereCommands.label(BreathabilityState.VACUUM, true));
        assertEquals("NON_BREATHABLE_ATMOSPHERE", AtmosphereCommands.label(BreathabilityState.VACUUM, false));
    }

    @Test
    void breathableAndPendingCellsKeepTheirNamesWhateverTheAmbientPressure() {
        for (boolean vacuum : new boolean[] {true, false}) {
            assertEquals("BREATHABLE", AtmosphereCommands.label(BreathabilityState.BREATHABLE, vacuum));
            assertEquals("PENDING", AtmosphereCommands.label(BreathabilityState.PENDING, vacuum));
        }
    }
}
