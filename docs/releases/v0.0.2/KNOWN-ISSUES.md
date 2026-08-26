# KNOWN-ISSUES — v0.0.2

## Blocking before `PASSED`

- GitHub Actions baseline and Forge 47.4.23 compatibility lanes have not completed a remote run yet.
- Packaged dedicated-server startup, player connection, clean stop, and restart have not been executed.
- Client Mods-screen and world-start manual evidence has not been captured.

## Expected bootstrap limitations

- The build has no playable blocks, items, machines, planets, dimensions, rockets, recipes, networking, or persistent project data.
- The original geometric logo is a bootstrap placeholder and may be replaced only by another provenance-audited asset.
- The local machine defaults to Java 8, so recorded Gradle commands explicitly select the installed Java 17 JDK.

## Accepted development-runtime warnings

- Forge userdev reports missing `mods.toml` files for its own language-provider JARs.
- Forge userdev reports `union:` resource URLs as an unexpected schema.
- ForgeGradle uses Gradle features scheduled for removal in Gradle 9; this project remains on the MDK-compatible Gradle 8.8 wrapper.

These warnings originate in the Forge/ForgeGradle development runtime, not in project code. They must be re-reviewed if their wording or source changes.

## Resolved during implementation

- A transient Mojang CDN asset download failure passed on retry and has not recurred.
- The initial generated GameTest structure used the wrong NBT tag type for coordinates. The corrected fixture loads and the required GameTest passes.
