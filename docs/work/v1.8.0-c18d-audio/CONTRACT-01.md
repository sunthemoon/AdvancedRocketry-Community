# CL18D-AUDIO-01: ten classic sound events, contract draft 01

Date: 2026-10-07. Milestone: v1.8.0 / C18d. Status: **proposed, not frozen**.
Author: Claude (delegated worker, interactive session). Task: [TASK-01](TASK-01.md), published
by Root at `90f257ff` (SHA-256 `853a2311b088fcc3474f51ca80fa28ad007e348a4325bfe2d37e64056e9f270b`).
Code basis: `a65dcbf68143ce63af3b2205b0c36af02eaae0e8`.

This draft proposes IDs, playback semantics, authoritative inputs, lifecycle and budget rules and
asset dispositions for the ten planned sound events. It registers nothing, creates or imports no
audio and approves no asset. Values marked **OPEN** need a Root input or an owner decision.
[TEST-DESIGN-01](TEST-DESIGN-01.md) derives its cases from this text.

Terms used below:

- **Loop**: a client sound instance that repeats while its source stays active.
- **One-shot**: a client sound played once for one server-confirmed event.
- **Source key**: the identity of one emitting object on one client: Level key plus block position
  for a block entity, or Level key plus entity UUID for an entity.
- **Active input**: the immutable, server-produced value a client already receives (a block state
  property, a block entity update tag, synchronized entity data or a block event) that says a
  source is running.

## 1. Inputs

| Input | What it fixes |
| --- | --- |
| ADR-066 §7.1, §7.3, §8, §9 | ten real events; loops follow bounded S2C active state, clear on unload/config/resource reload, play only within client distance 32, at most 32 simultaneous ARCE loops per client prioritizing nearest; no per-audio-frame packets; `lathe`/`rolling_machine` may share one approved OGG; five silent placeholders stay rejected; NEW audio needs pre-authoring provenance, an audible waveform check and a manual listen; per-asset terminal evidence |
| ADR-061 §4.1-§4.5 | allowed sources; `REVIEW` assets become `IMPORT` only through a recorded origin finding in `docs/provenance/v1.8.0-origin-findings.json`; schema-2 batch records; importer `generate`/`verify` |
| ADR-062 §4 row "Silent placeholder sounds", ledger rows 606-620 | the five silent events are `REJECTED`; ten events `PLANNED` for C18d |
| ADR-055 §5 "Visuals" | the laser drill marker's update tag carries `active`; "no sound louder than a machine hum" |
| ADR-056 §6 (as implemented) | railgun visuals use block events, at most 8 concurrent effects per client |
| ADR-063 §6 | electric mushroom flashes: client-only, rain in a stormland, CLIENT switch, at most once every five seconds |
| docs/08 §9 | OGG decodable; mono or stereo fits the use; `sounds.json` IDs; volume and loop; source; no unlicensed recordings |
| Legacy manifest and content audit §3.8 | file names, sizes, SHA-256; `lathe` and `rollingMachine` are one file; no OGG names its author |
| Legacy Java, pinned `c5cd5af` | gravity controller loops its machine sound while running; railgun plays its bang on the fire event (§7) |
| Current code | `registry/ModSounds.java` registers only `ui_select`; `datagen/ModSoundDefinitionsProvider.java` writes only `ui_select`; the producers in §3 |

## 2. Event identities and semantics

IDs are those already fixed in ADR-066 §7.1 and the C18 coverage table
(`docs/work/v1.8.0-c18-contract/proposals/revision-03/unit-coverage.csv:96-105`). Note that
`gravityOhhh` maps to `gravity_controller`, not to a literal rename.

