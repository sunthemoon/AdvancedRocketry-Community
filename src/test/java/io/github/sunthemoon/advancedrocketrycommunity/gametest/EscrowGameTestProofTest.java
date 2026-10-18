package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class EscrowGameTestProofTest {
    private record Result(boolean confirmed, EscrowReason reason) { }
    private static final Result SUPERSEDED = new Result(false, EscrowReason.SUPERSEDING_SAVE);
    private static final Result CONFIRMED = new Result(true, EscrowReason.NONE);

    @Test void confirmedProofIsDeliveredUnchangedOnce() {
        var clock = new AtomicLong(10);
        var delivered = new ArrayList<Result>();
        var proof = driver(clock, (tick, callback) -> { callback.accept(CONFIRMED); return true; }, delivered);
        proof.tick(); clock.set(10_000); proof.tick();
        assertEquals(1, proof.attempts());
        assertEquals(List.of(CONFIRMED), delivered);
        assertSame(CONFIRMED, delivered.get(0));
    }

    @Test void synchronousSupersessionCannotOverwriteTheRetryState() {
        var clock = new AtomicLong(10);
        var delivered = new ArrayList<Result>();
        var requested = new ArrayList<Long>();
        var proof = driver(clock, (tick, callback) -> {
            requested.add(tick); callback.accept(requested.size() == 1 ? SUPERSEDED : CONFIRMED); return true;
        }, delivered);
        proof.tick();
        for (long tick = 11; tick < 110; tick++) { clock.set(tick); proof.tick(); }
        assertEquals(List.of(10L), requested); assertTrue(delivered.isEmpty());
        clock.set(110); proof.tick();
        assertEquals(List.of(10L, 110L), requested); assertEquals(List.of(CONFIRMED), delivered);
    }

    @Test void asynchronousBackoffStartsAtRefusalNotAtCapture() {
        var clock = new AtomicLong(10);
        var delivered = new ArrayList<Result>();
        var callback = new AtomicReference<Consumer<Result>>();
        var proof = driver(clock, (tick, completed) -> { callback.set(completed); return true; }, delivered);
        proof.tick(); clock.set(40); proof.tick(); assertEquals(1, proof.attempts());
        callback.get().accept(SUPERSEDED);
        clock.set(139); proof.tick(); assertEquals(1, proof.attempts());
        clock.set(140); proof.tick(); assertEquals(2, proof.attempts());
        callback.get().accept(CONFIRMED); assertEquals(List.of(CONFIRMED), delivered);
    }

    @Test void thirdSupersessionIsTerminalWithTheUnchangedProductBackoffs() {
        var clock = new AtomicLong(0);
        var delivered = new ArrayList<Result>();
        var requested = new ArrayList<Long>();
        var proof = driver(clock, (tick, callback) -> { requested.add(tick); callback.accept(SUPERSEDED); return true; }, delivered);
        proof.tick();
        clock.set(EscrowReason.backoff(1) - 1); proof.tick(); assertEquals(1, proof.attempts());
        clock.incrementAndGet(); proof.tick(); assertEquals(2, proof.attempts());
        clock.addAndGet(EscrowReason.backoff(2) - 1); proof.tick(); assertEquals(2, proof.attempts());
        clock.incrementAndGet(); proof.tick(); assertEquals(3, proof.attempts());
        assertEquals(List.of(0L, 100L, 500L), requested);
        assertEquals(List.of(SUPERSEDED), delivered); assertSame(SUPERSEDED, delivered.get(0));
        clock.set(20_000); proof.tick(); assertEquals(3, proof.attempts());
    }

    @Test void everyOtherReasonIncludingAnUnconfirmedNoneStillReachesTheOriginalAssertion() {
        for (var reason : EscrowReason.values()) {
            if (reason == EscrowReason.SUPERSEDING_SAVE) { continue; }
            var clock = new AtomicLong(0);
            var delivered = new ArrayList<Result>();
            var failure = new Result(false, reason);
            var proof = driver(clock, (tick, callback) -> { callback.accept(failure); return true; }, delivered);
            proof.tick(); clock.set(20_000); proof.tick();
            assertEquals(1, proof.attempts(), reason.name());
            assertEquals(List.of(failure), delivered, reason.name()); assertSame(failure, delivered.get(0));
        }
    }

    @Test void budgetRefusalDoesNotConsumeAnAcceptedAttempt() {
        var clock = new AtomicLong(0);
        var delivered = new ArrayList<Result>();
        var proof = driver(clock, (tick, callback) -> {
            if (tick < 3) { return false; }
            callback.accept(CONFIRMED); return true;
        }, delivered);
        for (long tick = 0; tick < 3; tick++) { clock.set(tick); proof.tick(); assertEquals(0, proof.attempts()); }
        clock.set(3); proof.tick(); assertEquals(1, proof.attempts()); assertEquals(List.of(CONFIRMED), delivered);
    }

    @Test void assertionFailuresAndRequestExceptionsAreNotSwallowed() {
        var clock = new AtomicLong(0);
        var failure = new AssertionError("original assertion");
        var proof = new EscrowGameTestProof<Result>(clock::get,
                (tick, callback) -> { callback.accept(new Result(false, EscrowReason.BARRIER_IO)); return true; },
                Result::confirmed, Result::reason, result -> { throw failure; });
        assertSame(failure, assertThrows(AssertionError.class, proof::tick));
        var refused = driver(clock, (tick, callback) -> { throw failure; }, new ArrayList<>());
        assertSame(failure, assertThrows(AssertionError.class, refused::tick));
    }

    @Test void twoAttemptFixtureFailsOnSecondSupersessionWithoutWaitingForAnotherBackoff() {
        var clock = new AtomicLong(0);
        var delivered = new ArrayList<Result>();
        var proof = new EscrowGameTestProof<Result>(clock::get,
                (tick, callback) -> { callback.accept(SUPERSEDED); return true; },
                Result::confirmed, Result::reason, delivered::add, 2);
        proof.tick(); clock.set(99); proof.tick();
        assertEquals(1, proof.attempts()); assertTrue(delivered.isEmpty());
        clock.set(100); proof.tick();
        assertEquals(2, proof.attempts()); assertEquals(List.of(SUPERSEDED), delivered);
        clock.set(10_000); proof.tick(); assertEquals(2, proof.attempts());
    }

    @Test void preparationDeferralsDoNotConsumeAttemptsOrStartBackoff() {
        var clock = new AtomicLong(0);
        var delivered = new ArrayList<Result>();
        var requested = new ArrayList<Long>();
        var proof = new EscrowGameTestProof<Result>(clock::get, (tick, callback) -> {
            if (tick < 50 || tick >= 150 && tick < 160) { return false; }
            requested.add(tick); callback.accept(requested.size() == 1 ? SUPERSEDED : CONFIRMED); return true;
        }, Result::confirmed, Result::reason, delivered::add, 2);
        for (long tick = 0; tick <= 160; tick++) { clock.set(tick); proof.tick(); }
        assertEquals(List.of(50L, 160L), requested); assertEquals(2, proof.attempts());
        assertEquals(List.of(CONFIRMED), delivered);
    }

    @Test void attemptLimitCannotExceedTheProductRetryPolicyOrBeEmpty() {
        for (int attempts : new int[] {0, -1, 4}) {
            assertThrows(IllegalArgumentException.class, () -> new EscrowGameTestProof<Result>(() -> 0,
                    (tick, callback) -> true, Result::confirmed, Result::reason, result -> { }, attempts));
        }
        var delivered = new ArrayList<Result>();
        var proof = new EscrowGameTestProof<Result>(() -> 0,
                (tick, callback) -> { callback.accept(SUPERSEDED); return true; },
                Result::confirmed, Result::reason, delivered::add, 1);
        proof.tick(); assertEquals(List.of(SUPERSEDED), delivered); assertEquals(1, proof.attempts());
    }

    @Test void entityCaptureKeepsTheWholeProofWindowAwayFromVanillaAutosave() {
        assertFalse(EscrowProofRetryGameTests.entityCaptureWindow(0));
        assertTrue(EscrowProofRetryGameTests.entityCaptureWindow(1));
        assertTrue(EscrowProofRetryGameTests.entityCaptureWindow(5_959));
        assertFalse(EscrowProofRetryGameTests.entityCaptureWindow(5_960));
        assertFalse(EscrowProofRetryGameTests.entityCaptureWindow(5_999));
        assertFalse(EscrowProofRetryGameTests.entityCaptureWindow(6_000));
        assertTrue(EscrowProofRetryGameTests.entityCaptureWindow(6_001));
    }

    @Test void entityWindowDeferralIsBoundedAndDoesNotDependOnTheAutosaveCycleNumber() {
        for (long cycle : new long[] {0, 6_000, 36_000, 6_000_000_000L}) {
            int deferred = 0;
            long tick = cycle + 5_960;
            while (!EscrowProofRetryGameTests.entityCaptureWindow(tick++)) { deferred++; }
            assertEquals(41, deferred);
            assertTrue(EscrowProofRetryGameTests.entityCaptureWindow(cycle + 5_959));
        }
    }

    private static EscrowGameTestProof<Result> driver(AtomicLong clock, EscrowGameTestProof.Request<Result> request,
                                                     List<Result> delivered) {
        return new EscrowGameTestProof<>(clock::get, request, Result::confirmed, Result::reason, delivered::add);
    }
}
