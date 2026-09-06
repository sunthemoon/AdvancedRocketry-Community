# v1.0.0 console presentation verification

Date: 2026-09-06. Scope: the fuel-panel overlap and inactive station selection
marker observed in [the preceding native interaction run](../v1.0.0-station-duo/VERIFICATION.md).
This is development-worktree evidence, not a release candidate or human approval.
Version status remains **IN_PROGRESS**.

## Implementation and boundaries

- Extend the inner panel's exclusive bottom from logical y=79 to y=84. Keep
  fuel text at y=72 and destination controls at y=89. The screen and pure layout
  test share the constants; no overall control relocation is needed.
- Use one displayed-destination rule for body/station markers and route display:
  countdown uses the received active plan, otherwise use the editable choice.
  An unavailable countdown plan highlights no editable destination. This does
  not overwrite the editable body, station ID or index.
- No authoritative fuel, permission, transfer, packet, schema, stable-ID or
  performance-budget changes. No imported upstream assets. No v1.1+ features,
  authentication setup, remote server deployment, long soak or load testing.
- Modified product sources: `RocketFlightScreen`, `RocketFlightSelection`;
  added `RocketFlightScreenLayout`. Added one layout and three selection tests.
  Changelog, provenance and implementation/status records accompany the change.

Artifact: `advancedrocketry-community-1.20.1-1.0.0-dev.jar`, 1,255,571 bytes,
SHA-256 `46cb577653981a50638ff3d2bc32d0407dc4adaa5d7092fe73e65ae82bcd229c`.
Protocol remains 4. [Source inventory](verification/source-inventory.json) binds
738 inputs: two added sources and three changed existing inputs relative to the
preceding 736-input inventory. Earlier artifact-specific evidence is unchanged.

## Commands and automated results

Environment: Windows, Java 17.0.7, Gradle Wrapper, Forge 47.4.10; Python 3.13.15
at `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe`. TEMP/TMP use the
full Administrator profile path. Logs below preserve actual command output.

| Command | Actual result | Evidence |
| --- | --- | --- |
| `gradlew.bat test --tests '*RocketFlightSelectionTest' --tests '*RocketFlightScreenLayoutTest' --no-daemon` with extracted original semantics | Exit 1, 16 s; 10 tests, 4 failures | [Before log](verification/before.txt), `verification/before-results/` |
| `gradlew.bat test --tests '*RocketFlightSelectionTest' --tests '*RocketFlightScreenLayoutTest' --tests '*RocketFlightPlanPacketTest' --no-daemon` after correction | Exit 0, 17 s; 20 targeted cases | [After log](verification/after.txt) |
| `gradlew.bat clean build --no-daemon` | Exit 0, 24 s | [Build](verification/clean-build.txt) |
| `gradlew.bat test runData --no-daemon` | Exit 0, 26 s; archived 75 XML suites contain 391 tests, no failures/errors/skips | [Test/data](verification/test-data.txt), `verification/java-results/` |
| `gradlew.bat runGameTestServer --no-daemon` | Exit 0, 1 m 30 s; all 44 required tests passed | [Command](verification/gametest.txt), [raw native log](verification/gametest-native.log) |
| External `arce-presentation-duo-ui-session.py` and `arce-presentation-probe.py` | Both exit 0; driver `COMPLETE_WITH_OBSERVATIONS`, not an all-network pass | [Driver](verification/driver.txt), [probe](verification/probe.txt), [summary](verification/gui/summary.json) |
| External `arce-presentation-restart.py` | Exit 0; dedicated restart PASS | [Driver](verification/restart-driver.txt), [summary](verification/restart/summary.json) |
| `git diff --exit-code -- src/generated/resources` | Exit 0 | [Generated diff](verification/generated-diff.txt) |
| `git diff --check` | Exit 0 | [Whitespace check](verification/diff-check.txt) |
| `git diff --exit-code` | Exit 1: implementation and inherited uncommitted work; not reported as a clean tree | [Worktree diff](verification/worktree-diff.txt) |
| `python docs/work/v1.0.0-console-presentation/verification/audit.py` | Scoped source/test/log/PNG/checksum audit; see actual receipt | [Audit result](verification/audit-result.txt) |

The before run uses extracted original layout/display semantics in the new
regression cases; it is not a second pre-fix native GUI run. Original rendered
defects remain in the preceding report. No long historical Python suite or
four-cell compatibility matrix was repeated for this display-only correction.

## Native observations