| Ledger unit | Modern event ID | Kind | Category (`SoundSource`) | Emitter |
| --- | --- | --- | --- | --- |
| `sound_event:airHissLoop` | `advancedrocketrycommunity:air_hiss_loop` | loop | `BLOCKS` | oxygen vent while supplying |
| `sound_event:basicLaserGun` | `advancedrocketrycommunity:basic_laser_gun` | loop while firing (**OPEN**: one-shot per break) | `PLAYERS` | basic laser gun user (C18b) |
| `sound_event:combustionRocket` | `advancedrocketrycommunity:combustion_rocket` | loop | `NEUTRAL` | rocket entity while its engines fire |
| `sound_event:ElectricShockSmall` | `advancedrocketrycommunity:electric_shock_small` | one-shot | `AMBIENT` | electric mushroom flash; other "arcs" OPEN |
| `sound_event:gravityOhhh` | `advancedrocketrycommunity:gravity_controller` | loop | `BLOCKS` | gravity field controller while active |
| `sound_event:laserDrill` | `advancedrocketrycommunity:laser_drill` | loop | `BLOCKS` | orbital laser drill controller while active |
| `sound_event:lathe` | `advancedrocketrycommunity:lathe` | loop | `BLOCKS` | lathe controller while processing (C16b) |
| `sound_event:MachineLarge` | `advancedrocketrycommunity:machine_large` | loop | `BLOCKS` | large classic machines while processing (which ones: OPEN, §3.8) |
| `sound_event:railgunBang` | `advancedrocketrycommunity:railgun_bang` | one-shot | `BLOCKS` | railgun on launch |
| `sound_event:rollingMachine` | `advancedrocketrycommunity:rolling_machine` | loop | `BLOCKS` | rolling machine controller while processing |

Each event:

- is registered with `SoundEvent.createVariableRangeEvent` (as `ui_select` is) and gets a
  `sounds.json` definition from `ModSoundDefinitionsProvider` with a subtitle key
  `subtitles.advancedrocketrycommunity.<id>` in `en_us` and `zh_cn`;
- loops set `attenuation_distance` 32 so the audible range matches the 32-block play radius;
  one-shots keep the default 16 unless a review asks otherwise;
- loops use non-streamed playback (`stream: false`): at most 32 loops play at once and the legacy
  files are 85-207 KB, so streaming would spend the client's stream channels without need;
- is registered only together with its approved audio file. An event whose file is not approved
  is not registered at all, so no client logs a missing-sound warning and no silent stand-in ships
  (ADR-066 §7.1). `lathe` and `rolling_machine` stay two event IDs even when their definitions
  point at one file.

## 3. Authoritative producers and client inputs

"Available" means the base commit already sends the active input to clients. "Missing" means a
producer or an input must be built first; the sound cannot be delivered before that.

### 3.1 `air_hiss_loop`: available

- Server: `OxygenVentBlockEntity.setStatus` sets the block state `LIT` to
  `status == VentOperatingStatus.ACTIVE` and updates clients only on change
  (`OxygenVentBlockEntity.java:229-244`).
- Client input: the `LIT` property of the vent block state at a loaded position.
- Loop while `LIT` is true. ADR-066 says "(supplied vent)": `ACTIVE` is the status in which the vent
  supplies oxygen; no client-side inference from particles or room state.

### 3.2 `basic_laser_gun`: missing (C18b)

- The tool does not exist yet. ADR-066 §5.3 makes its use server-authoritative (held target, 20
  valid continuous-use ticks per break, cooldown, reset rules) and forbids target data in packets.
- Required producer: a bounded server-maintained "firing" flag per player, true only while the
  server counts valid continuous use, sent to tracking clients only on change (for example as
  synchronized player-attached data or a fixed-size S2C message on an existing channel).
  The client's own use animation is not an active input. **OPEN (Root):** carrier and channel;
  any new message needs the network-protocol pin (ADR-061 §3.4).
- **OPEN (owner):** loop while firing (closest to a beam tool) or one-shot per completed break.

### 3.3 `combustion_rocket`: available

- Server: `RocketEntity` synchronizes `DISPLAYED_FLIGHT_STATE` as a `RocketFlightState` name
  (`RocketEntity.java:52-53, 154-156, 467-471`); states are `ASSEMBLED`, `FUELED`, `COUNTDOWN`,
  `ASCENT`, `TRANSIT`, `DESCENT`, `LANDED`, `FAILED_RECOVERABLE`, `DISASSEMBLED`.
