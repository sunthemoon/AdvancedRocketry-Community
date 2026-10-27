package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * W152L: the W152K review probe, extended to pin finite-arrival contention rather than an unconditional first-window
 * guarantee. Uses the unchanged production-composition fixture, with its tick bound as production's server clock.
 */
class SealedRefusedWindowContentionTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void renewalAndServiceEligibilityWithNoFurtherSealedCompletion() {
        var now = new AtomicLong(10);
        var waiting = new AtomicBoolean(true);
        var turns = new SealedProofTurns();
        turns.bind(waiting::get); turns.bindServerTick(now::get);
        turns.reservationRefused(); turns.sealedEnded(); turns.ordinaryReservationHeld();
        var pending = UUID.randomUUID();
        assertFalse(turns.ordinaryReservationRoom(pending, true));
        assertTrue(turns.ordinaryRoom(pending));
        now.set(12);
        assertFalse(turns.ordinaryReservationRoom(pending, true));
        assertFalse(turns.ordinaryRoom(pending));
        assertTrue(turns.sealedRoom());
        now.set(10_000);
        assertFalse(turns.ordinaryReservationRoom(pending, true));
        waiting.set(false);
        assertTrue(turns.ordinaryReservationRoom(pending, true));
        assertTrue(turns.ordinaryRoom(pending));
        System.out.println("renewal: waiting sealed candidate blocks expired ordinary turn; removing sealed demand restores admission without completion");
    }

    @ParameterizedTest @CsvSource({"true,1", "false,1", "true,3", "false,3", "true,4", "false,4", "true,8", "false,8"})
    void twoFreshArrivalWavesCanCostOneSealedCycle(boolean callbacksFirst, int arrivalsPerTick) throws Exception {
        try (var f = new Fixture(2, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            var a = f.newReturn(); assertFalse(f.offer(a, null, null));
            var arrivals = new ArrayList<Object>();
            var admitted = new ArrayList<Long>();
            var offered = new ArrayList<Long>();
            for (long tick = 2; tick <= 300; tick++) {
                f.now(tick); f.tick(tick);
                long end = number(f.entry(0), "finished");
                if (end >= 0 && tick < end + 2) {
                    for (int i = 0; i < arrivalsPerTick; i++) {
                        var arrival = f.newReturn(); arrivals.add(arrival); offered.add(tick);
                        if (f.offer(arrival, new AtomicBoolean(false), null)) { admitted.add(tick); }
                    }
                }
                f.offer(a, null, null);
                assertUnretiredRecordKept(a);
                assertTrue(f.ordinary.activeAttempts() <= RocketOrdinaryRetirement.MAX_ATTEMPTS);
            }
            long end = number(f.entry(0), "finished"), opportunity = number(f.entry(0), "opportunity");
            long started = number(a, "started");
            System.out.printf("burst callbacksFirst=%s perTick=%d admitted=%d completion=%d opportunity=%d aStart=%d nextSealed=%d pending=%d%n",
                    callbacksFirst, arrivalsPerTick, admitted.size(), end, opportunity, started, number(f.entry(1), "started"), f.ordinary.size());
            assertEquals(83, end); assertEquals(83, opportunity);
            assertEquals(arrivalsPerTick, Collections.frequency(offered, end));
            assertEquals(arrivalsPerTick, Collections.frequency(offered, end + 1));
            boolean fillsAllAttempts = arrivalsPerTick >= 4;
            assertEquals(fillsAllAttempts ? 163 : 83, started);
            assertEquals(fillsAllAttempts ? 87 : 85, number(f.entry(1), "started"));
            assertEquals(fillsAllAttempts ? 8 : arrivalsPerTick, admitted.size());
            assertEquals(fillsAllAttempts ? 4 : arrivalsPerTick, Collections.frequency(admitted, end));
            assertEquals(fillsAllAttempts ? 4 : 0, Collections.frequency(admitted, end + 1));
            assertEquals(admitted.size(), f.ordinary.size());
            for (var arrival : arrivals) { assertUnretiredRecordKept(arrival); assertEquals(-1, number(arrival, "started")); }
            assertRetired(f, a);
        }
    }

    @ParameterizedTest @CsvSource({"true,true", "false,true", "true,false", "false,false"})
    void sameFourCrewWaitingLogicalsRetryAcrossSixSealedCycles(boolean callbacksFirst, boolean afterService) throws Exception {
        try (var f = new Fixture(6, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            var a = f.newReturn(); assertFalse(f.offer(a, null, null));
            var crew = new AtomicBoolean(false);
            var arrivals = new ArrayList<Object>();
            var admitted = new ArrayList<List<Long>>();
            for (int i = 0; i < 4; i++) { arrivals.add(f.newReturn()); admitted.add(new ArrayList<>()); }
            for (long tick = 2; tick <= 700; tick++) {
                f.now(tick);
                if (afterService) { f.tick(tick); }
                for (int i = 0; i < arrivals.size(); i++) {
                    if (f.offer(arrivals.get(i), crew, null)) { admitted.get(i).add(tick); }
                }
                f.offer(a, null, null);
                assertUnretiredRecordKept(a);
                assertTrue(f.ordinary.activeAttempts() <= RocketOrdinaryRetirement.MAX_ATTEMPTS);
                if (!afterService) { f.tick(tick); }
            }
            var starts = new ArrayList<Long>();
            var ends = new ArrayList<Long>();
            for (int i = 0; i < 6; i++) {
                starts.add(number(f.entry(i), "started")); ends.add(number(f.entry(i), "finished"));
            }
            var beforeCapture = admitted.stream().map(ticks -> ticks.stream().filter(tick -> tick <= number(a, "started")).toList()).toList();
            System.out.printf("sameFour callbacksFirst=%s afterService=%s aStart=%d aEnd=%d sealedStarts=%s sealedEnds=%s admissionsThroughCapture=%s%n",
                    callbacksFirst, afterService, number(a, "started"), number(a, "finished"), starts, ends, beforeCapture);
            assertEquals(6, ends.stream().filter(tick -> tick >= 0).count(), "Must exercise all six sealed cycles");
            assertEquals(afterService ? 84 : 164, number(a, "started"));
            assertEquals(afterService ? 85 : 165, number(a, "finished"));
            assertEquals(afterService ? List.of(List.of(83L), List.of(83L), List.of(83L), List.of(83L))
                    : List.of(List.of(84L, 164L), List.of(84L), List.of(84L), List.of(84L)), beforeCapture);
            assertEquals(afterService ? List.of(1L, 85L, 167L, 247L, 327L, 407L)
                    : List.of(1L, 87L, 167L, 247L, 327L, 407L), starts.stream().sorted().toList());
            assertRetired(f, a);
            for (var arrival : arrivals) {
                var record = (RocketTransferRecord) field(arrival, "record");
                var journal = (RocketTransferSavedData) field(arrival, "journal");
                assertSame(record, journal.find(record.transferId()).orElseThrow());
                assertTrue(f.ordinary.pending(record.logicalRocketId()));
                assertEquals("CREW_WAITING", f.ordinary.reason(record.logicalRocketId()));
                assertEquals(-1, number(arrival, "started"));
            }
        }
    }

    private static void assertUnretiredRecordKept(Object r) {
        if (number(r, "finished") >= 0) { return; }
        var record = (RocketTransferRecord) field(r, "record");
        assertSame(record, ((RocketTransferSavedData) field(r, "journal")).find(record.transferId()).orElseThrow());
    }

    private static void assertRetired(Fixture f, Object r) {
        var record = (RocketTransferRecord) field(r, "record");
        assertTrue(((RocketTransferSavedData) field(r, "journal")).find(record.transferId()).isEmpty());
        assertFalse(f.ordinary.pending(record.logicalRocketId()));
    }

    @ParameterizedTest @CsvSource({"true,true", "false,true", "true,false", "false,false"})
    void newIneligibleArrivalsWithoutEligibleRetry(boolean callbacksFirst, boolean afterService) throws Exception {
        try (var f = new Fixture(2, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            assertFalse(f.offer(f.newReturn(), null, null));
            long lastAdmission = -1;
            for (long tick = 2; tick <= 300; tick++) {
                f.now(tick);
                if (afterService) { f.tick(tick); }
                long end = number(f.entry(0), "finished");
                if (end >= 0 && tick <= end + 4 && f.offer(f.newReturn(), new AtomicBoolean(false), null)) { lastAdmission = tick; }
                if (!afterService) { f.tick(tick); }
            }
            long end = number(f.entry(0), "finished"), opportunity = number(f.entry(0), "opportunity");
            long next = number(f.entry(1), "started");
            System.out.printf("delay callbacksFirst=%s afterService=%s completion=%d opportunity=%d lastAdmission=%d sealed2=%d%n",
                    callbacksFirst, afterService, end, opportunity, lastAdmission, next);
            assertTrue(lastAdmission < end + 2);
            assertTrue(next <= lastAdmission + 3, "Token expires next tick, followed by at most two service ticks");
        }
    }

    @ParameterizedTest @CsvSource({"true,crew", "false,crew", "true,slot", "false,slot", "true,escrow", "false,escrow"})
    void singleIneligibleArrivalKinds(boolean callbacksFirst, String kind) throws Exception {
        try (var f = new Fixture(2, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            var a = f.newReturn(); assertFalse(f.offer(a, null, null));
            boolean arrived = false;
            for (long tick = 2; tick <= 300 && number(a, "started") < 0; tick++) {
                f.now(tick); f.tick(tick);
                if (!arrived && number(f.entry(0), "finished") >= 0) {
                    assertTrue(f.offer(f.newReturn(), kind.equals("crew") ? new AtomicBoolean(false) : null, kind));
                    arrived = true;
                }
                f.offer(a, null, null);
            }
            long opportunity = number(f.entry(0), "opportunity"), started = number(a, "started");
            System.out.printf("kind callbacksFirst=%s kind=%s opportunity=%d aStart=%d%n", callbacksFirst, kind, opportunity, started);
            assertTrue(started >= opportunity && started <= opportunity + 2);
        }
    }

    private final class Fixture implements AutoCloseable {
        final Object owner;
        final RocketOrdinaryRetirement ordinary;
        Fixture(int count, boolean callbacksFirst, int slots) throws Exception {
            var test = new SealedOrdinaryHandoffTest(); test.directory = directory;
            var type = nested(SealedOrdinaryHandoffTest.class, "Fixture");
            var ctor = type.getDeclaredConstructor(SealedOrdinaryHandoffTest.class, int.class, boolean.class, boolean.class, int.class);
            ctor.setAccessible(true); owner = ctor.newInstance(test, count, callbacksFirst, false, slots);
            ordinary = (RocketOrdinaryRetirement) field(owner, "ordinary");
            ((SealedProofTurns) field(owner, "turns")).bindServerTick(() -> number(owner, "now"));
        }
        Object entry(int i) { return ((List<?>) field(owner, "entries")).get(i); }
        void now(long tick) throws Exception { set(owner, "now", tick); }
        void tick(long tick) throws Exception { invoke(owner, "tick", new Class<?>[] {long.class}, tick); }
        @SuppressWarnings("unchecked") Object newReturn() throws Exception {
            var returns = (List<Object>) field(owner, "returns");
            var ctor = nested(owner.getClass(), "Return").getDeclaredConstructor(owner.getClass(), int.class);
            ctor.setAccessible(true); var r = ctor.newInstance(owner, returns.size()); returns.add(r); return r;
        }
        boolean offer(Object r, AtomicBoolean crew, String kind) throws Exception {
            var record = (RocketTransferRecord) field(r, "record");
            var journal = (RocketTransferSavedData) field(r, "journal");
            if (journal.find(record.transferId()).isEmpty() || !ordinary.pendingRoom(record.logicalRocketId())) { return false; }
            try (var held = ordinary.holdFailBack(journal, record.logicalRocketId())) {
                if (held.token() == null) { return false; }
                var original = (RocketOrdinaryRetirement.Port) field(r, "port");
                var port = (RocketOrdinaryRetirement.Port) Proxy.newProxyInstance(
                        RocketOrdinaryRetirement.Port.class.getClassLoader(), new Class<?>[] {RocketOrdinaryRetirement.Port.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("crewReady") && crew != null) { return crew.get(); }
                            if (method.getName().equals("slot") && "slot".equals(kind)) { return RocketOrdinaryRetirement.SlotState.UNLOADED; }
                            if (method.getName().equals("escrowAllows") && "escrow".equals(kind)) { return false; }
                            return method.invoke(original, args);
                        });
                ordinary.begin(port, journal, RocketOrdinaryRetirement.Site.FAIL_BACK, record, record.sourceEntityId(), List.of(),
                        false, held, RocketOrdinaryRetirement.Effects.of(() -> true));
                if (crew != null && !crew.get()) {
                    assertEquals("CREW_WAITING", ordinary.reason(record.logicalRocketId()));
                    assertFalse(journal.actionable(record.logicalRocketId()), "N2 keeps the creation-tick token");
                }
                return true;
            }
        }
        public void close() throws Exception { invoke(owner, "close", new Class<?>[0]); }
    }
    private static Class<?> nested(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredClasses()).filter(c -> c.getSimpleName().equals(name)).findFirst().orElseThrow();
    }
    private static Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static long number(Object target, String name) { return ((Number) field(target, name)).longValue(); }
    private static void set(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception {
        var m = target.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(target, values); }
        catch (InvocationTargetException e) { if (e.getCause() instanceof Error error) { throw error; } throw e; }
    }
}
