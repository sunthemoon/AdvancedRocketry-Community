package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RocketRecoveryDecisionTest {
    @Test
    void missingEntityAlwaysFallsBackToTheDurableSnapshotBlocks() {
        for (RocketTransactionType type : RocketTransactionType.values()) {
            for (RocketTransactionPhase phase : RocketTransactionPhase.values()) {
                assertEquals(
                        RocketRecoveryDecision.Authority.BLOCKS,
                        RocketRecoveryDecision.authority(type, phase, false),
                        () -> type + " " + phase
                );
            }
        }
    }

    @Test
    void assemblyKeepsEntityOnlyAfterItsDurableSpawnPhase() {
        for (RocketTransactionPhase phase : RocketTransactionPhase.values()) {
            RocketRecoveryDecision.Authority expected = phase == RocketTransactionPhase.SPAWNED
                            || phase == RocketTransactionPhase.COMMITTED
                    ? RocketRecoveryDecision.Authority.ENTITY
                    : RocketRecoveryDecision.Authority.BLOCKS;
            assertEquals(
                    expected,
                    RocketRecoveryDecision.authority(RocketTransactionType.ASSEMBLY, phase, true),
                    phase::name
            );
        }
    }

    @Test
    void disassemblyKeepsBlocksOnlyAfterDurableRestoration() {
        for (RocketTransactionPhase phase : RocketTransactionPhase.values()) {
            RocketRecoveryDecision.Authority expected = phase == RocketTransactionPhase.RESTORED
                            || phase == RocketTransactionPhase.COMMITTED
                    ? RocketRecoveryDecision.Authority.BLOCKS
                    : RocketRecoveryDecision.Authority.ENTITY;
            assertEquals(
                    expected,
                    RocketRecoveryDecision.authority(RocketTransactionType.DISASSEMBLY, phase, true),
                    phase::name
            );
        }
    }
}
