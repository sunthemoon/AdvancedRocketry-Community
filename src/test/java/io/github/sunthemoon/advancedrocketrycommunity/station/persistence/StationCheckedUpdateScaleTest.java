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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ORBIT-04 bounded measurement, not a load campaign: a checked gravity write (encode, validate,
 * stage, force, read back, atomic replace, publish) at 10, 100 and the 4,096-station limit, against
 * the 500 ms single-spike budget (docs/17), and constant-time region lookup at the limit.
 */
final class StationCheckedUpdateScaleTest {
    private static final long SPIKE_BUDGET_NANOS = 500_000_000L;
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
            long[] samples = new long[3];
            for (int run = 0; run < samples.length; run++) {
                StationState observed = data.find(target.stationId()).orElseThrow();
                long start = System.nanoTime();
                CheckedUpdate result = data.checkedSetGravity(file, observed, 100 + run * 100,
                        CheckedSavedDataFile::atomicMove);
                samples[run] = System.nanoTime() - start;
                assertEquals(CheckedUpdate.COMMITTED, result);
            }
            Arrays.sort(samples);
            long median = samples[1];
            System.out.printf("ARCE_ORBIT04_CHECKED_WRITE stations=%d file_bytes=%d median_ms=%.1f max_ms=%.1f%n",
                    count, Files.size(file), median / 1e6, samples[2] / 1e6);
            assertTrue(median < SPIKE_BUDGET_NANOS, count + " stations: median checked write " + median / 1e6 + " ms");
            assertEquals(300, data.find(target.stationId()).orElseThrow().environment().gravityMilli());
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
            long saveStart = System.nanoTime();
            CompoundTag saved = data.save(new CompoundTag());
            long saveNanos = System.nanoTime() - saveStart;
            CheckedSavedDataFile.replace(file, ManagedSavedDataType.STATIONS, () -> saved);
            StationState target = data.stations().get(count / 2);
            long[] samples = new long[3];
            String[] bodies = {"moon", "earth", "moon"};
            for (int run = 0; run < samples.length; run++) {
                StationState observed = data.find(target.stationId()).orElseThrow();
                long start = System.nanoTime();
                CheckedUpdate result = data.checkedRelocation(file, observed, ModIdentity.id(bodies[run]), 100_000,
                        CheckedSavedDataFile::atomicMove);
                samples[run] = System.nanoTime() - start;
                assertEquals(CheckedUpdate.COMMITTED, result);
            }
            Arrays.sort(samples);
            System.out.printf("ARCE_WARP05_SCALE stations=%d balances=%d file_bytes=%d save_ms=%.1f"
                            + " commit_median_ms=%.1f commit_max_ms=%.1f%n", count, data.warpEnergyBalances().size(),
                    Files.size(file), saveNanos / 1e6, samples[1] / 1e6, samples[2] / 1e6);
            assertTrue(samples[1] < SPIKE_BUDGET_NANOS, count + " stations: median warp commit " + samples[1] / 1e6 + " ms");
            assertTrue(saveNanos < SPIKE_BUDGET_NANOS, count + " stations: ordinary save " + saveNanos / 1e6 + " ms");
            assertEquals(StationLimits.MAX_WARP_ENERGY - 300_000, data.warpEnergy(target.stationId()));
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
