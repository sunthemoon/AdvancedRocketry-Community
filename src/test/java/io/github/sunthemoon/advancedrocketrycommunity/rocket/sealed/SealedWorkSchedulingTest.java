package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class SealedWorkSchedulingTest {
    @Test void phaseCeilingsShareTheOwnerAllowanceAndCannotResetOrEscapeTheirTick() {
        var owner = new SealedWorkBudget(); owner.beginTick(1);
        owner.runLimitedTo(128, () -> {
            owner.beginTick(1);
            owner.runLimitedTo(200, () -> {
                for (int i = 0; i < 128; i++) { assertTrue(owner.take()); }
                assertFalse(owner.take());
            });
            assertFalse(owner.take());
            assertThrows(IllegalStateException.class, () -> owner.beginTick(2));
        });
        assertEquals(128, owner.remaining());
        assertThrows(IllegalArgumentException.class, () -> owner.runLimitedTo(64, () -> {
            for (int i = 0; i < 64; i++) { assertTrue(owner.take()); }
            assertFalse(owner.take());
            throw new IllegalArgumentException("fixture callback");
        }));
        assertEquals(64, owner.remaining());
        for (int i = 0; i < 64; i++) { assertTrue(owner.take()); }
        assertFalse(owner.take()); owner.beginTick(2);
        assertEquals(256, owner.remaining());
    }

    @Test void everyConsumerSharesTheSame256VisitsAndRepeatedTickEntryCannotResetThem() throws Exception {
        var budget = budget(); begin(budget, 4);
        for (int i = 0; i < 256; i++) {
            begin(budget, 4);
            assertTrue(take(budget));
        }
        assertFalse(take(budget)); begin(budget, 4); assertFalse(take(budget));
        begin(budget, 5); assertTrue(take(budget));
    }

    @Test void noInspectionCanOccurBeforeTheOwnerBeginsItsTick() throws Exception {
        var budget = budget();
        assertFalse(take(budget)); begin(budget, 0); assertTrue(take(budget));
    }

    @Test void manyParticipantsRequestOnlyOneWholeJournalCapturePerTick() throws Exception {
        var port = new Port(); var scheduler = scheduler(port);
        for (int i = 0; i < 64; i++) { request(scheduler); }
        flush(scheduler); assertEquals(1, port.writes); assertTrue(port.current);
        port.current = false; request(scheduler); flush(scheduler);
        assertEquals(1, port.writes);
        port.tick++; flush(scheduler); assertEquals(2, port.writes);
    }

    @Test void unknownAndFailedCapturesKeepDemandButWaitTheExisting200TickBackoff() throws Exception {
        for (var outcome : new Outcome[] {Outcome.UNKNOWN, Outcome.NOT_COMMITTED}) {
            var port = new Port(); port.outcome = outcome; var scheduler = scheduler(port);
            request(scheduler); flush(scheduler); assertEquals(1, port.writes);
            for (int tick = 1; tick < 200; tick++) { port.tick = tick; request(scheduler); flush(scheduler); }
            assertEquals(1, port.writes); assertFalse(port.current);
            port.tick = 200; port.outcome = Outcome.COMMITTED; flush(scheduler);
            assertEquals(2, port.writes); assertTrue(port.current);
        }
    }

    @Test void aCommittedReturnDoesNotConsumeDemandForAnAlreadyStaleAcknowledgement() throws Exception {
        var port = new Port(); port.stale = true; var scheduler = scheduler(port);
        request(scheduler); flush(scheduler); assertEquals(1, port.writes); assertFalse(port.current);
        flush(scheduler); assertEquals(1, port.writes);
        port.tick++; port.stale = false; flush(scheduler); assertEquals(2, port.writes); assertTrue(port.current);
    }

    @Test void currentOrNonOperationalRootsNeverCauseUnneededCaptures() throws Exception {
        var port = new Port(); var scheduler = scheduler(port);
        flush(scheduler); assertEquals(0, port.writes);
        port.current = true; request(scheduler); flush(scheduler); assertEquals(0, port.writes);
        port.current = false; port.operational = false; request(scheduler); flush(scheduler); assertEquals(0, port.writes);
        port.tick++; port.operational = true; flush(scheduler); assertEquals(1, port.writes);
    }

    private static Object budget() throws Exception { return Class.forName(SealedWorkSchedulingTest.class.getPackageName() + ".SealedWorkBudget").getConstructor().newInstance(); }
    private static void begin(Object budget, long tick) throws Exception { budget.getClass().getMethod("beginTick", long.class).invoke(budget, tick); }
    private static boolean take(Object budget) throws Exception { return (boolean) budget.getClass().getMethod("take").invoke(budget); }
    private static void request(Object scheduler) throws Exception { scheduler.getClass().getMethod("request").invoke(scheduler); }
    private static void flush(Object scheduler) throws Exception { scheduler.getClass().getMethod("flush").invoke(scheduler); }
    private static Object scheduler(Port state) throws Exception {
        var type = Class.forName(SealedWorkSchedulingTest.class.getPackageName() + ".SealedJournalRequests");
        var port = Class.forName(type.getName() + "$Port");
        var proxy = Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port}, (object, method, args) -> switch (method.getName()) {
            case "now" -> state.tick;
            case "operational" -> state.operational;
            case "current" -> state.current;
            case "checkpoint" -> { state.writes++; state.current = state.outcome == Outcome.COMMITTED && !state.stale; yield state.outcome; }
            default -> throw new AssertionError(method.getName());
        });
        return type.getConstructor(port).newInstance(proxy);
    }
    private static final class Port {
        long tick; int writes; boolean current, stale; boolean operational = true; Outcome outcome = Outcome.COMMITTED;
    }
}
