package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * WARP review R1: the rocket transfer journal at its record limit, with rockets at their block limit,
 * measured against the heap-accounting factor and round-tripped through the checked writer and the
 * bounded reader. Block-entity NBT is arbitrary player data and is not modelled here.
 */
final class RocketTransferJournalCapacityTest {
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(12, 72, 12);
    private static final long ARRIVAL = RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS
            + RocketFlightLimits.TRANSIT_TICKS;
    @TempDir Path root;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void aFullJournalOfLargeRocketsRoundTripsWithinItsHeapFactor() throws Exception {
        RocketTransferSavedData journal = new RocketTransferSavedData();
        for (int index = 0; index < RocketFlightLimits.MAX_ACTIVE_TRANSFERS; index++) {
            journal.put(record(new RocketPosition(100 + 40 * index, 80, 8)));
        }
        CompoundTag payload = journal.save(new CompoundTag());
        CompoundTag outer = new CompoundTag();
        outer.put("data", payload);
        byte[] bytes = bytes(outer);
        NbtAccounter heap = new NbtAccounter(Long.MAX_VALUE);
        NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), heap);
        double ratio = heap.getUsage() / (double) bytes.length;
        ManagedSavedDataType type = ManagedSavedDataType.ROCKET_TRANSFERS;
        System.out.printf("ARCE_BOUNDED_IO_CAPACITY transfer_records=%d blocks_each=%d raw_bytes=%d"
                        + " heap_accounted=%d ratio=%.2f factor=%d%n", RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                RocketLimits.MAX_BLOCKS, bytes.length, heap.getUsage(), ratio, type.heapAccountingFactor());
        assertTrue(ratio < type.heapAccountingFactor(), "Journal heap ratio exceeds its factor: " + ratio);
        assertTrue(bytes.length <= type.maxCompressedBytes(), "Payload exceeds its raw bound");
        Path file = root.resolve(type.fileName());
        CheckedSavedDataFile.replace(file, type, () -> payload);
        assertEquals(payload, CheckedSavedDataFile.readPayload(file, type).orElseThrow());
    }

    private static RocketTransferRecord record(RocketPosition destinationOrigin) {
        UUID transfer = UUID.randomUUID();
        UUID logical = UUID.randomUUID();
        RocketBlockState iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        RocketBlockState stairs = new RocketBlockState(ResourceLocation.tryParse("minecraft:oak_stairs"),
                Map.of("facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false"));
        List<RocketBlock> blocks = new ArrayList<>();
        for (int index = 0; index < RocketLimits.MAX_BLOCKS; index++) {
            blocks.add(new RocketBlock(new RocketPosition(index % 16, index / 256, (index / 16) % 16),
                    index % 2 == 0 ? iron : stairs));
        }
        RocketStats stats = new RocketStats(blocks.size(), 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketStructureSnapshot source = RocketStructureSnapshot.create(UUID.randomUUID(),
                RocketFlightPlanner.EARTH.dimensionId(), EARTH_ORIGIN, blocks, List.of(new RocketPosition(1, 1, 0)),
                stats, 0L);
        RocketStructureSnapshot destination = source.relocated(UUID.randomUUID(),
                RocketFlightPlanner.MOON.dimensionId(), destinationOrigin, 160L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan plan = RocketFlightPlanner.plan(stats, fuel, RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON, transfer, 0L).plan();
        RocketFlightData sourceFlight = RocketFlightData.initial(logical, stats.fuelCapacity(), stats.seatCount(),
                        RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), EARTH_ORIGIN, 0L)
                .withFuel(fuel, 0L)
                .withPassengers(RocketPassengerManifest.empty(1))
                .withPlan(plan)
                .startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        RocketFlightData destinationFlight = sourceFlight.arriveAtDestination(
                sourceFlight.fuel().debit(transfer, plan.requiredFuel()).state(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), destinationOrigin, ARRIVAL);
        return RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(), source,
                destination, sourceFlight, destinationFlight, plan.requiredFuel(), 0L);
    }

    private static byte[] bytes(CompoundTag tag) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(buffer));
        return buffer.toByteArray();
    }
}
