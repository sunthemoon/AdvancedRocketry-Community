# v1.0.0 native flight-console observations

Date: 2026-09-06 (Asia/Shanghai). Development worktree on
`codex/v1.0.0-stable-core`, base `34b2e99`; not a release candidate or Gate approval.
This is a short local interaction check, not a stability/load run. No remote
server, launcher credentials, online-authentication setup or upstream assets
were used. Production Java, resources, protocols and budgets are unchanged
from the [flight-console implementation checks](../v1.0.0-flight-console/VERIFICATION.md).

## Input and method

- JAR: `advancedrocketry-community-1.20.1-1.0.0-dev.jar`, 1,247,571 bytes,
  SHA-256 `fb8539b600da54da3f4e5453af07e285e7dec1c17ace12f1e52153137be71e04`.
- Forge 47.4.10, JEI absent, Java 17.0.7, Windows native client plus dedicated
  server. Offline loopback profile `V100Visual1`; not authenticated V2 evidence.
- Native client log reports NVIDIA GeForce RTX 3070 Laptop GPU, OpenGL 4.6.0,
  NVIDIA 566.36. No Xvfb, software renderer or reconstructed screenshots.
- Fixture commands create a five-block rocket, fuel 1000/1000, chest with
  17 diamonds and two accessible stations. Menu actions use owned-window input;
  no server command sends launch/cancel on behalf of the client.
- Test-only Python scripts are retained with each attempt. Window ownership is
  checked against the launched client PID; input uses asynchronous Win32
  messages. A posted click is not proof of game handling. F2 captures native
  Minecraft PNGs, and server receipts establish actions actually processed.
- GUI scale 2, client areas 960x540 and 1280x720. The interaction driver has a
  20-minute total operator window; no game tick, startup or performance budget
  was increased. Only processes launched by these attempts were closed.

## Attempt 4: observed results

Evidence directory: [attempt-4](verification/attempt-4/).
The server/client ran approximately 01:39-01:50 local time.

1. `08-console-center.png` shows the actual console, Earth to Moon, FUELED,
   fuel 1000/1000. Initial fixture aim was off-center; moving the view to the
   actual rocket center allowed interaction. This does not establish a hitbox
   defect and no hitbox code was changed.
2. `09-beta-selection.png` and `10-beta-resize.png` actually show **ConsoleAlpha
   (1/2)**, not Beta. This selection survives 1280x720 to 960x540 resize.
   Filenames express intended scenarios, not assertions about their contents.
3. At 01:42:51 the server receives successful client LAUNCH and CANCEL for
   Alpha `6283a6ae-ff15-4e02-8085-1d677b11970c`. The subsequent release-test report
   shows FUELED, fuel 1000, passengers 0 and `transfer=none`. The cancellation
   target matches the launched station. `11-countdown.png` was captured before
   the state acknowledgement and is **not** a countdown screenshot.
4. [probe-01.png](verification/attempt-4/probe-01.png) shows **ConsoleBeta (2/2)**
   selected at 960x540; [probe-02.png](verification/attempt-4/probe-02.png) retains
   that exact selection at 1280x720. Controls fit both captured client areas.
5. [probe-03.png](verification/attempt-4/probe-03.png) shows COUNTDOWN, 47 ticks,
   Beta as active station, fuel 1000 and required fuel 330. Route buttons are
   visually disabled. The server received a successful Beta LAUNCH at 01:45:33.
6. The probe then posted Moon and cancellation clicks, but **no Beta CANCEL
   request appears in the server log**. [probe-04.png](verification/attempt-4/probe-04.png)
   still shows Beta COUNTDOWN, 25 ticks. Flight subsequently commits and lands
   with fuel 670 at 01:45:44. This is not a second cancellation pass or proof of
   a rejected server request; input delivery/handling remains unresolved.
7. Before launch, the selected Beta route displays `ROUTE NEEDS 372`; during
   its countdown the server-backed quote becomes 330. This is a concrete stale
   pre-launch quote observation to investigate in the current version, not a
   fuel-accounting failure: the observed flight debit is exactly 330.

The first six attempt-4 images show the world rather than the intended menu;
they are retained but provide no GUI cancellation evidence. The successful
Alpha cancellation has matching server receipts; a screenshot alone is not
used to invent a processed intent. No post-flight cargo or restart-conservation
assertion was performed in this interaction session.

## Retained failures and shutdown

| Execution | Actual outcome |
|---|---|
| PTY launch | Windows CreateProcessW rejected the PTY before Python started; switched to file-based requests |
| Attempt 1, Python driver | Exit 1: refuel issued before assembled entity became active; retained logs, no assertion relaxed |
| Attempt 2, Python driver | Exit 1: exclusive JSON writer rejected a second summary write; replaced repeated writes with immutable checkpoints in subsequent attempts |
| Attempt 3, Python driver | Exit 1: 300-second operator-idle queue timeout; native processes closed; final summary records FAIL |
| Attempt 4, Python driver | Exit 1 during stop audit, despite dedicated-server exit 0: the existing strict audit flags the actual `ARCE_TRANSFER_RETURNED_TO_SOURCE` WARN with `reason=countdown_cancelled` |
| Timed native UI probe | Exit 0 in about 5.25 seconds; this means input/capture completed, not that all intended game actions succeeded |

Attempt 4 client shutdown and server save/stop are present in native logs;
PIDs 23512 and 26444 were absent after the terminal result. The stop audit's
exception prevented final `summary.json` and server-native-log archival in
the driver. Original checkpoints are not rewritten into a successful summary.
Server native logs were copied afterwards with an explicit postmortem receipt.
The terminal diagnostic is retained as a tool-result transcription, not a
fabricated native log. Attempt 1's stale IN_PROGRESS summary and attempt 2's
incomplete summary likewise do not imply live processes or passed runs.

The cancellation WARN is not ignored or removed to obtain a green smoke test.
Its level/audit contract requires a separate reviewed decision. The driver
remains failed, while the individual GUI observations above remain usable.

## Verification scope and remaining work

`V100-VISUAL-CANCEL-UI` remains **in-progress**. Actual Alpha cancellation,
Beta selection persistence across resize and active-plan countdown rendering
are observed on this JAR. Earth/Moon cancellation, reliable Beta cancellation,
another real player's launch with an already-open menu, closed-menu late
updates, other GUI scales and full three-size/V1/V2 acceptance remain open.

The unchanged JAR's prior clean build (376 unit cases), runData and all 44
GameTests remain separately linked implementation evidence, not commands rerun
in this documentation/UI-only slice. No long stability suite is repeated.
The local audit verifies retained hashes, PNG dimensions, processed flight
receipts and unchanged implementation inputs; it does not approve any Gate.
v1.0.0 remains IN_PROGRESS and final candidate fields stay blank.

Final local checks: `python verification/audit.py` exits 0, verifying 732
unchanged implementation inputs, 16 attempt-4 PNG receipts/dimensions and 109
bundle checksums. Its separate [result](audit-result.txt) explicitly retains
`ui_driver_status=FAILED_STOP_AUDIT`; the audit output itself is excluded from
the manifest to avoid a self-referential hash. `git diff --check` and
`git diff --exit-code -- src/generated/resources` both exit 0. The worktree
still contains intended uncommitted implementation/documentation changes.
