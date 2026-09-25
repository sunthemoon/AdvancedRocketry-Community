package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import org.junit.jupiter.api.Test;

class PrecisionAssemblerMenuWireTest {
    @Test
    void explicitStatusIdsRoundTripAndUnknownValuesFailClosed() {
        for (MultiblockFormationState state : MultiblockFormationState.values()) {
            assertEquals(state, PrecisionAssemblerMenuWire.formation(
                    PrecisionAssemblerMenuWire.formationId(state)));
        }
        for (PatternValidationStatus status : PatternValidationStatus.values()) {
            assertEquals(status, PrecisionAssemblerMenuWire.validation(
                    PrecisionAssemblerMenuWire.validationId(status)).orElseThrow());
        }
        for (PatternDiagnosticReason reason : PatternDiagnosticReason.values()) {
            assertEquals(reason, PrecisionAssemblerMenuWire.diagnostic(
                    PrecisionAssemblerMenuWire.diagnosticId(reason)).orElseThrow());
        }
        for (ProcessMachineState state : ProcessMachineState.values()) {
            assertEquals(state, PrecisionAssemblerMenuWire.processState(
                    PrecisionAssemblerMenuWire.processStateId(state)));
        }
        for (ProcessFailureCode code : ProcessFailureCode.values()) {
            assertEquals(code, PrecisionAssemblerMenuWire.failure(
                    PrecisionAssemblerMenuWire.failureId(code)));
        }
        assertEquals(MultiblockFormationState.UNFORMED, PrecisionAssemblerMenuWire.formation(99));
        assertTrue(PrecisionAssemblerMenuWire.validation(99).isEmpty());
        assertTrue(PrecisionAssemblerMenuWire.diagnostic(99).isEmpty());
        assertEquals(ProcessMachineState.IDLE, PrecisionAssemblerMenuWire.processState(99));
        assertEquals(ProcessFailureCode.NONE, PrecisionAssemblerMenuWire.failure(99));
    }

    @Test
    void signedShortTransportPreservesFullWidthNumbers() {
        int[] values = {Integer.MIN_VALUE, -30_000_001, -1, 0, 20_000, 32_768, 72_000,
                30_000_001, Integer.MAX_VALUE};
        for (int value : values) {
            assertEquals(value, PrecisionAssemblerMenuWire.joinInt(
                    (short) PrecisionAssemblerMenuWire.low16(value),
                    (short) PrecisionAssemblerMenuWire.high16(value)));
        }
    }
}
