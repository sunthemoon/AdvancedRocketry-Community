# W85: a durable record that a landed disassembly is under way (P1-F8)

**Proposed design, 2026-10-16. Not implementation, review, risk acceptance or Gate evidence.**
Assignment: [TASK W85](../TASK-W85-P1-F8-DESIGN.md); record: [W85](W85.md). Finding F8 is in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-review/REVIEW.md` (SHA-256
`8c1da51138a655581c8a3256841615fd556c627eaed46edcf421b4fcc67de499`, checked in this session). The closure to meet is
[W11B3-DESIGN.md](W11B3-DESIGN.md) revision 2, section 1 (row P1-F8) and section 5 (leaves P1-F8-D and P1-F8).
[W77](W77.md) supplied the first analysis.

**Baselines.** The shared branch is read at `4a9f892a`; it contains W11b2 part 1 and W77. W11b2 is read through Git
only, at `463c0d56` (continuation 22), including `records/W11B2-DESIGN-2.md` on that commit. Citations are at
`4a9f892a` unless they say `@463c0d56`. Between the two commits, the disassembly service, the v0.5 transaction
classes, the checkpoint, the entity helpers and the pad classes are identical. W11b2 adds governed record 3 to
`RocketTransferRecord` and `RocketTransferJournalCodec`, and inserts lines into `RocketTransferService` (27, after
line 125), `RocketTransferSavedData` (27, from line 244 on) and `RocketTransferRecoveryService` (1, line 106).

**Notation.** `J` is `src/main/java/io/github/sunthemoon/advancedrocketrycommunity`. L is a logical rocket, T its
landed transfer record (phase COMMITTED) in the checked transfer journal `RocketTransferSavedData`, E the arrival
entity that T names, and D the proposed disassembly section. "Acknowledged" means the journal writer's
exact-generation acknowledgement (`J/rocket/flight/persistence/RocketTransferSavedData.java:401-451`). Proposed
names do not exist yet.

## 1. The current order and what a restart finds

1. `RocketDisassemblyService.validateRocket` (`J/rocket/server/RocketDisassemblyService.java:107-130`) requires
   `resourceMutationAllowed` (`J/rocket/server/RocketTransferService.java:404-422`: T is COMMITTED, settled in this
   session and stationary) and no overlapping v0.5 transaction record.
2. `executePlayer` and `disassembleForReleaseTest` call `releaseLandedReservation` **first** (lines 136 and 164).
   With T present (`RocketTransferService.java:424-447`), it removes T in RAM, queues an effect that clears the
   session sets and audits `landed_reservation_released`, and returns false. So every first request on a landed
   arrival answers REGION_BUSY.
3. A second request after the acknowledgement finds no T and runs `execute` (174-187), which runs
   `RocketDisassemblyTransaction.execute` (`J/rocket/transaction/RocketDisassemblyTransaction.java:46-268`) inside one
   server tick: v0.5 phase records, block placement, `removeRocket`, then removal of the v0.5 record.

Two facts bound every crash case below:
- The v0.5 journal (`J/rocket/persistence/RocketTransactionSavedData.java:36-42,140-167`) is ordinary dirty
  SavedData. A transaction that succeeds or rolls back completely adds and removes its record within one tick, so in
  production no save sees it. Only the release probe's mid-transaction `saveEverything`
  (`RocketTransactionReleaseProbe.java:47`) puts one on disk.
- Chunk saves are never acknowledged. E exists only in RAM until its entity section is first saved after arrival.
  Until then T is its only durable copy: ordinary recovery rebuilds a missing destination (REBUILD_DESTINATION in
  `RocketTransferRecoveryDecision`; `createDestination` at `RocketTransferRecoveryService.java:340`).

| Crash point today | On disk | A restart finds |
|---|---|---|
| C1: T removed in RAM, before the file replace | T | Ordinary recovery keeps or rebuilds E. The claim is kept. |
| C2: removal replaced, before or after its callback | no T | If E was saved: a rocket with **no claim**. Its region has left `reservations` (`RocketTransferService.java:89-92`) and the overlap check (`RocketTransferSavedData.java:608-621`), and while its chunk is unloaded, `RocketLandingPadSelector` (lines 91, 162, 246) can select a pad under it. If E was never saved: **the rocket is lost**. |
| C3: the second request is obstructed or rolls back | no T | As C2. This is F8's lost claim, already live in the session. |
| C4: the second request succeeded, before the next save | no T | If E was saved earlier: E is back and the blocks are absent, which is harmless. If E was never saved: **the rocket is lost**. |
| Base order (before W11b2: execute, then remove T): restored blocks saved, T not | T, blocks, no E | REBUILD_DESTINATION recreates the rocket **over its restored blocks**: duplication. This is why moving the call back is not enough (W77). |

## 2. The durable record

| Option | Where D lives | Ordering | Assessment |
|---|---|---|---|
| **A (recommended)** | A section on T itself | One writer and one generation per transition. Removing T removes D. | Only three durable states: T, T with D, nothing. No cross-root ordering. Older jars must refuse it (below). |
| B | A new checked root with its own managed type and writer | D in root B, then T's removal in the journal, then D's removal in B | Two roots add cuts (D without T, T without D), a blocked-root policy, a baseline and new validator registration. An older jar ignores the file and would rebuild over restored blocks after a downgrade. |
| C | The v0.5 transaction journal, made checked | As B | Changes the released persistence policy of assembly as well. Rejected. |

**Format of option A.**
- **Section** `landed_disassembly`, `schema_version` 1, with an exact key set:
  - `transaction_id`: the v0.5 transaction UUID, chosen before marking;
  - `requester`: the player UUID, or the nil UUID for the release-test hook;
  - `entity_id`, `snapshot_id` and `content_hash`: these must equal T's destination entity and snapshot;
  - `discard_fuel`: a long of at least 0, the consented amount;
  - `quote_digest`: SHA-256 of the confirmation quote;
  - `requested_at`: a game time of at least 0;
  - `digest`: SHA-256 over T's transfer ID and checksum and the fields above.

  It is at most 512 bytes. There is at most one per record, so at most 64 (`MAX_ACTIVE_TRANSFERS`).
- **Record encoding 4.** A record has `schema_version` 4 exactly when the section is present: the schema-2 key set
  (exact) plus the section. The record keeps authority schema 2 and its legacy checksum; 4 only encodes "schema 2
  with D". It never combines with W11b2's governed record 3. Record and root limits are unchanged:
  `MAX_TRANSFER_RECORD_NBT_BYTES` allows 64 KiB beyond two maximum snapshots and two maximum flight bodies, and the
  leaf tests a maximum legal record with D.
- **Downgrade.** Schema-1 and schema-2 decoding ignores unknown keys (`RocketTransferJournalCodec.java:39-87`), so a
  plain flag would let an older jar rebuild over restored blocks. Every older jar refuses record 4
  (`RocketTransferRecord.java:41-42`; at most 3 `@463c0d56`), which blocks the journal and preserves it verbatim. This
  exposure lasts only while a D exists.
- **Versions.** `RocketJournalCheckpoint.RecordVersion` (lines 21-30) adds D's digest. `resourceMutationUnproved`
  (`RocketTransferSavedData.java:373-382`) and the exact-record effect check in `pollWriter` (line 442) then see every
  transition.
- **Operations.** New `markLandedDisassembly(T, D)` and `clearLandedDisassembly(T, D)` compare and replace exactly the
  expected record; `remove(T)` is unchanged. Each is one generation through `afterAcknowledged`. `put`, `replace`,
  `migrateCommitted` and `rebindDestination` refuse a record with D. So no launch replaces it
  (`RocketTransferService.java:159-163`) and no recovery rebinds it.
- **Root 3 and W44.** The root stays schema 3 with no migration. D's transitions are ordinary generations of the same
  writer and carry the first-Moon-landing outbox (`RocketTransferSavedData.java:186-208`) unchanged, so W44's
  ELECTED and DELIVERED order is untouched. The test-only W44 hook `releaseLandedReservationForFirstEventTest` keeps
  its plain checked removal; FM12a of `scripts/run_v180_first_event_moon_cuts.py` still waits for
  `landed_reservation_released`.
- **W11b2's record 3.** D is never written to a record with `native_transfer` (section 5).
- **W80.** J1 in [W80-DESIGN.md](W80-DESIGN.md) section 3 is bound to T's exact version, so it refuses a T with D.
  Marking refuses a T that a W80 batch names. A restart classifies W80 batches first, then D, then ordinary
  recovery.

## 3. The new order

**Request (server thread).**
- **S0.** Validate as today. If L has no T, run today's one-tick path, unchanged and without D. If T is present,
  require all of the following, and otherwise answer with no mutation:
  - T is a schema-2 record and `isReplaceableLandedAuthority(E, T)` holds;
  - T has no `native_transfer` and no W80 batch names it;
  - the bay gate allows `REMOVE_JOURNAL`;
  - `journal.canQueueEffect()` holds (P1-N2's rule: reserve the continuation before any mutation).
- **S1.** Choose the transaction UUID, build D, call `markLandedDisassembly`, queue the continuation and answer
  DISASSEMBLY_PENDING. A session tracker (proposed `RocketLandedDisassemblyTracker`, owned and cleared by
  `RocketTransferService`) records T, D and the requester. The confirmation token is consumed here; D's quote
  digest and fuel amount replace it. The acknowledgement continuation only advances the tracker to MARKED.

**Restoration** (a tracker step, at most one per tick).
- **S2.** The step runs only when the acknowledged checkpoint holds T with exactly this D, that is,
  `resourceMutationUnproved(L)` is false. **This is the fence:** ordinary recovery can no longer rebuild E before
  any restored block exists. The step revalidates E (alive, loaded, operational, snapshot and owner), the region
  (loaded and empty), the v0.5 journal (operational, no overlap), the fuel (equal to `discard_fuel`) and
  `canQueueEffect()`. It then runs the v0.5 transaction with D's UUID (`RocketTransactionExecutor.disassemble`
  gains that parameter; today it draws a random UUID at line 70).
- **S3a, failure.** If the step fails without mutation, or rolls back completely in the same tick (ROLLED_BACK), it
  calls `clearLandedDisassembly` at once. No save can have seen restored blocks, because placement and rollback share
  one tick. The claim was never released, and the player gets the failure code.
- **S3b, ROLLBACK_FAILED.** D stays and the record is held as `LANDED_DISASSEMBLY_ROLLBACK_FAILED`. The v0.5 FAILED
  record goes through `RocketTransactionRecoveryService` first, and the reconciler then decides.
- **S3c, success.** The player gets the success and fuel-disposal messages now. There is no journal write yet.

**Save gate, removal and release.**
- **S4, save gate.** Every terrain chunk that holds a restored block (at most 16, `MAX_LANDING_CHUNKS`) must have a
  `ChunkDataEvent.Save` observed after the restoration tick, and the last one must be at least 40 ticks old. This is
  the persistence signal of [ADR-051](../../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md)
  section 5 with the age rule of
  [ADR-054](../../../decisions/ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md), the same event subscription that
  `EndgameService.onChunkSave` uses. Autosave and unloading pass the gate, and `save-all` passes it at once; a chunk
  that one autosave skips waits for a later save. `/save-off` keeps the entry waiting. E's removal is in the separate
  entity storage: vanilla stores it in the same save call or the same unload, so the 40-tick age covers it in
  ordinary operation (section 8 lists the residual). The leaf confirms that ordering with `javap` on the Forge jar,
  as W11A section 0 did, and stops to report if it does not hold.
- **S5, removal.** `journal.remove(T)`, which removes D too, with the release continuation. It is issued once
  (tracker state REMOVING).
- **S6, release, on the exact acknowledgement.** Drop T from the live and settled sets, call the idempotent
  `LandingPadService.release`, audit `landed_disassembly_released` once and drop the tracker entry. If the
  continuation is discarded (UNKNOWN or stop), the entry stays; the next time the journal is acknowledged-current
  with T absent, the tracker releases once.

**While D exists:**
- `resourceMutationAllowed` is false (fuel, cargo and launch) and `flightAllowed` refuses;
- `RocketTransferRecoveryService.step` skips the record (like W11b2's `native_transfer` filter, line 106
  `@463c0d56`), and `recoverById` and `tryReconnect` route it to the tracker, answering RETRY_LATER;
- assembly over its region answers REGION_BUSY;
- a repeated request answers DISASSEMBLY_PENDING.

**Reconciler.** It handles a D that has no tracker entry: after a restart, or an orphan. It runs once the journal is
acknowledged-current after the startup baseline, W80 batches are classified and no v0.5 record overlaps the region.
It loads only the destination end, through the existing one-record ticket of `recoveryEntityChunksLoaded`. It
handles one record per tick, with at most 2,048 block reads (`RocketLimits.MAX_BLOCKS`) through
`ServerLevelRocketTransactionWorld.readBlock`, as `RocketTransactionRecoveryService.recoverBlocks` does (201-219).
Each snapshot position reads as EMPTY, EXACT (the snapshot block with its block-entity NBT) or OTHER.

| Row | E (loaded matches of T) | Positions | Action |
|---|---|---|---|
| R1 | one or more | all EMPTY | Clear D; there is no world change. The claim is kept, and ordinary recovery then elects E as today. |
| R2 | exactly one | some EXACT, none OTHER | Remove the EXACT blocks without drops, pass the S4 gate for those chunks, then clear D. |
| R3 | one or more | any OTHER, or several E with any block | Hold as `LANDED_DISASSEMBLY_CONFLICT`. |
| R4 | none | any EXACT or OTHER | Remove T (with D) and release once. Place or remove nothing. |
| R5 | none | all EMPTY | Hold as `LANDED_DISASSEMBLY_EVIDENCE_MISSING`. Never rebuild and never release. |

A hold keeps T and D: the claim stays, the record is fenced like `RocketTransferFences`, and it logs one WARN per
record and reason per session with `repair=offline-whole-world`. R5 cannot tell "neither E nor the restoration was
saved" from "players took every restored block and the chunks were saved": rebuilding could duplicate and releasing
could lose, so it holds. Every row is idempotent. A restart before a row's generation is acknowledged sees the same
disk and repeats the row, and R2 removes again any block that is still EXACT.

## 4. Crash points in the new order

| Cut (probe pause, then kill) | Journal on disk | World on disk | A restart finds |
|---|---|---|---|
| N1: mark staged, before the replace | T | before | Ordinary recovery; the claim is kept and a retry works. |
| N2: mark replaced, before restoration (flushed) | T, D | before, E saved | R1: revert; the claim is kept. |
| N2u: as N2, E never saved, no flush | T, D | nothing | R5 hold (today this is C2's silent loss). |
| N3: during restoration, `RESTORING:3` (flushed) | T, D | E and 3 EXACT blocks | R2: roll back, gate, clear; the claim is kept. |
| N4: restoration done, before the gate (flushed) | T, D | blocks, no E | R4: removal and one release; no rebuild. |
| N4n: as N4, not flushed, E saved earlier | T, D | E, no blocks | R1: the disassembly is undone and the rocket kept. |
| N4u: as N4, not flushed, E never saved | T, D | nothing | R5 hold (today this is C4's silent loss). |
| N5: gate passed, removal replaced, before the release callback (flushed) | none | blocks | Nothing to recover and no rebuild; the release does not repeat. |
| N6: after the release | none | after | Steady state. |
| N7: removal writer fails (NOT_COMMITTED, retried every 200 ticks); players change and break restored blocks; `save-all`; kill | T, D | changed blocks, no E | R4 on both restarts; no rebuild and no duplicated items. |
| N8: the mark write ends UNKNOWN | T, or T with D | before | Ordinary recovery, or R1. |

The cuts cover both sides of restoration (N2, N3, N4), of the removal acknowledgement (N4 before the replace, N5
after it) and of the release (N5, N6), plus a failed removal writer (N7). Each cut is followed by two restarts, and
the second must find the same state as the first.

## 5. Governed flights

- A rocket with an ARMED entry, active or terminal, is never idle (`RocketEscrowGate.escrowIdle`, line 36
  `@463c0d56`), so `validateRocket` answers REGION_BUSY before S0. Design 2's "Ordinary interlocks" row (section 1 of
  `W11B2-DESIGN-2.md` `@463c0d56`) already locks disassembly.
- D2-F8 removes the governed T, or replaces it with the next F2, before it prunes the terminal entry. So a governed
  rocket reaches disassembly only without a T, and it takes the unchanged one-tick path with no D. A record-3 T
  without an entry is held by `RocketTransferFences.classify` (lines 54-64 `@463c0d56`) and is never settled, so
  `resourceMutationAllowed` is false.
- S0 still refuses any record with `native_transfer` (REGION_BUSY), matching `RocketTransferEscrowRules` line 20
  `@463c0d56`: record-3 data is never legacy authority. The escrow fences of `RocketTransactionRecoveryService`
  (lines 77-113) are unchanged.
- B3-S6's D2-F8 representative gains one P1-F8 case: a request during the terminal entry is refused, and after D2-F8
  it succeeds without D.

## 6. Player-visible behaviour

- **First request on a landed arrival:** the action bar shows DISASSEMBLY_PENDING. A few ticks later the blocks
  appear with the existing "disassembly committed" and fuel-disposal messages. There is no REGION_BUSY first attempt.
  Rockets without T behave as today.
- **While S1 and S2 are pending:** a repeated request answers DISASSEMBLY_PENDING, and refuelling and launch are
  refused.
- **Failure:** the existing message (for example TARGET_OCCUPIED). The rocket keeps its claim, and the next attempt
  works once the cause is gone.
- **After success, until the gate passes** (at most about one autosave interval, 6,000 ticks plus 40): the blocks
  are ordinary blocks. Only assembly over the region answers REGION_BUSY. The journal diagnostics and
  `/arce rocket recover` show the record as a pending disassembly.
- **Holds:** the diagnostics name the reason. The player sees the rocket present (R3) or missing (R5) until repair.
- **Release-test hook:** it returns DISASSEMBLY_PENDING, logs `ARCE_RELEASE_TEST_DISASSEMBLY ... code=SUCCESS` at S3c
  and audits the release at S6. When `scripts/run_v130_adapter_flight_smoke.py` (lines 513-519) is rerun, it needs
  the new event name.
- **New text:** one validation code, DISASSEMBLY_PENDING, with its language entries.

## 7. Tests

**A0 (JUnit, inside the ordinary unit run; no extra JVM).** About five classes and 40 cases:
- the codec: exact key sets, bounds, digest binding, a maximum record with D, a clear that restores byte-identical
  schema 2, and an older version bound that blocks the journal and preserves it;
- the journal operations: one generation each, the refusals, `RecordVersion`, no mutation without effect capacity,
  and UNKNOWN;
- the pure R1-R5 and tracker decision table over every combination, asserting never a rebuild, never a placement and
  a release only after the removal acknowledgement;
- the recovery-step filter;
- the save-gate ordering: a save before the restoration tick does not count, and the 40-tick age applies.

**A1 (GameTests with real services and checked-file reloads, as in W77).**
- G1: an obstructed disassembly keeps the claim. T is clear and acknowledged, the region still refuses a second
  flight's pad selection, and the attempt after the obstruction is removed succeeds.
- G2: a rollback injected with `RocketFailureInjector` keeps the claim in the same way. G2b: ROLLBACK_FAILED holds,
  then reverts after the v0.5 recovery.
- G3: the first attempt succeeds. One request produces exactly two checked generations, counted with W77's
  completed-write counter; the removal waits for a chunk save plus 40 ticks; and the release audit appears once.
- G4: with a delayed writer, the pending-state interlocks hold.
- G5: the reconciler rows R1 to R5, each reloaded twice.
- G6: record 3 is refused and no D is written.
- G7: with a full effect queue, the request is refused without mutation.

One full GameTest run takes about 18 minutes (W77's run).

**S2 (packaged dedicated server).** The new probe family `LANDED_DISASSEMBLY:<boundary>` pauses with or without a
flush, and the harness kills the server. N3 reuses the v0.5 `RESTORING` checkpoint. N7 adds the mode
`LANDED_DISASSEMBLY_REMOVAL` to `-Dadvancedrocketrycommunity.rocketJournalFailure`
(`scripts/run_v180_rocket_journal_failures.py`). Every case compares raw block, entity, inventory and journal NBT.
- The eleven cuts of section 4 take three JVM starts each, 33 in total.
- At W11b3's own rate (6 cuts and 18 starts in about an hour, about 3.3 minutes per start), that is about **1.8
  heavy-JVM lock hours**, plus the GameTest run.
- W11b3 estimated 18 starts. This plan adds N2u, N4n and N4u, which test the new holds and the gate, and N8.

## 8. Risk, ADR text and open questions

**Residuals.**
1. A save event is not proof that the IO completed; this is the R-032 class.
2. If a terrain chunk is saved after restoration but E's entity section is not, and the server then stops, the disk
   holds E and the blocks with no T. Vanilla stores both in the same save call or unload, and the gate's 40-tick age
   covers ordinary operation. A proof would need W11b1's entity-store decorator in every world (question 3).
3. Player data saved before the chunks (a logout, then a crash) is vanilla's own crash-duplication class, unchanged.
4. R5 holds need an operator.
5. Each landed disassembly writes two journal generations instead of one. While F3 is open, each write pauses live
   flights for 2 to 3 ticks.

**Proposed ADR-065 implementation note (for Root).**

> **P1-F8 (W85).** Disassembly of a landed arrival keeps its transfer record T until the restored world has been
> saved. A checked `landed_disassembly` section is acknowledged before any block is restored. It uses record encoding
> 4 (schema 2 plus the section), section schema 1, at most 512 bytes and one per T. While the section exists, T is
> never flight or recovery authority and the rocket's resources are locked. T is removed only after every restored
> chunk has a `ChunkDataEvent.Save` at least 40 ticks old (ADR-051 section 5), and the claim is released once on that
> removal's exact acknowledgement. A failed or completely rolled-back restoration clears the section and keeps the
> claim. After a restart, a section with no session entry is reconciled from loaded observations (rows R1 to R5). It
> never rebuilds the arrival and never places a block, and ambiguous evidence holds the record for offline repair.
> Governed record-3 transfers never carry the section. Root 3, the W44 outbox and W11b2's acknowledgement orders are
> unchanged. Older jars refuse record 4 and preserve the journal.

**Proposed R-035 addition (Chinese, as in the register).**

> F8（W85 设计）：着陆火箭拆解期间，转移记录 T 带 `landed_disassembly` 段（记录编码 4）。影响范围：单个转移记录及其目的地区域；
> 降级到旧 jar 时为整个转移日志（旧 jar 拒绝记录 4，日志被阻断并原样保留），仅限该段存在期间。持续时间：通常从请求到每个恢复区块
> 的下一次保存后 40 tick（约一个自动保存间隔，`/save-off` 期间一直等待）；冲突或证据缺失时保留到离线修复。日志量：每条记录每个原因
> 每次会话一条 WARN，另有一条审计 INFO。恢复：存储故障时修复存储后等待重试或重启；冲突或证据缺失时停服、一致备份整个世界后离线修复，
> 不得单独恢复日志。残余：保存事件不是写入证明（R-032 同类）；恢复的方块已保存而实体段未保存时停服，可能留下实体与方块两份。

**Open questions, each with a recommended answer.**
1. **Release timing: after the save gate (S4) or right after restoration?** Use the gate. Without it, the rocket is
   silently lost whenever E was never saved and the server stops before the next save, as at C4 today.
2. **R5: hold or rebuild?** Hold. A rebuild duplicates whenever players took the restored blocks.
3. **Prove E's entity chunk with W11b1's own-store barrier?** Not in P1-F8. It would install the escrow entity
   decorator in every world for a low-severity finding. Keep residual 2.
4. **An online operator command to resolve holds?** Not in P1-F8. Use offline repair, as for the other transfer
   fences, and revisit this alongside W80's operator command.
5. **Per-record encoding 4, or a root bump?** Per-record. Only worlds with a pending D are affected on downgrade.
6. **Should S0 require an acknowledged-current journal?** No. Requiring `canQueueEffect()` is enough: a busy writer
   delays the mark but does not refuse the first request.
7. **Should the reconciler load the destination end?** Yes, through ordinary recovery's existing bounded
   one-record ticket, for the destination end only. Never the source end, and never a client-supplied position.
