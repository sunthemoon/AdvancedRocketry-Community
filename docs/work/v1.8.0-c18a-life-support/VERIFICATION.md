# V180 C18a: life-support devices, airlock, thermite and living gravity, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C18a ledger rows that
W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status`,
`2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages, the records in
`docs/work/v1.8.0-root-queue/records/` and the earlier C18a folders named below. Every SHA
below resolves with `git cat-file -e` and is an ancestor of `codex/v1.8.0-classic-content` at
`e4bd8a12`.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Merges and tested HEADs

| Merge on `codex/v1.8.0-classic-content` | Slices | Tested HEAD | Hosted run |
|---|---|---|---|
| `a859d2f4` | W14, W14F (ADR-066 sections 3 and 4): CO2 scrubber facades with an unstackable cartridge bank, pipe seals and detectors, retained-root protection; W14F closes review F1 and F2 | `02d86c65` | 38249148243 |
| `06f1a898` | W25: release provably stale scrubber facade bindings (W14F review L1 and L2) | `977e0258` | 38354701151 |

The registering commit inside `a859d2f4` is `38af92d9` (scrubber, cartridge, pipe seal,
detector and `OXYGEN.scrubberRequiresCartrige`).

Delivered before the Root handover, as commits on the shared branch:

| Content | Commits | Records |
|---|---|---|
| Atmosphere analyzer | `9fce551e` | `docs/work/v1.8.0-c18a-atmosphere-analyzer/` |
| Airlock door | `c263f7d9` (paired door and bounded native fixtures), `61ae5001` (resources and recipe), `ee77c51a` (geometry-based vacuum exposure) | `docs/work/v1.8.0-c18a-airlock/` |
| Seal detector | `9cd5890b` (resources and recipe), `33a3156e`, `c554e810` (installed-rule coverage), `b0013161` (admission fixtures) | `docs/work/v1.8.0-c18a-seal-detector/` |
| Thermite and thermite torch | `e82e5682` (resources), `a6675a4e` (material and torches), `ead0ece2` (survival crafting coverage) | `docs/work/v1.8.0-c18a-thermite/` |
| Living gravity, leaf A | `1b6071d1` (the `environment.classicGravityEnabled` switch), `8b3fdca9` | `docs/work/v1.8.0-c18a-living-gravity/` |

The living-gravity path applies one transient modifier on the Forge `ENTITY_GRAVITY` attribute
(`celestial/service/CelestialGravityController.java`); there is no coremod. The thermite item
carries the tag `forge:dusts/thermite`.

## Hosted CI

Each run is the workflow `v1.8 development regression`
(`.github/workflows/v180-development.yml`): the ledger validator with `--require-accepted`,
`clean build` with fresh unit tests, the artifact audit, the client-boundary check, `runData`
twice with a clean-tree check, all Forge GameTests and `check_gametest_log.py`. It runs no
dedicated server, no restart and no real client.

On 2026-10-13 W31 read each run back through the GitHub REST API
(`/repos/sunthemoon/AdvancedRocketry-Community/actions/runs/<id>`).

| Run | Head SHA returned | Branch | Attempt | Conclusion |
|---|---|---|---|---|
| 38249148243 | `02d86c65` | `codex/v1.8.0-w14-life-support` | 1 | success |
| 38354701151 | `977e0258` | `codex/v1.8.0-w25-scrubber-bindings` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

Every pre-handover commit in the table above is an ancestor of `02d86c65` and `977e0258`. Run
38371580132 tested the shared branch after every Root-queue merge. Since `888a81b2` the shared
branch has changed only documents.

## Independent reviews

