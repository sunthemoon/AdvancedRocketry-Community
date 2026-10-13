# TASK W21cF: W21c CI crash, review follow-ups, and re-verification on the W21bF tree

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first. Read its new rule
"No GameTest scheduling from inside a scheduled callback".

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w21c-observatory` (at `368cae1b`: W21c plus the shared branch with W21b and W21bF) |
| Branch | `codex/v1.8.0-w21c-observatory` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W21c-review/REVIEW.md`, SHA-256 `8917c9fa7a34d38b7cb52765477dc7971125cb5dc5ad59a1059574fc1e3f5126`. Verdict MERGE-WITH-FOLLOWUP |
| Failed CI | Hosted run 38353212060 on `368cae1b`. Log: `D:/GitHub/ARCE-Task-Evidence/ci/run-38353212060/job.log`, around line 12,758 |
| Record | `docs/work/v1.8.0-root-queue/records/W21CF.md` |

Read the whole review. Fix each item in its own commit, together with its tests.

## 1. The CI crash (first)

**What happened.**
- `ResearchGameTests.ready()` runs its action inside an `onEachTick` callback.
- Several actions call `helper.onEachTick` again, for example `ResearchEnvironmentGameTests`
  around line 54.
- That inserts schedule entries while vanilla's `GameTestInfo.tickInternal` is iterating its
  fastutil map. The server crashed with `NullPointerException ... "this.wrapped" is null`.

**The fix.** Restructure the research GameTests:
- register one `onEachTick` at test start;
- move between steps through a mutable step variable;
- never schedule from inside a scheduled callback.

Every existing assertion stays. Run the 13 research GameTests and confirm they pass in a full
`runGameTestServer`.

## 2. Review follow-ups

- **L6 (cost):**
  - rebuild the retained directory only when the ground section changes, using a version
    counter;
  - compute idle statuses inside the budgeted inspection, or in the menu path;
  - return no ticker for non-research classic controllers.

  Add a test that pins the per-tick cost at the job limit.
- **L1:** the menu and inspect text separate the offline-repair-only quarantines (R4) from
  ordinary refusals, and no refusal reads "Job not ready" when another reason applies.
- **L2:** either produce `SOURCE_MISSING` where the design says, or remove the dead code and
  record why. Add the design's "break" GameTest.
- **L4:** the native harness records `git HEAD`, checks that the tree is clean, and records the
  jar's SHA-256 when the jar is built, as W21bF did for W21b.
- **L5:** assert the production scheduling path end to end. Exercise the unloaded-cell pause
  through a real chunk unload, not a simulation.
- **I2:** the post-publication debit must not throw. Handle it, or record why it cannot happen.

## 3. Re-verification on this tree (review merge condition 1)

On the final source, run and record:
- the four W21c unit classes, and the whole unit suite;
- `runGameTestServer` with all 13 research GameTests, then the log checker;
- the native restart and fault run, pinned to the source and jar.

## 4. Text for Root

Write in your record the proposed text of:
- the ADR-066 implementation note;
- the R-027 scope addition;
- the physical-collision risk entry.

Correct them as L3, I1 and I4 describe. Root adds them at merge.

## Not in this task

- ADR and risk-register edits (Root).
- Status files and the ledger.
- The advancements.
