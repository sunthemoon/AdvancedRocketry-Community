# Performance — v0.9.0 Beta 1

The packaged baseline Forge server ran one combined maximum Beta scenario for
at least 7,200 real seconds, then stopped and restarted the same migrated world.
The scenario contains:

- one operational 2,048-block rocket with 128,000 capacity;
- 16 concurrently active sealed-room vents;
- 10 persisted stations;
- 100 concurrent logical satellite missions;
- four concurrent Minecraft status-protocol client simulations;
- periodic saves, vent refills, operator reports, JVM/OS samples, and ticket
  assertions throughout the full window.

Fixed acceptance budgets are a 50 ms maximum for every sampled mean tick,
zero client-probe failures, zero permanent satellite/transfer tickets, no more
than 256 MiB sustained RSS growth, and no more than 20 percentage points of
sustained old-generation growth. The evidence summary records PASS for every
budget and a same-authority restart.

The accepted run lasted `7,200.001` seconds and recorded 240 JVM/TPS samples,
1,920 successful status probes, 23 flushed saves, 121 bounded vent refills,
and 241 assertions that all 16 vents were active. Mean tick time was 1.335 ms
(P95 1.518 ms, maximum 1.821 ms) at 20.0 TPS. Normalized process CPU averaged
0.118% (P95 0.180%, maximum 0.317%). RSS ranged from 1,520,971,776 to
1,669,476,352 bytes; the early-to-late median change was 376,832 bytes. The
old-generation median changed by 0.145 percentage points and no full GC was
recorded. The same-world restart retained all 10 stations, 100 missions, the
2,048-block rocket authority, and 16 active vents.

[`evidence/performance/summary.json`](evidence/performance/summary.json) records
the exact elapsed time, tick/TPS/CPU/RSS/GC distributions, environment,
candidate identity, and restart state. [`metrics.csv`](evidence/performance/metrics.csv)
and [`client-probes.csv`](evidence/performance/client-probes.csv) retain the
bounded raw sample series. The headless workload does not measure render time,
and OS working set includes native and mapped memory in addition to Java heap.
