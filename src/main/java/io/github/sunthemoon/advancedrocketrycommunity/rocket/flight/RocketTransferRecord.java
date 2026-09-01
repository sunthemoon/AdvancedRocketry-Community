package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable schema-1 authority record for one Earth/Moon transfer. */
public final class RocketTransferRecord {
    private final int schemaVersion;
    private final UUID transferId;
    private final RocketTransferPhase phase;
    private final UUID logicalRocketId;
    private final UUID ownerId;
    private final UUID sourceEntityId;
    private final UUID destinationEntityId;
    private final RocketStructureSnapshot sourceSnapshot;
    private final RocketStructureSnapshot destinationSnapshot;
    private final RocketFlightData sourceFlightData;
    private final RocketFlightData destinationFlightData;
    private final long requiredFuel;
    private final long createdAtGameTime;
    private final String checksum;

    private RocketTransferRecord(
            int schemaVersion,
            UUID transferId,
            RocketTransferPhase phase,
            UUID logicalRocketId,
            UUID ownerId,
            UUID sourceEntityId,
            UUID destinationEntityId,
            RocketStructureSnapshot sourceSnapshot,
            RocketStructureSnapshot destinationSnapshot,
            RocketFlightData sourceFlightData,
            RocketFlightData destinationFlightData,
            long requiredFuel,
            long createdAtGameTime,
            String checksum
    ) {
        if (schemaVersion != RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported rocket transfer record schema");
        }
        this.schemaVersion = schemaVersion;
        this.transferId = Objects.requireNonNull(transferId, "transferId");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.logicalRocketId = Objects.requireNonNull(logicalRocketId, "logicalRocketId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.sourceEntityId = Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        this.destinationEntityId = destinationEntityId;
        this.sourceSnapshot = Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        this.destinationSnapshot = Objects.requireNonNull(destinationSnapshot, "destinationSnapshot");
        this.sourceFlightData = Objects.requireNonNull(sourceFlightData, "sourceFlightData");
        this.destinationFlightData = Objects.requireNonNull(destinationFlightData, "destinationFlightData");
        if (requiredFuel <= 0L || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            throw new IllegalArgumentException("Transfer fuel is outside the fixed limit");
        }
        this.requiredFuel = requiredFuel;
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Transfer game time cannot be negative");
        }
        this.createdAtGameTime = createdAtGameTime;
        validateAuthorityShape();
        String expected = RocketTransferChecksum.compute(
                schemaVersion,
                transferId,
                logicalRocketId,
                ownerId,
                sourceEntityId,
                sourceSnapshot,
                destinationSnapshot,
                sourceFlightData,
                destinationFlightData,
                requiredFuel,
                createdAtGameTime
        );
        if (!Objects.requireNonNull(checksum, "checksum").equals(expected)) {
            throw new IllegalArgumentException("Rocket transfer checksum does not match authority data");
        }
        this.checksum = checksum;
    }

    public static RocketTransferRecord create(
            UUID transferId,
            UUID logicalRocketId,
            UUID ownerId,
            UUID sourceEntityId,
            RocketStructureSnapshot sourceSnapshot,
            RocketStructureSnapshot destinationSnapshot,
            RocketFlightData sourceFlightData,
            RocketFlightData destinationFlightData,
            long requiredFuel,
            long createdAtGameTime
    ) {
        int schema = RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION;
        String checksum = RocketTransferChecksum.compute(
                schema,
                transferId,
                logicalRocketId,
                ownerId,
                sourceEntityId,
                sourceSnapshot,
                destinationSnapshot,
                sourceFlightData,
                destinationFlightData,
                requiredFuel,
                createdAtGameTime
        );
        return new RocketTransferRecord(
                schema,
                transferId,
                RocketTransferPhase.PREPARED,
                logicalRocketId,
                ownerId,
                sourceEntityId,
                null,
                sourceSnapshot,
                destinationSnapshot,
                sourceFlightData,
                destinationFlightData,
                requiredFuel,
                createdAtGameTime,
                checksum
        );
    }

    public static RocketTransferRecord restore(
            int schemaVersion,
            UUID transferId,
            RocketTransferPhase phase,
            UUID logicalRocketId,
            UUID ownerId,
            UUID sourceEntityId,
            UUID destinationEntityId,
            RocketStructureSnapshot sourceSnapshot,
            RocketStructureSnapshot destinationSnapshot,
            RocketFlightData sourceFlightData,
            RocketFlightData destinationFlightData,
            long requiredFuel,
            long createdAtGameTime,
            String checksum
    ) {
        return new RocketTransferRecord(
                schemaVersion,
                transferId,
                phase,
                logicalRocketId,
                ownerId,
                sourceEntityId,
                destinationEntityId,
                sourceSnapshot,
                destinationSnapshot,
                sourceFlightData,
                destinationFlightData,
                requiredFuel,
                createdAtGameTime,
                checksum
        );
    }

