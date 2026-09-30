# Independent adapter compatibility fixture

This standalone ForgeGradle project builds the existing
[`src/adapterTest`](../src/adapterTest/) fixture into a separate, reobfuscated
mod JAR. It consumes the published ARCE **API classifier**, not the host project,
main JAR or compiled output. Its platform is Java 17, Minecraft 1.20.1 and
Forge 47.4.10. This fixture requires API 1.7 for satellite payload missions, in addition to environment queries (1.6), item fuels (1.5), rocket components (1.4),
suit equipment (1.3), atmosphere boundaries (1.2) and rocket registration (1.1).

The environment fixture listens for the actual server-ready API event, queries
Earth/Moon/unresolved and unloaded locations, and verifies expiration at stopping.
`-Darce_adapter_test.environmentSmoke=true` enables the permission-level-2
`arce_env_probe <label> <dimension> <position>` observation command. It logs the
configured snapshot and loaded-chunk state before/after the query without changing
the world. `-Darce_adapter_test.stationSmoke=true` enables the permission-level-2
`arce_station_probe join|run|leave|look|energy` commands for native station
checks.
- `join` connects a mock player with the given UUID over an embedded channel (as
  vanilla GameTest does), loads only that player's own chunk and moves it into
  Space.
- `run` executes, from that player's own command source, only:
  - `arce station expand` or `arce station expand confirm <id>`;
  - `arce station invite <id> probe<0-3>` or `remove <id> probe<0-3>` (another
    probe player), and `remove <id> uuid <member>`;
  - `arce station accept <id>` or `decline <id>`;
  - `arce station environment` or `list`;
  - `arce station admin inspect <id>`, so native checks can show that a
    non-operator never reaches an admin command;
  - `arce station warp <body>`, `confirm <id>`, `cancel` or `status`.
- `look` turns the player to aim at a block, as a real player aims.
- `energy` pushes at most 1,000,000 Forge Energy into the block entity at a Space
  position through its public capability, as a producer mod would. It is
  simulated first and then sent for real.
- `leave` disconnects the player.

At most four probe players exist, and all leave at server stop. At most 256 replies
are kept per player. Replies, including
action-bar messages, and the loaded-chunk count around the host command are
logged. It uses no host internals and never writes host data itself; connecting
players does write their normal player data.
Do not use the compatibility fixture in a normal player installation.

## Build

First publish the host artifacts to a local Maven repository from the repository
root:

```powershell
.\gradlew.bat publishMavenJavaPublicationToLocalProjectRepositoryRepository --offline --no-daemon --no-build-cache
```

Then build this independent project, still from the repository root:

```powershell
.\gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache
```

Both commands require an installed Java 17 JDK and previously cached Gradle,
ForgeGradle 6.0.54, Forge and platform dependencies. They do not install missing
dependencies in offline mode. The shared wrapper launches this project's own
settings/build; it does not include or build the host project.

To consume a publication from another checkout, pass an absolute local Maven
directory:

```powershell
.\gradlew.bat -p compat-test-mod build "-ParceRepository=D:/GitHub/AdvancedRocketry-Community/build/repo" --offline --no-daemon --no-build-cache
```

The default dependency is
`io.github.sunthemoon.advancedrocketrycommunity:advancedrocketry-community:1.20.1-1.3.0-dev:api`.
`-ParceVersion=<published-version>` selects another publication while retaining
the API classifier and platform boundary. The repository property is a local
directory, not a remote repository URL.

## Inspect the boundary

`build` runs two finite checks, also available individually:

- `verifyApiClasspath` records requested/resolved coordinates, raw publication
  and remapped API hashes, the complete compiler classpath, and fixture source
  hashes. It requires the 27 exported API 1.7 classes and rejects host internals,
  project output directories or additional host artifacts.
- `verifyConsumerBoundary` compiles an intentional internal import against that
  same classpath, requires the missing internal-package diagnostic, and checks
  the final reobfuscated fixture JAR for bundled host/API/platform classes. It
  reads the published normal host JAR to confirm the internal class exists,
  without ever adding that JAR to the compiler classpath.

Reports are written to `compat-test-mod/build/reports/consumer/`. The resulting
mod is `compat-test-mod/build/libs/arce-adapter-compat-test-1.0.0.jar`.

## Choose a finite check

The [API compatibility inventory](../docs/API-COMPATIBILITY.md) describes the
supported surface and its limits. From the repository root, `./gradlew runGameTestServer`
loads the development source-set fixture; the separate consumer build above proves
that the same source compiles without host internals. Packaged runners instead
install the separately reobfuscated JAR and require a fresh libraries-only server,
new evidence directory, explicit Java/JAR arguments and EULA acceptance.

