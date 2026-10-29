# TASK W198: N3-B4, the combined terrain matrix on the integrated I6 path

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w198` (new; from the shared head named in your prompt, which includes W164) |
| Branch | `codex/v1.8.0-w198` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W198.md` (new; the mapping plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W198/` |
| Inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W164/r156-terrain-inventory.json` (SHA-256 `6fa6ecff…f066e`): 89 existing B2 rows, at least 356 starts |
| Design | [records/W148-DESIGN.md](records/W148-DESIGN.md) section 6 and the N3-B4 row of section 7; `docs/work/v1.8.0-root-queue/records/W164.md` (N3-B3, merged at `45a423a2c`) |

W164 merged N3-B3 with 20 B-PRE cases and 14 B-PLAIN and B-M rows. The combined terrain matrix, W148 section 6, was kept
separate as N3-B4.

## What to do

1. **The mapping plan, committed before any native run.** Map each of the 89 inventory rows onto the integrated I6
   path: which cut, which oracle (I6's durable D1, exact T, complete resources, no old S, U or terrain duplicate, two
   unchanged restarts), and which rows are now equivalent or impossible, each with its reason. No row is dropped or
   folded into another silently. Count the starts.
2. **Run the matrix in tranches of at most 60 counted starts**, two restarts per row, under the heavy-JVM lock, on a JAR
   built from your committed source. Stop and report if a row fails twice. Keep each tranche's ledger, as W164 did.
3. **Evidence volume.** Keep the slice under 100 MB; Root authorizes moving sealed, superseded runs unchanged to
   `D:/GitHub/ARCE-Task-Evidence/archive/W198/` with a SHA-256 manifest and a pointer file, as R156 allowed W164.
4. A harness or oracle fix is a test-side change with Python tests that fail first. A production cause is not fixed
   here: describe it with its reproduction and stop.

## Verification

The harness's Python tests, the whole unit suite if a shared fixture changes, and the tranche ledgers.

## Not in this task

Production changes, the worst-case tick cost (W197), ADRs, status files and the ledger. An independent review of the
mapping and the evidence follows.
