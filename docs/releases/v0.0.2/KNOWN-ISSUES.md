# KNOWN-ISSUES — v0.0.2

## Blocking before `PASSED`

- Matching-client join, disconnect, restart, and reconnect have not been executed;
  they are deferred to a separate visible test machine.
- Client Mods-screen and world-start evidence has not been captured and is also
  deferred to the external test machine.

## Expected bootstrap limitations

- The build has no playable blocks, items, machines, planets, dimensions, rockets, recipes, networking, or persistent project data.
- The original geometric logo is a bootstrap placeholder and may be replaced only by another provenance-audited asset.
- The local machine defaults to Java 8, so recorded Gradle commands explicitly select the installed Java 17 JDK.
- The packaged-server harness requires network access during a fresh Forge server
  installation. It preserves failed installer-attempt logs and retries up to
  three times before failing.

## Accepted development-runtime warnings

- Forge userdev reports missing `mods.toml` files for its own language-provider JARs.
- Forge userdev reports `union:` resource URLs as an unexpected schema.
- ForgeGradle uses Gradle features scheduled for removal in Gradle 9; this project remains on the MDK-compatible Gradle 8.8 wrapper.

These warnings originate in the Forge/ForgeGradle development runtime, not in project code. They must be re-reviewed if their wording or source changes.

## Resolved during implementation

- A transient Mojang CDN asset download failure passed on retry and has not recurred.
- The initial generated GameTest structure used the wrong NBT tag type for coordinates. The corrected fixture loads and the required GameTest passes.
- The first Linux CI baseline changed the tracked `gradlew` mode during setup and was correctly rejected by the DataGen clean-tree check. The executable mode is now part of the repository.
- The initial packaged-server harness used the empty legacy Forge status list and
  an incomplete flat-world property. It now decodes Forge 47.4.10's optimized
  status data and uses a normal disposable world; final first-start/restart logs
  contain no ERROR.
- A transient Minecraft library download timed out in one discarded installer
  session. The final archived run installed successfully on attempt 1.
