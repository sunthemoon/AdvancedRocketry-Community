package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Lets unit tests in other packages await a detached journal's own writer completion instead of polling a wall clock. */
public final class RocketJournalTestAccess {
    private RocketJournalTestAccess() { }

    /** Pumps the owner side until the submitted generation completes; failures stop after the writer's bounded drain retries. */
    public static void awaitWriter(RocketTransferSavedData journal) { journal.drainForTests(); }

    /** W85 A0: queues a dependent continuation on a detached journal as {@code afterAcknowledged} does, without a server. */
    public static boolean queue(RocketTransferSavedData journal, UUID logical, BooleanSupplier action, Runnable discarded) {
        return journal.queueEffect(logical, action, discarded);
    }

    /** Requests one checked generation of a detached journal, waits for it, then runs its continuations at {@code tick}. */
    public static void write(RocketTransferSavedData journal, Path file, CheckedSavedDataPolicy policy, long tick) {
        journal.requestWrite(file, policy); journal.drainForTests(); journal.pollWriter(tick);
    }
}
