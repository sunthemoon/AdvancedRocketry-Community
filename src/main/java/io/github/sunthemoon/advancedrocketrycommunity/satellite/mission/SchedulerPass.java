package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

/** One scheduler pass (ADR-050 section 5): completions, queue inspection and pruning. */
public record SchedulerPass(
        long logicalGameTime,
        boolean clockAdvanced,
        int completed,
        int inspectedEntries,
        int staleEntries,
        int remainingScheduled,
        int pruned
) {
    /** Whether the pass changed records (not only the clock). */
    public boolean changed() {
        return completed > 0 || pruned > 0;
    }
}
