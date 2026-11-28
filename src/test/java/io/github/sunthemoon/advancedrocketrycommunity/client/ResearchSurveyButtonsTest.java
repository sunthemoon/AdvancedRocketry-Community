package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Phase;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResearchSurveyButtonsTest {
    private static final int ACTIVE_PHASE = Phase.ACTIVE.ordinal() + 1;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void unformedAndUnloadedWithoutJobDisableBothSurveyActions() {
        for (int action : new int[]{0, 1}) {
            for (var status : new ResearchStatus[]{ResearchStatus.UNFORMED, ResearchStatus.UNLOADED}) {
                assertFalse(ResearchControllerScreen.actionEnabled(action, 1, false, status, 0, 0));
            }
        }
    }

    @Test void missingCellDiagnosticWithoutJobStillDisablesSurveyAfterAStatusReply() {
        for (var status : ResearchStatus.values()) {
            for (int role = 1; role <= 10; role++) {
                assertFalse(ResearchControllerScreen.actionEnabled(0, 1, false, status, role, 0));
                assertFalse(ResearchControllerScreen.actionEnabled(1, 1, false, status, role, 0));
            }
        }
    }

    @Test void formedOwnerKeepsSurveyActionsAndOtherControlsKeepTheirExistingRules() {
        for (var status : ResearchStatus.values()) {
            if (status != ResearchStatus.UNFORMED && status != ResearchStatus.UNLOADED) {
                assertTrue(ResearchControllerScreen.actionEnabled(0, 1, false, status, 0, ACTIVE_PHASE));
                assertTrue(ResearchControllerScreen.actionEnabled(1, 1, false, status, 0, ACTIVE_PHASE));
            }
            for (boolean processor : new boolean[]{false, true}) {
                for (int action = 0; action < 6; action++) {
                    for (int owner : new int[]{0, 2, 3}) {
                        assertFalse(ResearchControllerScreen.actionEnabled(action, owner, processor, status, 0, ACTIVE_PHASE));
                    }
                    if (action == 0 || action == 1) {
                        assertFalse(ResearchControllerScreen.actionEnabled(action, 1, true, status, 0, ACTIVE_PHASE));
                    } else {
                        assertEquals(action != 4 || processor,
                                ResearchControllerScreen.actionEnabled(action, 1, processor, status, 1, ACTIVE_PHASE));
                    }
                }
            }
        }
    }

    @Test void unformedActiveJobCanBeCancelledButNotStarted() {
        for (var status : new ResearchStatus[]{ResearchStatus.UNFORMED, ResearchStatus.UNLOADED}) {
            for (int role = 0; role <= 10; role++) {
                assertTrue(ResearchControllerScreen.actionEnabled(1, 1, false, status, role, ACTIVE_PHASE));
                assertFalse(ResearchControllerScreen.actionEnabled(0, 1, false, status, role, ACTIVE_PHASE));
            }
        }
    }

    @Test void unformedWithoutJobCannotStartOrCancel() {
        assertFalse(ResearchControllerScreen.actionEnabled(0, 1, false, ResearchStatus.UNFORMED, 2, 0));
        assertFalse(ResearchControllerScreen.actionEnabled(1, 1, false, ResearchStatus.UNFORMED, 2, 0));
    }

    @Test void cancelRequiresTheOwnersActiveObservatoryJobForEveryStatusAndDiagnostic() {
        for (var status : ResearchStatus.values()) {
            for (int role = 0; role <= 10; role++) {
                // Slot 1 uses zero for no job, phase ordinal + 1 for a job, and a three-bit mask.
                for (int phase = 0; phase <= 7; phase++) {
                    assertEquals(phase == ACTIVE_PHASE,
                            ResearchControllerScreen.actionEnabled(1, 1, false, status, role, phase));
                    assertFalse(ResearchControllerScreen.actionEnabled(1, 1, true, status, role, phase));
                    for (int owner : new int[]{0, 2, 3}) {
                        assertFalse(ResearchControllerScreen.actionEnabled(1, owner, false, status, role, phase));
                    }
                }
            }
        }
    }
}
