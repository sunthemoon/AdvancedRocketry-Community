# CL18D-AUDIO-02: ten classic sound events, successor contract draft 02

Date: 2026-10-08. Milestone: v1.8.0 / C18d. Status: **proposed, not frozen**.
Author: Claude (delegated worker, owner-started interactive session). Task:
[TASK-02](TASK-02.md), read in the Root checkout at `65dd821180f6c0304340fc51d8d1d11df6d29347`
(SHA-256 `2ea0d7b85b701c5c3f60edafd2b64afe7717ad716eaf0e44b0ebc5d5a743f92f`).
Worktree base `a65dcbf68143ce63af3b2205b0c36af02eaae0e8`; Root source checkpoint
`f9f2d9d2c5eb0de2c9f5d28160ab7804fc57eaef`. Between them only HUD, client configuration and their
tests changed under `src/`, so every producer line cited here has the same bytes at both.

This draft replaces [CONTRACT-01](CONTRACT-01.md) as the proposal under review; draft 01 stays
unchanged. It registers nothing, creates or imports no audio, approves no asset and freezes no new
server carrier, rate allowance, placeholder sound or registration. **OPEN** items stay with the
named owner. The review input is
[REPORT-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-audio-independent-20261008-01/REPORT-01.md),
SHA-256 `762c1b5e523cee672cdfb4363e1f5462237448a8a64d91ef936b096bb674eb92` (seven Medium findings).

## 1. Disposition of the review findings

| Finding | Disposition | Where |
| --- | --- | --- |
| M1 hysteresis kept loops beyond 32 blocks | **Addressed.** Start only within 30 blocks, stop beyond 32, and police every playing loop's distance every client tick. No loop is ever selected or playing beyond 32. | §4.3, §4.5 |
| M2 dropped candidates never come back | **Addressed.** A larger bounded registry of known active sources with a complete comparator for admission and eviction, plus a bounded neighbourhood re-offer sweep whenever anything was dropped. | §4.2 |
| M3 missed invalidation and stale sessions | **Addressed.** Client Level instance generation, per-entry object binding, per-tick validation of playing loops against the loaded object and its current synchronized state, round-robin validation of the rest, and a defined callback, reload and thread order. No new server packet or lease. | §4.4, §4.6 |
| M4 no aggregate work bound | **Addressed.** Callbacks only enqueue into a bounded per-tick buffer; one deterministic selection and one command diff per tick at a single publication point; explicit per-tick work counters. | §4.5, §4.7 |
| M5 railgun visual slots do not bound audio | **Addressed.** One-shots get their own caps counted on actual sound-instance lifetime, plus a hard cut-off age. Simultaneous caps and producer rate limits are stated separately. | §5 |
| M6 laser-drill oracle wrong | **Addressed.** The input is the controller's `active` update-tag value, which is `running && status == OK`; a blocked running drill is silent; unload is a client-side cleanup, not an expected false update. The producer is not changed. | §3.6 |
| M7 audio-01 packet resealed in place | **Acknowledged; not repairable.** The draft-01 leaf stays untouched. HANDOFF-02 separates what can be verified today from the overwritten historical bytes. This draft's leaf uses an append-only sealer. | HANDOFF-02 |

The review's other notes are taken up as follows: the readiness wording (§3 opening), registration
permanence, NEW provenance timing, the grandfathered `ui_select` import and importer transformation
support (§6), invalid or missing replacement resources and bounded diagnostics (§4.8), the existing
`endgame` protocol not being a broadcast carrier (§7), and `advancedVisuals` (§8).

## 2. Event identities and semantics

Unchanged from draft 01 §2 except where marked. IDs are fixed by ADR-066 §7.1.

