package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/** Lets unit tests in other packages await a detached journal's own writer completion instead of polling a wall clock. */
public final class RocketJournalTestAccess {
    private RocketJournalTestAccess() { }

    /** Pumps the owner side until the submitted generation completes; failures stop after the writer's bounded drain retries. */
    public static void awaitWriter(RocketTransferSavedData journal) { journal.drainForTests(); }

    /**
     * W85 A0: queues a dependent continuation on a detached journal as {@code afterAcknowledged} does, without a server,
     * consuming the caller's reservation (R23).
     */
    public static boolean queue(RocketTransferSavedData journal, RocketTransferSavedData.Reservation reservation,
                                BooleanSupplier action, Runnable discarded) {
        return queue(journal, reservation, false, action, discarded);
    }

    /** {@link #queue}, opting in to run during a failed write as W85's M2 does (Root decision R30). */
    public static boolean queue(RocketTransferSavedData journal, RocketTransferSavedData.Reservation reservation,
                                boolean duringFailure, BooleanSupplier action, Runnable discarded) {
        return journal.queueEffect(reservation, false, duringFailure, action, discarded);
    }

    /**
     * R28: captures one checked generation of a detached journal now, without waiting for it; {@link #awaitWriter} then
     * publishes its completion on the owner thread.
     */
    public static void request(RocketTransferSavedData journal, Path file, CheckedSavedDataPolicy policy) {
        journal.requestWrite(file, policy);
    }

    /** Requests one checked generation of a detached journal, waits for it, then runs its continuations at {@code tick}. */
    public static void write(RocketTransferSavedData journal, Path file, CheckedSavedDataPolicy policy, long tick) {
        journal.requestWrite(file, policy); journal.drainForTests(); journal.pollWriter(tick);
    }
}
