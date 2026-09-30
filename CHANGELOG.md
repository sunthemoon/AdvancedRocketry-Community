# Changelog

This file records player- and operator-visible changes. The project is an
unofficial community rewrite and is not supported by the original Advanced
Rocketry maintainers.

## v1.5.0 — in development

**Status:** `IN_PROGRESS`; development identity `1.20.1-1.5.0-dev`.
No candidate or release approval is assigned.

- Upgrade legacy station storage before world startup, with byte-exact backups
  of managed files and their previous copies. Backup manifests identify each
  file's source and target schema; other managed authorities remain at schema 2.
- Preserve old station UUIDs, owners, members, invitations, cells, landing pads,
  orbit IDs and 512-square regions.
- Add `/arce station expand`: the owner, or an operator, standing inside a
  station in Space can grow its permission region once from 512 to 768 blocks
  square around the same center. The server shows the station ID and old/new
  bounds, warns that blocks already in the added area join the station, and
  requires `/arce station expand confirm <station_id>` within 10 seconds.
  Confirmations are one-shot and bound to that player and the observed station;
  they are discarded on logout or server stop. Members and invitees cannot
  expand. Both steps must be typed by the connected player themselves: the
  console, command blocks, functions, signs and `/execute as` are rejected.
  No blocks are moved or removed and no chunks are loaded.
- The expanded registry is written to a staged file, verified and atomically
  replaced before the new region takes effect. If saving fails the station
  keeps its old region; if the result cannot be determined, further station
  changes are disabled until restart.
- Stations now have their own gravity. Players inside a station's region use
  the station's gravity; elsewhere in Space the shared Space gravity (zero)
  still applies. Existing stations keep zero gravity. The owner, or an operator,
  standing in the station can set it with `/arce station gravity <0-100>`
  (percent of normal gravity), under the same rules as expansion and saved the
  same checked way. Stored values above four times normal are limited to four
  for movement; the public API still reports the stored value.
- Add `/arce station environment`: shows the station you are in, its orbited
  body (and whether it is still available), gravity, vacuum, solar intensity
  of the orbited body and sun angle. The sky is not yet per-station.
- Reject malformed, mixed-version or unsupported station data without replacing
  it. If station authority is unavailable, deny player placement and breaking
  in Space, including ordinary operator building; other Levels are unaffected.
- Keep the 4,096-station and 64-reservation limits independent. A failed commit
  at station capacity retains the pending reservation.

Back up the complete world before using development builds. Restore that backup
for downgrade; older station readers cannot use the new schema. Orbital
effects, multi-star travel and warp recovery remain under development.
See the [implementation log](docs/work/v1.5.0-implementation-log.md) for tested
scope and remaining work.

## v1.4.0 — in development

**Status:** `IN_PROGRESS`; development identity `1.20.1-1.4.0-dev`.
No candidate or release approval is assigned. The
[development handoff](docs/releases/v1.4.0/RELEASE-EVIDENCE.md) distinguishes
implemented features, finite verification and outstanding acceptance.

- Add fixed Mars and Venus worlds, contrasting terrain and bounded physical
  landing checks. Gas giants can be logical/orbit subjects, never fake surfaces.
- Introduce strict schema-2 celestial definitions with explicit capabilities,
  sunlight and reserved radiation metadata; legacy definitions keep defaults.
- Publish celestial definitions and routes together on successful reload;
  invalid data retains the previous pair. Persist body/Level reservations
  across removal and restart without deleting worlds or reassigning identities.
- Add opt-in cold, heat, pressure and sunlight exposure with passive equipment
  and supplied sealed-room protection. Climate protection does not provide oxygen.
- Add a schematic console star map with server-filtered destinations, station
  selection, environment information and explicit locked/unavailable reasons.
- Add bounded resource-pack sky, sun, stars, fog and ambience profiles. Shared
  Space uses a generic sky, not a station-specific view of its orbit body.
- Unlock eligible planetary arrivals through world-shared discovery and private
  satellite research. Concurrent missions retain their captured fees; repeated
  claims never intentionally repay research. Discovery does not grant station access.
- Order research receipts, discovery and completion writes for retryable process
  recovery. Retain removed-body progress and replay paid claims after restoration;
  a full 128-ID discovery history waits instead of evicting existing entries.
- Require matching client/server builds with flight protocol 8 and celestial
  display protocol 2. API 1.7 and existing SavedData schemas remain unchanged;
  the new body/Level ledger has its own schema 1.

Use complete pre-upgrade backups for downgrade; older strict schema-2 readers
do not accept new optional fields. Finite actual v1.3-copy upgrade and four
two-store interruption checks are not universal power-loss, whole-world,
real-GPU, multiplayer or long-load acceptance. No full terraforming, multi-star
warp or complete classic-content parity is included.

