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
