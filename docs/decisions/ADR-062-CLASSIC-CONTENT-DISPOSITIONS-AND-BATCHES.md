# ADR-062 — Classic content dispositions and batch plan

```yaml
status: PROPOSED
revision: 3
date: 2026-10-02
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.8.0
development_dependency: ADR-060, ADR-061
revisits: ADR-018 (campaign trigger, proposal for the owner in section 8), ADR-046 (station screens), ADR-049 (satellite bay, spy telescope, star-map overlay), ADR-051 (physical asteroid fields, observatory, rocket mining), ADR-055 (line and spiral modes), ADR-056 (cross-system cargo), ADR-057 (black-hole sky), ADR-058 (station gravity block, non-player fields, legacy gravity API), ADR-059 (capsule visuals)
supersedes: ""
import_allowlist_sha256: 2790bb18c00ec00d907598a0a158eeae1d4a6a3cea376e80db7a66a9b401a161
```

## Context

The [legacy inventory](../work/v1.8.0-legacy-inventory.json) lists 653 legacy
content units of the pinned upstream commit `c5cd5af`: 101 blocks with 13
metadata variants, 35 items with 32 variants, 15 materials (including the extra products of vanilla iron and gold), 5 fluids, 61 block
entities, 10 entities, 12 registered biomes (2 more never registered), 34 world
generation classes, 9 registered satellite kinds (3 more classes never
registered), 3 missions, 15 sound events, 17 advancements, 13 atmosphere types,
19 commands, 135 configuration units (132 key names, three of them read in
two categories), 14 XML configuration files, 41 event
rules, 4 coremod rules (the gravity hook in a living and an other half), 16 registered packets (and 1 never registered), 13
integrations, 7 key bindings, 1 enchantment, and 22 LibVulpes blocks, items and
machines that the legacy gameplay depends on (structure blocks, motors,
hatches, battery, linker, holographic projector, coal generator). The [content audit](../work/v1.8.0-content-audit.md) explains the
inventory and the legacy behaviour behind each group.

Earlier ADRs deferred several items to v1.8 and asked for a decision here:
station and warp screens (ADR-046), the rocket satellite bay, the spy telescope
and the star-map overlay (ADR-049), physical asteroid fields, the observatory and
rocket mining (ADR-051), laser drill line and spiral modes (ADR-055),
cross-system cargo (ADR-056), the black-hole sky (ADR-057), the station gravity
block, fields acting on non-player entities and the legacy gravity API
(ADR-058), the elevator capsule visuals (ADR-059), and the force field projector
(v1.7 audit §8).

v2.0 may not keep `MISSING` or `UNKNOWN` items; `DEFERRED` and `REJECTED` need
an accepted ADR and a player-impact statement (roadmap §5). This ADR is that
record for v1.8.

## Decision

### 1. The ledger is the per-unit decision

[`v1.8.0-content-ledger.csv`](../work/v1.8.0-content-ledger.csv) gives every
inventory unit exactly one disposition:

| Disposition | Count | Meaning |
|---|---:|---|
| `IMPLEMENTED` | 52 | an existing modern ID or system delivers the unit (version and ID named) |
| `REDESIGNED` | 133 | the gameplay goal is delivered by a different modern mechanism (named, with its ADR) |
| `PLANNED` | 292 | delivered by a v1.8 batch (C15a–C18d), including redesigns that v1.8 itself builds |
| `MERGED` | 93 | an internal part (block entity, base class, subcommand) that follows another row |
| `DEFERRED` | 25 | not in v1.8 or v2.0 commitments; reasons and player impact in §3 |
| `REJECTED` | 58 | not migrated; reasons and player impact in §4 |

[`v1.8.0-asset-plan.csv`](../work/v1.8.0-asset-plan.csv) gives every one of the
898 legacy assets exactly one handling under ADR-061 §4: 190 `IMPORT`, 155
`REVIEW`, 430 `REGENERATE`, 113 `EXCLUDE` and 10 already `IMPORTED` (seven
textures, one sound and the two language files whose reviewed keys v0.1.0
extracted).
`scripts/validate_v180_content_ledger.py` checks both files against
`legacy-manifest`.

