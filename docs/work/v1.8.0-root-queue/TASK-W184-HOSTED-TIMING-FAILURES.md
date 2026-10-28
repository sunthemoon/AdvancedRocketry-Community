# TASK W184: diagnose three intermittent hosted GameTest failures

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w184` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w184` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W184.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W184/` |
| Logs | `D:/GitHub/ARCE-Task-Evidence/root-queue/W176-hosted/ad8e360c/` and `.../W170-hosted/1af1d76ed/` (hosted job logs with the full server output) |
| Earlier diagnosis | [records/W166-DIAGNOSIS.md](records/W166-DIAGNOSIS.md): the same family, with three test-side causes |

Three hosted runs failed in ways that W166's fixes do not cover. Each passed in the other runs of the same code.

1. **`SealedLazyGameTests.loadedDiscoveryFindsCarriersBeyondTheHintBoundWithoutReplayingNativeCells`** timed out
   after 400 ticks, about 5.5 s of unpaced ticks. This was attempt 3 of run 40647807076 at `ad8e360c`
   (`job-122773813392.log`). The same batch takes about the same time in passing runs.
2. **A stray WARN.** Run 40671103652 at `1af1d76ed` (W170's branch, `job-122757783964.log`) passed all 1,464
   GameTests. The log check then failed on one `ARCE_REJOIN_RELEASE_HELD ... state=CAPTURE_FAILED` WARN, W161's
   expectation, in batch `rocket_rejoin_command_h1`. The release then retried and was verified.
3. **A blocked Moon pad.** In the rerun of the same run (`job-122788553270.log`), nine of the 13 tests in
   `moon_landing_rule` timed out at 9,000 ticks. Early in that batch a governed arrival returned with
   DESTINATION_PAD_BLOCKED.

## What to do

1. For each failure, find the cause from the logs and the code. Name the test or step that leaves the state behind,
   or the asynchronous work that the unpaced ticks outrun.
2. Say for each whether W170 (`codex/v1.8.0-w170` at `c4ab71611`, not yet merged), W176 or W179 can cause it. Failure 2
   and 3 ran on W170's branch; read its code with `git show` and do not check that branch out.
3. Fix test-side causes in their own commits, each with a regression that fails first where that is possible. Do not
   change a timeout, bound, budget or assertion, and do not add a manifest entry to hide a line that a fixed test
   would no longer write.
4. If a cause is in production code, do not fix it. Describe it in the record with a reproduction, and Root decides.

## Verification

- every affected batch three times, under the heavy-JVM lock;
- the whole unit suite if production code or a shared fixture changes.

Root's hosted run on the pushed branch stands in for a complete local GameTest.

## Not in this task

Production changes, timeouts, ADRs, status files and the ledger.
