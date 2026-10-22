package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** W129aF (W129a-R2): B3a's verdict needs every figure measured; a missing measurement is the stop rule, with its reason. */
final class RocketWriterOverlapVerdictTest {
    private static final long BUDGET = RocketWriterOverlapVerdict.RSS_BUDGET_BYTES;

    private static Map<String, Object> ticks(double mean, double p95, double p99, double maximum) {
        return new HashMap<>(Map.of("samples", 100, "mean_ms", mean, "p95_ms", p95, "p99_ms", p99, "maximum_ms", maximum));
    }
    private static Map<String, Object> inBudgetTicks() { return ticks(1, 2, 3, 4); }
    private static Map<String, Object> measured(long peak) {
        return Map.of("status", "MEASURED", "working_set_bytes", peak / 2, "peak_working_set_bytes", peak);
    }
    private static RocketWriterOverlapVerdict overlap(Map<String, Object> duration, Map<String, Object> workingSet) {
        return RocketWriterOverlapVerdict.of(false, false, duration, workingSet);
    }

    @Test void everyUnavailableOrFailedWorkingSetStatusIsTheStopRuleWithItsReason() {
        var statuses = List.<Map<String, Object>>of(
                Map.of("status", "FAILED", "error", "java.io.IOException: powershell.exe not found"),
                Map.of("status", "FAILED", "output", "Get-Process : access denied"),
                Map.of("status", "TIMEOUT"),
                Map.of("status", "UNAVAILABLE_NOT_WINDOWS"),
                Map.of());
        for (var workingSet : statuses) {
            for (boolean transferOnly : new boolean[] {false, true}) {
                var verdict = RocketWriterOverlapVerdict.of(false, transferOnly, inBudgetTicks(), workingSet);
                assertEquals("STOP_RULE", verdict.status(), workingSet::toString);
                assertEquals(1, verdict.reasons().size(), verdict::toString);
                assertTrue(verdict.reasons().get(0).startsWith("working set not measured: status=" + workingSet.get("status")), verdict::toString);
            }
        }
        var detail = overlap(inBudgetTicks(), statuses.get(0)).reasons().get(0);
        assertTrue(detail.contains("powershell.exe not found"), detail);
    }

    @Test void aMeasuredStatusWithoutAPositivePeakIsAMissingMeasurement() {
        for (Map<String, Object> workingSet : List.<Map<String, Object>>of(Map.of("status", "MEASURED"),
                Map.of("status", "MEASURED", "peak_working_set_bytes", 0L), Map.of("status", "MEASURED", "peak_working_set_bytes", "4"))) {
            var verdict = overlap(inBudgetTicks(), workingSet);
            assertEquals("STOP_RULE", verdict.status());
            assertEquals(List.of("working set MEASURED without a positive peak_working_set_bytes"), verdict.reasons());
        }
    }

    @Test void theMeasuredPeakPassesAtTheBudgetAndStopsOneByteAbove() {
        assertEquals(new RocketWriterOverlapVerdict("WITHIN_BUDGET", List.of()), overlap(inBudgetTicks(), measured(BUDGET)));
        var over = overlap(inBudgetTicks(), measured(BUDGET + 1));
        assertEquals("STOP_RULE", over.status());
        assertEquals(List.of("peak working set " + (BUDGET + 1) + " bytes > " + BUDGET), over.reasons());
        assertEquals(new RocketWriterOverlapVerdict("COMPARISON_WITHIN_BUDGET", List.of()),
                RocketWriterOverlapVerdict.of(false, true, inBudgetTicks(), measured(BUDGET)));
        assertEquals("COMPARISON_OUTSIDE_BUDGET", RocketWriterOverlapVerdict.of(false, true, inBudgetTicks(), measured(BUDGET + 1)).status());
    }

    @Test void tickFiguresPassAtTheirBudgetsAndStopJustAbove() {
        assertEquals("WITHIN_BUDGET", overlap(ticks(25, 50, 100, 500), measured(BUDGET)).status());
        for (var duration : List.of(ticks(25.001, 50, 100, 500), ticks(25, 50.001, 100, 500), ticks(25, 50, 100.001, 500), ticks(25, 50, 100, 500.001))) {
            var verdict = overlap(duration, measured(BUDGET));
            assertEquals("STOP_RULE", verdict.status(), duration::toString);
            assertEquals(1, verdict.reasons().size()); assertTrue(verdict.reasons().get(0).startsWith("tick work "));
        }
    }

    @Test void missingTickFiguresAreTheStopRuleInEveryMeasuredMode() {
        // plain() of no samples has no figures; NaN or a non-number is not a measurement either.
        var empty = new HashMap<String, Object>(Map.of("samples", 0));
        var nan = ticks(Double.NaN, 2, 3, 4);
        for (var duration : List.<Map<String, Object>>of(empty, nan)) {
            for (boolean transferOnly : new boolean[] {false, true}) {
                var verdict = RocketWriterOverlapVerdict.of(false, transferOnly, duration, measured(BUDGET));
                assertEquals("STOP_RULE", verdict.status(), duration::toString);
                assertTrue(verdict.reasons().stream().allMatch(reason -> reason.endsWith(" not measured")), verdict::toString);
            }
        }
        assertEquals(4, overlap(empty, measured(1)).reasons().size());
    }

    @Test void smokeIsNeverEvidenceButStillListsItsReasons() {
        var verdict = RocketWriterOverlapVerdict.of(true, false, inBudgetTicks(), Map.of("status", "TIMEOUT"));
        assertEquals("SMOKE_COMPLETED", verdict.status());
        assertEquals(List.of("working set not measured: status=TIMEOUT"), verdict.reasons());
    }
}
