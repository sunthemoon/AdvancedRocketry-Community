# TASK W44K: close the W44J re-review findings before merge

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w44i-world-first` (at `1661d4bf` plus this task) |
| Branch | `codex/v1.8.0-w44i-world-first` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W44J-review/REVIEW.md`, SHA-256 `ebe02455697a54e47aafc874c10ddc985f2ab8b8006d2b21c9c1eb2edd908b47`. Verdict MERGE-WITH-FOLLOWUP: N1 Medium, N2 Low |
| Record | Add a "W44K" section to `docs/work/v1.8.0-root-queue/records/W44I.md`. Keep the existing text |

Root fixes both findings before merge, because N1 can leave a server unable to start. Read the
review, `records/W44I.md` (its W44J section), the frozen design `records/W44-DESIGN.md` section 3.6,
and [TASK-W44J](TASK-W44J.md). Give each finding its own commit, with its test.

## Root's decisions

1. **N1: the first station save of a PROVEN_ABSENT root goes through the staged atomic write.**
   - Write it as the journal and progression roots already do: stage, force, read back, atomic move.
   - A failed attempt therefore never leaves a torn target, and the absence proof is not consumed.
   - Ownership still begins only at a known commit (W44J F2).
   - Later ordinary saves keep their current path and the ordinary-save clock policy, unless
     reusing the staged path for them is simpler and keeps every existing assertion. Say which you
     chose.
   - **Test:** fail the first save **after the target is opened** (for example a committer or
     staging fault that leaves a partial file under the old path). Then:
     - IO recovers;
     - the next save writes the registry;
     - nothing becomes UNREADABLE;
     - a restart starts cleanly.

     Keep W44J's missing-parent test.
2. **N2.** Clear the retained UNKNOWN candidate digest only when that attempt's `beforeMove` ran and
   proved absence. Otherwise keep it.
   - **Test:** UNKNOWN on the first move, then a staging failure (NOT_COMMITTED before the committer),
     then a retry. The retry must resolve COMMITTED for the session's own identical candidate, and must
     not switch the root to UNREADABLE.
3. **The record.** Say that the six earlier Moon rows ran on `9b4849c4`, before W44J, and list which
   native rows now cover the fixed persistence code. Rerun the two most relevant Moon rows on the
   final SHA if time allows.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf` and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- The 31 remaining H rows, and the real-client rows.
- ADR-066, the risk register, status files and the ledger (Root).