The ledger's vocabulary is not the parity verdict of docs/16 §5: `IMPLEMENTED`
and `REDESIGNED` become `PASSED_EQUIVALENT` and `PASSED_REDESIGNED` only after
behaviour acceptance with evidence; `PLANNED` is `MISSING` until its batch
closes; `DEFERRED` and `REJECTED` keep their names once this ADR is accepted.

### 2. Redesigned groups

| Group | Legacy | Modern delivery |
|---|---|---|
| Destination chips and selectors | planet, station and satellite ID chips; planet selector with UI entities; guidance computer access hatch | the flight star map (ADR-035), station commands (ADR-046), the satellite control chip (ADR-049) |
| Holographic planet selector | the block that set a station's warp destination | the station warp command (ADR-044, ADR-046); the C17b warp screen fronts it |
| Station construction | station assembler, packed station container, docking port | the station deployment kit (v0.7) and the checked expansion commit (ADR-040) |
| Rocket assembly bounds | launch pad, structure tower | the assembler scans connected blocks (v0.5); no pad or tower bounds |
| Research data | three data kinds, data bus | research points (v0.8) carried in data storage units |
| Mining rockets | rocket intake and drill blocks | gas intake and asteroid drill satellite modules with logical missions (ADR-051) |
| Vanilla equivalents | concrete, basalt, copper | vanilla blocks and the copper ingot, used by the batches that need them (copper products C15a, basalt C15b, concrete C17b) |
| Atmosphere tiers | low oxygen, super high pressure, superheated | no oxygen, one pressure tier and one heat tier (ADR-024, ADR-034); legacy low oxygen needed only a helmet, so folding it into no oxygen asks for a full suit |
| Configuration | ore, crater, geode, sealing and torch lists | data-driven features, tags and data packs |
| Protocol | 16 packets | the existing versioned channels; new intents follow ADR-061 §3.4 |
| LibVulpes machine parts | structure block, item and fluid hatches, power plug, holographic projector, battery | the machine casing (v0.1), kernel ports (ADR-016), pattern diagnostics that name each mismatched cell, satellite batteries (ADR-049) |
| Rocket screen key | open-rocket-UI key binding | interacting with the rocket opens the flight screen (v1.0) |
| Lifecycle and flight events | world load, save and unload; satellite and transition ticking; launch, de-orbit and teleport events; login sync | SavedData (ADR-032), the scheduler (ADR-050), the flight state machine and transfer journal (v0.6), celestial sync (ADR-035) |
| Planet spawn and ore rules | per-planet spawn lists; vanilla ore suppression on planets | biome spawners and feature lists in planet data (ADR-033) |
| Flight altitudes | orbit height, station clearance, trans-body injection | fixed flight phases (v0.6) |

### 3. Deferred (post-2.0) and player impact

| Item | Ledger rows | Reason | Player impact |
|---|---|---|---|
| Terraforming | terraformer, biome scanner, biome changer remote, biome changer satellite and component, biome ID packet, 7 configuration keys, 2 world-tick and chunk-populate rules | global world modification needs its own threat model, chunk budget and rollback design | planets keep their generated biomes; no device changes them |
| Hovercraft | item, entity | a new vehicle entity with control packets and rendering | no hovercraft; travel on foot, by rocket or by elevator |
| Landing float | block | legacy rockets placed floats under a landing on a non-water liquid (such as lava); on water they placed none and settled below the surface; the block had no recipe. The modern landing rule (ADR-033) needs solid support and skips liquid surfaces | rockets never land on liquid: the landing-site selector skips water and lava pads instead of floating on them |
| Cave planet terrain | `ChunkProviderCavePlanet` | a terrain type without a body that needs it | no cave-world planets |
| World types | planet start, space start | world creation presets outside the fixed Levels | worlds always start in the Overworld |
| Lunar lander decoration | `MapGenLander` | decorative structure | no lander structures on the Moon |
| Planet villages | `MapGenSpaceVillage`, `generateVanillaStructures` | vanilla villages on habitable planets need structure sets per body | no villages on other bodies |
| Satellite star-map overlay | (feature of ADR-049 §10) | client overlay with its own sync | satellite positions appear in the terminal list, not on the star map |
| Fields on non-player entities | (feature of ADR-058) | entity scans and push rules | area fields affect players only |
| Cross-system cargo | (feature of ADR-056) | routes stay inside one star system, like rockets | cargo crosses systems by station warp only |
| Cargo access from a rocket seat | coremod rule `RocketInventoryHelper.allowAccess` | assembled rockets keep their blocks in a snapshot; opening them in flight needs a container proxy | open rocket cargo after landing, or move it with the C17a loaders |

