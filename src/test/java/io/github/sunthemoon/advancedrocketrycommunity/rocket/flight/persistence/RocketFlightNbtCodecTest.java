package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class RocketFlightNbtCodecTest {
    private static final UUID ROCKET = UUID.fromString("00000000-0000-0000-0000-000000000641");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000642");
    private static final UUID TRANSFER = UUID.fromString("00000000-0000-0000-0000-000000000643");
    private static final UUID PASSENGER = UUID.fromString("00000000-0000-0000-0000-000000000644");

    @Test
    void activeDestinationStateRoundTripsLosslesslyAndDeterministically() {
        RocketFlightData original = destinationData();

        CompoundTag first = RocketFlightNbtCodec.encode(original);
        RocketFlightDecodeResult decoded = RocketFlightNbtCodec.decode(first);
        CompoundTag second = RocketFlightNbtCodec.encode(decoded.data().orElseThrow());

        assertEquals(RocketFlightDecodeResult.Status.VALID, decoded.status());
        assertEquals(original, decoded.data().orElseThrow());
        assertEquals(first, second);
        assertEquals(633L, decoded.data().orElseThrow().fuel().amount());
        assertTrue(decoded.data().orElseThrow().fuel().wasDebited(TRANSFER));
        assertEquals(PASSENGER, decoded.data().orElseThrow()
                .passengers().assignments().get(0).passengerId());
    }

    @Test
    void futureOuterSchemaIsPreservedVerbatim() {
        CompoundTag future = RocketFlightNbtCodec.encode(destinationData());
        future.putInt("schema_version", RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION + 1);
        future.putString("future_field", "opaque");

        RocketFlightDecodeResult result = RocketFlightNbtCodec.decode(future);

        assertEquals(RocketFlightDecodeResult.Status.FUTURE_SCHEMA, result.status());
        assertEquals(future, result.preservedPayload().orElseThrow());
        assertTrue(result.data().isEmpty());
    }

    @Test
    void missingWrongAndOldFieldsFailClosedWithPreservedInput() {
        CompoundTag missing = RocketFlightNbtCodec.encode(destinationData());
        missing.remove("logical_rocket_id");
        CompoundTag wrong = RocketFlightNbtCodec.encode(destinationData());
        wrong.putInt("state", 3);
        CompoundTag old = RocketFlightNbtCodec.encode(destinationData());
        old.putInt("schema_version", 0);

        for (CompoundTag malformed : new CompoundTag[]{missing, wrong, old}) {
            RocketFlightDecodeResult result = RocketFlightNbtCodec.decode(malformed);
            assertEquals(RocketFlightDecodeResult.Status.INVALID, result.status());
            assertEquals(malformed, result.preservedPayload().orElseThrow());
            assertTrue(result.data().isEmpty());
        }
    }

    @Test
    void nestedSchemaIdentifierAndListBoundsAreStrict() {
        CompoundTag futurePlan = RocketFlightNbtCodec.encode(destinationData());
        futurePlan.getCompound("plan").putInt("schema_version", 3);
        CompoundTag longIdentifier = RocketFlightNbtCodec.encode(destinationData());
        longIdentifier.putString("current_body", "a:" + "x".repeat(300));
        CompoundTag tooManyDebits = RocketFlightNbtCodec.encode(destinationData());
        ListTag debits = tooManyDebits.getCompound("fuel").getList("committed_debits", 10);
        CompoundTag debit = debits.getCompound(0).copy();
        while (debits.size() <= RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS) {
            CompoundTag next = debit.copy();
            next.putUUID("transaction_id", new UUID(0L, debits.size() + 1L));
            debits.add(next);
        }

        assertEquals(
                RocketFlightDecodeResult.Status.INVALID,
                RocketFlightNbtCodec.decode(futurePlan).status()
        );
        assertEquals(
                RocketFlightDecodeResult.Status.INVALID,
                RocketFlightNbtCodec.decode(longIdentifier).status()
        );
        assertEquals(
                RocketFlightDecodeResult.Status.INVALID,
                RocketFlightNbtCodec.decode(tooManyDebits).status()
        );
    }

    @Test
    void oversizedPayloadIsRejectedBeforeNestedParsing() {
        CompoundTag oversized = RocketFlightNbtCodec.encode(destinationData());
        oversized.putByteArray("oversized", new byte[RocketFlightLimits.MAX_FLIGHT_DATA_NBT_BYTES]);

        RocketFlightDecodeResult result = RocketFlightNbtCodec.decode(oversized);

        assertEquals(RocketFlightDecodeResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("size"));
        assertEquals(oversized, result.preservedPayload().orElseThrow());
    }

    @Test
    void schemaTwoStationPlanRoundTripsItsUuidAndSchemaOneRemainsReadable() {
        UUID stationId = UUID.fromString("00000000-0000-0000-0000-000000000700");
        RocketStats stats = new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketFuelState full = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan stationPlan = RocketFlightPlanner.plan(
                stats,
                full,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.SPACE_STATION,
                stationId,
                REQUEST,
                2L
        ).plan();
        RocketFlightData stationData = RocketFlightData.initial(
                ROCKET,
                1_000L,
                2,
                RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(),
                new RocketPosition(8, 80, 8),
                0L
        ).withFuel(full, 1L).withPlan(stationPlan).startCountdown(3L);

        RocketFlightDecodeResult decoded = RocketFlightNbtCodec.decode(
                RocketFlightNbtCodec.encode(stationData)
        );
        assertEquals(RocketFlightDecodeResult.Status.VALID, decoded.status());
        assertEquals(stationId, decoded.data().orElseThrow().plan().orElseThrow()
                .destinationStation().orElseThrow());

        CompoundTag legacy = RocketFlightNbtCodec.encode(destinationData());
        legacy.getCompound("plan").putInt("schema_version", 1);
        legacy.getCompound("plan").remove("destination_station_id");
        assertEquals(RocketFlightDecodeResult.Status.VALID, RocketFlightNbtCodec.decode(legacy).status());
    }

    private static RocketFlightData destinationData() {
        RocketStats stats = new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketFuelState full = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan plan = RocketFlightPlanner.plan(
                stats,
                full,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                REQUEST,
                2L
        ).plan();
        RocketFlightData source = RocketFlightData.initial(
                ROCKET,
                1_000L,
                2,
                RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(),
                new RocketPosition(8, 80, 8),
                0L
        ).withFuel(full, 1L)
                .withPassengers(RocketPassengerManifest.empty(2).assign(PASSENGER).orElseThrow())
                .withPlan(plan)
                .startCountdown(3L)
                .completeCountdown(63L)
                .beginTransit(TRANSFER, 143L);
        RocketFuelState debited = full.debit(TRANSFER, plan.requiredFuel()).state();
        return source.arriveAtDestination(
                debited,
                RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(),
                new RocketPosition(72, 80, 8),
                164L
        );
    }
}
