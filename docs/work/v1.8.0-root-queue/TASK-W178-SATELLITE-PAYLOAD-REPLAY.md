# TASK W178: the satellite payload replay failure in W164's full GameTest

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w178` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w178` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W178.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W178/` |
| Failed run | `D:/GitHub/ARCE-Task-Evidence/root-queue/W164/r131-full-2/` (`latest.log.gz`, `debug.log.gz`), on W164's branch at `d7e800dc4` |

W164's full GameTest failed one test:
- the test is `SatellitePayloadGameTests.registeredPayloadIsManufacturedLaunchedAndClaimedExactlyOnce`, in batch
  `satellite_payload`;
- the message is "Replayed claim intent was not processed".

The shared branch's hosted runs have passed it so far.

W164's read-only notes:
- the consumer fixture claims at GameTest tick 430 and replays at 441;
- the terminal menu's rate limiter counts server ticks;
- the boolean refusal also has validity and handler causes, so the log does not show which guard refused it.

Since W129bc (ADR-074 slice 3), data actions answer REGISTRY_PENDING until a registry commit covers them. Consider
whether the replay can land during that window.

## What to do

1. **Find which guard refused the replay.** Read the logs and the code path, and say whether it is a W164 effect or
   older:
   - the menu rate limiter, in server ticks;
   - W129bc's pending data action;
   - a validity or handler check.
2. **Reproduce it.**
   - Run batch `satellite_payload` alone, five times, on the shared head.
   - Run it once with the neighbouring batches the full run had around it.
   - If it does not reproduce, add a temporary diagnostic in a local experiment that is not committed, naming the
     refusing guard, and use it to force the suspected timing.
3. **Fix the cause.**
   - If it is the fixture (for example, a replay sent within the rate limit's window), fix the fixture without
     loosening any assertion, and fail it first.
   - If it is production, stop and report with the evidence.

## Verification

- The batch three times after the fix.
- The `satellite` packages and the whole unit suite.

Heavy runs go under the heavy-JVM lock with the hand-off rule.

## Not in this task

W164's integration, production changes, ADRs, status files and the ledger.