The reviews are kept outside the repository. W31 recomputed the SHA-256 values on 2026-10-13;
they match the merge message. W25 had a Root review only (the assertion audit lost nothing).
The pre-handover slices record their reviews in their own folders.

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W14-review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W14-review/REVIEW.md` | BLOCK on F1 and F2 | `121651590cfb1678801b4da6e805ec52f001a36762400df322887f6219f0e0b5` |
| W14F-review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W14F-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `d5f9bead354ef3b505cf41fbf0b9b7a2f2078872e7452894d97ecd2e80223fe3` |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `asm_rule:GravityHandler.applyGravity(living)` | REDESIGNED | planetary gravity on living entities: one transient ARCE modifier on the Forge gravity attribute, no coremod (`1b6071d1`, `8b3fdca9`) |
| `block:airlock_door` | IMPLEMENTED | `advancedrocketrycommunity:airlock_door` (`c263f7d9`, `61ae5001`): a sealing door, the block half of the small airlock |
| `block:oxygenDetection` | IMPLEMENTED | `advancedrocketrycommunity:atmosphere_detector` (W14): redstone output by atmosphere state |
| `block:oxygenScrubber` | IMPLEMENTED | `advancedrocketrycommunity:co2_scrubber` (W14, W14F, W25): cartridge-based vent support with a finite fourth debit |
| `block:pipeSealer` | IMPLEMENTED | `advancedrocketrycommunity:pipe_seal` (W14): a sealing block that passes pipes |
| `block:thermiteTorch` | IMPLEMENTED | `advancedrocketrycommunity:thermite_torch` (`a6675a4e`): a light that works in vacuum |
| `config:OXYGEN.scrubberRequiresCartrige` | REDESIGNED | COMMON `OXYGEN.scrubberRequiresCartrige` (W14): exempts cartridge use only |
| `item:atmAnalyser` | IMPLEMENTED | `advancedrocketrycommunity:atmosphere_analyzer` (`9fce551e`): a handheld environment readout |
| `item:carbonScrubberCartridge` | IMPLEMENTED | `advancedrocketrycommunity:carbon_scrubber_cartridge` (W14): the scrubber consumable |
| `item:sealDetector` | IMPLEMENTED | `advancedrocketrycommunity:seal_detector` (`9cd5890b` and the seal tests above; ADR-067): reports the seal state of a block |
| `item:thermite` | IMPLEMENTED | `advancedrocketrycommunity:thermite` (`a6675a4e`): the thermite torch ingredient |
| `material:Thermite` | IMPLEMENTED | `advancedrocketrycommunity:thermite`: the legacy `dustThermite` is the thermite item, tagged `forge:dusts/thermite` |

Twelve rows: 10 IMPLEMENTED and 2 REDESIGNED. Appendix A also moves `item:smallAirlockDoor`
to MERGED into `block:airlock_door`, because the airlock door's item uses a `block.` language
key; a MERGED row names no evidence.

Fifteen C18a rows stay PLANNED (W30 section 2.3):

- `block:oxygenCharger`: the gas charge pad waits for D4 (ADR-066 sections 4 and 9);
- torches and fire starters (ADR-066 section 3.2): `block:unlitTorch`, `command:root/addtorch`,
  `config:OXYGEN.torchBlocks`, `config:OXYGEN.dropExtinguishedTorches`,
  `event:PlanetEventHandler.blockPlacedEvent(PlaceEvent)` and
  `event:PlanetEventHandler.blockRightClicked(RightClickBlock)`;
- non-player effects and spawn admission (section 3.3): `config:OXYGEN.entityAtmBypass` and
  `event:PlanetEventHandler.CheckSpawn(CheckSpawn)`;
- gravity-scaled fall damage, leaf B: `event:PlanetEventHandler.fallEvent(LivingFallEvent)`;
- `config:OXYGEN.vacuumDamage`, `config:PLANET.allowPlanetRespawn`,
  `config:PLANET.forcePlanetRespawn`, `event:PlanetEventHandler.sleepEvent(PlayerSleepInBedEvent)`
  and `event:PlanetEventHandler.playerTick(LivingUpdateEvent)`.

## Open items

- **S1 (dedicated server and native restart):** not done for the scrubber, seals, detectors,
  analyzer, airlock, seal detector or thermite.
- **S2 (crash cut across stores):** no crash-cut evidence. The scrubber's retained root and its
  facade bindings span several block entities.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence. The airlock and thermite need V1 and
  V2.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open,
  including the thermite art's provenance approval.
- **Follow-ups of this batch:**
  - W14F-review L3: one inaccurate record sentence;
  - the lossless carbon-cartridge refresh in the chemical reactor (ADR-066 section 3.1);
  - leaf B of living gravity, with the vehicle and passenger Medium M1
    (`docs/work/v1.8.0-c18a-living-gravity/TASK-01.md`).

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, S2, V1, V2 and the
art and provenance reviews remain open, as listed above.

## Addendum (Root, 2026-10-14): W34 non-player atmosphere

W34 merged after W31 wrote this file, so its three ledger rows were still PLANNED. This addendum delivers them.

| Item | Value |
|---|---|
| Merge on `codex/v1.8.0-classic-content` | `179452d7` |
| Slice | W34 (ADR-066 section 3.3): non-player atmosphere effects, `atmosphere_bypass`, spawn admission and `vacuumDamage` |
| Tested HEAD | `85a0f6f4` |
| Hosted run | 38398138235 (success) |
| Review | Root review only. W34 changes no persistence |

| Unit | Disposition | Delivery |
|---|---|---|
| `config:OXYGEN.entityAtmBypass` | REDESIGNED | The entity-type tag `advancedrocketrycommunity:atmosphere_bypass`, generated empty. It exempts only atmospheric effects (`NonPlayerAtmosphereService`, `V180AtmosphereEntityTags`) |
| `event:PlanetEventHandler.CheckSpawn(CheckSpawn)` | REDESIGNED | `MobSpawnEvent.PositionCheck` denies natural and spawner spawns at an unavailable, PENDING or unbreathable eye cell, or for an opted-in hostile profile outside a sealed room. The command and spawn-egg exceptions are tested |
| `config:OXYGEN.vacuumDamage` | IMPLEMENTED | COMMON `OXYGEN.vacuumDamage`: 0 to 20 health points per scheduled second, default 2.0, which is the present engine's damage. Zero stops the damage only |

This addendum is development evidence only. It approves no Required Gate, candidate, version or release.

## Addendum (Root, 2026-10-14): W33 torches and W38 gravity-scaled falls

| Merge on `codex/v1.8.0-classic-content` | Slice | Tested HEAD | Hosted run | Review |
|---|---|---|---|---|
| `f2f8aa2c` | W33, W33F, W33G (ADR-066 section 3.2): combustion torches, unlit torches, fire starters | `dfaab973` | 38452267827 (success) | W33 review BLOCK, SHA-256 `79e13c96f3f4fcfd5ff3eaac07c65453dc46711b4e00a996d8fc5451fd35e60b`; W33F re-review BLOCK, `09c8d97b03e242442c324f0099ea3eb1f91caec7fe5c923b888acfa96ff7665f`; W33G's fixes verified by Root |
| `68dbd882` | W38 (ADR-066 section 3.3, living gravity leaf B): gravity-scaled fall damage | `f4406064` | 38450840128 (success) | Root review |

The review reports are kept in `D:/GitHub/ARCE-Task-Evidence/root-queue/W33-review/` and `W33F-review/`. The records are `records/W33.md`, `W33F.md`, `W33G.md`, `W38.md` and `W38-DESIGN.md`.

| Unit | Disposition | Delivery |
|---|---|---|
| `block:unlitTorch` | IMPLEMENTED | `advancedrocketrycommunity:unlit_torch` and `unlit_wall_torch`: no light or flame; the source pair is kept in a bounded schema-1 root; quarantined roots are protected (R-034) |
| `command:root/addtorch` | REDESIGNED | The `torch_pairs` data catalog and the `combustion_torches` block tag replace the command |
| `config:OXYGEN.torchBlocks` | REDESIGNED | The same catalog and tag replace the list |
| `config:OXYGEN.dropExtinguishedTorches` | IMPLEMENTED | COMMON `OXYGEN.dropExtinguishedTorches`, default false: removes the torch and drops one pair item instead of converting it |
| `event:PlanetEventHandler.blockPlacedEvent(PlaceEvent)` | REDESIGNED | Tagged torches placed in noncombustible or pending air turn unlit through a bounded per-Level queue; unsupported tagged torches refuse placement |
| `event:PlanetEventHandler.blockRightClicked(RightClickBlock)` | REDESIGNED | Tagged fire starters (`combustion_fire_starters`) are suppressed without combustion air; an unlit torch is relit only in combustion air |
| `event:PlanetEventHandler.fallEvent(LivingFallEvent)` | REDESIGNED | One `LivingFallEvent` listener scales a landing once by the applied gravity multiplier. The horse family stays native (ADR-066 W38 note) |

This addendum is development evidence only. It approves no Required Gate, candidate, version or release.

## Addendum (Root, 2026-10-14): W39 underwater breathing and W40 space safety return

| Merge on `codex/v1.8.0-classic-content` | Slice | Tested HEAD | Hosted run | Review |
|---|---|---|---|---|
| `fd3c406f` | W39 (ADR-066 section 3.3): suit underwater breathing | `eb91f79f` | 38490922953 (success) | Root review |
| `4548cf2a` | W40 (ADR-066 section 3.3): space safety return | `2a6ea6bc` | 38494140652 (success) | Root review |

The records are `records/W39.md` and `records/W40.md`.

| Unit | Disposition | Delivery |
|---|---|---|
| `event:PlanetEventHandler.playerTick(LivingUpdateEvent)` | REDESIGNED | Two server player-tick services replace the legacy handler. W39: a sealed, oxygenated suit restores air under water once per 20 ticks, only after its oxygen debit commits; vanilla drowning stays native. W40: a connected player adrift outside every committed station region of the Space Level for 100 ticks returns to the nearest station it may VISIT whose arrival is loaded and safe, or to the Overworld after a complete pass finds none. The query is fair, resumable and budgeted; it loads no chunk. The legacy farthest-station rule is not kept. The Moon advancement is C18c |

This addendum is development evidence only. It approves no Required Gate, candidate, version or release.

## Addendum (Root, 2026-10-14): W37 sleep admission and planet respawn

| Merge on `codex/v1.8.0-classic-content` | Slice | Tested HEAD | Hosted run | Reviews |
|---|---|---|---|---|
| `4f9e6597` | W37 with W37F to W37J (ADR-066 section 3.3) | `de1935ae` | 38530882792 (success) | W37 BLOCK `cfd61b74…`; W37F BLOCK `bc7d5b2b…`; W37H and W37I BLOCK `eaeb57cf…`; W37J Root review MERGE `e01e344d…` |

The records are `records/W37.md` and `W37F.md` to `W37J.md`; the reviews are kept in `D:/GitHub/ARCE-Task-Evidence/root-queue/`.

| Unit | Disposition | Delivery |
|---|---|---|
| `config:PLANET.allowPlanetRespawn` | IMPLEMENTED | COMMON `PLANET.allowPlanetRespawn`, default false: an off-world saved point is used for a death respawn or the End exit only when it is loaded, free of hazards, permitted by its station and breathable; otherwise the Overworld rules apply and the point is kept (R-036) |
| `config:PLANET.forcePlanetRespawn` | IMPLEMENTED | COMMON `PLANET.forcePlanetRespawn`, default false: with allowPlanetRespawn, skips only the arrival air check |

`event:PlanetEventHandler.sleepEvent(PlayerSleepInBedEvent)` stays PLANNED: W37's sleep refusal is implemented, but with the off-world types still `natural=false` vanilla refuses those beds first, so the row is delivered with W37b.

This addendum is development evidence only. It approves no Required Gate, candidate, version or release.
