# Campaign real-client driver

This Windows driver produces evidence, not a Gate approval. It uses only prepared
isolated official runtimes in launch mode. In attach mode the owner starts and
signs in to each client; the driver never reads a launcher, account file or
process command line, and never starts or terminates an attached client.

## Session setup

Create a session JSON outside Git. Paths are absolute or relative to that JSON.
The cell follows schema 1 and identifies Forge 47.4.10 or 47.4.23, its isolated
runtime, server and client template, candidate path/SHA-256 and optional JEI
path/SHA-256. The candidate must occur exactly once in both mods directories.
JEI is detected only from `[[mods]]` declarations in each JAR's `mods.toml`,
including renamed JARs. Optional dependencies and filenames do not establish
that JEI is installed. Metadata is limited to 1 MiB and 256 mod declarations.

Schema 1 accepts the original fields or the extended manifest with both `mods`
and `server_forge_args`. The extended manifest's `mods.server` and `mods.client`
arrays contain 1–256 `{path, sha256}` records each, covering exactly the JARs in
that side's mods directory. Each recorded file must exist within the cell and
match its hash; candidate and JEI records must identify listed client files.
`server_forge_args` uses the same record format and must identify the selected
server's `libraries/net/minecraftforge/forge/1.20.1-<forge>/win_args.txt` with its
matching hash. The runtime input may remain outside the cell. Paths resolve
relative to `cell.json`; unknown fields, partial extensions and other schema
versions are refused.

Example disposable single-client dedicated-server session:

```json
{
  "schema_version": 1,
  "mode": "launch",
  "cell": "cell.json",
  "run_dir": "round-01",
  "java": "C:/Program Files/Java/jdk-17/bin/java.exe",
  "singleplayer": false,
  "time_limit_seconds": 1800,
  "input_interval_seconds": 0.05,
  "clients": [{"player_name": "ArceTester1", "language": "en_us", "gui_scale": 2,
               "window_size": [1280, 720]}],
  "server": {
    "online-mode": false, "white-list": false, "enforce-whitelist": false,
    "server-ip": "127.0.0.1", "server-port": 25565,
    "whitelist": [], "ops": [], "jvm_properties": {}, "max_heap": "3G"
  }
}
```

For single-player use `singleplayer: true` with exactly one launch client. Its
copied `saves` directory can contain a prepared world; select it through requests.
`server` actions then type slash commands through chat, only while no screen is
open. Configure chat permission in the fixture. The driver does not infer that
the world is ready from a window appearing. A dedicated round likewise waits for
server readiness, not a successful player join; use log waits and screenshots to
establish that join. No automatic retry hides a failed first connection.

For attach use `mode: "attach"`, `singleplayer: false`. Each client additionally
declares `pid`, `account_label` (an arbitrary owner label), `game_dir`, with the
same expected player name, language, GUI scale and window size. The declared game
directory must have the cell's candidate and JEI state. `logs/latest.log` must
postdate the process and its last `Setting user:` line must match. `options.txt`
must match language and GUI scale. Identity logs are limited to 128 MiB and
options to 64 KiB; copied log snapshots are limited to 512 MiB and 64 KiB per
line. These are disk checks, not proof of unsaved
in-memory settings or cryptographic proof that the directory belongs to that PID;
the owner supplies that binding. `attach.json` records this limitation explicitly.
There must be exactly one visible top-level `GLFW30` window titled `Minecraft...`.
The driver does not bring an attached window to the foreground. Bring the target
window forward before submitting its batch. Batches addressing two input windows
at once are refused; submit each foreground window separately. No client close
action (or batch containing one) is accepted in attach mode. Normal session stop
only stops the server it started.

For online sessions explicitly set all three authentication/whitelist booleans
to true, choose a restricted bind address and port, and provide `whitelist` and
`ops` entries as `{"name":"PlayerName","uuid":"canonical-hyphenated-uuid"}`.
UUIDs must be supplied by the owner. The harness never resolves account names.
Ops get level 4, without player-limit bypass. RCON is always disabled, its old
password is removed, and no RCON connection is made. JVM properties are explicit
string entries in `jvm_properties`. No marker files or test-switch defaults are
invented. Network firewalling remains the operator's responsibility.

The optional `server.max_heap` sets the dedicated server's `-Xmx`, defaulting to
`3G`. Use a positive whole number with an uppercase `M` (MiB) or `G` (GiB) suffix,
such as `4096M`. Values above `8G` / `8192M` are refused. The initial heap stays
`1G` unless the maximum is smaller, in which case both use that smaller value.
Heap configuration is not a change to the campaign's reference-node budgets.

## Commands

```powershell
python -B scripts/run_campaign_client_session.py check D:/campaign/session.json
python -B scripts/run_campaign_client_session.py session D:/campaign/session.json --jvm-lock D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm
python -B scripts/run_campaign_client_session.py request D:/campaign/round-01 '@D:/campaign/ops.json' --mode attach --players PlayerName
python -B scripts/run_campaign_client_session.py frames D:/campaign/presentmon.csv --pid 1234 --threshold-ms 50 --output D:/campaign/frames.json
```

`check` never probes processes or starts Java. Do not run `session` while another
campaign owns the heavy-JVM lock. It atomically refuses a busy lock, never breaks
it, requires 10 GiB free on system/run drives, and uses a fresh run directory.
Lock release happens only after cleanup. If process cleanup fails the copies and
lock must be retained for operator recovery. Run copies are disposable; source
worlds and prepared cells are not modified. Keep restart worlds as separately
prepared inputs before a round; temporary server/client copies are removed.

## File-drop protocol and bounds

