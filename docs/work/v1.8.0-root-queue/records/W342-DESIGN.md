# W342 cut list: B6.5's destination rebuild from a consumed bay transfer

This cut list is committed before any W342 code. Authority is [TASK-W342](../TASK-W342-B65-REBUILD-GAMETEST.md) under
Root decision R412, from finding 3 of W338's review (`D:/GitHub/ARCE-Task-Evidence/root-queue/W338/REVIEW.md`). The
contract is the B6, B7 and B8 rows of [W27A section 9](W27A-DESIGN.md) and W27A section 5 (retirement). It plans
test coverage only: no production code changes, and no native case or gated save control is built.

## 1. The state under test

W338 showed this crash state is reachable without manual deletion. After D7 retires the group's receipts, the
transfer continues on T': the destination spawns (DESTINATION_SPAWNED), the passengers move, and SOURCE_REMOVED's
acknowledged callback discards S (`RocketTransferService.removeSource`). An ordinary save of the source Level can
persist that absence before the destination Level saves the new rocket. A crash in that gap leaves:

- the journal holding T' in a destination-authoritative phase (the TASK names COMMITTED), with both snapshots
  consumed: every registering bay holds `consumed_op = op` and no package;
- the satellite registry holding every registered satellite;
- no receipt for any member (D7 retired them);
- no entity of L at either end.

`RocketTransferRecoveryDecision` then chooses REBUILD_DESTINATION, and `RocketTransferRecoveryService` builds the
rocket from the record's destination snapshot (`RocketTransferEntities.createDestination`) under its acknowledged
destination UUID. With no receipt and no claim, the installed bay coordinator answers UNCLAIMED, so ordinary
recovery runs.

## 2. GameTests (test-only)

One new class, `rocket/server/BayRebuildDestinationGameTests`, in its own batch `bay_rebuild_destination`, so no
other test shares the journal while it runs. It reuses `BayDeploymentGameTestSupport` (`transit`, `solarPackage`,
`registerSolar`, `intent`, `consumedOps`, `bayPayloads`) and `BayRegisteredGameTests.consumed`. Each case builds its
state directly in one tick, with no production seam:

1. `transit` gives T (PREPARED, ungoverned, two solar bays) and S in TRANSIT at altitude. Both ends are in the
   GameTest's own Level, as in every bay GameTest. The decision and the rebuild do not depend on the Level; the
   cross-Level save gap is the subject of section 4.
2. For a consumed case: both satellites are registered (`registerSolar`); two INTENT receipts are built in RAM only,
   never stored, so their ops exist only as consumption ops (the "retired" state); `consumed` computes S' and D'
   as D4 does.
3. The journal gets T and then T' through the production mutation `RocketTransferConsumption.apply` (must answer
   CONSUMED), then T' advanced through `destinationSpawned(<new UUID>)` and every phase to COMMITTED; the checked
   writer acknowledges it (`RocketJournalGameTestSupport.acknowledgeFixture`).
4. S is discarded (absent case) or holds S' through `applyBayConsumption` (the KEEP_SOURCE control).
5. Recovery runs through the installed manager, `RocketRuntime.recoverTransfer`, the same service and bay gate the
   automatic restart recovery uses, in the same tick as the setup so the per-tick `recoverNext` cannot act first.

