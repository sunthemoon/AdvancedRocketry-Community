# W85: landed disassembly without a lost claim (P1-F8), revision 2

**Proposed design, revision 2, 2026-10-16. Not implementation, review, risk acceptance or Gate evidence.**
Assignment: [TASK W85R](../TASK-W85R-P1-F8-REDESIGN.md); record: [W85](W85.md), section "Revision 2".

Revision 1 (commit `08474506`) is superseded as a whole. Its `landed_disassembly` section, record encoding 4, save gate
and restart reconciler (rows R1 to R5) are withdrawn, not patched; Git history keeps that text. The inputs are:
- the independent review `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/REVIEW.md`, SHA-256
  `4dbb7f9ee948f20f7dce167534d1a583242f2adba3a329e20ffa14aa3ba1feb8`, verdict REDESIGN;
- Root's direction `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/ROOT-DECISIONS.md`, SHA-256
  `f808a2460e58969509e571f498e553131ba0aedf947d15a35c115269927f7351`.

Both hashes were checked in this session. The review's findings are called W85-R1 to W85-R6 here. The closure to meet
is [W11B3-DESIGN.md](W11B3-DESIGN.md) section 1, row P1-F8, and the F8 line of "Open before W11b3 or any admission" in
[ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md) (line 1318).

**Baselines.**
- **Shared code:** this branch at `acc295b2`. Its `src` tree equals revision 1's baseline `4a9f892a`
  (`git diff --stat 4a9f892a acc295b2 -- src` is empty). Citations are at `acc295b2` unless they say otherwise.
- **W11b2:** read through Git only, at `60cf89bf` (continuation 23, the head of `codex/v1.8.0-w11b2-rocket-integration`
  when read). Against the shared branch, W11b2 changes none of the code this design touches except:
  - the governed branch of `prepareLaunch`, inserted after line 125, that is, after the capacity check and the
    reservation list;
  - the native-transfer filters in `tick`, in recovery `step` (line 106 `@60cf89bf`) and in `tryReconnect`;
  - record 3 in the journal codec.

  `RocketDisassemblyService`, `releaseLandedReservation`, the pad classes, the selectors and the v0.5 transaction
  classes are unchanged.
- **Bytecode:** `javap -c -p` on the mapped Forge 47.4.10 jar; appendix A lists what was read.

**Notation.** `J` is `src/main/java/io/github/sunthemoon/advancedrocketrycommunity`. L is a logical rocket. T is its
landed transfer record in the checked journal `RocketTransferSavedData`: phase COMMITTED, settled in this session, with a
stationary arrival. E is the arrival entity that T names. P is the proposed session-only pending entry. "Acknowledged"
means the journal writer's exact-generation acknowledgement. "E saved" means that E's entity section has been written
since the arrival. Proposed names do not exist yet.

## 1. What a landed rocket holds today

| Claim | Where it lives | What it protects | Lost when T is removed? |
|---|---|---|---|
| Geometry reservation | T in `journal.entries()`. It feeds the `reservations` list of `prepareLaunch` (`J/rocket/server/RocketTransferService.java:89-92`), which all three selectors use (`J/rocket/transfer/RocketLandingPadSelector.java:91,162`, `NamedLandingPadStaging.java:39`), and the overlap check of `put` (`J/rocket/flight/persistence/RocketTransferSavedData.java:608-621`). | Only the arrival of another flight. Assembly never consults the transfer journal. | Yes: the reservation is T itself. |
| Journal slot | `entries().size()` against `MAX_ACTIVE_TRANSFERS` (`RocketTransferService.java:82`). | Capacity. | Yes. |
| Named-pad claim | `LandingPadIndex`. | A station pad while a flight is under way. | **Nothing to lose.** The claim is released when the landing commits (`updatePhase`, `RocketTransferService.java:686-691`); recovery releases it again (`J/rocket/server/RocketTransferRecoveryService.java:378,390`); and `LandingPadService.claim` (`J/station/pad/LandingPadService.java:86-99`) treats a claim whose transfer is COMMITTED or absent as stale. |
| Settled classification | `settledTransfers`. | Resource interlocks. | Session state only. |
| Loaded-rocket occupancy | `RocketLandingPadSelector.unavailable` (lines 243-250) and `NamedLandingPadOccupancy` (lines 36-37), at selection and again at arrival (`RocketTransferService.java:565-568`). | Any rocket whose entity section is loaded. | No. |

