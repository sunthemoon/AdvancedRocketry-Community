package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Optional;
import java.util.UUID;

/** Persistence boundary used to durably order journal writes around resource mutation. */
public interface ProcessJournalStore {
    Optional<ProcessTransactionJournal> load();

    void save(ProcessTransactionJournal journal);

    void clear(UUID transactionId);
}
