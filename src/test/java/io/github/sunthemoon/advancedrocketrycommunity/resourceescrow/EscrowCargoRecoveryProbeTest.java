package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoRecoveryProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void recoveryFixturesRequireTheExactCaseAndReleaseProperty() {
        for (String suffix : new String[] {"R1_255", "R1_256", "R2_DUPLICATE", "R3_CURSOR", "R3_OTHER", "R3_RELOCATED", "G1_FUTURE", "G1_INVALID"}) {
            assertTrue(EscrowCargoRecoveryProbe.selected(true, "FC_LOAD_" + suffix));
            assertFalse(EscrowCargoRecoveryProbe.selected(false, "FC_LOAD_" + suffix));
            assertFalse(EscrowCargoRecoveryProbe.selected(true, "IC_LOAD_" + suffix));
            assertFalse(EscrowCargoRecoveryProbe.selected(true, "FC_UNLOAD_" + suffix));
        }
        for (String name : new String[] {null, "", "FC_LOAD_R3", "FC_LOAD_K1"}) {
            assertFalse(EscrowCargoRecoveryProbe.selected(true, name));
        }
    }
    @Test void relocationObservationNeverUsesAnInputPositionOrMovesTheStartFixture() {
        assertEquals(new BlockPos(72, 100, 64), EscrowCargoRecoveryProbe.devicePosition("FC_LOAD_R3_RELOCATED", true));
        assertEquals(new BlockPos(64, 100, 64), EscrowCargoRecoveryProbe.devicePosition("FC_LOAD_R3_RELOCATED", false));
        assertEquals(new BlockPos(64, 100, 64), EscrowCargoRecoveryProbe.devicePosition("FC_LOAD_R3_OTHER", true));
    }
    @Test void recoveryReasonComesFromTheExactTransferFenceNotFlightFailureHistory() {
        var transfer = java.util.UUID.randomUUID();
        String diagnostic = "journal=acknowledged [fenced_transfer=" + transfer + " reason=FLOOR_CONFLICT repair=offline-whole-world]";
        assertTrue(EscrowCargoRecoveryProbe.floorConflict(diagnostic, transfer));
        assertFalse(EscrowCargoRecoveryProbe.floorConflict(diagnostic, java.util.UUID.randomUUID()));
        assertFalse(EscrowCargoRecoveryProbe.floorConflict(diagnostic.replace("FLOOR_CONFLICT", "OTHER_STATE"), transfer));
        assertFalse(EscrowCargoRecoveryProbe.floorConflict("last_failure=FLOOR_CONFLICT", transfer));
    }
}