| Case | State | Assertions |
|---|---|---|
| R1 (main) | Consumed committed T', both entities absent | RECOVERED, REBUILD_DESTINATION, counts 0/0. Exactly one rocket of L in the Level: the rebuilt one, with T''s destination UUID, at the destination origin's altitude, snapshot ID and content hash equal to T''s destination snapshot (the group's after hash), every bay `consumed_op` equal to its group op and no bay holding a package. No item entity with either package near either end, and no world bay in the structure holding one. Registry entries for both satellites unchanged (record equality, same tick). No receipt for either satellite, `BayReceipts.holdsRocket(L)` false, and no stored receipt naming L. Reload: the acknowledged journal file, read from disk and decoded with `RocketTransferSavedData.load`, holds T' unchanged (phase, checksum, destination UUID, consumed ops of both snapshots); the rebuilt entity's saved NBT, loaded into a new entity, keeps the same consumed ops. |
| R2 (control) | The same state with S' present (consumed, at altitude, T's source UUID) | KEEP_SOURCE with counts 1/0, RECOVERED after the ordinary survivor proof retires T (W98). The source keeps T's source UUID, is stationary at its origin with no plan, and still holds S' (hash and consumed ops). No destination is created; T is gone from RAM and from the journal file on disk. Registry: the same satellite IDs for the owner, none added or removed (the solar state may tick over the proof's ticks, so record equality is not required). No receipt, and no package anywhere, as in R1. This is W27A B8's "stationary consumed-source recovery". |
| R3 (control) | An unconsumed committed T (both bays still hold their packages), no satellite registered, no receipt, both entities absent | REBUILD_DESTINATION with counts 0/0. The rebuilt rocket carries T's destination snapshot unchanged: both bays still hold their own packages (no `consumed_op`). The registry has no entry for either satellite before or after, and no receipt appears. This is W27A section 7: unresolved packages fly on unconsumed, and section 5: recovery never registers a satellite or refunds a package. |

**Why R3 has no registered satellite.** An unconsumed record whose satellites are registered and whose receipts are
retired is outside W27A's assumptions: section 5 condition 3 lets D7 retire a REGISTERED receipt only after the
journal acknowledged a generation in which every record for L has consumed bays. No production path reaches it,
so no assertion would describe intended behaviour. R3 uses the reachable unconsumed case, a group that never
registered. REJECTED receipts, which W27A says never block, are not added; their non-blocking rule has its own
tests (`SatelliteBayReceiptGameTests`).

## 3. The negative check (scratch copy only)

W27A B6's "uses consumed T'; no package resurrection" has no separate check in the ordinary rebuild. It holds
because the branch materializes the journal's own record, and only T' is in the journal. The task's
"consumed-state guard" is read as that property, at its two points. Each is disabled in its own scratch export
(`git archive` of the test commit under `D:/GitHub/ARCE-Task-Evidence/root-queue/W342/scratch/`), and only
`bay_rebuild_destination` runs (the existing batch filter). R1 must fail each time:

- **M1, the destination choice.** `RocketTransferRecoveryDecision.decide` answers REBUILD_SOURCE for every
  absent/absent record. This is W338's "disabling only REBUILD_DESTINATION", which W331's KEEP_SOURCE case cannot
  detect. Expected: R1 fails on the action (and the rebuilt rocket would be a stationary source).
- **M2, the consumed payloads.** `RocketTransferEntities.createDestination` builds the rocket from the record's
  destination snapshot with each consumed bay reset to an empty root of its owner, so the consumption marker that
  W27A section 5 calls the bay's tombstone is lost. Expected: R1 fails on the snapshot hash and consumed ops.

Each mutation is kept only as a patch beside its log; the scratch tree is deleted.

## 4. Native assessment (not built; Root decides)

**The save boundary.** In 1.20.1, `MinecraftServer.saveAllChunks(suppressLog, flush, forced)` saves each Level in
turn (`getAllLevels()`, the overworld first) through `ServerLevel.save(progress, flush, skip)`, with
`skip = level.noSave && !forced`. Unless skipped, that call saves the Level's data, the chunk cache and then the
entity manager: `PersistentEntitySectionManager.saveAll()` when flushing, `autoSave()` otherwise. Entity sections
reach disk through `EntityStorage.storeEntities` and the Level's own `IOWorker`; a loaded chunk whose last entity
left is stored with an empty entity list. The periodic autosave, every 6,000 ticks, calls
`saveEverything(true, false, false)` (`forced = false`); `/save-all` passes `forced = true`. (Checked in W338's
javap logs of the mapped Forge 47.4.10 jar, `vanilla-MinecraftServer-javap.log` and `vanilla-ServerLevel-javap.log`
under `D:/GitHub/ARCE-Task-Evidence/root-queue/W338/`.) A chunk unload also stores its sections. So Earth's entity
save and the Moon's are separate calls, and Earth's runs first in each pass: the W338 gap is one pass in which
Earth has saved S's absence and the Moon has not yet saved the new rocket.

**A gated control with no production call site.** A new gated release-test command (beside W331's, under
`arce bay release-test ...`, registered only with the release-test hooks property and calling
`EscrowTestEndpoints.requireEnabled` first) would:

1. set `noSave = true` on the destination Level only, before the group's transfer spawns its destination;
2. after the journal has acknowledged COMMITTED, call `server.saveEverything(true, false, false)`, the autosave's
   own call: the source Level saves (S's empty section), and the destination Level is skipped;
3. confirm on disk, through the harness's stopped-world reader after the kill, that the source chunk has no S, the
   destination chunk has no rocket of L, and the journal holds committed T' with consumed snapshots;
4. then the runner hard-kills the JVM, as the forced-stop runner already does.

The alternative, a gated cut inside `removeSource`'s callback that calls `sourceLevel.save(null, true, false)`
and halts, cuts more exactly but adds a production call site, which W331's rules forbid for new fixtures.

**What it would show.** The production sequence (D7, spawn, SOURCE_REMOVED's discard, an ordinary source save)
leads after restart 1 to REBUILD_DESTINATION from the consumed T', with stable consumed operations, no package in
any bay or inventory, an unchanged registry and no receipt; restart 2 keeps the rebuilt destination with the same
consumed operations, with no entity file deleted and no forced source.

**What it would not show.** The natural timing of an autosave against the flight (the command forces the pass);
a crash inside one Level's save (torn region or entity files); the benign order where the destination saves first;
passengers. `noSave` also stops every other save of the destination Level (its chunks and Level-attached data),
so the oracle must not read the Moon's pad or atmosphere data as evidence. The destination chunk should stay loaded
until the kill: whether a chunk unload stores entities while `noSave` is set was not checked here and must be
checked in the bytecode before the command is built.

## 5. Not in this task

Production code; the native case or the command above; W338's findings 1 and 2 (W341); the status files, QUEUE,
the risk register and the ledger.
