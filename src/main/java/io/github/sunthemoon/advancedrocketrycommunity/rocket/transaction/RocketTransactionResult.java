package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RocketTransactionResult {
    private final boolean success;
    private final UUID transactionId;
    private final UUID rocketEntityId;
    private final RocketValidationIssue issue;
    private final int changedBlocks;
    private final int rolledBackBlocks;

    private RocketTransactionResult(
            boolean success,
            UUID transactionId,
            UUID rocketEntityId,
            RocketValidationIssue issue,
            int changedBlocks,
            int rolledBackBlocks
    ) {
        this.success = success;
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.rocketEntityId = rocketEntityId;
        this.issue = issue;
        this.changedBlocks = changedBlocks;
        this.rolledBackBlocks = rolledBackBlocks;
        if (changedBlocks < 0 || rolledBackBlocks < 0 || rolledBackBlocks > changedBlocks) {
            throw new IllegalArgumentException("Invalid transaction block counters");
        }
        if (success == (issue != null)) {
            throw new IllegalArgumentException("Transaction result success/issue mismatch");
        }
    }

    static RocketTransactionResult success(
            UUID transactionId,
            UUID rocketEntityId,
            int changedBlocks
    ) {
        return new RocketTransactionResult(
                true,
                transactionId,
                rocketEntityId,
                null,
                changedBlocks,
                0
        );
    }

    static RocketTransactionResult failure(
            UUID transactionId,
            RocketValidationIssue issue,
            UUID rocketEntityId,
            int changedBlocks,
            int rolledBackBlocks
    ) {
        return new RocketTransactionResult(
                false,
                transactionId,
                rocketEntityId,
                issue,
                changedBlocks,
                rolledBackBlocks
        );
    }

    public boolean success() {
        return success;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public Optional<UUID> rocketEntityId() {
        return Optional.ofNullable(rocketEntityId);
    }

    public Optional<RocketValidationIssue> issue() {
        return Optional.ofNullable(issue);
    }

    public RocketValidationCode code() {
        return success ? RocketValidationCode.SUCCESS : issue.code();
    }

    public int changedBlocks() {
        return changedBlocks;
    }

    public int rolledBackBlocks() {
        return rolledBackBlocks;
    }
}
