package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

/** Persisted phases for an exactly-once final resource commit. */
public enum ProcessJournalPhase {
    PREPARED,
    APPLYING,
    APPLIED
}
