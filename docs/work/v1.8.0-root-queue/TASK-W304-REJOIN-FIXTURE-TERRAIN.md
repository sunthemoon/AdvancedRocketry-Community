# TASK W304: the rejoin GameTests depend on the terrain around the world spawn

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w304`, branch `codex/v1.8.0-w304`, created by Root from the shared head |
| Decision | Root decision R347 (QUEUE row W304) |
| Background | [W301](records/W301.md), section W301F: W301's Overworld rocket fixture let water fall into the sea at the spawn and covered the one dry column the rejoin placement falls back to, which failed four `rocket_rejoin_*` tests; W301F contained the water. W303 (record `records/W303.md` on branch `codex/v1.8.0-w303` until it merges) built its own restored stone pad at y 300 for its rejoin test |
| Record | `docs/work/v1.8.0-root-queue/records/W304.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W304/` |

## The problem (W301F's finding)

The rejoin GameTests take each candidate's height from the Overworld surface. Where the surface above a pad is sea,
the placement falls back to a ring of 32 columns around the world spawn, of which only one, (4, -4), is dry in the
GameTest world. The tests also change that column without restoring it. So any test that changes the terrain near
the spawn, or a different world seed, can make the rejoin tests fail without any rejoin change.

## What to do

1. Read the `rocket_rejoin_*` GameTests (`RocketRejoin*GameTests`, `RocketRejoinNative*`) and their fixtures, and list
   which of them reach the spawn fallback and which spawn columns they change.
2. Give those fixtures their own bounded landing area that does not depend on the terrain around the spawn: for
   example a fixture-built pad, as W303 did, with every block it changes recorded and restored on success, failure or
   timeout, and no chunk loaded beyond what the test already loads. Do not change the rejoin placement rules or any
   production code; if a test can only pass through the spawn fallback, keep it there but make it build and restore
   the dry column it needs.
3. Show it: run the `rocket_rejoin_*` batches once as they are now and once after a fixture-only change that floods the
   one dry spawn column (an experiment commit that is never merged), before and after your change. They must pass in
   all four cases after it, and fail in the flooded case before it.

Keep every assertion and timeout unchanged.

## Verification

The experiment above; the `rocket_rejoin_*` batches three times; the whole unit suite if any class changes; the full
GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B
scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The rejoin placement rules and production code, the floating counter (W303), the ledger, the status file and QUEUE.
