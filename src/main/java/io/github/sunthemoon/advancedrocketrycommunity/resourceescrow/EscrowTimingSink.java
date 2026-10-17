package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Optional server-lifetime observer. It never changes store/force/readback ordering. */
@FunctionalInterface
interface EscrowTimingSink {
    EscrowTimingSink NONE = (phase, nanos) -> { };
    void sample(String phase, long nanos);
    default void captured(EscrowLedgerWriter.Snapshot snapshot, long nanos) { }
    default void encoded(EscrowLedgerWriter.Snapshot snapshot, long nanos) { }
    default void queued(long generation, long nanos) { }
    default void writing(long generation) { }
    default void written(long generation, long nanos) { }
    default void acknowledged(long generation) { }
}