Root's direction offered the named-pad claim as an example of a claim to re-establish. The code shows that a landed
arrival holds no named-pad claim when it is disassembled, so there is none to re-establish. The only claim that F8
loses is the journal-backed geometry reservation and its slot. Section 4 keeps both in RAM through the request and, when
restoration fails cleanly, writes the identical T back.

Without T, a rocket is protected only by the loaded-rocket occupancy check. That is already the case for every rocket
that was assembled in place and never flew. The check misses a rocket whose entity section is not loaded (W13-N1).
[W83](../TASK-W83-ARRIVAL-ENTITY-READINESS.md), parked until the W11b2 merge, makes an arrival wait until the entity
sections of every footprint chunk are loaded. It still misses a rocket whose entity lies outside the footprint while
its structure overlaps it.

## 2. Today's order, and vanilla's two stores

**Today's order.**
- `executePlayer` and `disassembleForReleaseTest` call `releaseLandedReservation` first
  (`J/rocket/server/RocketDisassemblyService.java:136,164`).
- With T present, that call removes T in RAM and queues a continuation without checking that it was queued:
  `afterAcknowledged`'s result is ignored (`RocketTransferService.java:438-443`), the P1-N2 pattern. It then answers
  REGION_BUSY.
- A second request after the acknowledgement runs the one-tick v0.5 transaction
  (`J/rocket/transaction/RocketDisassemblyTransaction.java:46-268`).

Between the acknowledgement and the second request, which may never come, disk holds no T:
- a crash leaves E as a parked rocket without its reservation if E was saved, and loses the rocket if it was not;
- an obstruction found by the second request (TARGET_OCCUPIED) leaves the same state live.

These are F8's two effects, plus an unbounded crash window.

**Two stores (checked with `javap`, appendix A).** Terrain and entities are saved separately.
- **Terrain** is saved incrementally. In every server tick that has time left, `ChunkMap.tick` calls `processUnloads`.
  That method saves up to 20 dirty visible chunks through `saveChunkIfNeeded`, at most once per chunk per 10,000 ms.
  `ChunkMap.save` posts `ChunkDataEvent.Save` before the terrain write and never touches entity storage. `/save-off`
  stops this path.
- **Entity sections** are written only by `ServerLevel.save` (autosave every 6,000 ticks, `save-all`, stop) or when
  their chunk unloads. Both hand the section to an asynchronous IO worker. `PersistentEntitySectionManager.tick` only
  loads and unloads.

Consequences:
- After **any** v0.5 disassembly, landed or not, of a rocket whose entity was saved earlier: a process stop after the
  first incremental terrain save and before the next entity save leaves the old entity and the saved restored blocks
  on disk together.
- After any assembly, the reverse window can lose the rocket.

This class predates W11b2 and F8, and it is why W85-R1 is not a landed-only problem. The base order before W11b2
(restore first, then remove T) was worse. Restored blocks could be saved while T was still on disk, and ordinary
recovery would then recreate the rocket over its own blocks ([W77](W77.md)).

## 3. Options

| | M: minimal (recommended) | F: full protocol |
|---|---|---|
| Durable change | None: no new record, key, schema or root. | A durable "disassembly under way" record, kept until restoration is proved on disk. |
| What it must answer | F8's two effects, without making any crash case worse than today. | W85-R1 to W85-R6: separate proof of terrain and of entity removal before T is retired (R1); no reactivation from block contents, and positive, complete exposure evidence before retirement (R2, R3); one recovery owner for the v0.5 transaction that the record names (R4); whole-world startup refusal on older jars, stated for its new record schema (R5); a reservation kept through pending removal, and an exactly-once release contract (R6). |
| Feasibility | One leaf of ordinary server code. | R1 needs proof that an entity section reached disk. Forge has no entity-section save event: `ChunkDataEvent.Save` covers terrain and fires before the write, and `LevelEvent.Save` fires after `autoSave` has only queued the IO. The proof would need W11b1's own-store entity decorator in every world, or a mixin, which AGENTS.md section 3.3 forbids without an ADR. R2 and R3 need restored resources that cannot escape while an undo is still possible, and restored blocks in a live world cannot give that. The review says this calls for a new scope or ADR decision, not a P1-F8 leaf. |
| What it closes beyond M | Nothing; M is the baseline. | The windows of section 2, for landed arrivals only. They stay open for every rocket assembled in place. |
| Cost | About 300 lines, one GameTest run and 24 native starts (section 7). | A new persisted format with a startup-refusal risk, a native entity-store proof, v0.5 integration, installed-downgrade tests, and more than revision 1's 33 starts. |

