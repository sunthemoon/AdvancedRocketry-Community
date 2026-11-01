package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidatesTest.pos;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.*;

final class BayExitSelectorTest {
    private static final Request REQUEST = new Request(pos(0, 100, 0), pos(0, 100, 0), Optional.empty(),
            Optional.of(new Anchor("test:level", 0, 64, 0)), .6, 1.8);
    private static final List<Position> C = BayExitCandidates.ordered(REQUEST.rocket(), REQUEST.hint(), REQUEST.anchor());

    /** R4's synthetic one-read/one-visit fixture; no fall grant/history/contact work is modeled in S1. */
    private static final class CountingPort implements Port {
        final Set<Position> available = new HashSet<>();
        final Set<Position> occupied = new HashSet<>();
        public Verdict inspect(Box box, BayExitBudget.Probe probe, Reservations reservations) {
            if (!probe.reserveReads(1)) { return Verdict.UNPROVED; }
            if (!available.contains(box.feet()) || occupied.contains(box.feet())) {
                return probe.visit() ? Verdict.UNSAFE : Verdict.UNPROVED;
            }
            if (!reservations.clear(box, probe)) { return probe.incomplete() ? Verdict.UNPROVED : Verdict.UNSAFE; }
            return probe.visit() ? Verdict.SAFE : Verdict.UNPROVED;
        }
    }