## v1.3.0 — in development

**Status:** `IN_PROGRESS`; development identity `1.20.1-1.3.0-dev`.
No candidate, stable artifact or release approval is assigned. See the
[development handoff](docs/releases/v1.3.0/RELEASE-EVIDENCE.md) for exact evidence
and remaining acceptance; preceding development milestones are not implied released.

- Add API 1.7 with explicit version/deprecation policy, a compile-only classifier,
  isolated compatibility mod and supported-use documentation.
- Allow integrations to register owned rocket-container adapters, block-state
  atmosphere boundaries, suit oxygen equipment, numeric rocket components,
  whole-item fuels and declarative satellite payload missions.
- Expose server-lifetime read-only environment/body-context queries without
  loading chunks or granting world-mutation authority.
- Preserve captured rocket statistics, fuel batches and satellite mission
  rewards when definitions change; validate registration ownership and limits.
- Migrate Fuel Loader schema 1 explicitly to schema 2, retaining pending units,
  exact remainder and ownership. Eligible loader/terminal drops preserve bounded
  resources or quarantined roots; arbitrary no-drop destruction is not recovery.
- Keep unavailable external data rather than silently substituting empty items;
  restore matching providers/dependencies before retrying blocked recovery.
- Require explicit consent before disassembling a rocket with remaining fuel;
  this is disposal, not fuel recovery.
- Add bounded satellite definition/target menu framing and stale-menu rejection.
  Install matching host builds on client/server; unchanged channel numbers do
  not make old/new menu frames compatible.
- Verify deliberate provider exceptions, oversized data and finite slow returns
  through actual assembly/disassembly. These checks are not callback preemption,
  arbitrary mod isolation or cross-file power-loss guarantees.

Full original-content, real-client and reference-load acceptance remains pending.

## v1.0.0 — in development

**Status:** `IN_PROGRESS`; development identity `1.20.1-1.0.0-dev`.
No stable artifact or release approval exists yet.

- Freeze the accepted Beta gameplay; preserve schema 2, its existing format
  epoch, stable IDs and network authority boundaries.
- Add populated five-root persistence regression coverage for lossless loads,
  migration backups, partial-commit rollback, malformed/future data rejection,
  ownership and mission/research replay protection.
- Defer interrupted assembly/disassembly recovery until the origin's persisted
  entities have loaded, preventing restored blocks from coexisting with a
  late-loading rocket. Existing save formats and chunk-ticket policy are unchanged.
- Cancel a countdown using its server-synchronized destination rather than the
  console's editable choice. Preserve station selection across window resizing
  and lock route editing during flight.
- Show the server's fuel quote for the selected Earth, Moon or station route,
  including its route-specific fuel sufficiency, instead of reusing the default
  route. The flight channel requires protocol 5 on both sides; save formats and
  server permission checks are unchanged.
- Plan `1.0.x` as a compatible maintenance line, with feature expansion in v1.1+.
- Keep occupied rockets in world storage rather than transferring their save
  ownership into the last passenger's file on logout. Normal resumption of an
  unchanged landed rocket is informational; actual recovery repairs still warn.
- Defer passenger recovery until entity storage is ready, using bounded
  lifecycle-owned retries and rechecking the selected entity's current seats.
- Cancel pending passenger recovery immediately on logout so a quick new login
  receives its own wait window without removing the saved passenger seat.
- Preserve post-landing boarding/leaving choices on reconnect and release the
  completed transfer reservation when a refueled rocket is disassembled.
- Reconcile matching legacy player-embedded rocket copies on login, preferring
  the journal-bound entity and preserving fuel added after landing instead of
  selecting an older copy by UUID order.
- Record successful requested countdown cancellation as informational rather
  than a transfer-failure warning. Destination/creation/spawn failures still
  warn with the same diagnostic fields; strict log audits are unchanged.
- Keep flight-console fuel text clear of its panel border and show selection
  markers for the active countdown destination, preserving the editable choice
  when the countdown is cancelled.
- Keep mounted client players aligned with the moving rocket during ascent and
  descent using bounded synchronized seat assignments, including reconnects.
  This does not expose authoritative flight state or change saved seat identities.

## v0.9.0 — PASSED Beta 1 pre-release

