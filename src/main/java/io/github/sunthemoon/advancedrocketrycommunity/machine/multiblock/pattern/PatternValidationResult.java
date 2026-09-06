package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.List;
import java.util.Objects;

public record PatternValidationResult(
        PatternValidationStatus status,
        List<PatternDiagnostic> diagnostics,
        int inspectedCells
) {
    public static final int MAX_DIAGNOSTICS = 32;

    public PatternValidationResult {
        Objects.requireNonNull(status, "status");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (diagnostics.size() > MAX_DIAGNOSTICS || inspectedCells < 0 || inspectedCells > PatternSize.MAX_CELLS) {
            throw new IllegalArgumentException("validation result exceeds a hard limit");
        }
    }

    public boolean formed() {
        return status == PatternValidationStatus.FORMED;
    }
}
