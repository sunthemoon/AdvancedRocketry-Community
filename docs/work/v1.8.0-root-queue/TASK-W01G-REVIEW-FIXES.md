# TASK W01G: fix the combined W01F/W05 review findings

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.
Implementer: a headless Claude session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w01-hatch-kernel` (at the combined candidate `901f38b3`: W01, the W01F fixes and W05, merged with the shared branch; hosted run 38196398172 passed) |
| Branch | `codex/v1.8.0-hatch-kernel` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W01C-review/REVIEW.md`, SHA-256 `bb1ee58e098d84e85d1cac7ec8cbe865d1f717241609ba244298b704120edbdb`. Verdict BLOCK on N1; N2 and N3 also need fixing. R2 to R4 are confirmed fixed. |
| Record | `docs/work/v1.8.0-root-queue/records/W01G.md` |

Read the whole review first. It gives exact lines, scenarios and suggested checks. The
earlier review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W01-review/REVIEW.md`, and the task
`TASK-W01F-REVIEW-FIXES.md` explain why the code looks as it does.

## Root's decisions

Fix each finding in its own commit, together with its tests.

1. **N1 (High): a partial controller root is reset.**
   - Take the fresh, empty path only when the block entity carries **none** of the classic
     controller roots.
   - Any partial or future-schema combination falls through to the existing decode, repair
     and retain path. Such a root is written back verbatim and does **not** refuse a chunk
     save, so Root's R1 goal is kept.
   - Add the GameTests the review lists:
     - remove only `arce_classic_machine` from a controller holding 7 ingots;
     - the mirror case: remove only the resources root during an active job;
     - the future-schema companion.
   - Correct the R-021 extension (`docs/11-RISK-REGISTER.md`) and the ADR-064 §11 note. The
     review gives the line numbers.
2. **N2 (Medium): the arc furnace unforms under snow, torches or cables.**
   - Add the block tag `advancedrocketrycommunity:machine_clearance`. It holds the air
     variants plus non-solid, non-block-entity decoration that legacy ignored:
     - the snow layer;
     - torches and wall torches, including soul and redstone torches;
     - redstone wire;
     - rails;
     - carpets;
     - vines;
     - and similar vanilla blocks.

     Generate it with data generation; datapacks can extend it.
   - Use it for the 20 legacy null cells of the arc furnace instead of `air`. Keep the 15
     legacy air cells as `air`.
   - This is a closed tag matcher, consistent with ADR-016. No ADR change is needed. Add one
     line to the ADR-064 §8.1 implementation note that names the tag.
   - Add a GameTest: a formed, running furnace gets a snow layer and a torch on rim cells,
     and keeps running.
3. **N3 (Low): parts two or more generations ahead of the controller.**
   - Extend the reconciliation so it also rebinds a **newer** same-owner binding (same
     Level, position and machine UUID), under the same loaded-owner and transaction checks.
     Parts own no Item or Fluid, so this transfers nothing.
   - Add the two-generations-ahead save-order test from the review.

## Tests and commands

- New tests as listed above.
- The whole unit suite once (`test`, no filter).
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.
- If the known vanilla IOWorker CME or the atmosphere boundary startup-budget failure
  appears, retry once and record it.

## Not in this task

- Other machines.
- ADR-068.
- Changes to `GuardedChunkSaves`.
- Status files and the ledger.