- Client input: the decoded state; an unknown name decodes to no sound (the accessor's fallback
  must not invent an engine state).
- Loop while the state is `ASCENT` or `DESCENT`. **OPEN (owner):** whether `COUNTDOWN` plays an
  ignition loop. `TRANSIT` plays nothing (the rocket has left the Level).
- The existing server one-shots in `RocketFlightFeedback` (vanilla beacon, firework, anvil,
  enderman sounds) are separate events. **OPEN (Root):** keep them alongside the loop or remove the
  launch one-shot that the loop now covers. Changing them is not part of this contract.

### 3.4 `electric_shock_small`: available for the mushroom, OPEN for "arcs"

- Existing producer: `ElectricMushroomBlock.animateTick` calls the client-installed
  `Flash.maybeFlash` (`ElectricMushroomBlock.java:25-59`); `ClientExoplanetEffects.Flashes` flashes
  the sky and plays vanilla thunder only when the CLIENT switch is on, it is raining, the biome is
  stormland, a 1-in-40 roll passes and a cooldown allows (`ClientExoplanetEffects.java:55-72`).
- This is a client-only cosmetic by design (ADR-063 §6): there is no server state to follow, and
  nothing in gameplay depends on it. Proposal: on the same accepted flash, also play
  `electric_shock_small` once at the mushroom's position. It inherits the switch, the rain and biome
  conditions and the cooldown, so its rate is at most one per five seconds per client.
- The ledger note says "electric mushroom and arcs". **OPEN (Root):** which "arcs" (for example an
  arc furnace discharge) and their active input; none is assumed here.

### 3.5 `gravity_controller`: available

- Server: `GravityFieldBlockEntity.getUpdateTag` sends `active`, radius and multiplier; the client
  stores `active` in `handleUpdateTag` (`GravityFieldBlockEntity.java:374-392`).
- Loop while `active`. Legacy behaviour matches: `TileAreaGravityController.getSound()` returns
  `gravityOhhh` and the running controller calls `playMachineSound` (legacy lines 69-70, 148-152).

### 3.6 `laser_drill`: available

- Server: `OrbitalLaserDrillBlockEntity.getUpdateTag` sends `active` (`renderActive`), read back in
  `handleUpdateTag` (`OrbitalLaserDrillBlockEntity.java:529-546`). The marker
  (`LaserTargetBlockEntity`) has its own update tag (ADR-055 §5).
- Loop at the drill controller while `active`; one loop per drill. Proposal: no second loop at the
  marker, so one drill costs one of the 32 slots. Volume no louder than an ordinary machine hum
  (ADR-055 §5); the listen check in TEST-DESIGN records this.

### 3.7 `lathe`: missing (C16b)

- The lathe controller does not exist yet. Required input: the classic machine family's bounded
  client-visible "processing" flag (§3.10).

### 3.8 `machine_large`: missing, mapping OPEN

- Legacy: the event is real audio (`machinelarge.ogg`, 206,897 bytes, not the silent placeholder).
  The legacy tiles that import `AudioRegistry` and have no real sound of their own are the
  centrifuge, chemical reactor and precision laser etcher (crystallizer, cutting machine, arc
  furnace, electrolyser and precision assembler point to the silent file); their `getSound()` lines
  are not available locally (`legacy-manifest/dependency-imports.csv`).
- Proposal: `machine_large` for the C16c chemical reactor, laser etcher and centrifuge while they
  process. **OPEN (Root):** confirm from the pinned legacy bodies of those three tiles before
  freezing; machines whose legacy sound was silent get no ARCE loop (ADR-062 §4).

### 3.9 `railgun_bang`: available

- Server: `RailgunBlockEntity.launchedEffect` posts block event `EVENT_LAUNCH` (1), `claimed` posts
  `EVENT_ARRIVAL` (2); the client accepts them only if `RailgunEffects.start` grants one of its 8
  effect slots (`RailgunBlockEntity.java:200-227`, `RailgunEffects.MAX_CONCURRENT = 8`).
