# Changelog

This file records player- and operator-visible changes. The project is an
unofficial community rewrite and is not supported by the original Advanced
Rocketry maintainers.

## v0.0.2 — UNRELEASED developer preview

**Status:** `IN_PROGRESS`. This version has not been approved, tagged, or
published as a public release.

### Added

- A Java 17 / Minecraft 1.20.1 / Forge 47.4.10 bootstrap project.
- Minimal mod metadata, an initialization entry point, and client/server side
  separation checks.
- Build, reproducibility, DataGen, unit-test, GameTest, artifact-audit, and CI
  checks.
- A disposable packaged dedicated-server smoke test covering installation,
  startup, status, save, clean stop, and same-world restart.
- Release evidence templates and a test-machine handoff for the remaining
  packaged-client checks.

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
  join/disconnect/restart/reconnect evidence, and human acceptance remain
  incomplete. No `PASSED` or public-release claim is made.

Installation and verification constraints are documented in
[`docs/releases/v0.0.2/INSTALLATION.md`](docs/releases/v0.0.2/INSTALLATION.md).
