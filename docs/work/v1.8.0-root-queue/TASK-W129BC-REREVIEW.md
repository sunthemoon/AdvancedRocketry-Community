# TASK W129bc-rereview: independent read-only review of the W129bc continuation

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w129bc` at `26113dc63`. The continuation runs from merge `2a39ca4c3` (shared head `9d9433028` into `bb8e87b8d`) to `26113dc63`. Read `git diff 2a39ca4c3..26113dc63`, and `git diff 9d9433028..26113dc63` for the whole slice against the shared head. Review worktree `D:/GitHub/arce-v180-w129bc-rereview`, a detached checkout of `26113dc63` |
| Task it answers | [TASK-W129bc](TASK-W129BC-REGISTRY-WRITER-AND-BARRIERS.md) and its R120 and R122 continuation section |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129bc-review/REVIEW.md` (SHA-256 `077920e0…405d`, BLOCK on W129bc-R1 and R2), with its probe; and W129aM-R1 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W129aM-review/REVIEW.md` |
| Design | [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) and [records/W129-DESIGN.md](records/W129-DESIGN.md) revision 2, sections 2 to 7 |
| Record | the "Continuation plan" and "Continuation results" sections of `docs/work/v1.8.0-root-queue/records/W129bc.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129bc/continuation-20261027/` (index `verification-summary.json`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129bc-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W129bc-R1 is closed.** A first discovery-paying claim keeps its RAM mutation result and receipt token. The
   terminal records its DATA entry and token, while discovery still waits for its paid receipt to be committed. Rerun
   the previous review's probe, and check the terminal path end to end: the button, the fixed DATA token, a
   conflicting button, a held or failed write, and result delivery after coverage.
2. **W129bc-R2 is closed.**
   - While one terminal's cancellation of a satellite is pending, no other terminal can start, claim, cancel or unlink
     that satellite.
   - Check every terminal mutation branch in the record's admission table, and look for any branch the table misses.
   - Check that the new wrappers admit before any mutation and leave RAM, items and FE unchanged on refusal.
3. **W129aM-R1 is closed.** The transaction adapter adds the elapsed await time on every exit, including exceptional
   ones, as the registry adapter does. Check that none of these changed: production cadence, the shutdown clock, the
   release-only gate and the 200-tick backoff.
4. **The merge.** `2a39ca4c3` brought in W152J and W129aM. Check that no sealed, ordinary or journal path regressed
   in the merged tree, and that the continuation touches nothing outside its stated write scope.
5. **Nothing weakened.** None of these changed:
   - a schema, format, bound, budget, quota, timeout or deadline;
   - the log checker or its manifest.

   Earlier tests are unchanged apart from the formatting commit `59e2ca70c`: check that it changes no assertion. The
   new tests failed before their fixes, as the record says.
6. **Evidence.**
   - The final unit, runData, GameTest and native runs used committed `6ff566ebe` without a shim or overlay.
   - R3, R4 and DISCOVERY each ran a cut and two restarts.
   - The lock timeline in `driver-corrections.txt` shows no overlapping heavy runs.
7. **Merge.** List conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and
   with W164 (`codex/v1.8.0-w164`) and W128R (`codex/v1.8.0-w128r`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- `ReviewDiscoveryClaimBarrierTest`, `RocketTransactionAwaitClockTest` and the previous review's probe;
- the W119 matrices, `SatelliteRegistryEpochTest`, `SatelliteRegistryProofTest` and the W129a group;
- the satellite, mission and rocket persistence packages.

**Take the heavy-JVM lock for every Gradle run**, with the atomic pattern and the 90-second hand-off; other workers
share it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle`
and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W129bc-R3, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
