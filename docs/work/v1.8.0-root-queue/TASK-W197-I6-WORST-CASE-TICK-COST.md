# TASK W197: the worst-case native tick cost of I6 proof and preparation (R165)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w197` (new; from the shared head named in your prompt, which includes W164) |
| Branch | `codex/v1.8.0-w197` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W197.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W197/` |
| Background | `docs/work/v1.8.0-root-queue/records/W164.md`, its R156 and R162 sections, and the unqualified draft `D:/GitHub/ARCE-Task-Evidence/root-queue/W164/r156-cost-unqualified-draft.patch` (SHA-256 `b464a785…691d3f`); [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) |

**Root decision R167.** W164 merged with its worst-case tick-cost measurement open (R165). This task measures it. It
changes no production code.

## What to do

1. **Qualify a worst-case fixture**, starting from W164's draft: a 64-entry queue, a 2,048-block, 32,768-volume
   snapshot with 16 columns per end, at the accepted 1 MiB serialized ceiling. Commit it as test or benchmark code
   that the production JAR does not ship.
2. **Measure natively** the server-tick cost of I6's proof and preparation steps at that worst case: P50, P95 and P99
   per step, the largest single tick, and the number of ticks a proof takes. Run under the heavy-JVM lock on a JAR
   built from your committed source, with the machine otherwise as quiet as the queue allows, and record the other
   load.
3. **Compare with docs/17** and say plainly which budgets hold and which do not. A budget that does not hold is a
   finding for Root; do not change a budget, bound or production path.

## Verification

The fixture's tests, the whole unit suite, and the native measurement with its raw samples kept as evidence.

## Not in this task

Production changes, the combined terrain matrix (N3-B4), ADRs, status files and the ledger.
