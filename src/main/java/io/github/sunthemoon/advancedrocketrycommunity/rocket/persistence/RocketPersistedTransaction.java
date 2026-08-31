package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import java.util.Objects;
import java.util.UUID;

public record RocketPersistedTransaction(
        RocketTransactionRecord record,
        RocketStructureSnapshot snapshot,
        UUID ownerId
) {
    public RocketPersistedTransaction {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(ownerId, "ownerId");
        if (!record.snapshotId().equals(snapshot.snapshotId())
                || !record.contentHash().equals(snapshot.contentHash())) {
            throw new IllegalArgumentException("Transaction record does not match its snapshot");
        }
    }
}
