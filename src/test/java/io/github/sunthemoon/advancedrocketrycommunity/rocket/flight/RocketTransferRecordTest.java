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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
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
