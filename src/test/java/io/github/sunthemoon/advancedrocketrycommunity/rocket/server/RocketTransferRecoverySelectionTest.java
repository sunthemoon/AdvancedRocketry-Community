package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * WARP review R7 and final review A1: recovery checks one record per call (its loaded check may load
 * chunks), in round-robin order, so a record whose ends cannot be loaded never starves the others.
 */
final class RocketTransferRecoverySelectionTest {
    @Test
    void aStuckRecordDoesNotStarveTheRecordBehindItAndEachCallChecksOneRecord() {
        RocketTransferRecord stuck = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        RocketTransferRecord ready = RocketStationMotionRuleTest.record(new RocketPosition(92, 80, 8));
        List<RocketTransferRecord> entries = List.of(stuck, ready);
        AtomicInteger checks = new AtomicInteger();
        var first = RocketTransferRecoveryService.step(entries, Set.of(), Set.of(), 0, record -> {
            checks.incrementAndGet();
            return record == ready;
        });
        assertSame(stuck, first.candidate());
        assertFalse(first.ready(), "The stuck record is retried later");
        assertEquals(1, checks.get(), "Only one record's ends may be checked (and loaded) per call");
        var second = RocketTransferRecoveryService.step(entries, Set.of(), Set.of(), first.cursor(), record -> {
            checks.incrementAndGet();
            return record == ready;
        });
        assertSame(ready, second.candidate(), "The record behind the stuck one is reached");
        assertTrue(second.ready());
        assertEquals(2, checks.get());
    }

    @Test
    void journalOrderDecidesWhileEveryRecordLoadsAndTheCursorWraps() {
        RocketTransferRecord a = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        RocketTransferRecord b = RocketStationMotionRuleTest.record(new RocketPosition(92, 80, 8));
        var step = RocketTransferRecoveryService.step(List.of(a, b), Set.of(), Set.of(), 0, record -> true);
        assertSame(a, step.candidate());
        assertEquals(0, step.cursor(), "A ready record keeps the cursor; recovery classifies it");
        var wrapped = RocketTransferRecoveryService.step(List.of(a, b), Set.of(), Set.of(), 5, record -> false);
        assertSame(b, wrapped.candidate(), "The cursor wraps around the unclassified records");
        assertEquals(2, wrapped.cursor());
    }

    @Test
    void liveAndSettledRecordsAreNeverSelectedAndAnEmptyJournalChecksNothing() {
        RocketTransferRecord live = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        RocketTransferRecord settled = RocketStationMotionRuleTest.record(new RocketPosition(92, 80, 8));
        RocketTransferRecord open = RocketStationMotionRuleTest.record(new RocketPosition(112, 80, 8));
        Set<UUID> liveIds = Set.of(live.transferId());
        Set<UUID> settledIds = Set.of(settled.transferId());
        for (int cursor = 0; cursor < 4; cursor++) {
            assertSame(open, RocketTransferRecoveryService.step(List.of(live, settled, open), liveIds, settledIds,
                    cursor, record -> true).candidate());
        }
        AtomicInteger checks = new AtomicInteger();
        var none = RocketTransferRecoveryService.step(List.of(live, settled), liveIds, settledIds, 3, record -> {
            checks.incrementAndGet();
            return true;
        });
        assertNull(none.candidate());
        assertEquals(0, checks.get());
    }
}
