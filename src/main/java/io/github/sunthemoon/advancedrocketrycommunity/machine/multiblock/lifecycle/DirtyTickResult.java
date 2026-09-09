package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

public record DirtyTickResult(
        int pendingBefore,
        int processedControllers,
        int processedCells,
        int pendingAfter
) {
    public DirtyTickResult {
        if (pendingBefore < 0 || processedControllers < 0 || processedCells < 0 || pendingAfter < 0) {
            throw new IllegalArgumentException("dirty tick counters cannot be negative");
        }
    }
}
