# v1.0.0 station-target console and interaction observations

Date: 2026-09-06, Asia/Shanghai. Development worktree on
`codex/v1.0.0-stable-core`, base `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`.
The preceding goal turn made concrete progress with the cancellation diagnostic
fix. This slice exercises the resulting implementation; no product Java,
resource, schema, protocol, permission, timeout or budget is changed here.

## Identity and environment

JAR SHA-256 `c860542e8971e845121b6fd5c41474407376b465acc301a7cab7975ac0ccf9d0`,
1,254,838 bytes, flight protocol 4. Clean build reproduces the preceding
[diagnostic-fix artifact](../v1.0.0-cancel-diagnostic/VERIFICATION.md).
The [inventory](verification/source-inventory.json) rechecks all 736 unchanged
source inputs. This is not a frozen candidate commit or release approval.

One fresh local packaged Forge 47.4.10 dedicated server, JEI absent, Windows
native clients on NVIDIA GeForce RTX 3070 Laptop GPU, OpenGL 4.6.0/NVIDIA 566.36,
Java 17.0.7. Both initial client connections succeed without a reconnect. This
does not diagnose or erase the earlier retained handshake failures.

- Owner `V100Visual1`, UUID `62bbb9cb-b2fa-39aa-9a6b-430b71f5493f`, PID 20200.
- Operator `V100Visual2`, UUID `6482f9f6-ff26-3046-b7ba-86db04b972f8`, PID 26812.
- Server PID 13960; the allocated loopback port is recorded in the
  [summary](verification/attempt-1/summary.json).

Profiles are offline fixture identities. No launcher credentials, online
authentication setup or remote server were used. The owner is deopped before
interaction; both archived ops files contain only the operator. Trusted console
commands construct a bounded five-block rocket/1000-fuel fixture and two owned
stations; LAUNCH and CANCEL originate from the real client interfaces.
Support blocks under both players are verified before opening menus.

## Actual two-client actions

Rocket entity `66165119-4c00-4057-bfa3-61b9dc16fb7f`, logical ID
`8b46f26f-28f6-4e3c-bdba-a7819c0f8e64`, source `384,101,384`.
Alpha station is `6ff3c1e0-1e99-4eb0-93ca-7c13620b6953`; Beta is
`82f2d83b-4a65-4ea0-9827-1ed46b6131d2`.

### Station UUID in an already-open menu

