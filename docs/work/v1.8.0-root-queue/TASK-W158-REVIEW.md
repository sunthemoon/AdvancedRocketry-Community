# TASK W158-review: independent read-only review of W158 (W80-release, the release coordination)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w158` at `f4415ba4` (base: shared `1bd216e9`, merged as `0a4b9da4`; 39 files). Review worktree `D:/GitHub/arce-v180-w158-review`, a detached checkout of `f4415ba4`; read `git diff 1bd216e9..f4415ba4` |
| Task it answers | [TASK-W158](TASK-W158-W80-RELEASE.md), with Root decision R104 at its end |
| Design | [records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3), [records/W157.md](records/W157.md), the ADR-065 W80 note and the W80 part of R-035 |
| Record | `docs/work/v1.8.0-root-queue/records/W158.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W158/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W158-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Conservation and seats.** At every cut from C0 to C11, with two restarts and a kill during the first recovery:
   no released seat survives in any recorded copy once the rocket accepts a later boarding, the rocket and its
   resources exist exactly once, and the "gone" outcome is taken only for slots the census names.
2. **R104.** The census holds at most three distinct slots, survives J1's removal of T, and lets a restart at C5
   reacquire B1. The batch stays within 2,048 bytes and the 1 MiB envelope (the record measures 1,651). J1 removes T
   only when the merged `crewReady` predicate holds for every remaining passenger; an online but unplaced rider keeps
   T.
3. **J1's compare-and-replace.** It changes only passenger fields, keeps ordinary `put`'s checksum rule, the outbox and
   governed records, and notifies L's and T's histories (W155). It never revives a lease from before its own mutation.
4. **B0, B1 and S2.** B0 validates the serialized manifest; B1 uses `beginOrdinary` with its sub-quota, capture support
   and shared-slot exclusion, invents no endpoint, and keeps and revalidates each slot proof through S2. Tickets stay
   within their caps and expiry, and retries are spaced 200 ticks apart.
5. **Scope.** Legacy records only, with governed records refused. No command, placement or login protection; the
   internal entry point is gated, and production admission stays disabled. The rocket is classified before its first
   entity join. No journal root, bound, budget, timeout or protocol change; nothing weakened; new tests fail on the
   old code; `scripts/w158_release_expected_log.json` lists exact lines, and the GameTest manifest is unchanged or
   exact.
6. **Merge** with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and with W152
   (`codex/v1.8.0-w152`, in progress): shared files and conflicts.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: W158's new and changed unit classes alone,
then the `rocket.rejoin`, `rocket.flight.persistence` and `rocket.server` packages; `tests/test_run_w158_release_cuts.py`.
No GameTest, server or heavy-JVM lock. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle` and `logs`
afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W158-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