Deferring terraforming and the hovercraft past v2.0 narrows the v2.0 parity
target (PRODUCT.md lists terraforming among later-restored features); that is
a product choice for the owner at acceptance, not a technical conclusion.
A deferred item can return only through a new ADR in a later version. Deferred
content registers no ID in v1.8, so no world holds it and nothing needs a
migration; its legacy assets stay unimported.

### 4. Rejected and player impact

| Item | Ledger rows | Reason | Player impact |
|---|---|---|---|
| Never shipped | pumpkin and watermelon biomes, spy telescope satellite, three pipe block entities whose blocks were never registered, `MapGenSpaceStation`, the no-op crafting event | not registered, not referenced or without behaviour in the pinned legacy build | none |
| Station-deployed mining rockets | unmanned vehicle assembler, deployed rocket entity | single-tick cross-dimension moves and forced loading; logical missions deliver the same resources | mine asteroids and gas giants with satellites and the terminal |
| Temporary asteroid dimensions | asteroid chunk provider and world provider | runtime dimension creation (ADR-031 fixed Levels) | asteroids are logical instances (ADR-051) |
| Rocket asteroid and warp burns, free flight | two configuration keys, the never-registered `PacketMoveRocketInSpace`, `experimentalSpaceFlight`, the RCS and four rocket-turning key bindings | client-driven movement in space | rockets fly server-planned routes |
| Terrain damage at launch | `launchBlockDestruction` | grief by launch | launches never alter terrain |
| Vitrified sand | block | unobtainable in survival in the legacy build | none |
| Runtime planet editing | planet generate, delete, reset and set commands; random-planet, biome-list, XML-reset and dimension-range configuration keys | bodies are data pack definitions | edit data packs and restart |
| Numeric station dimension | `spaceStationId` (read in two categories) | numeric dimension IDs are not persistent identity | none |
| Data networks | wireless transceiver, three cable tick and break rules | research data moves in storage units | carry data units by hand or automation |
| Space elevator chip | item | unbounded position list; endpoint records replace it (ADR-059) | bind elevators through their endpoints |
| Technical blocks and entities | light source, rocket fire, laser node | replaced by client effects (ADR-055) | none |
| Silent placeholder sounds | five machine sound events | the legacy files are one silent placeholder | machines get real sounds only where a non-silent file exists (C18d) |
| Galacticraft bridges | handler, `OverrideGCAir` | Galacticraft compatibility is a product non-goal | none |
| Debug commands | `begintest`, `dumpbiomes`, `filldata` | debug only; GameTests replace the in-game harness | none |
| Creative energy plug | LibVulpes creative input plug | creative-only infinite energy | use another mod's creative energy source |
| Laser drill line and spiral modes | (feature of ADR-055) | automatic target stepping across chunks | move the laser target to drill elsewhere |
| Legacy gravity API | (feature of ADR-058) | per-entity static overrides | use the v1.3 public API |
| Planet gravity on non-living entities | coremod rule `GravityHandler.applyGravity(other)`, the non-living half of the gravity hook | items, projectiles, minecarts, falling blocks and primed TNT have no gravity attribute; changing them needs a coremod or per-tick motion edits for every such entity (AGENTS.md rejects coremods) | items and projectiles fall at Overworld speed on every body; players and mobs follow the body's gravity (C18a) |

### 5. Earlier deferrals resolved here

