# TASK W233-fix: the laser fixture's cleanup on failure and its target chunk residency

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w233` (W233's; continue on its branch) |
| Branch | `codex/v1.8.0-w233`, now at `7b47f1295` (pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W233.md`: append "R238 review corrections"; keep your logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W233/` |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W233-review/REVIEW.md` (SHA-256 `6ea4e45c…9232d2e65`): APPROVE-WITH-CHANGES, finding W233-R1 (Medium); the hosted runtime log is in `W233-review/ci-41236115965/` |
| Hosted run | Branch run 41236115965 on `7b47f1295` failed the same two GameTests as W233's local run: `LaserPhysicalGameTests.anUnloadedTargetStopsTheDrillWithoutLoadingIt` and `StationSkyContextGameTests.theSkyContextIsServerDerivedSentOnChangeAndFollowsEveryChange`, plus two unexpected `PlayerDataStorage` WARNs |

**Root decision R238.** The review traced the chain:

1. The laser test's wait fails because its target chunk is still resident.
2. The test removes its connected player only in its success callback, so the failure leaks that player.
3. The leaked player alone explains the sky failure and both player-save WARNs.

W233 adds four GameTest batches. Those can change the order and placement of other tests even though W233 does not
touch the laser, sky or player-save code.

1. **Cleanup.** Make `LaserPhysicalGameTests`' fixture cleanup idempotent, and make it run on failure as well as
   success: the player, the target and `fixture.close()`. Show, with a test that fails first, that a failed wait no
   longer leaves the player connected.
2. **Residency.** Find out why the laser target chunk stays loaded in this order. For example: another batch's
   ticket or view distance, a K2 test's structure placement, a forced chunk or a player near it. Fix the cause in the
   fixture that holds it, or by choosing a target position the test truly owns. If a product defect appears, stop at
   a failing test and say so.

Do not:

- change an assertion, timeout, budget or log expectation;
- add an allowance for the leaked player's WARNs;
- move a test to another batch.

## Verification

- The laser and sky batches together with the K2 batches, in the hosted order, three times under the heavy-JVM
  lock.
- The changed tests alone, failing first.
- The Python log-check suites.

Root pushes; the branch run qualifies the result.

## Not in this task

W233's imports, renderers and projections (the review approved them), other failures, status files and the ledger.
