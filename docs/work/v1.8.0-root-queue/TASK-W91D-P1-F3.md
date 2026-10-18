# TASK W91D: the jackhammer fixture, then P1-F3, with Root decision R25

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91C-P1-F3](TASK-W91C-P1-F3.md) and
[records/W91.md](records/W91.md) (including its W91C section) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (unchanged; at W91C's record `bc20d057` plus this task) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Inputs | R23 and R24 as TASK-W91C names them; W91C's observer and launcher in `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/implementation-w91c-20261018/` |
| Record | `records/W91.md` (extend it) |

## Root decision R25 (recorded here)

1. **W91's own tests are in its write scope.** R24's rule ("no other existing test outside W86 Appendix C may change")
   covers tests that exist on the shared branch. `RocketJournalRemovalAdmissionTest` and the N2 GameTests in
   `journal_fail_back_admission` were added on this branch by W91B and are not merged; migrate them to F3's API with an
   explicit two-slot fixture where they need a full queue, keeping every outcome assertion. No permission is needed for
   that.
2. **The jackhammer fixture.** If the runtime observation confirms W91C's hypothesis (a cell that
   `JackhammerFixture` checks lies in a chunk the fixture never loaded, because adding a batch moves later fixtures),
   fix the fixture in its own commit: it loads, as explicit fixed test setup, every chunk containing a cell it checks,
   before checking them. Every assertion stays. This is a latent fixture defect exposed by placement, not an N2 defect,
   and any new batch could expose it. If the observation shows something else, report it with evidence and do not
   change the jackhammer test.
3. **Lock use.** W94 is finishing its last full GameTest; wait for the lock (no time limit of your own shorter than
   three hours). When you hold it for several runs in a row, release it between runs if another worker's run is
   waiting; never hold it idle.

## What to do

1. The jackhammer observation and, if confirmed, R25 item 2's fix, verified by one full GameTest with N2's batch present.
2. Then TASK-W91C items 2 to 4 (P1-F3 with R24's two adaptations and R25 item 1, N2's remaining tests, the whole unit
   suite, one full GameTest with the unchanged log checker, and the native timing and cut runs).

Root sends the finished diff to an independent read-only review before merging it (R23 item 7).

## Commands and not in this task

As TASK-W91C says.
