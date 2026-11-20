# v1.8.0 performance report — C6 draft

**DRAFT; every candidate performance measurement is PENDING.** Hosted test
durations and development-host microbenchmarks do not satisfy reference-node
or real-GPU budgets. Candidate: `c8fa16e16be87554f26c402546733df6555d15e6`,
JAR SHA-256 `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.

## Environment and workload to record

The [quality budgets](../../17-V1PLUS-QUALITY-BUDGETS.md) §§4–8 and
[v1.8 §13](../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md) govern; no
threshold is relaxed here. Reference target: **4 vCPU / 8 GiB / Debian 12**,
Java 17, server **Xmx ≤4 GiB**, ordinary non-root user. Actual CPU/storage,
kernel, Java build/flags/collector, swap, disk free space, CPU steal, server
view/simulation distances, Forge/JEI, configurations and pack hashes: **PENDING**.

At least two real or verifiably equivalent connected players, 16 oxygen
devices/sealed volumes, 10 station records, 100 satellites/tasks, two active
rockets, one 2,048-block assembly/disassembly, a dimension transfer, periodic
save and restart must coexist in the reference workload. Add v1.8's many-machine
idle/active layout and long production totals; an empty-server run is invalid.
O7 in the [owner packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md)
still needs the account arrangement for two connected real clients driven by the
harness. Fixture scale/placement, actual action schedule and achieved load are
PENDING, not inferred from a builder script.

## Reference server result table

| Metric | Required budget | C6 observed / raw sample hash / verdict |
|---|---|---|
| Server Java heap | Xmx ≤4 GiB | PENDING |
| Steady RSS | Not sustained above 5.5 GiB | PENDING |
| Mean MSPT | ≤25 ms with defined load | PENDING |
| P95 MSPT | ≤50 ms | PENDING |
| P99 MSPT | ≤100 ms | PENDING |
| Non-start/save spike | Not sustained above 500 ms; retain classified exceptions, not discard samples | PENDING |
| Watchdog / OOM | 0 / 0 | PENDING |
| Unexpected forced-loaded chunks | 0 | PENDING |
| Four-hour memory trend | No monotonic growth | PENDING |
| Soak duration | At least four uninterrupted hours; an interrupted attempt stays recorded and repeats in full | PENDING |
| Resource conservation | Exact before/after typed items/fluid/other applicable totals except explicit consumption | PENDING |

Collect raw per-tick timing, RSS/heap/GC and disk/CPU samples with UTC clock
alignment, warm-up and save/restart boundaries. Report mean/P95/P99/max, spike
duration, allocations/GC and memory trend with sampling method and denominator.
Keep all samples and failures; percentiles from incompatible runs are not pooled.
Use the raw-tick exporter/parser from [W407](../../work/v1.8.0-root-queue/records/W407.md)
with its pinned script commit; tool availability is not a measurement.

## Per-system and client measurements

| Campaign rows | Measurement / unchanged acceptance boundary | C6 result |
|---|---|---|
| A09/A10/A11 | Overall reference metrics; 2,048-block assembly/disassembly time/spike; 16-device atmosphere queue bounds | PENDING |
| C01/I02 | 100 idle machines, defined active layout and scaled v1.8 kinds; unload/reload; no unbounded per-tick work | PENDING |
| F01/G01/H07 | 10/100 stations; 500 missions (1,000 extended) and offline caps; inherited per-system ADR budgets | PENDING |
| H07 | ADR-054 §7 endgame flush ≤60 ms on reference hardware; if exceeded, the reviewed writer-thread design follow-up is required | PENDING |
| J05 | Bay D6/barrier, D3/registry writer; all 23 R-043 waiting rows, >3 slots and >2 claims | PENDING |
| J08/J09 | Cargo native reference cycle costs, shared bay/cargo work and capture quota, bounded concurrency/fairness | PENDING |
| J10 | W90 cold destination/readiness and first-use checkpoint cases, including prior >100 ms development observations | PENDING |
| J11 | First-event progression synchronous flush measured on reference node; R245 requires reviewed async design if over budget | PENDING |
| I05/I10/A08/H05 | Multi-hour production and joint reference soak, no duplicate credit/ticket/cache leak | PENDING |
| A12/E11/I09 | Real-GPU empty Forge/vanilla baseline, 64/512/2,048-block single/multiple rockets, complex OBJ/multiblocks/sky | PENDING |
| I09 | Mean FPS, 1% low, first stall; three F3+T reload durations/heap; cache release across reload/removal/dimension change | PENDING |
| I04/I09 | Each classic JEI category opening and search latency, recipe/usage correctness and counts per category | PENDING |

For every inherited per-system ADR budget, archive the exact threshold and
achieved workload along with the observation; this table does not replace those
contracts. Scans, queues, tasks, caches, NBT and packets retain their configured
hard maxima and per-tick caps. In particular existing rocket visual limits stay
524,288 bytes total / 32,768 per chunk and 1,048,576 BlockEntity NBT bytes;
the generic “about 1 MiB” guidance is not permission to raise them.

Client fields: OS/CPU/GPU/driver/resolution/effective GUI scale, frame capture
tool/version and raw CSV hash, JVM/client memory, scenes/rocket counts, baseline
comparison and first-stall definition are all **PENDING**. PresentMon is the
packet's O8 recommendation, not a tool already selected by the owner. Absolute
FPS is hardware-dependent; use baseline comparison, unacceptable spikes and
correctness under docs/17 §8, without inventing a new universal FPS cutoff.

## Four-cell scope and execution order

| Forge | Client JEI | I04 compatibility | I09 response samples | I10 configuration/coverage contribution |
|---|---|---|---|---|
| 47.4.10 | absent | PENDING | PENDING | PENDING |
| 47.4.10 | 15.56.0.205 | PENDING | PENDING | PENDING |
| 47.4.23 | absent | PENDING | PENDING | PENDING |
| 47.4.23 | 15.56.0.205 | PENDING | PENDING | PENDING |

[D9](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261031.md) moves
full modpack scope to v1.9 but retains all four compatibility reruns and the
joint four-hour reference soak. It does not require four hours in every cell
and does not waive a cell. Report the actual JEI configuration and coverage.
Reference/soak phase 5 follows V2 phase 4, with no overlapping storage-fault
experiment. Source: [final campaign plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md)
§§5–7. Actual commands, UTC run interval, logs/hashes, testers, findings and
independent budget review remain PENDING. Any failed budget stays failed until
a fix/retest or separately approved ADR under docs/17 §10; no implicit waiver.