| Ledger unit | Event ID | Kind | `SoundSource` | Emitter |
| --- | --- | --- | --- | --- |
| `sound_event:airHissLoop` | `advancedrocketrycommunity:air_hiss_loop` | loop | `BLOCKS` | oxygen vent while `LIT` |
| `sound_event:basicLaserGun` | `advancedrocketrycommunity:basic_laser_gun` | **OPEN (owner)**: loop while firing or one-shot per break | `PLAYERS` | C18b tool |
| `sound_event:combustionRocket` | `advancedrocketrycommunity:combustion_rocket` | loop | `NEUTRAL` | rocket entity in `ASCENT` or `DESCENT` |
| `sound_event:ElectricShockSmall` | `advancedrocketrycommunity:electric_shock_small` | one-shot | `AMBIENT` | accepted electric-mushroom flash; "arcs" OPEN |
| `sound_event:gravityOhhh` | `advancedrocketrycommunity:gravity_controller` | loop | `BLOCKS` | gravity field controller while `active` |
| `sound_event:laserDrill` | `advancedrocketrycommunity:laser_drill` | loop | `BLOCKS` | laser drill controller while `active` |
| `sound_event:lathe` | `advancedrocketrycommunity:lathe` | loop | `BLOCKS` | C16b lathe while processing |
| `sound_event:MachineLarge` | `advancedrocketrycommunity:machine_large` | loop | `BLOCKS` | machines **OPEN (Root)** |
| `sound_event:railgunBang` | `advancedrocketrycommunity:railgun_bang` | one-shot | `BLOCKS` | railgun launch |
| `sound_event:rollingMachine` | `advancedrocketrycommunity:rolling_machine` | loop | `BLOCKS` | rolling machine while processing |

`lathe` and `rolling_machine` stay two event IDs even if both definitions name one approved file.
The five silent placeholder events stay rejected (ADR-062 §4).

## 3. Producers and client inputs

Existing authority data is reusable input only. **No event is asset-only**: every one of the ten
still needs a physical-client binder (§4.6), the scheduler, an approved asset, registration and
DataGen, and executed tests before delivery.

| Event | Present at both fixed commits | Missing before delivery |
| --- | --- | --- |
| `air_hiss_loop` | `OxygenVentBlockEntity.setStatus` projects `ACTIVE` to block state `LIT` and updates clients on change (`OxygenVentBlockEntity.java:229-244`) | binder for initial state, state change and unload |
| `basic_laser_gun` | nothing | C18b tool; owner kind; server-authoritative valid-use summary, its tracking/reset carrier and protocol review (Root) |
| `combustion_rocket` | synchronized `DISPLAYED_FLIGHT_STATE` string (`RocketEntity.java:52-53`), refreshed by `refreshSyncedData` (`:455-474`); `displayedFlightState()` maps an unknown name to `FAILED_RECOVERABLE` (`:154-160`) | binder for join, state change and leave; COUNTDOWN and existing vanilla one-shot feedback (owner/Root) |
| `electric_shock_small` | client-only `Flashes.maybeFlash` with a 1-in-40 roll, rain, stormland biome, CLIENT switch and `FlashCooldown` of 100 ticks per client world (`ClientExoplanetEffects.java:55-72`, `FlashCooldown.java:8-27`); allowed client cosmetic (ADR-063) | hook on the accepted flash; approved asset; "arcs" producer (Root) |
| `gravity_controller` | `active` in the update tag; activation and deactivation call `sendBlockUpdated` (`GravityFieldBlockEntity.java:233-264, 374-392`); packets reach `handleUpdateTag` through `EndgameDeviceBlockEntity.onDataPacket` (`EndgameDeviceBlockEntity.java:146`) | binder; client unload cleanup |
| `laser_drill` | §3.6 | binder; client unload cleanup |
| `lathe` | nothing | C16b producer and the Root-owned processing state |
| `machine_large` | a non-silent legacy file exists; which machines used it is not established | Root mapping from the pinned tile bodies; C16c producer and processing state |
| `railgun_bang` | block events `EVENT_LAUNCH` (1) and `EVENT_ARRIVAL` (2) posted on launch and claim; the client grants a visual slot in `triggerEvent` (`RailgunBlockEntity.java:202-227`) | §5 audio admission; arrival bang (owner) |
| `rolling_machine` | the v1.2 controller runs processes; no update tag or block-state value reaches nearby clients; menu data is not nearby-client authority | the Root-owned processing state (TASK-02: "The separate processing-state carrier remains Root-owned") |

### 3.6 `laser_drill` input

The controller sets `renderActive = running && status == EndgameCode.OK`, sends a block update only
when it changes, and writes it as `active` in the update tag
(`OrbitalLaserDrillBlockEntity.java:519-538`). Therefore:

- a running drill with any stop code is silent; the loop plays only for `active == true`;
- a stop or a status change sends `active == false` and the loop stops at the next publication;
- server-side `unloadRuntime()` releases admission and the structure index without resetting
  `renderActive` or sending an update (`:410-431`). The client stops the loop because the client
  block entity is removed or its chunk unloads (§4.4, §4.6), never because it expects a false
  update tag. The shared producer is not changed for audio.

The marker block gets no second loop: one drill costs one loop slot.

## 4. Client loop lifecycle

### 4.1 Ownership

