package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SealedWorkSchedulingTest {
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

    // W129aM transition (ADR-074 item 8): a request returns at once and its write's result is published on a later
    // owner tick, so each case below publishes between the request and the assertion on its result. No assertion changed.
    @Test void manyParticipantsRequestOnlyOneWholeJournalCapturePerTick() throws Exception {
        var port = new Port(); var scheduler = scheduler(port);
        for (int i = 0; i < 64; i++) { request(scheduler); }
        flush(scheduler); assertEquals(1, port.writes);
        port.publish(); assertTrue(port.current);
        port.current = false; request(scheduler); flush(scheduler);
        assertEquals(1, port.writes);
        port.tick++; flush(scheduler); assertEquals(2, port.writes);
    }

    @Test void unknownAndFailedCapturesKeepDemandButWaitTheExisting200TickBackoff() throws Exception {
        for (var outcome : new Outcome[] {Outcome.UNKNOWN, Outcome.NOT_COMMITTED}) {
            var port = new Port(); port.outcome = outcome; var scheduler = scheduler(port);
            request(scheduler); flush(scheduler); assertEquals(1, port.writes);
            port.publish();
            for (int tick = 1; tick < 200; tick++) { port.tick = tick; request(scheduler); flush(scheduler); }
            assertEquals(1, port.writes); assertFalse(port.current);
            port.tick = 200; port.outcome = Outcome.COMMITTED; flush(scheduler);
            assertEquals(2, port.writes);
            port.publish(); assertTrue(port.current);
            assertEquals(List.of(outcome), port.written, "Each failed write is reported once, after its publication");
        }
    }

    @Test void aCommittedReturnDoesNotConsumeDemandForAnAlreadyStaleAcknowledgement() throws Exception {
        var port = new Port(); port.stale = true; var scheduler = scheduler(port);
        request(scheduler); flush(scheduler); assertEquals(1, port.writes);
        port.publish(); assertFalse(port.current);
        flush(scheduler); assertEquals(1, port.writes);
        port.tick++; port.stale = false; flush(scheduler); assertEquals(2, port.writes);
        port.publish(); assertTrue(port.current);
    }

    @Test void writePendingIsNeverAResultAndNeverStartsASecondCapture() throws Exception {
        for (var outcome : new Outcome[] {Outcome.COMMITTED, Outcome.NOT_COMMITTED, Outcome.UNKNOWN}) {
            var port = new Port(); port.outcome = outcome; var scheduler = scheduler(port);
            request(scheduler); flush(scheduler); assertEquals(1, port.writes);
            // Past the 200-tick retry, a write that is still pending is waited for: not repeated, not reported.
            for (int tick = 1; tick <= 400; tick++) { port.tick = tick; request(scheduler); flush(scheduler); }
            assertEquals(1, port.writes, "WRITE_PENDING started another capture");
            assertTrue(port.written.isEmpty(), "WRITE_PENDING was reported as a result");
            assertFalse(port.current);
            port.publish(); port.tick++; flush(scheduler);
            assertEquals(List.of(outcome), port.written, "The published result is reported once");
            assertEquals(outcome == Outcome.COMMITTED ? 1 : 2, port.writes, "Only a published failure may capture again");
        }
    }

    @Test void aCommittedWriteThatMadeTheJournalCurrentStillAllowsTheNextTick() throws Exception {
        var port = new Port(); var scheduler = scheduler(port);
        request(scheduler); flush(scheduler); assertEquals(1, port.writes);
        port.publish(); flush(scheduler);
        assertTrue(port.current); assertEquals(List.of(Outcome.COMMITTED), port.written);
        // A later change inside the 200-tick failure wait: a COMMITTED result always allowed the next tick.
        port.tick = 1; port.current = false; request(scheduler); flush(scheduler);
        assertEquals(2, port.writes, "A COMMITTED result settled while current kept the failure wait");
    }

    @Test void aRefusedRequestKeepsDemandBehindTheRetryAndReportsNoResult() throws Exception {
        var port = new Port(); port.refuse = true; var scheduler = scheduler(port);
        request(scheduler); flush(scheduler); assertEquals(1, port.writes);
        port.refuse = false;
        for (int tick = 1; tick < 200; tick++) { port.tick = tick; flush(scheduler); }
        assertEquals(1, port.writes); assertTrue(port.written.isEmpty());
        port.tick = 200; flush(scheduler); assertEquals(2, port.writes);
        port.publish(); assertTrue(port.current); assertTrue(port.written.isEmpty());
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
            case "request" -> {
                state.writes++;
                if (state.refuse) { yield false; }
                state.pending = true; yield true;
            }
            case "writePending" -> state.pending;
            case "outcome" -> state.published;
            case "written" -> { state.written.add((Outcome) args[0]); yield null; }
            default -> throw new AssertionError(method.getName());
        });
        return type.getConstructor(port).newInstance(proxy);
    }
    private static final class Port {
        long tick; int writes; boolean current, stale, pending, refuse; boolean operational = true;
        Outcome outcome = Outcome.COMMITTED, published = Outcome.UNACKNOWLEDGED;
        final List<Outcome> written = new ArrayList<>();
        /** The requested write's completion, published by the owner thread on a later tick. */
        void publish() {
            assertTrue(pending, "No write was requested");
            pending = false; published = outcome; current = outcome == Outcome.COMMITTED && !stale;
        }
    }
}
