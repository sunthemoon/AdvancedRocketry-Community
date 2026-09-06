package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.UUID;
import org.slf4j.Logger;

/** Fixed server outcomes; a requested cancellation is distinct from a transfer fault. */
enum RocketTransferReturnReason {
    COUNTDOWN_CANCELLED("countdown_cancelled"),
    DESTINATION_PAD_BLOCKED("destination_pad_blocked"),
    DESTINATION_ENTITY_CREATE_FAILED("destination_entity_create_failed"),
    DESTINATION_ENTITY_SPAWN_FAILED("destination_entity_spawn_failed");

    private static final String MESSAGE =
            "ARCE_TRANSFER_RETURNED_TO_SOURCE transfer={} logical={} reason={} fuel={}";
    private final String diagnosticReason;

    RocketTransferReturnReason(String diagnosticReason) {
        this.diagnosticReason = diagnosticReason;
    }

    void log(Logger logger, UUID transferId, UUID logicalRocketId, long fuel) {
        if (this == COUNTDOWN_CANCELLED) {
            logger.info(MESSAGE, transferId, logicalRocketId, diagnosticReason, fuel);
        } else {
            logger.warn(MESSAGE, transferId, logicalRocketId, diagnosticReason, fuel);
        }
    }
}
