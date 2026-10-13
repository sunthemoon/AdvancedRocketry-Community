package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Optional server-lifetime observer. It never changes store/force/readback ordering. */
@FunctionalInterface
interface EscrowTimingSink {
    EscrowTimingSink NONE = (phase, nanos) -> { };
    void sample(String phase, long nanos);
}
