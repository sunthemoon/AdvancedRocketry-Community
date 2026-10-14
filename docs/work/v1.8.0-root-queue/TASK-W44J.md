# TASK W44J: fix the W44I review findings (first-write ownership, unreadable writer, assertions, evidence)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w44i-world-first` (at `48d8b4b1` plus this task) |
| Branch | `codex/v1.8.0-w44i-world-first` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W44I-review/REVIEW.md`, SHA-256 `1fa55c3620bf363a1b789612e34ff7f5865cd56af8c95fb7bbf287c941316e69`. Verdict BLOCK: F1 and F2 High; F3 to F6 Medium |
| Record | Add a "W44J" section to `docs/work/v1.8.0-root-queue/records/W44I.md`. Keep the existing text |

Read the whole review, [TASK-W44I](TASK-W44I-WORLD-FIRST.md) and the frozen design
`records/W44-DESIGN.md`, especially section 3.6 and lines 699-709. Give each finding its own commit,
with its tests.

## Root's decisions

1. **F1.** The journal's absence proof is retired only after a **known commit** establishes
   ownership.
   - A failed or NOT_COMMITTED first move keeps the proof. The retry re-checks absence before it
     replaces anything.
   - UNKNOWN stays fail-closed. A retry must tell its own exact committed candidate apart from a
     foreign file.
   - Add the regression: the first move fails, a file appears, and the retry must not replace it.
2. **F2.** Station first-write ownership advances only on a demonstrably successful write.
   - Neither admission nor vanilla's normal return from `SavedData.save(File)` is proof, because it
     swallows `IOException`.
   - Keep the ordinary-save clock policy.
   - Cover a failed initial ordinary save followed by an appeared file, through both the ordinary
     entry and the checked entry.
3. **F3.** After the UNREADABLE transition:
   - cancel or terminate the pending journal work;
   - keep the root non-dirty;
   - keep the rule that only a restart recovers.

   Test several production-style ticks after the transition, including removal of the appeared file,
   and assert that nothing is staged or replaced.
4. **F4.** In `ClassicMilestoneAdvancementGameTests`, assert `ownerDone == elected` and
   `memberDone == elected`. Keep the new positive and the outsider and remote negatives.
5. **F5.** After the fixes, run `runGameTestServer` on a **committed** SHA. Record the SHA, the jar
   hash and the log checker result as new evidence, under `W44I/gametest-02` or later. Do not edit
   the sealed `gametest-01`; add a correction note that keeps it as a development smoke run only.
6. **F6.** Root keeps the contract: every V row needs **two subsequent restarts**.
   - Extend the runner so each V row has an initial start plus two restarts.
   - Rerun the ten V rows, pinned to the source and the jar.
   - Correct the inventory in the record.

## Also

- Continue the remaining headless H rows if time allows: the writer-thread Moon rows first, then the
  latch seam, the gated fixture actions and the station-world fixture. List what is left.

## Commands

- The whole unit suite once.
- `runData` under the lock if generated data changes.
- `runGameTestServer` under the lock on a committed SHA, then the log checker.
- The native cuts, under the lock.
- At the end, delete your generated `build/gametest/world` and server copies with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- The real-client rows.
- ADR-066, the risk register, status files and the ledger (Root).