| Concern | Fixture / packaged runner |
|---|---|
| Registration and cargo | `AdapterRegistrationGameTests`; [recovery](../scripts/run_v130_adapter_recovery_smoke.py) |
| Callback failures and returned-time budget | `AdapterFaultGameTests` through the production scanner/transactions |
| Actual Earth-Moon-Earth cargo | [Flight runner](../scripts/run_v130_adapter_flight_smoke.py) |
| Block-state atmosphere boundaries | [Atmosphere runner](../scripts/run_v130_atmosphere_boundary_smoke.py) |
| Equipment and owned oxygen | [Suit runner](../scripts/run_v130_suit_equipment_smoke.py) |
| Declarative rocket components | [Component runner](../scripts/run_v130_component_smoke.py) |
| Item fuels, remainders and loader migration | [Fuel runner](../scripts/run_v130_fuel_smoke.py) |
| Configured environment and handle lifetime | [Environment runner](../scripts/run_v130_environment_smoke.py) |
| Payload missions and research | [Satellite runner](../scripts/run_v130_satellite_payload_smoke.py) |

These are separate bounded scenarios, not a command to run a full load campaign.
Each runner's actual evidence states its scope and artifact hashes. A source-set
GameTest pass is not a claim that the same fault ran in a packaged player server.

The adapter fixture deliberately attempts and catches two invalid registrations
on its actual MOD bus: a duplicate owned adapter ID with a different valid type,
and a new owned ID claiming an already registered type. It refuses startup if
either succeeds. The original normal registration and inventory path remain in
use. This is two registrations from one fixture mod, not two unrelated mod authors.

Fault GameTests use instance-local source probes and a synchronous restore scope
cleared in `finally`. They inject exceptions, oversized payloads, false/mismatched
restore results, and finite 20 ms delays in each callback phase. Restoration faults
occur after the new target's inventory is written; tests check cleanup, unchanged
rocket authority, exact cargo, no drops and a healthy retry. Fault controls contain
no saved world state, do not change the payload version, and are inactive during
ordinary fixture operations. The slow-return checks do not test callback preemption.

## Runtime limits

The component fixture registers diamond, emerald, oak-plank, gold and quartz
blocks as an engine, tank, seat, guidance and structural contribution. It also
tags diamond blocks as both legacy engines and seats to exercise explicit
definition precedence. These are disposable fixtures, not gameplay balance.
`-Darce_adapter_test.skipRocketComponents=true` omits the definitions while keeping
the blocks installed. `-Darce_adapter_test.componentVariant=updated` changes only
the declared engine values and tank capacity on the next process start.

The [component runner](../scripts/run_v130_component_smoke.py) accepts the same
fresh libraries-only server and explicit artifact arguments as the recovery
runner below. It assembles five blocks, restarts with definitions omitted,
restarts with changed definitions, then disassembles/reassembles and restarts
again. Native saved snapshots must keep captured values until reassembly.
It uses zero fuel and opt-in operator diagnostics; it is not player flight,
custom-fuel, missing-block, forced-crash or load evidence.

The equipment fixture registers vanilla leather armor as an external suit, with
its own oxygen payload; it does not add art or make existing third-party capability
tanks compatible. Its GameTests exercise real host player-tick events, canister use
and native ItemStack serialization. `-Darce_adapter_test.skipSuitEquipment=true`
skips registration while retaining items and saved data.

For disposable packaged verification only, `-Darce_adapter_test.suitSmoke=true`
enables the console-only operator command `arce_fixture_suit` with four finite
phases. The [equipment runner](../scripts/run_v130_suit_equipment_smoke.py) creates
one vanilla Moon chest and launches separate setup/restart/skipped/restored
processes. Each phase uses a FakePlayer and twenty manually dispatched life ticks,
then saves the chest's actual armor/canisters. This proves neither real-client
network/HUD behavior nor real-time player persistence, power-loss or load stability.

Use the fixture JAR only in disposable development/test installations with the
matching normal host mod and Forge. Never install the API classifier as a mod,
and do not ship this fixture in a gameplay modpack. It has no ordinary gameplay
UI or client assets. The build and boundary checks above do not launch Minecraft.

Successful compilation/reobfuscation is not startup, gameplay, restart,
uninstall/reinstall or cross-dimension evidence. Those require separately
recorded runtime tests with the exact fixture and host artifact hashes.

## Missing-provider control

