# W85: landed disassembly without a lost claim (P1-F8), revision 3

**Proposed design, revision 3, 2026-10-16. Not implementation, review, risk acceptance or Gate evidence.**
Assignment: [TASK W85R2](../TASK-W85R2-P1-F8-REVISION-3.md); record: [W85](W85.md), section "Revision 3".

Revision 3 applies Root's decisions 1 to 4 on the second review to revision 2 (commit `6ee8dc5b`). It keeps revision
2's design M and its structure, and corrects it finding by finding. The inputs are:
- the second independent review `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review-2/REVIEW.md`, SHA-256
  `63c9477581ca2a14b16f973f2de3357325ce64e1952e3b559194622b250e0ffa`, verdict APPROVE-WITH-CHANGES;
- Root's decisions `D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review-2/ROOT-DECISIONS.md`, SHA-256
  `1f8957301ff174284979ed67b70e696261de7911755125c1d875a1b08cec1b27`.

Both hashes were checked in this session. The second review's findings W85R2-N1 to W85R2-N6 are called N1 to N6 here,
and each change in this revision names the finding that it answers. Revision 2 answered the first review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/REVIEW.md`, SHA-256
`4dbb7f9ee948f20f7dce167534d1a583242f2adba3a329e20ffa14aa3ba1feb8`, findings W85-R1 to W85-R6) and Root's direction on
it (`D:/GitHub/ARCE-Task-Evidence/root-queue/W85-review/ROOT-DECISIONS.md`, SHA-256
`f808a2460e58969509e571f498e553131ba0aedf947d15a35c115269927f7351`). Revision 1 (commit `08474506`) stays superseded as a
whole. Git history keeps both earlier texts. The closure to meet is [W11B3-DESIGN.md](W11B3-DESIGN.md) section 1, row
P1-F8, and the F8 line of "Open before W11b3 or any admission" in
[ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md) (line 1318).

**Baselines.**
- **Shared code:** this branch at `18719548`, where Root merged the shared branch in again. Since revision 2's baseline
  `acc295b2`, only twelve GameTest classes under `J/gametest/` changed (W68 and W84), and none of them is cited here.
  The `src` tree of `acc295b2` equals revision 1's baseline `4a9f892a`. Citations are at `acc295b2` unless they say
  otherwise, and they hold unchanged at `18719548`.
- **W11b2:** read through Git only, at `60cf89bf` (continuation 23). The head of `codex/v1.8.0-w11b2-rocket-integration`
  is now `489b650e`, and `git diff --stat 60cf89bf 489b650e` over every file cited here is empty. Against the shared
  branch, W11b2 changes the code that this design touches in these places:
  - the governed branch of `prepareLaunch`, inserted after line 125, that is, after the capacity check and the
    reservation list, with the new `RocketGovernedLaunchBoundary` and the governed source's dispatch and cleanup in the
    new `RocketGovernedSourceDispatcher`;
  - the native-transfer filters in `tick`, in recovery `step` (line 106 `@60cf89bf`) and in `tryReconnect`;
  - record 3 in the journal codec;
  - in the journal, the read-only insert check `validateNewRecord`, `actionAuthorityAvailable`, `acknowledgedLease`
    and a `volatile` mutation sequence. Revision 2 omitted these; the second review corrected the list.

  `RocketDisassemblyService`, `RocketDisassemblyConfirmations`, `releaseLandedReservation`, the pad classes, the
  selectors and the v0.5 transaction classes are unchanged. The leaf integrates against the merged source and keeps the
  W11b2 helpers.
- **Bytecode:** `javap` on the mapped Forge 47.4.10 jar. Appendix A lists what revisions 2 and 3 read.

**Notation.** `J` is `src/main/java/io/github/sunthemoon/advancedrocketrycommunity`. L is a logical rocket. T is its
landed transfer record in the checked journal `RocketTransferSavedData`: phase COMMITTED, settled in this session, with a
stationary arrival. E is the arrival entity that T names. P is the proposed session-only pending entry, and S is the
pending snapshot of E that P keeps (N5). "Acknowledged" means the journal writer's exact-generation acknowledgement. "E
saved" means that E's entity section, with E in it, has been written since the arrival. The "restoration latch" is P's
one-way flag, set before `execute` is entered (N1). A "certified clean rollback" is a transaction result that M2
classifies as leaving the world unchanged (section 4, M2). Proposed names do not exist yet.

## 1. What a landed rocket holds today

| Claim | Where it lives | What it protects | Lost when T is removed? |
|---|---|---|---|
| Geometry reservation | T in `journal.entries()`. It feeds the `reservations` list of `prepareLaunch` (`J/rocket/server/RocketTransferService.java:89-92`), which all three selectors use (`J/rocket/transfer/RocketLandingPadSelector.java:91,162`, `NamedLandingPadStaging.java:39`), and the overlap check of `put` (`J/rocket/flight/persistence/RocketTransferSavedData.java:608-621`). | Only the arrival of another flight. Assembly never consults the transfer journal. | Yes: the reservation is T itself. |
| Journal slot | `entries().size()` against `MAX_ACTIVE_TRANSFERS` (`RocketTransferService.java:82`). | Capacity. | Yes. |
| Named-pad claim | `LandingPadIndex`. | A station pad while a flight is under way. | **Nothing to lose.** The claim is released when the landing commits (`updatePhase`, `RocketTransferService.java:686-691`); recovery releases it again (`J/rocket/server/RocketTransferRecoveryService.java:378,390`); and `LandingPadService.claim` (`J/station/pad/LandingPadService.java:86-99`) treats a claim whose transfer is COMMITTED or absent as stale. |
| Settled classification | `settledTransfers`. | Resource interlocks. | Session state only. |
| Loaded-rocket occupancy | `RocketLandingPadSelector.unavailable` (lines 243-250) and `NamedLandingPadOccupancy` (lines 36-37), at selection and again at arrival (`RocketTransferService.java:565-568`). | Any rocket whose entity section is loaded. | No. |

Root's direction offered the named-pad claim as an example of a claim to re-establish. The code shows that a landed
arrival holds no named-pad claim when it is disassembled, so there is none to re-establish. The second review confirmed
this, and added that a stale saved index entry after a crash is not a landed claim either; M never reclaims or clears a
successor flight's claim. The only claim that F8 loses is the journal-backed geometry reservation and its slot. Section 4
keeps both in RAM through the request and, when restoration never started or was rolled back cleanly, writes the
identical T back.

Without T, a rocket is protected only by the loaded-rocket occupancy check. That is already the case for every rocket
that was assembled in place and never flew. The check misses a rocket whose entity section is not loaded (W13-N1).
[W83](../TASK-W83-ARRIVAL-ENTITY-READINESS.md), parked until the W11b2 merge, makes an arrival wait until the entity
sections of every footprint chunk are loaded. It still misses a rocket whose entity lies in an unloaded chunk outside the
footprint while its structure overlaps the footprint.

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
- **Terrain** is saved per chunk. In every server tick that has time left, `ChunkMap.tick` calls `processUnloads`. That
  method saves up to 20 dirty visible chunks through `saveChunkIfNeeded`, which skips a chunk that was saved less than
  10,000 ms earlier. Autosave and `save-all` without `flush` also save terrain through `saveChunkIfNeeded`, so they skip
  a chunk inside that cooldown too (N3). Only `save-all flush` and a normal stop save every chunk regardless of the
  cooldown. `ChunkMap.save` posts `ChunkDataEvent.Save` before the terrain write and never touches entity storage.
- **Entity sections** are written only by `ServerLevel.save` (autosave every 6,000 ticks, `save-all`, stop) or when
  their chunk unloads. Both hand the section to an asynchronous IO worker. `ServerLevel.save` stores the entity sections
  of every loaded chunk, whether or not its terrain pass skipped those chunks. `PersistentEntitySectionManager.tick` only
  loads and unloads.
- **`/save-off`** stops the incremental path. It also makes autosave and a normal stop skip the level save entirely;
  only `save-all`, which is forced, still saves.

Consequences:
- After **any** v0.5 disassembly, landed or not, of a rocket whose entity was saved earlier: a process stop after the
  first incremental terrain save and before the next entity save leaves the old entity and the saved restored blocks
  on disk together.
- After any v0.5 disassembly, the opposite order can happen as well (N3). Suppose that a footprint chunk was saved less
  than 10 s before the disassembly. The next autosave then writes the entity section without the entity but skips that
  chunk's restored terrain. A process stop before the chunk's next terrain save leaves neither the entity nor the blocks
  on disk, even though the entity had been saved.
- After any assembly, the reverse window can lose the rocket.

This class predates W11b2 and F8, and it is why W85-R1 is not a landed-only problem. The base order before W11b2
(restore first, then remove T) was worse. Restored blocks could be saved while T was still on disk, and ordinary
recovery would then recreate the rocket over its own blocks ([W77](W77.md)).

## 3. Options

| | M: minimal (recommended) | F: full protocol |
|---|---|---|
| Durable change | None: no new record, key, schema or root. | A durable "disassembly under way" record, kept until restoration is proved on disk. |
| What it must answer | F8's two effects, without making any crash case worse than today, and the second review's N1 to N6. | W85-R1 to W85-R6: separate proof of terrain and of entity removal before T is retired (R1); no reactivation from block contents, and positive, complete exposure evidence before retirement (R2, R3); one recovery owner for the v0.5 transaction that the record names (R4); whole-world startup refusal on older jars, stated for its new record schema (R5); a reservation kept through pending removal, and an exactly-once release contract (R6). |
| Feasibility | One leaf of ordinary server code. | R1 needs proof that an entity section reached disk. Forge has no entity-section save event: `ChunkDataEvent.Save` covers terrain and fires before the write, and `LevelEvent.Save` fires after `autoSave` has only queued the IO. The proof would need W11b1's own-store entity decorator in every world, or a mixin, which AGENTS.md section 3.3 forbids without an ADR. R2 and R3 need restored resources that cannot escape while an undo is still possible, and restored blocks in a live world cannot give that. The first review says this calls for a new scope or ADR decision, not a P1-F8 leaf. |
| What it closes beyond M | Nothing; M is the baseline. | The windows of section 2, for landed arrivals only. They stay open for every rocket assembled in place. |
| Cost | About 400 lines, one GameTest run and 27 native starts (section 7). | A new persisted format with a startup-refusal risk, a native entity-store proof, v0.5 integration, installed-downgrade tests, and more than revision 1's 33 starts. |

**Recommendation: M.** It closes F8 for every failure that leaves the world unchanged, and it fixes the first answer.
Its residuals (section 5) are narrower than today's. The widest of them belong to the v0.5 class of section 2, which a
landed-only protocol could not close. The second review approved this direction and asked for the corrections that
this revision makes.

## 4. Design M

**M0. Request (server thread, one tick).**
- **Status of an existing P (N6).** A P makes `resourceMutationAllowed` false, so `validateRocket` would answer
  REGION_BUSY (`J/rocket/server/RocketDisassemblyService.java:115-116`) before any check for P. A status branch therefore
  runs first, and only when a P exists for L:
  - the player path runs today's actor checks (alive, not a spectator, same level), then the range check, then the
    identity checks at the top of `validateRocket` (lines 108-114: server level, server thread, alive, loaded chunk,
    operational), then the owner-or-permission check (lines 100-103), and answers DISASSEMBLY_PENDING;
  - the release-test hook runs the same identity checks and answers DISASSEMBLY_PENDING;
  - the branch only reports status. It mutates nothing, issues no offer, never bypasses the resource lock and never
    starts a second operation.

  Without a P, today's order is unchanged. A confirmation keeps `take` first (line 63), so a replayed or unknown token
  still answers `disassembly_confirmation_invalid` before any check for P. A valid token whose rocket has a P reaches the
  branch through `validatePlayer` and answers DISASSEMBLY_PENDING. Its token is spent, as every confirmation that fails
  validation is spent today, and it starts nothing.
- Otherwise `validateRocket` runs as today. If L has no T, today's one-tick path runs unchanged. If T is present, all of
  the following must hold. Otherwise the request answers the failing code, changes nothing and keeps T.
  - T is a schema-2 record with no `native_transfer`, and `isReplaceableLandedAuthority(E, T)` holds. Governed rockets
    never reach this point: `resourceMutationAllowed` requires `RocketEscrowGate.idle`, which is `escrowIdle` and
    refuses any ARMED entry (`J/resourceescrow/RocketEscrowGate.java:11-13,25-38` `@60cf89bf`), and record-3 data is
    never legacy authority (`J/rocket/server/RocketTransferEscrowRules.java:20` `@60cf89bf`). The explicit check keeps
    M3's write-back to schema-2 records by construction.
  - The bay gate allows `REMOVE_JOURNAL`, as today.
  - `journal.canQueueEffect()` holds, so the continuation is reserved before any mutation (P1-N2's rule).
  - **The restoration preflight passes.** These are the existing no-mutation checks of
    `RocketDisassemblyTransaction.execute` (lines 68-123): the snapshot region, the dimension, a fully loaded region,
    the entity identity and hash, `canRestoreSnapshot` and an empty target. They move into one read-only method that
    `execute` keeps calling, so the two cannot drift. An obstruction, an unloaded region or an unsupported block entity
    now answers its code with T kept. The commonest F8 failure no longer releases anything.
- **The pending snapshot S (N5).** S holds E's entity ID, owner, snapshot ID, snapshot content hash, flight data,
  position and fuel-catalog signature: the fields of `RocketDisassemblyConfirmations.Quote` (lines 15-35). Unlike
  `Quote`, S accepts zero fuel; otherwise it checks the same formats (hash and signature formats, finite position).
  Consent stays separate from S:
  - none for a zero-fuel request, which bypasses confirmation today (`RocketDisassemblyService.java:36-56`);
  - the taken `Quote` for a confirmed fueled request, which S must equal;
  - the expected discard amount for the release-test hook, which must equal the fuel (lines 158-163).

  `Quote` and its existing positive-fuel test are unchanged.

**M1. Release.**
- Record P: L, T (the record value), S, the requester (none for the release-test hook), the consent, the request game
  time, the stage RELEASED and an unset restoration latch.
- Call `journal.remove(T)`, then queue the continuation with `afterAcknowledged(server, L, action, discarded)`. After M0
  it cannot be refused.
- Answer DISASSEMBLY_PENDING. A confirmed fueled request has already spent its token, and P carries the consented
  amount.

While P exists:
- In the stages RELEASED and RESTORING, T is absent from RAM. `prepareLaunch` then adds T's destination region to
  `reservations` and counts P toward `MAX_ACTIVE_TRANSFERS`. Both precede W11b2's governed branch, so ordinary and
  governed launches see the same reservation (W85-R6). In the stage WRITE_BACK, T itself is in `entries()` again, so P
  is not counted a second time (N2).
- A repeated disassembly request answers DISASSEMBLY_PENDING through M0's status branch (N6).
- E's resources stay locked in every stage. `resourceMutationAllowed` refuses while P exists, and
  `resourceMutationUnproved(L)` also holds until the last change to L's record is acknowledged.

P lives in the transfer service and holds at most 64 entries (one per removed T). It is emptied by `clear()` at
`ServerStoppedEvent` (`J/AdvancedRocketryCommunity.java:481-492`). That runs after the journal's `stop()` at
`ServerStoppingEvent` (`J/rocket/flight/persistence/RocketJournalLifecycle.java:29-32`), so the discards of a stop still
find P.

**P's stages (N1, N2).** Stages only move forward. Each callback captures its P and acts only while the transfer
service's entry for L is that same P, in the stage that the callback expects. Every other call does nothing, so cleanup
is idempotent, also after P has ended.

| Stage | T in RAM | Acknowledged generation | Journal on disk | Restoration | Next |
|---|---|---|---|---|---|
| RELEASED, before the removal's atomic move | Absent. | Contains T. | T. | Not started. | M2 on the acknowledgement; M3 on a discard. |
| RELEASED, after the move, before the continuation runs | Absent. | Without T after COMMITTED; none after UNKNOWN. | No T after COMMITTED; T or no T after UNKNOWN. | Not started. | As above. |
| RESTORING, inside one M2 call | Absent. | Without T. | No T. | Started; uncertain until M2 classifies the result. | End on SUCCESS; M3 only after a certified clean rollback; otherwise end without T. |
| WRITE_BACK | The identical T. | Without T until the write-back commits. | No T until its atomic move; T or no T after UNKNOWN. | Not started, or rolled back with certification. | End on the acknowledgement; stay, with no time limit, while the write fails. |
| Ended | P is removed. | | | | Nothing. |

**M2. Restore, in the removal's exact-acknowledgement continuation.** It runs on the server thread, inside a
`pollWriter` call: either the one at the start of each server tick (`RocketJournalLifecycle.java:22-28`) or the one
inside `retryChecked`.
1. If P is not current or not RELEASED, return true and do nothing. Otherwise audit `landed_reservation_released` once,
   as today, and drop T from the session sets.
2. Restore only if all of the following hold. Otherwise go to M3 without restoring, and return true.
   - The game time is at most 200 ticks after the request (`RocketDisassemblyConfirmations.LIFETIME_TICKS`). This is
     checked when M2 runs. It is not a timer, and it bounds nothing while the acknowledgement is late (N4).
   - E is alive and loaded, and its snapshot still equals S.
   - The bay gate still allows `REMOVE_JOURNAL`.
3. Set the restoration latch and the stage RESTORING (N1). Neither is ever undone. Then run today's `execute`: the v0.5
   transaction, inside one tick.
4. Classify the result.
   - **SUCCESS:** send the existing "disassembly committed" and fuel messages to the requester if online, log
     `ARCE_ROCKET_FUEL_DISCARDED` as today, and end P.
   - **Certified clean rollback:** the result is not a success, its code is not ROLLBACK_FAILED, its rolled-back count
     equals its changed count (both are 0 for a failure without mutation), and E is still alive, loaded and equal to S.
     The v0.5 rollback reports the original failure code with these counts, not a ROLLED_BACK code
     (`J/rocket/transaction/RocketDisassemblyTransaction.java:320-336`), so M2 classifies the result explicitly. Mark P
     certified and go to M3. No save can have seen a restored block, because placement and rollback share one
     server-thread call.
   - **Anything else** (ROLLBACK_FAILED, unequal counts, or E no longer matching): do not write T back. With part of the
     rocket restored, T on disk could let ordinary recovery recreate the rocket over blocks after a crash. The v0.5
     FAILED record goes to `RocketTransactionRecoveryService` exactly as today, as its only recovery owner. Log one WARN
     `ARCE_LANDED_RESERVATION_NOT_RESTORED reason=rollback_failed`, send the code and end P.
5. Return true from the continuation in every handled case, success or failure (N1). The journal runs the discarded
   callback whenever the action does not return true, so returning the transaction's success flag would send a handled
   failure into that callback.

**Exceptions (N1).** The action catches `RuntimeException`.
- One thrown before the latch is set goes to M3.
- One thrown after the latch is set, while P is still RESTORING, means that mutation is uncertain. Log one WARN
  `ARCE_LANDED_RESERVATION_NOT_RESTORED reason=uncertain`, end P without writing T back, and return true. This also
  covers a throw after SUCCESS, for example from a notification. A throw after P has moved on to WRITE_BACK or ended
  changes nothing more.
- An `Error` is not caught. `Effect.run` runs the discarded callback in its `finally`
  (`J/rocket/flight/persistence/RocketTransferSavedData.java:74-77`), and the `Error` then leaves `pollWriter`, which
  catches only `RuntimeException` (lines 443-447). `RocketJournalContinuationTest` (lines 57-77) already asserts both.
  The transaction's own handlers also catch only `RuntimeException` (`RocketDisassemblyTransaction.java:241-263`), so an
  `Error` after a placement leaves restored blocks behind. The latch is what stops the discarded callback from writing T
  back in that case.

**M3. Write T back.** M3 is the only step that puts T back. It acts only if P is current, T has not yet been written
back for P, and either the restoration latch is unset or M2 certified a clean rollback (N1). It has three outcomes (N2).
- **Preconditions.** L has no record in RAM. Either E is loaded and `isReplaceableLandedAuthority(E, T)` holds, or E's
  entity section at S's position is not loaded, so its unload stored E and ordinary recovery classifies T when it loads
  again. If that section is loaded and E is absent or changed, E was destroyed or altered, and the precondition fails.
  The insert checks then run before any change: the journal is operational, it holds no record with T's ID, it has a
  free slot, T conflicts with no authority, and, after the W11b2 merge, the candidate journal encodes within the fixed
  limits. These are the checks of `validateNewRecord` at `60cf89bf` without its `stopping` refusal, so that a stop can
  still carry T into its final write. `put` repeats its own checks before it changes anything
  (`RocketTransferSavedData.java:243-268`).
- **(a) Refused insert.** A precondition or an insert check fails, or `put` throws. Nothing has changed. Log one WARN
  `ARCE_LANDED_RESERVATION_NOT_RESTORED reason=<journal|full|overlap|mismatch>` and end P. T stays released (residual 2
  of section 6). This is the only M3 outcome that releases T.
- **(b) Queued write-back.** Call `journal.put(T)` with the identical schema-2 record, set the stage WRITE_BACK and,
  unless the journal is stopping, queue the write-back's continuation. `canQueueEffect()` holds at this point: M3 runs
  inside `pollWriter` with the journal acknowledged-current, and the removal's own effect slot was freed before its
  callback ran (lines 438 and 441). Because P kept the reservation and the slot (M1), no new flight can have taken them.
  - On the exact acknowledgement: if E is loaded and matches, add T to the settled set as recovery does
    (`J/rocket/server/RocketTransferRecoveryService.java:372`); otherwise leave T to ordinary recovery. Audit
    `landed_reservation_restored` and end P.
  - Until the write-back is acknowledged, T, P and every interlock stay, with no time limit. The writer keeps a failed
    write and retries it every 200 ticks (`J/rocket/flight/persistence/RocketJournalWriter.java:99-116`). The rocket
    cannot be refuelled, launched or disassembled, and a repeated request answers DISASSEMBLY_PENDING.
  - While F3 is open, the transfer service's tick also returns early whenever the journal is dirty and unacknowledged
    (`RocketTransferService.java:240`). Every live flight, recovery and reconnection then waits for as long as the
    write keeps failing. This is inherited from F3 and applies to any failing journal write, but each landed
    disassembly adds generations to it.
  - Nothing releases unacknowledged state to end the wait, and no timeout drops T or P. The wait ends when storage is
    repaired and a retry is acknowledged, or with a restart, which reads whichever generation reached the disk.
  - Disk outcomes: after NOT_COMMITTED the atomic move did not happen, and disk holds the previous generation, without
    T. After UNKNOWN the move may or may not have happened, so disk holds T or no T, and the journal claims neither.
  - Reporting is bounded. The journal's existing ERROR `ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED` appears once per failure
    episode (`RocketTransferSavedData.java:471-474`). At the start of its tick, before the return at line 240, the
    transfer service logs at most one WARN `ARCE_LANDED_DISASSEMBLY_UNACKNOWLEDGED logical=<L> stage=<removal|write_back>`
    per P and stage, the first time that it sees a failed write while P waits in that stage. No line repeats.
- **(c) Stop or abort.** Both run the discarded callbacks with `stopping` already set (lines 497 and 510). M3 then only
  calls `put(T)`, because no continuation can be queued. "Discarded continuations" below gives the outcome.
- The player received the failure code at M2 (for example TARGET_OCCUPIED), or REGION_BUSY when M2 did not restore.
  The next request works once the cause is gone and P has ended.

**Discarded continuations (N1).** The journal runs a continuation's discarded callback in four cases, not the three that
revision 2 listed:
1. the action returns false, or throws a `RuntimeException` or an `Error` (`Effect.run`, lines 74-77);
2. a later generation is acknowledged before the continuation's own generation committed, after a coalesced capture or
   after an UNKNOWN or failed write (lines 437-438);
3. its generation committed, but L's record in RAM is no longer the one captured when it was queued (lines 442 and
   448);
4. the journal stops or aborts, or its root becomes unreadable, which also clears RAM so that `put` then refuses (lines
   478-489, 497 and 506-510).

The callbacks act as follows.
- **Removal callback.** If P is not current, or is already WRITE_BACK or ended, it does nothing. If the latch is set
  without a certified clean rollback, it logs the `reason=uncertain` WARN once, ends P and never calls `put`; while an
  `Error` unwinds, this is all it does. Otherwise it runs M3.
  - At a normal stop, `stop()` sets `stopping` and runs the discards before its drain and its final write (lines
    497-503), so `put(T)` puts T into that final write. If the drain gives up after three outcomes that are not
    COMMITTED (`RocketJournalWriter.java:109`), or the last write failed, `stop()` skips its final write (`!failedWrite`,
    line 500). Disk then keeps whichever generation reached it: no T after a committed removal, and T or no T after
    UNKNOWN.
  - At an abort without a stop, `put(T)` changes only RAM, the writer is aborted, and disk keeps whichever generation
    reached it.
  - When the root becomes unreadable, `put` throws, so the outcome is a refused insert.
- **Write-back callback.** It ends P. T is already in RAM. After case 2 the acknowledged later generation already
  contains T, and ordinary recovery settles it. After case 4, T goes into a normal stop's final write under the same
  condition as above.

**Hooks.**
- `disassembleForReleaseTest` returns DISASSEMBLY_PENDING for a landed arrival, also when a P already exists (N6), and
  logs `ARCE_RELEASE_TEST_DISASSEMBLY ... code=<result>` when M2 or M3 finishes. Its command should print "pending" for
  that code. The smoke script `scripts/run_v130_adapter_flight_smoke.py` (lines 513-519) waits for the SUCCESS line and
  the `landed_reservation_released` audit from a start index taken before the command. An asynchronous SUCCESS line fits
  these waits as long as no intermediate line uses the same receipt.
- The same script's fuel-discard wait (lines 516-517) expects `amount=<n> reason=confirmed_disassembly` at the end of
  the line, while the service logs `amount=<n> vector=<summary> reason=confirmed_disassembly`
  (`RocketDisassemblyService.java:180-183`). This mismatch predates W85. The leaf corrects the expression to the logged
  line, keeping the fuel assertion and its 30-second timeout, before the smoke run is cited as acceptance (question 6).
- W44's test hook `releaseLandedReservationForFirstEventTest` keeps today's plain removal. FM12a of
  `scripts/run_v180_first_event_moon_cuts.py` is unchanged.

**New text.** One validation code, DISASSEMBLY_PENDING, with English and Chinese entries. Two WARN keys,
`ARCE_LANDED_RESERVATION_NOT_RESTORED` and `ARCE_LANDED_DISASSEMBLY_UNACKNOWLEDGED`.

## 5. Crash points and what a restart finds

Each cut pauses at a probe boundary and kills the process. A cut is followed by two restarts. Terrain and entities are
listed separately (N3). "Old" terrain means that a footprint chunk on disk has no restored block; "new" means that it has
the restored blocks. "E" means that E's entity section on disk still holds E. "No E" means that the section on disk never
held E (E never saved) or that E's removal was saved.

| Cut | Journal on disk | Footprint terrain on disk | E's section on disk | Both restarts find |
|---|---|---|---|---|
| K1: P recorded and T removed in RAM, before the removal's atomic move | T | Old. | E if saved. | Ordinary recovery keeps E, or rebuilds it if E was never saved (`RocketTransferRecoveryService.java:338-340`). The claim is kept, and no block exists. |
| K2: removal moved, before its continuation runs | No T. | Old. | E if saved. | E saved: a parked rocket without its reservation, protected by occupancy only. E never saved: **the rocket is lost.** |
| K3: clean failure, write-back queued, before its atomic move; also a write-back that ends NOT_COMMITTED | No T. | Old. | E if saved. | As K2. |
| K4: write-back moved, before its continuation runs | T | Old. | E if saved. | As K1: the claim is kept, and a never-saved E is rebuilt. |
| K5: success, nothing saved since | No T. | Old. | E if saved. | E saved: E is back without its reservation, and the disassembly is undone. E never saved: **the rocket is lost.** |
| K6: success; some or all footprint chunks saved incrementally; E's section not saved since | No T. | New in the saved chunks, old in the rest. | E if saved. | E saved: **the old E and the saved blocks both exist** (the v0.5 class of section 2). E never saved: **the blocks of the unsaved chunks are lost**; when every chunk was saved, nothing is lost. Not detected. |
| K11 (new, N3): success; an autosave or `save-all` without `flush` writes E's section without E but skips footprint chunks inside their 10 s cooldown; a stop before those chunks are saved | No T. | Old in the skipped chunks, new in the rest. | No E. | **The blocks of the skipped chunks are lost, and the whole rocket when every footprint chunk was skipped**, although E had been saved. Not detected. |
| K7: success; every footprint chunk saved after the restoration, and E's removal saved | No T. | New. | No E. | Steady state. K7 means that these writes completed, not merely that an autosave, an unload or a stop began. |
| K8: ROLLBACK_FAILED or an uncertain mutation, before any save | No T. | Old, or partly new if a chunk was saved. | E if saved (the transaction removes E only at its end). | No T, so no transfer rebuild. If the v0.5 FAILED or RESTORING record was saved, `RocketTransactionRecoveryService` alone recovers it, as today. If it was not, the state is that of K6. The reservation stays released. |
| K9: the removal write is refused past the 200-tick limit, then succeeds | T while refused; then no T; then T again. | Old. | E if saved. | While refused: as K1. After the retry, M2 does not restore and M3 writes T back; later kills land in K3 or K4. |
| K10: normal stop while P is pending | T, unless the drain gave up or the last write failed. | Old. | E when saving is on, because the stop saves the world after the journal's stop; with `/save-off`, whatever was saved before. | E with T: as K1. After a failed, abandoned or UNKNOWN last write: E with T or without it. |

Across the two restarts, the resource result must stay the same: one rocket or its blocks, never both and never neither.
The exceptions are the cells in bold above, which are recorded residuals. Ordinary recovery is idempotent for these
states: W77's retry keeps the acknowledged destination identity, so the second restart finds a rebuilt rocket and does
not repeat the rebuild.

**How long each exposure lasts (N3, N4).** Three different durations apply, and the residuals name them separately.
- **Live wait:** the time during which a stop would produce a bad state.
  - K2's disk gap begins at the removal's atomic move, not when its continuation is dispatched. In the uncontended
    case, with no other generation in RAM and a write that commits at its first attempt, the continuation runs in the
    first `pollWriter` after the IO. The gap then lasts at most about one tick after the move.
  - K2's gap is unbounded in two cases (N4). First, when a disjoint successor generation is already in RAM and its
    write is slow or keeps failing, because continuations run only while the whole journal is acknowledged-current
    (`RocketTransferSavedData.java:431-435`). Second, after an UNKNOWN outcome, because disk may already lack T while
    the retries continue every 200 ticks. The writer's p95 of about 125 ms at eight transfers is not a maximum.
  - K3 lasts one checked write in the uncontended case, and is unbounded while the write-back fails (N2).
  - K5, K6 and K11 last until every footprint chunk and E's section are saved (K7). With normal saving, terrain takes
    seconds plus the 10 s cooldown, and the entity section takes at most one autosave interval (6,000 ticks) or an
    unload. With `/save-off`, with failing saves, or with saves postponed because no tick has time left, there is no
    finite bound.
- **Post-crash loss or duplication:** after a restart these states are permanent. A lost rocket stays lost. A stale E
  that loads again next to its saved blocks can be saved again with them, so duplication does not end at the next entity
  save. Nothing detects either.
- **v0.5 recovery conflict:** after ROLLBACK_FAILED, the v0.5 FAILED record, when it was saved, makes every overlapping
  v0.5 transaction answer REGION_BUSY (`J/rocket/server/RocketManager.java:94-98`, `RocketDisassemblyService.java:128-129`)
  until `RocketTransactionRecoveryService` succeeds. That service tries one loaded transaction per tick, waits while the
  region or its entities are unloaded, and keeps the record after a conflict
  (`J/rocket/server/RocketTransactionRecoveryService.java:43-122`, in particular lines 95-99 and 116-118). Unlike the
  other residuals, this one is detected: by a WARN and by the busy region.

**Compared with today.** M replaces today's two-request window, whose length depends on a person's second request, with
an automatic continuation. That removes the human delay, not every unbounded crash window: K2 lasts about one tick only
in the uncontended case (N4). K3 exists only after a clean failure, where today the lost reservation is permanent. K5, K6
and K11 are unchanged from today's second request and from any v0.5 disassembly.

**W85-R4 and W85-R6 for this order.**
- No v0.5 record exists in K2 to K4: restoration has not started, or it was rolled back with certification in one call,
  so the v0.5 recovery has nothing to own. After ROLLBACK_FAILED or an uncertain mutation it is the only owner, because
  T is never written back.
- The geometry reservation exists in RAM from M1 until P ends: through P while T is absent, and through T in the stage
  WRITE_BACK. On disk it is missing between the removal's and the write-back's atomic moves, and longer when either
  write is late, fails or ends UNKNOWN.
- No named-pad claim is involved (section 1).
- Audit lines are session observations, at most one per transition per P. Nothing replays them after a restart, and
  none is a durability claim.

**Are K2, K3, K5, K6 and K11 acceptable as recorded residuals?** Recommended yes, with the durations stated above. K2 and
K3 lose a rocket only when its arrival was never saved and the process stops abnormally; in the uncontended case they
last about one tick and one checked write. K5, K6 and K11 are the general v0.5 class. Root decides.

## 6. Closure against ADR-065

ADR-065 line 1318 reads: "F8: the landed reservation is released before a disassembly that can still fail." The W11b2
review's F8 has two effects.

**Closed by M.**
1. "The first disassembly attempt always returns REGION_BUSY": one request answers DISASSEMBLY_PENDING and completes
   without a second request. A repeated request also answers DISASSEMBLY_PENDING (N6), and a zero-fuel arrival takes the
   same path (N5).
2. "If disassembly then fails, the arrival has already lost its landed reservation", for every failure that leaves the
   world unchanged:
   - the failures that the preflight detects (obstruction, unloaded region, unsupported block entity, entity mismatch)
     release nothing;
   - after the release, a failure without mutation, a certified clean rollback, a late acknowledgement, or a discarded
     continuation before restoration started writes the identical T back. In the session, P and then T hold the
     reservation throughout; on disk, it is back when the write-back is acknowledged.
3. The release's continuation is reserved before T is removed, which closes the unchecked `afterAcknowledged` in
   `releaseLandedReservation`.

**Recorded residuals** (the proposed R-035 text is in section 9):
1. ROLLBACK_FAILED, or an uncertain mutation after the restoration latch, leaves T released, and the v0.5 recovery owns
   the outcome. This one is detected (section 5).
2. A refused insert leaves T released: the journal is blocked, full or overlapping, or E no longer matches T.
3. A queued write-back that is not acknowledged keeps T, P and the interlocks in RAM with no time limit. The rocket stays
   locked, and while F3 is open every flight waits. Disk holds no T after NOT_COMMITTED, and T or no T after UNKNOWN. The
   state ends when storage is repaired and the write is acknowledged, or with a restart.
4. A process stop while disk lacks T (K2, K3) leaves T released, and loses the rocket if its arrival was never saved.
   The gap lasts about one tick or one checked write when uncontended, and is unbounded with a late or failing successor
   generation or after UNKNOWN.
5. After a successful restoration, terrain and entities persist independently:
   - K5 loses a never-saved arrival when the process stops before the footprint's terrain is saved;
   - K6 keeps the old entity together with the saved blocks, or loses the unsaved chunks' blocks of a never-saved
     arrival;
   - K11 loses the skipped chunks' blocks, and possibly the whole rocket, of an arrival whose entity had been saved.

   These belong to the v0.5 class of section 2, unchanged from today.

While T is released, the spot is protected only by the loaded-rocket occupancy check and, once W83 lands, by its
footprint readiness, as for a rocket assembled in place. W83 does not see a parked rocket whose entity lies in an
unloaded chunk outside the footprint while its structure overlaps the footprint. This lasts until the rocket launches
again or is disassembled.

**Against the closure text of W11B3-DESIGN.md.** M meets three of its requirements, the third with a qualification:
- "keep claims through failure", for clean failures;
- "a successful restoration must fence old-transfer recovery before checked removal and release": the removal is
  acknowledged before any block is restored;
- "no rocket recreated over blocks", in the sense of **no transfer-journal rebuild**: T is never on disk while restored
  blocks exist, because T is written back only when restoration never started or was rolled back with certification.
  This is not a promise that a stale native entity cannot reappear next to the blocks (K6).

It does not meet "no lost claim" in every S2 cut. The residuals are K2, K3 and K8, the refused insert, the unacknowledged
or UNKNOWN write-back as it stands on disk, and the persistence cuts K5, K6 and K11. Root decides whether that is enough
for admission. If it is, the closure text needs Root's explicit amendment to name all of these, not only K2, K3 and K8.

## 7. Tests

**A0 (JUnit, inside the ordinary unit run).**
- The pure M0 decision over T's presence, schema and native transfer, the bay gate, effect capacity, an existing P and
  the preflight result. It returns the plain path, a release, a status answer or a refusal, and only a release mutates.
  With a P, the status branch runs the actor, range, identity and permission checks in that order (N6).
- The pure M2 and M3 decision over the deadline, E's state, the transaction result and the write-back preconditions. It
  never writes T back after the latch without a certified clean rollback, and never restores after the deadline (N1).
- The result classification: a failure without mutation (0 of 0), a complete rollback (n of n), unequal counts,
  ROLLBACK_FAILED, and E no longer matching (N1).
- P's stages: the latch never resets; a callback does nothing once P has ended or moved past its stage; a second
  discard, a stop after P ended and repeated cleanup change nothing (N1). P counts toward capacity and reservations only
  while T is absent from RAM, never a second time in WRITE_BACK (N2).
- The removal continuation through a detached journal (N1). Its action returns false, throws a `RuntimeException`, or
  throws an `Error`, each with the latch unset and set. Two further cases run a real `RocketDisassemblyTransaction`:
  a fake `RocketTransactionWorld` that throws an `Error` after its first placement, and a notifier that throws after
  SUCCESS. With the latch set, T never reappears in RAM or in a capture, and P ends exactly once; with it unset, T is
  written back.
- S accepts zero fuel and keeps the catalog signature. `Quote` still rejects zero fuel, and its existing positive-fuel
  test is unchanged (N5).
- The preflight shared with `execute`: the same fixture gives the same code from both.
- The journal:
  - `remove(T)` followed by `put(T)` encodes byte-identical record NBT and the same `RecordVersion`;
  - `resourceMutationUnproved(L)` holds until the write-back is acknowledged, also across NOT_COMMITTED and UNKNOWN
    retries (N2);
  - the W44 outbox, and after the W11b2 merge any native record-3 entry, are identical across removal, write-back,
    NOT_COMMITTED and UNKNOWN;
  - a detached journal that stops with a queued removal continuation writes T in its final capture, and one whose drain
    gives up writes no final capture (N2).
- P: the 64-entry bound, its inclusion in `prepareLaunch`'s reservation and capacity inputs, and `clear()`.

**A1 (GameTests with real services and checked-file reloads, as in W77).**
- G1, obstruction at request: TARGET_OCCUPIED, T unchanged, no journal generation (W77's completed-write counter), and a
  second flight's selection still refused as reserved.
- G2, obstruction placed between the request and the acknowledgement with a delayed writer: DISASSEMBLY_PENDING, then
  TARGET_OCCUPIED. T is written back byte-identical, acknowledged and settled, a second flight is still refused, and
  the next request after clearing succeeds. G2b holds the write-back's acknowledgement for a while and checks that P
  keeps the rocket locked and is not counted twice.
- G3, rollback injected with `RocketFailureInjector`: as G2, and the result's changed and rolled-back counts are equal.
  G3b, ROLLBACK_FAILED: no write-back, one WARN, the v0.5 record recovered, and an overlapping transaction refused as
  REGION_BUSY until then.
- G4, success on the first request: one checked generation, blocks restored, E removed, the fuel messages, one
  `landed_reservation_released` audit and no REGION_BUSY.
- G5, delayed writer: a repeated request answers DISASSEMBLY_PENDING; refuelling and launch are refused; a second flight
  whose pad overlaps T's region is refused while T is absent from RAM (W85-R6); the slot is counted.
- G6, writer held past 200 ticks: no restoration, a write-back, REGION_BUSY, and the next request works.
- G7, full effect queue at request: refused, nothing changed.
- G8, discarded removal continuation (UNKNOWN, then a later acknowledged generation): a write-back.
- G9, a record-3 T from W11b2's fixtures: refused before release.
- G10, the release-test hook: DISASSEMBLY_PENDING, then the log line with SUCCESS.
- G11, removal committed and successor delayed (N4): a second flight's generation enters RAM right after the removal,
  and its write is held past 200 ticks. When it commits, M2 does not restore, T is written back, and no block appears.
- G12, UNKNOWN after the move (N4): the removal's write moves the file and then reports UNKNOWN, and the retry commits
  after 200 ticks. M2 does not restore, T is written back, and no block appears.
- G13, persistent write-back failure (N2): after a clean failure, the write-back's writes end NOT_COMMITTED.
  - T and P stay; refuelling, launch and disassembly are refused; a request answers DISASSEMBLY_PENDING; exactly one
    `ARCE_LANDED_DISASSEMBLY_UNACKNOWLEDGED` WARN appears; live flights do not advance while F3 is open.
  - A disjoint generation from another flight is queued meanwhile.
  - When writes succeed again, T is acknowledged and settled, and P ends.
  - G13b repeats this with UNKNOWN.
- G14, zero fuel (N5): the player path and the release-test hook through M0 to M3, and a fueled confirmation. A
  confirmed request whose fuel changed before M2 does not restore.
- G15, repeated requests while P exists (N6): the owner and the hook get DISASSEMBLY_PENDING, an unauthorized player
  gets UNAUTHORIZED, a distant player gets OUT_OF_RANGE, and none of them issues an offer or changes anything. A replayed
  confirmation token still answers `disassembly_confirmation_invalid`.
- G16, E at M2 and at M3: an unloaded E gets T written back; a destroyed or changed E in a loaded section gets no
  restoration and a refused insert (`reason=mismatch`).

The WARN lines that G3b, G13, G16 and the A0 failure cases drive get expectations in
`scripts/gametest_expected_log.json`.

**S2 (packaged dedicated server).** A new probe family `LANDED_DISASSEMBLY:<boundary>` pauses and the harness kills the
server. No cut forces a flush after the request; "E saved" is a precondition saved before the request. Every case
compares raw block, entity, inventory and journal NBT through two restarts.
- **Required:** K1; K2 with E never saved (its outcome is the recorded loss); K4 with E never saved (its outcome is the
  rebuild); K5 with E saved; K9; and K10, as a normal stop instead of a kill while K9's refusal holds. That is 6 cuts and
  18 starts.
  - K9 and K10 need one new mode, `LANDED_REMOVAL`, in the gated release-test policy behind
    `-Dadvancedrocketrycommunity.rocketJournalFailure` (`RocketJournalFailurePolicy.java`); production never sets it.
  - K4 does not need that mode. The gated release-test command gets an `obstruct` option that places one block in the
    footprint right after M1 returns. M2 then fails without mutation (TARGET_OCCUPIED), M3 queues the write-back, and
    the probe pauses at the write-back's continuation.
  - If Root wants no new mode, G6 and G8 cover K9's and K10's logic in A1, K9 and K10 drop out, and K4 stays native
    through the `obstruct` option.
- **Diagnostic, not PASS:** 3 cuts and 9 starts.
  - K6 with E saved: an incremental terrain save observed through `ChunkDataEvent.Save`, no `save-all`, no unload, and a
    kill before the next autosave.
  - K6 with E never saved and a two-chunk footprint of which one chunk was saved.
  - K11 with E saved (N3): the harness dirties the footprint chunks with a marker block outside the footprint and
    observes their incremental save. Inside those chunks' 10 s cooldown, the disassembly succeeds and `save-all` without
    `flush` runs. That save writes E's section without E and skips the cooled-down chunks, and the kill comes before the
    cooldown ends.
- K3 has K2's disk state, G13 covers the failing write-back, and K8 needs a new v0.5 injection seam, so none of them gets
  a native cut. G2, G3b, G13 and the A0 failure cases cover them.
- At W11b3's rate of about 3.3 minutes per start, 27 starts take about 89 minutes, about **1.5 heavy-JVM lock hours**,
  plus one GameTest run of about 18 minutes (W77). This matrix is a plan, not executed evidence.

## 8. Integration

- **W11b2 record 3:** refused at M0. Only schema-2 records are written back, so record-3 data never is. This matches the
  "Ordinary interlocks" row of design 2. The leaf keeps W11b2's journal helpers (`validateNewRecord`,
  `actionAuthorityAvailable`, `acknowledgedLease` and the `volatile` sequence) and its governed-source dispatch. M3's
  insert checks are those of `validateNewRecord` without its `stopping` refusal.
- **W44:** the removal and the write-back are ordinary generations of the same writer and carry the first-Moon-landing
  outbox unchanged. EMPTY, ELECTED and DELIVERED are unaffected, native records are preserved, and the FM12a hook is
  unchanged.
- **W80** (designed, not on the shared branch): the write-back restores a version identical to the removed one, and
  W80's record version (checksum, phase and destination identity, [W80-DESIGN.md](W80-DESIGN.md) lines 137-138) cannot
  tell the two apart. Whichever of W80 and this leaf lands second adds a two-way exclusion. M0 refuses a T that a W80
  batch names or that W80's admission in progress has fenced, and W80's admission refuses a logical rocket that has a
  P. Neither side can then start inside the other's admission window. After a restart no P exists, and T is either
  present or absent, so a session-only exclusion is enough.
- **F3:** a success writes one generation, as today, and a clean failure writes two. While F3 is open, each write pauses
  live flights for 2 to 3 ticks, and a write-back that keeps failing pauses them until it is acknowledged (N2).
- **P1-N2:** M adopts its rule, reserving the continuation before mutation. `failBackToSource` itself stays with N2's
  leaf.
- **W83:** it protects the rocket in the residual cases whose entity chunk lies in the footprint (question 3). It does
  not see an unloaded entity outside the footprint.

## 9. Proposed ADR-065 note and R-035 text (for Root)

**ADR-065 implementation note.**

> **P1-F8 (W85, revision 3).** Disassembly of a landed arrival keeps the existing order: the checked removal of its
> transfer record T is acknowledged before any block is restored, so the transfer journal never rebuilds the rocket
> over restored blocks. This is not a promise that a stale native entity cannot reappear. No record, key, schema or root
> is added, and older jars read the journal unchanged. A request first runs the restoration's no-mutation checks; a
> predictable failure answers its code and keeps T. Otherwise the service reserves a journal continuation, removes T,
> keeps T's destination region and journal slot reserved for new flights in a session-only pending entry (at most 64),
> and answers DISASSEMBLY_PENDING. Repeated requests answer DISASSEMBLY_PENDING after the actor, identity, range and
> permission checks, and zero-fuel arrivals take the same path. On the removal's exact acknowledgement, and at most 200
> ticks after the request, the continuation sets a one-way restoration latch and restores the rocket. The identical T
> is written back only when restoration never started (a late acknowledgement, a failed precondition or a discarded
> continuation) or after a certified complete rollback. Once mutation is uncertain (ROLLBACK_FAILED, or an exception or
> `Error` after the latch), nothing writes T back, and the v0.5 recovery stays the only owner of its record. A
> written-back T keeps the pending entry and the rocket's interlocks until its write is acknowledged, with no time limit;
> no timeout releases unacknowledged state. Residuals (R-035): ROLLBACK_FAILED or an uncertain mutation, a refused
> write-back, and a process stop while disk lacks T leave T released. That disk gap is about one tick or one checked
> write when uncontended, and unbounded while a successor write is late or failing or after an UNKNOWN outcome. The spot
> is then protected only by the loaded-rocket occupancy check and, once merged, W83's footprint readiness, which does not
> see an unloaded rocket whose entity lies outside the footprint. A process stop after the removal reaches disk loses an
> arrival whose entity section was never saved. A write-back that keeps failing locks the rocket and, while F3 is open,
> pauses every flight until storage is repaired. After a successful restoration, vanilla saves terrain chunks and entity
> sections independently, so a stop can leave a stale entity together with saved blocks, lose the unsaved chunks' blocks
> of a never-saved arrival, or, when the entity's removal was saved while a footprint chunk was inside its 10-second
> save cooldown, lose those blocks of an arrival that had been saved. These belong to the existing v0.5 disassembly
> class. Only ROLLBACK_FAILED is detected, and a restart makes the losses and duplicates permanent. Repair needs a
> known-consistent whole-world backup from before the operation. This is a development implementation note, not owner
> acceptance or a Gate result.

**R-035 addition** (Chinese, as in the [risk register](../../../11-RISK-REGISTER.md)).

> F8（W85 修订 3）：着陆火箭拆解保持现有顺序，先确认移除转移记录 T，再恢复方块，因此转移日志永远不会在已恢复的方块上重建火箭；
> 这不保证陈旧的原生实体不会重新出现。不新增记录、键、schema 或根，旧 jar 读取日志不受影响。请求先做恢复的无变更检查，
> 可预见的失败直接答复并保留 T；否则先预留日志续体，再移除 T，在会话内的待处理条目中为新航班保留 T 的目的地区域和日志名额
> （至多 64 条），答复 DISASSEMBLY_PENDING。重复请求在执行者、身份、距离和权限检查之后答复 DISASSEMBLY_PENDING；无燃料的
> 着陆火箭走同一路径。移除确认后（请求后至多 200 tick），续体先设置单向的“恢复已开始”标记，再恢复方块。只有恢复从未开始
> （确认过迟、前置条件不满足或续体被丢弃），或经核实完整回滚时，才重新写入同一个 T；一旦变更不确定（ROLLBACK_FAILED，或标记
> 之后的异常或 Error），任何路径都不重新写入 T，由 v0.5 恢复单独处理。重新写入的 T 在确认之前一直保留待处理条目和火箭的全部
> 互锁，没有时限；不得为了结束等待而释放未确认的状态。
> 影响范围：单个着陆火箭及其目的地区域；P1-F3 未关闭时，日志写入持续失败会使所有航班暂停推进。
> 残余与持续时间：（1）ROLLBACK_FAILED 或变更不确定、重新写入在写入前被拒绝（日志阻断、已满、区域重叠或实体不再匹配），
> 或进程在磁盘缺少 T 时停止，T 保持释放，直到该火箭下次发射或被拆解。磁盘缺少 T 的窗口在无竞争时约为一个 tick 或一次检查
> 写入；后继代次的写入迟到或持续失败，或写入结果为 UNKNOWN 时，没有上限。期间该位置只靠已加载火箭的占用检查保护（W83 合入后
> 加上足迹实体段就绪；实体位于足迹之外的未加载区块、而结构与足迹重叠的火箭，W83 也看不到），与原地组装的火箭相同。
> （2）重新写入已排队但未确认时，T、待处理条目和互锁一直保留，火箭不能加注、发射或拆解，没有时限；NOT_COMMITTED 时磁盘保留
> 不含 T 的上一代，UNKNOWN 时磁盘可能含 T，也可能不含。存储修复且重试得到确认后结束；重启则读取实际写到磁盘上的那一代。
> （3）移除写盘后进程异常停止时，若到达后实体段从未保存，火箭丢失。（4）成功恢复后，原版分别保存地形区块和实体段：足迹地形
> 保存之前停止，会丢失从未保存的到达；只有部分足迹区块已保存时，从未保存的到达会丢失其余区块的方块；地形增量保存之后、实体段
> 保存之前停止，旧实体与已保存的方块同时存在；自动保存或不带 flush 的 save-all 会跳过 10 秒保存冷却内的区块、但照常写入实体段，
> 此后在这些区块保存之前停止，即使到达此前已保存，这些区块的方块也会丢失，全部足迹区块都被跳过时整枚火箭丢失。以上与所有
> v0.5 拆解同类：正常保存时暴露窗口为数秒加 10 秒冷却，实体段至多一次自动保存间隔（6,000 tick）；在 /save-off、保存失败或
> 保存被推迟时没有上限；重启后丢失或重复成为永久状态。
> 检测：ROLLBACK_FAILED 记录一条 WARN；已保存的 v0.5 FAILED 记录使重叠区域的事务答复 REGION_BUSY，直到 v0.5 恢复成功。其余
> 情况均不被检测。
> 日志量：每次拆解每个转换一条审计 INFO；恢复未完成（ROLLBACK_FAILED、变更不确定或重新写入被拒绝）时每个待处理条目一条 WARN
> （`ARCE_LANDED_RESERVATION_NOT_RESTORED`）；日志写入未确认时每个待处理条目每个阶段至多一条 WARN
> （`ARCE_LANDED_DISASSEMBLY_UNACKNOWLEDGED`）；继承的日志写入错误不变。
> 恢复：（2）需要修复存储，使重试得到确认。丢失或重复只能在停服后，用操作之前、已知一致的整世界备份离线修复，不得单独恢复
> 日志；对已经不一致的世界所做的备份只能保留证据，不能重建丢失的数据。

## 10. Open questions, each with a recommended answer

1. **Write T back after a clean failure, or accept the lost reservation?** Write it back, but only while the restoration
   latch is unset or after a certified clean rollback (N1). It uses only `put` of the identical schema-2 record, and no
   restored block can exist in those cases.
2. **The 200-tick limit?** Keep the confirmation's own lifetime. A player who clicked should not see blocks appear much
   later, and the write of the largest legal journal (about 117 ticks, ADR-065) fits. The limit only decides whether M2
   restores. It is checked when M2 runs and guarantees no liveness (N4).
3. **Make W83 a prerequisite for admission?** Recommended yes, for arrivals whose footprint contains the parked rocket's
   entity chunk. W83 does not cover an unloaded entity outside the footprint, does not restore the durable reservation,
   and does not fix the loss and duplication cuts. Root decides.
4. **Recheck the requester's range in M2?** No. Authorization and range are checked at M0, and M2 requires E to equal
   S. The release-test hook has no player.
5. **Register the two-store class of section 2 for all assembly and disassembly?** Recommended, under R-006's scope and
   separately from F8, now including K11's entity-first order. This design does not change the class.
6. **The smoke script's fuel-line expression?** Recommended that the P1-F8 leaf corrects it, as section 4 describes.
   Root may assign it separately instead.
7. **The `LANDED_REMOVAL` failure mode?** Recommended, because K9 and K10 need it to run natively. K4 does not need it.

## Appendix A. Bytecode read

Jar: `C:/Users/Administrator/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.10_mapped_official_1.20.1/forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`,
SHA-256 `95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`, checked again in revision 3. Tool:
`C:/Program Files/Java/jdk-17.0.7/bin/javap.exe -c -p`; revision 3 also ran `-v -p` on `ChunkMap` to read its bootstrap
methods. Nothing was extracted and no game code was run.

| Class and method | Bytecode offsets | What it shows |
|---|---|---|
| `ChunkMap.tick(BooleanSupplier)` | `ServerLevel.noSave` at 38, `processUnloads` at 46 | Incremental saves run every tick unless saving is off. |
| `ChunkMap.processUnloads` | `visibleChunkMap.values()` at 185-188, `bipush 20` at 197, `saveChunkIfNeeded` at 232 | Up to 20 visible chunks are saved per tick while the tick has time left. |
| `ChunkMap.saveChunkIfNeeded` | `wasAccessibleSinceLastSave` at 1; cooldown read at 48-61; skip at 68-77; `save` at 80; cooldown set to now plus `10000L` at 94-105 | A chunk is skipped for 10 s after each save (offsets before 80 read in revision 3). |
| `ChunkMap.saveAllChunks(boolean)` (revision 3) | `flush` test at 0-1; without `flush`, `visibleChunkMap.values().forEach` with bootstrap method 22 at 132-145; with `flush`, a stream filter through bootstrap method 19 at 87-93 | Bootstrap method 22 is `this::saveChunkIfNeeded`, so a save without `flush` honours the cooldown; method 19 is `this::save`, so a flushing save does not. |
| `ChunkMap.save(ChunkAccess)` | `ChunkSerializer.write` at 111, `ChunkDataEvent.Save` posted at 147, `write` at 157 | The event precedes the terrain write; there is no entity storage call. |
| `ServerChunkCache.save(boolean)` (revision 3) | `ChunkMap.saveAllChunks` at 10 | Passes `flush` through. |
| `PersistentEntitySectionManager.tick` | `processPendingLoads` at 1, `processUnloads` at 5 | No entity save in the tick. |
| `PersistentEntitySectionManager.autoSave`, its lambda, and `saveAll` | `autoSave`: `getAllChunksToSave` at 1 and the lambda at 5-10 (revision 3); lambda: `processChunkUnload` at 28 or `storeChunkSections` at 42; `saveAll`: `flush(true)` at 19 and 49 | Level saves hand the entity sections of every loaded chunk over, with no cooldown; only `saveAll` flushes. |
| `EntityStorage.storeEntities` | `IOWorker.store` at 37 and 101 | The entity write is asynchronous. |
| `ServerLevel.save(ProgressListener, boolean, boolean)` | skip flag at 6-7 (revision 3); `ServerChunkCache.save` at 49, `saveAll` at 60 or `autoSave` at 70, `LevelEvent.Save` posted at 84 | Entities are saved here, or at unload. Without `flush`, `autoSave` writes entity sections after a terrain pass that honoured the cooldown. |
| `MinecraftServer.tickServer` | `sipush 6000` and `irem` at 62-65, `saveEverything(true, false, false)` at 92-96 (flags read in revision 3) | Autosave every 6,000 ticks, without `flush` and not forced. |
| `MinecraftServer.saveEverything(boolean, boolean, boolean)` (revision 3) | `saveAllChunks` with the same three flags at 12-16 | Passes the flags through. |
| `MinecraftServer.saveAllChunks(boolean, boolean, boolean)` (revision 3) | `ServerLevel.save(null, flush, noSave && !forced)` at 61-85 | With `/save-off`, an unforced save skips the level entirely. |
| `SaveAllCommand.saveAll` (revision 3) | `saveEverything(true, flush, true)` at 15-19 | `save-all` is forced; without `flush` it honours the chunk cooldown. |
| `MinecraftServer.stopServer` (revision 3) | `saveAllChunks(false, true, false)` at 225-229 | A normal stop flushes, but is not forced, so `/save-off` makes it skip the level save. |
| `MinecraftServer.runServer` (revision 3) | `ServerLifecycleHooks.handleServerStopping` at 291, `stopServer` at 303, `handleServerStopped` at 327 | On a normal stop, the journal's `stop()` at `ServerStoppingEvent` runs before the world is saved, and `clear()` at `ServerStoppedEvent` runs after. |
