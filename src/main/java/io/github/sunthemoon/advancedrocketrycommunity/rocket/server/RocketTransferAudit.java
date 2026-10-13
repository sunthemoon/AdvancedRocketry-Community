package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.UUID;

final class RocketTransferAudit {
    private RocketTransferAudit() { }
    static void auditPhase(RocketTransferRecord record, String event, UUID entityId) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_TRANSFER_PHASE transfer={} logical={} phase={} event={} entity={} fuel_before={} fuel_after={} required={}",
                record.transferId(), record.logicalRocketId(), record.phase(), event, entityId,
                record.sourceFlightData().fuel().amount(), record.destinationFlightData().fuel().amount(), record.requiredFuel());
    }
}
