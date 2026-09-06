# v1.0.0 flight-console cancellation fix

Date: 2026-09-06 (Asia/Shanghai). Branch: `codex/v1.0.0-stable-core`.
Source: uncommitted development worktree based on `34b2e99`; not a frozen RC.
The preceding turn made progress by completing and auditing the CI archives.
This slice implements a core interaction defect fix, not v1.1 content.

## Defect and implementation

The old screen allowed destination changes during countdown and constructed
CANCEL from that editable choice. The server correctly required the active
plan's exact body and station UUID. The screen also reinitialized the station
choice on resize, and its opening-only station payload could not track another
player's launch while the menu stayed open.

- `RocketFlightSelection` separates an editable choice from the active plan;
  cancellation always uses the latter, including stations absent from the
  opening destination list. Reinitialization preserves the user's selection.
- `RocketFlightMenu` sends an atomic plan target only on first synchronization
  or target changes. One message contains both body and station; it does not
  use separate partially updated UUID data slots or a global client cache.
- `RocketFlightPlanPacket` is S2C-only, exactly 9 or 25 bytes. Invalid sizes,
  identifiers, station bindings, truncation and trailing data are rejected.
  The receiver applies it only to the matching open container and rocket.
- The screen waits for the first plan snapshot before initialization, disables
  route editing while nonstationary, and uses the active route during countdown.
  It does not invent a cancellation target before synchronization.
- The existing C2S packet and service checks are unchanged: owner/operator,
  distance, loaded chunk, state, exact destination, rate limit and replay checks
  remain the server's responsibility. No persistence schema, ID, fuel calculation,
  world-loading behavior, asset, dependency or performance budget changes.

The flight channel advances from protocol `2` to `3` because it adds a message;
both sides still require an exact match. This is not a relaxation of compatibility
policy. Other channels are unchanged. Client/server development JARs must match.
Client handling uses a physical-client adapter on the main-thread consumer;
the side separation follows [Forge's side guidance](https://docs.minecraftforge.net/en/1.20.x/concepts/sides/).

## Actual verification

Development JAR: 1,247,571 bytes, 770 entries, SHA-256
`fb8539b600da54da3f4e5453af07e285e7dec1c17ace12f1e52153137be71e04`.
[Java/artifact summary](verification/java-artifact-summary.json) and
[source inventory](verification/source-inventory.json) identify the tested input.
Earlier `cf077ef7...` compatibility/recovery/upgrade records remain historical
development evidence for that different JAR; they are not silently rebound here.

| Command/check | Observed result |
|---|---|
| `gradlew.bat test --tests '*RocketFlightSelectionTest' --no-daemon`, pre-fix extracted logic | Exit 1: six cases, five failures, 21 seconds; raw log and XML retained |
| Same targeted command after correction | Exit 0: all six pass, 19 seconds |
| `gradlew.bat clean build --no-daemon` | Exit 0, 38 seconds; 376 Java tests in 72 suites, zero failures/errors/skips |
| `gradlew.bat test runData --no-daemon` | Exit 0, 27 seconds; test up-to-date, DataGen completes |
| `git diff --exit-code` | Exit 1 for the intended uncommitted worktree; not a clean CI/G2 approval |
| `git diff --exit-code -- src/generated/resources` | Exit 0, no generated-resource drift |
| `gradlew.bat runGameTestServer --no-daemon` | Exit 0, 1 minute 46 seconds; all 44 required GameTests pass |
| Single baseline packaged connection/restart driver | Exit 0; one Forge 47.4.10 client without JEI, dedicated first start and restart; all three processes exit 0 |

The six selection regressions exercise extracted selection logic matching the
old screen, not a pre-fix rendered client. Five fail before the correction;
launch-selection behavior already passes. Eight further packet tests cover
fixed sizes, all routes, malformed frames, container/rocket identity and the
protocol revision. All 362 pre-existing Java cases are retained and pass too.

The security GameTest opens a server menu before launch, edits a separate local
choice, obtains the menu's active cancellation target and sends it through the
existing server intent entry point. It checks FUELED restoration, unchanged fuel
and empty transfer journal, retaining ownership/distance/replay coverage. It is
not a real two-client or packet-delivery test.

The [packaged receipts](verification/packaged/summary.json) retain native client
connection/renderer logs, server status identity, first-start/save/stop/restart
and complete process logs. No project warnings, errors or fatal entries occur;
the client has 24 external warnings and no errors/fatals. The driver uses the
existing pinned pristine runtime in a new disposable loopback directory and
does not read PCL credentials. Its copied executed script is retained. This is
one connection smoke, not the four-cell compatibility matrix or GUI interaction.

## Remaining verification and manual steps

`V100-VISUAL-CANCEL-CODE` is verified in the scope above. The parent remains
`implemented-unverified` until real interaction is checked; no V1/V2 or release
Gate is approved. The remote server was not contacted. Long-load tests remain
deferred until implementation is complete, as requested by the owner.

Use the same new JAR on server and clients and a disposable test world:

1. Open a fueled rocket's console, select a nondefault station, resize the window
   and change GUI scale; inspect the selected station and button layout.
2. Launch to Moon and to each of two stations. Inspect countdown route choices
   and cancel; record the server result, state, fuel and journal contents.
3. Keep one authorized player's menu open while another authorized player starts
   a different station route. Inspect the displayed active target and cancellation.
4. Close/reopen the console around a plan update. Observe whether updates affect
   the matching menu only; repeat after cancellation with a different destination.
5. Record unauthorized and out-of-range attempts separately. A successful owner
   flow must not stand in for denial tests, authenticated V2 or performance data.

Actual GUI screenshots, those interaction results, the complete new-JAR
compatibility matrix, the retained earlier handshake-timeout investigation,
final candidate binding and human release acceptance remain outstanding.

## Reproduction environment

Java: `C:\Program Files\Java\jdk-17.0.7`; Python:
`D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe`.
PowerShell commands set `JAVA_HOME` and prepend its `bin`; Python commands set
`PYTHONUTF8=1` and canonical full-form `TEMP`/`TMP` for the process only.
No project commit, push, tag or publication is performed.
