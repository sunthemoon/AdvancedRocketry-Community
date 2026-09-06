# v1.0.0 selected-route quotation fix

Date: 2026-09-06 (Asia/Shanghai). Branch `codex/v1.0.0-stable-core`,
uncommitted development tree based on `34b2e99`. This is a verified defect
slice, not final-candidate acceptance or a stable release.

## Defect and implementation

The [previous native interaction](../v1.0.0-flight-console-ui/VERIFICATION.md)
showed the station route displaying required fuel 372 before launch, while
the server's station plan and actual debit were 330. Source inspection found
that the menu's single preview always targeted the default opposite body,
not the screen's locally selected destination. Station launch availability
also used merely positive fuel, and station departures reused Earth's
availability for the selectable Moon route.

- New `RocketFlightQuotes` computes the three existing route previews through
  the same server-side `RocketFlightPlanner` used by launch. Each immutable
  quote binds required fuel and launchable state/fuel sufficiency together.
- `RocketFlightMenu` includes those quotes in its atomic S2C active-plan
  snapshot. It sends only initial/changed snapshots to the matching valid open
  menu. There is no C2S preview request, per-station scan or client fuel formula.
- `RocketFlightPlanPacket` is exactly 22 bytes without a station target and
  38 bytes with one. Three full-width integer costs and three known flag bits
  are bounded and validated. Truncated/trailing frames, unknown flags, negative
  or excessive costs and available zero-cost quotes are rejected.
- `RocketFlightScreen` binds the fuel marker, required-fuel text and launch
  affordance to the displayed/selected route. Countdown route editing and
  cancellation's authoritative target remain unchanged.
- Flight protocol advances from 3 to 4, retaining exact-match negotiation.
  Both peers must use the same development build. No persistence schema,
  resource ID, destination, dependency, permission check, C2S intent frame,
  fuel formula or performance budget changes. No upstream assets/code copied.

Previews are not permission grants or landing reservations. All station UUIDs
share the existing station travel profile; the real launch still checks actor,
station existence/access, distance, loaded chunks, state, fuel and landing.

## Tests and actual commands

Development JAR: 1,253,352 bytes, 773 entries, SHA-256
`6953ecfe64ae937080f8bc99eb000791e76e24cc36d6d786976a08658f3064fc`.
Previous `fb8539b6...` console evidence remains bound to that older JAR.

| Command | Actual result |
|---|---|
| `gradlew.bat test --tests '*RocketFlightQuotesTest' --tests '*RocketFlightPlanPacketTest' --tests '*RocketFlightSelectionTest' --no-daemon` | Exit 0, 31 s; 23 targeted cases |
| `gradlew.bat clean build --no-daemon` | Exit 0, 27 s; 385 Java tests, 73 suites, zero failures/errors/skips; 10 tasks executed, 2 from cache |
| `gradlew.bat test runData --no-daemon` | Exit 0, 25 s; no generated-resource writes |
| `git diff --exit-code -- src/generated/resources` | Exit 0 |
| `git diff --exit-code` | Exit 1 for intended dirty development tree, not clean CI/G2 approval |
| `git diff --check` | Exit 0 |
| `gradlew.bat runGameTestServer --no-daemon` | Exit 0, 2 min 27 s; all 44 required tests pass |
| `python .../arce-v100-quote-smoke.py` | Exit 0; baseline Forge 47.4.10, JEI absent, one native client plus dedicated first start/restart; three process exits 0 |
| `python .../arce-console-quote-ui-session.py` | Exit 0; fresh native GUI session, client and server exit 0 after explicit owned-session stop |
| `python scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 passed, no pending items, warnings or failures; this validates governance and historical releases, not v1.0 Gate completion |

Seven new quote tests cover the reproduced 372/330 distinction, 329/330 fuel
threshold, station-to-Earth/Moon availability, every existing route against
the actual planner, countdown state, missing inputs/components and bounds.
Two added packet cases cover malformed quote values/flags and full-width
2,048,000-unit quotes; all eight existing packet cases retain their original
security intent under the new frame. Six selection cases are unchanged.
The existing security GameTest additionally compares menu previews with the
actual launch plan and verifies countdown disables launchable previews. Owner,
distance, replay and cancellation/resource checks are retained.

This slice uses the already retained rendered defect as its reproduction; it
does not claim a separate pre-fix execution of the newly introduced API tests.
Raw Gradle/native logs and XMLs are retained under [verification](verification/).

## Native GUI observations

Fresh packaged client/server, offline loopback profile `V100Visual1`, Forge
47.4.10 without JEI, native Windows renderer. The client log reports NVIDIA
GeForce RTX 3070 Laptop GPU, OpenGL 4.6.0, NVIDIA 566.36; GUI scale 2.
Fixture commands only build/refuel the five-block rocket and create two
stations. Actual console choices use owned-window input and native Minecraft
F2 screenshots. No launch, cancellation, cargo movement or restart-conservation
assertion is claimed for this specific GUI session.

| Native image | Observed result |
|---|---|
| [02-console](verification/gui/02-console.png) | Earth to Moon, required fuel 372, fuel 1000/1000, client area 1280x720 |
| [04-station-beta](verification/gui/04-station-beta.png) | Beta (2/2) selected, required fuel 330; gauge marker changes with route |
| [05-station-resize](verification/gui/05-station-resize.png) | Beta and quote 330 retained at 960x540; controls fit |
| [06-moon-return](verification/gui/06-moon-return.png) | Switching back to Moon restores quote 372 at 960x540 |

Executed driver/control scripts, immutable request checkpoints, final summary,
PNG hashes and native client/server logs are retained in `verification/gui`.
The setup's vanilla teleport warning is not a project error. Unlike the prior
cancellation attempt, this session has no cancellation WARN or failed stop
audit. This does not resolve or erase that earlier retained failure.

## Scope and remaining acceptance

`V100-VISUAL-QUOTE` is verified for its selected-route quotation scope. No
remote host, credentials, authentication setup or long stability run was used.
This single-GPU, single-offline-client interaction is not full V1/V2 approval.
Other GUI scales, maximum-size rendering, real multiplayer, remaining console
cancellation cases, final-RC compatibility, independent review and release
approval remain separate v1.0 tasks. The owner-deferred long workload is not
waived. v1.0 remains IN_PROGRESS; candidate hash/commit fields remain blank.

The [final scoped audit](audit-result.txt) checks 734 implementation-input
hashes, all retained JUnit/GameTest receipts, five native process exits and log
hashes, six PNG receipts/dimensions, report links and the bundle checksum list.
It does not infer rendered text from filenames: the observations above come
from inspecting the actual images. Audit output is excluded from the manifest
to avoid a self-referential checksum. Exact invocation records are retained in
[commands.txt](verification/commands.txt).
