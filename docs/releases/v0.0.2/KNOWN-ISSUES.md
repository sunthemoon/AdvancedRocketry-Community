# KNOWN-ISSUES — v0.0.2

## Post-acceptance release boundary

- No acceptance-blocking issue remains; repository owner `sunthemoon` approved
  G8/G9 and marked the evidence-backed version `PASSED` on 2026-08-30.
- The version is not tagged or published. Any later GitHub Release must be a
  pre-release, never a stable release.
- Provenance and future client evidence bind historical Git commits. PR #3 must
  use a normal merge commit when accepted; squash/rebase history rewriting
  requires new bindings, mechanical validation, and the affected human reviews.

## Artifact reproducibility result and boundary

- The current approved and mechanically tested artifact has SHA-256
  `cd5ae579bae1bc21c1f67df2c3e00f196e0ee4a9ead01653c926b88ca37f32ad`;
  two Windows clean builds produced identical bytes.
- The current sources JAR has SHA-256
  `f958f4334e8f95062a6ed15257fb9c5d940759490f3dc335c70e2764f1acacbe`.
- The tested-implementation Linux Forge 47.4.10 upload was downloaded from
  workflow run 33302877815. Its main JAR, sources JAR, and content manifest are
  byte-identical to the Windows build and committed manifest.
- Governance run 33302877802 passed 517/517 tests and uploaded exact-head packet
  and final-G0 input-report artifacts. The renewed owner-approved source review
  is bound in immutable record commit `db9ce96`; mechanical artifacts alone are
  not relabeled as human decisions.
- Historical hashes `b10db978...`, `c627d23a...`, and `827c07b...` identify
  superseded artifacts from earlier packaging/evidence states. They must not be
  mixed with the current client/server session.
- Session-level evidence confirms source, server, and matching-client copies all
  used the same `cd5ae579...` SHA-256.

## Expected bootstrap limitations

- The build has no playable blocks, items, machines, planets, dimensions,
  rockets, recipes, networking, or persistent project data.
- Worlds are disposable; no compatibility is promised for `v0.0.x` through
  `v0.4.x`.
- The original geometric logo is a bootstrap placeholder and may be replaced
  only by another provenance-audited asset.
- A Forge-only client without the project JAR displays a red incompatible-
  server marker and an additional-mods message, but the tested connection was
  accepted into the world. v0.0.2 defines no gameplay/network state and makes
  no rejection guarantee; later content milestones must retest this behavior.
- Schema-4 client-profile snapshot timestamps are local self-attestation. The
  collector binds ordered endpoint inventories and rejects changed final state,
  but those endpoints cannot prove that no mod was temporarily added between
  captures. Human review must confirm the isolated-profile procedure and
  launch sequence before accepting the evidence.
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
