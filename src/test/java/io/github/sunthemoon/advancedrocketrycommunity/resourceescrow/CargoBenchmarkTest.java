package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CargoBenchmarkTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void fourDeviceFixturesRetainRealEnvelopeEligibilityAt2048BlocksAnd128Payloads() throws Exception {
        for (boolean item : List.of(false, true)) {
            for (boolean load : List.of(true, false)) {
                var c = CargoBenchmarkFixtures.expand(CargoBenchmarkFixtures.small(item, load, -1), 128 * 512);
                assertEquals(2048, c.before().blocks().size());
                assertEquals(128, c.before().blocks().stream().filter(b -> b.blockEntityPayload().isPresent()).count());
                assertEquals(128 * 512, CargoBenchmarkFixtures.aggregate(c.before()));
                assertEquals(RocketCargoSelection.Status.ELIGIBLE, c.check().status());
                var samples = new ArrayList<Map<String, Object>>(); CargoBenchmark.measure(c, 0, samples);
                assertEquals(Set.of("prepare_tick", "capture_tick", "recompute_tick", "readback_hash_io", "visual_encode_main"),
                        CargoBenchmark.summarize(samples).keySet());
            }
        }
    }
    @Test void exactAggregateBoundaryIsNotMislabelledAsAnAdmittedWholeSnapshot() {
        for (boolean item : List.of(false, true)) {
            var seed = CargoBenchmarkFixtures.small(item, true, -1);
            var boundary = CargoBenchmarkFixtures.snapshot(seed, CargoBenchmarkFixtures.AGGREGATE);
            assertEquals(1_048_576, CargoBenchmarkFixtures.aggregate(boundary));
            var refused = assertThrows(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException.class,
                    () -> io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec.encode(boundary));
            assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE, refused.code());
            assertFalse(CargoBenchmarkFixtures.fits(seed, 1_048_576));
        }
    }
    @Test void largestFixtureHasAnAdjacentRefusalAndExactWholeCodecBoundary() {
        var seed = CargoBenchmarkFixtures.small(false, true, -1);
        int largest = CargoBenchmarkFixtures.largest(seed);
        var c = CargoBenchmarkFixtures.expand(seed, largest);
        assertTrue(largest < 1_048_576);
        assertEquals(1_048_576, RocketNbtSize.uncompressedBytes(c.rocketAfter().getCompound("snapshot")));
        assertEquals(RocketCargoSelection.Status.ELIGIBLE, c.check().status());
        assertFalse(CargoBenchmarkFixtures.fits(seed, largest + 1));
    }
    @Test void missingPhasesAndAnyOwnerThreadBudgetMissCannotPass() {
        assertFalse(CargoBenchmark.within(Map.of()));
        var rows = new ArrayList<Map<String, Object>>();
        for (String phase : List.of("prepare_tick", "capture_tick", "recompute_tick", "visual_encode_main")) {
            rows.add(Map.of("phase", phase, "wall_ns", 25_000_000));
        }
        assertTrue(CargoBenchmark.within(CargoBenchmark.summarize(rows)));
        rows.add(Map.of("phase", "capture_tick", "wall_ns", 100_000_001));
        assertFalse(CargoBenchmark.within(CargoBenchmark.summarize(rows)));
    }
}
