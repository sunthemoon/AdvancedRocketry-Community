package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Result of one permission-gated, bounded operator recovery attempt. */
public record RocketTransferRecoveryReport(
        Status status,
        UUID transferId,
        Optional<RocketTransferPhase> phase,
        Optional<RocketTransferRecoveryAction> action,
        int sourceMatches,
        int destinationMatches
) {
    public enum Status {
        RECOVERED,
        WAITING_FOR_PASSENGERS,
        RETRY_LATER,
        NOT_FOUND,
        JOURNAL_BLOCKED
    }

    public RocketTransferRecoveryReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(transferId, "transferId");
        phase = Objects.requireNonNull(phase, "phase");
        action = Objects.requireNonNull(action, "action");
        if (sourceMatches < 0 || destinationMatches < 0) {
            throw new IllegalArgumentException("Recovery match counts cannot be negative");
        }
    }
}
