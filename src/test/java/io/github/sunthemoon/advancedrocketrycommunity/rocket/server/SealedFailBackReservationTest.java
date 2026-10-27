package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Arrival coverage over the unchanged W152H composition fixture and its real native quota and checked journals. */
class SealedFailBackReservationTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest
    @CsvSource({"RECOVERED_SOURCE,true", "RECOVERED_SOURCE,false", "FAIL_BACK,true", "FAIL_BACK,false"})
    void arrivalsThroughTickTenRespectTheReverseHandoff(String siteName, boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(callbacksFirst)) {
            f.enqueueOrdinary();
            var site = RocketOrdinaryRetirement.Site.valueOf(siteName);
            for (long tick = 1; tick <= 40; tick++) {
                f.now(tick);
                if (tick <= 10) {
                    if (site == RocketOrdinaryRetirement.Site.FAIL_BACK) { f.offer(f.newReturn()); }
                    else { f.enqueueOrdinary(); }
                }
                f.tick(tick);
            }
            long started = number(f.sealed(), "started");
            var preceding = f.returns().stream().filter(r -> number(r, "started") >= 0 && number(r, "started") < started)
                    .max(Comparator.comparingLong(r -> number(r, "finished"))).orElseThrow();
            assertHandoff(preceding, started);
            long ended = number(preceding, "finished");
            assertTrue(f.returns().stream().noneMatch(r -> number(r, "started") >= ended && number(r, "started") < started),
                    "A new ordinary attempt passed the waiting sealed candidate");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void alternatingPendingFailBackNudgesCannotDelaySealedUntilAfterTick300(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(callbacksFirst)) {
            var first = f.enqueueOrdinary();
            // Before sealed discovery, the first return consumes this tick's capture opportunity.
            var pending = List.of(f.newReturn(), f.newReturn());
            for (var r : pending) { assertTrue(f.offer(r)); assertEquals(-1, number(r, "started")); }
            assertEquals(3, f.ordinary.size());
            f.tick(0);
            for (long tick = 1; tick <= 300; tick++) {
                f.now(tick);
                var r = pending.get((int) ((tick - 1) % 2));
                // Keep presenting the same two requests; acknowledged returns no longer have a T to nudge.
                if (journal(r).find(record(r).transferId()).isPresent()) { f.offer(r); }
                f.tick(tick);
                assertTrue(f.ordinary.size() <= 3, "The bounded nudge sequence added pending returns");
            }
            assertHandoff(first, number(f.sealed(), "started"));
            assertTrue(pending.stream().allMatch(r -> number(r, "started") > number(f.sealed(), "started")),
                    "A nudged return passed the sealed turn");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void arrivalsThenAlternatingNudgesRespectTheBoundBeforeDemandStops(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(callbacksFirst)) {
            var first = f.enqueueOrdinary(); set(f.owner, "sealedDemand", true);
            var returns = new ArrayList<Object>();
            for (long tick = 1; tick <= 300; tick++) {
                f.now(tick);
                if (tick <= 2) { returns.add(f.newReturn()); }
                var r = returns.get((int) ((tick - 1) % 2));
                if (journal(r).find(record(r).transferId()).isPresent()) { f.offer(r); }
                f.tick(tick);
                assertTrue(f.ordinary.size() <= 3);
            }
            assertHandoff(first, number(f.sealed(), "started"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void reconnectNudgesOfPendingFailBacksUseTheSameReservationAdmission(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(callbacksFirst)) {
            var first = f.enqueueOrdinary(); var pending = List.of(f.newReturn(), f.newReturn());
            for (var r : pending) { assertTrue(f.offer(r)); }
            f.tick(0);
            for (long tick = 1; tick <= 300; tick++) {
                f.now(tick); var r = pending.get((int) ((tick - 1) % 2));
                if (journal(r).find(record(r).transferId()).isPresent()) {
                    f.offer(r, true, RocketOrdinaryRetirement.Site.RECONNECT);
                }
                f.tick(tick);
            }
            assertHandoff(first, number(f.sealed(), "started"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void admittedButCrewWaitingFailBackCannotGiveEveryNewArrivalTheSameTurn(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(callbacksFirst)) {
            set(f.owner, "sealedDemand", true);
            ((SealedProofTurns) field(f.owner, "turns")).sealedEnded();
            var first = f.newReturn(); assertTrue(f.offer(first, false));
            assertFalse(journal(first).actionable(record(first).logicalRocketId()));
            assertEquals("CREW_WAITING", f.ordinary.reason(record(first).logicalRocketId()));
            f.tick(0);
            for (long tick = 1; tick <= 10; tick++) { f.now(tick); f.offer(f.newReturn(), false); f.tick(tick); }
            long started = number(f.sealed(), "started");
            assertTrue(started >= 1 && started <= 3, "A retained non-proving token repeatedly renewed the ordinary turn: " + started);
            assertTrue(f.returns().stream().allMatch(r -> number(r, "started") < 0));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void waitingFailBackReceivesItsOwnTurnAfterSealedEnds(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(callbacksFirst)) {
            f.enqueueOrdinary();
            var waiting = f.newReturn(); assertTrue(f.offer(waiting)); f.tick(0);
            for (long tick = 1; tick < 300 && number(waiting, "started") < 0; tick++) {
                f.now(tick); f.offer(waiting); f.tick(tick);
            }
            assertHandoff(f.sealed(), number(waiting, "started"));
            for (long tick = number(f.owner, "now") + 1; tick < 300 && f.ordinary.pending(record(waiting).logicalRocketId()); tick++) {
                f.tick(tick);
            }
            assertEquals(1, f.accepted.get(), "The original fail-back callback must run exactly once");
            assertTrue(journal(waiting).find(record(waiting).transferId()).isEmpty());
        }
    }

    @Test void refusedReservationDoesNotCreatePendingDemandOrChangeTheJournal() throws Exception {
        try (var f = new Fixture(true)) {
            set(f.owner, "sealedDemand", true);
            var r = f.newReturn(); var before = journal(r).save(new net.minecraft.nbt.CompoundTag()).copy();
            assertFalse(f.offer(r), "A new fail-back reserved ahead of the waiting sealed candidate");
            assertEquals(0, f.ordinary.size()); assertEquals(0, f.ordinary.activeAttempts());
            assertEquals(before, journal(r).save(new net.minecraft.nbt.CompoundTag()));
            assertTrue(journal(r).actionable(record(r).logicalRocketId()));
            assertEquals(0, f.accepted.get() + f.discarded.get());
            var turns = (SealedProofTurns) field(f.owner, "turns");
            turns.sealedEnded();
            assertTrue(turns.sealedRoom(), "A refused caller registered phantom pending demand");
            assertTrue(f.offer(r), "A caller with no older pending competitor must get the ordinary turn");
        }
    }

    @Test void admittedFailBackKeepsTheSameTickTokenAndAcknowledgementBoundary() throws Exception {
        try (var f = new Fixture(true)) {
            f.enqueueOrdinary(); var r = f.newReturn(); assertTrue(f.offer(r));
            assertFalse(journal(r).actionable(record(r).logicalRocketId()), "JOURNAL_PENDING protection was removed");
            assertEquals(2, f.ordinary.activeAttempts(), "The caller-held token must still count toward capacity");
            assertEquals(0, f.accepted.get());
            f.tick(0); f.tick(1);
            assertTrue(journal(r).actionable(record(r).logicalRocketId()), "The waiting token survived its creation tick");
            assertEquals(0, f.accepted.get());
        }
    }

    @Test void callerReservationCannotPassAnOlderDueReturn() {
        var turns = new SealedProofTurns(); turns.bind(() -> true);
        var first = UUID.randomUUID(); var later = UUID.randomUUID();
        assertFalse(turns.ordinaryRoom(first)); turns.sealedEnded();
        assertFalse(turns.ordinaryReservationRoom(later));
        assertTrue(turns.ordinaryReservationRoom(first));
        turns.ordinaryStarted(first);
        assertFalse(turns.ordinaryReservationRoom(later));
    }

    @Test void productionCallerChecksTheTurnAwareReservationBeforeMutation() throws Exception {
        var source = java.nio.file.Files.readString(Path.of(
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferContinuations.java"));
        var body = source.split("boolean failBack\\(", 2)[1].split("boolean update\\(", 2)[0];
        int reserve = body.indexOf("retirements.holdFailBack(journal, current.logicalRocketId())");
        int refusal = body.indexOf("if (held.token() == null) { return false; }");
        assertTrue(reserve >= 0 && reserve < refusal);
        for (var mutation : List.of("source.updateFlightData(", "RocketTransferEntities.positionAtOrigin(",
                "RocketFlightFeedback.returnedToSource(", "RocketTransferEntities.remountOnlinePassengers(",
                "retirements.begin(", "LandingPadService.release(", "completion.accepted().run()")) {
            assertTrue(refusal < body.indexOf(mutation), "Reservation refusal must precede " + mutation);
        }
        assertTrue(body.contains("completion.discarded()"));
        assertFalse(body.contains("RocketOrdinaryRetirement.hold("));
        var recovery = java.nio.file.Files.readString(Path.of(
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferRecoveryService.java"));
        assertEquals(2, recovery.split("retirements.holdReturn\\(journal, record.logicalRocketId\\(\\)\\)", -1).length - 1,
                "Recovery and reconnect must both arbitrate a pending fail-back's renewed token");
    }

    private static void assertHandoff(Object preceding, long started) {
        long ended = number(preceding, "finished"), opportunity = number(preceding, "opportunity");
        assertTrue(ended >= 0 && opportunity >= ended && started >= opportunity && started <= opportunity + 2,
                "Attempt ended at " + ended + ", next native opportunity " + opportunity + ", first capture " + started);
    }

    /** Reflection is confined to reusing the pre-existing private fixture without modifying its 14 regressions. */
    private final class Fixture implements AutoCloseable {
        final Object owner;
        final RocketOrdinaryRetirement ordinary;
        final AtomicInteger accepted = new AtomicInteger(), discarded = new AtomicInteger();
        Fixture(boolean callbacksFirst) throws Exception {
            var test = new SealedOrdinaryHandoffTest(); test.directory = directory;
            var type = nested(SealedOrdinaryHandoffTest.class, "Fixture");
            var ctor = type.getDeclaredConstructor(SealedOrdinaryHandoffTest.class, int.class, boolean.class, boolean.class);
            ctor.setAccessible(true); owner = ctor.newInstance(test, 1, callbacksFirst, false);
            ordinary = (RocketOrdinaryRetirement) field(owner, "ordinary");
        }
        Object sealed() { return ((List<?>) field(owner, "entries")).get(0); }
        @SuppressWarnings("unchecked") List<Object> returns() { return (List<Object>) field(owner, "returns"); }
        void now(long tick) { set(owner, "now", tick); }
        void tick(long tick) { invoke(owner, "tick", new Class<?>[] {long.class}, tick); }
        Object enqueueOrdinary() { return invoke(owner, "enqueue", new Class<?>[0]); }
        Object newReturn() throws Exception {
            var ctor = nested(owner.getClass(), "Return").getDeclaredConstructor(owner.getClass(), int.class);
            ctor.setAccessible(true); var r = ctor.newInstance(owner, returns().size()); returns().add(r); return r;
        }
        boolean offer(Object r) { return offer(r, true); }
        boolean offer(Object r, boolean crewReady) { return offer(r, crewReady, RocketOrdinaryRetirement.Site.FAIL_BACK); }
        boolean offer(Object r, boolean crewReady, RocketOrdinaryRetirement.Site site) {
            var record = record(r); var journal = journal(r);
            try (var held = site == RocketOrdinaryRetirement.Site.FAIL_BACK ? reserve(journal, record.logicalRocketId())
                    : ordinary.holdReturn(journal, record.logicalRocketId())) {
                if (held.token() == null) { return false; }
                var original = (RocketOrdinaryRetirement.Port) field(r, "port");
                var port = crewReady ? original : (RocketOrdinaryRetirement.Port) Proxy.newProxyInstance(
                        RocketOrdinaryRetirement.Port.class.getClassLoader(), new Class<?>[] {RocketOrdinaryRetirement.Port.class},
                        (proxy, method, args) -> method.getName().equals("crewReady") ? false : method.invoke(original, args));
                ordinary.begin(port, journal, site,
                        record, record.sourceEntityId(), List.of(), false, held,
                        new RocketOrdinaryRetirement.Effects(() -> { accepted.incrementAndGet(); return true; }, discarded::incrementAndGet));
                return true;
            }
        }
        RocketOrdinaryRetirement.Held reserve(RocketTransferSavedData journal, UUID logical) {
            return ordinary.holdFailBack(journal, logical);
        }
        public void close() throws Exception { ((AutoCloseable) owner).close(); }
    }

    private static RocketTransferSavedData journal(Object r) { return (RocketTransferSavedData) field(r, "journal"); }
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