**Recommendation: M.** It closes F8 for every failure that leaves the world unchanged, and it fixes the first answer.
Its residuals (section 5) are narrower than today's. The widest of them belongs to the v0.5 class of section 2, which
a landed-only protocol could not close.

## 4. Design M

**M0. Request (server thread, one tick).** `validateRocket` runs as today. If L has no T, today's one-tick path runs
unchanged. If T is present, all of the following must hold. Otherwise the request answers the failing code, changes
nothing and keeps T.
- T is a schema-2 record with no `native_transfer`, and `isReplaceableLandedAuthority(E, T)` holds. Governed rockets
  never reach this point: `resourceMutationAllowed` requires `RocketEscrowGate.idle`, which is `escrowIdle` and refuses
  any ARMED entry (`J/resourceescrow/RocketEscrowGate.java:11-13,25-38` `@60cf89bf`), and record-3 data is never legacy authority
  (`J/rocket/server/RocketTransferEscrowRules.java:20` `@60cf89bf`). The explicit check keeps M3's re-insert to
  schema-2 records by construction.
- The bay gate allows `REMOVE_JOURNAL`, as today.
- `journal.canQueueEffect()` holds, so the continuation is reserved before any mutation (P1-N2's rule).
- No P exists for L.
- **The restoration preflight passes.** These are the existing no-mutation checks of `RocketDisassemblyTransaction.execute`
  (lines 68-123): the snapshot region, the dimension, a fully loaded region, the entity identity and hash,
  `canRestoreSnapshot` and an empty target. They move into one read-only method that `execute` keeps calling, so the two
  cannot drift. An obstruction, an unloaded region or an unsupported block entity now answers its code with T kept. The
  commonest F8 failure no longer releases anything.

**M1. Release.**
- Record P: T (the record value), E's confirmation quote (entity, owner, snapshot, content hash, flight data and
  position), the requester (none for the release-test hook), the consented fuel amount and the request game time.
- Call `journal.remove(T)`, then queue the continuation with `afterAcknowledged(server, L, effect, discarded)`. After M0
  it cannot be refused.
- Answer DISASSEMBLY_PENDING. The confirmation token has already been consumed; P carries the consented amount.

While P exists and T is absent from RAM:
- `prepareLaunch` adds T's destination region to `reservations` and counts P toward `MAX_ACTIVE_TRANSFERS`. Both
  precede W11b2's governed branch, so ordinary and governed launches see the same reservation (W85-R6).
- A repeated disassembly request answers DISASSEMBLY_PENDING.
- E's resources stay locked. `resourceMutationUnproved(L)` holds until the removal is acknowledged, and
  `resourceMutationAllowed` also refuses while P exists.

P lives in the transfer service, holds at most 64 entries (one per removed T), and is emptied by `clear()` at server
stop.

**M2. Restore, in the removal's exact-acknowledgement continuation.** It runs on the server thread, inside the
`pollWriter` call that publishes the acknowledgement.
1. Audit `landed_reservation_released` once, as today, and drop T from the session sets.
2. Restore only if all of the following hold. Otherwise go to M3 without restoring.
   - The game time is at most 200 ticks after the request (`RocketDisassemblyConfirmations.LIFETIME_TICKS`).
   - E is alive, loaded and still equal to P's quote.
   - The bay gate still allows `REMOVE_JOURNAL`.
3. Run today's `execute`: the v0.5 transaction, inside one tick.
   - **SUCCESS:** send the existing "disassembly committed" and fuel messages to the requester if online, log
     `ARCE_ROCKET_FUEL_DISCARDED` as today, and drop P.
   - **Failure without mutation, or ROLLED_BACK** (every placed block removed and E still matching, in the same call): go
     to M3. No save can have seen a restored block, because placement and rollback share one server-thread call.
   - **ROLLBACK_FAILED:** do not re-insert T. With part of the rocket restored, T on disk could let ordinary recovery
     recreate the rocket over blocks after a crash. The v0.5 FAILED record goes to `RocketTransactionRecoveryService`
     exactly as today, as its only recovery owner. Log one WARN `ARCE_LANDED_RESERVATION_NOT_RESTORED
     reason=rollback_failed`, send ROLLBACK_FAILED and drop P.

