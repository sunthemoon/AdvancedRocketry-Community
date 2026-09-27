# v1.3 performance status

**Reference workload not executed; G7 is not approved.** The
[quality budgets](../../17-V1PLUS-QUALITY-BUDGETS.md) remain unchanged.
No empty-server timing, callback micro-test or build duration is a substitute
for the defined workload.

## Bounded implementation evidence

- Rocket providers execute at assembly/disassembly boundaries, not by adding
  per-tick full-world serialization. Each callback result is checked against
  5 ms after return; a non-returning provider cannot be preempted by this check.
- Atmosphere providers compile finite block-state classifications at loading;
  world scans reuse the existing budgeted scanner.
- Environment queries read bounded indexed metadata, adding no per-position
  cache, block read or chunk request. This follows ADR-028 rather than inventing
  a cache to satisfy the version plan's generic wording.
- Component/fuel/mission values are declarative and bounded. Existing loader
  transfer rate, rocket capacities and mission scheduling limits remain.
- NBT/wire limits and registration quotas are documented in the API guide and
  contracts. Post-return limits do not cap arbitrary provider allocations.

## Observations, not acceptance

The [COMPAT record](../../work/v1.3.0-compatibility/VERIFICATION.md) retains
20 ms-target slow callbacks, a 2,060 ms / 41 tick GameTest warning, and a
2,553 ms / 51 tick native startup/restart-campaign warning. Other per-slice
reports retain their own measurements and warnings. Handoff build logs retain
the new **2,233 ms / 44 tick** GameTest warning separately. No warning is hidden
or timeout enlarged.

Near-zero incremental cost with no extensions has not been measured against
a defined baseline. No MSPT percentile, sustained RSS, allocation/GC trend,
network-byte budget or long-duration cache-growth PASS is assigned.

## Deferred execution specification

After the ADR-018 completion trigger, use the defined 4 vCPU / 8 GiB Debian 12
reference environment and workload, recording CPU/JVM/Forge/artifact/world,
players, 16 oxygen devices, 10 stations, 100 tasks, two rockets and maximum
structure activity, saves and transfers. Compare average/P95/P99 MSPT,
non-startup/save spikes, memory/GC, forced chunks and no-extension overhead.
Run the required duration and retain full samples, not selected quiet windows.
This document does not reserve or authorize a remote server session.
