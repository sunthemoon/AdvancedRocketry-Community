package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * W152K (W152J-R3, R5): the W152J re-review's sequences RR1 to RR5 over the unchanged W152H production-composition
 * fixture, with the fixture's tick bound as the server tick, as the sealed coordinator binds its own.
 */
class SealedRefusedWindowHandoffTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    /** RR1: a pending crew-waiting B is nudged every tick before service, ahead of or after the refused caller A. */
    @ParameterizedTest @CsvSource({"2,true,true", "2,false,true", "62,true,true", "62,false,true",
            "2,true,false", "2,false,false", "62,true,false", "62,false,false"})
    void pendingCrewWaitingNudgeDoesNotSpendTheRefusedCallersWindow(int count, boolean callbacksFirst, boolean bFirst) {
        try (var f = new Fixture(count, callbacksFirst, 18)) {
            f.now(-2);
            var b = f.newReturn(); assertTrue(f.offer(b, new AtomicBoolean(false)), "B must be pending before sealed work starts");
            f.tick(-1); f.tick(0); f.tick(1);
            assertTrue(f.active(0)); assertEquals(1, f.ordinary.size());
            var a = f.newReturn(); assertFalse(f.offer(a), "A is refused behind the active sealed attempt");
            for (long tick = 2; tick <= (count == 2 ? 300 : 600); tick++) {
                f.now(tick);
                if (bFirst) { f.offer(b); f.offer(a); } else { f.offer(a); f.offer(b); }
                f.tick(tick);
            }
            f.report("RR1 count=" + count + " callbacksFirst=" + callbacksFirst + " bFirst=" + bFirst, a);
            assertBound(f.entry(0), a);
            assertSealedResumesAfter(f, a);
        }
    }

    /** RR1b: one new crew-waiting fail-back C arrives in the first window, before A's retry, all before service. */
    @ParameterizedTest @CsvSource({"true", "false"})
    void newCrewWaitingArrivalDoesNotSpendTheRefusedCallersWindow(boolean callbacksFirst) {
        try (var f = new Fixture(2, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            var a = f.newReturn(); assertFalse(f.offer(a));
            Object c = null;
            for (long tick = 2; tick <= 300; tick++) {
                f.now(tick);
                if (c == null && f.finished(0) >= 0) { c = f.newReturn(); assertTrue(f.arrival(c), "C arrives in the window, ahead of A"); }
                f.offer(a);
                f.tick(tick);
            }
            f.report("RR1b callbacksFirst=" + callbacksFirst, a);
            assertBound(f.entry(0), a);
            assertSealedResumesAfter(f, a);
        }
    }

    /**
     * RR1c, production order: A retries after both services, as tickLive's pad-blocked producer does. C arrives in the
     * first window either after service, sorted ahead of A, or before service, as a player's packet does.
     */
    @ParameterizedTest @CsvSource({"true,true", "false,true", "true,false", "false,false"})
    void productionOrderCrewWaitingArrivalDoesNotSpendTheRefusedCallersWindow(boolean callbacksFirst, boolean arrivalAfterService) {
        try (var f = new Fixture(2, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            var a = f.newReturn(); assertFalse(f.offer(a));
            Object c = null; boolean admitted = false;
            for (long tick = 2; tick <= 300; tick++) {
                f.now(tick);
                if (!arrivalAfterService && c == null && f.finished(0) >= 0) { c = f.newReturn(); admitted = f.arrival(c); }
                f.tick(tick);
                if (arrivalAfterService && c == null && f.finished(0) >= 0) { c = f.newReturn(); admitted = f.arrival(c); }
                f.offer(a);
            }
            // After service C lands ahead of A in the completion tick; before service A has already captured.
            assertEquals(arrivalAfterService, admitted, "C's admission does not match the production order under test");
            f.report("RR1c callbacksFirst=" + callbacksFirst + " arrivalAfterService=" + arrivalAfterService, a);
            assertBound(f.entry(0), a);
            assertSealedResumesAfter(f, a);
        }
    }

    /** RR1d, production order with continuing sealed work: B nudged before service every tick, A retries after service. */
    @ParameterizedTest @CsvSource({"true", "false"})
    void productionOrderNudgesBeforeServiceDoNotStarveAnAfterServiceRetrier(boolean callbacksFirst) {
        try (var f = new Fixture(62, callbacksFirst, 18)) {
            f.now(-2);
            var b = f.newReturn(); assertTrue(f.offer(b, new AtomicBoolean(false)));
            f.tick(-1); f.tick(0); f.tick(1);
            var a = f.newReturn(); assertFalse(f.offer(a));
            for (long tick = 2; tick <= 600; tick++) { f.now(tick); f.offer(b); f.tick(tick); f.offer(a); }
            f.report("RR1d callbacksFirst=" + callbacksFirst, a);
            assertBound(f.entry(0), a);
        }
    }

    /**
     * After an ineligible hold limits the window, the held logical cannot renew its token through it. B, pending and
     * crew-waiting, is nudged after service every tick, as tickLive would; A was refused once and never retries.
     */
    @ParameterizedTest @CsvSource({"true", "false"})
    void pendingIneligibleNudgesAfterServiceCannotRenewThroughTheWindow(boolean callbacksFirst) {
        try (var f = new Fixture(2, callbacksFirst, 18)) {
            f.now(-2);
            var b = f.newReturn(); assertTrue(f.offer(b, new AtomicBoolean(false)));
            f.tick(-1); f.tick(0); f.tick(1);
            var abandoned = f.newReturn(); assertFalse(f.offer(abandoned));
            for (long tick = 2; tick <= 300; tick++) { f.now(tick); f.tick(tick); f.offer(b); }
            long ended = f.finished(0), next = f.started(1);
            System.out.printf("renewal callbacksFirst=%s firstEnd=%d opportunity=%d sealed2Start=%d%n",
                    callbacksFirst, ended, number(f.entry(0), "opportunity"), next);
            assertTrue(next >= ended && next <= ended + 2, "A renewed ineligible token held the next sealed owner until " + next);
            assertEquals(-1, number(b, "started"));
        }
    }

    /** RR2: the refused caller retries every one or three ticks, before or after service. */
    @ParameterizedTest @CsvSource({"1,true,false", "1,false,false", "1,true,true", "1,false,true",
            "3,true,false", "3,false,false", "3,true,true", "3,false,true"})
    void refusedCallerRetryingWithinTheWindowMeetsTheBound(int period, boolean callbacksFirst, boolean afterService) {
        try (var f = new Fixture(63, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            var a = f.newReturn(); assertFalse(f.offer(a));
            for (long tick = 2; tick <= 1200 && number(a, "started") < 0; tick++) {
                f.now(tick);
                boolean due = tick % period == 0;
                if (due && !afterService) { f.offer(a); }
                f.tick(tick);
                if (due && afterService) { f.offer(a); }
            }
            f.report("RR2 period=" + period + " callbacksFirst=" + callbacksFirst + " afterService=" + afterService, a);
            assertBound(f.entry(0), a);
        }
    }

    /** RR3, the other direction: a refused caller whose crew never boards delays each sealed hand-off only briefly. */
    @ParameterizedTest @CsvSource({"true", "false"})
    void crewWaitingRetrierDelaysEachSealedHandOffOnlyBriefly(boolean callbacksFirst) {
        try (var f = new Fixture(6, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            var crew = new AtomicBoolean(false);
            var a = f.newReturn(); assertFalse(f.offer(a, crew));
            for (long tick = 2; tick <= 700; tick++) { f.now(tick); f.offer(a, crew); f.tick(tick); }
            var ends = new TreeMap<Long, Long>();
            var starts = new TreeSet<Long>();
            for (var e : f.entries()) {
                if (number(e, "finished") >= 0) { ends.put(number(e, "finished"), number(e, "opportunity")); }
                if (number(e, "started") >= 0) { starts.add(number(e, "started")); }
            }
            long worst = 0;
            for (var end : ends.entrySet()) {
                Long next = starts.higher(end.getKey() - 1);
                if (next != null) { worst = Math.max(worst, next - end.getValue()); }
            }
            System.out.printf("RR3 callbacksFirst=%s sealedEnds=%d worstDelayAfterOpportunity=%d pending=%d%n",
                    callbacksFirst, ends.size(), worst, f.ordinary.size());
            assertTrue(ends.size() >= 5, "Sealed work stopped completing behind the crew-waiting retrier");
            assertTrue(worst <= 4, "A crew-waiting refused retrier delayed a sealed hand-off by " + worst + " ticks after its native opportunity");
        }
    }

    /** RR5: a refused caller admitted while the native window is exhausted keeps its turn until tick 100. */
    @ParameterizedTest @CsvSource({"true,false", "false,false", "true,true", "false,true"})
    void refusedCallerAdmittedIntoAnExhaustedNativeWindowKeepsTheTurn(boolean callbacksFirst, boolean afterService) {
        try (var f = new Fixture(2, callbacksFirst, 20)) {
            f.tick(-1); f.tick(0);
            assertTrue(f.active(0));
            var a = f.newReturn(); assertFalse(f.offer(a));
            for (long tick = 1; tick <= 300; tick++) {
                f.now(tick);
                if (!afterService) { f.offer(a); }
                f.tick(tick);
                if (afterService) { f.offer(a); }
            }
            f.report("RR5 callbacksFirst=" + callbacksFirst + " afterService=" + afterService, a);
            assertEquals(84, f.finished(0)); assertEquals(100, number(f.entry(0), "opportunity"));
            assertBound(f.entry(0), a);
            assertSealedResumesAfter(f, a);
        }
    }

    private static void assertBound(Object sealed, Object ordinary) {
        long ended = number(sealed, "finished"), opportunity = number(sealed, "opportunity"), started = number(ordinary, "started");
        assertTrue(ended >= 0 && opportunity >= ended && started >= opportunity && started <= opportunity + 2,
                "Sealed ended at " + ended + ", native opportunity " + opportunity + ", return first capture " + started);
    }
    /** No later sealed owner captures before A, and the next one starts within R112's bound after A's attempt. */
    private static void assertSealedResumesAfter(Fixture f, Object a) {
        long started = number(a, "started"), ended = number(a, "finished"), opportunity = number(a, "opportunity");
        long next = Long.MAX_VALUE;
        for (var other : f.entries().subList(1, f.entries().size())) {
            long start = number(other, "started");
            if (start >= 0) {
                assertTrue(start > started, "A later sealed owner captured at " + start + ", before the refused return at " + started);
                next = Math.min(next, start);
            }
        }
        assertTrue(ended >= started && next >= ended && next <= opportunity + 2,
                "The return ended at " + ended + " with native opportunity " + opportunity + "; the next sealed owner started at " + next);
    }

    private final class Fixture implements AutoCloseable {
        final Object owner;
        final RocketOrdinaryRetirement ordinary;
        Fixture(int count, boolean callbacksFirst, int slots) {
            var test = new SealedOrdinaryHandoffTest(); test.directory = directory;
            var type = nested(SealedOrdinaryHandoffTest.class, "Fixture");
            try {
                var ctor = type.getDeclaredConstructor(SealedOrdinaryHandoffTest.class, int.class, boolean.class, boolean.class, int.class);
                ctor.setAccessible(true); owner = ctor.newInstance(test, count, callbacksFirst, false, slots);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            ordinary = (RocketOrdinaryRetirement) field(owner, "ordinary");
            // As the sealed coordinator binds server.getTickCount(): the fixture's tick, which its ports also report.
            ((SealedProofTurns) field(owner, "turns")).bindServerTick(() -> number(owner, "now"));
        }
        List<?> entries() { return (List<?>) field(owner, "entries"); }
        Object entry(int index) { return entries().get(index); }
        boolean active(int index) { return (boolean) invoke(entry(index), "activeAttempt", new Class<?>[0]); }
        long finished(int index) { return number(entry(index), "finished"); }
        long started(int index) { return number(entry(index), "started"); }
        void now(long tick) { set(owner, "now", tick); }
        void tick(long tick) { invoke(owner, "tick", new Class<?>[] {long.class}, tick); }
        @SuppressWarnings("unchecked") Object newReturn() {
            var returns = (List<Object>) field(owner, "returns");
            try {
                var ctor = nested(owner.getClass(), "Return").getDeclaredConstructor(owner.getClass(), int.class);
                ctor.setAccessible(true); var r = ctor.newInstance(owner, returns.size()); returns.add(r); return r;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        /** A new crew-waiting fail-back; once admitted it is a pending return that holds for its crew. */
        boolean arrival(Object c) {
            boolean admitted = offer(c, new AtomicBoolean(false));
            if (admitted) { assertEquals("CREW_WAITING", ordinary.reason(record(c).logicalRocketId())); }
            return admitted;
        }
        boolean offer(Object r) { return offer(r, null); }
        /** The production fail-back boundary: holdFailBack, then begin only under its token. A nudge keeps B's own port. */
        boolean offer(Object r, AtomicBoolean crew) {
            var record = record(r); var journal = (RocketTransferSavedData) field(r, "journal");
            if (journal.find(record.transferId()).isEmpty()) { return false; }
            try (var held = ordinary.holdFailBack(journal, record.logicalRocketId())) {
                if (held.token() == null) { return false; }
                var original = (RocketOrdinaryRetirement.Port) field(r, "port");
                var port = crew == null ? original : (RocketOrdinaryRetirement.Port) Proxy.newProxyInstance(
                        RocketOrdinaryRetirement.Port.class.getClassLoader(), new Class<?>[] {RocketOrdinaryRetirement.Port.class},
                        (proxy, method, args) -> method.getName().equals("crewReady") ? crew.get() : method.invoke(original, args));
                ordinary.begin(port, journal, RocketOrdinaryRetirement.Site.FAIL_BACK,
                        record, record.sourceEntityId(), List.of(), false, held, RocketOrdinaryRetirement.Effects.of(() -> true));
                return true;
            }
        }
        void report(String label, Object a) {
            System.out.printf("%s firstEnd=%d opportunity=%d aStart=%d aEnd=%d aOpportunity=%d sealed2Start=%d pending=%d%n",
                    label, finished(0), number(entry(0), "opportunity"), number(a, "started"), number(a, "finished"),
                    number(a, "opportunity"), started(1), ordinary.size());
        }
        public void close() { invoke(owner, "close", new Class<?>[0]); }
    }

    private static RocketTransferRecord record(Object r) { return (RocketTransferRecord) field(r, "record"); }
    private static Class<?> nested(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredClasses()).filter(c -> c.getSimpleName().equals(name)).findFirst().orElseThrow();
    }
    private static Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static long number(Object target, String name) { return ((Number) field(target, name)).longValue(); }
    private static void set(Object target, String name, Object value) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... values) {
        try { var m = target.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(target, values); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) { throw error; }
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
