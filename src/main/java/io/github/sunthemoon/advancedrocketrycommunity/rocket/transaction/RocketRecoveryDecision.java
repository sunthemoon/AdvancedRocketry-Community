package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import java.util.Objects;

/** Pure restart-recovery policy. The snapshot remains authoritative if no entity survived. */
public final class RocketRecoveryDecision {
    public enum Authority {
        /** Native terrain for legacy records, sealed terrain for SEALED_DISASSEMBLY. */
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
        return switch (type) {
            case ASSEMBLY -> phase == RocketTransactionPhase.SPAWNED
                            || phase == RocketTransactionPhase.COMMITTED
                    ? Authority.ENTITY
                    : Authority.BLOCKS;
            case DISASSEMBLY, SEALED_DISASSEMBLY -> phase == RocketTransactionPhase.RESTORED
                        || phase == RocketTransactionPhase.COMMITTED
                ? Authority.BLOCKS
                : Authority.ENTITY;
        };
    }
}
