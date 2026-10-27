package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Refused callers and native-window nudges over the unchanged production-composition fixture. */
class SealedFailBackAdmissionHandoffTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @CsvSource({"2,true", "2,false", "63,true", "63,false"})
    void initiallyRefusedFailBackRetriesAcrossSealedCompletions(int count, boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(count, callbacksFirst, 18)) {
            f.tick(0); f.tick(1);
            assertTrue((boolean) invoke(f.entry(0), "activeAttempt", new Class<?>[0]));
            var r = f.newReturn();
            var journal = (RocketTransferSavedData) field(r, "journal");
            var before = journal.save(new net.minecraft.nbt.CompoundTag()).copy();
            assertFalse(f.offer(r));
            assertEquals(0, f.ordinary.size());
            assertEquals(0, f.ordinary.activeAttempts());
            assertEquals(before, journal.save(new net.minecraft.nbt.CompoundTag()));
            assertTrue(journal.actionable(record(r).logicalRocketId()));
            for (long tick = 2; tick <= 300; tick++) {
                f.now(tick); f.offer(r); f.tick(tick);
                if (number(r, "started") < 0) {
                    for (var other : f.entries().subList(1, count)) {
                        assertEquals(-1, number(other, "started"), "A later sealed owner captured before the refused return");
                        assertFalse((boolean) invoke(other, "activeAttempt", new Class<?>[0]),
                                "A later sealed owner reserved before the refused return");
                    }
                }
            }
            System.out.printf("refused count=%d callbacksFirst=%s firstEnd=%d opportunity=%d returnStart=%d sealed2Start=%d pending=%d%n",
                    count, callbacksFirst, number(f.entry(0), "finished"), number(f.entry(0), "opportunity"),
                    number(r, "started"), number(f.entry(1), "started"), f.ordinary.size());
            assertBound(f.entry(0), r);
        }
    }

    @ParameterizedTest @CsvSource({"true", "false"})
    void abandonedRefusedCallerExpiresWithoutRetainingAPendingReturn(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(2, callbacksFirst, 18)) {
            f.tick(0); f.tick(1); var abandoned = f.newReturn();
            assertFalse(f.offer(abandoned));
            for (long tick = 2; tick <= 110; tick++) { f.tick(tick); }
            long ended = number(f.entry(0), "finished"), next = number(f.entry(1), "started");
            assertTrue(next >= ended && next <= ended + 2, "An absent caller retained sealed admission: " + next);
            assertEquals(0, f.ordinary.size()); assertEquals(0, f.ordinary.activeAttempts());
            assertTrue(((RocketTransferSavedData) field(abandoned, "journal")).actionable(record(abandoned).logicalRocketId()));
        }
    }

    @ParameterizedTest @CsvSource({"true", "false"})
    void aFullPendingQueueDoesNotPromiseAnUnadmittableCallerATurn(boolean callbacksFirst) throws Exception {
        try (var f = new Fixture(64, callbacksFirst, 18)) {
            f.tick(0); f.tick(1); var refused = f.newReturn();
            assertFalse(f.ordinary.pendingRoom(record(refused).logicalRocketId()));
            assertFalse(f.offer(refused));
            for (long tick = 2; tick <= 100; tick++) { f.tick(tick); }
            long next = Long.MAX_VALUE;
            // The rotating selector need not choose the second list element after the first owner ends.
            for (var other : f.entries().subList(1, 64)) {
                long started = number(other, "started");
                if (started >= 0) { next = Math.min(next, started); }
            }
            assertEquals(number(f.entry(0), "opportunity"), next);
            assertEquals(0, f.ordinary.size());
        }
    }

    @ParameterizedTest @CsvSource({"true,true", "false,true", "true,false", "false,false"})
    void pendingFailBackRetainsItsTurnThroughNativeBusy(boolean callbacksFirst, boolean nudge) throws Exception {
        try (var f = new Fixture(2, callbacksFirst, 20)) {
            f.now(-2);
            var crew = new java.util.concurrent.atomic.AtomicBoolean(false);
            var waiting = f.newReturn(); assertTrue(f.offer(waiting, crew));
            assertEquals(-1, number(waiting, "started"));
            f.tick(-1);
            crew.set(true);
            for (long tick = 0; tick <= 300; tick++) {
                f.now(tick);
                if (nudge && number(f.entry(0), "finished") >= 0 && number(waiting, "started") < 0) {
                    boolean admitted = f.offer(waiting);
                    if (admitted) {
                        System.out.printf("nudge callbacksFirst=%s tick=%d reason=%s attempts=%d%n", callbacksFirst, tick,
                                f.ordinary.reason(record(waiting).logicalRocketId()), f.ordinary.activeAttempts());
                    }
                }
                f.tick(tick);
            }
            System.out.printf("busy callbacksFirst=%s nudge=%s firstStart=%d firstEnd=%d opportunity=%d returnStart=%d sealed2Start=%d%n",
                    callbacksFirst, nudge, number(f.entry(0), "started"), number(f.entry(0), "finished"),
                    number(f.entry(0), "opportunity"), number(waiting, "started"), number(f.entry(1), "started"));
            assertEquals(0, number(f.entry(0), "started"), "The exhausted-window fixture must start its first sealed owner at zero");
            assertEquals(84, number(f.entry(0), "finished"));
            assertEquals(100, number(f.entry(0), "opportunity"));
            assertBound(f.entry(0), waiting);
            assertTrue(number(f.entry(1), "started") > number(waiting, "started"), "A later sealed owner overtook the waiting return");
        }
    }

    private static void assertBound(Object sealed, Object ordinary) throws Exception {
        long ended = number(sealed, "finished"), opportunity = number(sealed, "opportunity"), started = number(ordinary, "started");
        assertTrue(ended >= 0 && opportunity >= ended && started >= opportunity && started <= opportunity + 2,
                "Sealed ended at " + ended + ", native opportunity " + opportunity + ", return first capture " + started);
    }
    private final class Fixture implements AutoCloseable {
        final Object owner;
        final RocketOrdinaryRetirement ordinary;
        Fixture(int count, boolean callbacksFirst, int slots) throws Exception {
            var test = new SealedOrdinaryHandoffTest(); test.directory = directory;
            var type = Arrays.stream(SealedOrdinaryHandoffTest.class.getDeclaredClasses())
                    .filter(c -> c.getSimpleName().equals("Fixture")).findFirst().orElseThrow();
            var ctor = type.getDeclaredConstructor(SealedOrdinaryHandoffTest.class, int.class, boolean.class, boolean.class, int.class);
            ctor.setAccessible(true); owner = ctor.newInstance(test, count, callbacksFirst, false, slots);
            ordinary = (RocketOrdinaryRetirement) field(owner, "ordinary");
        }
        List<?> entries() throws Exception { return (List<?>) field(owner, "entries"); }
        Object entry(int index) throws Exception { return entries().get(index); }
        void now(long tick) throws Exception { set(owner, "now", tick); }
        void tick(long tick) throws Exception { invoke(owner, "tick", new Class<?>[] {long.class}, tick); }
        @SuppressWarnings("unchecked") Object newReturn() throws Exception {
            var type = Arrays.stream(owner.getClass().getDeclaredClasses()).filter(c -> c.getSimpleName().equals("Return")).findFirst().orElseThrow();
            var ctor = type.getDeclaredConstructor(owner.getClass(), int.class); ctor.setAccessible(true);
            var returns = (List<Object>) field(owner, "returns");
            var r = ctor.newInstance(owner, returns.size()); returns.add(r); return r;
        }
        boolean offer(Object r) throws Exception {
            return offer(r, null);
        }
        boolean offer(Object r, java.util.concurrent.atomic.AtomicBoolean crew) throws Exception {
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
        public void close() throws Exception { ((AutoCloseable) owner).close(); }
    }
    private static RocketTransferRecord record(Object r) throws Exception { return (RocketTransferRecord) field(r, "record"); }
    private static Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static long number(Object target, String name) throws Exception { return ((Number) field(target, name)).longValue(); }
    private static void set(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception {
        var m = target.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(target, values); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) { throw error; }
            if (failure.getCause() instanceof Exception exception) { throw exception; }
            throw failure;
        }
    }
}
