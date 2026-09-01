package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RocketTransferRecoveryDecisionTest {
    @Test
    void allPresenceCasesUseTheDurableAuthorityBoundary() {
        for (RocketTransferPhase phase : RocketTransferPhase.values()) {
            boolean destination = phase.destinationAuthoritative();
            assertEquals(
                    destination
                            ? RocketTransferRecoveryAction.REMOVE_SOURCE_KEEP_DESTINATION
                            : RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE,
                    decide(phase, true, true)
            );
            assertEquals(RocketTransferRecoveryAction.KEEP_SOURCE, decide(phase, true, false));
            assertEquals(RocketTransferRecoveryAction.KEEP_DESTINATION, decide(phase, false, true));
            assertEquals(
                    destination
                            ? RocketTransferRecoveryAction.REBUILD_DESTINATION
                            : RocketTransferRecoveryAction.REBUILD_SOURCE,
                    decide(phase, false, false)
            );
        }
    }

    private static RocketTransferRecoveryAction decide(
            RocketTransferPhase phase,
            boolean source,
            boolean destination
    ) {
        return RocketTransferRecoveryDecision.decide(
                phase,
                new RocketTransferPresence(source, destination)
        );
    }
}
