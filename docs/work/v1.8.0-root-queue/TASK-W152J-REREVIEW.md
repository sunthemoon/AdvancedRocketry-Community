# TASK W152J-rereview: independent read-only review of W152J's R117 fix

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w152j` at `cf26f6df4`: the first fix `f44640ee5`, the R117 fix `e134a4cfc` and their records, on `cac55dcc7`. Read `git diff cac55dcc7..cf26f6df4`, and `git diff 737d2e95d..cf26f6df4` for what R117 changed. Review worktree `D:/GitHub/arce-v180-w152j-rereview`, a detached checkout of `cf26f6df4` |
| Task it answers | [TASK-W152J](TASK-W152J-FAILBACK-RESERVATION-TURN.md) (R114) and its R117 continuation |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152J-review/REVIEW.md` (SHA-256 `8f38f669…e2e7`, BLOCK on W152J-R1 and R2), with its probe `probe-src/.../SealedAdmissionReviewTest.java` |
| Design | ADR-065's N3-B2 proof-admission bullet (R109, R110, R112 and R114) and R-035's N3-B2 part |
| Record | the W152J sections of `docs/work/v1.8.0-root-queue/records/W152.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152J/` (the R117 run is under `r117/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152J-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W152J-R1 and R2 are closed.** Run the previous review's probe sequences against `cf26f6df4`, in both callback
   orders:
   - the refused fail-back retried before service with 2 and 63 sealed owners;
   - the 84-to-100 nudge case and its no-nudge control.
2. **W152H-R1 stays closed.** Run the original arrival and nudge sequences: one fail-back per tick through tick 10, and
   alternating nudges of two pending returns through tick 300.
3. **The new refused-demand signal.**
   - Is it RAM-only? Does it expire on a stated bound? Does it respect the 64-pending limit?
   - Can it be renewed forever without any proof starting, in either direction?
   - On refusal, is there no journal reservation and no source, record or pad change (N2)?
4. **The preserved BUSY turn.** A native-unavailable `BUSY` keeps the pending return's place through the next capture
   opportunity. Crew-waiting arrivals still cannot keep a sealed candidate waiting.
5. **Look for any sequence that starves either class** under R112's bound, using arrivals, nudges, refusals, BUSY
   results, withdrawals, expiry and both callback orders.
6. **Nothing weakened.** No quota, the 100-tick proof deadline, the 200-tick retry, the 64-pending bound, the inspection
   allowance, saved formats or the protocol changed. Earlier unit classes are unchanged, and the new tests fail on
   `737d2e95d` where the record says so. The eight native cases ran on the committed R117 fix, with no production
   source change after it.
7. **Merge.** List conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and
   with W164 (`codex/v1.8.0-w164`) and W129aM (`codex/v1.8.0-w129a`), which also touch sealed and ordinary
   admission.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- `SealedFailBackReservationTest` and the R117 test classes alone, and the previous review's probe;
- the W152, W152F, W152G, W152H and W152J classes, then the `resourceescrow`, `rocket.server` and `rocket.sealed`
  packages.

**Take the heavy-JVM lock for every Gradle run**, with the atomic pattern and the 90-second hand-off; other workers
share it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle`
and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W152J-R3, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
