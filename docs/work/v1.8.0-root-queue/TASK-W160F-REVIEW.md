# TASK W160F-review: independent read-only review of W160F and W160G

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w160f` at `2fad9b8f1`: 15 commits on `0efb2a55c` (Root's merge of W160 onto the shared head with W152). Read `git diff 0efb2a55c..2fad9b8f1`. Review worktree `D:/GitHub/arce-v180-w160f-review`, a detached checkout of `2fad9b8f1` |
| Tasks it answers | [TASK-W160F](TASK-W160F-REVIEW-FIXES-AND-NATIVE-ROWS.md) (R111) and [TASK-W160G](TASK-W160G-UNUSED-WORLD-BASELINE.md) (R113) |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W160-review/REVIEW.md` (BLOCK; W160-R1 to R4) |
| Design | [records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3, Appendix A) |
| Record | the W160F and W160G sections of `docs/work/v1.8.0-root-queue/records/W160.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W160F/` and `.../W160G/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W160F-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W160-R3.** The prompt observation reads only; `RocketRejoinSaveClock`, `RocketRejoinPlacement` and
   `RocketRejoinGuards` change no save decision or cadence. The 100-tick spacing assertion still applies to W80's own
   prompts, the failed-write, old-file and PENDING checks remain, and the two ordinary-save variants fail on the old
   oracle. The additions to `scripts/gametest_expected_log.json` are exact counts for the two new batches only.
2. **W160-R2.** The C22 oracle checks both released riders in every captured rocket and every reconstructible journal
   manifest; the mutation tests cover each rider at seed, recovery and check; both installed-file assertions remain.
3. **W160-R4.** The original `W160/SHA256SUMS.txt` is unchanged and the correction files follow the W159 process.
4. **W160-R1, the native rows.** Each Appendix A row the record claims is covered by its evidence. Check that fixture
   boundaries are not over-claimed: the synthetic history in C28 and C30, and the unpublished governed peers in C29 and
   C32. Check that the fixture changes made between pilots weaken no gate: disabled natural regeneration, the
   `isAlive` change, and the native-only `GOVERNED_LEDGER_MISSING` declaration. Counted starts are 62 of the 63 R113
   allows.
5. **W160G.** The downgrade declaration accepts exactly the two `rocket_monitoring_station` entries in the third start
   and nothing else, and its tests show that.
6. **New code under `src/main`.** `RocketRejoinNativeRows`, `RocketRejoinNativePressure`, `RocketRejoinNativeJournal`
   and the probe changes must be inert unless the existing test-hook switch is on, and add no saved format, journal
   root, sidecar schema, bound, budget, timeout or protocol change.
7. **Merge.** List conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and
   any overlap with W152I (branch `codex/v1.8.0-w152h`, in progress), which merges first.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- `RocketRejoinSaveClockTest`, `RocketReleaseNativeCutsTest` and `RocketRejoinNativeJournalTest` alone, then the
  `rocket.rejoin` package and the W158 release classes;
- `tests/test_run_w158_release_cuts.py`, `tests/test_run_w159_rejoin_cuts.py`, `tests/test_w160*.py` and
  `tests/test_check_gametest_log.py`.

**Take the heavy-JVM lock for every Gradle run**, with the atomic pattern and the 90-second hand-off; W152I shares
it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle` and
`logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W160F-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
