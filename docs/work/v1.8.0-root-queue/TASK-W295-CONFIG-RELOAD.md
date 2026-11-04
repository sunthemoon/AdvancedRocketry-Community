# TASK W295: make the endgame switches' live reload true, or correct the guide

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w295`, branch `codex/v1.8.0-w295`, created by Root from the shared head |
| Decision | Root decision R329 (QUEUE row W295) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 4 finding 4-1; `evidence/r04-solo-switch-log.txt`, `evidence/r04-solo/server-debug.log`, screenshots `e19*`; the harness's `scripts/set_switch.py` |
| Record | `docs/work/v1.8.0-root-queue/records/W295.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W295/` |

## What a real client showed

On a Windows dedicated server, changing `[endgame.laserDrill] physicalMining` from false to true in
`config/advancedrocketrycommunity-common.toml` while the server ran, once by a Python rewrite and once by .NET's
`WriteAllText`, had no effect: the laser drill's Mode button still answered `PHYSICAL_DISABLED`. The server's
`debug.log` showed `Watching TOML config file ...` at start and one `Config file ... changed, sending notifies` when the
server itself completed the file, but nothing after the external edits. The same setting placed before the next start
worked. `docs/ENDGAME-OPERATOR-GUIDE.md` lines 15 and 16 say the switch "is read each time a device acts, so a change
applies without a restart"; `config/CommonConfig.java`'s `serverSwitchValue` does read the value on use.

## What to do

1. Find why Forge 47.4's config watcher did not reload: for example, the watched path versus the edited file,
   atomic-rename writes (a known limitation of file watchers), a COMMON config loaded once into a different
   `ModConfig` instance, or a value cached by `serverSwitchValue`. Reproduce it in a test or a scripted dedicated
   server run, on this machine and, if you can, describe the Linux behaviour from Forge's source.
2. Choose the smallest change that makes the documented behaviour true for the switches this guide and the other
   operator guides describe as live (for example, the operator command the mod may already have to reload, or reading
   through the spec without a stale cache), or, if live reload cannot be made reliable without a Mixin or a new
   watcher, correct every guide that promises it to say "takes effect after a restart" (and name any reload command
   that does work). Say which you chose and why in the record.
3. Search all `docs/*GUIDE*.md` and config comments for the same promise and keep them consistent.

## Verification

A test or scripted run that shows the chosen behaviour (before and after); the config and endgame unit tests;
`./gradlew build` with all unit tests; the GameTests that touch the changed code, or the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock if production code changes; `python -B
scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Switch default values, endgame behaviour, the ledger, the status file and QUEUE.
