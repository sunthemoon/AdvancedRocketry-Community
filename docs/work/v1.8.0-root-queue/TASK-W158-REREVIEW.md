# TASK W158-rereview: re-review of the W158F fixes

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), the first review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W158-review/REVIEW.md`, SHA-256 `3cc42c6d…a34b`, verdict BLOCK on W158-R1 and
W158-R2) and [TASK-W158F](TASK-W158F-RECOVERY-KILL-AND-BOARDING.md) first. You are a new session and a reviewer: do not
commit, change nothing outside your review worktree and output folder, and do not start other agents. The first
reviewer's session has grown large, so this re-review starts fresh from its written review.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w158` at `9f227785`. Read `git diff f4415ba4..9f227785` (9 commits, 8 files). Your review worktree `D:/GitHub/arce-v180-w158-review` is a detached checkout of `9f227785` |
| Record | The W158F section of `docs/work/v1.8.0-root-queue/records/W158.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W158F/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W158-review/REREVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W158-R1 closed.** The recovery cut is selected on its own and armed for restored batches at S1. The runner kills
   the first recovery externally at `B1_CAPTURE`, `S2_PENDING` and `C6`, inspects the stores, and restarts. C5 covers
   an absent T with B1 reacquired from the census. Check the record's reason that a C6 seed has no pending work to
   interrupt. Resource and seat census are asserted at each cut. The clean-restart cases and their exact log checks
   are unchanged.
2. **W158-R2 closed.** `RocketReleaseBoardingGameTests` goes through the real flight-intent service and real mounting:
   refusal under the fence, the same player in the same seat after the acknowledged S2, survival after save and
   reload, old seats revoked in the other recorded copy and in T, and a retained rider who leaves. Check that its
   reflection and the fixture's sidecar handling cannot leak state into other GameTests.
3. **Production stays unchanged without the probe property.** The changes to `RocketReleaseNativeCuts`,
   `RocketReleaseAccess` and `RocketReleaseWorld` are no-ops when no selection is set.
4. **The recorded hand-off deviation** (a lease 4.397 seconds after a preflight-only lease, no overlapping JVMs) and
   the driver fix.
5. **Nothing weakened**: no timeout, bound, assertion, checker or manifest relaxed; the merge with the shared head and
   with W152 (`codex/v1.8.0-w152`) as before.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: `RocketReleaseNativeCutsTest` and the other
changed or related unit classes alone, then `tests/test_run_w158_release_cuts.py`. No GameTest, server or heavy-JVM
lock. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

One verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings only where something remains, with an ID (W158-R3,
...); the commands you ran and their results; the SHA-256 of REREVIEW.md on the last line.