The [owner selects Alpha](verification/attempt-1/04-owner-alpha.png), while the
[operator selects Beta](verification/attempt-1/05-operator-beta.png), both with
quote 330. The owner's menu remains open without rebuilding or reopening it.
At 03:05:49 the operator's native launch is accepted; the
[owner's existing menu](verification/attempt-1/06-owner-active-station.png)
shows Beta, COUNTDOWN 52 ticks, quote 330, and disabled destination buttons.
The owner presses Tab then Enter; its CANCEL request contains Beta's exact UUID
and is accepted. [Owner](verification/attempt-1/07-owner-after-cancel.png) and
[operator](verification/attempt-1/08-operator-after-cancel.png) retain Alpha and
Beta respectively, with fuel 1000 and stationary controls restored.

### Pointer-only cancellation and body target

- At 03:06:21/22 the operator launches Beta again, and the owner cancels by
  clicking the button, without Tab/Enter fallback. The
  [active target](verification/attempt-1/09-owner-active-pointer.png),
  [owner result](verification/attempt-1/10-owner-after-pointer.png) and
  [operator result](verification/attempt-1/11-operator-after-pointer.png)
  agree with the server's Beta launch/cancel receipts.
- At 03:07:50/51 the operator instead launches Moon. The owner still has its
  Alpha menu open. Its [active display](verification/attempt-1/13-owner-active-moon.png)
  changes to Moon and quote 372; native pointer cancellation succeeds with
  destination Moon and station null. The owner's
  [editable Alpha selection](verification/attempt-1/14-owner-after-moon.png)
  and operator's [Moon selection](verification/attempt-1/15-operator-after-moon.png)
  survive. A secondary station-button marker inconsistency is recorded below.

### Closed menu and reopen during countdown

At 03:08:27 the owner [closes its menu](verification/attempt-1/16-owner-closed-menu.png)
before the operator launches Moon. The owner right-clicks the same rocket;
the [new menu](verification/attempt-1/17-owner-reopened-countdown.png) shows
Moon, COUNTDOWN 47 ticks, quote 372. Its pointer CANCEL succeeds at 03:08:28;
[afterwards](verification/attempt-1/18-owner-after-reopen-cancel.png) it remains
on Moon, the active plan used to initialize that new menu. An old menu-local
Alpha choice is not incorrectly required to survive closing the menu.
This uses ordinary packet delivery, not injected delayed or reordered frames.

The [native server log](verification/attempt-1/server-latest.log) and full log
contain eight successful actor-bound intents (four launch/cancel pairs), four
INFO return-to-source events with fuel 1000, and no destination spawn phase.
Reports at 03:07:00, 03:08:57 and 03:13:03 match entity/logical ID/snapshot:
FUELED, fuel/capacity 1000, passengers 0, transfer none, origin `384,101,384`,
five blocks. These observations do not assert cargo contents or passenger
recovery. Restart is covered separately by the preceding same-JAR diagnostic
slice, not rerun or claimed as a restart inside this two-client session.

## GUI scales, resize and retained observation mistakes

Actual native video settings show [scale 2](verification/attempt-1/22-video-scale-2.png),
[scale 3](verification/attempt-1/23-video-scale-3.png) and
[scale 4](verification/attempt-1/33-video-scale-4.png).
The flight panel and controls are visible at
[scale 3 / 1280x720](verification/attempt-1/26-console-scale-3.png) and
[scale 4 / 1920x1061](verification/attempt-1/39-console-scale-4.png), in addition
to the scale-2 interaction screenshots above. This is windowed, not fullscreen.

- At scale 3 a non-default [Beta choice](verification/attempt-1/27-beta-scale-3.png)
  survives [window enlargement](verification/attempt-1/28-beta-resized-scale-3.png),
  with quote 330 unchanged. The request was 1920x1080 but the measured native
  client area/PNG is **1920x1061**; the report uses that actual size.
- At scale 4 [Beta](verification/attempt-1/40-beta-scale-4.png) survives resizing
  to [1600x1000](verification/attempt-1/41-beta-resized-scale-4.png), still quote 330.
- Filenames are not verdicts: `24-console-scale-3.png` and
  `37-console-scale-4.png` actually show the pause menu and are not counted as
  console evidence. Escape navigation passed through Options to the world;
  an extra Escape reopened Pause. In the scale-4 intermediate captures,
  `34-options-scale-4.png` is Options, `35-pause-scale-4.png` is the world, and
  `36-world-scale-4.png` is Pause. The retained sequence demonstrates why a
  intended screenshot name is insufficient proof. Clicking Back to Game and
  reopening the rocket yields the actual console captures linked above.
- SetForegroundWindow returns false for both focus requests. Nonetheless the
  named owned windows receive the recorded actions and native F2 captures;
  actual processed server receipts, not focus-return values, establish the
  successful gameplay interactions. No explanation is inferred for earlier
  failed input or missing support blocks.

### Remaining presentation defects

These captures are not a blanket no-defect visual verdict:

1. Fuel text touches/overlaps the lower inner-panel border at all three scales.
   `RocketFlightScreen` draws the panel through local y=79 while placing the
   fuel text at y=72. The [scale-4 image](verification/attempt-1/39-console-scale-4.png)
   makes the overlap clear. This needs a small layout correction and regression.
2. During another player's Moon countdown, an owner whose editable choice was
   Alpha still sees bracket markers on the disabled Alpha station button,
   although the route/Moon button/quote and actual CANCEL correctly use Moon.
   See [13-owner-active-moon](verification/attempt-1/13-owner-active-moon.png).
   `stationLabel()` tests editable selection on this branch instead of the
   displayed active destination. The marker should follow the same active
   display semantics without losing the saved editable choice.

These are observed presentation issues, not evidence of a fuel or permission
failure. Their fixes are remaining v1.0 work; no release severity waiver is made.

## Commands, verification and cleanup

| Actual command | Result |
|---|---|
| `gradlew.bat clean build --no-daemon` | exit 0, 20s; same JAR hash |
| `gradlew.bat test runData --no-daemon` | exit 0, 31s; 387 Java cases / 74 suites, zero failures/errors/skips |
| `gradlew.bat runGameTestServer --no-daemon` | exit 0, 1m42s; all 44 required tests pass |
| `git diff --exit-code -- src/generated/resources` | exit 0 |
| `git diff --check` | exit 0 |
| `git diff --exit-code` | exit 1 for inherited and task uncommitted work; not clean-candidate approval |
| `python .../arce-station-duo-ui-session.py` | exit 0, COMPLETE_WITH_OBSERVATIONS; approximately 10m27s including setup and manual observations |
| Four archived `arce-*-probe.py` scripts | each exit 0; native receipts and screenshots establish the outcomes above |

Interpreter is Python 3.13.15, `PYTHONUTF8=1`; canonical TEMP/TMP point to the
task-owned external test area. The [executed driver](verification/attempt-1/arce-station-duo-ui-session.py),
window helper, probe scripts, 70 control requests, 41 unmodified native PNGs,
checkpoints, options and full/native logs are archived in
[attempt-1](verification/attempt-1/). Raw GameTest bytes and Java XML are also
retained in [verification](verification/).

Both native clients and the dedicated server exit 0. Existing strict scans
find no error/fatal/project-warning/client-linkage failures; no scanner rule
changes. Cleanup has no errors and no task-owned Java process remains running.
The [scoped audit](verification/audit.py) checks source/JAR identity, current
Java/GameTest results, non-OP status, actor/target-bound receipts, matching
reports, process/log hashes, all PNG receipts and [checksums](checksums.txt).
Its [result](verification/audit-result.txt) preserves the observation-only
driver status and does not approve full visual or release Gates.
The actual audit exits 0 with 736 source inputs, 387 Java cases, 44 GameTests,
three native processes, four launch/cancel pairs, 41 native PNGs and 295 retained
file checksums. Its own result is excluded from the manifest to avoid a
self-referential checksum.

## Status and next action

Station/body open-menu synchronization, pointer cancellation, ordinary
close/reopen and the scoped scale/resize observations are verified. Full
console visual acceptance remains in-progress because presentation corrections
and controlled late-frame coverage remain. All-core-interface V1/V2,
passenger/reconnect/permission scenarios, candidate-bound compatibility,
independent review and human release approval remain separate requirements.
v1.0 stays IN_PROGRESS, Required Gates are not all met. No long workload,
remote deployment, full Python-suite rerun, tag or release approval occurred.
The next implementation work is the two observed console presentation defects,
within the existing v1.0 feature freeze.
