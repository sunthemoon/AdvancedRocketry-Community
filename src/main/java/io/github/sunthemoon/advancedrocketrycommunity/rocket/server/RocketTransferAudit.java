package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.UUID;

final class RocketTransferAudit {
    private RocketTransferAudit() { }
    static void auditPrepare(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan plan,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelection selection,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode code) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_TRANSFER_PREPARE transfer={} logical={} source={} destination={} code={} candidates={} chunks={} detail={}",
                plan.requestId(), rocket.assemblyTransactionId().orElse(null), rocket.level().dimension().location(),
                plan.destinationDimension(), code, selection.candidatesChecked(), selection.chunksLoaded(), selection.detail());
    }
    static void auditPhase(RocketTransferRecord record, String event, UUID entityId) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_TRANSFER_PHASE transfer={} logical={} phase={} event={} entity={} fuel_before={} fuel_after={} required={}",
                record.transferId(), record.logicalRocketId(), record.phase(), event, entityId,
                record.sourceFlightData().fuel().amount(), record.destinationFlightData().fuel().amount(), record.requiredFuel());
    }
}
