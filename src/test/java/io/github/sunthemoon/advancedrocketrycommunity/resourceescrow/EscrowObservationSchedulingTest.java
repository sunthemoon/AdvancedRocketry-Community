package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowObservationSchedulingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void liveMarkersNeedNoNativeObservationAndAcknowledgedRetirementInvalidatesTheNegativeCache() {
        var f = EscrowReconciliationBindingBoundaryTest.sample();
        var cache = new EscrowReconciliationPruning.Candidates(); var observations = new AtomicInteger();
        var wrapper = new RocketEscrowSection(f.ledger().id(), 2, List.of(
                new EscrowPrefix.Link(f.a(), f.a().destination(), "DESTINATION").marker(),
                new EscrowPrefix.Link(f.b(), f.b().destination(), "DESTINATION").marker()));
        for (int tick = 0; tick < 1_000; tick++) { cache.observe(f.ledger(), wrapper, observations::incrementAndGet); }
        assertEquals(0, observations.get(), "Live markers caused a full native census/hash");
        var retired = f.ledger().retire(Set.of(f.a().id())).generation(f.ledger().writerGeneration() + 1);
        cache.observe(retired, wrapper, observations::incrementAndGet);
        assertEquals(1, observations.get(), "The exact acknowledged retirement must enable restart cleanup");
        var pruned = new RocketEscrowSection(retired.id(), 2, List.of(wrapper.markers().get(1)));
        cache.observe(retired, pruned, observations::incrementAndGet);
        assertEquals(1, observations.get(), "A wrapper change must not reuse the old positive candidate");
        cache.observe(retired.retire(Set.of(f.b().id())).generation(retired.writerGeneration() + 1), pruned, observations::incrementAndGet);
        assertEquals(2, observations.get());
    }

    @Test void positiveCandidatesAlwaysRevalidateNativeLoadAndStoreChanges() {
        var op = EscrowTestFixtures.operation(1); var live = EscrowTestFixtures.ledger(op);
        var marker = new EscrowPrefix.Link(op, op.destination(), "DESTINATION").marker();
        var wrapper = new RocketEscrowSection(live.id(), 1, List.of(marker));
        var root = live.retire(Set.of(op.id())); var cache = new EscrowReconciliationPruning.Candidates();
        var observations = new AtomicInteger(); var loaded = new AtomicReference<>(false);
        Runnable nativeCheck = () -> { observations.incrementAndGet(); if (!loaded.get()) { return; } };
        cache.observe(root, wrapper, nativeCheck); loaded.set(true);
        cache.observe(root, wrapper, nativeCheck);
        assertEquals(2, observations.get(), "An unsuccessful unloaded observation is not authority for the next load");
        assertThrows(IllegalStateException.class, () -> cache.observe(root, wrapper,
                () -> { observations.incrementAndGet(); throw new IllegalStateException("superseding store"); }));
        cache.observe(root, wrapper, nativeCheck); assertEquals(4, observations.get());
    }

    @Test void settlementReceiptAndEmptyWrapperNeverEnterOrdinaryPruning() {
        var receipt = EscrowTestFixtures.receipt(1); var op = EscrowTestFixtures.operation(10_001);
        var root = new EscrowLedger(EscrowTestFixtures.id(500), op.sequence(), 1, List.of(), List.of(), List.of(receipt));
        var wrapper = new RocketEscrowSection(root.id(), 1, List.of(
                new EscrowPrefix.Marker(receipt.getUUID("op_id"), op.sequence(), "DESTINATION", 1)));
        var cache = new EscrowReconciliationPruning.Candidates();
        cache.observe(root, wrapper, () -> fail("Settlement marker entered ordinary cleanup"));
        cache.observe(root, new RocketEscrowSection(root.id(), 1, List.of()), () -> fail("Empty wrapper observed"));
    }

    @Test void immutableIntentIsDecodedOncePerComponentAndAcknowledgedRootNotPerTickOrMember() {
        var first = EscrowTestFixtures.operation(1, 99, 0).withReason(EscrowReason.OTHER_STATE);
        var second = EscrowTestFixtures.operation(2, 99, 1).withReason(EscrowReason.OTHER_STATE);
        var root = EscrowTestFixtures.ledger(first, second);
        var observed = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, first.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observed);
        var frozen = intent.freeze(root); var decodes = new AtomicInteger();
        try (var recovery = new EscrowSettlementRecovery(tag -> { decodes.incrementAndGet(); return EscrowSettlementIntent.decode(tag); })) {
            var result = recovery.intents(frozen);
            for (int tick = 0; tick < 1_000; tick++) { assertSame(result, recovery.intents(frozen)); }
            assertEquals(1, decodes.get()); assertEquals(List.of(intent), result);
            assertThrows(UnsupportedOperationException.class, () -> result.clear());
            assertNotSame(result, recovery.intents(frozen.generation(1))); assertEquals(2, decodes.get());
            assertTrue(recovery.intents(root).isEmpty());
            assertEquals(List.of(intent), recovery.intents(frozen)); assertEquals(3, decodes.get());
        }
    }

    @Test void legacySettlingPhaseWithoutAnIntentRemainsLockedWithoutDecodingAnEmptyTag() {
        var op = EscrowTestFixtures.operation(1);
        var legacy = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(),
                EscrowOperation.Phase.SETTLING_ACCEPT, EscrowReason.NONE, op.payload(), op.payloadHash(),
                op.source(), op.destination(), op.guard());
        var root = EscrowTestFixtures.ledger(legacy);
        try (var recovery = new EscrowSettlementRecovery(tag -> { fail("Empty legacy intent decoded"); return null; })) {
            assertTrue(recovery.intents(root).isEmpty());
            assertEquals(List.of(legacy), root.records());
        }
    }

    @Test void rootIndexRefreshPreservesIndependentRetryHistoryAndRemovesRetiredWork() {
        var ops = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 64; i++) { ops.add(EscrowTestFixtures.operation(i)); }
        var root = new AtomicReference<>(new EscrowLedger(EscrowTestFixtures.id(500), 64, 1, ops, List.of(), List.of()));
        var visits = new ArrayList<Long>();
        var scheduler = new EscrowReconciliationScheduler(root::get, (id, reason) -> fail(), (op, all, done) -> {
            visits.add(op.sequence()); done.accept(new EscrowReconciliationScheduler.Result(EscrowReason.BARRIER_IO, false));
        });
        scheduler.tick(0); assertEquals(8, visits.size());
        root.set(root.get().retire(Set.of(ops.get(0).id())).generation(2));
        for (int tick = 1; tick <= 8; tick++) {
            int before = visits.size(); scheduler.tick(tick); assertTrue(visits.size() - before <= 8);
        }
        assertEquals(64, visits.size()); assertEquals(64, visits.stream().distinct().count());
        assertEquals(1, scheduler.view(ops.get(1).id()).failures());
        assertEquals(100, scheduler.view(ops.get(1).id()).nextTick());
        for (int tick = 9; tick < 100; tick++) { scheduler.tick(tick); }
        assertEquals(64, visits.size(), "A new generation reset backoff or queue position");
    }

    @Test void barrierAvailabilityDoesNotSpendAnyBudgetAndCannotBypassTheFinalReservation() {
        var budget = new EscrowBarrierBudget();
        for (int i = 0; i < 100; i++) { assertTrue(budget.available("level", true, 0)); }
        assertEquals(0, budget.pending()); assertEquals(0, budget.bytes());
        assertTrue(budget.reserve("level", false, 0)); budget.captured(0);
        assertFalse(budget.available("level", true, 0)); assertFalse(budget.reserve("level", true, 0));
        for (int i = 1; i < 32; i++) {
            assertTrue(budget.available("level" + i, false, i));
            assertTrue(budget.reserve("level" + i, false, i)); budget.captured(0);
        }
        assertFalse(budget.available("other", true, 100)); budget.release(0);
        assertTrue(budget.available("other", true, 100)); assertTrue(budget.reserve("other", true, 100));
    }

    @Test void componentAndLevelCapacityRefusalsAreRetryableButInvalidThreadOrLifetimeIsNotCapacity() {
        assertDoesNotThrow(() -> EscrowReconciliationView.requireCaptureBounds(true, 8));
        var component = assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationView.requireCaptureBounds(true, 9));
        assertEquals(EscrowReason.BARRIER_BOUNDS, component.reason); assertFalse(component.reason.manual());
        assertDoesNotThrow(() -> EscrowReconciliationView.requireStoreCapacity(false, false, 63));
        assertDoesNotThrow(() -> EscrowReconciliationView.requireStoreCapacity(false, true, 64));
        var level = assertThrows(EscrowReconciliationView.Refusal.class,
                () -> EscrowReconciliationView.requireStoreCapacity(false, false, 64));
        assertEquals(EscrowReason.BARRIER_BOUNDS, level.reason); assertTrue(level.reason.retryableProofFailure());
        assertEquals(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class,
                () -> EscrowReconciliationView.requireCaptureBounds(false, 9)).getClass());
        assertEquals(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class,
                () -> EscrowReconciliationView.requireStoreCapacity(true, false, 64)).getClass());
    }
}
