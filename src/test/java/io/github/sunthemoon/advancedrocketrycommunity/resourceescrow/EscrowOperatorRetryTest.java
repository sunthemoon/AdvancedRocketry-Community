package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowOperatorRetryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void accelerationPreservesFailureProgressionAndDoesNotRunFromTheCommand() {
        var op = EscrowTestFixtures.operation(1); var root = EscrowTestFixtures.ledger(op);
        var attempts = new AtomicInteger();
        var scheduler = new EscrowReconciliationScheduler(() -> root, (id, reason) -> { }, (value, all, done) -> {
            attempts.incrementAndGet(); done.accept(new EscrowReconciliationScheduler.Result(EscrowReason.BARRIER_IO, false));
        });
        scheduler.tick(0);
        assertEquals(100, scheduler.view(op.id()).nextTick());
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, scheduler.accelerate(op.id(), 1));
        assertEquals(1, attempts.get()); assertEquals(1, scheduler.view(op.id()).failures());
        assertEquals(EscrowOperatorRetry.Outcome.PENDING, scheduler.accelerate(op.id(), 1));
        scheduler.tick(1);
        assertEquals(2, scheduler.view(op.id()).failures()); assertEquals(401, scheduler.view(op.id()).nextTick());
        scheduler.tick(401);
        assertEquals(3, scheduler.view(op.id()).failures()); assertEquals(1601, scheduler.view(op.id()).nextTick());
    }
    @Test void commandCooldownIsPerOperationAndDoesNotInvokeTheQueueEarly() {
        var first = EscrowTestFixtures.operation(1); var second = EscrowTestFixtures.operation(2);
        var root = EscrowTestFixtures.ledger(first, second); var commands = new EscrowOperatorRetry();
        var requests = new AtomicInteger();
        java.util.function.Supplier<EscrowOperatorRetry.Outcome> accept = () -> { requests.incrementAndGet(); return EscrowOperatorRetry.Outcome.ACCEPTED; };
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, commands.request(root, first.id(), 10, accept).outcome());
        assertEquals(EscrowOperatorRetry.Outcome.COOLDOWN, commands.request(root, first.id(), 209, accept).outcome());
        assertEquals(210, commands.request(root, first.id(), 209, accept).nextTick());
        assertEquals(1, requests.get());
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, commands.request(root, second.id(), 209, accept).outcome());
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, commands.request(root, first.id(), 210, accept).outcome());
        assertEquals(EscrowOperatorRetry.Outcome.REFUSED, commands.request(root, EscrowTestFixtures.id(3), 1000, accept).outcome());
        assertEquals(3, requests.get());
    }
    @Test void settledChainMembersShareTheirIntentCooldown() {
        var first = EscrowTestFixtures.operation(1, 99, 0).withReason(EscrowReason.OTHER_STATE);
        var second = EscrowTestFixtures.operation(2, 99, 1).withReason(EscrowReason.OTHER_STATE);
        var unrelated = EscrowTestFixtures.operation(3);
        var chain = EscrowTestFixtures.ledger(first, second); var observations = EscrowSettlementIntentTest.observations(chain, false);
        var preview = EscrowSettlementIntent.preview(chain, first.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 0, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 1, observations);
        var frozen = intent.freeze(chain).append(List.of(unrelated));
        assertEquals(java.util.Set.of(first.id(), second.id()), intent.ids());
        var state = new EscrowSettlementRecovery.Work(); state.lastReason = "BARRIER_IO"; state.retryAt = 500;
        var commands = new EscrowOperatorRetry(); var requests = new AtomicInteger(); var tick = new java.util.concurrent.atomic.AtomicLong();
        java.util.function.Supplier<EscrowOperatorRetry.Outcome> settle = () -> { requests.incrementAndGet(); return state.accelerate(tick.get()); };
        tick.set(10);
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, commands.request(frozen, first.id(), 10, settle).outcome());
        assertEquals(10, state.retryAt);
        state.retryAt = 11 + 200; // The accelerated attempt failed again and stalled.
        tick.set(11);
        var other = commands.request(frozen, second.id(), 11, settle);
        assertEquals(EscrowOperatorRetry.Outcome.COOLDOWN, other.outcome()); assertEquals(210, other.nextTick());
        assertEquals(1, requests.get()); assertEquals(211, state.retryAt);
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, commands.request(frozen, unrelated.id(), 11,
                () -> EscrowOperatorRetry.Outcome.ACCEPTED).outcome());
        tick.set(210);
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, commands.request(frozen, second.id(), 210, settle).outcome());
        assertEquals(2, requests.get()); assertEquals(210, state.retryAt);
        assertEquals(EscrowOperatorRetry.Outcome.COOLDOWN, commands.request(frozen, first.id(), 211, settle).outcome());
        assertEquals(2, requests.get());
    }
    @Test void sixtyFourAccelerationsRetainTheEightVisitBudgetAndQueueOrder() {
        var ops = new ArrayList<EscrowOperation>(); for (int i = 1; i <= 64; i++) { ops.add(EscrowTestFixtures.operation(i)); }
        var root = EscrowTestFixtures.ledger(ops.toArray(EscrowOperation[]::new)); var visits = new ArrayList<Long>();
        var scheduler = new EscrowReconciliationScheduler(() -> root, (id, reason) -> fail(), (op, all, done) -> {
            visits.add(op.sequence()); done.accept(new EscrowReconciliationScheduler.Result(EscrowReason.BARRIER_IO, false));
        });
        for (int tick = 0; tick < 8; tick++) { scheduler.tick(tick); }
        assertEquals(64, visits.size()); visits.clear();
        for (var op : ops) { assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, scheduler.accelerate(op.id(), 8)); }
        for (int tick = 8; tick < 16; tick++) {
            int before = visits.size(); scheduler.tick(tick); assertEquals(8, visits.size() - before);
        }
        assertEquals(ops.stream().map(EscrowOperation::sequence).toList(), visits);
    }
    @Test void manualVetoInvalidRetiredAndInflightWorkCannotBeAccelerated() {
        for (var reason : List.of(EscrowReason.OTHER_STATE, EscrowReason.RELOCATED_ENDPOINT,
                EscrowReason.BARRIER_VETO, EscrowReason.BARRIER_BOUNDS, EscrowReason.BARRIER_EXTERNAL)) {
            var op = EscrowTestFixtures.operation(1).withReason(reason); var root = EscrowTestFixtures.ledger(op);
            var scheduler = new EscrowReconciliationScheduler(() -> root, (id, next) -> { }, (value, all, done) ->
                    done.accept(new EscrowReconciliationScheduler.Result(reason, false)));
            scheduler.tick(0);
            var before = EscrowCodec.encode(root);
            assertEquals(EscrowOperatorRetry.Outcome.REFUSED, scheduler.accelerate(op.id(), 1));
            assertEquals(before, EscrowCodec.encode(root));
        }
        var op = EscrowTestFixtures.operation(1); var root = EscrowTestFixtures.ledger(op);
        var callback = new AtomicReference<Consumer<EscrowReconciliationScheduler.Result>>();
        var scheduler = new EscrowReconciliationScheduler(() -> root, (id, next) -> fail(), (value, all, done) -> callback.set(done));
        scheduler.tick(0);
        assertEquals(EscrowOperatorRetry.Outcome.PENDING, scheduler.accelerate(op.id(), 1));
        assertTrue(scheduler.view(op.id()).active()); assertNotNull(callback.get());
        assertEquals(EscrowOperatorRetry.Outcome.REFUSED, scheduler.accelerate(EscrowTestFixtures.id(999), 1));
    }
    @Test void settlementRetryWhitelistDoesNotTreatFrozenMismatchOrVetoAsRetryable() {
        for (String reason : List.of("BARRIER_IO", "BARRIER_FORCE", "BARRIER_UNSTABLE", "SUPERSEDING_SAVE",
                "O3_WRITE_UNACKNOWLEDGED", "O4_WRITE_UNACKNOWLEDGED",
                "WAITING_UNLOADED: endpoint slot must be FULL, entity-loaded and ticking", "Endpoint Level is unavailable")) {
            assertTrue(EscrowSettlementRecovery.retryable(reason));
        }
        for (String reason : List.of("BARRIER_VETO", "BARRIER_EXTERNAL", "BARRIER_BOUNDS", "Frozen settlement endpoint changed",
                "Frozen matching journal changed", "REPAIR", "Unknown")) { assertFalse(EscrowSettlementRecovery.retryable(reason)); }
        assertFalse(EscrowSettlementRecovery.retryable(null));
    }
    @Test void settlementAccelerationOnlyAdvancesTheWaitAndNeverCancelsAnUnknownWrite() {
        var state = new EscrowSettlementRecovery.Work(); state.phase = "O4";
        state.lastReason = "O4_WRITE_UNACKNOWLEDGED"; state.retryAt = 500;
        state.writing = true;
        assertEquals(EscrowOperatorRetry.Outcome.PENDING, state.accelerate(10));
        assertEquals(500, state.retryAt); assertTrue(state.writing);
        state.writing = false;
        assertEquals(EscrowOperatorRetry.Outcome.ACCEPTED, state.accelerate(10));
        assertEquals(10, state.retryAt); assertEquals("O4", state.phase); assertEquals("O4_WRITE_UNACKNOWLEDGED", state.lastReason);
        assertEquals(EscrowOperatorRetry.Outcome.PENDING, state.accelerate(10));
        state.lastReason = "BARRIER_VETO"; state.retryAt = 600;
        assertEquals(EscrowOperatorRetry.Outcome.REFUSED, state.accelerate(11)); assertEquals(600, state.retryAt);
    }
}