    private void assertResult(Result result, Status status, int ordinal, long attempt, long capture, int units) {
        assertEquals(status, result.status()); assertEquals(ordinal, result.ordinal());
        assertEquals(attempt, result.attemptSequence()); assertEquals(capture, result.captureSequence());
        assertEquals(ordinal == 0 ? REQUEST.fallback() : C.get(ordinal - 1), result.position());
        assertEquals(new BayExitBudget.Usage(units, units, units), result.work());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyOneBoxExecutionOrderWinsAndLoserTriesAllThirtyOne(boolean reverse) {
        var budget = new BayExitBudget(0); var port = new CountingPort(); port.available.add(C.get(0));
        var selector = new BayExitSelector(budget, port);
        var a = selector.capture(REQUEST, () -> true); var b = selector.capture(REQUEST, () -> true);
        var first = reverse ? b : a; var second = reverse ? a : b;
        assertEquals(new BayExitBudget.Usage(0, 0, 0), budget.usage());
        assertResult(selector.select(first), SAFE, 1, 1, reverse ? 2 : 1, 1);
        assertResult(selector.select(second), NO_SAFE_EXIT, 0, 2, reverse ? 1 : 2, 31);
        assertEquals(new BayExitBudget.Usage(32, 32, 32), budget.usage());
        assertSame(first.result().orElseThrow(), selector.select(first));
        assertEquals(new BayExitBudget.Usage(32, 32, 32), budget.usage());
    }

    @Test void secondExitTakesExactNextCandidateWhileFirstIsStillReserved() {
        var budget = new BayExitBudget(0); var port = new CountingPort(); port.available.addAll(C.subList(0, 2));
        var selector = new BayExitSelector(budget, port);
        var a = selector.capture(REQUEST, () -> true); var b = selector.capture(REQUEST, () -> true);
        assertResult(selector.select(a), SAFE, 1, 1, 1, 1);
        assertResult(selector.select(b), SAFE, 2, 2, 2, 2);
        assertEquals(new BayExitBudget.Usage(3, 3, 3), budget.usage());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lastCandidateUnitGoesToFirstExecutedExitNotFirstCaptured(boolean reverse) {
        var budget = new BayExitBudget(0);
        for (int n = 0; n < 127; n++) { budget.openProbe().orElseThrow().close(); }
        var port = new CountingPort(); port.available.add(C.get(0)); var selector = new BayExitSelector(budget, port);
        var a = selector.capture(REQUEST, () -> true); var b = selector.capture(REQUEST, () -> true);
        assertResult(selector.select(reverse ? b : a), SAFE, 1, 1, reverse ? 2 : 1, 1);
        assertResult(selector.select(reverse ? a : b), EXIT_BUDGET_EXHAUSTED, 0, 2, reverse ? 1 : 2, 0);
        assertEquals(new BayExitBudget.Usage(128, 1, 1), budget.usage());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void delayedCaptureCannotPreemptSynchronousSelectionOrReserveFutureBudget(boolean lastUnit) {
        var budget = new BayExitBudget(0);
        if (lastUnit) { for (int n = 0; n < 127; n++) { budget.openProbe().orElseThrow().close(); } }
        var port = new CountingPort(); port.available.add(C.get(0)); var selector = new BayExitSelector(budget, port);
        var delayed = selector.capture(REQUEST, () -> true);
        assertEquals(new BayExitBudget.Usage(lastUnit ? 127 : 0, 0, 0), budget.usage());
        var immediate = selector.capture(REQUEST, () -> true);
        assertResult(selector.select(immediate), SAFE, 1, 1, 2, 1);
        var completed = new ArrayList<Context>(); selector.drain(completed::add);
        assertEquals(List.of(delayed), completed);
        assertResult(delayed.result().orElseThrow(), lastUnit ? EXIT_BUDGET_EXHAUSTED : NO_SAFE_EXIT,
                0, 2, 1, lastUnit ? 0 : 31);
        assertEquals(new BayExitBudget.Usage(lastUnit ? 128 : 32, lastUnit ? 1 : 32, lastUnit ? 1 : 32), budget.usage());
    }

    @Test void fifoDrainAndEarlierSynchronousReservationSurviveUntilAllDecisionsFinish() {
        var budget = new BayExitBudget(0); var port = new CountingPort(); port.available.add(C.get(0));
        var selector = new BayExitSelector(budget, port);
        var a = selector.capture(REQUEST, () -> true); var b = selector.capture(REQUEST, () -> true);
        var c = selector.capture(REQUEST, () -> true); selector.select(a);
        var order = new ArrayList<Long>(); selector.drain(context -> order.add(context.captureSequence()));
        assertEquals(List.of(2L, 3L), order);
        assertResult(b.result().orElseThrow(), NO_SAFE_EXIT, 0, 2, 2, 31);
        assertResult(c.result().orElseThrow(), NO_SAFE_EXIT, 0, 3, 3, 31);
        assertEquals(new BayExitBudget.Usage(63, 63, 63), budget.usage());
        budget.advanceTick(1);
        assertEquals(SAFE, selector.select(selector.capture(REQUEST, () -> true)).status(), "END expired all reservations");
    }

    @Test void staleDelayedCompletionCostsZeroAndTheNextValidCaptureStartsAttemptOne() {
        var budget = new BayExitBudget(0); var port = new CountingPort(); port.available.add(C.get(0));
        var selector = new BayExitSelector(budget, port); var current = new AtomicBoolean(true);
        var stale = selector.capture(REQUEST, current::get); current.set(false);
        var next = selector.capture(REQUEST, () -> true); var completed = new ArrayList<Context>();
        selector.drain(completed::add);
        assertEquals(List.of(next), completed);
        assertResult(stale.result().orElseThrow(), STALE, 0, 0, 1, 0);
        assertResult(next.result().orElseThrow(), SAFE, 1, 1, 2, 1);
        assertEquals(new BayExitBudget.Usage(1, 1, 1), budget.usage());
    }

    @Test void sameContextIsPendingInFlightAndDifferentNestedExitRefusesWithoutWork() {
        var budget = new BayExitBudget(0); var contexts = new Context[2]; var holder = new BayExitSelector[1];
        var selector = new BayExitSelector(budget, (box, probe, reservations) -> {
            assertEquals(PENDING, holder[0].select(contexts[0]).status());
            assertResult(holder[0].select(contexts[1]), REENTRANT_REFUSED, 0, 0, 2, 0);
            assertThrows(IllegalStateException.class, () -> budget.advanceTick(1));
            assertTrue(probe.reserveReads(1)); assertTrue(reservations.clear(box, probe)); assertTrue(probe.visit());
            return Verdict.SAFE;
        });
        holder[0] = selector; contexts[0] = selector.capture(REQUEST, () -> true); contexts[1] = selector.capture(REQUEST, () -> true);
        assertResult(selector.select(contexts[0]), SAFE, 1, 1, 1, 1);
        assertEquals(new BayExitBudget.Usage(1, 1, 1), budget.usage());
    }

    @Test void hintIsRevalidatedAndAllUnsafeHasNoUncheckedAltitudePlacement() {
        var budget = new BayExitBudget(0); var port = new CountingPort(); port.available.add(C.get(0));
        var selector = new BayExitSelector(budget, port);
        var hintRequest = new Request(REQUEST.rocket(), REQUEST.fallback(), Optional.of(pos(90, 64, 90)),
                REQUEST.anchor(), .6, 1.8);
        var result = selector.select(selector.capture(hintRequest, () -> true));
        assertEquals(SAFE, result.status()); assertEquals(C.get(0), result.position()); assertEquals(2, result.ordinal());
        assertEquals(new BayExitBudget.Usage(2, 2, 2), result.work());
        port.available.clear();
        assertResult(selector.select(selector.capture(REQUEST, () -> true)), NO_SAFE_EXIT, 0, 2, 2, 31);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lastReadOrVisitIsUsableButInsufficientRemainingAllowanceNeverProducesSafety(boolean reads) {
        var budget = new BayExitBudget(0);
        int remaining = reads ? 4095 : 255;
        while (remaining > 0) {
            int used = Math.min(remaining, reads ? 96 : 16);
            try (var probe = budget.openProbe().orElseThrow()) {
                if (reads) { assertTrue(probe.reserveReads(used)); }
                else { for (int n = 0; n < used; n++) { assertTrue(probe.visit()); } }
            }
            remaining -= used;
        }
        var port = new CountingPort(); port.available.add(C.get(0)); var selector = new BayExitSelector(budget, port);
        assertResult(selector.select(selector.capture(REQUEST, () -> true)), SAFE, 1, 1, 1, 1);
        var loser = selector.select(selector.capture(REQUEST, () -> true));
        assertEquals(EXIT_BUDGET_EXHAUSTED, loser.status()); assertEquals(REQUEST.fallback(), loser.position());
        assertEquals(2, loser.attemptSequence()); assertEquals(2, loser.captureSequence()); assertEquals(0, loser.ordinal());
        assertEquals(new BayExitBudget.Usage(31, reads ? 0 : 31, 0), loser.work());
        assertEquals(reads ? new BayExitBudget.Usage(75, 4096, 1) : new BayExitBudget.Usage(48, 32, 256), budget.usage());
    }

    @Test void releasingAnotherContextCannotRemoveAReservationAndApplicationUsesActualOccupancy() {
        var budget = new BayExitBudget(0); var port = new CountingPort(); port.available.add(C.get(0));
        var selector = new BayExitSelector(budget, port);
        var a = selector.capture(REQUEST, () -> true); selector.select(a);
        selector.release(selector.capture(REQUEST, () -> true));
        assertEquals(NO_SAFE_EXIT, selector.select(selector.capture(REQUEST, () -> true)).status());
        port.occupied.add(C.get(0)); selector.applied(a);
        assertEquals(NO_SAFE_EXIT, selector.select(selector.capture(REQUEST, () -> true)).status());
        port.occupied.clear(); selector.release(a);
        assertEquals(SAFE, selector.select(selector.capture(REQUEST, () -> true)).status());
    }

    @Test void cappedStateRefusesWithoutEvictionAndForeignContextsCannotBeSelected() {
        var budget = new BayExitBudget(0); var selector = new BayExitSelector(budget, new CountingPort());
        var first = selector.capture(REQUEST, () -> true);
        for (int n = 1; n < 1024; n++) { selector.capture(REQUEST, () -> true); }
        assertEquals(CAPACITY_REFUSED, selector.select(selector.capture(REQUEST, () -> true)).status());
        assertResult(selector.select(first), NO_SAFE_EXIT, 0, 1, 1, 31);
        var other = new BayExitSelector(new BayExitBudget(0), new CountingPort());
        assertThrows(IllegalArgumentException.class, () -> other.select(first));
        assertEquals(new BayExitBudget.Usage(31, 31, 31), budget.usage());
    }

    @Test void incompleteOrMissingReservationProofCannotBeTrustedEvenWhenPortSaysSafe() {
        var budget = new BayExitBudget(0);
        var selector = new BayExitSelector(budget, (box, probe, reservations) -> {
            probe.reserveReads(97); reservations.clear(box, probe); return Verdict.SAFE;
        });
        assertEquals(EXIT_BUDGET_EXHAUSTED, selector.select(selector.capture(REQUEST, () -> true)).status());
        var unchecked = new BayExitSelector(budget, (box, probe, reservations) -> Verdict.SAFE);
        assertEquals(NO_SAFE_EXIT, unchecked.select(unchecked.capture(REQUEST, () -> true)).status());
        assertEquals(new BayExitBudget.Usage(62, 0, 0), budget.usage());
    }

    @Test void maximumCandidateListStopsAtThirtyTwoAndValidHintIsStillProbed() {
        var budget = new BayExitBudget(0); var port = new CountingPort();
        var selector = new BayExitSelector(budget, port);
        var request = new Request(REQUEST.rocket(), REQUEST.fallback(), Optional.of(pos(90, 64, 90)),
                REQUEST.anchor(), .6, 1.8);
        var unsafe = selector.select(selector.capture(request, () -> true));
        assertEquals(NO_SAFE_EXIT, unsafe.status()); assertEquals(REQUEST.fallback(), unsafe.position());
        assertEquals(new BayExitBudget.Usage(32, 32, 32), unsafe.work());
        port.available.add(request.hint().orElseThrow());
        var hint = selector.select(selector.capture(request, () -> true));
        assertEquals(SAFE, hint.status()); assertEquals(request.hint().orElseThrow(), hint.position());
        assertEquals(1, hint.ordinal()); assertEquals(new BayExitBudget.Usage(1, 1, 1), hint.work());
    }

    @Test void losingCurrentnessDuringAProbeCannotPublishAPlacement() {
        var budget = new BayExitBudget(0); var current = new AtomicBoolean(true);
        var selector = new BayExitSelector(budget, (box, probe, reservations) -> {
            assertTrue(probe.reserveReads(1)); assertTrue(reservations.clear(box, probe)); assertTrue(probe.visit());
            current.set(false); return Verdict.SAFE;
        });
        assertResult(selector.select(selector.capture(REQUEST, current::get)), STALE, 0, 1, 1, 1);
        assertEquals(new BayExitBudget.Usage(1, 1, 1), budget.usage());
    }

    @Test void exceptionalDecisionCannotSpendAgainAndExceptionalDrainDiscardsTheWholeBatch() {
        var budget = new BayExitBudget(0);
        var broken = new BayExitSelector(budget, (box, probe, reservations) -> { throw new IllegalStateException("fixture"); });
        var context = broken.capture(REQUEST, () -> true);
        assertThrows(IllegalStateException.class, () -> broken.select(context));
        assertEquals(NO_SAFE_EXIT, broken.select(context).status());
        assertEquals(new BayExitBudget.Usage(1, 0, 0), budget.usage());
        var port = new CountingPort(); port.available.add(C.get(0)); var selector = new BayExitSelector(budget, port);
        var a = selector.capture(REQUEST, () -> true); var b = selector.capture(REQUEST, () -> true);
        assertThrows(IllegalStateException.class, () -> selector.drain(c -> { throw new IllegalStateException("fixture"); }));
        assertEquals(SAFE, a.result().orElseThrow().status()); assertEquals(STALE, b.result().orElseThrow().status());
        budget.advanceTick(1);
        assertEquals(SAFE, selector.select(selector.capture(REQUEST, () -> true)).status());
    }

    @Test void pendingContextsInTheSameEndDrainUseFifoCaptureOrder() {
        var budget = new BayExitBudget(0); var port = new CountingPort(); port.available.add(C.get(0));
        var selector = new BayExitSelector(budget, port);
        var a = selector.capture(REQUEST, () -> true); var b = selector.capture(REQUEST, () -> true);
        var order = new ArrayList<Context>(); selector.drain(order::add);
        assertEquals(List.of(a, b), order);
        assertResult(a.result().orElseThrow(), SAFE, 1, 1, 1, 1);
        assertResult(b.result().orElseThrow(), NO_SAFE_EXIT, 0, 2, 2, 31);
        assertEquals(new BayExitBudget.Usage(32, 32, 32), budget.usage());
    }

    @Test void noncollidingReservationVisitsShareTheSixteenVisitCandidateLimit() {
        var budget = new BayExitBudget(0); var port = new CountingPort(); var selector = new BayExitSelector(budget, port);
        Context last = null;
        for (int n = 1; n <= 17; n++) {
            Position hint = pos(2, 100, n * 10);
            port.available.add(hint);
            var request = new Request(REQUEST.rocket(), REQUEST.fallback(), Optional.of(hint), REQUEST.anchor(), .6, 1.8);
            last = selector.capture(request, () -> true);
            Result result = selector.select(last);
            if (n <= 16) {
                assertEquals(SAFE, result.status()); assertEquals(hint, result.position());
                assertEquals(n, result.attemptSequence()); assertEquals(n, result.captureSequence());
                assertEquals(new BayExitBudget.Usage(1, 1, n), result.work());
            }
        }
        Result refused = last.result().orElseThrow();
        assertEquals(EXIT_BUDGET_EXHAUSTED, refused.status()); assertEquals(REQUEST.fallback(), refused.position());
        assertEquals(17, refused.attemptSequence()); assertEquals(17, refused.captureSequence());
        assertEquals(new BayExitBudget.Usage(32, 32, 47), refused.work());
        assertEquals(new BayExitBudget.Usage(48, 48, 183), budget.usage());
    }

    @Test void fallbackCannotCrossLevelsOrContainNonfiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new Request(REQUEST.rocket(),
                new Position("test:other", 0, 100, 0), Optional.empty(), Optional.empty(), .6, 1.8));
        assertThrows(IllegalArgumentException.class, () -> new Request(REQUEST.rocket(),
                pos(Double.NaN, 100, 0), Optional.empty(), Optional.empty(), .6, 1.8));
    }

    @Test void completionCallbackCannotReplenishTheTickBeforeTheRestOfTheDrain() {
        var budget = new BayExitBudget(0); var port = new CountingPort(); port.available.add(C.get(0));
        var selector = new BayExitSelector(budget, port);
        var a = selector.capture(REQUEST, () -> true); var b = selector.capture(REQUEST, () -> true);
        selector.drain(context -> assertThrows(IllegalStateException.class, () -> budget.advanceTick(1)));
        assertResult(a.result().orElseThrow(), SAFE, 1, 1, 1, 1);
        assertResult(b.result().orElseThrow(), NO_SAFE_EXIT, 0, 2, 2, 31);
        assertEquals(new BayExitBudget.Usage(32, 32, 32), budget.usage());
        budget.advanceTick(1);
        assertEquals(new BayExitBudget.Usage(0, 0, 0), budget.usage());
    }
}
