package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Read-only timeline for the gated S6 unload diagnostic, not a fairness oracle. */
final class EscrowLoaderS6UnloadObservation {
    static final long WINDOW = 20;
    private long chunkAbsentAt = -1, entitiesUnloadedAt = -1, loaderRemovedAt = -1, rocketRemovedAt = -1;

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
}
