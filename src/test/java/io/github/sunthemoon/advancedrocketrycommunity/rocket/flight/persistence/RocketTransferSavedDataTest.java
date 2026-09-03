package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RocketTransferSavedDataTest {
    private static final UUID TRANSFER = UUID.fromString("10000000-0000-0000-0000-000000000681");
    private static final UUID LOGICAL = UUID.fromString("10000000-0000-0000-0000-000000000682");
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000683");
    private static final UUID SOURCE_ENTITY = UUID.fromString("10000000-0000-0000-0000-000000000684");
    private static final UUID DESTINATION_ENTITY = UUID.fromString("10000000-0000-0000-0000-000000000685");
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(12, 72, 12);
    private static final RocketPosition MOON_ORIGIN = new RocketPosition(72, 80, 8);

    @Test
    void currentJournalRoundTripsEveryAuthorityField() {
        RocketTransferRecord spawned = record().destinationSpawned(DESTINATION_ENTITY);
        RocketTransferSavedData original = new RocketTransferSavedData();
        original.put(spawned);

        CompoundTag encoded = original.save(new CompoundTag());
        RocketTransferSavedData decoded = RocketTransferSavedData.load(encoded);
        RocketTransferRecord restored = decoded.entries().get(0);

        assertTrue(decoded.operational());
        assertEquals(1, decoded.entries().size());
        assertEquals(spawned.transferId(), restored.transferId());
        assertEquals(spawned.phase(), restored.phase());
        assertEquals(spawned.destinationEntityId(), restored.destinationEntityId());
        assertEquals(spawned.sourceSnapshot().contentHash(), restored.sourceSnapshot().contentHash());
        assertEquals(spawned.destinationSnapshot().contentHash(), restored.destinationSnapshot().contentHash());
        assertEquals(spawned.sourceFlightData(), restored.sourceFlightData());
        assertEquals(spawned.destinationFlightData(), restored.destinationFlightData());
        assertEquals(spawned.checksum(), restored.checksum());
    }

    @Test
    void futureAndChecksumDamagedJournalsArePreservedWhole() {
        RocketTransferSavedData valid = new RocketTransferSavedData();
        valid.put(record());
        CompoundTag future = valid.save(new CompoundTag());
        future.putInt("schema_version", RocketTransferSavedData.ROOT_SCHEMA_VERSION + 1);
        future.putString("future_marker", "preserve-exactly");

        RocketTransferSavedData futureData = RocketTransferSavedData.load(future);
        assertFalse(futureData.operational());
        assertEquals(future, futureData.preservedBlockedData().orElseThrow());
        assertEquals(future, futureData.save(new CompoundTag()));

        CompoundTag damaged = valid.save(new CompoundTag());
        ListTag entries = damaged.getList("transfers", CompoundTag.TAG_COMPOUND);
        entries.getCompound(0).putLong("required_fuel", 1L);
        RocketTransferSavedData damagedData = RocketTransferSavedData.load(damaged);
        assertFalse(damagedData.operational());
        assertEquals(damaged, damagedData.preservedBlockedData().orElseThrow());
    }

    @Test
    void journalRejectsPhaseRegressionAndConflictingAuthority() {
        RocketTransferRecord prepared = record();
        RocketTransferSavedData data = new RocketTransferSavedData();
        data.put(prepared);
        data.put(prepared.destinationSpawned(DESTINATION_ENTITY));

        assertThrows(IllegalArgumentException.class, () -> data.put(prepared));
        assertThrows(IllegalArgumentException.class, () -> data.put(record(
                UUID.fromString("20000000-0000-0000-0000-000000000681"),
                LOGICAL,
                UUID.fromString("20000000-0000-0000-0000-000000000684")
        )));
    }

    @Test
    void committedLandingReservationIsAtomicallyReplacedByReturnFlight() {
        RocketTransferRecord landed = committed(record());
        RocketTransferSavedData data = new RocketTransferSavedData();
        data.put(landed);
        RocketTransferRecord returning = record(
                UUID.fromString("30000000-0000-0000-0000-000000000681"),
                LOGICAL,
                DESTINATION_ENTITY
        );

        data.replace(landed.transferId(), returning);

        assertTrue(data.find(landed.transferId()).isEmpty());
        assertEquals(returning, data.find(returning.transferId()).orElseThrow());

        RocketTransferRecord wrongLogical = record(
                UUID.fromString("40000000-0000-0000-0000-000000000681"),
                UUID.fromString("40000000-0000-0000-0000-000000000682"),
                UUID.fromString("40000000-0000-0000-0000-000000000684")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> data.replace(returning.transferId(), wrongLogical)
        );
        assertEquals(returning, data.find(returning.transferId()).orElseThrow());
    }

    private static RocketTransferRecord record() {
        return record(TRANSFER, LOGICAL, SOURCE_ENTITY);
    }

    private static RocketTransferRecord committed(RocketTransferRecord prepared) {
        return prepared.destinationSpawned(DESTINATION_ENTITY)
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED);
    }

    private static RocketTransferRecord record(UUID transfer, UUID logical, UUID sourceEntity) {
        ResourceLocation iron = ResourceLocation.tryParse("minecraft:iron_block");
        List<RocketBlock> blocks = List.of(
                new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 2, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(1, 1, 0), new RocketBlockState(iron, Map.of()))
        );
        RocketStats stats = new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketStructureSnapshot source = RocketStructureSnapshot.create(
                UUID.nameUUIDFromBytes(("source:" + transfer).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                RocketFlightPlanner.EARTH.dimensionId(),
                EARTH_ORIGIN,
                blocks,
                List.of(new RocketPosition(1, 1, 0)),
                stats,
                0L
        );
        RocketStructureSnapshot destination = source.relocated(
                UUID.nameUUIDFromBytes(("destination:" + transfer).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                RocketFlightPlanner.MOON.dimensionId(),
                MOON_ORIGIN,
                160L
        );
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan plan = RocketFlightPlanner.plan(
                stats,
                fuel,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                transfer,
                0L
        ).plan();
        RocketFlightData sourceFlight = RocketFlightData.initial(
                logical,
                stats.fuelCapacity(),
                stats.seatCount(),
                RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(),
                EARTH_ORIGIN,
                0L
        ).withFuel(fuel, 0L)
                .withPassengers(RocketPassengerManifest.empty(1))
                .withPlan(plan)
                .startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        RocketFlightData destinationFlight = sourceFlight.arriveAtDestination(
                sourceFlight.fuel().debit(transfer, plan.requiredFuel()).state(),
                RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(),
                MOON_ORIGIN,
                RocketFlightLimits.COUNTDOWN_TICKS
                        + RocketFlightLimits.ASCENT_TICKS
                        + RocketFlightLimits.TRANSIT_TICKS
        );
        return RocketTransferRecord.create(
                transfer,
                logical,
                OWNER,
                sourceEntity,
                source,
                destination,
                sourceFlight,
                destinationFlight,
                plan.requiredFuel(),
                0L
        );
    }
}
