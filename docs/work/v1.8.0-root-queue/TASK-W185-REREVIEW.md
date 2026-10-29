# TASK W185-rereview: independent read-only review of W185's R149, R150 and R154 rounds

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w185` at `f9e61b484`. The rounds since the first review are `31bf36d10..f9e61b484` (19 commits): R149 to `6d6eed860`, R150 to `3e9eacc93`, R154 to `f9e61b484`. Review worktree `D:/GitHub/arce-v180-w185-rereview`, a detached checkout of `f9e61b484` |
| Task it answers | The R149, R150 and R154 sections of [TASK-W185](TASK-W185-W27D3-COPIES-AND-ABSENCE.md) |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W185-review/REVIEW.md` (SHA-256 `eeea955c…f35c`) |
| Record | the R149, R150 and R154 sections of `docs/work/v1.8.0-root-queue/records/W185.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W185/r149/`, `.../r150/` and `.../r154/` |
| Hosted run | the run on `f9e61b484` (Root pushed the branch at 08:59); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W185-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is a persistence slice. Deployment admission stays off outside the GameTest server and the full release-test gate,
but the restart rows and D4 to D7 run on every server. Check the code, not the records' account of it.

## What to check

1. **R149's fixes** close the first review's R1, R2, R4, R5, R9, R11 and R12 as R149 asked. In particular:
   - R1: the exact source above the build limit is recognized, and the extended query box cannot admit a wrong copy;
   - R2: an in-session claim's source-end tickets are bounded (16 columns), released, and cannot outlive the claim
     except by the existing 400-tick timeout;
   - R4: only a passenger riding a recognized copy that will be removed is moved;
   - R5: a parked D7 holds no slot proofs. Judge the record's park loop under steady store traffic.
2. **R150.** `RocketDiscoveryColumns` and its tests are byte-identical to `a9375878b`. `BayCensusColumns` covers the low
   strip for the bay only, and every bay path that waits for or tickets an end uses it. An end the strip pushes past 16
   columns holds (END_UNAVAILABLE), never truncates. Strip tickets that are not released explicitly expire within the
   existing timeout and never drop a flight's own ticket.
3. **R154, the turn.**
   - At most one claim captures and holds proofs between D6 and D7, and a waiting claim saves nothing.
   - Every way a holder can stop ends its turn: D7 ends, a repair hold, the claim ends, a park, or a wait with no
     capture and no proof.
   - Can the turn be lost, held for ever, or held by a claim that no longer exists (a cleared coordinator, a server
     stop, a claim removed by another path)?
   - The record's risks: the holder keeps the turn through W98's 200-tick retry, and the barrier's 64-entry first-slot
     retry list. Are they bounded?
   - The record says the turn is server-wide, not per overlapping slot. Judge the wait this adds in production.
4. **The two complete-GameTest failures** the R154 record names
   (`aRefusedCheckpointRequestRejectsEveryBayWithNoReceipt` and `aGovernedProofOfTheSurvivorsOwnSlotIsObserved`): can
   either come from these rounds?
5. **Nothing weakened:** formats, bounds, budgets, quotas, timeouts and `RocketTransferService`. One login assertion
   was inverted on purpose, to follow R149's R4 scope; confirm the riding case asserts the routing instead. Check the
   new manifest entries.
6. **R-043.** List each sentence of R-043 that these rounds and the first review make wrong or missing, for Root's
   update before merge.
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W164 (`codex/v1.8.0-w164`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes, and the
`rocket.bay`, `rocket.server`, `resourceescrow` and `rocket.transfer` packages; the harness's Python tests. Targeted
unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or
server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W185-R21, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