| Earlier record | Item | v1.8 decision |
|---|---|---|
| ADR-046 | station and warp screens | warp controller screen and station control blocks planned in C17b; the commands stay the authority |
| ADR-049 | rocket satellite bay | planned in C17c: a package in a bay registers through the ADR-049 launch path once, on reaching orbit |
| ADR-049 | spy telescope | rejected (§4) |
| ADR-049 §10 | star-map overlay | deferred (§3) |
| ADR-051 | observatory | planned in C18c as a ground survey that creates ADR-051 asteroid instances |
| ADR-051 | physical asteroid fields, rocket mining | rejected (§4) |
| ADR-055 | line and spiral modes | rejected (§4) |
| ADR-056 | cross-system cargo | deferred (§3) |
| ADR-057 | black-hole sky | planned in C18d |
| ADR-058 | station gravity controller block | planned in C17b over the ADR-041 commit |
| ADR-058 | non-player fields | deferred (§3) |
| ADR-058 | legacy gravity API | rejected (§4) |
| ADR-059 | capsule entity and animation | planned in C18d as a client effect; rides stay server-side |
| v1.7 audit §8 | force field projector | planned in C17b through the protection chain |
| ADR-046 UI-02 (also the v1.5 known issues) | station and warp messages are literal English; notices do not name the station, visitors are not notified, offline members get no notice at login; the waiver expires no later than v1.8.0 | fixed in C17b with the station screens: translatable keys in the `advancedrocketrycommunity_v150` namespace, GameTests that assert `StationManagementCode` tokens instead of prose, and the three notice gaps closed |

### 6. Batches

Each batch freezes its own contract (a batch ADR, an independent review, an
acceptance) before runtime work, then delivers its slices with tests, evidence,
a commit and a push. Slices can be split further; the ledger rows move with
them.

