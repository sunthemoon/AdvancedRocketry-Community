package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

public record CoordinatorTickReport(
        int inspections,
        int activeTasks,
        int pendingTasks,
        int completedScans,
        int mergedTasks
) {
    public CoordinatorTickReport {
        if (inspections < 0 || activeTasks < 0 || pendingTasks < 0
                || completedScans < 0 || mergedTasks < 0) {
            throw new IllegalArgumentException("Coordinator metrics cannot be negative");
        }
    }
}
