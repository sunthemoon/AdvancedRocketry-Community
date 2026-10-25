package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedLazyWork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SealedProofCapacityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @CsvSource({"4,2", "4,21", "64,2", "64,21"})
    void admittedAttemptsFinishWhileOtherNativeCandidatesWait(int count, int slots) throws Exception {
        var budget = new SealedWorkBudget();
        var scheduler = new SealedWorkScheduler();
        var selection = new SealedRecoverySelection();
        var quota = new NativeQuota();
        var entries = new ArrayList<Attempt>();
        long[] now = {0};
        var lazy = new SealedLazyWork<UUID>(new SealedLazyWork.Port<>() {
            public UUID discover() { return null; }
            public void attempt(UUID id, SealedWorkBudget allowance) { fail(); }
        });
        for (int index = 0; index < count; index++) {
            var entry = new Attempt(new UUID(0, index + 1), slots, now, budget, quota);
            entry.room = () -> SealedProofAdmission.inspectionRoom((int) entries.stream().filter(Attempt::activeAttempt).count());
            entries.add(entry);
        }
        int maxActive = 0;
        try {
            for (int tick = 0; tick < 20_000 && entries.stream().anyMatch(entry -> !entry.done); tick++) {
                now[0] = tick; budget.beginTick(tick);
                var selected = selection.select(tick, entries.stream().map(Attempt::id).toList(), id -> {
                    var entry = entries.stream().filter(value -> value.id.equals(id)).findFirst().orElseThrow();
                    return !entry.activeAttempt() && entry.eligible(now[0]);
                }).orElse(null);
                scheduler.tick(tick, entries, selected, budget, () -> lazy.tick(budget), selection::serviced);
                maxActive = Math.max(maxActive, (int) entries.stream().filter(Attempt::activeAttempt).count());
                assertTrue(budget.remaining() >= 0 && budget.remaining() <= 256);
                for (var entry : entries) {
                    if (entry.activeAttempt()) { assertEquals(tick, entry.lastService, "Active proof missed its tick"); }
                }
            }
            assertEquals(1, maxActive, "A non-resumable census reserves the whole tick; the other candidates must wait");
            assertTrue(entries.stream().allMatch(entry -> entry.done), "Deferred records must not be forgotten");
            for (var entry : entries) {
                assertEquals(1, entry.starts, "No replacement deadline may hide starvation");
                assertEquals(slots, entry.captures);
                assertTrue(entry.finished - entry.started <= RocketOrdinaryRetirement.ATTEMPT_TICKS);
                assertEquals(0, entry.deadlines);
            }
            System.out.printf("records=%d slots=%d maxActive=%d maxAge=%d ticks=%d%n", count, slots, maxActive,
                    entries.stream().mapToLong(entry -> entry.finished - entry.started).max().orElseThrow(), now[0] + 1);
        } finally { entries.forEach(entry -> entry.batch.close()); }
    }

    @Test void plansBeyondTheNativeWindowRemainIntactAndUnstarted() throws Exception {
        var quota = new NativeQuota();
        var budget = new SealedWorkBudget();
        long[] now = {0};
        for (int slots : new int[] {22, RocketOrdinaryRetirement.MAX_SLOTS}) {
            var entry = new Attempt(UUID.randomUUID(), slots, now, budget, quota);
            try {
                for (int tick = 0; tick < 500; tick++) {
                    now[0] = tick; budget.beginTick(tick); entry.tickProof();
                    assertFalse(entry.activeAttempt());
                }
                assertEquals(0, entry.starts); assertEquals(0, entry.captures); assertEquals(0, entry.deadlines);
                assertFalse(entry.done);
            } finally { entry.batch.close(); }
        }
    }

    @Test void nativeQuotaAndPendingBoundsStayPinned() throws Exception {
        assertEquals(64, RocketOrdinaryRetirement.MAX_PENDING);
        assertEquals(4, RocketOrdinaryRetirement.MAX_ATTEMPTS);
        assertEquals(100, RocketOrdinaryRetirement.ATTEMPT_TICKS);
        assertEquals(200, RocketOrdinaryRetirement.RETRY_TICKS);
        assertEquals(129, RocketOrdinaryRetirement.MAX_SLOTS);
        var type = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrierBudget");
        var cadence = type.getDeclaredField("CADENCE"); cadence.setAccessible(true);
        var captures = type.getDeclaredField("ORDINARY_PER_LEVEL"); captures.setAccessible(true);
        assertEquals(20, cadence.getInt(null)); assertEquals(4, captures.getInt(null));
        assertTrue(SealedProofAdmission.fitsNativeWindow(21));
        assertFalse(SealedProofAdmission.fitsNativeWindow(22));
        for (int active = 1; active <= RocketOrdinaryRetirement.MAX_ATTEMPTS; active++) {
            assertFalse(SealedProofAdmission.inspectionRoom(active));
        }
    }

    @Test void directCallsCannotConsumeTheNextTicksReservedInspectionAllowance() {
        assertTrue(SealedProofAdmission.priorityPending(1, 25, 24));
        assertTrue(SealedProofAdmission.priorityPending(1, 25, Long.MIN_VALUE));
        assertFalse(SealedProofAdmission.priorityPending(1, 25, 25));
        assertFalse(SealedProofAdmission.priorityPending(0, 25, 24));
    }

    @Test void fourNativeAttemptsAndALastSlotStartStillFitTheOriginalDeadline() throws Exception {
        var quota = new NativeQuota();
        var budget = new SealedWorkBudget();
        long[] now = {0};
        var ordinary = new ArrayList<SealedProofBatch>();
        var sealed = new Attempt(UUID.randomUUID(), 21, now, budget, quota);
        sealed.room = () -> SealedProofAdmission.inspectionRoom(sealed.activeAttempt() ? 1 : 0)
                && ordinary.stream().filter(SealedProofBatch::active).count() < RocketOrdinaryRetirement.MAX_ATTEMPTS;
        try {
            // Three other native claimants have completed their one-slot proofs but still own their attempts.
            // They consume the first three captures in the shared window, not sealed inspection allowance.
            for (int index = 0; index < RocketOrdinaryRetirement.MAX_ATTEMPTS - 1; index++) {
                now[0] = index;
                var batch = new SealedProofBatch(1, new SealedProofBatch.Port() {
                    public long now() { return now[0]; }
                    public SealedProofBatch.Readiness readiness() { return SealedProofBatch.Readiness.READY; }
                    public boolean attemptRoom() { return true; }
                    public boolean available(int slot) { return quota.available(now()); }
                    public boolean capture(int slot, Consumer<EscrowBarrier.Result> callback) {
                        if (!quota.capture(now())) { return false; }
                        callback.accept(SealedRecoveryProofSchedulingTest.proved()); return true;
                    }
                    public void held(String reason) { fail(reason); }
                });
                ordinary.add(batch); batch.tick(); assertTrue(batch.current());
            }
            now[0] = 3; budget.beginTick(now[0]); sealed.tickProof();
            assertEquals(4, ordinary.stream().filter(SealedProofBatch::active).count() + (sealed.activeAttempt() ? 1 : 0));
            assertEquals(3, sealed.started);
            ordinary.forEach(batch -> { assertTrue(batch.current()); batch.close(); });
            for (int tick = 4; tick <= 103 && !sealed.done; tick++) {
                now[0] = tick; budget.beginTick(tick); sealed.tickProof(); sealed.work();
            }
            assertTrue(sealed.done); assertEquals(21, sealed.captures);
            assertEquals(100, sealed.finished - sealed.started, "Inclusive original deadline, not an extended attempt");
            assertEquals(1, sealed.starts); assertEquals(0, sealed.deadlines);
        } finally { sealed.batch.close(); ordinary.forEach(SealedProofBatch::close); }
    }

    private static final class Attempt implements SealedWorkScheduler.Entry, SealedProofBatch.Port {
        final UUID id;
        final int slots;
        final long[] now;
        final SealedWorkBudget budget;
        final NativeQuota quota;
        final SealedProofBatch batch;
        java.util.function.BooleanSupplier room = () -> true;
        long lastCensus = Long.MIN_VALUE, lastService = Long.MIN_VALUE, started, finished;
        boolean censusReady, done;
        int starts, captures, deadlines;
        Attempt(UUID id, int slots, long[] now, SealedWorkBudget budget, NativeQuota quota) {
            this.id = id; this.slots = slots; this.now = now; this.budget = budget; this.quota = quota;
            batch = new SealedProofBatch(slots, this);
        }
        public UUID id() { return id; }
        public boolean recovery() { return true; }
        public boolean activeAttempt() { return batch.active(); }
        public boolean eligible(long tick) { return !done && tick >= batch.retryAt(); }
        public void tickDeadline() { batch.tickDeadline(); }
        public void tickProof() { lastService = now(); batch.tick(); }
        public void work() {
            if (budget.take() && batch.current()) { done = true; finished = now(); batch.close(); }
        }
        public long now() { return now[0]; }
        public SealedProofBatch.Readiness readiness() {
            if (lastCensus != now()) {
                lastCensus = now(); censusReady = true;
                // Deliberately almost the full allowance; this cannot be split among four active owners.
                for (int visit = 0; visit < 255; visit++) { censusReady &= budget.take(); }
            }
            return censusReady ? SealedProofBatch.Readiness.READY : SealedProofBatch.Readiness.UNLOADED;
        }
        public boolean attemptRoom() {
            return budget.remaining() > 0 && SealedProofAdmission.fitsNativeWindow(slots) && room.getAsBoolean();
        }
        public boolean available(int slot) { return quota.available(now()); }
        public boolean capture(int slot, Consumer<EscrowBarrier.Result> callback) {
            if (!quota.capture(now())) { return false; }
            if (slot == 0) { started = now(); starts++; }
            captures++; callback.accept(SealedRecoveryProofSchedulingTest.proved()); return true;
        }
        public void held(String reason) { if (reason.equals("DEADLINE")) { deadlines++; } }
    }

    /** Production quota with immediate zero-byte IO, not a permissive synthetic capture port. */
    private static final class NativeQuota {
        final Object quota;
        final Method available, reserve, captured, release;
        NativeQuota() throws Exception {
            var type = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrierBudget");
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true); quota = constructor.newInstance();
            available = method(type, "availableOrdinary", String.class, boolean.class, long.class);
            reserve = method(type, "reserveOrdinary", String.class, boolean.class, long.class);
            captured = method(type, "captured", int.class); release = method(type, "releaseOrdinary", int.class);
        }
        boolean available(long tick) {
            try { return (boolean) available.invoke(quota, "minecraft:overworld", true, tick); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        boolean capture(long tick) {
            try {
                if (!(boolean) reserve.invoke(quota, "minecraft:overworld", true, tick)) { return false; }
                captured.invoke(quota, 0); release.invoke(quota, 0); return true;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        private static Method method(Class<?> type, String name, Class<?>... arguments) throws Exception {
            var method = type.getDeclaredMethod(name, arguments); method.setAccessible(true); return method;
        }
    }
}
