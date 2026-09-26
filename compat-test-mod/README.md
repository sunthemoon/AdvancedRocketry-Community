# Independent adapter compatibility fixture

This standalone ForgeGradle project builds the existing
[`src/adapterTest`](../src/adapterTest/) fixture into a separate, reobfuscated
mod JAR. It consumes the published ARCE **API classifier**, not the host project,
main JAR or compiled output. Its platform is Java 17, Minecraft 1.20.1 and
Forge 47.4.10. This fixture requires API 1.2 for atmosphere boundaries as well as
the rocket registration API introduced in 1.1.

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
  hashes. It requires the ten exported API 1.2 types and rejects host internals,
  project output directories or additional host artifacts.
- `verifyConsumerBoundary` compiles an intentional internal import against that
  same classpath, requires the missing internal-package diagnostic, and checks
  the final reobfuscated fixture JAR for bundled host/API/platform classes. It
  reads the published normal host JAR to confirm the internal class exists,
  without ever adding that JAR to the compiler classpath.

Reports are written to `compat-test-mod/build/reports/consumer/`. The resulting
mod is `compat-test-mod/build/libs/arce-adapter-compat-test-1.0.0.jar`.

## Runtime limits

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
