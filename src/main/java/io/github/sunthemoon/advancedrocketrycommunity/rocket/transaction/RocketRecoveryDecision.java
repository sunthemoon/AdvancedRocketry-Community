package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import java.util.Objects;

/** Pure restart-recovery policy. The snapshot remains authoritative if no entity survived. */
public final class RocketRecoveryDecision {
    public enum Authority {
        BLOCKS,
        ENTITY
    }

    private RocketRecoveryDecision() {
    }

    public static Authority authority(
            RocketTransactionType type,
            RocketTransactionPhase phase,
            boolean matchingEntityPresent
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(phase, "phase");
        if (!matchingEntityPresent) {
            return Authority.BLOCKS;
        }
        if (type == RocketTransactionType.ASSEMBLY) {
            return phase == RocketTransactionPhase.SPAWNED
                            || phase == RocketTransactionPhase.COMMITTED
                    ? Authority.ENTITY
                    : Authority.BLOCKS;
        }
        return phase == RocketTransactionPhase.RESTORED
                        || phase == RocketTransactionPhase.COMMITTED
                ? Authority.BLOCKS
                : Authority.ENTITY;
    }
}
