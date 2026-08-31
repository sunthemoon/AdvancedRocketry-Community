package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RocketTransactionRecord(
        UUID transactionId,
        RocketTransactionType type,
        RocketTransactionPhase phase,
        UUID snapshotId,
        String contentHash,
        RocketRegion region,
        int progress,
        UUID rocketEntityId
) {
    public RocketTransactionRecord {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(region, "region");
        if (progress < 0) {
            throw new IllegalArgumentException("Transaction progress must not be negative");
        }
    }

    public Optional<UUID> rocketEntityIdOptional() {
        return Optional.ofNullable(rocketEntityId);
    }
}