The `arce_adapter_test:state_boundary` block has an `open` state but keeps a full
collision cube. Its API rule alone makes the open state permeable. For disposable
atmosphere checks, `-Darce_adapter_test.skipAtmosphereBoundary=true` leaves the
block installed but skips its rule; `-Darce_adapter_test.failAtmosphereBoundary=true`
instead throws during compilation to check startup rejection. Both are startup-only
controls; do not use them for the normal GameTest run.

The finite atmosphere runner accepts the same fresh libraries-only server and
explicit JAR arguments described below:

```powershell
python -B scripts/run_v130_atmosphere_boundary_smoke.py C:/Temp/arce-atmosphere-server `
  --host-jar build/libs/advancedrocketry-community-1.20.1-1.3.0-dev.jar `
  --fixture-jar compat-test-mod/build/libs/arce-adapter-compat-test-1.0.0.jar `
  --evidence-dir C:/Temp/arce-atmosphere-evidence --java C:/Java/jdk-17/bin/java.exe --accept-eula
```

It prepares one Moon room using native operator commands and runs four clean
processes: state transitions/two live tag reloads, unchanged open-room restart,
omitted-provider fallback, and provider restoration. It checks native Anvil state,
vent resources and actual installed JAR identities. This is not equipment, player,
graphics, crash/power-loss or long-load acceptance.

On disposable test servers, add `-Darce_adapter_test.skipRocketAdapter=true` to
the Java startup arguments to leave the fixture's block, BlockEntity and movable
tag installed while omitting its rocket adapter registration. The fixture logs
the delivered registration event as skipped. Omit the property to restore normal
registration on the next process start; there is no runtime unregister operation.

This control isolates missing-provider behavior from missing block registrations.
It is not equivalent to uninstalling the fixture JAR, and should not be set for
the normal GameTest run, whose round-trip cases require the provider.

## Check saved cargo recovery

The bounded recovery runner uses a fresh disposable Forge 47.4.10 server and
the two built JARs. The server directory must contain **only** its prepared
`libraries` directory; an existing world, mods or configuration is rejected.
The evidence directory must not exist and must be outside the server directory.

From the repository root, with Python 3.11+ and Java 17:

```powershell
python -B scripts/run_v130_adapter_recovery_smoke.py D:/Temp/arce-recovery-server `
  --host-jar build/libs/advancedrocketry-community-1.20.1-1.3.0-dev.jar `
  --fixture-jar compat-test-mod/build/libs/arce-adapter-compat-test-1.0.0.jar `
  --evidence-dir D:/Temp/arce-recovery-evidence `
  --java "C:/Program Files/Java/jdk-17.0.7/bin/java.exe" --accept-eula
```

`--accept-eula` confirms acceptance of the Minecraft server EULA for this test.
The runner binds an offline, no-player server to loopback and launches six short
processes: assemble, entity restart, skipped-provider refusal, actual fixture
uninstall, matching-fixture reinstall/recovery, and restored-container restart.
Only the copied fixture JAR is removed/reinstalled; the input artifacts remain
unchanged. No remote server or client is used.

The runner checks exact cargo metadata, opaque entity/journal data, restored
inventory and authority counts, and retains command, process, status, log and
raw NBT evidence. Forge missing-registry ERROR diagnostics are classified only
for the known absent fixture; unrelated errors remain failures. A failed run
retains its world and evidence rather than retrying it automatically.

Recovery staging is synthetic, not a forced crash. This check covers one fixed
container with vanilla items; it does not establish cross-dimension behavior,
arbitrary missing-item/world-block recovery or long-duration stability.

## Check satellite payload missions

The fixture registers `arce_adapter_test:research_payload` using an untagged
amethyst shard and API 1.7's declarative mission values. Its GameTests use the real
terminal to manufacture, launch and claim, and check owner restrictions, replay
and native inventory serialization. No custom reward callback is installed.

For disposable packaged verification, use the same fresh libraries-only server
layout and explicit artifact arguments with
[`run_v130_satellite_payload_smoke.py`](../scripts/run_v130_satellite_payload_smoke.py).
The runner enables the fixture's operator command, performs a real datapack reload,
then removes and reinstalls only its copied fixture JAR across three clean
processes. The original task keeps its saved duration and reward after removal;
the later task uses the new definition. Exact research accounting includes the
first discovery charge and refuses duplicate claims. Raw SavedData and terminal
chunk data are retained alongside process and artifact identities.

The actor is a network-free FakePlayer, not a logged-in client. These short checks
do not establish real-player UI behavior, crash/power-loss atomicity, arbitrary
missing-item recovery or long-duration stability.
