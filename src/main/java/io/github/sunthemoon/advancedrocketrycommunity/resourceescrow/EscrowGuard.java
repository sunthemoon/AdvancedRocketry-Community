package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.Objects;
import java.util.UUID;

/** Frozen checked journal identity; null transfer means positively checked no-transfer state. */
public record EscrowGuard(UUID rocket, UUID transfer, long journalGeneration, String journalDigest) {
    public EscrowGuard {
        Objects.requireNonNull(rocket);
        EscrowTags.require(journalGeneration > 0, "Journal generation must be checked");
        EscrowTags.digest(journalDigest);
    }
}