Physical-client code only. A Minecraft-free scheduler (pure Java, like
`celestial/visual/AmbientController`, which also detects a world change by object identity) makes
every decision; a thin adapter turns decisions into `SoundManager` calls and reads loaded objects for
validation. The scheduler instance belongs to the client event handler; there is no static mutable
world collection.

### 4.2 Registry of known active sources (bounded) and re-offer

- **Entry**: source key (block: client Level generation plus `BlockPos`; entity: generation plus
  entity UUID), event ID, last known position, binding (a weak reference to the client block
  entity or entity object), generation.
- **Capacity 1,024 entries.** An `offer(active=true)` for a key already present updates it; an
  `offer(active=false)` or `remove` deletes it.
- **Complete comparator** used for admission, eviction and selection: squared distance from the
  current listener position ascending, then event ID (ASCII), then source key (blocks before
  entities; blocks by x, y, z ascending; entities by UUID most-significant then least-significant
  bits, unsigned).
- **Admission when full**: compare the new source with the registry's last entry under the
  comparator. If the new one sorts earlier, evict that last entry; otherwise drop the new one. Either
  way set `overflowed` and count `dropped`.
- **Re-offer sweep**: while `overflowed` is set, the adapter examines loaded client block entities
  and tracked entities inside the axis-aligned cube of half-size 32 blocks around the listener, at
  most 128 objects per client tick, continuing a cursor over the chunk columns that intersect the cube
  in x-then-z order. Each examined object whose current active input is true is offered again. When a
  complete pass finishes with no drop and the registry holds at most 768 entries, `overflowed`
  clears. Listener movement restarts the pass from the new cube.
- Consequence: a source dropped at 257 blocks while the listener stood elsewhere is offered again by
  the sweep once the listener walks within 32 blocks, without any state change at the source. No scan
  of all loaded chunks is made.

### 4.3 Selection and distance

- Listener position: the main camera position read at the publication point (§4.5). Source position:
  block centre for blocks, the entity position read at the publication point for entities.
- **Eligible** for a slot: a playing source with squared distance ≤ 1,024 (32 blocks), or a source
  not playing with squared distance ≤ 900 (30 blocks). Start threshold 30, stop threshold 32: a
  listener standing between them neither starts nor restarts the loop repeatedly.
- The first 32 eligible entries under the comparator are selected. At most one loop instance per
  source key.

### 4.4 Validity (no reliance on a callback arriving)

- **Generation.** The adapter keeps the identity of the current `ClientLevel` object (as
  `AmbientController` does). A different object, `ClientPlayerNetworkEvent.LoggingOut`, `Clone`
  (respawn or dimension change) or a missing level increments the generation, stops every ARCE sound
  and clears the registry. A Level `ResourceKey` is never used as identity, so two client Level
  instances of one dimension (reconnect, respawn) never share entries.
- **Stale input.** An offer or removal carrying an older generation is ignored.
- **Playing loops are validated every client tick** (at most 32): the binding is not cleared, the
  object is not removed, it belongs to the current client Level, its chunk is loaded, for blocks
  `level.getBlockEntity(pos)` is the same object (a replaced block entity is stale), its current
  synchronized active input is true (read from the object: block state `LIT`, the `active` field,
  the decoded entity state), and the squared distance is ≤ 1,024. Any failure stops the loop at this
  tick's publication and removes the entry.
- **Other entries** are validated round-robin, 64 per client tick in comparator order of a snapshot
  taken when the round starts; an invalid entry is removed. A missed false or unload callback is
  therefore recovered within one tick for a playing loop and within 16 ticks for any entry.

### 4.5 Ingestion, publication point and order within a tick