    public RocketTransferRecord destinationSpawned(UUID entityId) {
        if (phase != RocketTransferPhase.PREPARED) {
            throw new IllegalStateException("Destination can only be bound from PREPARED");
        }
        return copy(RocketTransferPhase.DESTINATION_SPAWNED, Objects.requireNonNull(entityId, "entityId"));
    }

    public RocketTransferRecord advance(RocketTransferPhase next) {
        Objects.requireNonNull(next, "next");
        if (next.ordinal() != phase.ordinal() + 1) {
            throw new IllegalStateException("Transfer phases must advance exactly once");
        }
        return copy(next, destinationEntityId);
    }

    public RocketTransferRecord rebindDestination(UUID entityId) {
        if (!phase.destinationAuthoritative()) {
            throw new IllegalStateException("Only destination-authoritative recovery may rebind an entity");
        }
        return copy(phase, Objects.requireNonNull(entityId, "entityId"));
    }

    private RocketTransferRecord copy(RocketTransferPhase updatedPhase, UUID updatedDestinationEntityId) {
        return new RocketTransferRecord(
                schemaVersion,
                transferId,
                updatedPhase,
                logicalRocketId,
                ownerId,
                sourceEntityId,
                updatedDestinationEntityId,
                sourceSnapshot,
                destinationSnapshot,
                sourceFlightData,
                destinationFlightData,
                requiredFuel,
                createdAtGameTime,
                checksum
        );
    }

    private void validateAuthorityShape() {
        if (phase.destinationAuthoritative() != (destinationEntityId != null)) {
            throw new IllegalArgumentException("Transfer phase and destination entity binding disagree");
        }
        if (!logicalRocketId.equals(sourceFlightData.logicalRocketId())
                || !logicalRocketId.equals(destinationFlightData.logicalRocketId())) {
            throw new IllegalArgumentException("Transfer flight data changed logical rocket identity");
        }
        if (sourceFlightData.state() != RocketFlightState.TRANSIT
                || destinationFlightData.state() != RocketFlightState.DESCENT
                || sourceFlightData.activeTransferId().filter(transferId::equals).isEmpty()
                || destinationFlightData.activeTransferId().filter(transferId::equals).isEmpty()) {
            throw new IllegalArgumentException("Transfer authority requires matching transit/descent states");
        }
        if (!sourceSnapshot.sourceDimension().equals(sourceFlightData.currentDimension())
                || !sourceSnapshot.sourceOrigin().equals(sourceFlightData.currentOrigin())
                || !destinationSnapshot.sourceDimension().equals(destinationFlightData.currentDimension())
                || !destinationSnapshot.sourceOrigin().equals(destinationFlightData.currentOrigin())) {
            throw new IllegalArgumentException("Transfer snapshots do not match flight locations");
        }
        if (sourceSnapshot.snapshotId().equals(destinationSnapshot.snapshotId())
                || sourceSnapshot.contentHash().equals(destinationSnapshot.contentHash())
                || !sourceSnapshot.blocks().equals(destinationSnapshot.blocks())
                || !sourceSnapshot.passengerAnchors().equals(destinationSnapshot.passengerAnchors())
                || !sourceSnapshot.stats().equals(destinationSnapshot.stats())) {
            throw new IllegalArgumentException("Destination is not an exact relocated source structure");
        }
        if (!sourceFlightData.passengers().equals(destinationFlightData.passengers())
                || !sourceFlightData.plan().equals(destinationFlightData.plan())) {
            throw new IllegalArgumentException("Transfer changed the passenger manifest or flight plan");
        }
        RocketFlightPlan plan = sourceFlightData.plan().orElseThrow();
        if (!plan.requestId().equals(transferId) || plan.requiredFuel() != requiredFuel) {
            throw new IllegalArgumentException("Transfer identity or fuel differs from the server plan");
        }
        RocketFuelMutation expectedDebit = sourceFlightData.fuel().debit(transferId, requiredFuel);
        if (!expectedDebit.success() || !expectedDebit.state().equals(destinationFlightData.fuel())) {
            throw new IllegalArgumentException("Destination fuel is not the exact once-only transfer debit");
        }
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public UUID transferId() {
        return transferId;
    }

    public RocketTransferPhase phase() {
        return phase;
    }

    public UUID logicalRocketId() {
        return logicalRocketId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID sourceEntityId() {
        return sourceEntityId;
    }

    public Optional<UUID> destinationEntityId() {
        return Optional.ofNullable(destinationEntityId);
    }

    public RocketStructureSnapshot sourceSnapshot() {
        return sourceSnapshot;
    }

    public RocketStructureSnapshot destinationSnapshot() {
        return destinationSnapshot;
    }

    public RocketFlightData sourceFlightData() {
        return sourceFlightData;
    }

    public RocketFlightData destinationFlightData() {
        return destinationFlightData;
    }

    public long requiredFuel() {
        return requiredFuel;
    }

    public long createdAtGameTime() {
        return createdAtGameTime;
    }

    public String checksum() {
        return checksum;
    }
}
