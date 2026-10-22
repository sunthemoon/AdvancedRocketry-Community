package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * W129aF (review finding W129a-R2): B3a's verdict fails closed. Every tick figure and the peak working set must be
 * measured and within budget; a figure that is missing, or a working set whose status is not MEASURED (FAILED,
 * TIMEOUT, UNAVAILABLE_NOT_WINDOWS or absent), is a measurement that could not be made and gives STOP_RULE with its
 * reason, as W128's stop rule requires. The budgets are B3a's unchanged ones.
 */
record RocketWriterOverlapVerdict(String status, List<String> reasons) {
    static final double MEAN_MS = 25, SPIKE_MS = 500;
    static final long RSS_BUDGET_BYTES = (long) (5.5 * 1024 * 1024 * 1024);

    RocketWriterOverlapVerdict { reasons = List.copyOf(reasons); }

    /** {@code duration} is the tick work summary of {@code plain}; {@code workingSet} is {@code WriterBenchmarkEnvironment.workingSet()}. */
    static RocketWriterOverlapVerdict of(boolean smoke, boolean transferOnly, Map<String, Object> duration, Map<String, Object> workingSet) {
        var missing = new ArrayList<String>(); var misses = new ArrayList<String>();
        tick(duration, "mean_ms", MEAN_MS, missing, misses);
        tick(duration, "p95_ms", WriterBenchmarkEnvironment.P95_MS, missing, misses);
        tick(duration, "p99_ms", WriterBenchmarkEnvironment.P99_MS, missing, misses);
        tick(duration, "maximum_ms", SPIKE_MS, missing, misses);
        Object status = workingSet.get("status");
        if (!"MEASURED".equals(status)) {
            Object detail = workingSet.containsKey("error") ? workingSet.get("error") : workingSet.get("output");
            missing.add("working set not measured: status=" + status + (detail == null ? "" : " (" + detail + ")"));
        } else if (!(workingSet.get("peak_working_set_bytes") instanceof Number peak) || peak.longValue() <= 0) {
            missing.add("working set MEASURED without a positive peak_working_set_bytes");
        } else if (peak.longValue() > RSS_BUDGET_BYTES) {
            misses.add("peak working set " + peak.longValue() + " bytes > " + RSS_BUDGET_BYTES);
        }
        var reasons = new ArrayList<>(missing); reasons.addAll(misses);
        String verdict = smoke ? "SMOKE_COMPLETED" : !missing.isEmpty() ? "STOP_RULE"
                : transferOnly ? misses.isEmpty() ? "COMPARISON_WITHIN_BUDGET" : "COMPARISON_OUTSIDE_BUDGET"
                : misses.isEmpty() ? "WITHIN_BUDGET" : "STOP_RULE";
        return new RocketWriterOverlapVerdict(verdict, reasons);
    }

    private static void tick(Map<String, Object> duration, String key, double budget, List<String> missing, List<String> misses) {
        if (!(duration.get(key) instanceof Number value) || !Double.isFinite(value.doubleValue()) || value.doubleValue() < 0) {
            missing.add("tick work " + key + " not measured");
        } else if (value.doubleValue() > budget) {
            misses.add("tick work " + key + " " + value.doubleValue() + " > " + budget);
        }
    }
}