1. Binder callbacks never call the scheduler's selection. They append `(generation, kind, key,
   event, position, binding, active)` to a **change buffer of 512 events per tick**. When full,
   later events of that tick are dropped, `overflowed` is set (the sweep recovers them) and `dropped`
   counts.
2. At `TickEvent.ClientTickEvent` phase `END`, in this order:
   1. generation check (§4.4);
   2. drain the buffer in arrival order, applying each event to the registry (last event per key
      wins), with the admission rule of §4.2;
   3. validation of playing loops and the round-robin slice (§4.4);
   4. one sweep step if `overflowed`;
   5. selection, when the buffer was not empty, a playing loop was removed, the listener moved at
      least one block since the last selection, or 10 ticks passed since the last selection;
   6. publication: stop every playing loop that is no longer selected, in ascending source-key
      order; then start every newly selected loop in selection order, skipping events that are
      unavailable (§4.8).
3. All binder callbacks run on the client main thread: S2C block, block entity, entity-data and
   block-event packets are handled there (vanilla `PacketUtils.ensureRunningOnSameThread`), and the
   Forge client tick, level and entity events fire there. The scheduler and adapter are main-thread
   only; a call from another thread is dropped and counted.
4. Within one tick the commands depend only on the registry after step 2.2 and the listener
   position, so any order of same-tick events for **different** keys gives identical commands (when
   nothing overflowed). Events for one key are applied in arrival order.

### 4.6 Binder ports (Root dependencies)

The adapter needs these client callbacks per producer; Root chooses the mechanism. Each must
offer the current state, so the order in which a chunk's block entity, its first update tag and its
block state arrive does not matter: validation (§4.4) reads the object anyway.

| Producer | Initial state | Change | End |
| --- | --- | --- | --- |
| vent (`LIT`) | client block entity loaded with its block state | block state change at the position | client block entity removed or chunk unloaded |
| gravity controller, laser drill (`active`) | first `handleUpdateTag` / chunk data | `handleUpdateTag` from `onDataPacket` | as above |
| rocket entity | client entity join | synchronized-data change (an `onSyncedDataUpdated` override or bounded polling of registered rockets, at most 256 per 10 ticks) | entity leave (`ClientRocketEvents` already listens to `EntityLeaveLevelEvent`) |
| railgun | — | accepted `EVENT_LAUNCH` in `triggerEvent` | — |
| mushroom | — | accepted flash in `Flashes.maybeFlash` | — |
| processing machines | Root-owned processing state | same | same |

### 4.7 Per-tick work bound

Per client tick at most: 512 buffered events applied; 32 playing-loop validations plus 64
round-robin validations; 128 sweep examinations; one selection over at most 1,024 entries
(distance computation and a sort of the eligible entries); 32 stops plus 32 starts. The adapter
keeps counters `events_applied`, `events_dropped`, `validations`, `sweep_examined`, `selection_runs`,
`starts`, `stops`, `dropped`, and the debug screen or a test hook can read them. A burst of N
changes in one tick costs one selection, not N.

### 4.8 Reload, pause, missing resources and diagnostics

| Event | Effect |
| --- | --- |
| resource reload begins (the reload listener's `reload` call, main thread) | stop every ARCE sound; set `reloading`; no starts |
| reload listener `apply` completes (main thread) and no loading overlay remains | clear `reloading`; increment the resource generation; the next publication restarts selected loops |
| single-player pause | the scheduler does not tick (as `AmbientController` documents); vanilla pauses sounds |
| category volume 0 | vanilla mutes; caps still apply |
| a registered event whose sound resolves to no `WeighedSoundEvents` (replacement pack missing or invalid) | the event is unavailable for this resource generation: not started; one log line per event and generation, at most 10 lines per generation, then a counter |

The ordering of vanilla `SoundManager`'s own reload against ours is a Root dependency: the rule
"no starts until our apply completed and no overlay remains" must be confirmed against Forge 1.20.1.

## 5. One-shots: caps on actual sound lifetime

| Event | Admission | Simultaneous cap (actual instances) | Hard cut-off | Producer rate |
| --- | --- | ---: | ---: | --- |
| `railgun_bang` | accepted `EVENT_LAUNCH` with a visual grant, listener within 32 blocks | 8 | 40 ticks | at most `RailgunSettings.MAX_LAUNCHES_PER_TICK` = 4 launches per tick (`RailgunSettings.java:10`) |
| `electric_shock_small` | accepted flash, at the mushroom position | 1 | 40 ticks | at most one flash per 100 ticks per client world |
| `basic_laser_gun`, if owner chooses one-shot | server-confirmed break | 4 | 40 ticks | server cooldown (C18b) |

- An instance counts while `SoundManager.isActive(instance)` is true; the adapter checks its own
  one-shot instances each tick (at most 13). Each tick it first applies cut-offs and forgets ended
  instances, then admits new one-shots. When the cap is reached, a new one-shot is dropped and
  counted; it is not queued.
- The cut-off stops any one-shot 40 ticks after its start, so a long asset cannot hold a slot for
  longer, whatever the visual slot (12 ticks, `RailgunBlockEntity.java:49`) does.
- The visual grant is still required for a bang, so a bang never plays for an effect the client
  did not show. The visual slots alone do not bound audio (the review's tick 0/1/13 sequence grants
  nine visuals within one second); the audio cap does.
- One-shots are not counted in the 32-loop cap. Total ARCE instances per client are at most
  32 + 8 + 1 (+ 4 if the laser gun is a one-shot).
- Arrival bang: owner question (A-OPEN-6); not proposed.

## 6. Registration, assets and provenance

- An event is registered in Root-owned `ModSounds` and `ModSoundDefinitionsProvider` only together
  with its approved functional asset. From the first commit that registers an ID, ADR-061 §1.4
  permanence applies (rename by `MissingMappingsEvent` remap and an ADR revision; removal by world
  scan and migration).
- Definitions as draft 01 §2: subtitles in `en_us` and `zh_cn`; loops `attenuation_distance` 32,
  `stream: false`.
- Terminal path per event (ADR-066 §7.3, ADR-061 §4): **Import** after an origin finding with
  decision `CLEARED` in `docs/provenance/v1.8.0-origin-findings.json`, a schema-2 batch record,
  reviewer and allowlist rules; or **NEW** with a provenance record written **before authoring
  begins** (not merely before the file's first commit), an audible waveform check and a manual
  listen. A silent file is never delivery.
- Mono for positional sounds. A downmix or any other transformation needs the importer to support
  and record it; that support is not assumed and needs its own review.
- The v0.1.0 `ui_select` import stays as approved; the rules above do not reopen it.
- All ten candidate files remain `REVIEW`; their paths, sizes and SHA-256 are in draft 01 §7 and
  were confirmed by the review against `legacy-manifest/assets.csv`.

## 7. Network and authority

No new C2S message, no per-frame or per-tick packet, no client decision that reaches the server.
Existing block-state, block-entity and entity-data transport needs no channel version change. The
`endgame` channel's `EndgameDeviceView` answers only the requesting player and is not a nearby-client
broadcast carrier (`src/test/resources/network-protocols.txt` pins `endgame` 1). Any new S2C
message for the laser gun or processing state needs an exact bounded payload, recipients,
freshness and reset rules and protocol coordination before source work; none is proposed here.

## 8. Configuration

No new setting. `electric_shock_small` follows `effects.electricMushroomFlashes` because it plays on
the accepted flash. `effects.advancedVisuals` is a visual scope in ADR-066; this draft does not
assume it silences audio (A-OPEN-8).

## 9. Limits

| Limit | Value | Source |
| --- | ---: | --- |
| loop start radius | 30 blocks (squared 900) | this draft |
| loop stop radius, hard | 32 blocks (squared 1,024), checked every tick | ADR-066 §7.1 |
| simultaneous ARCE loops per client | 32 | ADR-066 §7.1 |
| registry of known active sources | 1,024 entries | this draft |
| change buffer | 512 events per tick | this draft |
| validations per tick | 32 playing + 64 round-robin | this draft |
| sweep examinations per tick | 128 | this draft |
| one-shot caps | 8 / 1 / 4, cut-off 40 ticks | §5 |
| resource diagnostics | 10 lines per resource generation | §4.8 |

## 10. Root integration surfaces (future, not authorized)

Registration and definitions with approved assets; language keys; the client audio package, its
event registration and reload listener; the binder ports of §4.6; the processing state for rolling
machine, lathe and the `machine_large` machines (Root-owned); the laser-gun summary (C18b);
origin findings or NEW provenance; ledger rows moved one by one with their delivery records.

## 11. Open items

| ID | Question | Owner |
| --- | --- | --- |
| A-OPEN-1 | origin finding or NEW file per OGG | owner or named independent reviewer |
| A-OPEN-2 | laser gun loop or one-shot; summary carrier | owner; Root |
| A-OPEN-3 | rocket COUNTDOWN loop; keep or drop existing vanilla feedback | owner; Root |
| A-OPEN-4 | "arcs" producers | Root |
| A-OPEN-5 | `machine_large` machines from the pinned tile bodies | Root |
| A-OPEN-6 | railgun bang on arrival | owner |
| A-OPEN-7 | processing-state carrier for classic and v1.2 machines | Root (TASK-02) |
| A-OPEN-8 | `advancedVisuals=false` and audio | owner |
| A-OPEN-9 | channels, seams and any transformation of imported files | asset task |
| A-OPEN-10 | binder mechanism per §4.6 row, including rocket state change | Root |
| A-OPEN-11 | reload ordering against vanilla `SoundManager` (§4.8) | Root |
| A-OPEN-12 | numeric proposals of §9 (30/32, 1,024, 512, 128, 8/1/4, 40) | Root review |

This contract passes no build, listening, V1, V2 or v1.8 G0-G9 Gate.
