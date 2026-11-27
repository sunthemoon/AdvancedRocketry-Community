package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElevatorPairViewTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void unboundEndpointUsesTheExistingTranslatedCode() {
        var line = ElevatorMenu.pairLine(ElevatorRules.Check.of(EndgameCode.NOT_BOUND));
        assertTrue(line.translated());
        assertEquals("advancedrocketrycommunity.endgame.view.pair", line.label());
        assertEquals(EndgameCode.NOT_BOUND.translationKey(), line.value());
    }

    @Test void untranslatedFailedRuleRetainsItsDiagnostic() {
        var check = ElevatorRules.Check.rule(ElevatorEndpointCode.NOT_CURRENT_ORBIT);
        var line = ElevatorMenu.pairLine(check);
        assertFalse(line.translated());
        assertEquals(check.describe(), line.value());
    }
}
