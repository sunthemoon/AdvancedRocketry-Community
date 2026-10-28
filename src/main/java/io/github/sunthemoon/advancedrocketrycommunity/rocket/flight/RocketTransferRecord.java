package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTransferEnvelope;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable, versioned authority record for one bounded inter-Level rocket transfer. */
public final class RocketTransferRecord {
    public static final int GOVERNED_SCHEMA_VERSION = 3;
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
    private final EscrowTransferEnvelope nativeTransfer;

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
            String checksum,
            EscrowTransferEnvelope nativeTransfer
    ) {
        if (schemaVersion < 1 || schemaVersion > GOVERNED_SCHEMA_VERSION
                || (schemaVersion == GOVERNED_SCHEMA_VERSION) != (nativeTransfer != null)) {
            throw new IllegalArgumentException("Unsupported rocket transfer record schema");
        }
        this.schemaVersion = schemaVersion;
        this.nativeTransfer = nativeTransfer;
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
        int flightSchema = schemaVersion == GOVERNED_SCHEMA_VERSION ? 2 : schemaVersion;
        if (sourceFlightData.schemaVersion() != flightSchema
                || destinationFlightData.schemaVersion() != flightSchema) {
            throw new IllegalArgumentException("Unsupported flight schema for transfer record");
        }
        if (requiredFuel <= 0L || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            throw new IllegalArgumentException("Transfer fuel is outside the fixed limit");
        }
        this.requiredFuel = requiredFuel;
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Transfer game time cannot be negative");
        }
        this.createdAtGameTime = createdAtGameTime;
        validateAuthorityShape();
        if (nativeTransfer != null) { nativeTransfer.validate(this); }
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
        if (nativeTransfer != null) { expected = RocketTransferChecksum.governed(expected, nativeTransfer.digest()); }
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
                checksum,
                null
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
                checksum,
                null
        );
    }

    /** Only the governed dispatcher may publish this value, after the exact ARMED acknowledgement. */
    public static RocketTransferRecord createGoverned(
            UUID transferId, UUID logicalRocketId, UUID ownerId, UUID sourceEntityId,
            RocketStructureSnapshot sourceSnapshot, RocketStructureSnapshot destinationSnapshot,
            RocketFlightData sourceFlightData, RocketFlightData destinationFlightData,
            long requiredFuel, long createdAtGameTime, EscrowTransferEnvelope nativeTransfer) {
        Objects.requireNonNull(nativeTransfer, "nativeTransfer");
        String checksum = RocketTransferChecksum.governed(RocketTransferChecksum.compute(GOVERNED_SCHEMA_VERSION, transferId,
                logicalRocketId, ownerId, sourceEntityId, sourceSnapshot, destinationSnapshot,
                sourceFlightData, destinationFlightData, requiredFuel, createdAtGameTime), nativeTransfer.digest());
        return restoreGoverned(transferId, RocketTransferPhase.PREPARED, logicalRocketId, ownerId,
                sourceEntityId, null, sourceSnapshot, destinationSnapshot, sourceFlightData, destinationFlightData,
                requiredFuel, createdAtGameTime, checksum, nativeTransfer);
    }

    public static RocketTransferRecord restoreGoverned(
            UUID transferId, RocketTransferPhase phase, UUID logicalRocketId, UUID ownerId,
            UUID sourceEntityId, UUID destinationEntityId,
            RocketStructureSnapshot sourceSnapshot, RocketStructureSnapshot destinationSnapshot,
            RocketFlightData sourceFlightData, RocketFlightData destinationFlightData,
            long requiredFuel, long createdAtGameTime, String checksum, EscrowTransferEnvelope nativeTransfer) {
        return new RocketTransferRecord(GOVERNED_SCHEMA_VERSION, transferId, phase, logicalRocketId, ownerId, sourceEntityId,
                destinationEntityId, sourceSnapshot, destinationSnapshot, sourceFlightData, destinationFlightData,
                requiredFuel, createdAtGameTime, checksum, Objects.requireNonNull(nativeTransfer, "nativeTransfer"));
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

    /** Upgrades a completed legacy authority record after recovery has selected its authoritative side. */
    public RocketTransferRecord migrateTargets(
            io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget sourceTarget,
            io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget destinationTarget
    ) {
        if (schemaVersion != 1) {
            throw new IllegalStateException("Only schema-1 transfer records require target migration");
        }
        if (phase != RocketTransferPhase.COMMITTED) {
            throw new IllegalStateException("Active legacy authority must finish before target migration");
        }
        RocketFlightData migratedSource = sourceFlightData.withMigratedCurrentTarget(sourceTarget);
        RocketFlightData migratedDestination = destinationFlightData.withMigratedCurrentTarget(destinationTarget);
        int migratedSchema = RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION;
        String migratedChecksum = RocketTransferChecksum.compute(
                migratedSchema,
                transferId,
                logicalRocketId,
                ownerId,
                sourceEntityId,
                sourceSnapshot,
                destinationSnapshot,
                migratedSource,
                migratedDestination,
                requiredFuel,
                createdAtGameTime
        );
        return new RocketTransferRecord(
                migratedSchema,
                transferId,
                phase,
                logicalRocketId,
                ownerId,
                sourceEntityId,
                destinationEntityId,
                sourceSnapshot,
                destinationSnapshot,
                migratedSource,
                migratedDestination,
                requiredFuel,
                createdAtGameTime,
                migratedChecksum,
                null
        );
    }

    /** Passenger-only legacy projection. Publication still requires the checked release CAS, never ordinary put. */
    public RocketTransferRecord releasePassengers(Set<UUID> released) {
        if (nativeTransfer != null) { throw new IllegalArgumentException("Governed passengers cannot be released"); }
        Objects.requireNonNull(released, "released");
        var passengers = sourceFlightData.passengers();
        for (UUID player : released) { passengers = passengers.remove(player); }
        if (passengers.equals(sourceFlightData.passengers())) { return this; }
        var source = sourceFlightData.withPassengers(passengers);
        var destination = destinationFlightData.withPassengers(passengers);
        String updatedChecksum = RocketTransferChecksum.compute(schemaVersion, transferId, logicalRocketId,
                ownerId, sourceEntityId, sourceSnapshot, destinationSnapshot, source, destination, requiredFuel, createdAtGameTime);
        return new RocketTransferRecord(schemaVersion, transferId, phase, logicalRocketId, ownerId,
                sourceEntityId, destinationEntityId, sourceSnapshot, destinationSnapshot, source, destination,
                requiredFuel, createdAtGameTime, updatedChecksum, null);
    }

    /**
     * W170 D4/D5: T' for a bay consumption group. Only PREPARED and ungoverned; it keeps the transfer ID, phase, owner,
     * source UUID, flight data, fuel and creation tick, and replaces only the two snapshots (same IDs, dimensions,
     * origins, positions and states; block-entity payloads only) and the checksum.
     */
    public RocketTransferRecord withConsumedSnapshots(RocketStructureSnapshot source, RocketStructureSnapshot destination) {
        if (phase != RocketTransferPhase.PREPARED || nativeTransfer != null) {
            throw new IllegalStateException("Only an ungoverned PREPARED transfer can carry a bay consumption");
        }
        if (!payloadOnly(sourceSnapshot, source) || !payloadOnly(destinationSnapshot, destination)) {
            throw new IllegalArgumentException("A bay consumption replaces block-entity payloads only");
        }
        String updated = RocketTransferChecksum.compute(schemaVersion, transferId, logicalRocketId, ownerId, sourceEntityId,
                source, destination, sourceFlightData, destinationFlightData, requiredFuel, createdAtGameTime);
        return new RocketTransferRecord(schemaVersion, transferId, phase, logicalRocketId, ownerId, sourceEntityId, null,
                source, destination, sourceFlightData, destinationFlightData, requiredFuel, createdAtGameTime, updated, null);
    }

    private static boolean payloadOnly(RocketStructureSnapshot before, RocketStructureSnapshot after) {
        if (before.schemaVersion() != after.schemaVersion() || !before.snapshotId().equals(after.snapshotId())
                || !before.sourceDimension().equals(after.sourceDimension())
                || !before.sourceOrigin().equals(after.sourceOrigin()) || before.blocks().size() != after.blocks().size()
                || !before.passengerAnchors().equals(after.passengerAnchors()) || !before.stats().equals(after.stats())
                || !before.propulsion().equals(after.propulsion()) || before.createdAtGameTime() != after.createdAtGameTime()) {
            return false;
        }
        for (int i = 0; i < before.blocks().size(); i++) {
            var was = before.blocks().get(i); var now = after.blocks().get(i);
            if (!was.position().equals(now.position()) || !was.state().equals(now.state())
                    || was.blockEntityPayload().isPresent() != now.blockEntityPayload().isPresent()
                    || was.blockEntityPayload().isPresent() && !was.blockEntityPayload().get().adapterId()
                            .equals(now.blockEntityPayload().get().adapterId())) {
                return false;
            }
        }
        return true;
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
                checksum,
                nativeTransfer
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
                || !sourceSnapshot.stats().equals(destinationSnapshot.stats())
                || !sourceSnapshot.propulsion().equals(destinationSnapshot.propulsion())) {
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
        RocketFuelMutation expectedDebit = sourceFlightData.fuel().debit(plan);
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

    public Optional<EscrowTransferEnvelope> nativeTransfer() { return Optional.ofNullable(nativeTransfer); }
}