Write a complete UTF-8 JSON object to `requests/<id>.tmp`, then rename to `.json`:

```json
{"id":"check-screen","ops":[{"op":"key","player":"PlayerName","key":"F3"},{"op":"screenshot","name":"debug-screen"}]}
```

The request helper accepts the old JSON list of operations or `@file`, and waits
for its result. Limits: 256 KiB per request, 1–200 actions, 10,000 files per round,
two clients, 6 hours maximum including startup, at least 50 ms between individual
input messages, text length 256, waits up to 300 seconds, sleeps up to 120 seconds.
Cleanup has bounded process-stop grace periods after the input deadline.

Actions: `key`, `keydown`, `keyup`, `combo` (`keys`), `text`, `click`, `move`,
`scroll` (`x`, `y`, `notches`), `resize`, `screenshot` (`name`), `sleep`, `server`
(`command`, optional `wait`), `client_wait` (`pattern`, optional `start_at`),
`status`, `close_client` (launch only), `stop`, and the gates `require`, `require_screen`,
`close_screen` and `expect_log` described below. Optional `player`, `hold`, `settle`,
`timeout` and `tail` apply where accepted by the parser. Unlike the untracked
driver, log wait strings are literal substrings, not regexes: convert historical
regex batches before reuse. This avoids unbounded regex evaluation inside a timed
session. Key names include A–Z, 0–9, F1–F12, arrows, CTRL, SHIFT, ENTER, ESC,
SPACE, TAB, BACK, HOME, END, DELETE and SLASH; numeric virtual keys are also allowed.
Pointer coordinates are client pixels; scroll is converted to screen coordinates.
Pair held keys with `keyup`. If an attach target loses focus, even releases are
refused; the owner must release such keys in Minecraft before continuing.

Results go to `results.jsonl`, `summary.json`, `attach.json` (attach only), and
`evidence/` with native F2 screenshots and redacted logs. Malformed batches are
recorded without executing any action. A `COMPLETE` status alone is not a pass:
inspect failed request counts and cleanup errors. All output serialization and
copied/streamed logs mask access-token, client-ID and XUID fields. Do not put
credentials in session or request files; redaction is not an invitation to
collect them. Startup failures also clean up every owned process tree. Suspended
creation and per-process Windows jobs prevent descendants escaping ownership.

## Gates

Four actions let a request check the state it depends on before it acts. They exist because a
request whose actions all succeed can still have acted on the wrong state (a closed screen, a dead
player, a missing rocket).

- `require` (`command`, `expect`, optional `attempts` 1–60, `interval` 0.05–30 s, `timeout` 0.01–30 s
  per attempt): sends the command and waits for a reply line that contains `expect` (a literal
  substring) among the lines written after the command. It repeats the command, never an input, so
  use a readback such as `data get`. One action may wait at most 300 seconds in total.
- `require_screen` (`open`: true or false, optional `timeout` up to 30 s): waits until Minecraft
  shows the pointer (a container screen, chat, a menu or the death screen) or has captured it.
- `close_screen` (optional `timeout`): sends ESC only when a screen is open, and then requires it
  to close. With no screen open it sends nothing, because ESC would open the Game Menu.
- `expect_log` (`pattern`, optional `timeout` up to 300 s): waits for a server line (the client log
  in an integrated world) written since this request began, for outcomes that follow a click.

When a gate is not met the request stops there: its remaining actions are not sent, its result
carries `PRECONDITION_FAILED: ...` (or `EXPECTATION_FAILED: ...` for `expect_log`), it counts as a
failed request, and `summary.json` counts it in `precondition_failures`. Later requests still run.

The screen state is read from the pointer clip that GLFW sets while the game holds the pointer, so
the window must be in front, and a window that covers the whole desktop on a single monitor is
refused as undecidable. `status` reports the same reading as `pointer_captured` (null when the
window is not in front).

In launch mode the driver brings its disposable client to the front before each input. When Windows
refuses that to a session started without a console window, the driver first shares the foreground
thread's input queue for the call, and as a last resort sends one synthetic Alt tap, the only
desktop-wide input it sends for that purpose. Attach mode never does either: the owner brings the
window forward.

Shift (`SHIFT`, or virtual keys 160 and 161) is the one key sent through the system input queue
instead of a window message, in both modes: GLFW polls the real state of the Shift keys and releases
one it has only seen in a message, so a posted Shift never holds and a sneak-and-use arrives as a plain
use. It is pressed only while the target window is in front, and a release is always sent, including
at cleanup. Pair every `keydown` of Shift with a `keyup` in the same request; a session killed between
the two would leave Shift pressed on the desktop until the next physical Shift press.

## Other session options

- A launch client may set `"pause_on_lost_focus": true`; the disposable `options.txt` then gets
  `pauseOnLostFocus:true`. The default stays false, because an unattended integrated world would
  pause whenever another window takes the focus. Attach clients keep their own options.
- `session --lock-label <name>` sets the words before `pid=` in the lock's `owner.txt` (letters,
  digits, dots, underscores and hyphens, at most 64). The default is `campaign-<run directory name>`,
  so another worker reading the lock can tell which round holds it.
- A fixture's request files are `{id, ops}` objects. Copy them into `requests/` by the file-drop
  protocol above (`.tmp`, then rename), which keeps their ids. The `request` command is for a bare
  list of actions and gives it a generated id.

The frame analysis subcommand reads existing PresentMon CSVs, does not install
or start a capture tool, and requires an explicit threshold and PID/name filter.
Its output includes all metric definitions, including summed matched-frame
duration, nearest-rank P99, slowest-ceiling-1% mean and first-stall start time.
Do not combine unrelated swap chains or captures when comparing FPS.
