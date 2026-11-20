# TASK W425: start each campaign cell's server once on C6, and check the raw tick subcommand live

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w425`, branch `codex/v1.8.0-w425` from the shared branch |
| Decision | Root decision R585 (QUEUE row W425) |
| Sources | [W400](records/W400.md) (the cells and its "later start check" list), [W399](records/W399.md) and `scripts/campaign_client/` (server settings and process ownership), [W403](records/W403.md) and `scripts/campaign_observe/` (samplers), [W407](records/W407.md) and `scripts/campaign_observe/raw_ticks.py` (the `perf ticks raw` subcommand, gated by `-Dadvancedrocketrycommunity.releaseTestHooks=true`), QUEUE row "C6 run" (R581) for C6's identity |
| Inputs | The four cells under `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells/` with C6's JAR (SHA-256 `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`) |
| Outputs | Evidence under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase1-prep/cell-checks/`; the record `docs/work/v1.8.0-root-queue/records/W425.md`; if you need a small driver script, `scripts/campaign_prep/cell_check.py` with a unit test |
| Lock | Candidate C6's native campaign holds the heavy-JVM lock and yields it for 120 seconds after each family. Each server start is heavy: take the lock with the WORKER-RULES loop (poll every 60 seconds, wait at most three hours), hold it for one cell's checks only, release it, and take it again for the next cell. Never break a lock you do not own. |
| Not allowed | Starting any client (clients come in the P9 dry run). Changing a cell in place: run every check on a copy of the cell's server folder in your evidence folder, and delete the copy's world afterwards (keep logs). |

## What to check, per cell (four cells)

1. Before starting: the `cell.json` fields, both mods folders' hashes against `cell.json`, C6's hash, JEI only in
   the JEI cells' client folder, the Forge argument file's hash.
2. Start the copied server (offline, loopback, RCON off) with `-Dadvancedrocketrycommunity.releaseTestHooks=true`
   added only to this check copy; wait for the ready line; record the time to ready and every ERROR or FATAL line, and
   compare WARN lines with the known expected ones.
3. Through the server console, poll `arce satellite release-test perf ticks raw` every 2 seconds for 3 minutes, merge
   the polls with `raw_ticks.py`, and report the tick count, gaps, mean, p95, p99, maximum and any run over 500 ms;
   check that consecutive polls overlap by tick number and that no tick is missing.
4. `save-all flush`, `stop`, start again, confirm the world loads with the same level identity and no new ERROR,
   then stop.
5. Record per cell: pass or fail per check, with the log excerpts and hashes, in the evidence folder and the record.

## Checks

- Run any new script's unit test and `python -B scripts/validate_repository.py`, which must end with `0 failed`.
- A failure of a cell's server that is a product defect is a finding: stop that cell, record the evidence.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch (record and any script) in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
