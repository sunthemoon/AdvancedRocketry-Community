package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

public record AtmosphereLevelMetrics(
        int trackedVents,
        int activeProviders,
        int activeScanTasks,
        int pendingScanTasks,
        int indexedVolumes,
        int indexedCells,
        int dirtyPositions,
        int lastTickInspections,
        long totalInspections,
        long dirtyOverflows
) {
}
