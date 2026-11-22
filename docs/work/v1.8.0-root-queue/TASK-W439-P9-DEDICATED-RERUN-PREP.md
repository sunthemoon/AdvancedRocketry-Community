# TASK W439: prepare P9's dedicated-server rerun and a sound probe (no client today)

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w439`, branch `codex/v1.8.0-w439` from the shared branch |
| Decision | Root decision R615 (QUEUE row W439), following R613 on [W430](records/W430.md) |
| Sources | [TASK-W430](TASK-W430-P9-DRY-RUN.md) and its [record](records/W430.md); the tracked harness `scripts/run_campaign_client_session.py` and `scripts/campaign_client/` with its README (read only); K16's sealed fixture `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/final/` (`USAGE.md`, `batch-index.json`, `batches/core-*-1280x720-s3.json`; read only); W430's evidence directory `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase1-prep/p9-dry-run/` (its `inputs/`, `dedicated.json`, `singleplayer.json` and `tools/`; read only) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase1-prep/p9-rerun/`: the session files, the scripts below and a short `RUNBOOK.md`. Tracked: only the record `docs/work/v1.8.0-root-queue/records/W439.md` |
| Not allowed | Starting any client, server or round (desktop window R604: Root starts them from 23:30). Any change under `src/`, to the harness, to K16's fixture or to W430's files. Changing a request file: the sealed requests are submitted byte for byte. |

## What to prepare

1. **Dedicated session.** A copy of W430's `dedicated.json` with a fresh run directory name inside `p9-rerun/`,
   reusing W430's preflight-verified `inputs/` (check them again against K16's and the cell's checksum lists and
   say so) and the same two-hour time limit.
2. **Detached submitter.** A Python script (no PowerShell: WMI-created processes are refused it on this PC) that
   waits for the session's READY state, then drops the 14 sealed core requests in K16's planned order by the
   file-drop protocol (as W430's `tools/submit.py` does: write a temporary file, then rename), waits for each
   result with a bounded per-request timeout taken from K16's usage notes or 300 seconds, logs every result with
   its time, continues after a failed request, and finally drops a stop request. It never edits a request and never
   removes a lock. Its unit tests may live next to it outside Git; they run on any OS.
3. **Round runner.** A wrapper that refuses to start outside 23:30 to 08:00 or too late to finish by 08:00 (as
   W430's `run_round.py` does), waits for the heavy-JVM lock without breaking it, starts the harness session and
   the submitter, and records both exit codes.
4. **Sound probe.** A minimal single-player launch session (title screen, status, stop; no world) and a note of the
   exact log lines that show whether sound started (`Error starting SoundSystem` or its absence, and the OpenAL
   device line). Root will run it twice tonight: once from its own interactive shell and once through the detached
   WMI launcher, to see whether the launch context is the cause.
5. **Check.** Run `python -B scripts/run_campaign_client_session.py check` on both session files (it starts no
   process) and the submitter's tests; keep the outputs.
6. **Runbook.** `RUNBOOK.md` lists, in order, the exact commands Root runs from 23:30: sound probe (interactive),
   sound probe (detached), then the dedicated round through the runner, and where each result lands.

## Rules

- Write the record (what was prepared, file hashes, check results, anything a live run may still expose) and run
  `python -B scripts/validate_repository.py`, which must end with `0 failed`. Commit the record. Do not push.
- The last line of your answer is the path of the record.
