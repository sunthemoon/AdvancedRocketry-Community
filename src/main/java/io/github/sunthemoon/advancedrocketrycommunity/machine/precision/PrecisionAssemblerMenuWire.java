package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import java.util.Optional;

/** Stable menu IDs and signed-short packing for server-owned state. */
final class PrecisionAssemblerMenuWire {
    private PrecisionAssemblerMenuWire() {
    }

    static int formationId(MultiblockFormationState state) {
        return switch (state) {
            case UNFORMED -> 1;
            case FORMED -> 2;
            case WAITING_UNLOADED -> 3;
            case INVALID_DEFINITION -> 4;
            case BINDING_CONFLICT -> 5;
            case UNSUPPORTED_DATA -> 6;
        };
    }

    static MultiblockFormationState formation(int id) {
        return switch (id) {
            case 2 -> MultiblockFormationState.FORMED;
            case 3 -> MultiblockFormationState.WAITING_UNLOADED;
            case 4 -> MultiblockFormationState.INVALID_DEFINITION;
            case 5 -> MultiblockFormationState.BINDING_CONFLICT;
            case 6 -> MultiblockFormationState.UNSUPPORTED_DATA;
            default -> MultiblockFormationState.UNFORMED;
        };
    }

    static int validationId(PatternValidationStatus status) {
        return switch (status) {
            case FORMED -> 1;
            case MISMATCH -> 2;
            case WAITING_UNLOADED -> 3;
            case LIMIT_EXCEEDED -> 4;
            case INVALID_DEFINITION -> 5;
        };
    }

    static Optional<PatternValidationStatus> validation(int id) {
        return switch (id) {
            case 1 -> Optional.of(PatternValidationStatus.FORMED);
            case 2 -> Optional.of(PatternValidationStatus.MISMATCH);
            case 3 -> Optional.of(PatternValidationStatus.WAITING_UNLOADED);
            case 4 -> Optional.of(PatternValidationStatus.LIMIT_EXCEEDED);
            case 5 -> Optional.of(PatternValidationStatus.INVALID_DEFINITION);
            default -> Optional.empty();
        };
    }

    static int diagnosticId(PatternDiagnosticReason reason) {
        return switch (reason) {
            case BLOCK_MISMATCH -> 1;
            case CHUNK_NOT_LOADED -> 2;
            case TRANSFORM_NOT_ALLOWED -> 3;
            case POSITION_OVERFLOW -> 4;
        };
    }

    static Optional<PatternDiagnosticReason> diagnostic(int id) {
        return switch (id) {
            case 1 -> Optional.of(PatternDiagnosticReason.BLOCK_MISMATCH);
            case 2 -> Optional.of(PatternDiagnosticReason.CHUNK_NOT_LOADED);
            case 3 -> Optional.of(PatternDiagnosticReason.TRANSFORM_NOT_ALLOWED);
            case 4 -> Optional.of(PatternDiagnosticReason.POSITION_OVERFLOW);
            default -> Optional.empty();
        };
    }

    static int processStateId(ProcessMachineState state) {
        return switch (state) {
            case IDLE -> 1;
            case RUNNING -> 2;
            case WAITING_INPUT -> 3;
            case WAITING_OUTPUT -> 4;
            case WAITING_ENERGY -> 5;
            case REDSTONE_DISABLED -> 6;
            case INVALID_RECIPE -> 7;
            case UNSUPPORTED_DATA -> 8;
            case RECOVERY_REQUIRED -> 9;
        };
    }

    static ProcessMachineState processState(int id) {
        return switch (id) {
            case 2 -> ProcessMachineState.RUNNING;
            case 3 -> ProcessMachineState.WAITING_INPUT;
            case 4 -> ProcessMachineState.WAITING_OUTPUT;
            case 5 -> ProcessMachineState.WAITING_ENERGY;
            case 6 -> ProcessMachineState.REDSTONE_DISABLED;
            case 7 -> ProcessMachineState.INVALID_RECIPE;
            case 8 -> ProcessMachineState.UNSUPPORTED_DATA;
            case 9 -> ProcessMachineState.RECOVERY_REQUIRED;
            default -> ProcessMachineState.IDLE;
        };
    }

    static int failureId(ProcessFailureCode code) {
        return switch (code) {
            case NONE -> 0;
            case MISSING_ITEM_INPUT -> 1;
            case MISSING_FLUID_INPUT -> 2;
            case OUTPUT_BLOCKED -> 3;
            case INSUFFICIENT_ENERGY -> 4;
            case REDSTONE_DISABLED -> 5;
            case INVALID_RECIPE -> 6;
            case STALE_TRANSACTION -> 7;
            case JOURNAL_CONFLICT -> 8;
            case RECOVERY_DIVERGED -> 9;
            case ARITHMETIC_OVERFLOW -> 10;
        };
    }

    static ProcessFailureCode failure(int id) {
        return switch (id) {
            case 1 -> ProcessFailureCode.MISSING_ITEM_INPUT;
            case 2 -> ProcessFailureCode.MISSING_FLUID_INPUT;
            case 3 -> ProcessFailureCode.OUTPUT_BLOCKED;
            case 4 -> ProcessFailureCode.INSUFFICIENT_ENERGY;
            case 5 -> ProcessFailureCode.REDSTONE_DISABLED;
            case 6 -> ProcessFailureCode.INVALID_RECIPE;
            case 7 -> ProcessFailureCode.STALE_TRANSACTION;
            case 8 -> ProcessFailureCode.JOURNAL_CONFLICT;
            case 9 -> ProcessFailureCode.RECOVERY_DIVERGED;
            case 10 -> ProcessFailureCode.ARITHMETIC_OVERFLOW;
            default -> ProcessFailureCode.NONE;
        };
    }

    static int low16(int value) {
        return value & 0xFFFF;
    }

    static int high16(int value) {
        return value >>> 16;
    }

    static int joinInt(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }
}
