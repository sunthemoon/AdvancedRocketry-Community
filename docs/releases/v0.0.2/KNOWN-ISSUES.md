# KNOWN-ISSUES — v0.0.2

## Blocking before `PASSED`

- Human review has not approved the Forge MDK/Gradle Wrapper license scope and
  the source/binary notice treatment. The mechanical provenance record remains
  `PENDING_HUMAN_REVIEW`.
- An isolated packaged client has not captured the Mods page or disposable
  single-player world evidence.
- A matching packaged client has not joined, disconnected, or reconnected after
  restarting the retained server world.
- The missing-project-mod compatibility behavior has not been observed, and the
  proposed G4 applicability decisions have not been reviewed.
- Final human release acceptance is absent. The version is not tagged, merged
  as accepted, or published. Any later GitHub Release must be a pre-release,
  never a stable release.

## Artifact reproducibility result and boundary

- The current reviewed local artifact has SHA-256
  `58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944`;
  two Windows clean builds produced identical bytes.
- The current sources JAR has SHA-256
  `2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293`.
- The current-head Linux Forge 47.4.10 upload was downloaded from workflow run
  33098971600. Its main JAR, sources JAR, and content manifest are byte-identical
  to the Windows build and committed manifest.
- Historical hashes `b10db978...`, `c627d23a...`, and `827c07b...` identify
  superseded artifacts from earlier packaging/evidence states. They must not be
  mixed with the current client/server session.
- This result does not replace session-level identity checks. Manual evidence
  still requires the source, server, and client copies used in one session to
  have the same SHA-256.

## Expected bootstrap limitations

- The build has no playable blocks, items, machines, planets, dimensions,
  rockets, recipes, networking, or persistent project data.
- Worlds are disposable; no compatibility is promised for `v0.0.x` through
  `v0.4.x`.
- The original geometric logo is a bootstrap placeholder and may be replaced
  only by another provenance-audited asset.
- A fresh Forge/server installation requires network access. The harness
  verifies the installer, preserves failed attempt logs, retries timeouts or
  nonzero exits with validated partial downloads, and refuses to resume a
  directory containing server runtime state.

## Accepted development-runtime findings

- Forge userdev language-provider JARs report missing `mods.toml` files.
- Forge userdev reports `union:` resource URLs as an unexpected schema.
- ForgeGradle uses Gradle features scheduled for removal in Gradle 9; the
  project remains on the MDK-compatible Gradle 8.8 wrapper.
- Fresh GameTest/server directories create missing default configuration files;
  GameTest also logs Minecraft's initial missing `server.properties` before
  continuing successfully.
- Headless runs report that advanced terminal features are unavailable.

These findings originate in Minecraft, Forge, ForgeGradle, or the fresh test
environment. They must be re-reviewed if their source or wording changes; no
project-source ERROR is accepted.

## Resolved during implementation

- A transient Mojang CDN asset download failure passed on retry and left the
  generated resource unchanged.
- The generated GameTest structure now uses the required list-of-int coordinate
  schema and an explicit air block.
- The executable bit for `gradlew` is stored in Git, so CI setup no longer
  dirties the DataGen worktree.
- The packaged-server harness decodes Forge's optimized status data, validates
  Minecraft/protocol/mod identity, and uses a normal disposable world.
- Installer timeout output is retained, and explicit installer-only recovery
  was verified before the final two-cycle server pass.
