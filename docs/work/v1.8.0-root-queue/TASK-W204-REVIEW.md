# TASK W204-review: independent read-only review of N3-C and the named-pad fix

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w204` at `9a1c77e1a`, cut from `8975e1213`; the slice is `8975e1213..9a1c77e1a` (9 commits). The production fix is `db87ec57a`, after the design note `7007f35b9`. Review worktree `D:/GitHub/arce-v180-w204-review`, a detached checkout of `9a1c77e1a` |
| Tasks it answers | [TASK-W204](TASK-W204-N3C-REFUSAL-AND-NAMED-PAD-CUTS.md) (R174) and [TASK-W204-FIX](TASK-W204-FIX.md) (R176) |
| Record | `docs/work/v1.8.0-root-queue/records/W204.md` on the branch (the cut mapping, the finding, the design note and the campaign) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W204/` (`native-01/` holds the finding, `native-02/` the campaign after the fix) |
| Hosted run | the run on `9a1c77e1a` (Root pushed the branch at 06:12); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W204-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The fix (`db87ec57a`).** `LandingPadService.claim` keeps an old claim while the old transfer is absent but its
   absence is unacknowledged, and releases it exactly once when acknowledged. Check
   `RocketTransferSavedData.transferAbsenceAcknowledged` and `RocketJournalLeases.absenceAcknowledged`: can an older
   empty generation, a failed or UNKNOWN write, a stopped journal, an overflowed change history or a reload prove a
   later removal? Can a claim now stay held forever, and what does a contender see? Does the validated-load path grant
   anything beyond pad absence?
2. **Callers.** Every caller of `claim` and `release`, and whether any other path reuses a pad on RAM absence alone.
   The `live=0` membership drop at the service's absent-record branch: correct, or explained?
3. **The seam and probe.** The lowered continuation limit is release-test-only, refused on a normal server, restored on
   failure, and the production default of 128 unchanged; `RocketN3cReleaseProbe` (in `src/main`, behind
   `EscrowTestEndpoints`) changes no product path; the FP6 arming cannot leak into another cut.
4. **The campaign.** The mapping was committed before the first native run; every row's oracle (source, T, pad, live
   membership, resources, UUID and owner, fuel and debit history, unrelated transfers, two restarts, exactly one
   acknowledged release); the retained failed start; the counts. Reproduce the results from the evidence.
5. **Nothing weakened:** formats, bounds, budgets, timeouts and assertions. The log checker's standalone exit 1 on
   excluded-batch expectations: confirm that it is only that.
6. **Risk text:** which sentences of R-035 or R-042 the finding and fix make wrong, for Root.
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W207 (`codex/v1.8.0-w207`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: `RocketTransferAbsenceTest`,
`LandingPadClaimTest`, `RocketN3cLimitTest` and the `station.pad`, `rocket.flight.persistence` and `rocket.server`
packages; the runner's Python tests. Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No
GameTest, native case, runData, full build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W204-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