The continuation catches its own exceptions. One thrown before `execute` starts goes to M3; one thrown after it is
treated as ROLLBACK_FAILED.

**M3. Re-insert T.**
- Preconditions: L has no record in RAM; either E is loaded and `isReplaceableLandedAuthority(E, T)` holds, or E is not
  loaded (its unload stored it, and ordinary recovery classifies T when it loads again); `canQueueEffect()` holds. The
  last always holds here: the journal is acknowledged-current, and the removal's own slot has just been freed.
- Call `journal.put(T)` with the identical schema-2 record, and queue its continuation. `put`'s capacity and overlap
  checks still apply. Because P kept the reservation and the slot (M1), no new flight can have taken them.
- On the exact acknowledgement: if E is loaded and matches, add T to the settled set as recovery does
  (`RocketTransferRecoveryService.java:372`); otherwise leave T to ordinary recovery. Audit `landed_reservation_restored`
  and drop P.
- The player received the failure code at M2 (for example TARGET_OCCUPIED), or REGION_BUSY when M2 did not restore. The
  next request works once the cause is gone.
- If a precondition fails or `put` throws, log one WARN `ARCE_LANDED_RESERVATION_NOT_RESTORED reason=<journal|mismatch>`
  and drop P.

**Discarded continuations.** The journal discards a continuation in three cases (`RocketTransferSavedData.java:428-451`,
`478-510`): a later generation is acknowledged first, after an UNKNOWN or failed write; the journal stops or aborts; or
the root becomes unreadable, which also clears RAM, so `put` then refuses.
- **A discarded removal continuation** re-inserts T as in M3. At a normal stop, `stop()` sets `stopping` and runs the
  discards before its final write (line 497), so `put(T)` alone puts T into that final write; no continuation is
  queued while stopping. If the last write failed or ended UNKNOWN, `stop()` skips its final write (`!failedWrite`), and
  disk keeps whichever generation reached it.
- **A discarded re-insert continuation** only drops P: T is already in RAM and goes into the next generation or the
  final write.

**Hooks.**
- `disassembleForReleaseTest` returns DISASSEMBLY_PENDING for a landed arrival, and logs `ARCE_RELEASE_TEST_DISASSEMBLY
  ... code=<result>` when M2 or M3 finishes. Its command should print "pending" for that code. The smoke script
  `scripts/run_v130_adapter_flight_smoke.py` (lines 513-519) already waits for the SUCCESS line and the
  `landed_reservation_released` audit from a start index taken before the command, so its waits need no change.
- W44's test hook `releaseLandedReservationForFirstEventTest` keeps today's plain removal. FM12a of
  `scripts/run_v180_first_event_moon_cuts.py` is unchanged.

**New text.** One validation code, DISASSEMBLY_PENDING, with English and Chinese entries.

## 5. Crash points and what a restart finds

Each cut pauses at a probe boundary and kills the process. A cut is followed by two restarts.

