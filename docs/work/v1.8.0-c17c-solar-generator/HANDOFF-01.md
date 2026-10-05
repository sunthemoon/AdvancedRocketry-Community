# C17c-SOLAR-01 source handoff

Date: 2026-10-06. Status: READY_FOR_REVIEW, not delivery.
Author: delegated implementation worker; Root is integrator and sole committer.
Fixed base: `a2d23d1f2fbd17af13c9e1f7097d8ca2e11b79bd`.
Worktree: `D:/GitHub/arce-v180-solar-generator-20261006`.
Branch: `codex/v1.8.0-solar-generator`.
Source and task records are frozen for independent review; no commit or source authority is inferred from this uncommitted snapshot.

## Scope and integration

Eighteen new owned files: eight `machine/solar` classes, `SolarGeneratorScreen`,
`V180SolarData`/`V180SolarLanguage`, four named unit classes, `SolarGeneratorGameTests`,
and this handoff/[progress](PROGRESS-01.md). Exact paths/bytes/hashes are in
[OWN-FILES-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17c-solar-author-20261006-81dfea/OWN-FILES-01.json).
No existing file or generated resource is modified. Root must add the agreed
registry/config/lifecycle/client/DataGen/shared tags/languages and generate resources.

Internal constructor shapes, already sent to Root:

- `SolarExposure()`; `start(MinecraftServer, CelestialCatalogManager)`,
  `read(ServerLevel, BlockPos, BlockEntity)`, `owns(MinecraftServer)`, `close(MinecraftServer)`.
  Startup alone acquires station SavedData. Matching server-thread close clears the one binding.
- `SolarGeneratorBlock(Properties, BiFunction<BlockPos, BlockState, SolarGeneratorBlockEntity>,
  Supplier<BlockEntityType<SolarGeneratorBlockEntity>>)`.
- `SolarGeneratorBlockEntity(BlockEntityType<?>, BlockPos, BlockState,
  Supplier<MenuType<SolarGeneratorMenu>>, BooleanSupplier, IntSupplier, Supplier<SolarExposure>)`.
- Client menu `(MenuType<SolarGeneratorMenu>, int, Inventory, FriendlyByteBuf)`;
  server menu `(MenuType<SolarGeneratorMenu>, int, SolarGeneratorBlockEntity)`.
- `V180SolarData(PackOutput, boolean client, boolean server)`;
  static `clientFiles()`, `serverFiles()`, `grids()`, `tint(String)`, `textures()`.
  `V180SolarLanguage.entries(boolean chinese)` contributes 21 keys per language.

Root config suppliers must use `solarGeneratorEnabled()` and
`solarGeneratorMultiplier()` with the committed task's keys/bounds. Root owns
one initially inactive `ModBlocks.SOLAR_EXPOSURE`, starts it after catalogs at
ServerStarted and closes it on stopping/stopped before catalog disposal.
Registered tests reference `ModBlocks.SOLAR_GENERATOR/SOLAR_PANEL`,
`ModBlockEntities.SOLAR_GENERATOR`, `ModItems.SOLAR_PANEL` and
`ModMenuTypes.SOLAR_GENERATOR`; they cannot compile until that disjoint integration exists.
No new exported API, writer, station transition, array or global world collection is added.

## Behavior and source tests

The successor [TASK](TASK.md) governs metadata-first checks and at most one
existing own FULL chunk/BE-map identity before catalog/cache sampling. Station
exposure reads the existing native UP-column cache; surface uses skylight,
in-build top, native day/sky and once-sampled rain/thunder. Captured catalog
identity/generation must remain current after a query. The buffer is 10,000 FE;
credit is room-limited final-floor binary64; all faces/null, push/pull share
1,000 FE/tick. Simulation does not reset allowance. Stale epochs, removed or
replaced owners, closed/foreign hosts, disable and repair cannot export. A live
owned but unavailable context can export retained FE.

`arce_solar_generator` schema1 is exactly two native ints (`schema`, `energy`).
The 4,096-byte/depth16/node1024 preflight precedes copy. Bounded unsupported
roots are faithfully copied; non-admissible roots retain their reference and
retrigger the existing whole-chunk/Level guard. Removal is blocked for refused
data. Ordinary supported drops carry no FE. The owner decision extends that
existing refusal policy to solar only; R-021, repair/recovery and whole-version
acceptance remain open. Seven scalar fields and the two-VarInt open format add
no inventory/action packet. Screen primitives and four opaque 16x16 grids are
NEW under the precommitted provenance; no external art was read or copied.

Authored, unexecuted: 21 JUnit methods in four suites and ten registered
GameTests with 20/40-tick bounds. The latter cover actual adapters, native
crafting/loot, six/null shared quota, push/pull and reentry, stale epochs,
disabled/repair/orphan/closed ownership, changing-catalog refusal and live
unavailable-context export, surface day/weather/roof/night, ordinary
no-skylight Space column updates and committed/missing orbit, repeated load,
future/scalar raw retention, oversized whole-chunk refusal and a connected
embedded-channel mock menu. Test-only setup explicitly loads/forces one owned
Space chunk and cleans only its owned station/marks/cells. It is not read-side
loading or dedicated-restart/client proof.

## Actual evidence and remaining work

Own evidence leaf:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17c-solar-author-20261006-81dfea`.
Read-only Git/source/governance reads and Python 3.13.15 `-B` controls only.
Check01: exit0, 29 static/declaration controls, 32,020 formula-model samples;
check02 binds the final source separately. Original read errors remain named
in progress/tool observations. No Java/javac/JUnit/Gradle/DataGen/GameTest,
native/server/network or Git mutation ran. No temporary fixture, build or
runtime copy was created; no cleanup was attempted.

Independent actual-source review, committed integration/compile/full regression,
DataGen twice/clean output, asset and client-boundary checks are still required.
Dedicated S1 supported FE and open/blocked Space stop/reload, malformed/refused
stopped bytes and new-Level retrigger, normal lifecycle/closed-host behavior,
real GPU V1, multiplayer V2 and every remaining v1.8 Gate are unverified here.
The declaration/control count is not acceptance; uncommitted source is not a
ledger delivery. Root must retain all later failures without changing ceilings,
assertions, source authority or the admitted save policy.
