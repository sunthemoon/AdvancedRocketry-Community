package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RocketTransferFencesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void loadedOnlyRegionReadIsBoundedAtSixteenColumnsAndRejectsOversizeBeforeReading() {
        var calls = new AtomicInteger();
        assertTrue(RocketTransferEntities.columnsAlreadyLoaded(0, 3, 0, 3, (x, z) -> { calls.incrementAndGet(); return true; }));
        assertEquals(16, calls.get());
        assertFalse(RocketTransferEntities.columnsAlreadyLoaded(0, 16, 0, 0, (x, z) -> { fail("Oversize region was inspected"); return true; }));
        assertFalse(RocketTransferEntities.columnsAlreadyLoaded(Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0,
                (x, z) -> { fail("Overflow region was inspected"); return true; }));
        assertFalse(RocketTransferEntities.columnsAlreadyLoaded(0, 3, 0, 3, (x, z) -> x != 2 || z != 2));
    }

    @Test void fencedSelectionNeverCallsTheTicketAddingPredicateAndPreservesFairness() {
        var fenced = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        var healthy = RocketStationMotionRuleTest.record(new RocketPosition(92, 80, 8));
        var calls = new AtomicInteger();
        for (int attempt = 0; attempt < 1_200; attempt++) {
            var step = RocketTransferRecoveryService.step(List.of(fenced), Set.of(), Set.of(), Set.of(fenced.transferId()), 0,
                    record -> { calls.incrementAndGet(); return true; });
            assertNull(step.candidate());
        }
        assertEquals(0, calls.get());
        var step = RocketTransferRecoveryService.step(List.of(fenced, healthy), Set.of(), Set.of(), Set.of(fenced.transferId()), 0,
                record -> { calls.incrementAndGet(); return true; });
        assertSame(healthy, step.candidate()); assertEquals(1, calls.get());
    }

    @Test void retriesHaveBothTickAndTransferBoundsIncludingRepeatedOperatorAttempts() {
        var fences = new RocketTransferFences(); var reads = new AtomicInteger();
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID();
        for (var id : List.of(a, b, c)) {
            fences.reevaluate(id, 0, () -> { reads.incrementAndGet(); return "WAITING_UNLOADED"; }, live, settled);
        }
        assertEquals(RocketTransferFences.RETRIES_PER_TICK, reads.get());
        for (int attempt = 0; attempt < 100; attempt++) {
            assertFalse(fences.reevaluate(a, 1, () -> { reads.incrementAndGet(); return null; }, live, settled));
        }
        assertEquals(2, reads.get()); assertTrue(fences.ids().contains(a));
        assertFalse(fences.reevaluate(a, RocketTransferFences.RETRY_TICKS - 1L,
                () -> { reads.incrementAndGet(); return null; }, live, settled));
        assertTrue(fences.reevaluate(a, RocketTransferFences.RETRY_TICKS,
                () -> { reads.incrementAndGet(); return null; }, live, settled));
        assertEquals(3, reads.get()); assertFalse(fences.ids().contains(a));
        assertFalse(fences.reevaluate(a, RocketTransferFences.RETRY_TICKS + 1L, () -> null, live, settled));
    }

    @Test void classificationPersistsAcrossReadOnlyObservationAndAnUnrelatedWarpStaysAvailable() {
        var record = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        var fences = new RocketTransferFences(); var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        fences.reevaluate(record.transferId(), 0, () -> "WAITING_UNLOADED", live, settled);
        var unrelated = new RocketStationMotionRule.Region(record.destinationSnapshot().sourceDimension(), 1_000, 1_000, 1_511, 1_511);
        for (int tick = 1; tick <= 1_200; tick++) {
            fences.reevaluate(record.transferId(), tick, () -> {
                assertTrue(fences.ids().contains(record.transferId()), "Observation must not temporarily unclassify");
                return "WAITING_UNLOADED";
            }, live, settled);
            assertFalse(RocketStationMotionRule.blocks(true, List.of(record), fences.ids()::contains, fences.ids()::contains, tick, unrelated));
        }
        assertTrue(RocketStationMotionRule.blocks(true, List.of(record), fences.ids()::contains, fences.ids()::contains, 1_200,
                new RocketStationMotionRule.Region(record.destinationSnapshot().sourceDimension(), 0, 0, 511, 511)));
    }
}
