# W346 design: B6.5's native case at the source-save boundary

This addendum is committed before any W346 code. Authority is [TASK-W346](../TASK-W346-B65-NATIVE-SAVE-BOUNDARY.md) under
Root decision R415. It builds on [W342-DESIGN](W342-DESIGN.md) section 4 (the native assessment) and [W342](W342.md).
The contract is the B6 row of [W27A section 9](W27A-DESIGN.md).

## 1. Sources

- The mapped Forge 47.4.10 jar, `forge-1.20.1-47.4.10_mapped_official_1.20.1.jar` from the local ForgeGradle cache,
  SHA-256 `95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
- `javap -p -c -l` of eleven classes and `javap -p -v` of `ServerLevel`, and a constant-pool scan of the whole jar for
  every reference to a save or unload method. All are under `D:/GitHub/ARCE-Task-Evidence/root-queue/W346/javap/`;
  `excerpts.txt` holds each cited method body with its line number in its `<Class>-javap.log`. Below, "ChunkMap:1264"
  means line 1264 of `ChunkMap-javap.log`. W338's logs (`root-queue/W338/vanilla-*-javap.log`) agree where they overlap.
- The constant-pool scan (`callers-forge.json`) lists every class of the jar that references `ServerLevel.save`,
  `ServerLevel.noSave`, `ServerChunkCache.save`, `ChunkMap.saveAllChunks`, `ChunkMap.processUnloads`, the entity manager's
  `saveAll`, `autoSave`, `close`, `tick`, `storeChunkSections`, `processChunkUnload` and `updateChunkStatus`,
  `EntityPersistentStorage.storeEntities`, `DimensionDataStorage.save`, `MinecraftServer.saveAllChunks` and
  `saveEverything`. Nine classes do: `MinecraftServer`, `SaveAllCommand`, `SaveOffCommand`, `SaveOnCommand`, `ChunkMap`,
  `ServerChunkCache`, `ServerLevel`, `WorldUpgrader` and `PersistentEntitySectionManager`.

## 2. The four questions

### 2.1 Which paths store a `noSave` Level's chunks or entity sections while the server is up

- **The Level save.** `MinecraftServer.saveAllChunks` calls `level.save(null, flush, level.noSave && !forced)` for each
  Level (MinecraftServer:1319-1331). `ServerLevel.save` returns at once when its third argument is true
  (ServerLevel:2212-2213, `ifne 90` to the `return`). Otherwise it calls `saveLevelData`, `ServerChunkCache.save(flush)`,
  then the entity manager's `saveAll()` (flush) or `autoSave()`, then posts Forge's `LevelEvent.Save`
  (ServerLevel:2220-2245).
- **Chunk unloads and the incremental chunk save.** `ChunkMap.tick` calls `processUnloads` only when
  `!level.noSave()` (ChunkMap:1262-1268). `processUnloads` is the only caller of `scheduleUnload` (which stores the
  chunk and unloads its block entities) and of `saveChunkIfNeeded` (up to 20 chunks per tick) (ChunkMap:1377, 1425).
  Its one other caller is `ChunkMap.saveAllChunks(true)` (ChunkMap:1214), reached only through `ServerChunkCache.save`.
  So while `noSave` is set, no terrain chunk of that Level is stored or unloaded.
- **The entity manager's unload processing: not stopped by `noSave`.** `ServerLevel.tick` calls the entity manager's
  `tick()` unconditionally at its end (ServerLevel:663-667), and `tick()` runs `processPendingLoads` and
  `processUnloads` (PersistentEntitySectionManager:747-753). `processUnloads` calls `processChunkUnload` for every
  queued chunk whose visibility is HIDDEN (PersistentEntitySectionManager:1235-1248), which stores the chunk's entity
  sections through `storeChunkSections` and `EntityPersistentStorage.storeEntities` (PersistentEntitySectionManager:654,
  557 and 578). Nothing on this path reads `noSave`. A chunk is queued when `updateChunkStatus` receives HIDDEN
  (PersistentEntitySectionManager:438-450). That status comes from the chunk holder: `ServerLevel` passes
  `entityManager::updateChunkStatus` as the chunk map's status listener (`ServerLevel-javap-verbose.log`,
  BootstrapMethods entry 2), and `ChunkHolder.demoteFullChunk` reports a demotion through
  `ChunkMap.onFullChunkStatusChange` (ChunkHolder:1136, ChunkMap:4277-4283). Ticket expiry and distance updates run every
  tick before the guarded `ChunkMap.tick` (ServerChunkCache:1000-1021: `purgeStaleTickets`, `runDistanceManagerUpdates`,
  then `ChunkMap.tick`). **So when a chunk of a `noSave` Level loses the tickets that keep it FULL, its entities are
  stored even though the chunk itself stays in memory.**
- **The stop.** `stopServer` first sets `noSave = false` on every Level (MinecraftServer:1515-1530), then calls
  `saveAllChunks(false, true, false)` and closes each Level; `ServerLevel.close` closes the entity manager, whose `close`
  calls `saveAll` (ServerLevel:5454-5457, PersistentEntitySectionManager:861). A clean stop therefore saves a held
  Level in full. Only a hard kill keeps the gap, which the forced-stop runner already uses.
- **Nothing else in the jar.** The other referencing classes are the save commands (`/save-all` passes `forced = true`
  through `saveEverything`; `/save-off` and `/save-on` set `noSave` on every Level) and `WorldUpgrader`, which runs
  only for `--forceUpgrade` before the server starts.

In this repository, the escrow barrier writes entity slots itself (`EscrowChunkStore.captureEntitySlot` through
`EscrowEntityStore.captureAndStore`), and that store does not read `noSave` either. Each production caller checks it
first: the ordinary retirement holds a save-off slot (`RocketOrdinaryRetirement` line 472, `RocketRetirementWorld`
lines 45-55), the bay's D6 census waits on SAVE_OFF (`BayAbsenceCensus.waiting`, lines 38-42) and its capture refuses
any slot that is not LOADED (`BaySlotProofs` line 46), the passenger release blocks (`RocketReleaseWorld` line 134),
and sealed disassembly and its materializer refuse (`SealedDisassemblyWorld` line 58, `SealedMaterializer` line 84). The
governed escrow paths (`EscrowGovernedDestination`, `EscrowGovernedReturn`, `EscrowGovernedTerminal`,
`EscrowFlightCheckpoint`, `EscrowPreF2Cancellation`, `EscrowSettlementRecovery`, `EscrowReconciliationDriver`) act only
for ledger-bound flights and escrow operations; the bay fixture's rocket is neutral (D0 refuses any other).

### 2.2 Does `saveEverything(true, false, false)` skip such a Level entirely

Yes. It calls `PlayerList.saveAll()` and then `saveAllChunks(true, false, false)` (MinecraftServer:1427-1432). With
`forced = false`, each Level whose `noSave` is true gets `skip = true` (MinecraftServer:1324-1328), and
`ServerLevel.save` then does nothing at all: no Level data, no chunk cache, no entity manager (`autoSave`) and no
`LevelEvent.Save`. The other Levels save as usual, the overworld first (`getAllLevels()` iterates the server's linked
Level map). Afterwards `saveAllChunks` stores `level.dat` and the custom boss events (MinecraftServer:1335-1362). The
periodic autosave is this same call every 6,000 ticks (MinecraftServer:2548-2564).

### 2.3 What else `noSave` stops for that Level

Its `DimensionDataStorage` (`dimensions/<namespace>/<path>/data/*.dat`, through `saveLevelData`), its terrain region
files and POI, its entity sections at save time, the `LevelEvent.Save` and `ChunkDataEvent.Save` listeners (this mod's
atmosphere, device and classic save observers among them), and every chunk unload with its `ChunkEvent.Unload`. The
oracle therefore reads nothing from the destination Level except its entity regions, and those only as the evidence of
absence that the case is about. It does not read the destination's terrain, landing pad, atmosphere or other data
files. The overworld holds the transfer journal, the bay receipts and the satellite registry, so those are unaffected.

### 2.4 What retires a COMMITTED transfer record after the destination spawns

Nothing in the forward flight. `RocketTransferService.tickLive` moves T' through DESTINATION_SPAWNED,
PASSENGERS_TRANSFERRED, SOURCE_REMOVED (whose acknowledged effect discards S, lines 743-748) and COMMITTED, and then
calls `RocketDestinationCompletion.tickDestination` (lines 572-578). After the landing, `cleanupLandedIfComplete` only
moves T from the live to the settled set and logs `landed_reservation_retained` (`RocketDestinationCompletion` lines
158-164); T stays in the journal as the landed reservation. Restart recovery keeps it too: KEEP_DESTINATION,
REMOVE_SOURCE_KEEP_DESTINATION and REBUILD_DESTINATION rebind or keep T and never remove it
(`RocketTransferRecoveryService` lines 418-472).

The record leaves the journal only through a later operation on the landed rocket, each of which proves its slots or
keeps the rocket in a successor record:

- a fluid loader's fill (BEFORE_FILL) and a relaunch (BEFORE_REPLACE, BEFORE_RELEASE) go through
  `RocketOrdinaryRetirement` with `RocketDestinationRetirement` or `RocketReplacementRetirement` as the port, both
  subclasses of `RocketRetirementWorld`, so a save-off slot holds them; a replacement also keeps the rocket in its
  successor record;
- the landed disassembly runs through the retained sealed executor, which refuses a save-off Level;
- the passenger release (J1) blocks on a save-off Level;
- `EscrowGovernedCleanup.remove` acts only on governed records, and `EscrowSettlementRecovery` only on a transfer that
  an escrow operation's guard names.

None of these runs in the window of this case: no player, loader, relaunch or disassembly, and an ungoverned flight
with no escrow operation. So production cannot retire T' while the destination exists only in memory in this sequence,
and there is no production finding.

## 3. Decision: build the control, and close the window while the destination chunk is ticketed

`noSave` alone does not keep an entity section off disk: the entity manager's unload processing (section 2.1) stores
the sections of any chunk that drops below FULL. That path can run for the destination chunk only if the chunk loses its
tickets inside the window. It does not, as long as the window closes in time:

- `tickLive` renews the transfer's flight ticket on every column of the destination's ticket set, its origin column
  among them, on every tick while T is live (`RocketTransferService` lines 565-567, `RocketTransferEntities` lines
  469-484). T stays live through COMMITTED until the landing.
- The ticket is a level-2 region ticket with a 400-tick timeout (`RocketTransferEntities` lines 36-41,
  `RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS`), so after the landing the column stays FULL for at least 400 more
  ticks (20 seconds at most 20 ticks per second) before `purgeStaleTickets` can demote it.
- The descent lasts 80 ticks (`RocketFlightLimits.DESCENT_TICKS`) from the spawn.

So the case kills the JVM a few seconds after the journal acknowledges COMMITTED, well inside that bound. The control
does not add a ticket of its own; it checks instead. In the same tick as the save, the autosave subcommand reports
whether the destination entity is in memory and whether its chunk's entity sections are loaded, and the runner requires
both. The stopped-world oracle then reads the destination entity regions: if any path had stored the destination, the
case fails rather than passes.

The other paths are closed in the window: the Level save is skipped (section 2.2), the chunk map does not unload or
store (section 2.1), every production barrier caller checks save-off (section 2.1), and nothing retires T' (section
2.4). This is a control that holds the state, so the task's stop condition does not apply.

## 4. The gated control

New class `rocket/server/BaySaveBoundaryReleaseTestCommands`, registered by `BayReleaseTestCommands.register` beside
W331's `BayGapReleaseTestCommands`, so it exists only with the release-test property. Every entry requires permission 2
and calls `EscrowTestEndpoints.requireEnabled` before it resolves a dimension or reads anything:

- `arce bay release-test save-boundary hold <dimension>` sets `noSave` on that Level and remembers it. It refuses,
  changing nothing, when `noSave` is already set (for example after `/save-off`).
- `arce bay release-test save-boundary autosave <logical>` reads the transfer journal's record for L. Unless that record
  is COMMITTED, acknowledged (`logicalAcknowledged`), and its destination Level is held by this command, it logs a WAIT
  line and saves nothing. Otherwise it reports the destination's in-memory state, calls
  `server.saveEverything(true, false, false)`, the autosave's own call, and reports which Levels were skipped (those
  whose `noSave` was set at the call) and which saved. Checking and saving in one tick leaves no gap between them.
- `arce bay release-test save-boundary restore <dimension>` clears `noSave` on a Level this command holds, for an
  aborted run, and refuses for any other Level.

The held Levels live only in memory, in a map keyed weakly by the server, so they end with it; nothing outside the
command reads them, and no production code calls the command. A clean stop clears `noSave` and saves anyway (section
2.1). A unit test, like W331's, checks that the entries are absent without the property and refused by the closed
gate before any effect, with an open-gate control; plain unit tests cover the hold bookkeeping (refusal when already
saving off, restore only what was held).

This is a release-test fault control equivalent to `/save-off` for one Level on the facility server, not a protection
that refuses a player's save, so I read AGENTS.md section 3.3's risk-register rule as not applying. The task excludes
the risk register; Root decides.

## 5. The native case B6.5

`W346_B6_SAVE_BOUNDARY`, in a new runner `scripts/run_v180_bay_save_boundary.py` beside the forced-stop runner. It
reuses that runner's session, candidate pin (`--jar-sha256`, `--candidate-commit`, the installed-JAR checks), fixture
and status helpers. The forced-stop runner's `run()` gains an optional table of extra cases, which leaves its own cases
unchanged.

**First start** (fresh flat world, the standard fixture: a data bay and a research-locked solar bay):

1. A self-check of the control before the flight: hold the Moon, hold again (refused), restore, restore again
   (refused).
2. Launch with the existing cut `B16_GUARD`, which pauses D7's guard after D6 has proved both ends. D6's census
   includes the destination end's slots, and it waits on a save-off Level (section 2.1), so the hold must come after D6.
3. At the pause: the claim is RETIRE with T PREPARED in TRANSIT. Hold the Moon, then release the guard with nothing
   changed. D7 must end with READY_SOURCE and no GUARD_REFUSED.
4. The flight continues on T': the destination spawns in the held Moon, the source is discarded, and T' reaches
   COMMITTED. The runner polls `autosave <L>` until the journal has acknowledged COMMITTED; that call then saves. Its
   report must name T, COMMITTED, the Moon as the destination Level, the destination entity in memory with its chunk
   loaded, no loaded source, and exactly the Moon as skipped.
5. After two seconds for the overworld's queued region writes, a status line (destination UUID, COMMITTED, no claim,
   no receipt, data bay consumed, solar bay unchanged, one satellite), then a hard kill. No unexpected ERROR or FATAL.

**At the kill**, in the stopped world: no entity of L in the overworld's entity regions (the source chunk has no S);
none in the Moon's, while the destination's origin chunk has a stored entity record (written by D6's census before the
hold, so the reader looked at the right chunk); the journal file holds T' for L, COMMITTED, with the destination UUID
of the autosave report and, in both snapshots, the data bay's consumed operation and the solar bay's package; the
registry names the data satellite once and not the solar one; no receipt names L; no item entity in either dimension.

**Restart 1**: ordinary recovery logs exactly one recovery line for T, `phase=COMMITTED source_count=0
destination_count=0 action=REBUILD_DESTINATION status=RECOVERED`. The rocket settles LANDED with T''s destination UUID,
COMMITTED, the same consumed operations, the data bay consumed and the solar bay byte-equal, no claim or receipt, one
owned satellite, and no pending or retained bay work. After its stop: exactly one entity of L in the Moon's regions
(that UUID, its bays carrying the same consumed operations), none in the overworld's, T' unchanged in the journal, the
same registry satellites, no receipt, no item entity, and no entity region file removed.

**Restart 2**: one recovery line for T, `source_count=0 destination_count=1 action=KEEP_DESTINATION status=RECOVERED`,
and no source action. The same settled state and disk checks as restart 1, with the same entity UUID, and no entity
region file removed since restart 1.

What it does not show, as W342-DESIGN section 4 says: the natural timing of an autosave against the flight, a crash
inside one Level's save, the benign order where the destination saves first, and passengers.

## 6. Proof that the case can fail

A scratch `git archive` export of the commit with W342's mutation M1 (`RocketTransferRecoveryDecision` answers
REBUILD_SOURCE for every record with neither entity), built to a JAR. The case must fail at restart 1. The patch is kept
beside its log, and the export is deleted.

## 7. The campaign driver

`scripts/run_v180_integrated_campaign.py` appends one family, `forced-W346-R`, for the new runner with
`--tranche W346_R`, after the existing 51, and its tests pin the new count and the family's command.

## 8. Not in this task

Production code outside the gated command, W331's other NOT_COVERED rows, W345's families, and the status files, QUEUE
and risk register.
