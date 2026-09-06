package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Optional;
import java.util.UUID;

/** Server-thread adapter for one machine's revisioned resources and replay marker. */
public interface ProcessResourceStore {
    ProcessResourceSnapshot snapshot();

    boolean replaceIfMatches(ProcessResourceSnapshot expected, ProcessResourceSnapshot replacement);

    Optional<UUID> lastAppliedTransactionId();

    void markApplied(UUID transactionId);
}