| Cut | Journal on disk | World on disk | Both restarts find |
|---|---|---|---|
| K1: P recorded and T removed in RAM, before the removal's atomic move | T | before | Ordinary recovery keeps E, or rebuilds it if E was never saved (`RocketTransferRecoveryService.java:338-340`). The claim is kept, and no block exists. |
| K2: removal moved, at the start of its continuation (the directory force plus at most one tick) | no T | before | E saved: a parked rocket without its reservation, protected by occupancy only. E never saved: **the rocket is lost.** |
| K3: clean failure, re-insert queued, before its atomic move | no T | before | As K2. The window is one more checked write (p95 about 125 ms at eight transfers), and longer while writes fail. |
| K4: re-insert moved, at the start of its continuation | T | before | As K1: the claim is kept, and a never-saved E is rebuilt. |
| K5: success, before any save | no T | before | E saved: E is back without its reservation, and no block was saved; the disassembly is undone. E never saved: **the rocket is lost.** |
| K6: success; some or all footprint terrain chunks saved incrementally; no entity save, no `save-all`, no unload | no T | the saved blocks, plus the old E if E was saved | E saved: **the old E and the saved blocks both exist** (the v0.5 class of section 2). E never saved: only the saved blocks, so the blocks of unsaved chunks are lost. Not detected. |
| K7: success, both stores saved (autosave, unload or stop) | no T | after | Steady state. |
| K8: ROLLBACK_FAILED, before any save | no T | before, plus a v0.5 FAILED record if one was saved | No T, so no transfer rebuild. `RocketTransactionRecoveryService` alone recovers any v0.5 record, as today. The reservation stays released. |
| K9: the removal write is refused past the 200-tick limit, then succeeds | T while refused; then no T; then T again | before | While refused: as K1. After the retry, M2 does not restore and M3 re-inserts; later kills land in K3 or K4. |
| K10: normal stop while P is pending | T, unless the last write failed or ended UNKNOWN | before, with E (the stop saves the world) | E with T: as K1. After a failed or UNKNOWN last write: E, with T or without it. |

Across the two restarts, the resource result must stay the same: one rocket or its blocks, never both and never neither.
The exceptions are the cells in bold above, which are recorded residuals. Ordinary recovery is idempotent for these
states: W77's retry keeps the acknowledged destination identity, so the second restart finds a rebuilt rocket and does
not repeat the rebuild.

**Compared with today.** K2 shrinks today's unbounded two-request window to at most one tick. K3 exists only after a
clean failure, where today the lost reservation is permanent. K5 and K6 are unchanged from today's second request and
from any v0.5 disassembly.

**W85-R4 and W85-R6 for this order.**
- No v0.5 record exists in K2 to K4: restoration has not started, or it rolled back completely in one call, so the v0.5
  recovery has nothing to own. After ROLLBACK_FAILED it is the only owner, because T is not re-inserted.
- The geometry reservation exists in RAM through P from M1 to the end of M3. It exists on disk except between the
  removal's and the re-insert's atomic moves.
- No named-pad claim is involved (section 1).
- Audit lines are session observations, at most one per transition per P. Nothing replays them after a restart, and none
  is a durability claim.

**Are K2, K3 and K5 acceptable as recorded residuals?** Recommended yes, for these reasons. K2 lasts at most one tick and
K3 one checked write. Both lose a rocket only when its arrival was never saved and the process stops abnormally. K5's
loss lasts until the footprint's next incremental terrain save, a matter of seconds under normal load. K6 is the general
v0.5 class. Root decides.

## 6. Closure against ADR-065

ADR-065 line 1318 reads: "F8: the landed reservation is released before a disassembly that can still fail." The W11b2
review's F8 has two effects.

**Closed by M.**
1. "The first disassembly attempt always returns REGION_BUSY": one request answers DISASSEMBLY_PENDING and completes
   without a second request.
2. "If disassembly then fails, the arrival has already lost its landed reservation", for every failure that leaves the
   world unchanged:
   - the failures that the preflight detects (obstruction, unloaded region, unsupported block entity, entity mismatch)
     release nothing;
   - after the release, a failure without mutation, a complete rollback, a late acknowledgement or a discarded
     continuation re-inserts the identical T. In the session, P holds the reservation throughout; on disk, it is back
     when the re-insert is acknowledged.
3. The release's continuation is reserved before T is removed, which closes the unchecked `afterAcknowledged` in
   `releaseLandedReservation`.

**Recorded residuals** (the proposed R-035 text is in section 9):
1. ROLLBACK_FAILED leaves T released, and the v0.5 recovery owns the outcome.
2. A re-insert that cannot be written leaves T released: the journal is blocked, full or overlapping, or E no longer
   matches T.
3. A process stop between the removal's and the re-insert's atomic moves (K2, K3) leaves T released. It loses the
   rocket if its arrival was never saved.
4. After a successful restoration, a process stop before the footprint's terrain save loses a never-saved arrival (K5).
   A stop after an incremental terrain save but before the next entity save keeps the old entity and the saved blocks
   (K6). Both belong to the v0.5 class of section 2, unchanged from today.

While T is released, the spot is protected only by the loaded-rocket occupancy check, and by W83's footprint readiness
once it lands, as for a rocket assembled in place. This lasts until the rocket launches again or is disassembled.

