# V180 C17a-04: the full linker and the rocket monitoring station, delivery evidence

Date: 2026-10-24. Written by Root for the two C17a ledger rows that W153 delivers. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), [records/W153.md](../v1.8.0-root-queue/records/W153.md) and the merge commit.
Every SHA below is an ancestor of `codex/v1.8.0-classic-content` at the commit that adds this file.

This file is development evidence only. **It approves no Required Gate, candidate, version or release.** Only the owner
marks a Gate as passed (AGENTS.md sections 5 and 13).

## Units

| Ledger unit | Modern ID | What W153 adds |
|---|---|---|
| `block:monitoringStation` | `advancedrocketrycommunity:rocket_monitoring_station` | ADR-065 section 4.3: status, height relative to the captured origin, vertical velocity, banks and required costs, comparator output 0 to 15, an explicit launch and one launch per rising redstone edge through the shared flight path, ordinary and governed |
| `libvulpes:java/itemLinker` | `advancedrocketrycommunity:linker` | ADR-065 section 4.2: the existing schema-1 selection now also binds monitoring stations and managed landing pads, checked server-side against loaded objects; W11b3, W109 and W120 delivered the loader roles |

The monitor uses `classic_controls` protocol 2 with kind 4 (Root decisions R92 and R102; the R92 note after ADR-065
section 11.1).

## Merge, tests and reviews

| Item | Value |
|---|---|
| Merge | `8dee8680`, a merge by SHA of `b40f5919` (W153 code `d85639bc`, W153F to `669c9b57`, W153G to `c25058c6`, W153H fix `e8fef790`) |
| Local verification at `b40f5919` | clean build with 4,804 unit tests; runData twice with a clean tree (W153G); all 1,340 GameTests with the unchanged log checker (W153H) |
| Hosted CI at `8dee8680` | `v1.8 development regression` run 40092320937, success; `Repository governance` run 40092320887, success |
| Review | W153-review BLOCK (`59e893c1…`: W153-R1 to R6); fixed in W153F, W153G and W153H; the re-review (`7ef3540d…`) closes R1 to R6 |
| Open | W153-R7, a journal-lease limitation that predates W153, fixed separately by W155 (Root decision R103) |

## Not covered here

Real-client V1 and V2 evidence (the final campaign), a dedicated-server restart of an armed monitor, cross-dimension
rebinding (by design the monitor does not rebind) and the satellite bay (`loader/1`).