- One-shot on an accepted `EVENT_LAUNCH`, at the railgun, inside `triggerEvent`'s existing grant,
  so the effect cap also caps the sound. Legacy plays its bang only on the fire event (legacy
  `TileRailgun.java:511-515`). Proposal: no bang on `EVENT_ARRIVAL` (**OPEN**, owner taste).

### 3.10 `rolling_machine`: producer exists, input missing

- The v1.2 rolling machine runs processes, but no running state reaches nearby clients: its blocks
  have no `LIT`/`POWERED` property and its block entities have no update packet (the only
  `getUpdatePacket` implementations are the endgame, elevator and black-hole block entities).
  Menu data reaches only the player with the menu open and is not an active input.
- Required input, shared with §3.7 and §3.8: one bounded boolean "processing" per controller, set
  by the server when a process starts or resumes and cleared when it pauses, completes, unforms or
  unloads, sent only on change. **OPEN (Root):** carrier. Recommendation: a block entity update tag
  with one boolean (`getUpdateTag`/`handleUpdateTag`, as the gravity controller does), because a
  new block-state property would add a value to existing worlds' saved block states and to the
  generated blockstate files; the update tag changes neither.

## 4. Client lifecycle

### 4.1 Ownership and placement

- All playback code is physical-client only (`client/classicpresentation/audio/` as the handoff
  suggests); common and server code never reference `net.minecraft.client.*`.
- A pure, Minecraft-free scheduler decides which loops play; a thin client adapter turns its
  decisions into `SoundManager` calls. The pattern matches the existing pure
  `celestial/visual/AmbientController` with its `Playback` interface.
- No static mutable world collection: the scheduler instance is owned by the client event handler
  and reset on the events in §4.4.

### 4.2 Candidate collection (bounded)

- A source becomes a candidate when its active input turns true and stops being one when it turns
  false, its block entity or entity unloads, or its Level is no longer the client Level. Block
  entities report changes from `handleUpdateTag`/block-state updates/`triggerEvent`; entities from
  their data-change callback. No scan of the world or of all loaded chunks is made.
- The candidate set holds at most 256 sources per client. When full, a new candidate replaces the
  farthest existing one only if it is nearer; otherwise it is dropped and counted. Dropping is safe:
  the set is a presentation cache, and a later state change re-offers the source.

### 4.3 Selection (deterministic)

- Re-evaluate every 10 client ticks and immediately on a candidate change, never per audio frame or
  per render frame.
- Eligible: candidates whose source position is within 32 blocks (squared distance ≤ 1,024, using
  the listener position) in the client Level.
- Order: squared distance ascending; ties by event ID (ASCII), then source key (block position
  x, y, z ascending, or entity UUID). The first 32 eligible sources play; the rest are silent.
- Hysteresis: a playing source keeps its slot until its distance exceeds 34 blocks or a nearer
  source needs the slot at a re-evaluation, so a player standing at 32 blocks does not hear the loop
  restart every evaluation.
- At most one loop instance per source key. A source that plays two events (none proposed) would
  need its own rule.

### 4.4 Start, stop, pause and clear

| Event | Effect |
| --- | --- |
| active input false, source unloaded or removed | stop that source's loop at the next tick |
| client Level change, disconnect, `ClientPlayerNetworkEvent.LoggingOut` | stop all ARCE loops, clear candidates |
| resource reload (`RegisterClientReloadListenersEvent` listener) | stop all ARCE loops; re-evaluate after reload completes (vanilla sound reload already stops instances) |
| CLIENT config reload affecting audio (none proposed now) | stop all and re-evaluate |
| game paused in single player | vanilla pauses sounds; the scheduler does not tick while paused, as `AmbientController` documents |
| sound category volume 0 | vanilla mutes; the scheduler still caps instances so unmuting is bounded |

Each loop instance is a tickable sound that also checks, every tick, that its source key is still
selected; if not, it stops itself. This covers a missed callback without trusting it.

