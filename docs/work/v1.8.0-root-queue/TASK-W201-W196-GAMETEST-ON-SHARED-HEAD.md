# TASK W201: W196's two-claim GameTest fails on the shared head

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w201` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w201` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W201.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W201/` |
| Logs | `D:/GitHub/ARCE-Task-Evidence/root-queue/W196-hosted/7ac2d1be/`: the job logs of runs 40869564018 (at `7ac2d1be`) and 40878728018 (at `124deb40`) |
| Background | `docs/work/v1.8.0-root-queue/records/W196.md` (W27d4, its R170 section) and `records/W164.md`, `records/W199.md` |

**Root decision R172.** W196's R170 GameTest `outsideCopyDuringPartialCaptureHoldsOnlyItsClaim` passed on W196's
branch (cut from `c0b0daab8`, before W164 and W199 merged) and has now failed in both shared-head runs after W196's
merge: "Two-claim proof fixture exceeded its wait at stage 2". Every other GameTest passed. This is a persistence area.

## What to do

1. **Find the cause** from the logs and the code: which change on the shared head since `c0b0daab8` (most likely
   W164's I6 integration or W199's preparation and ticket pacing) changes what the two-claim fixture waits for at stage
   2, and whether the product or the fixture is wrong. Reproduce it locally under the heavy-JVM lock.
2. **A test-side cause** (a fixture that assumes the old ticket or preparation timing): fix the fixture, keeping every
   assertion, timeout and bound, and say what it now waits for.
3. **A production cause** (W196's R170 cleanup and W164 or W199 disagree on a claim's turn, tickets or proofs): write a
   short design note first, fix it with a regression that fails first, and stop for Root if the fix changes W196's or
   W199's reviewed behaviour.

## Verification

- the new tests alone, failing first; the whole unit suite;
- the bay batches and the I6 and sealed batches three times under the heavy-JVM lock.

Root's hosted run on the pushed branch stands in for a complete local GameTest. An independent review follows a
production fix.

## Not in this task

Other tests, ADRs, status files and the ledger.