Two real local clients used the NVIDIA GeForce RTX 3070 Laptop GPU and offline
test profiles. Owner `V100Visual1` was deopped after fixture setup; operator
`V100Visual2` launched. Server PID 13420, owner PID 25032, operator PID 18664
all exited 0 through normal cleanup. No owned Java process remained afterwards.
The 46 request files, driver/helper/probe sources, native logs, options and
operator lists are retained. There are 30 native F2 screenshots with hash and
dimension receipts: 21 interactive, 3 launch/cancel and 6 connection-navigation
frames. Not every screenshot is a passing console state.

1. [Owner Alpha selection](verification/gui/02-owner-alpha.png): scale 2,
   fuel 1000/1000, station quote 330, selected Alpha.
2. [Actual Moon countdown](verification/gui/04-owner-active-moon.png): operator
   launch request `e2412860-701c-4205-9459-2a1acecda15e` succeeds at 03:33:36.
   Owner's existing menu shows Moon, quote 372, countdown 52 ticks. The disabled
   Alpha button is **not bracketed**; Moon alone is marked.
3. Owner pointer cancellation request `39eda976-d3ce-4cd0-badc-1a87c7518826`
   succeeds at 03:33:37 without keyboard fallback.
   [Owner afterwards](verification/gui/05-owner-after-cancel.png) restores
   bracketed Alpha/330; [operator afterwards](verification/gui/06-operator-after-cancel.png)
   retains Moon/372. INFO return-to-source records fuel 1000.
4. Fuel text is clear of the lower panel border in scale-2 frames above,
   [scale 3, 1280x720](verification/gui/14-console-scale-3.png), and
   [scale 4, measured 1920x1061](verification/gui/23-console-scale-4.png).
   Native video settings confirm [scale 3](verification/gui/11-video-scale-3.png)
   and [scale 4](verification/gui/20-video-scale-4.png). Windows clamps the
   requested 1920x1080 client area to 1920x1061; do not relabel it 1080p.
   Changing settings closes and reopens the menu; this is not a claim that
   editable Alpha survives closing the menu. Live resize preservation was
   separately observed on the preceding artifact.
5. Two post-cancel reports agree on entity
   `b78255b4-1f3c-4180-b513-3018f388dc6a`, logical ID
   `31046efe-4ddd-45c9-8991-5fcf8432f636`, snapshot
   `42b80f05279e11debb63897848889c462cf5fb42cd49e0d55fe9652b8419c912`,
   FUELED, 1000/1000, zero passengers, no transfer, origin 384,101,384,
   five blocks. Dedicated restart PID 24764 reports the same fields and exits 0.
   This does not independently inventory cargo or exercise passenger transfer.

## Retained failures and limits

- **Both initial connections timed out**, owner at 03:28:54 and operator at
  03:31:42. The same still-running clients subsequently connected through native
  Direct Connection. No Java restart, timeout enlargement or authentication
  change occurred. Pre-reconnect native logs, timeout screenshots and input
  receipts remain under `verification/gui/`. Successful reconnection is not a
  fix or explanation of the initial handshake failures. These failures remain
  unresolved even though the later presentation observations are usable.
- [First operator menu frame](verification/gui/03-operator-moon.png) has an
  uninitialized route/zero quote/disabled launch. The
  [later frame without intervening input](verification/gui/03b-operator-settled.png)
  has Moon/372 and enabled launch. Exact initialization latency was not measured;
  the first frame is not counted as a settled quotation result.
- Strict existing log scanning remains enabled and passes cleanup; it is not a
  substitute for checking connection outcomes. The scoped audit additionally
  checks that both corresponding pre-login server disconnect records are retained.
  The first audit invocation failed because its assertion expected the server
  to spell the reason `Timed out`; native screens show that reason, while the
  server records `Disconnected`. The corrected assertion identifies both login
  listener records by profile and timestamp, excluding normal gameplay exits.
  [First audit failure](verification/audit-first.txt) remains retained.
- Inspected `RocketFlightNetwork` main-thread dispatch and
  `RocketFlightPlanHandler`: resolve the current player/menu at handling time,
  require a flight menu and matching container and rocket entity IDs before
  applying plan/quotes. Existing packet tests exercise matching and mismatched
  IDs. No controlled delayed-frame injection was performed; the late-frame
  verification task stays ready, not verified. No adapter abstraction was added
  solely to mock already explicit five-line routing.
- Exploratory file reads addressed nonexistent optional paths and returned
  shell exit 1; no tests or retained evidence were altered to suppress them.

## Acceptance

The two named presentation corrections have scoped automated and native
evidence. **Not all v1.0.0 Required Gates are satisfied.** Full V1/V2 coverage,
remaining acceptance work, unresolved handshake behavior and human release
approval remain separate. No tag, commit, push or Gate approval was created.
Continue with controlled late-frame verification and remaining v1.0 work;
do not start v1.1+ or long-load work.
