# Performance — v0.8.0

The packaged Java Flight Recorder run created 100 logical data-satellite
missions with no players online and observed them until all 100 became ready.
The deadline scheduler completed them in four passes of 32, 32, 32, and 4.
Observed queue inspections matched those completion counts and never exceeded
the configured 64-inspection limit.

The 13 one-second server-tick samples in the workload window measured:

- mean: `6.535525615 ms`;
- P95: `22.8302728 ms`;
- maximum: `37.666768 ms`;
- GC overhead: `0.024218492%`;
- permanent satellite chunk tickets: `0`;
- chunk generation time attributed to the workload: `0 ns`.

All measured tick statistics are below the 50 ms budget. JFR sampling adds
profiler overhead, the first pre-workload warmup sample is excluded from the
workload window, and this headless measurement does not include client render
cost. The sanitized machine-readable record and bounded log are
[`evidence/performance/summary.json`](evidence/performance/summary.json) and
[`evidence/performance/scheduler-jfr.txt`](evidence/performance/scheduler-jfr.txt).
