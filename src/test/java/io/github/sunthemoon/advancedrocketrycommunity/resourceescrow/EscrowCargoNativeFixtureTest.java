package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoNativeFixtureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void pairsHaveDistinctSlotsInsideTheStationaryWatchersViewAndOutsideOxygenRooms() {
        var chunks = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for (int i = 0; i < 12; i++) {
            var pos = CargoNativeBenchmark.at(i); var chunk = new net.minecraft.world.level.ChunkPos(pos);
            assertTrue(chunks.add(chunk)); assertEquals(chunk, new net.minecraft.world.level.ChunkPos(pos.offset(4, 0, 0)));
            assertTrue(Math.abs(chunk.x - 16) <= 2 && Math.abs(chunk.z - 8) <= 2);
            assertFalse((chunk.x == 15 || chunk.x == 16) && (chunk.z == 7 || chunk.z == 8));
        }
        assertThrows(IllegalArgumentException.class, () -> CargoNativeBenchmark.at(12));
    }
    @Test void recoveryComparisonRequiresExactSourceAndEveryLedgerDeviceAndRocketDigest() {
        var state = new java.util.TreeMap<String, String>(); state.put("ledger", "root");
        for (int i = 0; i < 12; i++) { state.put("device_" + i, "device" + i); state.put("rocket_" + i, "rocket" + i); }
        var baseline = new com.google.gson.JsonObject(); baseline.addProperty("schema_version", 1); baseline.addProperty("source_commit", "abc");
        baseline.add("state", new com.google.gson.Gson().toJsonTree(state));
        assertTrue(CargoNativeRecovery.matches(baseline, "abc", state));
        assertFalse(CargoNativeRecovery.matches(baseline, "different", state));
        for (var key : state.keySet()) {
            var altered = new java.util.TreeMap<>(state); altered.put(key, "different");
            assertFalse(CargoNativeRecovery.matches(baseline, "abc", altered), key);
            altered.remove(key); assertFalse(CargoNativeRecovery.matches(baseline, "abc", altered), key);
        }
    }
    @Test void nativeFixtureCountsEveryTankWithoutChangingGuidanceOrFuelStats() {
        for (boolean large : List.of(false, true)) {
            for (var tanks : List.of(List.of(FluidStack.EMPTY), List.of(new FluidStack(Fluids.WATER, 5000), FluidStack.EMPTY))) {
                var snapshot = EscrowCargoPreparationGameTests.snapshot(ResourceLocation.tryParse("minecraft:overworld"),
                        BlockPos.ZERO, large, tanks);
                assertEquals(tanks.size(), snapshot.stats().blockEntityCount());
                assertEquals(1, snapshot.stats().guidanceCount());
                assertEquals(2000, snapshot.stats().fuelCapacity());
                assertEquals(large ? 2048 : tanks.size() + 1, snapshot.blocks().size());
                assertEquals(tanks.size(), snapshot.blocks().stream().filter(block -> block.blockEntityPayload().isPresent()).count());
            }
        }
    }
}
