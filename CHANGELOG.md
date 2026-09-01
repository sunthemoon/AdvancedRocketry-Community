# Changelog

This file records player- and operator-visible changes. The project is an
unofficial community rewrite and is not supported by the original Advanced
Rocketry maintainers.

## v0.6.0 — PASSED candidate, unreleased developer preview

**Status:** `PASSED` by repository owner `sunthemoon` on 2026-09-01 after two
byte-identical clean builds, 20 packaged Earth–Moon round trips, eight restart
checkpoints, two-client dedicated-server evidence, and G0-G9 review. PR #10,
merge identity, and post-merge artifact reproduction remain operational steps;
no tag or public release exists.

### Added

- A bounded Fuel Loader, Rocket Fuel Cell, and exactly-once server fuel debit.
- A persisted flight state machine and server-recomputed destination plan.
- Fixed-pad, journaled Earth–Moon transfer with four-case crash recovery and
  one authoritative rocket.
- Passenger UUID/seat bindings, disconnect/reconnect recovery, and exact landing
  disassembly.
- A destination screen plus launch, transit, landing, cancellation, and
  recovery feedback.
- Packaged-server acceptance covering 40 production flight legs and exact
  block, inventory, fuel, passenger, and entity accounting.

## v0.5.0 — PASSED, unreleased developer preview

**Status:** `PASSED` on 2026-09-01 after 3/3 pull-request checks, merge, owner
acceptance, packaged-server recovery, visible client evidence, and exact
post-merge JAR reproduction. No tag or public release exists.

### Added

- Loaded-chunk-only bounded rocket structure scanning and readable diagnostics.
- Canonical schema-1 snapshots with server-recomputed mass, thrust, capacity,
  seat, engine, and guidance statistics.
- Region-locked transactional assembly/disassembly with fault-injected rollback
  and exact approved container restoration.
- A restart-safe same-dimension `RocketEntity` and stale-journal recovery.
- Bounded tracking-player visual synchronization and cached block rendering.
- Rocket Assembler, motor, fuel tank, seat, guidance computer, recipes, tags,
  models, localization, and GameTest data.

This milestone does not launch, consume fuel, fly, or change dimensions. Those
behaviors remain in v0.6.0.

## v0.4.0 — PASSED, unreleased developer preview

**Status:** `PASSED` on 2026-09-01 after repeated build, automated tests,
packaged-server persistence/performance, two-player client evidence, owner
review, and all 3/3 pull-request checks completed. No tag or public release
exists.

Moon and Space require a complete suit with
finite oxygen unless a powered Oxygen Vent has established a bounded sealed
room. Opening the boundary or losing supply returns the room to vacuum.

## v0.3.0 — PASSED, unreleased developer preview

**Status:** `PASSED` on 2026-08-31 after repeated build, automated, packaged
client, two-player dedicated-server restart, persistence, provenance, owner
review, and all 3/3 pull-request checks completed. No tag or public release
exists.

### Implemented player-visible result

- Operators can list and validate the Earth/Moon/Space catalog.
- Development players can enter fixed Moon and Space Levels through controlled
  commands and return safely; rocket travel remains unavailable.
- Discovery and first-visit state survives a dedicated-server restart.

The three-body catalog is a bounded display snapshot, invalid datapack reloads
retain the last valid catalog, and legacy XML conversion produces deterministic
JSON plus explicit warnings without registering runtime dimensions.

This milestone does not add random planets, atmosphere damage, life support,
rockets, stations, or a custom sky renderer.

## v0.2.0 — PASSED, unreleased developer preview

**Status:** `PASSED` on 2026-08-31 after repeated build, automated, packaged
client, matching-client server restart, machine persistence, maintainer review,
and all 3/3 pull-request checks completed. No tag or public release exists.

### Added

- Fill the machine with water, insert empty canisters, and supply Forge Energy
  or redstone charge material.
- Observe server-authoritative progress and receive hydrogen and oxygen
  canisters after an exact, restart-safe processing cycle.
- Read energy, water, progress, redstone pause, and failure states in one menu.
- Use side-bounded item, fluid, and energy capabilities with schema-versioned
  persistence and future-schema preservation.
- Validate exact 50-cycle conservation, 12 JUnit cases, 12 Forge GameTests,
  matching-client reconnect, and GUI scales 1–4.

This milestone does not add a general power network, multiblock framework,
JEI integration, dimensions, atmosphere, or rockets.

Evidence and installation boundaries are documented in
[`docs/releases/v0.2.0/`](docs/releases/v0.2.0/).

## v0.1.0 — PASSED, unreleased developer preview

**Status:** `PASSED` on 2026-08-31 after packaged-client, matching-client
server, maintainer review, and all 3/3 pull-request checks completed. No tag or
public release exists.

### Added

- A deterministic exact-commit audit of 510 upstream Java files and 898
  upstream assets, including registry, LibVulpes, network, static-state,
  ASM/coremod, reference, and case-collision indexes.
- A ten-entry MIT provenance ledger with reproducible namespace/format
  transforms and a full ten-entry maintainer review.
- One inert machine casing, silicon wafer, basic circuit, advanced circuit,
  data storage unit, UI sound, and dedicated creative tab.
- DeferredRegister-based block, item, sound, and creative-tab registration.
- DataGen for English/Chinese language data, blockstate/models, sounds, loot,
  five recipes, tags, and recipe advancements.
- Asset/JAR/release-evidence validators and three registry/content GameTests.

### Player-visible result

- All five entries appear in the dedicated creative tab with English and
  Chinese names and complete item/block textures.
- The machine casing supports normal placement, orientation, particles, drop,
  and an explicit notice that machine behavior begins in v0.2.0.
- The matching packaged client joins and reconnects to the dedicated server
  after a same-world restart.

### Compatibility and limitations

- No machine processing, inventory, energy, fluid, dimension, atmosphere,
  rocket, satellite, networking, or progression system is implemented.
- OBJ/MTL import is explicitly deferred; v0.1.0 validates JSON models only.
- Worlds remain disposable and no compatibility is promised through `v0.4.x`.
- If published after acceptance, this build must be classified as a
  pre-release, never stable.

Evidence and installation boundaries are documented in
[`docs/releases/v0.1.0/`](docs/releases/v0.1.0/).

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
