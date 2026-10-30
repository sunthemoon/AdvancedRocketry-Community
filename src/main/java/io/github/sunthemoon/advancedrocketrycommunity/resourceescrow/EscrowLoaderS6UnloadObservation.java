package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Read-only timeline for the gated S6 unload diagnostic, not a fairness oracle. */
final class EscrowLoaderS6UnloadObservation {
    static final long WINDOW = 20;
    static final long REMOVAL_WAIT_TICKS = 20;
    private long chunkAbsentAt = -1, entitiesUnloadedAt = -1, loaderRemovedAt = -1, rocketRemovedAt = -1;
    private long removalWaitAt = -1;
    private boolean completed;

    void observe(long tick, boolean chunkAbsent, boolean entitiesUnloaded, boolean loaderRemoved, boolean rocketRemoved) {
        if (chunkAbsent && chunkAbsentAt < 0) { chunkAbsentAt = tick; }
        if (entitiesUnloaded && entitiesUnloadedAt < 0) { entitiesUnloadedAt = tick; }
        if (loaderRemoved && loaderRemovedAt < 0) { loaderRemovedAt = tick; }
        if (rocketRemoved && rocketRemovedAt < 0) { rocketRemovedAt = tick; }
    }

    long chunkAbsentAt() { return chunkAbsentAt; }
    long entitiesUnloadedAt() { return entitiesUnloadedAt; }
    long loaderRemovedAt() { return loaderRemovedAt; }
    long rocketRemovedAt() { return rocketRemovedAt; }
    boolean windowComplete(long tick) {
        return chunkAbsentAt >= 0 && entitiesUnloadedAt >= 0
                && tick - Math.max(chunkAbsentAt, entitiesUnloadedAt) >= WINDOW;
    }

    boolean awaitComplete(long tick, boolean chunkAbsent, boolean entitiesUnloaded, boolean loaderRemoved, boolean rocketRemoved) {
        observe(tick, chunkAbsent, entitiesUnloaded, loaderRemoved, rocketRemoved);
        if (removalWaitAt < 0 && chunkAbsent && entitiesUnloaded) { removalWaitAt = tick; }
        boolean all = chunkAbsent && entitiesUnloaded && loaderRemoved && rocketRemoved;
        if (completed && !all) { throw new IllegalStateException("S6 complete unloaded state was lost"); }
        if (!completed && removalWaitAt >= 0 && (tick - removalWaitAt > REMOVAL_WAIT_TICKS
                || tick - removalWaitAt == REMOVAL_WAIT_TICKS && !all)) {
            throw new IllegalStateException("S6 endpoint removal did not complete within 20 ticks of spatial unload");
        }
        completed |= all;
        return all;
    }
}
