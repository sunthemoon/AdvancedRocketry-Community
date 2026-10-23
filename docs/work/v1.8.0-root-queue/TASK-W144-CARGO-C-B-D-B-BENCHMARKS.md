# TASK W144: cargo benchmarks C-B and D-B (fluid and item cargo on the integrated build)

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 6 (the C-B and D-B rows and "Benchmarks and budgets"), [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md), and the
escrow benchmark harnesses already in the repository (`scripts/run_v180_escrow_benchmark.py`,
`scripts/run_v180_governed_benchmark.py` and their records) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w144` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w144` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W144.md` (new; a short plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W144/` |

Benchmark evidence for cargo on the integrated build (all cargo leaves and the S2 families merged); it adds a benchmark
harness only, changes no production code and does not enable admission. Development-host results do not replace the
docs/17 reference node.

## What to measure (design section 6, unchanged budgets)

- ADR-065 section 4.1.1 and W11A section 6: one whole-snapshot reconstruction per tick at 16 MiB and 16,384 visits, at
  most 64 commits per tick, 16 MiB of detached memory, at most 8 reconciliations per tick.
- Fixtures of 2,048 blocks, 128 payloads and a 1 MiB aggregate-NBT boundary; the largest admitted fixture apart from the
  oversized rejection fixtures; the effective maximum Fluid and item representation once framing, depth, nodes and the
  whole snapshot codec are counted (section 2.3); refusal with no mutation.
- Capture, recompute at apply, off-thread readback hashing and visual republication after each operation, for fluid
  (C-B) and item (D-B) devices, and each device's cycle time.
- A mixed fuel and cargo queue with a maximum-size rocket among small requests, and 64 synthetic competing records.
- Native reference load separately from microbenchmarks; for D-B, admitted versus rejected item NBT fixtures and the mixed
  load.
- docs/17: Xmx at most 4 GiB, RSS at most 5.5 GiB, mean MSPT at most 25 ms, P95 at most 50 ms, P99 at most 100 ms, no
  watchdog, OOM or forced load.

**Stop rule:** if a budget misses or a measurement cannot be made, stop and report; do not lower a fixture, relax a
budget or skip a check. The design's remedy (a lower admitted cadence or subdivided preparation) is Root's decision. A
missing measurement gives a stop verdict, never a pass (as W129aF's verdict does).

## Verification

The harness's unit and Python tests; then the benchmarks on committed source under the heavy-JVM lock with the atomic
pattern and the hand-off rule in WORKER-RULES, releasing it in the `finally` of the same detached script that took it,
deleting every server copy. Report hardware, JVM flags, collector and fixture composition. Check C: and D: above 10 GiB
and that 6 GiB of memory is free before each run. Keep the evidence under 100,000,000 bytes.

## Not in this task

The joint docs/17 soak (B3-B), V1 and V2 client evidence, enabling admission, production changes, status files and the
ledger (Root).