**Status:** `PASSED` by repository owner `sunthemoon` on 2026-09-03. PR #13
passed 4/4 checks and merged as `a7196ff9b22220c344071a1af69a663036f76aef`;
that merge reproduced the accepted 1,225,536-byte JAR and 758-entry content
manifest byte-for-byte. The exact artifact is published as
[v0.9.0-beta.1](https://github.com/sunthemoon/AdvancedRocketry-Community/releases/tag/v0.9.0-beta.1),
a pre-release rather than a stable build.

### Added

- Transactional schema-1-to-2 migration for five managed SavedData roots with
  byte-exact backup manifests, staged validation, atomic replacement, rollback,
  idempotent restart, and stable recovery diagnostics.
- A bounded path-free operator report, stable `ARCE-BETA-*` diagnostic IDs,
  strict localization/resource/accessibility auditing, and exact-hash Beta bug
  and compatibility report fields.
- Isolated optional JEI `15.56.0.205` Electrolyzer recipe presentation, with
  Forge 47.4.10/47.4.23 and JEI present/absent matrix evidence.
- Packaged maximum-combination soak and process-kill recovery harnesses covering
  a 2,048-block rocket, 16 vents, 10 stations, 100 missions, four simulated
  status clients, periodic saves, memory/tick budgets, and restart authority.

### Changed

- Fixed the candidate identity at `1.20.1-0.9.0-beta.1` and documented the
  supported runtime, one-way v0.8 world-upgrade floor, feature freeze, and
  optional compatibility boundary.
- Tightened flight-intent decoding to reject oversized, truncated, trailing,
  and noncanonical frames before server handling.
- Kept machine recipes out of the vanilla recipe book while preserving their
  optional JEI display.

### Verified

- Two byte-identical Java 17 clean builds, 273 JUnit tests, 44 Forge GameTests,
  deterministic DataGen, a 758-entry JAR audit, common/client side scanning,
  packaged migration/restart, forced-stop exact recovery, and four client
  compatibility cells are bound to one SHA-256 candidate.
- A strengthened 7,200.001-second combined run kept all 16 vents active at
  every 30-second sample, held 20 TPS, passed fixed tick/RSS/old-generation
  budgets, and retained the maximum scenario across restart. The same migrated
  world then passed the Electrolyzer restart plus 20 Earth–Moon round trips and
  all 8 flight restart checkpoints.
- The security review reports zero known Critical or High findings. The owner
  accepted G0/G8/G9 with the explicit v0.9-only visual boundary in ADR-013.

## v0.8.0 — PASSED candidate, unreleased developer preview

**Status:** `PASSED` by repository owner `sunthemoon` on 2026-09-03. PR #12
passed 4/4 checks and merged as `8e39b1ef440306632cf101b5017e0bcb1f12eef5`;
that merge reproduced the accepted 1,166,061-byte JAR and 723-entry content
manifest byte-for-byte. No tag or public release exists.

### Added

- One bounded logical data satellite with a powered Satellite Terminal,
  owner-bound assembly and launch, game-time mission scheduling, exactly-once
  research claims, and persisted celestial discovery.
- Independent fail-closed satellite, mission, research, and terminal schemas;
  server-validated terminal intents; bounded operator inspect/recover/cancel
  commands; and zero permanent satellite chunk tickets.
- Generated recipes, models, blockstate, loot, tags, localization, and one
  data-driven satellite definition with an exact 21-file provenance manifest.
- Packaged restart, two-owner, 100-mission stress, JFR performance, two-client
  join/disconnect, and post-restart reconnect evidence bound to one JAR.

## v0.7.0 — PASSED candidate, unreleased developer preview

**Status:** `PASSED` by repository owner `sunthemoon` on 2026-09-01. PR #11
passed 4/4 checks and merged; its merge commit reproduced the accepted
1,009,631-byte JAR and 636-entry content manifest byte-for-byte. No tag or
public release exists.

### Added

- Persistent 512×512 station regions on a 1,024-block square-spiral grid in one
  shared Space Level, with bounded allocation and schema-versioned SavedData.
- Transactional station deployment, a 17×17 platform, exact rollback/recovery,
  ownership, invitations, membership, transfer, inspection, and isolated deletion.
- Server-authoritative region protection, accessible station destinations,
  approved-pad arrivals, and 400-tick bounded flight tickets.
- Packaged station lifecycle and two-client validation bound to the exact
  1,009,631-byte candidate JAR.

## v0.6.0 — PASSED candidate, unreleased developer preview

**Status:** `PASSED` by repository owner `sunthemoon` on 2026-09-01 after two
byte-identical clean builds, 20 packaged Earth–Moon round trips, eight restart
checkpoints, two-client dedicated-server evidence, and G0-G9 review. PR #10
passed 3/3 checks and merged; its merge commit reproduced the accepted JAR and
591-entry manifest byte-for-byte. No tag or public release exists.

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
