# TASK W399: a tracked real-client harness with an owner-window attach mode

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w399`, branch `codex/v1.8.0-w399` from the shared branch |
| Decision | Root decision R534 (QUEUE row W399) |
| Sources | [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) sections 3 and 5 (phase 1, step P3); [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) section 4; the old harness outside Git: `D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/` (`PLAN.md`, `REPORT.md`, `scripts/live_session.py`, `scripts/window_control.py`, `scripts/req.py`) and its latest variant `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/scripts/live_session8.py` with `start_session8.ps1`; the tracked launcher helpers in [v100_compatibility_runtime.py](../../../scripts/v100_compatibility_runtime.py) |
| Record | `docs/work/v1.8.0-root-queue/records/W399.md` |
| Write scope | A new package `scripts/campaign_client/`, one entry script `scripts/run_campaign_client_session.py`, new tests `tests/test_campaign_client_*.py`, the record. No change to any existing file. No product code. |
| Not allowed | Starting a Minecraft server or client, Gradle, GameTest or any JVM: the heavy-JVM lock belongs to candidate C4's native runs until about 2026-11-21. Reading any launcher file, account file, token or process command line. |

## Why

The final campaign's real-client rows run on the owner's machine with the owner's two signed-in accounts. The old
harness starts its own clients with offline names on Forge 47.4.22, so it cannot drive those sessions, and it lives
outside Git, so its evidence cannot name a reviewed version. Phase 1 step P3 needs a harness that can either start
disposable clients (scripted single-player rows and the four Forge and JEI cells) or attach to the two client
windows the owner started, never touching credentials.

## What to build

1. **Port the old harness into the package**, keeping its request protocol (JSON request files dropped into a
   folder, as `req.py` writes them), its actions (keys, text, mouse, waits, server console commands, native F2
   screenshots) and its outputs per round (`results.jsonl`, `summary.json`, collected screenshots and logs). Port
   the logic, do not copy the old files verbatim; split it by job (window input, server process, client launch,
   session loop, evidence). Keep each module under 500 lines.
2. **Cells.** A session names one prepared cell by its `cell.json` (written by W400). Its fields: `schema_version`
   1, `forge` (`47.4.10` or `47.4.23`), `jei` (true or false), `runtime_root`, `server_dir`, `client_game_dir`,
   `version_id`, `candidate` (`path`, `sha256`) and `jei_jar` (`path`, `sha256`, or null). Before any start, check
   that the candidate JAR in the server's and the client's `mods` folders has the declared SHA-256, and that the
   client's `mods` folder holds JEI exactly when `jei` is true; refuse otherwise.
3. **Launch mode** (the old behaviour): copy the cell's server to a run folder, start it, start one or two
   disposable clients with `v100_compatibility_runtime.client_command` (offline names, loopback), run the requests,
   stop everything it started. It closes or kills only processes it started, by their process tree.
4. **Attach mode** (new): the session file lists one or two clients, each with a process ID, an account label (a
   name the owner chooses, never a credential), the expected player name, language, GUI scale, game directory and
   expected window size. The harness:
   - checks that the process exists and is `java.exe` or `javaw.exe`, and reads its creation time, using only the
     process name and creation time. It never asks for the command line: on Windows it holds the access token;
   - finds exactly one visible top-level window of that process with the GLFW class whose title starts with
     `Minecraft`, and refuses on zero or several;
   - before every input batch, checks again that the process ID, creation time and window handle are unchanged and
     that the window is in the foreground, and refuses the batch otherwise;
   - confirms the player name from the client's `logs/latest.log` (the line that sets the user), and records the
     SHA-256 of every JAR in the declared game directory's `mods` folder;
   - never starts, closes or kills a client. Any request to close a client is refused in attach mode;
   - writes `attach.json` with what it verified and when.
5. **Server settings for online sessions**: from the session file, write `server.properties` keys `online-mode`,
   `white-list`, `enforce-whitelist`, `server-ip`, `server-port`; always `enable-rcon=false`; write `whitelist.json`
   and `ops.json` from declared player names and UUIDs (validate the UUID form); pass extra JVM system properties
   given in the session file (name pattern `[A-Za-z0-9_.-]+`, each value one line). The marker file and properties
   of W254-V2-CHECKLIST section 0.1 are phase 1 step P5's job; the harness only passes what the session file says.
6. **Bounds**: at most two clients; at most 200 actions per request; a minimum interval between inputs; a session
   time limit from the session file with a hard ceiling of 6 hours; request files over 256 KiB are refused.
7. **Evidence redaction**: every client or server log copied into the evidence passes a redactor that replaces the
   value after `--accessToken` and the values of `accessToken`, `clientId` and `xuid` fields with a fixed mask.
   Session and summary files never contain those values.
8. **Frame times** (decision O8 chooses the capture tool later; do not install or start one): a module and a
   subcommand that read a CSV in the PresentMon format (version 1 column `MsBetweenPresents`; version 2 columns
   `FrameTime` or `MsBetweenPresents`), filtered by process ID or name, and report the frame count, the capture
   length, the average FPS (frames divided by total time), the 1% low FPS (1000 divided by the mean of the slowest
   1% of frame times) with the 99th-percentile frame time, the longest frame and the first frame longer than a
   threshold given by a required argument (no default), with its time from the start. Write the definitions into
   the output.

## Tests and checks

- Unit tests for every part that runs without Minecraft: request parsing and bounds, cell checks (wrong hash, JEI
  present or absent), attach verification with fake process and window lookups (zero, one and two windows; a
  changed creation time; a changed handle; a window not in the foreground; a close request in attach mode),
  `server.properties`, whitelist and ops writing, the redactor, and the frame-time figures on small hand-made CSVs
  in both formats with values you can check by hand.
- Run the new tests with `python -B -m unittest discover -s tests -p "test_campaign_client_*.py" -v`, and
  `python -B scripts/validate_repository.py`, which must end with `0 failed`. Do not run the full Python suite.
- In the record, list what was ported from which old file, what is new, the commands and results, and what still
  needs a real client (the dry run of phase 1 step P9 qualifies the window input on C4).

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