**Against the closure text of W11B3-DESIGN.md.** M meets three of its requirements:
- "keep claims through failure", for clean failures;
- "a successful restoration must fence old-transfer recovery before checked removal and release": the removal is
  acknowledged before any block is restored;
- "no rocket recreated over blocks": T is never on disk while restored blocks exist, because it is re-inserted only after
  a clean failure.

It does not meet "no lost claim" in every S2 cut: K2, K3 and K8 are residuals. Root decides whether that is enough for
admission. If it is, the closure text needs Root's amendment to name those cuts.

## 7. Tests

**A0 (JUnit, inside the ordinary unit run).**
- The pure M0 decision over T's presence, schema and native transfer, the bay gate, effect capacity, an existing P and the
  preflight result. It returns the plain path, a release or a refusal, and a refusal mutates nothing.
- The pure M2 and M3 decision over the deadline, E's state, the transaction result and the re-insert preconditions. It
  never re-inserts after ROLLBACK_FAILED and never restores after the deadline.
- The preflight shared with `execute`: the same fixture gives the same code from both.
- The journal:
  - `remove(T)` followed by `put(T)` encodes byte-identical record NBT and the same `RecordVersion`;
  - `resourceMutationUnproved(L)` holds until the re-insert is acknowledged;
  - the W44 outbox is identical across removal, re-insert, NOT_COMMITTED and UNKNOWN;
  - a detached journal that stops with a queued removal continuation writes T in its final capture.
- P: the 64-entry bound, its inclusion in `prepareLaunch`'s reservation and capacity inputs, and `clear()`.

