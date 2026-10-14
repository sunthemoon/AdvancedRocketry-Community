package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

/** Lets unit tests in other packages await a detached journal's own writer completion instead of polling a wall clock. */
public final class RocketJournalTestAccess {
    private RocketJournalTestAccess() { }

    /** Pumps the owner side until the submitted generation completes; failures stop after the writer's bounded drain retries. */
    public static void awaitWriter(RocketTransferSavedData journal) { journal.drainForTests(); }
}
