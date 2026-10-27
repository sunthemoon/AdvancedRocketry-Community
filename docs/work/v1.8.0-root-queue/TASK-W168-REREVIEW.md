# TASK W168-rereview: independent read-only review of the W168 R129 continuation

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w168` at `ac6af2e5b`. The continuation is `00409f25c..ac6af2e5b`; read it, and `git diff 67cc6e01e..ac6af2e5b` for the whole slice. Review worktree `D:/GitHub/arce-v180-w168-rereview`, a detached checkout of `ac6af2e5b` |
| Task it answers | The R129 section of [TASK-W168](TASK-W168-GROUND-PREFLIGHT.md) |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W168-review/REVIEW.md` (SHA-256 `7df7009b…894e`, BLOCK on W168-R1 and R2), with its probe |
| Record | the "Continuation (R129)" section of `docs/work/v1.8.0-root-queue/records/W168.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W168/r129/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W168-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W168-R1 is closed.**
   - Rerun the previous review's probe: the START must now refuse exactly as the baseline does.
   - Check the note's claim that only missions were under-reserved, and only by 40 bytes, in the state quarantined
     from CLAIM_PENDING_DISCOVERY with both timestamps. Look independently for any other record kind or state whose
     largest legal encoding exceeds its reservation, including the longest names and reasons the decoder accepts.
   - `ReservationCoverageTest` must cover every legal state the decoder accepts, not only the states the code creates.
2. **The capacity effect.** The record says:
   - about 1% fewer missions fit, only when a reservation exceeds 2,048 bytes;
   - a root filled under the old reservations still loads, and then refuses only new missions.

   Check both. Confirm that nothing refuses to load and no admitted record is refused, and say whether any other
   operation on such a root changes its answer.
3. **The fast path, again.** With the corrected reservations, can any START, account creation or ordinary change
   succeed where the whole-root check would refuse? Also check the older fast path for ordinary changes, which the
   previous review asked about.
4. **The pre-existing exception.** The record says that releasing a non-discovery mission quarantined from
   CLAIM_PENDING_DISCOVERY throws instead of refusing. Find where, what calls it, and whether a player, command or tick
   can reach it.
5. **Nothing weakened:** formats, bounds, section budgets, quotas, timeouts and the protocol. Earlier tests change
   only as the record states.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- `ReservationCoverageTest`, `GroundPreflightTest`, `GroundSurveyScaleTest`, `GroundSurveyBoundsTest` and the previous
  review's probe;
- the `satellite` packages.

Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, benchmark, runData, full build
or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W168-R3, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
