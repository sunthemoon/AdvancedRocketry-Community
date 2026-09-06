# v1.0.0 performance acceptance

**NOT_EXECUTED for the required final-candidate reference workload.** There is
no v1.0 four-hour reference-load result, final-candidate MSPT/RSS distribution,
or complete three-size GPU frame-time report in this handoff. Historical Beta
soak and short correctness runs do not satisfy those requirements.

The unchanged [quality budgets](../../17-V1PLUS-QUALITY-BUDGETS.md) specify a
4-vCPU/8-GiB reference node, heap at most 4 GiB, sustained RSS at most 5.5 GiB,
mean MSPT at most 25 ms, P95 at most 50 ms and P99 at most 100 ms. There must be
no watchdog/OOM, unexpected forced chunks or sustained monotonic memory growth.

The reference load includes two connected players (or validated equivalent
behavior), 16 oxygen devices/sealed volumes, ten station records, 100 satellite
tasks, two active rockets, a 2048-block assembly/disassembly, dimension transfer,
and periodic saves/restarts. Empty-server numbers are not a substitute.

When scheduled, retain candidate hash, environment, workload construction,
raw tick/memory/GC samples, elapsed time, anomalies, cleanup and comparison
against every budget. Use the visual report for 64/512/2048-block rendering,
cache and frame-time measurements. No budget change or exemption is approved
by the absence of measurements.
