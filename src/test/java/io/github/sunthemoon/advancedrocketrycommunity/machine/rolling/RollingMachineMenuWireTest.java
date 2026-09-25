package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import org.junit.jupiter.api.Test;

class RollingMachineMenuWireTest {
    @Test
    void explicitEnumIdsRoundTripAndUnknownValuesFailClosed() {
        assertEquals(2, RollingMachineMenuWire.formationId(MultiblockFormationState.FORMED));
        assertEquals(2, RollingMachineMenuWire.validationId(PatternValidationStatus.MISMATCH));
        assertEquals(1, RollingMachineMenuWire.diagnosticId(PatternDiagnosticReason.BLOCK_MISMATCH));
        assertEquals(2, RollingMachineMenuWire.processStateId(ProcessMachineState.RUNNING));
        assertEquals(4, RollingMachineMenuWire.failureId(ProcessFailureCode.INSUFFICIENT_ENERGY));

        for (MultiblockFormationState state : MultiblockFormationState.values()) {
            assertEquals(state, RollingMachineMenuWire.formation(
                    RollingMachineMenuWire.formationId(state)
            ));
        }
        for (PatternValidationStatus status : PatternValidationStatus.values()) {
            assertEquals(status, RollingMachineMenuWire.validation(
                    RollingMachineMenuWire.validationId(status)
            ).orElseThrow());
        }
        for (PatternDiagnosticReason reason : PatternDiagnosticReason.values()) {
            assertEquals(reason, RollingMachineMenuWire.diagnostic(
                    RollingMachineMenuWire.diagnosticId(reason)
            ).orElseThrow());
        }
        for (ProcessMachineState state : ProcessMachineState.values()) {
            assertEquals(state, RollingMachineMenuWire.processState(
                    RollingMachineMenuWire.processStateId(state)
            ));
        }
        for (ProcessFailureCode code : ProcessFailureCode.values()) {
            assertEquals(code, RollingMachineMenuWire.failure(
                    RollingMachineMenuWire.failureId(code)
            ));
        }

        assertEquals(MultiblockFormationState.UNFORMED, RollingMachineMenuWire.formation(99));
        assertTrue(RollingMachineMenuWire.validation(99).isEmpty());
        assertTrue(RollingMachineMenuWire.diagnostic(99).isEmpty());
        assertEquals(ProcessMachineState.IDLE, RollingMachineMenuWire.processState(99));
        assertEquals(ProcessFailureCode.NONE, RollingMachineMenuWire.failure(99));
    }

    @Test
    void signedShortTransportPreservesFullWidthProgressAndCoordinates() {
        int[] values = {
                Integer.MIN_VALUE,
                -30_000_001,
                -1,
                0,
                32_768,
                72_000,
                30_000_001,
                Integer.MAX_VALUE
        };
        for (int value : values) {
            int transportedLow = (short) RollingMachineMenuWire.low16(value);
            int transportedHigh = (short) RollingMachineMenuWire.high16(value);
            assertEquals(value, RollingMachineMenuWire.joinInt(transportedLow, transportedHigh));
        }
    }
}