### 4.5 One-shots

`railgun_bang` and `electric_shock_small` are bounded by their producers (8 railgun effect slots,
one flash per five seconds) and are not counted in the 32-loop cap. A `basic_laser_gun` one-shot,
if chosen, is bounded by the server cooldown (one break per 20 valid ticks).

## 5. Network and authority

- No new per-frame or per-tick packets. Loops follow existing state-change updates; one-shots follow
  existing block events. The only proposed new server output is the processing flag of §3.10 (one
  boolean per controller, sent on change) and the laser gun flag of §3.2.
- No client decision reaches the server. A sound never changes a machine, a rocket, an atmosphere
  value or a player.
- Missing, unknown or out-of-range inputs mean silence, never a guessed state.

## 6. Configuration

ADR-066 §7.2 adds no audio settings. The vanilla sound categories are the player's volume
controls. `electric_shock_small` inherits `effects.electricMushroomFlashes`. **OPEN (owner):**
whether `effects.advancedVisuals=false` should also suppress loops; this draft proposes no.

## 7. Assets and provenance

Candidate files from the pinned legacy tree (`legacy-manifest/assets.csv`):

| Event | Legacy file | Bytes | SHA-256 | Current disposition |
| --- | --- | ---: | --- | --- |
| `air_hiss_loop` | `sounds/airhissloop.ogg` | 117,459 | `f22364b53b66314e5377aee1d85bdfdc883dea76bfa67fba0c27ba2bea912f72` | `REVIEW` |
| `basic_laser_gun` | `sounds/basiclasergun.ogg` | 10,781 | `00c97b7f80b8a6204cb999cb68516a67b286350be06a4fa901d0b6819395efc2` | `REVIEW` |
| `combustion_rocket` | `sounds/combustionrocket.ogg` | 135,062 | `18a34e864d1b56bd937f04498d8e21cf4f82d12147d892fbe9a6e2a0efcab01f` | `REVIEW` |
| `electric_shock_small` | `sounds/electricshocksmall.ogg` | 11,837 | `004051efb25324f970a607de71b02379131027c461625fa160847200880a384b` | `REVIEW` |
| `gravity_controller` | `sounds/gravityohhh.ogg` | 11,333 | `0d70f4fd0d43d94d9c2ec92367cb5334f7f484ba7aeb896a4ce3a79cd96dde64` | `REVIEW` |
| `laser_drill` | `sounds/laserdrill.ogg` | 84,739 | `dbe75ef85cd9191a024e2bffaa0a13a691a8d9d0cc8d5ca311d063ebab4a64e5` | `REVIEW` |
| `lathe`, `rolling_machine` | `sounds/lathe.ogg` = `sounds/rollingmachine.ogg` | 129,547 | `673d70feacea2d3a1031ae13d960879ef79b7eca3539e75a2322fb1190d75a32` | `REVIEW` |
| `machine_large` | `sounds/machinelarge.ogg` | 206,897 | `05308c20260a3e6fdd995f620a06d7cdb5e709a274dcf88e7598fc84c0c637e1` | `REVIEW` |
| `railgun_bang` | `sounds/railgunbang.ogg` | 15,220 | `5b63dfc455678bd85237c64cd2d029dcd03997015be8c12b0197ae5f6004e002` | `REVIEW` |

Rejected and excluded, unchanged: `dummy.ogg` and the five files byte-identical to it
(`f33cbdaceb70c53c2b017300dbef85e217d92b896cd8941b4172c9b3c04c3ba3`); legacy `sounds.json`
(`REGENERATE`, DataGen writes the modern file).

Per event, exactly one terminal path (ADR-066 §7.3):

1. **Import:** an origin finding with decision `CLEARED` for that file in
   `docs/provenance/v1.8.0-origin-findings.json` (no sound entry exists at the base commit), then the
   ADR-061 importer with a byte copy or a recorded transformation, a schema-2 batch record, source
   and target SHA-256, maintainer review. The absence of a Vorbis author comment is not a clearance.
   `lathe.ogg` is imported once; both definitions reference it.
