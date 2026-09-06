package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RocketTransferRecordTest {
    private static final UUID LOGICAL = UUID.fromString("00000000-0000-0000-0000-000000000671");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000672");
    private static final UUID SOURCE_ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000673");
    private static final UUID DESTINATION_ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000674");
    private static final UUID PASSENGER = UUID.fromString("00000000-0000-0000-0000-000000000675");
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(8, 80, 8);
    private static final RocketPosition MOON_ORIGIN = new RocketPosition(72, 80, 8);

    @Test
    void recordBindsExactRelocationFuelPassengersAndMonotonicPhases() {
        Fixture fixture = fixture();
        RocketTransferRecord prepared = fixture.record();

        assertEquals(RocketTransferPhase.PREPARED, prepared.phase());
        assertTrue(prepared.destinationEntityId().isEmpty());
        assertEquals(PASSENGER, prepared.destinationFlightData()
                .passengers().assignments().get(0).passengerId());
        assertEquals(
                prepared.sourceFlightData().fuel().amount() - prepared.requiredFuel(),
                prepared.destinationFlightData().fuel().amount()
        );
        assertNotEquals(prepared.sourceSnapshot().snapshotId(), prepared.destinationSnapshot().snapshotId());
        assertNotEquals(prepared.sourceSnapshot().contentHash(), prepared.destinationSnapshot().contentHash());
        assertEquals(prepared.sourceSnapshot().blocks(), prepared.destinationSnapshot().blocks());

        RocketTransferRecord spawned = prepared.destinationSpawned(DESTINATION_ENTITY);
        RocketTransferRecord passengers = spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED);
        RocketTransferRecord removed = passengers.advance(RocketTransferPhase.SOURCE_REMOVED);
        RocketTransferRecord committed = removed.advance(RocketTransferPhase.COMMITTED);

        assertEquals(DESTINATION_ENTITY, committed.destinationEntityId().orElseThrow());
        assertEquals(prepared.checksum(), committed.checksum());
        assertThrows(
                IllegalStateException.class,
                () -> prepared.advance(RocketTransferPhase.SOURCE_REMOVED)
        );
    }

    @Test
    void checksumAndAuthorityTamperingFailClosed() {
        Fixture fixture = fixture();
        RocketTransferRecord record = fixture.record();

        assertThrows(IllegalArgumentException.class, () -> RocketTransferRecord.restore(
                record.schemaVersion(),
                record.transferId(),
                record.phase(),
                record.logicalRocketId(),
                record.ownerId(),
                record.sourceEntityId(),
                null,
                record.sourceSnapshot(),
                record.destinationSnapshot(),
                record.sourceFlightData(),
                record.destinationFlightData(),
                record.requiredFuel(),
                record.createdAtGameTime(),
                "0".repeat(64)
        ));
        assertThrows(IllegalArgumentException.class, () -> RocketTransferRecord.restore(
                record.schemaVersion(),
                record.transferId(),
                RocketTransferPhase.DESTINATION_SPAWNED,
                record.logicalRocketId(),
                record.ownerId(),
                record.sourceEntityId(),
                null,
                record.sourceSnapshot(),
                record.destinationSnapshot(),
                record.sourceFlightData(),
                record.destinationFlightData(),
                record.requiredFuel(),
                record.createdAtGameTime(),
                record.checksum()
        ));
    }

    @Test
    void relocationPreservesExactStructureButChangesLocationIdentity() {
        Fixture fixture = fixture();

        assertEquals(fixture.source().blocks(), fixture.destination().blocks());
        assertEquals(fixture.source().passengerAnchors(), fixture.destination().passengerAnchors());
        assertEquals(fixture.source().stats(), fixture.destination().stats());
        assertEquals(RocketFlightPlanner.MOON.dimensionId(), fixture.destination().sourceDimension());
        assertEquals(MOON_ORIGIN, fixture.destination().sourceOrigin());
        assertNotEquals(fixture.source().contentHash(), fixture.destination().contentHash());
    }

    @Test
    void legacyCommittedRecordMigratesOnlyAfterAuthoritySettlementAndSecondSaveIsStable() throws Exception {
        RocketTransferRecord legacy = legacyCommittedRecord();
        assertEquals(1, legacy.schemaVersion());
        assertThrows(IllegalStateException.class, () -> legacyRecord(
                legacy,
                RocketTransferPhase.PREPARED,
                null
        ).migrateTargets(
                new TravelTarget.BodySurface(RocketFlightPlanner.EARTH.bodyId()),
                new TravelTarget.BodySurface(RocketFlightPlanner.MOON.bodyId())
        ));

        String fixture;
        try (var stream = RocketTransferRecordTest.class.getResourceAsStream(
                "/migrations/v110/v100-committed-earth-moon-transfer-v1.snbt"
        )) {
            if (stream == null) {
                throw new AssertionError("Missing committed v1.0 transfer fixture");
            }
            fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        RocketTransferSavedData loadedLegacy = RocketTransferSavedData.load(TagParser.parseTag(fixture));
        assertTrue(loadedLegacy.operational());
        assertEquals(1, loadedLegacy.entries().get(0).schemaVersion());
        assertEquals(legacy.checksum(), loadedLegacy.entries().get(0).checksum());

        RocketTransferRecord migrated = loadedLegacy.entries().get(0).migrateTargets(
                new TravelTarget.BodySurface(RocketFlightPlanner.EARTH.bodyId()),
                new TravelTarget.BodySurface(RocketFlightPlanner.MOON.bodyId())
        );
        assertEquals(RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION, migrated.schemaVersion());
        assertEquals(RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION,
                migrated.sourceFlightData().schemaVersion());
        assertNotEquals(legacy.checksum(), migrated.checksum());
        loadedLegacy.migrateCommitted(migrated);

        CompoundTag firstSave = loadedLegacy.save(new CompoundTag());
        RocketTransferSavedData secondLoad = RocketTransferSavedData.load(firstSave);
        CompoundTag secondSave = secondLoad.save(new CompoundTag());
        assertTrue(secondLoad.operational());
        assertEquals(migrated.checksum(), secondLoad.entries().get(0).checksum());
        assertEquals(firstSave, secondSave);
    }

    @Test
    void schemaTwoChecksumBindsCurrentAndDestinationTargets() {
        RocketTransferRecord record = fixture().record();
        RocketFlightData source = record.destinationFlightData();
        RocketFlightData tampered = RocketFlightData.restore(
                source.schemaVersion(),
                source.logicalRocketId(),
                source.state(),
                source.fuel(),
                source.plan().orElse(null),
                source.passengers(),
                source.currentBody(),
                source.currentDimension(),
                source.currentOrigin(),
                source.stateStartedGameTime(),
                source.activeTransferId().orElse(null),
                new TravelTarget.Mission(UUID.fromString("123e4567-e89b-42d3-a456-426614174701"))
        );

        assertThrows(IllegalArgumentException.class, () -> RocketTransferRecord.restore(
                record.schemaVersion(),
                record.transferId(),
                record.phase(),
                record.logicalRocketId(),
                record.ownerId(),
                record.sourceEntityId(),
                record.destinationEntityId().orElse(null),
                record.sourceSnapshot(),
                record.destinationSnapshot(),
                record.sourceFlightData(),
                tampered,
                record.requiredFuel(),
                record.createdAtGameTime(),
                record.checksum()
        ));
    }

    private static Fixture fixture() {
        RocketStructureSnapshot source = sourceSnapshot();
        RocketStructureSnapshot destination = source.relocated(
                UUID.fromString("00000000-0000-0000-0000-000000000676"),
                RocketFlightPlanner.MOON.dimensionId(),
                MOON_ORIGIN,
                160L
        );
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan plan = RocketFlightPlanner.plan(
                source.stats(),
                fuel,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                LOGICAL,
                0L
        ).plan();
        RocketPassengerManifest passengers = RocketPassengerManifest.empty(1)
                .assign(PASSENGER)
                .orElseThrow();
        RocketFlightData sourceFlight = RocketFlightData.initial(
                LOGICAL,
                source.stats().fuelCapacity(),
                source.stats().seatCount(),
                RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(),
                EARTH_ORIGIN,
                0L
        ).withFuel(fuel, 0L)
                .withPassengers(passengers)
                .withPlan(plan)
                .startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(LOGICAL, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        RocketFuelState debited = sourceFlight.fuel().debit(LOGICAL, plan.requiredFuel()).state();
        RocketFlightData destinationFlight = sourceFlight.arriveAtDestination(
                debited,
                RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(),
                MOON_ORIGIN,
                RocketFlightLimits.COUNTDOWN_TICKS
                        + RocketFlightLimits.ASCENT_TICKS
                        + RocketFlightLimits.TRANSIT_TICKS
        );
        RocketTransferRecord record = RocketTransferRecord.create(
                LOGICAL,
                LOGICAL,
                OWNER,
                SOURCE_ENTITY,
                source,
                destination,
                sourceFlight,
                destinationFlight,
                plan.requiredFuel(),
                0L
        );
        return new Fixture(source, destination, record);
    }

    private static RocketTransferRecord legacyCommittedRecord() {
        RocketTransferRecord current = fixture().record();
        RocketFlightPlan currentPlan = current.sourceFlightData().plan().orElseThrow();
        RocketFlightPlan legacyPlan = new RocketFlightPlan(
                2,
                currentPlan.requestId(),
                currentPlan.sourceBody(),
                currentPlan.destinationBody(),
                currentPlan.sourceDimension(),
                currentPlan.destinationDimension(),
                currentPlan.destinationStation().orElse(null),
                currentPlan.requiredFuel(),
                currentPlan.createdAtGameTime()
        );
        RocketFlightData source = legacyFlight(current.sourceFlightData(), legacyPlan);
        RocketFlightData destination = legacyFlight(current.destinationFlightData(), legacyPlan);
        String checksum = RocketTransferChecksum.compute(
                1,
                current.transferId(),
                current.logicalRocketId(),
                current.ownerId(),
                current.sourceEntityId(),
                current.sourceSnapshot(),
                current.destinationSnapshot(),
                source,
                destination,
                current.requiredFuel(),
                current.createdAtGameTime()
        );
        return RocketTransferRecord.restore(
                1,
                current.transferId(),
                RocketTransferPhase.COMMITTED,
                current.logicalRocketId(),
                current.ownerId(),
                current.sourceEntityId(),
                DESTINATION_ENTITY,
                current.sourceSnapshot(),
                current.destinationSnapshot(),
                source,
                destination,
                current.requiredFuel(),
                current.createdAtGameTime(),
                checksum
        );
    }

    private static RocketFlightData legacyFlight(RocketFlightData current, RocketFlightPlan plan) {
        return RocketFlightData.restore(
                1,
                current.logicalRocketId(),
                current.state(),
                current.fuel(),
                plan,
                current.passengers(),
                current.currentBody(),
                current.currentDimension(),
                current.currentOrigin(),
                current.stateStartedGameTime(),
                current.activeTransferId().orElse(null)
        );
    }

    private static RocketTransferRecord legacyRecord(
            RocketTransferRecord template,
            RocketTransferPhase phase,
            UUID destinationEntity
    ) {
        String checksum = RocketTransferChecksum.compute(
                template.schemaVersion(),
                template.transferId(),
                template.logicalRocketId(),
                template.ownerId(),
                template.sourceEntityId(),
                template.sourceSnapshot(),
                template.destinationSnapshot(),
                template.sourceFlightData(),
                template.destinationFlightData(),
                template.requiredFuel(),
                template.createdAtGameTime()
        );
        return RocketTransferRecord.restore(
                template.schemaVersion(),
                template.transferId(),
                phase,
                template.logicalRocketId(),
                template.ownerId(),
                template.sourceEntityId(),
                destinationEntity,
                template.sourceSnapshot(),
                template.destinationSnapshot(),
                template.sourceFlightData(),
                template.destinationFlightData(),
                template.requiredFuel(),
                template.createdAtGameTime(),
                checksum
        );
    }

    private static RocketStructureSnapshot sourceSnapshot() {
        ResourceLocation iron = ResourceLocation.tryParse("minecraft:iron_block");
        List<RocketBlock> blocks = List.of(
                new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 2, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(1, 1, 0), new RocketBlockState(iron, Map.of()))
        );
        return RocketStructureSnapshot.create(
                UUID.fromString("00000000-0000-0000-0000-000000000677"),
                RocketFlightPlanner.EARTH.dimensionId(),
                EARTH_ORIGIN,
                blocks,
                List.of(new RocketPosition(1, 1, 0)),
                new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0),
                0L
        );
    }

    private record Fixture(
            RocketStructureSnapshot source,
            RocketStructureSnapshot destination,
            RocketTransferRecord record
    ) {
    }
}
