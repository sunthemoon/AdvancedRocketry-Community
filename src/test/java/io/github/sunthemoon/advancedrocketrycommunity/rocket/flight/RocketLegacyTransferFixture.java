package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

/**
 * W92-R4: the schema-1 form of a schema-2 record, as a world saved before the target migration holds it, built as
 * {@code RocketTransferRecordTest.legacyCommittedRecord} builds it. Same IDs, snapshots, phase and fuel.
 */
public final class RocketLegacyTransferFixture {
    private RocketLegacyTransferFixture() { }

    public static RocketTransferRecord schemaOne(RocketTransferRecord current) {
        if (current.schemaVersion() != RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION) {
            throw new IllegalArgumentException("A schema-2 template is required");
        }
        RocketFlightPlan currentPlan = current.sourceFlightData().plan().orElseThrow();
        RocketFlightPlan legacyPlan = new RocketFlightPlan(2, currentPlan.requestId(), currentPlan.sourceBody(),
                currentPlan.destinationBody(), currentPlan.sourceDimension(), currentPlan.destinationDimension(),
                currentPlan.destinationStation().orElse(null), currentPlan.requiredFuel(),
                currentPlan.createdAtGameTime());
        RocketFlightData source = legacyFlight(current.sourceFlightData(), legacyPlan);
        RocketFlightData destination = legacyFlight(current.destinationFlightData(), legacyPlan);
        String checksum = RocketTransferChecksum.compute(1, current.transferId(), current.logicalRocketId(),
                current.ownerId(), current.sourceEntityId(), current.sourceSnapshot(), current.destinationSnapshot(),
                source, destination, current.requiredFuel(), current.createdAtGameTime());
        return RocketTransferRecord.restore(1, current.transferId(), current.phase(), current.logicalRocketId(),
                current.ownerId(), current.sourceEntityId(), current.destinationEntityId().orElse(null),
                current.sourceSnapshot(), current.destinationSnapshot(), source, destination, current.requiredFuel(),
                current.createdAtGameTime(), checksum);
    }

    private static RocketFlightData legacyFlight(RocketFlightData current, RocketFlightPlan plan) {
        return RocketFlightData.restore(1, current.logicalRocketId(), current.state(), current.fuel(), plan,
                current.passengers(), current.currentBody(), current.currentDimension(), current.currentOrigin(),
                current.stateStartedGameTime(), current.activeTransferId().orElse(null));
    }
}
