# Changelog

This file records player- and operator-visible changes. The project is an
unofficial community rewrite and is not supported by the original Advanced
Rocketry maintainers.

## v0.0.2 — PASSED, unreleased developer preview

**Status:** `PASSED` on 2026-08-30. This version is approved but has not been
tagged or published as a public release.

### Added

- A Java 17 / Minecraft 1.20.1 / Forge 47.4.10 bootstrap project.
- Minimal mod metadata, an initialization entry point, and client/server side
  separation checks.
- Build, reproducibility, DataGen, unit-test, GameTest, artifact-audit, and CI
  checks.
- A disposable packaged dedicated-server smoke test covering installation,
  startup, status, save, clean stop, and same-world restart.
- Strict, privacy-reviewed packaged-client evidence for metadata, disposable
  world entry, dedicated-server join/reconnect, and missing-project-mod behavior.

### Compatibility and content

- This build contains no playable blocks, items, machines, planets,
  dimensions, rockets, recipes, or progression.
- Worlds used with this developer preview are disposable; no save
  compatibility is promised for `v0.0.x` through `v0.4.x`.
- There are no optional runtime dependencies. Minecraft 1.20.1, Forge 47.4.10,
  and Java 17 are the verification baseline.

### Verification status

- Automated build, DataGen, unit, GameTest, artifact, side-boundary, CI, and
  packaged-server lifecycle evidence is present.
- Packaged-client metadata/world screenshots, matching-client
  join/disconnect/restart/reconnect, dedicated-server lifecycle, provenance,
  reproducibility, and human acceptance are complete.
- A Forge-only client displays the server as incompatible when the project mod
  is absent, but the tested connection was still accepted; this observed
  behavior is retained as a compatibility limitation.
- `PASSED` is an acceptance claim, not a stable-release claim. No public release
  or stable download is provided.

Installation and verification constraints are documented in
[`docs/releases/v0.0.2/INSTALLATION.md`](docs/releases/v0.0.2/INSTALLATION.md).
