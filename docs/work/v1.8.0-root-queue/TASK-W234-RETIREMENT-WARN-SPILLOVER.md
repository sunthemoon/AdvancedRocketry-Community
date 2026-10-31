# TASK W234: the census-deadline WARNs that land in `ordinary_source_retirement`

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w234` (new; branch `codex/v1.8.0-w234` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W234.md` |
| Evidence | [W232's record](records/W232.md), lines 64 and 73 to 80: in W232's local whole-suite GameTest run, the strict log checker failed on five WARNs, all inside batch `ordinary_source_retirement`, which does not declare them: three `w151-pinned-partial-census-deadline`, and two `FAIL_BACK` retirement retries (`ARCE_TRANSFER_RETIREMENT_RETRY`, `reason=DEADLINE`, its log lines 9167 to 9172). Every test passed. The hosted single-job regression at `ef9e2f27` passed its log check. W232's local log is gone: it was under `D:/GitHub/arce-v180-w232/build/w232/`, and Root removed that worktree after merging W232, before this task existed. Reproduce the WARNs instead of reading them |

**Root decision R237.** These WARNs belong to another batch's census deadline and appeared inside a neighbouring
batch in one local order. This is the same kind of cross-batch leftover as R218's `w132-item-cargo-named-hold`.
Sharding (W225) changes batch orders, so it can surface in CI as well.

1. Find which test or fixture starts the pinned partial census and the retirement whose deadline fires later. Find
   why its deadline outlives its own batch: a fixture that succeeds before its work settles, a deadline counted in
   server ticks across batches, or a retry scheduled after success.
2. Fix it so that each WARN lands in its own batch in any order. The preferred fix is a fixture that settles,
   removes or acknowledges its own work before it succeeds, as W225-fix2 did for `RocketPropulsionGameTests`. If the
   WARN is legitimately emitted later, use a precise attribution rule in the checker for exactly these
   expectations, with a test that fails first and a statement of why it is not a loosening.
3. Check whether `ordinary_source_retirement` itself can start a census or retirement that another batch could
   inherit, and say what you find.

Do not:

- remove or weaken an assertion;
- raise a timeout, budget or ceiling;
- move a test to another batch to hide the interaction.

## Verification

- The changed tests alone, failing first where you can reproduce the failure (the owning batch followed by
  `ordinary_source_retirement`).
- The pair in that order, five times, under the heavy-JVM lock.
- The Python log-check suites.

Root pushes and runs hosted.

## Not in this task

Product changes beyond a found defect, other WARNs, the workflow, status files and the ledger.
