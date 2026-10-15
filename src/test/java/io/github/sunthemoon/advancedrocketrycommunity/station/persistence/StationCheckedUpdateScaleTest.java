package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData.CheckedUpdate;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.SpikeCost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ORBIT-04 bounded measurement, not a load campaign: a checked gravity write (encode, validate,
 * stage, force, read back, atomic replace, publish) at 10, 100 and the 4,096-station limit, against
 * the 500 ms single-spike budget (docs/17), and constant-time region lookup at the limit. Each spike is the median
 * server-thread cost ({@link SpikeCost}) of five calls after two warm-up calls (W55).
 */
final class StationCheckedUpdateScaleTest {
    private static final long SPIKE_BUDGET_NANOS = 500_000_000L;
    private static final int WARMUPS = 2;
    private static final int SAMPLES = 5;
    @TempDir Path root;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void checkedGravityWriteStaysWithinTheSpikeBudgetUpToTheStationLimit() throws Exception {
        for (int count : new int[]{10, 100, StationLimits.MAX_STATIONS}) {
            Path directory = Files.createDirectory(root.resolve("stations-" + count));
            Path file = directory.resolve(ManagedSavedDataType.STATIONS.fileName());
            StationRegistrySavedData data = registry(count);
            CheckedSavedDataFile.replace(file, ManagedSavedDataType.STATIONS, () -> data.save(new CompoundTag()));
            StationState target = data.stations().get(count / 2);
            var samples = SpikeCost.measure(WARMUPS, SAMPLES, run -> assertEquals(CheckedUpdate.COMMITTED,
                    data.checkedSetGravity(file, data.find(target.stationId()).orElseThrow(), 100 + run * 100,
                            CheckedSavedDataFile::atomicMove)));
            System.out.printf("ARCE_ORBIT04_CHECKED_WRITE stations=%d file_bytes=%d median_ms=%.1f max_ms=%.1f"
                            + " cost_median_ms=%.1f cost_max_ms=%.1f%n", count, Files.size(file),
                    samples.medianWall() / 1e6, samples.maxWall() / 1e6, samples.medianCost() / 1e6, samples.maxCost() / 1e6);
            assertTrue(samples.medianCost() < SPIKE_BUDGET_NANOS,
                    count + " stations: median checked write cost " + samples.medianCost() / 1e6 + " ms");
            assertEquals(100 + (WARMUPS + SAMPLES - 1) * 100,
                    data.find(target.stationId()).orElseThrow().environment().gravityMilli());
        }
    }

    /**
     * WARP-05 (ADR-044 §8, plan §13.2-13.4): a checked warp commit, and an ordinary save of a registry
     * whose 4,096 stations all hold a balance, at 10, 100 and the station limit.
     */
    @Test
    void warpCommitAndFullBalanceSaveStayWithinTheSpikeBudget() throws Exception {
        for (int count : new int[]{10, 100, StationLimits.MAX_STATIONS}) {
            Path directory = Files.createDirectory(root.resolve("warp-" + count));
            Path file = directory.resolve(ManagedSavedDataType.STATIONS.fileName());
            StationRegistrySavedData data = registry(count);
            java.util.Map<UUID, Integer> credits = new java.util.LinkedHashMap<>();
            data.stations().forEach(station -> credits.put(station.stationId(), StationLimits.MAX_WARP_ENERGY));
            assertEquals((long) count * StationLimits.MAX_WARP_ENERGY, data.foldWarpCredits(credits).credited());
            CompoundTag[] saved = new CompoundTag[1];
            var saves = SpikeCost.measure(WARMUPS, SAMPLES, run -> saved[0] = data.save(new CompoundTag()));
            CheckedSavedDataFile.replace(file, ManagedSavedDataType.STATIONS, () -> saved[0]);
            StationState target = data.stations().get(count / 2);
            // Alternate away from the starting Earth orbit, so the last of the odd number of commits ends at the Moon.
            var samples = SpikeCost.measure(WARMUPS, SAMPLES, run -> assertEquals(CheckedUpdate.COMMITTED,
                    data.checkedRelocation(file, data.find(target.stationId()).orElseThrow(),
                            ModIdentity.id(run % 2 == 0 ? "moon" : "earth"), 100_000, CheckedSavedDataFile::atomicMove)));
            System.out.printf("ARCE_WARP05_SCALE stations=%d balances=%d file_bytes=%d save_ms=%.1f"
                            + " commit_median_ms=%.1f commit_max_ms=%.1f save_cost_median_ms=%.1f"
                            + " commit_cost_median_ms=%.1f commit_cost_max_ms=%.1f%n", count,
                    data.warpEnergyBalances().size(), Files.size(file), saves.medianWall() / 1e6,
                    samples.medianWall() / 1e6, samples.maxWall() / 1e6, saves.medianCost() / 1e6,
                    samples.medianCost() / 1e6, samples.maxCost() / 1e6);
            assertTrue(samples.medianCost() < SPIKE_BUDGET_NANOS,
                    count + " stations: median warp commit cost " + samples.medianCost() / 1e6 + " ms");
            assertTrue(saves.medianCost() < SPIKE_BUDGET_NANOS,
                    count + " stations: median ordinary save cost " + saves.medianCost() / 1e6 + " ms");
            assertEquals(StationLimits.MAX_WARP_ENERGY - (WARMUPS + SAMPLES) * 100_000L, data.warpEnergy(target.stationId()));
            assertEquals(ModIdentity.id("moon"), data.find(target.stationId()).orElseThrow().orbitBody());
        }
    }

    @Test
    void regionLookupIsConstantTimeAtTheStationLimit() {
        StationRegistrySavedData data = registry(StationLimits.MAX_STATIONS);
        List<StationState> stations = data.stations();
        int lookups = 200_000;
        long start = System.nanoTime();
        int found = 0;
        for (int index = 0; index < lookups; index++) {
            StationState station = stations.get(index % stations.size());
            if (data.findAt(station.cell().centerX() + (index % 200) - 100, station.cell().centerZ()).isPresent()) {
                found++;
            }
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("ARCE_ORBIT04_LOOKUP stations=%d lookups=%d total_ms=%.1f per_lookup_ns=%d%n",
                stations.size(), lookups, elapsed / 1e6, elapsed / lookups);
        assertEquals(lookups, found);
        // Generous bound: a scan over 4,096 stations per lookup would be far slower than this.
        assertTrue(elapsed / lookups < 20_000L, "Lookup averaged " + elapsed / lookups + " ns");
    }

    private static StationRegistrySavedData registry(int count) {
        StationRegistrySavedData data = new StationRegistrySavedData();
        List<UUID> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID id = UUID.randomUUID();
            data.reserve(id, UUID.randomUUID(), "Scale " + index, ModIdentity.id("earth"), index);
            data.commit(id);
            ids.add(id);
        }
        assertEquals(count, data.stations().size());
        return data;
    }
}
