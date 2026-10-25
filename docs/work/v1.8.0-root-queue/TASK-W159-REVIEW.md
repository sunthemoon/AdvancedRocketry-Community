# TASK W159-review: independent read-only review of W159 (W80-rejoin core)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w159` at `397a0a525`, 17 commits on `32c8ded4`. Read `git diff 32c8ded4..397a0a525` (53 files). Review worktree `D:/GitHub/arce-v180-w159-review`, a detached checkout of `397a0a525` |
| Task it answers | [TASK-W159](TASK-W159-W80-REJOIN-CORE.md), with Root decisions R105 and R106 at its top and end |
| Design | [records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3; sections 1, 4, 5, 6 and Appendix A, with the R106 correction in section 4), [records/W157.md](records/W157.md), `records/W158.md`, the W80 note in ADR-065 and the W80 part of R-035 |
| Record | `docs/work/v1.8.0-root-queue/records/W159.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W159/` (`SHA256SUMS.txt`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W159-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Protection.** From the first entity-join callback to unguarding, a rejoining passenger cannot take damage, ride,
   board, interact, gain a harmful effect (including through `forceAddEffect`) or keep one gained while guarded. The
   effects held at guard install survive. The 200-tick deadline disconnects with the message, and an ordinary
   reconnect with no rejoin entry is unaffected. Check the hooks W159 adds to `AtmosphereRuntime`,
   `PlayerLifeSupportService`, `SuitEquipmentRuntime`, `PlayerEnvironmentalService` and `SpaceSafetyReturnService`:
   they must skip only a guarded player and change nothing for anyone else.
2. **Placement.** The 64 deterministic candidates and their full safety check, rechecked before the teleport and before
   unguarding; at most two tickets per player and 16 in total; `REJOIN_CONFLICT` without a winner; boarding and flight
   refused for a player of AP until VERIFIED; the marker's schema, size and placement through `getPersistentData()`.
3. **Proof.** `saveAll` once after a placement, then at most once per 100 ticks and never while a scan is open; the
   streaming scanner's ceilings (64 MiB, 512 MiB, depth 512, 10 seconds) and the size and mtime check; P2 only on an
   exact proof of the installed file. A replacement failure that still fires `SaveToFile` must never count as proof.
   Check that `2be7e5dc` binds the proof to the complete placement tuple.
4. **Persistence.** No saved format beyond W157's schema 1; P1 and P2 use the generations it defines; no change to the
   journal root, bounds, budgets, timeouts or protocols. Every queued or held obligation survives a restart. Death,
   clone, logout before P2 and stale markers are handled as the design says.
5. **Threads.** The scanner runs on one daemon thread and touches no world state; results return to the owner thread
   through the 128-slot queue, with at most four actions per tick. Server stop and a world unload leave no thread or
   ticket behind.
6. **Scope.** No Mixin, access transformer, reflection on the player writer or offline player-data writer. No block
   writes, beds, respawn anchors or granted equipment. No command or player-facing admission (W160). Nothing weakened:
   the change to `RocketReleaseBoardingGameTests` and `ReleaseTestPlayers` must not loosen an existing assertion. New
   log lines have exact entries in `scripts/gametest_expected_log.json` and `scripts/w159_rejoin_expected_log.json`.
   New tests fail on the old code. Player text is in both language files.
7. **Merge.** List the conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`).
   W152 (sealed staging, branch `codex/v1.8.0-w152`) is also waiting to merge; note any overlap with it in
   `RocketTransferService`, `RocketRuntime` and `scripts/gametest_expected_log.json`.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- W159's new unit classes alone, then the `rocket.rejoin` package;
- `tests/test_run_w159_rejoin_cuts.py` and `tests/test_check_gametest_log.py`.

**Take the heavy-JVM lock for every Gradle run in this review**, with the atomic pattern and the 90-second hand-off;
W152F shares it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`,
`.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W159-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.