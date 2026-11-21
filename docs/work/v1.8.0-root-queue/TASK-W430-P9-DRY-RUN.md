# TASK W430: package K16 part 2, the phase 1 dry run (P9) with disposable clients on C6

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w430`, branch `codex/v1.8.0-w430` from the shared branch (it has W428's harness fixes) |
| Decision | Root decision R594 (QUEUE row W430) |
| Sources | [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) phase 1 step P9 (one scripted round of phase 2 on the candidate, a rehearsal and not evidence); [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) section 6 (what blocks P9); the tracked harness [scripts/campaign_client/](../../../scripts/campaign_client/README.md) (launch mode, disposable offline clients, owned Windows jobs); W427's record on branch `codex/v1.8.0-w427` (`docs/work/v1.8.0-root-queue/records/W427.md`) and its sealed fixture `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/final/` (world, controls, batches, the single-player round); the cells under `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells/` with C6's JAR |
| Outputs | Evidence under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase1-prep/p9-dry-run/`; the record `docs/work/v1.8.0-root-queue/records/W430.md`; harness fixes only if the dry run exposes a harness defect (in `scripts/campaign_client/` with tests) |
| Lock | The dry run starts a server and clients: take the heavy-JVM lock with the WORKER-RULES loop (C6's campaign yields it for 120 seconds after each family) and hold it for one round at a time, then release it. Hold it for at most 2 hours 30 minutes at a time, because C6's campaign runner stops waiting after 3 hours: release it between the single-player round and the dedicated round, and stop a round cleanly if it would run past that. |
| Desktop | Disposable client windows open on this PC's desktop and take keyboard focus while the harness drives them. Run between 23:30 and 08:00 local time only (R604); if the round cannot finish by 08:00, stop it cleanly, release the lock and record where it stopped. Never attach to or close a window the harness did not start. |
| Not allowed | Any product change. Any owner account, launcher profile or credential (launch mode only, offline loopback). |

## What to do

1. Preflight with the harness's `check` command: the `47.4.10-nojei` cell, a copy of W427's core route world, the
   session file (launch mode, one disposable client, the C2 windowed viewport 1280 by 720 at GUI scale 3, English),
   free space, the lock.
2. Run the single-player round W427 generated (integrated server, including the pause branch, C22) and then one
   dedicated-server round with the core-route batch, through the harness, collecting `results.jsonl`, `summary.json`,
   F2 screenshots and logs.
3. Record per request: pass, fail or not reached, with the screenshot or log line; classify each failure as harness,
   fixture or product (product findings are recorded, not fixed); and the timing of the whole round.
4. State plainly that this is a rehearsal and not campaign evidence (plan P9).
5. Run any changed tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch (record and any harness fix) in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
