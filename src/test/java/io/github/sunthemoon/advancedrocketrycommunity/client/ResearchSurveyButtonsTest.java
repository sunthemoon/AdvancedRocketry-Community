package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResearchSurveyButtonsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void unformedAndUnloadedDataDisableBothSurveyActions() {
        for (int action : new int[]{0, 1}) {
            for (var status : new ResearchStatus[]{ResearchStatus.UNFORMED, ResearchStatus.UNLOADED}) {
                assertFalse(ResearchControllerScreen.actionEnabled(action, 1, false, status, 0));
            }
        }
    }

    @Test void missingCellDiagnosticStillDisablesSurveyAfterAStatusReply() {
        for (var status : ResearchStatus.values()) {
            for (int role = 1; role <= 10; role++) {
                assertFalse(ResearchControllerScreen.actionEnabled(0, 1, false, status, role));
                assertFalse(ResearchControllerScreen.actionEnabled(1, 1, false, status, role));
            }
        }
    }

    @Test void formedOwnerKeepsSurveyActionsAndOtherControlsKeepTheirExistingRules() {
        for (var status : ResearchStatus.values()) {
            if (status != ResearchStatus.UNFORMED && status != ResearchStatus.UNLOADED) {
                assertTrue(ResearchControllerScreen.actionEnabled(0, 1, false, status, 0));
                assertTrue(ResearchControllerScreen.actionEnabled(1, 1, false, status, 0));
            }
            for (boolean processor : new boolean[]{false, true}) {
                for (int action = 0; action < 6; action++) {
                    for (int owner : new int[]{0, 2, 3}) {
                        assertFalse(ResearchControllerScreen.actionEnabled(action, owner, processor, status, 0));
                    }
                    if (action == 0 || action == 1) {
                        assertFalse(ResearchControllerScreen.actionEnabled(action, 1, true, status, 0));
                    } else {
                        assertEquals(action != 4 || processor,
                                ResearchControllerScreen.actionEnabled(action, 1, processor, status, 1));
                    }
                }
            }
        }
    }
}
