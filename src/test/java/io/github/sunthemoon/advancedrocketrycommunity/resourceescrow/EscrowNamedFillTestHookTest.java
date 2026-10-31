package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class EscrowNamedFillTestHookTest {
    @Test void s6O3jNamesRequireBothGatesAndNoOtherS6NameIsAdmitted() {
        for (var name : List.of("LF_S6_O3J_BEFORE", "LF_S6_O3J_REPLACED", "LF_S6_O3J_ACK")) {
            assertFalse(EscrowNamedFillTestHook.allowed(false, name, () -> { fail("Gate read with hooks off"); return true; }));
            assertFalse(EscrowNamedFillTestHook.allowed(true, name, () -> false));
            assertTrue(EscrowNamedFillTestHook.allowed(true, name, () -> true));
        }
        for (var name : List.of("LF_S6_", "LF_S6_O3J", "LF_S6_O3J_BEFORE_typo", "LF_S6_O3J_ACK_typo",
                "LF_S6_O3J_REPLACED_typo", "LF_S6_O1_ACK", "LF_S6_E5_UNLOADED")) {
            assertFalse(EscrowNamedFillTestHook.allowed(true, name, () -> { fail("Gate read for an unselected S6 case"); return true; }));
        }
    }

    @Test void everyNamedControlNeedsBothHooksAndTheReleaseGate() {
        for (var name : List.of("LF_M_NAMED_E5", "LF_M_NAMED_CROSS_K2", "LF_M_NAMED_CROSS_K9",
                "LF_M_R1_FLOORS_255_DISTINCT", "LF_M_R1_FLOORS_255_DISTINCT_ACK", "LF_M_R1_FLOORS_255_COALESCED",
                "LF_M_R1_FLOORS_255_COALESCED_ACK", "LF_M_R1_FLOORS_256")) {
            assertFalse(EscrowNamedFillTestHook.allowed(false, name, () -> { fail("Gate read with hooks off"); return true; }));
            assertFalse(EscrowNamedFillTestHook.allowed(true, name, () -> false));
            assertTrue(EscrowNamedFillTestHook.allowed(true, name, () -> true));
        }
    }

    @Test void aMissingOrUnknownCaseCannotEnableNamedAdmission() {
        for (var name : List.of("", "LF_M_NAMED_", "LF_M_NAMED_CROSS_K9_typo", "LF_M_R1_FLOORS_UNKNOWN", "LF_M_K2")) {
            assertFalse(EscrowNamedFillTestHook.allowed(true, name, () -> { fail("Gate read for an unselected case"); return true; }));
        }
    }
}