2. **NEW replacement:** if the finding is `EXCLUDED`, or if no finding is made, a community-authored
   file with a provenance record written before authoring (tool, method, author, license MIT, no
   third-party samples), then the audible waveform check and a manual listen (TEST-DESIGN §4).
   A silent or near-silent file is not delivery.

Format checks for either path (docs/08 §9): Ogg Vorbis that the client decodes; mono for every
positional sound, because vanilla does not attenuate stereo sources (a stereo legacy file needs a
recorded mono-downmix transformation or a NEW replacement); a seamless loop point for loops; peak
level not louder than vanilla machine-like references. The channel count of the legacy files is
not known to this author (no audio file was opened, per TASK-01).

The v0.1.0 import of `buttonblipa.ogg` as `ui_select.ogg`
(`docs/provenance/v0.1.0-minimal-content.json:46-50`) predates the ADR-061 `REVIEW` rule and is not
a precedent for clearing these ten files.

The asset-review assignment gives the ten `REVIEW` OGG rows to Claude
(`docs/work/v1.8.0-claude-asset-review-assignment.csv:12-21`). That evidence work is a separate task;
this contract only states what it must produce.

## 8. Limits summary

| Limit | Value | Source |
| --- | ---: | --- |
| play radius for loops | 32 blocks | ADR-066 §7.1 |
| simultaneous ARCE loops per client | 32 | ADR-066 §7.1 |
| candidate set per client | 256 | this draft |
| re-evaluation interval | 10 client ticks, plus on change | this draft |
| stop hysteresis | 34 blocks | this draft |
| railgun one-shots | 8 concurrent effects | `RailgunEffects.MAX_CONCURRENT` |
| mushroom one-shots | one per flash cooldown (5 s) | ADR-063 §6 |
| new network traffic | state changes only | ADR-066 §7.1 |

## 9. Root integration surfaces (future, not authorized here)

1. `ModSounds`: one registration per event, added only with its approved file.
2. `ModSoundDefinitionsProvider`: definitions, subtitles, `attenuation_distance`, `stream`.
3. Language: subtitle keys in `en_us`, `zh_cn` (C18 language output).
4. Server state: the processing flag (§3.10) for the rolling machine and the classic family; the
   laser gun flag (§3.2); both with GameTests.
5. Client: the audio package, its registration in the client bootstrap/global event entry, a reload
   listener.
6. Assets: origin findings, importer records or NEW provenance, the resource validator's
   definition → OGG check (ADR-061 §5.2).
7. Ledger: each row moves with its own delivery record; rows whose producer is missing stay
   `PLANNED`.

## 10. Open items

| ID | Question | Owner |
| --- | --- | --- |
| A-OPEN-1 | Origin finding per OGG (or NEW replacement) | owner or named independent reviewer |
| A-OPEN-2 | Laser gun: loop or one-shot; flag carrier | owner; Root |
| A-OPEN-3 | Rocket: loop in `COUNTDOWN`; keep or drop existing vanilla one-shots | owner; Root |
| A-OPEN-4 | "Arcs" producers for `electric_shock_small` | Root |
| A-OPEN-5 | `machine_large` machines, from the legacy tile bodies | Root |
| A-OPEN-6 | Railgun bang on arrival | owner |
| A-OPEN-7 | Processing-flag carrier (update tag recommended) | Root |
| A-OPEN-8 | `advancedVisuals=false` and loops | owner |
| A-OPEN-9 | Mono/stereo and loop seams of any imported file | asset task |

Delivery order follows producers: `air_hiss_loop`, `gravity_controller`, `laser_drill`,
`railgun_bang`, `combustion_rocket` and the mushroom part of `electric_shock_small` need only their
asset; `rolling_machine` needs §3.10; `lathe` and `machine_large` need C16b/C16c; `basic_laser_gun`
needs C18b. This draft passes no build, listening, V1, V2 or v1.8 G0-G9 Gate.
