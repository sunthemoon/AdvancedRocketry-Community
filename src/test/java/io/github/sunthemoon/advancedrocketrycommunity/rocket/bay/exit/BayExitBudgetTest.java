package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class BayExitBudgetTest {
    @Test void candidateCeilingsCannotBeResetBySameTickOrNestedCalls() {
        var budget = new BayExitBudget(50);
        for (int n = 0; n < 128; n++) { budget.openProbe().orElseThrow().close(); }
        assertTrue(budget.openProbe().isEmpty());
        budget.advanceTick(50);
        assertEquals(new BayExitBudget.Usage(128, 0, 0), budget.usage());
        assertEquals(32, BayExitBudget.CANDIDATES_PER_EXIT);
        assertThrows(IllegalStateException.class, () -> budget.advanceTick(49));
        budget.advanceTick(51);
        assertEquals(new BayExitBudget.Usage(0, 0, 0), budget.usage());
        try (var probe = budget.openProbe().orElseThrow()) {
            assertThrows(IllegalStateException.class, () -> budget.advanceTick(52));
            assertTrue(probe.reserveReads(1));
        }
        assertEquals(new BayExitBudget.Usage(1, 1, 0), budget.usage());
    }

    @Test void exactPerCandidateReadAndVisitBoundsPoisonAnIncompleteProof() {
        var budget = new BayExitBudget(0);
        try (var probe = budget.openProbe().orElseThrow()) {
            assertTrue(probe.reserveReads(96));
            assertFalse(probe.reserveReads(1));
            for (int n = 0; n < 16; n++) { assertTrue(probe.visit()); }
            assertFalse(probe.visit());
            assertTrue(probe.incomplete());
        }
        assertEquals(new BayExitBudget.Usage(1, 96, 16), budget.usage());
        try (var probe = budget.openProbe().orElseThrow()) {
            assertFalse(probe.reserveReads(97));
            assertFalse(probe.reserveReads(-1));
        }
        assertEquals(new BayExitBudget.Usage(2, 96, 16), budget.usage());
    }

    @Test void globalReadLimitReservesAtomicallyAndContactProbesShareIt() {
        var budget = new BayExitBudget(0);
        for (int n = 0; n < 42; n++) {
            try (var probe = budget.openProbe().orElseThrow()) { assertTrue(probe.reserveReads(96)); }
        }
        try (var probe = budget.openProbe().orElseThrow()) {
            assertFalse(probe.reserveReads(65));
            assertEquals(4032, budget.usage().reads());
            assertTrue(probe.reserveReads(64));
            assertFalse(probe.reserveReads(1));
        }
        try (var contact = budget.openProbe().orElseThrow()) { assertFalse(contact.reserveReads(1)); }
        assertEquals(new BayExitBudget.Usage(44, 4096, 0), budget.usage());
    }

    @Test void globalEntityLimitIncludesNoncollidingEntitiesAndReservations() {
        var budget = new BayExitBudget(0);
        for (int n = 0; n < 16; n++) {
            try (var probe = budget.openProbe().orElseThrow()) {
                for (int e = 0; e < 16; e++) { assertTrue(probe.visit()); }
            }
        }
        try (var probe = budget.openProbe().orElseThrow()) { assertFalse(probe.visit()); }
        assertEquals(new BayExitBudget.Usage(17, 0, 256), budget.usage());
    }

    @Test void closedAndForeignThreadProbesCannotSpendOrReplenish() throws InterruptedException {
        var budget = new BayExitBudget(0);
        var probe = budget.openProbe().orElseThrow();
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try { probe.visit(); } catch (Throwable error) { failure.set(error); }
        });
        thread.start(); thread.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        probe.close(); probe.close();
        assertThrows(IllegalStateException.class, probe::visit);
        assertThrows(IllegalStateException.class, () -> probe.reserveReads(1));
        assertEquals(new BayExitBudget.Usage(1, 0, 0), budget.usage());
    }
}