| Slice | Scope | Depends on |
|---|---|---|
| C15a | Materials and ores: titanium, aluminum, steel, tin, silicon, iridium, dilithium, titanium aluminide, titanium iridium, copper, iron and gold products (including the steel fan used by ten legacy recipes); ores and Overworld placement; smelting; common tags; the small plate press | ADR-061 |
| C15b | Moon, Mars and Venus surfaces: moon turf, ferric sand, geodes, charcoal logs; their biomes; craters, volcanoes and geodes; Moon ore placement. It changes Levels that existing worlds use, so its batch ADR discloses the seams (ADR-061 §6) | C15a |
| C15c | Classic exoplanet worlds for the alien forest, stormland, crystal chasms, deep swamp, marsh and ocean spires biomes; lightwood, electric mushrooms, crystal blocks and crystal clusters | C15b |
| C16a | Classic machine family on the v1.2 kernel; a combustion generator as the first Forge Energy source (a legacy install had the LibVulpes coal generator; without it the C16 machines would need another mod's power); motor tiers and the advanced casing; oxygen, hydrogen, nitrogen, rocket fuel and enriched lava fluids; pressurized tank; pump | C15a |
| C16b | Electric arc furnace, lathe, cutting machine (with sawmill recipes) and their structure parts | C16a |
| C16c | Crystallizer, chemical reactor, precision laser etcher, centrifuge and their parts | C16a |
| C16d | Components (circuit plates and boards, user interface, carbon brick) and the recipe graph tool; progression rebalance of existing recipes | C16b, C16c |
| C17a | Propulsion tiers (bipropellant, advanced, nuclear), oxidizer and fuel tables, fluid fueling, rocket item and fluid loaders, monitoring station | C15b (Moon and Mars iridium ore, ADR-063 §4), C16b (titanium aluminide and titanium iridium for the advanced and nuclear engines), C16c (dilithium crystals), C16d (tracking circuits) |
| C17b | Station controls (warp controller screen, gravity, altitude and orientation blocks), landing pads, station light, force field projector; the ADR-046 UI-02 language and notice gaps | C16d (circuits and the user interface of the warp controller); the ADR-046 revision in the batch ADR |
| C17c | Beacon and beacon finder, rocket satellite bay, solar generator and solar array | C17a, C16d (beacon circuits) |
| C18a | Life support and environment rules: CO2 scrubber and cartridge, gas charge pad, atmosphere detector, airlock door, pipe seal, torch and fire rules, thermite, seal detector, atmosphere analyzer, respawn and sleeping rules, atmosphere effects and spawning for non-player entities, planet gravity on living entities, gravity-scaled fall damage, suit underwater breathing, the space Level safety return | C15a (steel fan), C16d (user interface, carbon brick) |
| C18b | Equipment: suit workstation, pressure tanks, jetpack, upgrades, jackhammer, basic laser gun, space breathing enchantment | C18a, C16c (the chemical reactor applies the space breathing enchantment) |
| C18c | Research: observatory, astrobody data processor, advancements, technology tree report | C16d |
| C18d | Presentation: sounds, OBJ models, black-hole sky, capsule effect, GUI art, languages, the visual refresh of existing blocks, and a classic content guide for players | all batches |
| C19 | Ledger and matrix closure (`--require-accepted`, zero `PLANNED`), independent review, handoff | all batches |

### 7. Change control

A batch may implement fewer files or move a planned unit to an earlier or later
batch inside v1.8 in its own commit. The `import_allowlist_sha256` field above
pins the importable assets (ADR-061 §4.2); changing that list is a revision of
this ADR. Every `DEFERRED` and `REJECTED` ledger row cites this ADR, which
holds the player impact. Moving a unit to `DEFERRED` or `REJECTED`,
or reviving a deferred or rejected unit, needs a revision of this ADR with
reasons and player impact. The validator checks the form of every row; the
meaning of a link (that an owning unit really owns an asset, that an
`IMPLEMENTED` target is the unit's own delivery, that a `REJECTED` row has its
§4 entry) is checked in the review of the commit that changes it.

### 8. ADR-018's campaign trigger (owner decision)

ADR-018 (accepted, reaffirmed by the owner four times) starts the full
acceptance campaign when every original machine and dimension in the matrix is
implemented, and says that "closing an inventory row by deferring or rejecting
it is not completion for this trigger". This ADR defers or rejects original
machines (the atmosphere terraformer, the biome scanner, the unmanned vehicle
assembler, the wireless transceiver) and original dimension kinds (temporary
asteroid dimensions, cave planets, the planet and space world types, random
planet generation). Read literally, the trigger could then never fire, and v2.0
could not start its campaign.

This ADR does not change ADR-018. It asks the owner to choose, as part of the
acceptance of this ADR:

1. **Amend the trigger (recommended).** The campaign starts when every
   original machine and dimension is implemented, or carries a `DEFERRED` or
   `REJECTED` disposition that the owner has accepted as outside the v2.0
   parity target, with its player impact published. Rows that are only
   proposed, `MISSING` or `UNKNOWN` still block the trigger. The amendment is
   recorded as a revision of ADR-018.
2. **Keep the trigger.** The campaign waits until those machines and
   dimensions are implemented; the affected rows move from `DEFERRED` or
   `REJECTED` to `PLANNED` in a later version, and v2.0 cannot start its
   campaign before then.

Until the owner decides, ADR-018 stands as written.

## Alternatives

### A. One ADR per deferred or rejected item

Twenty ADRs with the same template; the ledger already holds the per-unit
record. Grouping keeps one reviewable decision.

### B. Defer everything not needed for a playable technology path

Would leave v2.0 with dozens of `DEFERRED` rows (pumps, force fields, upgrades,
the observatory) that are cheap enough to deliver.

## Consequences

### Positive

- Every legacy unit and asset has one decision before content work starts.
- Earlier "decide in v1.8" items are closed.

### Negative

- v1.8 is large: 14 slices and 292 planned units.
- Several classic toys (hovercraft, terraforming) stay out of v2.0.

## Validation

- `scripts/validate_v180_content_ledger.py` passes;
- an independent contract review checks the dispositions against the upstream
  behaviour;
- each batch moves its rows and keeps the validator passing.

## Revisit when

- a batch shows that a planned unit cannot be delivered safely;
- the maintainer changes the parity target for v2.0.

## Review history

- Revision 1 (`f05eec2`): proposed with the C14 audit.
- Revision 2: answers review round 1 (0 Critical, 2 High, 12 Medium, 7 Low,
  6 Info), one commit per finding; see
  [review-01-dispositions](../work/v1.8.0-preparation/review-01-dispositions.md).
- Revision 3: answers review round 2 (0 Critical, 1 High, 6 Medium, 7 Low,
  5 Info), one commit per finding; see
  [review-02-dispositions](../work/v1.8.0-preparation/review-02-dispositions.md).
