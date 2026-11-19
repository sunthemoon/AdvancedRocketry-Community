# TASK W394: make the warm-up capacity GameTest name the refused pad and its reason

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w394`, branch `codex/v1.8.0-w394` from the shared branch |
| Decision | Root decision R525 (QUEUE row W394) |
| Source | [W392](records/W392.md), "Commands and results" (the filtered run); W392R's review, item 5 (outside Git: `D:/GitHub/ARCE-Task-Evidence/root-queue/W392-review/REVIEW.md`) |
| Record | `docs/work/v1.8.0-root-queue/records/W394.md` |
| Write scope | `resourceescrow/EscrowWarmupFixtures.java` (the `predict` helper), `resourceescrow/EscrowGovernedWarmupGameTests.java` (the capacity assertion), the record. Test support and GameTest code only; no product class. |

## Why

In W392's filtered GameTest run (59 batches), `rocket_warmup_capacity` failed once with "The Moon does not offer
eight admissible pads" before its launch. It passed in the full run and alone. W392R compared both logs: the
batches that ran before it in the filtered run also ran before it in the full run, every rocket announced before it
was removed, and nothing the new arrival GameTest leaves is read by pad selection. The cause is unknown, because
`EscrowWarmupFixtures.predict` returns `null` and drops the selector's reason. The hosted CI splits batches into
shards in another order, so if this is order- or timing-dependent it can come back as a flake. The next occurrence
must name the pad and the reason.

## What to do

1. Keep `predict`'s current contract for its other callers (null on any refusal), and add a variant, or a result
   type, that also gives the refusal: the index of the pick that failed (0 to count - 1), the selector's
   `detail()` for that pick, the regions already picked, and the reservations it started from (count and each
   region). Do not change the selection itself, the reservations it uses or any product code.
2. Make the capacity assertion at `EscrowGovernedWarmupGameTests` (the "eight admissible pads" check) use the
   variant, and put that information in the failure message (one line, bounded, for example at most 2,048
   characters). Keep the assertion itself: eight pads are still required.
3. Run the capacity batch alone and the warm-up batches (`rocket_warmup_*`), and the unit suite once, with
   `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`, under the heavy-JVM lock for the GameTest server. A full GameTest run
   is not needed: only test support and one GameTest assertion change; the hosted regression runs everything.
4. Record what you changed, an example of the new message (you may produce one in a unit test or by a temporary
   local edit that you do not commit), and the commands and results. The last line of the record is its path.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
