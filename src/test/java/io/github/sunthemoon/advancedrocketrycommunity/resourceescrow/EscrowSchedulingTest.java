package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowSchedulingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void capturePendingBytesTickTypeAndLevelLimitsAreIndependent() {
        var budget = new EscrowBarrierBudget();
        for (int i = 0; i < 32; i++) {
            assertTrue(budget.reserve("level" + i, false, i)); budget.captured(0);
            assertFalse(budget.reserve("level" + i, true, i), "More than one serialization in a tick");
        }
        assertEquals(32, budget.pending()); assertFalse(budget.reserve("other", true, 100));
        budget.release(0); assertTrue(budget.reserve("other", true, 100)); budget.captured(0);
        var memory = new EscrowBarrierBudget();
        for (int i = 0; i < 4; i++) { assertTrue(memory.reserve("memory", false, i)); memory.captured(4_194_304); }
        assertEquals(16_777_216, memory.bytes()); assertFalse(memory.reserve("memory", false, 4));
        memory.release(4_194_304); assertTrue(memory.reserve("memory", false, 4));
        assertThrows(IllegalArgumentException.class, () -> memory.captured(4_194_305)); memory.captured(4_194_304);
        for (boolean entity : new boolean[] {false, true}) {
            var perType = new EscrowBarrierBudget();
            for (int i = 0; i < 16; i++) { assertTrue(perType.reserve("level", entity, i)); perType.captured(0); perType.release(0); }
            assertFalse(perType.reserve("level", entity, 16));
            assertTrue(perType.reserve("level", !entity, 16)); perType.captured(0); perType.release(0);
            assertTrue(perType.reserve("level", entity, 20));
        }
        var levels = new EscrowBarrierBudget();
        for (int i = 0; i < 64; i++) { assertTrue(levels.reserve("level" + i, false, i)); levels.captured(0); levels.release(0); }
        assertFalse(levels.reserve("level64", false, 64)); assertTrue(levels.reserve("level0", false, 64));
    }
    @Test void rotatingEightVisitQueueSkipsBlockedAndUnloadedWorkAtAll64Records() {
        var records = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 64; i++) { records.add(EscrowTestFixtures.operation(i)); }
        records.set(0, records.get(0).withReason(EscrowReason.OTHER_STATE));
        var ledger = new EscrowLedger(EscrowTestFixtures.id(500), 64, 1, records, List.of(), List.of());
        for (int restart = 0; restart < 3; restart++) {
            var visits = new ArrayList<Long>();
            var scheduler = new EscrowReconciliationScheduler(() -> ledger, (id, reason) -> fail("Unexpected transition"), (op, all, done) -> {
                visits.add(op.sequence()); done.accept(new EscrowReconciliationScheduler.Result(EscrowReason.WAITING_UNLOADED, false));
            });
            for (int tick = 0; tick < 8; tick++) {
                int before = visits.size(); scheduler.tick(tick); assertTrue(visits.size() - before <= 8);
            }
            assertEquals(63, visits.size()); assertEquals(63, visits.stream().distinct().count()); assertFalse(visits.contains(1L));
        }
    }
    @Test void threeFailuresPersistStalledAndOnlyAValidProofClearsItAcrossRestarts() {
        var op = EscrowTestFixtures.operation(1); var ledger = new AtomicReference<>(EscrowTestFixtures.ledger(op));
        var reasons = new ArrayList<EscrowReason>(); var visits = new ArrayList<Long>();
        var scheduler = new EscrowReconciliationScheduler(ledger::get, (id, reason) -> {
            reasons.add(reason); ledger.set(EscrowTestFixtures.ledger(ledger.get().records().get(0).withReason(reason)));
        }, (record, all, done) -> { visits.add(record.sequence()); done.accept(new EscrowReconciliationScheduler.Result(EscrowReason.BARRIER_IO, false)); });
        scheduler.tick(0); scheduler.tick(99); assertEquals(1, visits.size()); assertTrue(reasons.isEmpty());
        scheduler.tick(100); scheduler.tick(499); assertEquals(2, visits.size()); assertTrue(reasons.isEmpty());
        scheduler.tick(500); assertEquals(List.of(EscrowReason.BARRIER_IO), reasons);
        assertEquals(EscrowOperation.Phase.STALLED, ledger.get().records().get(0).phase());
        scheduler.tick(1699); assertEquals(3, visits.size()); scheduler.tick(1700); assertEquals(4, visits.size());
        var persisted = EscrowCodec.decode(EscrowCodec.encode(ledger.get()));
        for (EscrowReason reason : List.of(EscrowReason.WAITING_UNLOADED, EscrowReason.NONE, EscrowReason.BARRIER_VETO)) {
            var retry = new EscrowReconciliationScheduler(() -> persisted, (id, next) -> assertEquals(EscrowReason.BARRIER_VETO, next),
                    (record, all, done) -> done.accept(new EscrowReconciliationScheduler.Result(reason, false)));
            retry.tick(0);
            assertEquals(EscrowOperation.Phase.STALLED, persisted.records().get(0).phase());
        }
        var recovered = new EscrowReconciliationScheduler(() -> persisted, (id, next) -> assertEquals(EscrowReason.NONE, next),
                (record, all, done) -> done.accept(new EscrowReconciliationScheduler.Result(EscrowReason.NONE, true)));
        recovered.tick(0);
        for (var manual : EscrowReason.values()) {
            if (!manual.manual()) { continue; }
            var repaired = EscrowTestFixtures.ledger(op.withReason(manual));
            var locked = new EscrowReconciliationScheduler(() -> repaired, (id, next) -> fail("Manual transition"),
                    (record, all, done) -> fail("Manual retry")); locked.tick(0); locked.tick(2000);
        }
        assertTrue(EscrowReason.SUPERSEDING_SAVE.retryableProofFailure());
        assertFalse(EscrowReason.SUPERSEDING_SAVE.barrierFailure());
    }
}
