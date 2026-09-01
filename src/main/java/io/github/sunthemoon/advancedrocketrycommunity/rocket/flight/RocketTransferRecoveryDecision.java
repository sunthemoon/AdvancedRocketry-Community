package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;

/** Pure four-case recovery decision using ADR-006's durable authority boundary. */
public final class RocketTransferRecoveryDecision {
    private RocketTransferRecoveryDecision() {
    }

    public static RocketTransferRecoveryAction decide(
            RocketTransferPhase phase,
            RocketTransferPresence presence
    ) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(presence, "presence");
        if (presence.sourceExists() && presence.destinationExists()) {
            return phase.destinationAuthoritative()
                    ? RocketTransferRecoveryAction.REMOVE_SOURCE_KEEP_DESTINATION
                    : RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE;
        }
        if (presence.sourceExists()) {
            return RocketTransferRecoveryAction.KEEP_SOURCE;
        }
        if (presence.destinationExists()) {
            return RocketTransferRecoveryAction.KEEP_DESTINATION;
        }
        return phase.destinationAuthoritative()
                ? RocketTransferRecoveryAction.REBUILD_DESTINATION
                : RocketTransferRecoveryAction.REBUILD_SOURCE;
    }
}
