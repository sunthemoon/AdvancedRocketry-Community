# TASK W152J-review: independent read-only review of W152J

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w152j` at `737d2e95d`: the fix `f44640ee5` and its record on `cac55dcc7`. Read `git diff cac55dcc7..737d2e95d`. Review worktree `D:/GitHub/arce-v180-w152j-review`, a detached checkout of `737d2e95d` |
| Task it answers | [TASK-W152J](TASK-W152J-FAILBACK-RESERVATION-TURN.md) (R114) |
| Finding it closes | W152H-R1 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W152H-review/REVIEW.md` (SHA-256 `1685ad9a…8b70`), with its probe `probe-src/.../SealedArrivalReviewTest.java` |
| Design | ADR-065's N3-B2 proof-admission bullet (R109, R110, R112 and R114) and R-035's N3-B2 part |
| Record | the W152J section of `docs/work/v1.8.0-root-queue/records/W152.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152J/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152J-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W152H-R1 is closed.** A fail-back arriving every tick, or nudges that renew a waiting return's token, can no
   longer keep a sealed candidate from starting within the R112 bound. Run the review's probe sequences against the
   fix, in both callback orders.
2. **No starvation the other way.** A waiting fail-back or ordinary return still gets its turn after the sealed attempt
   ends, under R112's bound. Look for a sequence of arrivals, nudges, BUSY results and callbacks that starves either
   class, including a refused `holdFailBack` that never registers demand.
3. **N2 and the token protection.** A fail-back that cannot reserve takes the existing refusal and retry path with no
   source, record or pad change. Reservation still comes before mutation; the `JOURNAL_PENDING` boundary and the
   fail-back callback are unchanged; tokens still count in the capacity check. `holdReturn` treats only an existing
   `FAIL_BACK` entry as a fail-back.
4. **Nothing weakened.** No quota, the 100-tick proof deadline, the 200-tick retry, the 64-pending bound, the
   inspection allowance, saved formats or the protocol changed. The W152, W152F, W152G and W152H unit classes are
   unchanged. The new tests fail on `cac55dcc7` where the record says so.
5. **Native evidence.** The eight W152 native cases ran on the committed fix. Confirm that no production source changed
   after the commit they ran on.
6. **Merge.** List conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`). W164
   (branch `codex/v1.8.0-w164`, in progress) also edits `RocketOrdinaryRetirement` and
   `RocketTransferRecoveryService`; note any overlap with W152J's lines.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- `SealedFailBackReservationTest` alone, and the review's probe;
- the W152, W152F, W152G and W152H classes, then the `resourceescrow`, `rocket.server` and `rocket.sealed` packages.

**Take the heavy-JVM lock for every Gradle run**, with the atomic pattern and the 90-second hand-off; W164 shares it.
No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle` and `logs`
afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W152J-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
