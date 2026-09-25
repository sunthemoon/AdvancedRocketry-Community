package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnostic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationResult;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;

/** Read-only, bounded server snapshot synchronized by vanilla menu data slots. */
final class PrecisionAssemblerMenuData implements ContainerData {
    static final int FORMATION = 0;
    static final int VALIDATION = 1;
    static final int DIAGNOSTIC_REASON = 2;
    static final int DIAGNOSTIC_LOCAL_X = 3;
    static final int DIAGNOSTIC_LOCAL_Y = 4;
    static final int DIAGNOSTIC_LOCAL_Z = 5;
    static final int DIAGNOSTIC_WORLD_X_LOW = 6;
    static final int DIAGNOSTIC_WORLD_X_HIGH = 7;
    static final int DIAGNOSTIC_WORLD_Y_LOW = 8;
    static final int DIAGNOSTIC_WORLD_Y_HIGH = 9;
    static final int DIAGNOSTIC_WORLD_Z_LOW = 10;
    static final int DIAGNOSTIC_WORLD_Z_HIGH = 11;
    static final int PROCESS_STATE = 12;
    static final int PROCESS_FAILURE = 13;
    static final int PROGRESS_LOW = 14;
    static final int PROGRESS_HIGH = 15;
    static final int TOTAL_LOW = 16;
    static final int TOTAL_HIGH = 17;
    static final int ENERGY = 18;
    static final int ENERGY_CAPACITY = 19;
    static final int DIAGNOSTIC_PRESENT = 20;
    static final int INSPECTED_CELLS = 21;
    static final int COUNT = 22;

    private final PrecisionAssemblerBlockEntity controller;

    PrecisionAssemblerMenuData(PrecisionAssemblerBlockEntity controller) {
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
    }

    @Override
    public int get(int index) {
        Optional<PatternValidationResult> validation = controller.lastValidation();
        Optional<PatternDiagnostic> diagnostic = validation.flatMap(result ->
                result.diagnostics().stream().findFirst());
        int progress = controller.processProgress().map(value -> value.progressTicks()).orElse(0);
        int total = controller.totalProcessingTicks();
        return switch (index) {
            case FORMATION -> PrecisionAssemblerMenuWire.formationId(controller.formationState());
            case VALIDATION -> validation.map(value -> PrecisionAssemblerMenuWire.validationId(value.status()))
                    .orElse(0);
            case DIAGNOSTIC_REASON -> diagnostic.map(value -> PrecisionAssemblerMenuWire.diagnosticId(value.reason()))
                    .orElse(0);
            case DIAGNOSTIC_LOCAL_X -> local(diagnostic).x();
            case DIAGNOSTIC_LOCAL_Y -> local(diagnostic).y();
            case DIAGNOSTIC_LOCAL_Z -> local(diagnostic).z();
            case DIAGNOSTIC_WORLD_X_LOW -> PrecisionAssemblerMenuWire.low16(world(diagnostic).x());
            case DIAGNOSTIC_WORLD_X_HIGH -> PrecisionAssemblerMenuWire.high16(world(diagnostic).x());
            case DIAGNOSTIC_WORLD_Y_LOW -> PrecisionAssemblerMenuWire.low16(world(diagnostic).y());
            case DIAGNOSTIC_WORLD_Y_HIGH -> PrecisionAssemblerMenuWire.high16(world(diagnostic).y());
            case DIAGNOSTIC_WORLD_Z_LOW -> PrecisionAssemblerMenuWire.low16(world(diagnostic).z());
            case DIAGNOSTIC_WORLD_Z_HIGH -> PrecisionAssemblerMenuWire.high16(world(diagnostic).z());
            case PROCESS_STATE -> PrecisionAssemblerMenuWire.processStateId(controller.processState());
            case PROCESS_FAILURE -> PrecisionAssemblerMenuWire.failureId(controller.processFailure().code());
            case PROGRESS_LOW -> PrecisionAssemblerMenuWire.low16(progress);
            case PROGRESS_HIGH -> PrecisionAssemblerMenuWire.high16(progress);
            case TOTAL_LOW -> PrecisionAssemblerMenuWire.low16(total);
            case TOTAL_HIGH -> PrecisionAssemblerMenuWire.high16(total);
            case ENERGY -> ports().map(value -> value.energy().storedEnergy()).orElse(0);
            case ENERGY_CAPACITY -> PrecisionAssemblerPortPersistence.ENERGY_CAPACITY;
            case DIAGNOSTIC_PRESENT -> diagnostic.isPresent() ? 1 : 0;
            case INSPECTED_CELLS -> validation.map(PatternValidationResult::inspectedCells).orElse(0);
            default -> throw new IndexOutOfBoundsException("Unknown Precision Assembler menu field " + index);
        };
    }

    @Override
    public void set(int index, int value) {
        // Menu data is only written by the authoritative controller.
    }

    @Override
    public int getCount() {
        return COUNT;
    }

    private Optional<PrecisionAssemblerPortSet> ports() {
        return controller.getLevel() instanceof ServerLevel level
                ? PrecisionAssemblerPortSet.resolve(level, controller) : Optional.empty();
    }

    private static PatternPosition local(Optional<PatternDiagnostic> diagnostic) {
        return diagnostic.map(PatternDiagnostic::localPosition)
                .orElse(new PatternPosition(0, 0, 0));
    }

    private static PatternPosition world(Optional<PatternDiagnostic> diagnostic) {
        return diagnostic.map(PatternDiagnostic::worldPosition)
                .orElse(new PatternPosition(0, 0, 0));
    }
}