**A1 (GameTests with real services and checked-file reloads, as in W77).**
- G1, obstruction at request: TARGET_OCCUPIED, T unchanged, no journal generation (W77's completed-write counter), and a
  second flight's selection still refused as reserved.
- G2, obstruction placed between the request and the acknowledgement with a delayed writer: DISASSEMBLY_PENDING, then
  TARGET_OCCUPIED. T is re-inserted byte-identical, acknowledged and settled, a second flight is still refused, and the
  next request after clearing succeeds.
- G3, rollback injected with `RocketFailureInjector`: as G2. G3b, ROLLBACK_FAILED: no re-insert, one WARN, and the v0.5
  record recovered.
- G4, success on the first request: one checked generation, blocks restored, E removed, the fuel messages, one
  `landed_reservation_released` audit and no REGION_BUSY.
- G5, delayed writer: a repeated request answers DISASSEMBLY_PENDING; refuelling and launch are refused; a second flight
  whose pad overlaps T's region is refused while T is absent from RAM (W85-R6); the slot is counted.
- G6, writer held past 200 ticks: no restoration, a re-insert, REGION_BUSY, and the next request works.
- G7, full effect queue at request: refused, nothing changed.
- G8, discarded removal continuation (UNKNOWN, then a later acknowledged generation): a re-insert.
- G9, a record-3 T from W11b2's fixtures: refused before release.
- G10, the release-test hook: DISASSEMBLY_PENDING, then the log line with SUCCESS.

The WARN lines that G3b and the re-insert failure test drive get expectations in `scripts/gametest_expected_log.json`.

**S2 (packaged dedicated server).** A new probe family `LANDED_DISASSEMBLY:<boundary>` pauses and the harness kills the
server. No cut forces a flush after the request; "E saved" is a precondition saved before the request. Every case
compares raw block, entity, inventory and journal NBT through two restarts.
- **Required:** K1; K2 with E never saved (its outcome is the recorded loss); K4 with E never saved (its outcome is the
  rebuild); K5 with E saved; K9; and K10, as a normal stop instead of a kill while K9's refusal holds. That is 6 cuts and
  18 starts. K9, K10 and K4 need one new mode, `LANDED_REMOVAL`, in the gated release-test policy behind
  `-Dadvancedrocketrycommunity.rocketJournalFailure` (`RocketJournalFailurePolicy.java`); production never sets it. If
  Root wants no new mode, G6 and G8 cover the same logic in A1, and K9 and K10 drop out.
- **Diagnostic, not PASS:** K6 with E saved (an incremental terrain save observed through `ChunkDataEvent.Save`, no
  `save-all`, no unload, and a kill before the next autosave), and K6 with E never saved and a two-chunk footprint of
  which one chunk was saved. That is 2 cuts and 6 starts.
- K3 has K2's disk state and K8 needs a new v0.5 injection seam, so neither gets a native cut; G2 and G3b cover them.
- At W11b3's rate of about 3.3 minutes per start, 24 starts take about **1.3 heavy-JVM lock hours**, plus one GameTest
  run of about 18 minutes (W77).

## 8. Integration

- **W11b2 record 3:** refused at M0. Only schema-2 records are re-inserted, so record-3 data never is. This matches the
  "Ordinary interlocks" row of design 2.
- **W44:** the removal and the re-insert are ordinary generations of the same writer and carry the first-Moon-landing
  outbox unchanged. ELECTED and DELIVERED are unaffected, and the FM12a hook is unchanged.
- **W80** (designed, not on the shared branch): the re-insert restores a version identical to the removed one, and W80's
  record version (checksum, phase and destination identity, [W80-DESIGN.md](W80-DESIGN.md) lines 137-138) cannot tell
  the two apart. Whichever of W80 and this leaf lands second adds a two-way exclusion: M0 refuses a T that a W80 batch
  names, and W80's admission refuses a logical rocket that has a P. After a restart no P exists, and T is either present
  or absent, so a session-only exclusion is enough.
- **F3:** a success writes one generation, as today, and a clean failure writes two. While F3 is open, each write pauses
  live flights for 2 to 3 ticks.
- **P1-N2:** M adopts its rule, reserving the continuation before mutation. `failBackToSource` itself stays with N2's
  leaf.
- **W83:** it protects the rocket in the residual cases (question 3).

## 9. Proposed ADR-065 note and R-035 text (for Root)

**ADR-065 implementation note.**

> **P1-F8 (W85, revision 2).** Disassembly of a landed arrival keeps the existing order: the checked removal of its
> transfer record T is acknowledged before any block is restored, so recovery never recreates the rocket over restored
> blocks. No record, key, schema or root is added, and older jars read the journal unchanged. A request first runs the
> restoration's no-mutation checks; a predictable failure answers its code and keeps T. Otherwise the service reserves
> a journal continuation, removes T, keeps T's destination region and journal slot reserved for new flights in a
> session-only pending entry (at most 64), and answers DISASSEMBLY_PENDING. On the removal's exact acknowledgement, and
> at most 200 ticks after the request, that continuation restores the rocket. A failure without mutation, a complete
> rollback, a late acknowledgement or a discarded continuation re-inserts the identical T, which restores the landed
> reservation when it is acknowledged. ROLLBACK_FAILED never re-inserts T, and the v0.5 recovery stays the only owner
> of its record. Residuals (R-035): ROLLBACK_FAILED, a re-insert that cannot be written, and a process stop between the
> removal's and the re-insert's atomic moves leave T released. The spot is then protected only by the loaded-rocket
> occupancy check, with W83's footprint readiness once merged, as for a rocket assembled in place. A process stop after
> the removal reaches disk loses an arrival whose entity section was never saved. After a successful restoration,
> vanilla's separate terrain and entity saves can leave the old entity and the saved blocks together until the next
> entity save; this is the existing v0.5 disassembly class. None of these is detected. This is a development
> implementation note, not owner acceptance or a Gate result.

**R-035 addition** (Chinese, as in the [risk register](../../../11-RISK-REGISTER.md)).

> F8（W85 修订 2）：着陆火箭拆解保持现有顺序，先确认移除转移记录 T，再恢复方块，因此恢复永远不会在已恢复的方块上重建火箭。
> 不新增记录、键、schema 或根，旧 jar 读取日志不受影响。请求先做恢复的无变更检查，可预见的失败直接答复并保留 T；否则先预留
> 日志续体，再移除 T，在会话内的待处理条目中为新航班保留 T 的目的地区域和日志名额（至多 64 条），答复 DISASSEMBLY_PENDING，
> 并在移除确认后（请求后至多 200 tick）自动恢复。无变更的失败、完整回滚、确认过迟或续体被丢弃时，重新写入同一个 T，确认后
> 着陆保留恢复；ROLLBACK_FAILED 不重新写入，由 v0.5 恢复单独处理。影响范围：单个着陆火箭及其目的地区域。残余与持续时间：
> ROLLBACK_FAILED、重新写入无法完成（日志阻断、已满、区域重叠或实体不再匹配），或进程在移除与重新写入两次原子替换之间停止
> （通常为一次检查写入，写入失败期间更长）时，T 保持释放，直到该火箭下次发射或被拆解；期间该位置只靠已加载火箭的占用检查
> 保护（W83 合入后加上足迹实体段就绪），与原地组装的火箭相同。移除写盘后进程异常停止时，若到达后实体段从未保存，火箭丢失。
> 成功恢复后，原版地形与实体分开保存：足迹地形保存之前停止，会丢失从未保存的到达；地形增量保存之后、下一次实体保存（每
> 6,000 tick 的自动保存或区块卸载）之前停止，旧实体与已保存的方块同时存在，与所有 v0.5 拆解同类。以上情况均不被检测。日志
> 量：每次拆解每个转换一条审计 INFO；只有 ROLLBACK_FAILED 或重新写入失败时，每次请求一条 WARN
> （`ARCE_LANDED_RESERVATION_NOT_RESTORED`）；继承的日志写入错误不变。恢复：无需操作即可继续游戏；火箭丢失或重复只能在停服
> 后用一致的整世界备份离线修复，不得单独恢复日志。

## 10. Open questions, each with a recommended answer

1. **Re-insert T after a clean failure, or accept the lost reservation?** Re-insert. It uses only `put` of the identical
   schema-2 record. It is safe because no restored block can exist after a clean failure. It closes F8's main effect
   in the session.
2. **The 200-tick limit?** Keep the confirmation's own lifetime. A player who clicked should not see blocks appear much
   later, and the write of the largest legal journal (about 117 ticks, ADR-065) fits.
3. **Make W83 a prerequisite for admission?** Recommended yes, because W83 is what protects a rocket in the residual
   cases while its entity section is unloaded. Root decides.
4. **Recheck the requester's range in M2?** No. Authorization and range are checked at M0, and M2 requires E to equal
   the quote. The release-test hook has no player.
5. **Register the two-store class of section 2 for all assembly and disassembly?** Recommended, under R-006's scope and
   separately from F8. This design does not change it.

## Appendix A. Bytecode read

Jar: `C:/Users/Administrator/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.10_mapped_official_1.20.1/forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`,
SHA-256 `95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`. Tool:
`C:/Program Files/Java/jdk-17.0.7/bin/javap.exe -c -p`. Nothing was extracted and no game code was run.

| Class and method | Bytecode offsets | What it shows |
|---|---|---|
| `ChunkMap.tick(BooleanSupplier)` | `ServerLevel.noSave` at 38, `processUnloads` at 46 | Incremental saves run every tick unless saving is off. |
| `ChunkMap.processUnloads` | `visibleChunkMap.values()` at 185-188, `bipush 20` at 197, `saveChunkIfNeeded` at 232 | Up to 20 visible chunks are saved per tick while the tick has time left. |
| `ChunkMap.saveChunkIfNeeded` | `save` at 80, `10000L` cooldown at 101 | At most one save per chunk per 10 s. |
| `ChunkMap.save(ChunkAccess)` | `ChunkSerializer.write` at 111, `ChunkDataEvent.Save` posted at 147, `write` at 157 | The event precedes the terrain write; there is no entity storage call. |
| `PersistentEntitySectionManager.tick` | `processPendingLoads` at 1, `processUnloads` at 5 | No entity save in the tick. |
| `PersistentEntitySectionManager.autoSave`, its lambda, and `saveAll` | lambda: `processChunkUnload` at 28 or `storeChunkSections` at 42; `saveAll`: `flush(true)` at 19 and 49 | Level saves hand entity sections over; only `saveAll` flushes. |
| `EntityStorage.storeEntities` | `IOWorker.store` at 37 and 101 | The entity write is asynchronous. |
| `ServerLevel.save(ProgressListener, boolean, boolean)` | `ServerChunkCache.save` at 49, `saveAll` at 60 or `autoSave` at 70, `LevelEvent.Save` posted at 84 | Entities are saved here, or at unload. |
| `MinecraftServer.tickServer` | `sipush 6000` and `irem` at 62-65, `saveEverything` at 96 | Autosave every 6,000 ticks. |
