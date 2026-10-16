# W86: per-record journal scheduling (P1-F3) and the fail-back reservation (P1-N2)

**Proposed design, revision 1, 2026-10-16. Not implementation, review, risk acceptance or Gate evidence.**
Assignment: [TASK W86](../TASK-W86-P1-F3-DESIGN.md); record: `records/W86.md`. This is W11b3's leaf P1-F3-D; AGENTS.md
section 13 requires a read-only review before code, because the note changes when saved transfer data counts as
acknowledged.

**Inputs.** Both hashes were checked in this session.
- F3: `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-review/REVIEW.md`, SHA-256
  `8c1da51138a655581c8a3256841615fd556c627eaed46edcf421b4fcc67de499`.
- N2: `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-P1F-review/ROOT-REVIEW.md`, SHA-256
  `8266bf6fb9266bf3ca8691e93e58ec2e6c05477ffc87eef428b548f7654e6ab1`.
- The contract: [W11B3-DESIGN.md](W11B3-DESIGN.md) revision 2, section 1 rows P1-F3 and P1-N2 and the leaves P1-F3-D,
  P1-F3 and P1-N2 of section 5; and [W85-DESIGN.md](W85-DESIGN.md) revision 3 (P1-F8, frozen).

**Code.** W11b2 at `a8d81d24` (continuation 25), read only through `git show`. `J` is
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity`. A citation `Class:line` means that class under `J` at
`a8d81d24`. W11b2's F5 to F8 are not written yet; the leaf applies this note's rules to whatever W11b2 merges.

**Notation.** L is a logical rocket and T its transfer record; the journal holds at most one record per L
(`RocketTransferSavedData:633-646`, and on load). A **slot** is either L's place in the journal (T or nothing) or the
single root slot of W44's Moon-landing outbox. **A** is the latest exact acknowledgement held by the checkpoint
(`RocketJournalCheckpoint:32-39`), with sequence **a**. **s(X)** is the RAM mutation sequence just after the last change
to slot X in this session. Proposed names do not exist yet.

## 1. The current gating

| Site | Waits for | Why |
|---|---|---|
| `RocketTransferService.tick` :268 | When the journal is dirty or holds records, `retryChecked` must report the whole root acknowledged-current: operational, not dirty, and a equal to the RAM sequence (`RocketTransferSavedData:379-381`). Otherwise the tick returns before recovery (:269), reconnects (:270), governed dispatch (:271) and the live loop (:272-297). | R1 of the W11b2 design review: dependent effects run only after the exact acknowledgement, and a clean journal implies that every record is on disk. The call also drives the writer and its 200-tick retry spacing (`RocketTransferSavedData:372-377`). |
| `tickLive` :494 | `flightAllowed` and `acknowledgedCurrent()`. | The first transfer in sorted order that calls `put` dirties the journal, and every later one in that tick skips. One phase advance per acknowledgement server-wide: this is F3. |
| `tickSource` :515-569 | Nothing of its own. It runs under `tickLive`'s gate, and its countdown, ascent and transit steps change only the entity. | So entity-only progress also stops during every write. |
| `spawnDestination` :577 | `flightAllowed(SPAWN_DESTINATION)` and `acknowledgedCurrent()`. Then `updatePhase` puts DESTINATION_SPAWNED and places the entity in the continuation (:623-630). | Placement needs DESTINATION_SPAWNED on disk. |
| `failBackToSource` :668-698 | `flightAllowed(SETTLE_SOURCE)` and the bay gate. It then changes the source (:676-684) and removes T (:685) before `afterAcknowledged` (:686), which can refuse. | The pad release and the `liveTransfers` cleanup need the removal on disk. The order is N2. |
| `updatePhase` :713-727, `prepareLaunch` :192, recovery (`RocketTransferRecoveryService` :357, :411, :459), schema migration (`RocketDestinationCompletion` :135) | A put or remove, then `afterAcknowledged`. Most callers ignore its result. | The effect follows the acknowledgement. |
| `afterAcknowledged`, `queueEffect` (`RocketTransferSavedData` :427-449) | A continuation is keyed by the sequence after the mutation: one per sequence, two in total. It runs at once only when the whole root is current. | One active write and one successor (`RocketJournalWriter:59-70`). |
| `canQueueEffect` :451 | Not stopping, no failed write, fewer than two continuations. | W44's election (`MoonLandingCompletion:61`), F4 (`EscrowGovernedDestination:68`) and W85's M0. |
| `pollWriter` :453-476 | Continuations run only while the whole root is current, rechecked before each one (:460). A continuation whose own generation was never exactly committed is discarded once a later one is acknowledged (:462-463). A committed one runs only if L's RAM record is still the captured object (:467). | Exact-generation proof: a coalesced generation has no acknowledgement of its own. |
| `actionAuthorityAvailable` :384, `acknowledgedGuard` :417, `acknowledgedLease` :391 | The whole root current (and, for the first, no failed write). The lease fails on any mutation of any record. | One lease for both the flight permits and the escrow writer. |
| `resourceMutationUnproved` :398-407, used by `flightAllowed` (`RocketTransferEscrowRules:27`) and `resourceMutationAllowed` (`RocketTransferService:442`) | Already per record: L's `RecordVersion` equals A's, or L is absent from A. Unproved when a failed write left no A. An absent L counts as proved before the session's first A (:404). | F2's fix. It compares versions only, with no sequence (section 2). |
| Recovery: `recoverNext` :80, `recover` :301, `recoverById` :140, :147, :154, `tryReconnect` :217 | The whole root current. | Recovery rebuilds or discards entities from the record. |
| `RocketTransferFences.classify` :48, `RocketTransferBayDispatch.prune` :37 | Session sets are pruned only while the whole root is current. | Never prune on a RAM-only removal. |
| Governed flight: `EscrowGovernedDispatch.acquire` :89-100 | `actionAuthorityAvailable`, the exact RAM record, a guard naming T, and an authority that stays valid only while `acknowledgedLease` holds. Otherwise `classify` answers HOLD `GOVERNED_JOURNAL_UNACKNOWLEDGED` (:68), and `RocketTransferFences.classifyGoverned` (:72-85) fences the record with one WARN (:241-249). `EscrowGovernedSourceFlight.tickChecked` (:37-40) and `EscrowGovernedDestination.checked` (:48-50) hold without that authority; the F4 request also needs `canQueueEffect` (:68). | The governed permits bind a checked journal lease. Any other record's write invalidates every governed authority, so part of continuation 25's eight `GOVERNED_JOURNAL_UNACKNOWLEDGED` warnings are cross-record. |
| Governed admission: `EscrowGovernedPreparation.begin` :26-27, `EscrowFlightCheckpoint.begin` :80, :91, `EscrowFlightJournal.noTransfer` :11-21 | `retryChecked`, a no-T guard, and the whole-root lease, rechecked in the barrier callback. | F1 needs checked absence of T, plus a scan of RAM for the root-wide serial (:16-19). |
| Escrow ledger: `EscrowCoordinator` :153, :300; `EscrowRuntime` :157; `EscrowSettlementRecovery` :52-135 (O3J at :101-102); `RocketEscrowAudit` :81; `EscrowSettlementObserver` :28, :51 | The whole root current, and the whole-root lease handed to the ledger writer. | Settlement and floor proofs compare a clean acknowledged journal. |
| W44: `MoonLandingCompletion` :52, :84 | LANDED waits for the generation that holds ELECTED. | The world-first event is durable before its effects. |
| Release-test hooks: `RocketFlightLifecycleController` :60, `FirstEventCaseExports` :31, `FirstEventCutSeams` :214, the escrow probes | The whole root current. | Cut fixtures. |

The whole-root test was the simplest condition that implies every record is durable, and it makes continuation order
trivial. Its cost is F3: every write pauses every flight, recovery and reconnection, and phase advances are serialized.

<div style="page-break-after: always;"></div>

## 2. Options and the per-record rule

| | A: sequence leases on the one acknowledgement (recommended) | B: version equality only | C: one file per record |
|---|---|---|---|
| Idea | Keep one writer and one A. A slot counts as acknowledged when A was captured at or after the slot's last change. | Gate on today's `resourceMutationUnproved` and raise the two-continuation limit. | Split the journal so that each record has its own file and writer. |
| Safety | Section 2.1. | **Unsafe.** After `remove(T)` and an identical `put(T)` (W85's write-back), an older A that holds T still matches, while the snapshot without T may still be written; an effect could then run on authority that the disk is about to lose. Coalesced continuations are still discarded, which under concurrency is most of them. | Per-record durability, and a failing record no longer blocks other records' next writes. |
| Cost | Session-only bookkeeping; no saved-format change. | Small, but needs the fixes that turn it into A. | New layout, migration and downgrade refusal; several writers; W44's single-root outbox and the cross-file crash order need an ADR. |

Batching whole-root steps per tick (one check per tick, then one flush) was also considered. It removes the serialization
but still pauses every flight for every write and every failure, so it does not meet W11b3's closure text.
**Recommendation: A.**

### 2.1 The rule

- **Slot sequences.** Every journal mutation already increments the RAM sequence on the owner thread. Option A also
  records **s(X)** for the slot it changes: `put`, `replace`, `migrateCommitted` and `remove` for L's slot, and
  `electFirstMoonLanding` and `deliverFirstMoonLanding` for the outbox. Records decoded at load get the load sequence (1,
  or 2 after a load-time migration). An absent slot keeps its s(L) until its absence is acknowledged; after that the
  entry is dropped. An absent slot that nothing changed in this session has no entry.
- **Acknowledged slot.** Slot X is acknowledged when all of the following hold:
  1. the journal is operational, that is, neither blocked nor unreadable;
  2. A exists;
  3. a ≥ s(X); for an untouched absent slot, any A counts;
  4. as a final guard, A agrees with RAM: for a record R, A holds R's transfer ID with `RecordVersion.of(R)`; for no
     record, L is not in A's logical set; for the outbox, A's outbox equals RAM's.
- **Why this is the acknowledgement of the record.** `capture()` takes the records and the sequence together on the
  owner thread (`RocketTransferSavedData:582-584`). Completions arrive in capture order: the writer retries a failed
  active snapshot before its successor and refuses a backwards generation (`RocketJournalWriter:64, 99-116`). So the
  snapshot behind A holds slot X exactly as RAM holds it now, and every later snapshot does too until X changes. A
  slot's version counts as acknowledged exactly when a snapshot captured at or after its last change was acknowledged.
  Condition 4 follows from condition 3 and is checked anyway, because the comparison is a cheap map lookup.
- **Cross-record order.** Every snapshot holds the whole RAM, so an A that acknowledges a change also acknowledges every
  earlier change to every slot. A new record that reuses a destination region or a journal slot freed by another
  record's removal is therefore never acknowledged before that removal. The overlap and capacity checks of `put` and
  `prepareLaunch` read RAM, as today, and W85's P keeps T's region in RAM while T is absent.
- **Leases.** A **version lease** for L captures L, s(L), the RAM record object and A's digest for it. It is valid
  while the slot is acknowledged, s(L) is unchanged and RAM still holds that object. An **absence lease** captures L
  and s(L) or "untouched", and is valid while L has no record and its absence is acknowledged. Leases are owner-thread
  values. They read session state only and never load a chunk or touch storage.
- **Actionable.** A slot is actionable when it is acknowledged and has no pending continuation. The second condition
  keeps a live step from running beside its own continuation; today's whole-root gate gives this implicitly.

| State | Records and absences | Pending continuations | New reservation (section 3) |
|---|---|---|---|
| First write of a session, before the baseline commits | No A, so no slot is acknowledged, as today (W44 section 2.4). `resourceMutationUnproved` keeps its untouched-absent rule (:404) for rockets that have no record. | Wait for the baseline or a later snapshot. | Allowed. `canQueueEffect(L)` is allowed as well, unless a write has failed. |
| A COMMITTED snapshot at k | Acknowledged when k ≥ s(X). | Run when every dependency is acknowledged. | Allowed. |
| Definite failure (NOT_COMMITTED) | The retained A still describes the disk: no move happened, or readback equals A's digest (`RocketJournalCheckpoint:77-90`). Slots that A covers stay acknowledged; slots changed since then wait for a retry. | Those covered by A run; the rest wait. | `reserve` allowed; `canQueueEffect(L)` refused, as today. |
| UNKNOWN, or a `Throwable` on the writer | No A (`RocketJournalCheckpoint:91-98`, `RocketJournalWriter:91-94`), so no slot is acknowledged. Every flight pauses, as today. | Kept, not discarded. They run after a later COMMITTED snapshot if their slots are unchanged. | `reserve` allowed; `canQueueEffect(L)` refused. |
| Blocked (future or invalid) or unreadable root | Not operational: nothing is acknowledged, as today (:262). | None; `becomeUnreadable` discards them (:510). | Refused. |
| Stopping or abort | Leases fail. | Discarded, as today (:522, :535). | Refused. |

Two consequences of this table. First, a definite failure now stops only steps that need a new write; acknowledged
progress (countdown, ascent, transit, descent and landing) continues. Today it stops everything. Second, a continuation
is no longer discarded because its exact generation was coalesced or failed; a later snapshot that still holds its slot
unchanged acknowledges it. Today the exact generation can also commit on a retry 200 ticks later, so a late
continuation is not new. The continuation bodies keep their final checks.

## 3. Scheduler, call sites and bounds

**Continuations.** A continuation names L, its dependencies and their expected RAM values: L's record object or
"absent", plus the outbox for W44's election. It also keeps the sequence at which it was queued.
- `reserve(L)` refuses when the journal is not operational, is stopping, already holds a continuation for L, or holds
  `RESERVATION_LIMIT` continuations. The limit is 128, twice `MAX_ACTIVE_TRANSFERS`: at most 64 records and at most 64
  removals awaiting acknowledgement. It is a constructor parameter, so that a detached test journal can use 2.
- `canQueueEffect(L)` is `reserve(L)` without a failed write; it replaces today's `canQueueEffect()` at the three sites
  in section 1.
- `afterAcknowledged(reservation, effect, discarded)` consumes the reservation and runs at once if every dependency is
  already acknowledged, as today's immediate path does (:446). Otherwise it queues and calls `flushChecked`.
- A running continuation is removed before it runs (:466), so its successor for the same L can reserve again. W85's M3
  and the spawn continuation's fail-back rely on this.
- `pollWriter` drains the writer as today. If the journal is operational and not stopping, it visits pending
  continuations in queue order, at most 64 runs per tick across all calls. Queue order runs an earlier change's
  continuation, such as a removal that releases a pad, before a later one that the same A acknowledges. For each one it
  re-evaluates the dependencies:
  - acknowledged and unchanged: remove and run, with today's handling of `RuntimeException` (:469-472) and `Error`
    (`Effect.run`, :74-77);
  - changed (another record object, a record removed, or one added): remove and run the discarded callback;
  - otherwise: keep it.
- `stop()`, `abort()` and `becomeUnreadable()` discard every continuation, as today.

**One writer and bounded captures.** `RocketJournalWriter` and the checkpoint's write protocol are unchanged. There is
still one active snapshot and one successor of at most 64 immutable record references. Coalescing becomes normal and
safe. Main-thread bookkeeping is at most 129 slot entries and 128 continuations, with constant-time lookups. Dispatch
visits no blocks.

| Site | After P1-F3 |
|---|---|
| `tick` :268 | Still calls `retryChecked` every tick to drive writes and their spacing, but no longer returns on its result. |
| `tickLive` :494, `spawnDestination` :577 | `flightAllowed` and `journal.actionable(record)`, which requires a version lease on that exact record. Several transfers can step in one tick; each queues its own continuation, and the writer coalesces them. |
| `tickSource` | Unchanged, under `tickLive`'s gate. |
| `failBackToSource` | Section 5. |
| `updatePhase`, `prepareLaunch`, recovery removals, migration | `reserve(L)` before the first mutation; a refusal changes nothing. W11b2's F2 callback keeps its order (section 5). |
| `recoverNext`, `recover`, `recoverById`, `tryReconnect` | `step` skips records that are not actionable, before `recoveryEntityChunksLoaded` can add a ticket; `recover` and `tryReconnect` check the candidate's lease, and `recoverById` checks the absence lease for a completed result. |
| Fences :48, bays :37 | Prune a transfer ID once its slot's change is acknowledged, never on a RAM-only removal. |
| `EscrowGovernedDispatch.acquire` | The version lease of the exact record replaces `acknowledgedCurrent`, `acknowledgedGuard` and `acknowledgedLease`; `!stopping && !failedWrite` stays (W11b2's hold). `classify` then holds only a record whose own slot is unacknowledged. |
| F4 request (`EscrowGovernedDestination:68`), W44 (`MoonLandingCompletion:61`) | `canQueueEffect(L)`. |
| W44 election (`MoonLandingCompletion:84`) | Depends on L's record and the outbox slot. |
| `resourceMutationUnproved(L)` | Becomes "L's slot is not acknowledged", keeping the untouched-absent rule before the first A. This differs from today only after a remove and identical put, where it is now stricter. |
| Governed admission, escrow ledger, release-test hooks | **Unchanged**, whole-root. A clean root implies every slot is acknowledged, so they stay correct, but they wait for a clean window. The eight-transfer run measures those windows (section 8, open question 2). |
| `acknowledgedCurrent`, `acknowledgedGuard`, `acknowledgedLease`, `actionAuthorityAvailable` | Meanings unchanged. Callers move to the new methods one by one. |

**Final guards.** Every effect rechecks its lease immediately before acting: in the continuation, in `tickLive` and
`spawnDestination` at entry, and in every governed permit through `Authority.valid()`. The existing `flightAllowed`,
bay, identity and loaded-candidate checks stay in place. The scheduler belongs in a new package-private class
`RocketJournalSchedule`, so that `RocketTransferSavedData` (648 lines) stays under the 800-line limit.

<div style="page-break-after: always;"></div>

## 4. What must not change

| Invariant | Under option A |
|---|---|
| Journal root 3 | Unchanged: one file, stage, force, decoded readback, atomic move and directory force; A only from a COMMITTED completion; NOT_COMMITTED keeps A; UNKNOWN clears it. |
| The order of each record kind | Unchanged: PREPARED before countdown; DESTINATION_SPAWNED before placement; PASSENGERS_TRANSFERRED, SOURCE_REMOVED and COMMITTED before their effects; a removal before the pad release and settlement. Only "the exact generation" becomes "a snapshot taken at or after the change". |
| W44's outbox | ELECTED is acknowledged before LANDED, because the continuation depends on the outbox slot. DELIVERED still follows the durable receipt, and the durable view is still the latest A. **Observable change:** a second Moon landing no longer waits for the first one's election write. It answers NOT_FIRST from the occupied RAM slot (`FirstEventElection:29-30`), as W44 designed for one session. |
| W11b2's governed record 3 | F2 before COUNTDOWN and F4 before publication; ARMED, the ledger generations, the barrier proofs and the native envelopes are unchanged. The legacy loop still skips record 3 (`RocketTransferService:279`), and so does recovery (`RocketTransferRecoveryService:106`). The no-failed-write hold stays. Flight permits bind the record lease, which proves the fact that they use (this record is on disk), and nothing they use concerns another record. |
| P1-F8's write-back | The removal is acknowledged before restoration, and the write-back before P ends (section 6). |
| Proofs and S2 cuts | The same snapshots are written in the same order, so every existing cut keeps its disk state. New are only the interleavings in which one record's effect runs while another record's write is in flight; section 8 tests them. |
| Holds | Blocked and unreadable roots, UNKNOWN, stopping, bay claims, fences, escrow locks, resource interlocks and the governed no-failed-write hold all stay. Only the cross-record "another record is being written" hold goes. |
| Replay exclusion | Design 2's ARMED serial, receipt and retired-T rules, `prepareLaunch`'s REQUEST_REPLAYED, and "record 3 is never legacy authority" (`RocketTransferEscrowRules:20`) are not touched. |

Option A keeps every item. Nothing here needs Root to stop the design.

## 5. N2: reserve before `failBackToSource` changes anything

1. Keep the checks `flightAllowed(SETTLE_SOURCE)` and the bay gate.
2. Call `reserve(L)`. A refusal returns false before any mutation: the source keeps its flight state and position, T
   stays in RAM unchanged, the pad stays claimed and `liveTransfers` keeps the ID. In production only the total limit or
   a stop can refuse here, because L is actionable. The callers then see the state they started from: `spawnDestination`
   tries again on the next tick; after a refused fail-back in the spawn continuation, ordinary recovery finds the
   source alone and keeps it (KEEP_SOURCE, `RocketTransferRecoveryDecision:21-23`), which is the fail-back's own
   outcome; and `cancelCountdown` answers TRANSFER_JOURNAL_BLOCKED, as the P1F fix does.
3. Only then change the source (stationary flight, origin, feedback, remount), call `journal.remove(T)` and queue the
   continuation with the reservation. Its dependency is L's absence. On acknowledgement it releases the pad, drops the
   ID and logs the return reason, as today. Its discarded callback runs `completion.discarded()`.

Inside the spawn continuation (:626), the running continuation has already been removed, so the reservation succeeds.
`failBackToSource`, W85's M0 and the recovery removals all reserve the same way. That is the one rule for the ordinary
paths: no journal mutation without a reserved continuation. F4 already checks before its put
(`EscrowGovernedDestination:68-70`). W11b2's F2 callback keeps its frozen order, in which a refused queue retains ARMED
and PREPARED for recovery (`EscrowGovernedPreparation:39-53`); under the per-L limit only a stop or the total limit can
refuse it. A crash before the removal reaches disk leaves T on disk with the source present and no destination, so
recovery answers KEEP_SOURCE, as today. N2 also needs the per-record dispatch: with whole-root dispatch, a write held
for another flight frees no reservation, so "one release" cannot be shown. **Recommendation:** deliver N2 as the first commit of the
P1-F3 leaf, and add its test after the scheduler commit (open question 1).

## 6. P1-F8 after F3

Once F3 is in, W85's queued write-back holds the following:
- T in RAM, with s(L) at the write-back;
- P in the stage WRITE_BACK;
- the one reservation for L;
- the rocket's interlocks, because `resourceMutationUnproved(L)` holds until a snapshot taken at or after the
  write-back commits.

It **no longer holds other flights**. A flight with an acknowledged record keeps advancing. While the write-back's
snapshot fails definitely, any flight that needs a new write waits, because every record shares the file. After
UNKNOWN, every flight waits, as before. The W85 text changes as follows. Root applies these changes when the second of
P1-F3 and P1-F8 lands.

| W85-DESIGN.md | Change |
|---|---|
| M0, line 159 | `journal.canQueueEffect()` becomes `journal.reserve(L)`. |
| M3 (b), lines 265-267 | "M3 runs inside `pollWriter` with the journal acknowledged-current" becomes "the removal continuation was removed before it ran, so `reserve(L)` holds except at a stop or abort; the journal need not be acknowledged-current, and a failed write does not refuse it". |
| M3 (b), lines 274-277, and the "before the return at line 240" WARN at lines 283-285 | Replace with: "After P1-F3, other flights advance on their acknowledged records. A flight's next journal step waits while the write-back's snapshot keeps failing, and every flight waits after UNKNOWN." The WARN stays at the start of the tick, at most once per P and stage. |
| Discarded continuations, case 2 (lines 294-295) | After P1-F3 this is not a discard: a later snapshot that still lacks T acknowledges the removal, and M2 runs. After a coalesced capture that is within the 200-tick limit, so M2 restores. After an UNKNOWN or failed write, the retry comes at least 200 ticks later, so M2 does not restore and M3 writes T back (G8 and G12 keep their results). A snapshot that holds T again is case 3. |
| Section 5, K2's first unbounded case (lines 364-366) and residual 4 (lines 429-431) | A late or failing disjoint successor no longer delays the removal's continuation. After the atomic move, K2 lasts about one tick unless the write ends UNKNOWN; UNKNOWN is the only unbounded case. |
| Residual 3 (lines 426-428), section 8's F3 line (569-570), the ADR note (lines 594-599), and the R-035 lines 617 and 619-620 | "while F3 is open every flight waits" becomes "other flights keep advancing on their acknowledged records; their next journal step waits until storage is repaired". The successor-generation case leaves the duration text. |
| G11 (lines 507-508) | Expected result changes: with the successor held, M2 runs as soon as the removal's own snapshot commits and restores within the limit. G11 asserts that, and that no write-back happens. |
| G13 (line 513) | "live flights do not advance while F3 is open" becomes "acknowledged flights keep advancing; their next journal step waits". |

## 7. Saved format

No record, key, schema or root changes. Slot sequences, leases and continuations are session-only state owned by the
`RocketTransferSavedData` instance, which lives as long as the server; none of it is a global static collection. The
writer, checkpoint, codec, root 3, record schemas 1 to 3 and the outbox encoding are unchanged. Older jars read the
journal unchanged, and W44's baseline is unchanged. No migration is needed. Nothing new refuses a save, so AGENTS.md
section 3.3 needs no new risk row for that. No new log key is added.

<div style="page-break-after: always;"></div>

## 8. Tests and estimates

**A0 (unit tests, detached journal and fake completions):**
- the table in section 2.1 for slots and absences: no A, a below and above s(X), NOT_COMMITTED with A retained,
  UNKNOWN, blocked, unreadable, stopping, and an untouched slot before the baseline;
- a remove followed by an identical put stays unacknowledged until a snapshot taken at or after the put commits, and
  `resourceMutationUnproved` holds throughout (W85);
- three slots changed while both writer slots are busy: all three continuations run after the later commit, and none
  is discarded;
- L1 committed while L2's change sits in the held successor: L1 runs and L2 waits;
- discard only when a slot changes, once per continuation, and on stop, abort and unreadable;
- the limit per L and in total, refusal without mutation, FIFO order and the 64-run budget;
- the outbox dependency;
- `RocketJournalContinuationTest`'s `Error` cases, unchanged.

**A1 (GameTests with real services, a held writer and checked reloads, as in W77):**

| Test | Assertions |
|---|---|
| G1, two-transfer delayed write | Flights A and B. A's DESTINATION_SPAWNED snapshot is held. B's countdown, ascent and transit continue, and its height rises tick by tick. B's own next step waits for the same file. A places nothing before its acknowledgement. Both complete with one entity each, no discard and one generation per step. In one tick both can step. |
| G2, UNKNOWN and restart | A write ends UNKNOWN: both flights stop, their continuations stay, the retry commits, and each continuation runs once with no duplicate. A checked-file reload, as in W77, ends in the same state. |
| G3, definite failure | NOT_COMMITTED keeps A: an acknowledged descent lands; a launch's `canQueueEffect(L)` refuses; the next steps wait; after the retry everything completes. |
| G4, governed | Two W11b2 governed flights. One's F4 write is held; the other's F3 transitions continue, with no cross-record `GOVERNED_JOURNAL_UNACKNOWLEDGED` fence. |
| G5, W44 | Two Moon landings. The second lands NOT_FIRST while the first's ELECTED write is held; the first lands only after its acknowledgement. |
| G6, recovery and reconnect | An unclassified record recovers, and a passenger reconnects, while another record's write is held. No ticket is added for an unacknowledged record. |
| G-N2 | A detached journal with a limit of 2. Two other flights' continuations are pending behind a held writer; L's arrival pad is blocked. The fail-back refuses before mutation: the source, a byte-identical T, the pad claim and `liveTransfers` are unchanged. One write commits, so exactly one continuation runs. L's fail-back then reserves and removes T. After its acknowledgement the pad is released, the ID is gone, and the return reason is logged once. Variants: the removal ends UNKNOWN and then commits, with the continuation running once; and a reload. |

**S2 and native (packaged dedicated server; base: `scripts/run_v180_rocket_journal_failures.py`):**
- **Eight-transfer timing**, proposed runner `run_v180_transfer_concurrency.py`. Eight ordinary flights, staggered and
  simultaneous, plus a variant in which one record's write is held through the gated
  `-Dadvancedrocketrycommunity.rocketJournalFailure` policy. Report the phase timeline of each flight; the ticks in
  which a flight with an acknowledged slot and no pending continuation still did not step, which must be 0;
  continuation latency (p50, p95, max); writer timing; the largest queue; per-tick main-thread time against docs/17
  (mean at most 25 ms, P95 at most 50 ms, P99 at most 100 ms); and, for the whole-root consumers, the fraction of clean
  ticks and the longest gap between them. Acceptance: each flight's timeline equals the single-flight timeline plus
  only its own write latency per step. That is 2 sessions with one restart check each: **4 starts**.
- **F3 cuts:** kill with A's snapshot committed and B's change in the held successor, and kill after UNKNOWN. Two
  restarts each; A durable, B at its last acknowledged phase, no duplicate. **2 cuts, 6 starts**.
- **N2 cuts:** after the refused reservation; after the removal's atomic move and before its continuation; and after an
  UNKNOWN removal; plus W11B3's fourth, a governed exact-source return if W11b2 has merged one. **4 cuts, 12 starts**.
- **Lock time,** with W11B3 section 5's basis ((starts x 20 s + 15 min of setup + an 18-minute GameTest run) x 1.5):
  P1-F3 with 10 starts is about 0.9 hours, and P1-N2 with 12 starts is about 0.9 hours. As one leaf (22 starts and one
  GameTest run), it is about 1 hour. These are planning figures, not executed evidence.

## 9. Risk and ADR text for Root, and open questions

**R-035, the clauses that change.** Root replaces the "持续时间与可用性" clause and, against evidence, removes F3 and N2
from "未关闭项":

> 持续时间与可用性（P1-F3 之后）：每条转移记录只等待包含其当前版本的日志快照被确认，其他记录的写入不再暂停它；
> 一次推进只让本火箭等待包含该变更的写入（实测八转移写入 p95 约 125 ms，即 2 到 3 tick；最大合法日志约 117 tick）。
> 所有记录共用一个文件：写入明确失败时，已确认的阶段（倒计时、上升、转移、下降、着陆）继续推进，需要新写入的步骤
> 等待重试成功；写入结果未知、日志被阻断或不可读、以及每次会话首次写入确认之前，所有转移暂停推进，与之前相同。
> 托管结算、楼层回收、受管火箭的 F1 准入和测试钩子仍等待整个日志确认，持续流量下可能等到流量间歇。
> `failBackToSource` 先预留续体再改动来源、记录和着陆台，预留失败时什么都不改（N2）。

W85's R-035 addition: "影响范围" at line 617 becomes "……P1-F3 之后，日志写入持续失败时其他航班继续推进已确认的
阶段，下一次需要写日志的步骤等待存储修复。"

**ADR-065 implementation note (proposed).**

> **P1-F3 and P1-N2 (W86).** The transfer journal keeps one file, one writer and one exact acknowledgement. Each record
> slot, and the Moon-landing outbox, counts as acknowledged when the acknowledged snapshot was captured at or after the
> slot's last change in RAM. Live flights, recovery, reconnection and governed flight permits check that per-record
> lease instead of a clean journal, and every journal mutation first reserves its continuation, one per logical rocket.
> UNKNOWN, a blocked or unreadable journal, and the time before a session's first acknowledgement still pause every
> transfer. A definite failure stops only steps that need a new write. Escrow settlement, floor collection, governed
> F1 admission and test hooks still wait for a clean journal. No saved format changes. This is a development
> implementation note, not owner acceptance or a Gate result.

**Open questions, each with a recommended answer.**
1. *N2 in its own leaf, or in the P1-F3 leaf?* In the P1-F3 leaf, as its first commit. N2's test needs per-record
   dispatch for "one release".
2. *Convert the whole-root escrow consumers too?* Not in P1-F3. Their proofs were reviewed against whole-root leases,
   and their lease reaches the ledger writer's thread. Convert only if the eight-transfer run shows long gaps, under its
   own design note.
3. *Should governed permits keep the no-failed-write hold?* Yes. It is W11b2's hold; revisit it with W11b2 after the
   merge.
4. *Let the writer replace an unstarted successor with a newer capture?* No. It would save at most one write of latency,
   and it changes the reviewed writer.
5. *Should a definite failure keep acknowledged progress running?* Yes. The disk definitely holds A, and it is what
   stops rockets freezing in mid-ascent with their passengers.
6. *Who changes the GameTest log expectations when the governed warning count falls?* The implementer reruns the
   unchanged checker and reports the new count; Root decides on the expectations. Nothing is loosened.
7. *Make `resourceMutationUnproved` follow the per-record rule?* Yes, keeping the untouched-absent rule, so that the
   interlocks and the scheduler never disagree.
