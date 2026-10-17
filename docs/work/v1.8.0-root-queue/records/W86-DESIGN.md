# W86: per-record journal scheduling (P1-F3) and the fail-back reservation (P1-N2)

**Proposed design, revision 2, 2026-10-16. Not implementation, review, risk acceptance or Gate evidence.**
Assignment: [TASK W86](../TASK-W86-P1-F3-DESIGN.md), and for this revision [TASK W86R](../TASK-W86R-P1-F3-REVISION-2.md);
record: [W86.md](W86.md). This is W11b3's leaf P1-F3-D. AGENTS.md section 13 requires a read-only review before code,
because the note changes when saved transfer data counts as acknowledged.

**Revision 2.** This revision applies Root's decisions 1 to 8 on the review of revision 1, finding by finding (W86R-1 to
W86R-16). The record maps each finding to the place that answers it. The acknowledged-slot rule of section 2.1
(conditions 1 to 4) is unchanged. Section 2.1 changes only where W86R-1, W86R-2 and W86R-12 require it: the leases,
"actionable", when a slot entry is dropped, and the definite-failure row of its table. The tables of sites, continuation
bodies and changed tests are in appendices A to C.

**Inputs.** All four hashes were checked in this session.
- F3: `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-review/REVIEW.md`, SHA-256
  `8c1da51138a655581c8a3256841615fd556c627eaed46edcf421b4fcc67de499`.
- N2: `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-P1F-review/ROOT-REVIEW.md`, SHA-256
  `8266bf6fb9266bf3ca8691e93e58ec2e6c05477ffc87eef428b548f7654e6ab1`.
- The review of revision 1: `D:/GitHub/ARCE-Task-Evidence/root-queue/W86-review/REVIEW.md`, SHA-256
  `e03efbf9b26a32001e5487fb53f44dd185233c64f5bf10c5d373336ed141bb8a`.
- Root's decisions on it: `D:/GitHub/ARCE-Task-Evidence/root-queue/W86-review/ROOT-DECISIONS.md`, SHA-256
  `c1ba7eb18e1de9600a4e6173c1c9a44ac1b0b874b3b2f0fca18a2b010166a0dd`.
- The contract: [W11B3-DESIGN.md](W11B3-DESIGN.md) revision 2, section 1 rows P1-F3 and P1-N2 and the leaves P1-F3-D,
  P1-F3 and P1-N2 of section 5; and [W85-DESIGN.md](W85-DESIGN.md) revision 3 (P1-F8, frozen).

**Code.** W11b2 at `a8d81d24` (continuation 25), read only through `git show` and `git grep`. `J` is
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity`. A citation `Class:line` means that class under `J` at
`a8d81d24`. The Minecraft and Forge facts used here were read with `javap` from the mapped Forge 47.4.10 jar; the record
lists them. W11b2's F5 to F8 are not written yet; the leaf applies this note's rules to whatever W11b2 merges.

**Notation.** L is a logical rocket and T its transfer record; the journal holds at most one record per L
(`RocketTransferSavedData:633-646`, and on load). A **slot** is either L's place in the journal (T or nothing) or the
single root slot of W44's Moon-landing outbox. **A** is the latest exact acknowledgement held by the checkpoint
(`RocketJournalCheckpoint:32-39`), with sequence **a**. **s(X)** is the RAM mutation sequence just after the last change
to slot X in this session. Further terms:
- A continuation is **queued** while it waits in the journal's queue. A **reservation** is the single-use token of
  section 3 that a site takes before it changes the journal. **actionable(L)** and **clean** are defined in section 2.1.
- From W11b2: **F1** is governed launch admission, the ledger write that records ARMED (`EscrowFlightCheckpoint.begin`).
  **F2** puts the governed PREPARED record and starts the countdown in its continuation (`EscrowGovernedPreparation`).
  The **governed source executor** advances a governed source (`EscrowGovernedSourceFlight`). **F4** puts the governed
  DESTINATION_SPAWNED record and publishes the destination (`EscrowGovernedDestination`). The **source envelope** is the
  source rocket's native state that F1 checked. **O3**, **O3J** and **O4** are settlement's proof ledger write, its
  journal removal and its releasing ledger write (`EscrowSettlementRecovery`).
- From W85: **P** is the session record of a landed disassembly under way. **M0** to **M3** are its request, its release
  (remove T and queue), its restoration (inside the removal's continuation) and its write-back. **K2** is the crash
  window after the removal's atomic move and before its continuation runs. **G8** to **G13** are W85's planned GameTests,
  named in section 6.
- Proposed names do not exist yet.

## 1. The current gating

At `a8d81d24` every dependent effect waits for the whole root. `RocketTransferService.tick` returns before recovery,
reconnection, governed dispatch and the live loop unless `retryChecked` reports the whole root acknowledged-current
(:268). The live step and the destination spawn test `acknowledgedCurrent()` again (:494, :577), so the first transfer
that writes in a tick stops every later one: one phase advance per acknowledgement, server-wide. That is F3. Recovery,
reconnection, the governed permits, the escrow ledger, W44 and the release-test hooks test the same whole root.

Appendix A.1 lists every site, with what it waits for and why. Revision 2 adds the sites that W86R-3 found:
- `RocketTransferService.releaseLandedReservation` (:466-475), which removes T and ignores its queue result;
- the governed launch's discard callback (:141-148) and the result code of `cancelCountdown` (:252);
- pre-F2 cancellation (`EscrowPreF2Cancellation:56-57, 72, 86`) and the escrow audit (`RocketEscrowAudit:81-84`);
- the mutations without a continuation of their own: settlement's O3J removal (`EscrowSettlementRecovery:96-102`), the
  Moon-landing delivery (`FirstEventSources:41-48`) and F2's put before its queue call (`EscrowGovernedPreparation:39-40`);
- W44's publication call that queues without a mutation (`MoonLandingCompletion:50-53`).

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

- **Slot sequences.** Every journal mutation already increments the RAM sequence on the owner thread
  (`RocketTransferSavedData:193-195, 204-206, 279-281, 310-312, 319-322, 338-350`, and the load-time migration at
  :145-147). Option A also records s(X) for the slot it changes: `put`, `replace`, `migrateCommitted` and `remove` for
  L's slot, and `electFirstMoonLanding` and `deliverFirstMoonLanding` for the outbox. Records decoded at load get the
  load sequence (1, or 2 after a load-time migration). A slot entry also keeps each transfer ID that a change removed
  from RAM (by a removal or a `replace`), until that change is acknowledged and L is actionable, for pruning
  (section 3). An absent slot's entry is dropped once its absence is acknowledged and L is actionable. An absent slot
  that nothing changed in this session has no entry; it is **untouched**.
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
- **Actionable (W86R-1).** `actionable(L)` holds when L's slot is acknowledged, no continuation for L is queued and no
  reservation for L is held. It is the one per-rocket gate: every entry point that acts for one rocket checks it
  (appendix A.2), and every lease requires it. Today's whole-root gate gave the second and third conditions implicitly,
  because `pollWriter` runs every ready continuation before any whole-root check can pass
  (`RocketTransferSavedData:456-474`). Under option A they must be explicit: the 64-run budget of section 3 leaves
  acknowledged continuations queued, and W44's election waits for the outbox while L's record is already acknowledged.
- **Leases.** A **version lease** for L captures L, s(L), the RAM record object and A's digest for it. It is valid
  while `actionable(L)` holds, s(L) is unchanged and RAM still holds that object. An **absence lease** captures L and
  s(L), or "untouched". It is valid while L has no record in RAM, `actionable(L)` holds and L has not changed since the
  capture (W86R-12): a put and a remove between two checks give L a new s(L), and the lease fails. Dropping an
  acknowledged absent entry is not a change, so a lease that captured that entry's s(L), or "untouched", stays valid
  until L next changes. Leases are owner-thread values. They read session state only and never load a chunk or touch
  storage.
- **Clean (W86R-1).** The consumers that stay whole-root (section 3) require `clean()`: the root is acknowledged-current
  (`RocketTransferSavedData:379-381`), no continuation is queued and no reservation is held. While the root is
  acknowledged-current every queued continuation is runnable, so this is Root's "no runnable continuation queued".
- **Interlocks (W86R-12).** `resourceMutationUnproved(L)` becomes "L is not actionable", with two of today's rules kept
  explicitly. After a failed write that left no A, every rocket is unproved, including untouched absent ones (:402).
  Before the session's first A, an untouched absent rocket counts as proved (:404). The new rule is stricter than today
  in four cases, all safe: after a remove and an identical put; after a put and a remove that were never acknowledged
  (A lacks L, but a < s(L)); for a touched absent rocket before the first A; and while L has a queued continuation or a
  held reservation.

| State | Records and absences | Queued continuations | New reservation (section 3) |
|---|---|---|---|
| First write of a session, before the baseline commits | No A, so no slot is acknowledged, as today (W44 section 2.4). The interlocks keep :404 for untouched absent rockets. | Wait for the baseline or a later snapshot. | `reserve(L)` allowed; `canQueueEffect(L)` allowed unless a write has failed. |
| A COMMITTED snapshot at k | Acknowledged when k ≥ s(X). | Run in queue order once every dependency is acknowledged, at most 64 runs per tick. | Allowed. |
| Definite failure (NOT_COMMITTED) | The retained A still describes the disk: no move happened, or readback equals A's digest (`RocketJournalCheckpoint:77-90`). Slots that A covers stay acknowledged, so their entity-only progress continues; slots changed since then wait for a retry. | **Wait for the retry, even when acknowledged (W86R-2)**, except a continuation that opts in. P1-F3 opts in none; W85's M2 will (section 6). | `reserve(L)` allowed; `canQueueEffect(L)` refused, as today. |
| UNKNOWN, or a `Throwable` on the writer | No A (`RocketJournalCheckpoint:91-98`, `RocketJournalWriter:91-94`), so no slot is acknowledged. Every flight pauses, as today. | Kept, not discarded. They run after a later COMMITTED snapshot if their slots are unchanged, after the rechecks of section 3 (W86R-8). | `reserve(L)` allowed; `canQueueEffect(L)` refused. |
| Blocked (future or invalid) or unreadable root | Not operational: nothing is acknowledged, as today (`RocketTransferService:262`). | None; `becomeUnreadable` discards them (:510). | Refused. |
| Stopping or abort | Leases fail. | Discarded, as today (:522, :535). | Refused. |

Two consequences of this table. First, a definite failure now stops only steps that need a new write. Entity-only
progress on acknowledged records (countdown, ascent, transit, descent and landing) continues, and every queued
continuation waits for the retry. Today a definite failure stops everything. Second, a continuation is no longer
discarded because its exact generation was coalesced or failed; a later snapshot that still holds its slots unchanged
acknowledges it. Today the exact generation can also commit on a retry 200 ticks later, so a late continuation is not
new. Option A keeps more of them, however, so section 3 makes every body recheck the world state it captured (W86R-8).

## 3. Scheduler, call sites and bounds

**Reservations (W86R-4).** A reservation is a single-use token owned by the server thread. It names L, and only the
journal creates one.
- `reserve(L)` returns a reservation, or nothing when the journal is not operational, is stopping, already holds a
  queued continuation or a reservation for L, or holds `RESERVATION_LIMIT` continuations and reservations together. The
  limit is 128 (see the invariant below). It is a constructor parameter, so that a detached test journal can use 2.
- `canQueueEffect(L)` is `reserve(L)` that a failed write also refuses, and it returns the same kind of token. It
  replaces today's `canQueueEffect()` at F4 (`EscrowGovernedDestination:68`), W44's election
  (`MoonLandingCompletion:61`) and W85's M0. The no-argument method is removed.
- `afterAcknowledged(reservation, dependencies, effect, discarded)` consumes the token. The dependencies are L's slot as
  RAM holds it at this call (the record object, or absence), plus the outbox slot for W44's two publication calls.
  - If every dependency is already acknowledged, no write has failed (or the continuation opts in) and the journal is
    not stopping, the body runs at once, as today's immediate path does (:446). The token is consumed first, just as a
    queued continuation is removed before it runs (:466), so a body can reserve L again. The spawn continuation's
    fail-back and W85's M3 rely on this.
  - Otherwise the continuation is queued, and `flushChecked` is called.
  - If the journal stopped, aborted or became unreadable since `reserve`, the call returns false and runs neither
    callback, as today's refusal does (:433).
- `close()` releases a token that was not consumed, and does nothing after consumption. Each site holds its token in
  `try`-with-resources. So every path that does not queue releases it, including a `put` or `replace` that throws
  (`RocketTransferSavedData:258-275, 326-347`), which `prepareLaunch` catches (`RocketTransferService:203`).
- While a token is held, it counts as a queued continuation for `actionable(L)` and for the limit.
- **Order at every site: check, reserve, mutate, queue.**
  - A site makes every check that reads `actionable(L)` before it reserves. That covers direct calls and the indirect
    ones, through `flightAllowed`, `resourceMutationUnproved` or a lease.
  - Between `reserve` and `afterAcknowledged`, a site does only the effects and the mutation that it does today. It
    calls no acknowledgement predicate and no `retryChecked`. The bay gate that recovery calls there reads no
    acknowledgement in production: production keeps the default gate (`RocketTransferBayDispatch:22`),
    `RocketTransferBayRecoveryGate.UNCLAIMED` (:57-63), and only a GameTest installs another.
  - A site that must recheck after reserving passes its token, as `actionable(L, reservation)`, which ignores that one
    reservation.
- The tokenless `afterAcknowledged(server, L, effect, discarded)` stays for the exceptions below. It reserves and queues
  in one call, and is refused whenever `reserve(L)` would be. The package-private `queueEffect(L, …)` stays as the same
  one-call form, so the existing unit tests keep calling it.

**Exceptions to reserving first (W86R-3).** These sites change the journal without a reservation of their own, on
purpose:
- **F2** puts before its queue call (`EscrowGovernedPreparation:39-40`). This keeps W11b2's frozen order, in which a
  refused queue call retains ARMED and PREPARED for recovery (:52-53). F1 started on a clean root, so nothing was queued
  for L then, and L has had no record since, so no site can have queued a continuation for it; no reservation outlives
  the call that took it. Only a stop, an abort, an unreadable root or the total limit can refuse the queue call.
- **Settlement's O3J removal** (`EscrowSettlementRecovery:96-101`) runs only on a clean root, so no continuation is
  queued for any rocket, and it calls only `flushChecked`. O4 then waits for a clean root (:102).
- **The Moon-landing delivery** (`FirstEventSources:41-48`) changes only the outbox, after the progression receipt is
  durable, and calls only `flushChecked`. No rocket-side effect waits for DELIVERED.
- **W85's M3 at a stop or an abort** only puts T, because no continuation can be queued then (W85-DESIGN.md:286-287).
- **Test code:** GameTest fixtures and release-test probes that put or remove directly, for example
  `FirstEventRocketFixtures:90-94` and `EscrowPreF2Probe:135, 142`.

**Continuations.** A queued continuation keeps:
- L, and its dependencies with their expected RAM values;
- its body and its discarded callback;
- whether it opts in to run during a failed write;
- the transfer IDs that it names.

A dependency has **changed** when RAM no longer holds the expected value: another record object, a record removed, a
record added, or another outbox value.

**The visit (W86R-11).** `pollWriter` drains the writer as today. If the journal is operational and not stopping, it
then visits the queue from its head:
- a continuation with a changed dependency is removed, and its discarded callback runs;
- a continuation whose dependencies are all acknowledged is removed and runs. A `RuntimeException` is handled as today
  (:469-472), and so is an `Error` (`Effect.run`, :74-78). The exception is a failed write: then a continuation that
  does not opt in stays in the queue, and the visit moves past it;
- the first continuation that has neither changed nor been acknowledged ends the visit. Queue order is mutation order,
  so no later entry that was queued right after its own mutation can be acknowledged. An entry that was queued without a
  mutation (W44's projection at `MoonLandingCompletion:52`, or recovery's KEEP_DESTINATION without a put) waits at most
  until the entry ahead of it is acknowledged.

Queue order thus runs an earlier change's continuation, such as a removal that releases a pad, before a later one that
the same A acknowledges.

Bounds and re-entry:
- Each body run and each discarded callback counts as one run. At most 64 runs happen per server tick, across all
  `pollWriter` calls; the rest wait for the next tick. A call inspects the entries that it runs or discards, plus the one
  entry that ends it, plus, during a failed write, at most 128 entries that it moves past.
- A body may re-enter `pollWriter` through `retryChecked`. The nested call drains the writer and publishes as usual, but
  starts no second visit. The outer visit reads the queue head afresh after each run, so it sees entries that the body
  added or removed in order. If the nested call finds the root unreadable, the queue is empty when the outer visit
  resumes.
- `stop()`, `abort()` and `becomeUnreadable()` discard every continuation, regardless of the budget, as today. No token
  is held across them, because a token never outlives the call that took it.

**The bound of 128 (W86R-11).** Invariant: at most 64 rockets with a record and at most 64 rockets without one have a
queued continuation or a held reservation, one each.
- **The rockets with a record** number at most 64, because the journal holds at most 64 records.
- **The absent rockets with a queued continuation** were all present in RAM at the earliest of their removals.
  - A record added after that removal queued its own continuation behind the removal's continuation.
  - Its rocket becomes actionable, and therefore removable, only after its own continuation runs.
  - In queue order, that is after the earlier removal's continuation has run or been discarded.
  - The visit runs no entry out of order except an opt-in one during a failed write, and the only opt-in, W85's M2, is a
    removal's continuation. A continuation that runs at once without being queued adds no record.
- **The one way around the argument** is F2's refused queue call, which leaves a record without a continuation. It
  happens only at the total limit or a stop.

In production a refusal for the total limit therefore never happens, and `reserve` enforces 128 as a defensive bound.
A0 asserts the invariant.

**One writer and bounded captures.** `RocketJournalWriter` and the checkpoint's write protocol are unchanged.
- There is still one active snapshot and one successor, each of at most 64 immutable record references. Coalescing
  becomes normal and safe.
- During a definite failure, `afterAcknowledged` still calls `flushChecked`. That can fill the writer's free successor
  slot once (`RocketJournalWriter:65, 68`). Further requests are refused until the active write completes, and the
  writer retries the failed active snapshot on its own 200-tick clock (:72, :108). Several steps in one tick therefore
  cause no write storm.
- Main-thread bookkeeping is at most 129 slot entries (64 records, 64 absences and the outbox) and 128 continuations
  and reservations, with constant-time lookups. Dispatch visits no blocks.

**Call sites.** Appendix A.2 lists every site after P1-F3. In short:
- `tick` keeps calling `retryChecked` every tick to drive writes and their spacing, and no longer returns on its result.
- Every per-rocket entry point checks `actionable(L)`:
  - the live step, the destination spawn and the fail-back;
  - recovery's `step`, `recover`, `recoverById` and `tryReconnect`, `finishSourceSettlementIfComplete` and
    `fences.reevaluateOne`;
  - the governed `acquire` and `Authority.valid()`;
  - `cancelCountdown`, `releaseLandedReservation`, `prepareLaunch` with its replace path, and `MoonLandingCompletion`.
- Every ordinary mutation reserves first.
- Governed permits bind the version lease of their exact record and keep W11b2's `!stopping && !failedWrite`.
- The whole-root consumers require `clean()`. `retryChecked` returns `clean()`, and `acknowledgedGuard`,
  `acknowledgedLease` and `actionAuthorityAvailable` are granted only on a clean root. `acknowledgedCurrent()` keeps its
  meaning, so the GameTest fixtures that wait for it are unaffected.
- When the leaf starts, it rechecks appendix A against the merged W11b2. It greps for `afterAcknowledged`,
  `retryChecked`, `acknowledgedCurrent`, `actionAuthorityAvailable`, `acknowledgedLease`, `acknowledgedGuard`,
  `canQueueEffect`, `queueEffect`, and the journal's `put`, `replace`, `remove` and `migrateCommitted`.

**Continuation bodies (W86R-2, W86R-8).** Under option A a body usually runs while other rockets' later changes are
already in RAM, so a whole-root predicate inside a body is usually false. Appendix B lists every body and discarded
callback, the predicates each one tests, and their per-record replacements. Two rules apply to all of them.
- **No body or callback tests the whole root.** The governed launch's discard callback (`RocketTransferService:143`)
  replaces `actionAuthorityAvailable()` with a per-record guard. It releases the tentative pad only when all of these
  hold: the journal is not stopping, L's slot is acknowledged, no T exists for the request, and
  `resourceMutationAllowed(rocket)` holds. F2's and F4's bodies obtain their authority through the version lease.
- **A body rechecks every captured entity or world observation before acting** (decision 6).
  - The checks: the entity is alive and added to its world (`Entity.isAlive`, `Entity.isAddedToWorld`), its chunk's
    entities are loaded (`ServerLevel.areEntitiesLoaded`), and it is still the record's entity. The spawn continuation
    also repeats the destination readiness and landing checks of `spawnDestination` (:587-599) before `addFreshEntity`.
  - If a recheck fails, the body changes nothing and drops the transfer ID from `liveTransfers`, as `tickSource` does
    for a missing source (:524-529). The record is left to recovery.
  - Why this is needed: flight tickets expire after 400 ticks (`RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS`;
    `TicketType.create(String, Comparator, int)` stores the timeout, and `Ticket.timedOut` compares it with the
    ticket's creation tick). A rocket with a queued continuation no longer refreshes its tickets, because the refresh
    (`RocketTransferService:500-505`) sits behind the gate. So a continuation kept across a long UNKNOWN can find its
    entities unloaded.

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
| W44's outbox | ELECTED is acknowledged before LANDED, because both publication calls depend on the outbox slot. DELIVERED still follows the durable receipt, and the durable view is still the latest A. **No observable change (W86R-5).** A Moon landing whose disposition would be NOT_FIRST only because the RAM slot holds another transfer's event (`FirstEventElection:29-30`), that is, while the durable view is not KNOWN with a non-empty outbox, answers WAIT and holds its terminal pose (`RocketDestinationCompletion:72-76`). It does not call the FP7A seam `moonDeferred`, because today's tick never reaches this state. A second lander therefore cannot land, or save LANDED or its `moon_landing` advancement, before ELECTED is durable, and the crash order stays W44's. The cost is one election write of waiting, for Moon landers only, once per world. |
| W11b2's governed record 3 | F2 before COUNTDOWN and F4 before publication; ARMED, the ledger generations, the barrier proofs and the native envelopes are unchanged. The legacy loop still skips record 3 (`RocketTransferService:279`), and so does recovery (`RocketTransferRecoveryService:106`). The no-failed-write hold stays. Flight permits bind the record lease, which proves the fact that they use (this record is on disk), and nothing they use concerns another record. |
| P1-F8's write-back | The removal is acknowledged before restoration, and the write-back before P ends (section 6). |
| Proofs and S2 cuts (W86R-15) | A single-flight fixture writes the same snapshots in the same order, so its cuts keep their disk state. A fixture with several flights coalesces differently: a generation can hold several rockets' changes, and there are fewer generations. W11b2's eight-transfer benchmark (48 acknowledged checked writes, `records/W11B2.md:547, 739` at `a8d81d24`) is the before-and-after baseline (section 8). New are the interleavings in which one record's effect runs while another record's write is in flight; section 8 tests them. |
| Holds | Blocked and unreadable roots, UNKNOWN, stopping, bay claims, fences, escrow locks, resource interlocks and the governed no-failed-write hold all stay, and queued continuations now also wait while a write has failed. Only the cross-record "another record is being written" hold goes. |
| Replay exclusion | Design 2's ARMED serial, receipt and retired-T rules, `prepareLaunch`'s REQUEST_REPLAYED, and "record 3 is never legacy authority" (`RocketTransferEscrowRules:20`) are not touched. |

Option A keeps every item. Nothing here needs Root to stop the design.

**Visible changes (W86R-16).** This is the one list of what players and operators can observe.
1. Flights, recovery and reconnection no longer pause while another record's write is in flight. This is the purpose of
   P1-F3.
2. During a definite failure, flights whose slots are acknowledged keep their entity-only progress. Their next journal
   step, and every queued continuation, waits for the retry.
3. A step whose generation was coalesced now completes. Today its continuation is discarded
   (`RocketTransferSavedData:462-463`). For a launch, the requester is then told that the launch was discarded, and
   recovery returns the rocket.
4. A request for a rocket whose own continuation is still queued, or whose reservation is held, waits or is refused as
   if its record were unacknowledged. With the 64-run budget this can last one tick longer than today.
5. Fewer `GOVERNED_JOURNAL_UNACKNOWLEDGED` fence warnings appear, because another record's write no longer holds a
   governed record.
6. The whole-root escrow consumers can wait longer under traffic (section 9, W86R-6).

Moon landings show no change (decision 4).

## 5. N2: reserve before `failBackToSource` changes anything

1. Keep the checks `flightAllowed(SETTLE_SOURCE)` and the bay gate (`RocketTransferService:670-671`). Through
   `resourceMutationUnproved`, `flightAllowed` now also requires `actionable(L)`.
2. Call `reserve(L)`, held in `try`-with-resources. A refusal returns false before any mutation: the source keeps its
   flight state and position, T stays in RAM unchanged, the pad stays claimed and `liveTransfers` keeps the ID. In
   production only a stop or an abort can refuse here: L is actionable, so it has no continuation or reservation, and
   the total limit is never reached (section 3). The callers then see the state they started from.
   - `spawnDestination` tries again on the next tick.
   - After a refused fail-back in the spawn continuation, ordinary recovery finds the source alone and keeps it
     (KEEP_SOURCE, `RocketTransferRecoveryDecision:21-22`), which is the fail-back's own outcome.
   - `cancelCountdown` answers TRANSFER_JOURNAL_BLOCKED, as the P1F fix does.
3. Only then change the source (stationary flight, origin, feedback, remount), call `journal.remove(T)` and pass the
   reservation to `afterAcknowledged`. The continuation's dependency is L's absence. On acknowledgement it rechecks the
   bay gate, releases the pad, drops the ID and logs the return reason once, as today. Its discarded callback runs
   `completion.discarded()`.

Inside the spawn continuation (:626), the running continuation has already been removed and its token consumed, so the
reservation succeeds.

**One rule, with named exceptions (W86R-9).** The following sites take `reserve(L)` before their first change:
`failBackToSource`, `prepareLaunch`, `updatePhase`, `migrateCommitted`, `recover` (before its entity changes) and the
other recovery removals, `releaseLandedReservation`, W44's publication at `MoonLandingCompletion:52`, and W85's M3.
F4, W44's election and W85's M0 take `canQueueEffect(L)`, which a failed write also refuses, as frozen W85 requires at
M0 (W85-DESIGN.md:159). Section 3 names the exceptions. A crash before the removal reaches disk leaves T on disk with
the source present and no destination, so recovery answers KEEP_SOURCE, as today.

**Delivery (W86R-14).** N2 is the first commit of the P1-F3 leaf, and it is self-contained on today's two-slot queue.
- **The check.** Before the first mutation, `failBackToSource` checks what `afterAcknowledged` would refuse after the
  removal: the journal is operational and not stopping, and it holds fewer than two queued effects
  (`RocketTransferSavedData:433, 443, 447`). A failed write does not refuse this check, just as it does not refuse
  today's queue call.
- **The tests in that commit.** The refusal-without-mutation test (G-N2's first half) lands with it, together with its
  UNKNOWN and restart variants and a stop or abort variant.
- **The scheduler commit** then replaces the check with `reserve(L)`. Only "one release" waits for it: with whole-root
  dispatch, a write held for another flight frees no slot, so that property cannot be shown before. N2 thus closes even
  if the scheduler commit is reworked.
- **Root's update.** Root updates the leaf table of `W11B3-DESIGN.md` (lines 424 and 427) when it freezes this note.

## 6. P1-F8 after F3

Once F3 is in, W85's queued write-back holds the following:
- T in RAM, with s(L) at the write-back;
- P in the stage WRITE_BACK;
- the one reservation for L, consumed by the write-back's queue call;
- the rocket's interlocks, because L is not actionable until a snapshot taken at or after the write-back commits and its
  continuation has run.

It **no longer holds other flights**. A flight with an acknowledged record keeps its entity-only progress. While the
write-back's snapshot fails definitely, every flight's next journal step and every queued continuation waits, because
every record shares the file. The one exception is M2, which opts in to run during a failed write, so a removal that was
acknowledged before another record's write failed still restores within the limit. After UNKNOWN, every flight waits, as
before. The W85 text changes as follows. Root applies these changes when the second of P1-F3 and P1-F8 lands.

| W85-DESIGN.md | Change |
|---|---|
| M0, line 159 | `journal.canQueueEffect()` becomes `journal.canQueueEffect(L)`, which returns the reservation; M1's queue call consumes it. A failed write still refuses M0, as frozen W85 requires. |
| M1, line 178 | The queue call uses M0's reservation, and the removal's continuation opts in to run during a failed write. |
| M3 (b), lines 265-267 | "M3 runs inside `pollWriter` with the journal acknowledged-current" becomes "M3 calls `journal.reserve(L)`. The removal's continuation was removed and its reservation consumed before it ran, so the reservation holds except at a stop or an abort, where M3 only puts T, as in (c). The journal need not be clean, and a failed write does not refuse it." |
| M3 (b), lines 274-277, and the "before the return at line 240" WARN at lines 283-285 | Replace with: "After P1-F3, other flights advance on their acknowledged records. A flight's next journal step, and every queued continuation except M2, waits while the write-back's snapshot keeps failing, and every flight waits after UNKNOWN." The WARN stays at the start of the tick, at most once per P and stage. |
| Discarded continuations, lines 291-299 | "Four cases, not the three that revision 2 listed" becomes three cases: (1) the action returns false or throws; (2) L's record in RAM, or its absence, is no longer the one captured when the continuation was queued (today's case 3); (3) a stop, an abort or an unreadable root (today's case 4). Today's case 2 goes: a later snapshot that still lacks T acknowledges the removal, and M2 runs. After a coalesced capture that is within the 200-tick limit, so M2 restores. After an UNKNOWN or a failure of the removal's own write, the retry comes at least 200 ticks later, so M2 does not restore and M3 writes T back. A snapshot that holds T again falls under case 2. |
| Section 5, K2's first unbounded case (lines 364-366), and residual 4 (lines 429-431) | A late or failing disjoint successor no longer delays the removal's continuation: once the removal's own snapshot commits, M2 runs, also during another write's definite failure. After the atomic move, K2 lasts about one tick unless the removal's own write ends UNKNOWN; UNKNOWN is the only unbounded case. |
| Residual 3 (lines 426-428), section 8's F3 line (569-570), the ADR note (lines 594-599), and the R-035 lines 617 and 619-620 | "While F3 is open every flight waits" becomes "other flights keep advancing on their acknowledged records; their next journal step waits until storage is repaired". The successor-generation case leaves the duration text. |
| G8 (line 504) and G12 (lines 509-510) | Results unchanged: a write-back, and no block appears. G8's path changes: the removal's continuation is kept across the UNKNOWN instead of being discarded, and after the later generation commits it runs as M2's late branch, past the 200-tick limit, which leads to M3. G12 already takes that path when the retry commits the removal's own snapshot (`RocketJournalWriter:107-111`), and it still does. |
| G11 (lines 507-508) | The expected result changes: with the successor held, M2 runs as soon as the removal's own snapshot commits and restores within the limit. G11 asserts that, and that no write-back happens. |
| G13 (line 513) | "Live flights do not advance while F3 is open" becomes "acknowledged flights keep advancing; their next journal step waits". |

## 7. Saved format

No record, key, schema or root changes. Slot sequences, reservations, leases and continuations are session-only state
owned by the `RocketTransferSavedData` instance, which lives as long as the server; none of it is a global static
collection. The writer, checkpoint, codec, root 3, record schemas 1 to 3 and the outbox encoding are unchanged. Older
jars read the journal unchanged, and W44's baseline is unchanged. No migration is needed. Nothing new refuses a save, so
AGENTS.md section 3.3 needs no new risk row for that. No new log key is added, and the Moon-landing WAIT of section 4
logs nothing.

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
- a discard only when a slot changes, once per continuation, and on stop, abort and unreadable;
- **more than 64 ready continuations (W86R-1):**
  - one commit acknowledges 65 or more, and exactly 64 run in that tick;
  - until the rest run, every per-rocket entry point refuses those rockets, and `clean()` is false;
  - the next tick runs the rest;
- **the reservation lifecycle (W86R-4):**
  - `put` throws after `reserve`, and L is actionable again afterwards;
  - a held token makes L non-actionable and counts toward the limit;
  - the immediate path consumes the token before the body, and the body can reserve L again;
  - `close()` after consumption does nothing;
  - `afterAcknowledged` after a stop returns false and runs neither callback;
- **the scheduler and its bounds (W86R-11):**
  - the limits per L and in total (with a limit of 2), each refusal without mutation;
  - FIFO order, and the 64-run budget with discards counted;
  - the visit ends at the first unacknowledged entry, and a nested `pollWriter` starts no second visit;
  - the 128 invariant over generated sequences of puts, removals, commits, failures and stops, with no refusal for the
    total limit;
- **a failed write (W86R-2):** acknowledged continuations wait, an opt-in one runs, and the immediate path queues
  instead;
- **leases and interlocks (W86R-12):** an absence lease fails after a put and a remove between two checks and survives
  the dropping of an acknowledged entry; and the four stricter interlock cases of section 2.1;
- the outbox dependency, and the Moon-landing WAIT rule (W86R-5);
- a governed authority survives another record's put and fails on its own record's change. `EscrowGovernedDispatchTest`
  gains the first case;
- the governed launch's discard callback releases the pad while another record is dirty, and not while T exists
  (W86R-2);
- the prune rule: a removed transfer ID stays in the fence and bay sets until its removal is acknowledged and its
  rocket is actionable;
- `RocketJournalContinuationTest`'s `Error` cases, unchanged.

**A1 (GameTests with real services, a held writer and checked reloads, as in W77):**

| Test | Assertions |
|---|---|
| G1, two-transfer delayed write | Flights A and B. A's DESTINATION_SPAWNED snapshot is held. B's countdown, ascent and transit continue, and its height rises tick by tick. B's own next step waits for the same file. A places nothing before its acknowledgement. Both complete with one entity each, no discard and one generation per step. In one tick both can step. |
| G2, UNKNOWN and restart (W86R-8) | A write ends UNKNOWN and stays UNKNOWN for longer than the 400-tick ticket timeout. Both flights stop, and their continuations stay. When the retry commits, each continuation rechecks its captured entities. One whose entities stayed loaded runs once. One whose source or destination unloaded changes nothing, drops its ID and is recovered. No entity is duplicated, and each rocket ends with one entity. A checked-file reload, as in W77, ends in the same state. |
| G3, definite failure | NOT_COMMITTED keeps A. An acknowledged descent lands. A Moon election's `canQueueEffect(L)` refuses, and that lander waits. A launch reserves and queues, and its continuation waits, as the acknowledged ones do. After the retry everything completes in queue order. |
| G4, governed | Two W11b2 governed flights. One's F4 write is held; the other's governed source executor keeps advancing, with no cross-record `GOVERNED_JOURNAL_UNACKNOWLEDGED` fence. |
| G5, W44 (W86R-5) | Two Moon landings. While the first's ELECTED write is held, the second waits in its terminal pose, not LANDED and without `moon_landing`. After the acknowledgement the first lands, and then the second lands NOT_FIRST. |
| G6, recovery and reconnect (W86R-1) | An unclassified record recovers, and a passenger reconnects, while another record's write is held. No ticket is added for an unacknowledged record. Variant: a passenger reconnects while its rocket's spawn continuation is still queued. The reconnection waits, recovery does not keep the source, and after the continuation runs the passenger is placed on the destination. |
| G-N2 (W86R-14) | A detached journal with a limit of 2. Two other flights' continuations are pending behind a held writer; L's arrival pad is blocked. The fail-back refuses before mutation: the source, a byte-identical T, the pad claim and `liveTransfers` are unchanged. One write commits, so exactly one continuation runs. L's fail-back then reserves and removes T. After its acknowledgement the pad is released, the ID is gone, and the return reason is logged once. Variants: the removal ends UNKNOWN and then commits, with the continuation running once; a reload; a fail-back while the journal is stopping or aborted, which refuses without mutation; and a stop while the fail-back's continuation is queued, which discards it once and leaves the record to recovery after the restart. The refusal, UNKNOWN, reload and stop variants run first on today's two-slot queue (section 5). |

**S2 and native (packaged dedicated server; base: `scripts/run_v180_rocket_journal_failures.py`):**
- **Eight-transfer timing** (W86R-6, W86R-7, W86R-15), proposed runner `run_v180_transfer_concurrency.py`. Eight ordinary
  flights run staggered and simultaneously, plus a variant in which one record's write is held through the gated
  `-Dadvancedrocketrycommunity.rocketJournalFailure` policy. The run reports:
  - each flight's phase timeline. For each step it gives the sequence of the snapshot that first held the change, and
    the number of writes that completed between the change and its acknowledgement;
  - the ticks in which an actionable flight did not step. **This must be 0; it is the hard pass criterion;**
  - the step bound: without a failure, at most three write latencies per step. They are the rest of the active write,
    the successor captured before the change, and the write that captures it. A step beyond the bound is a finding;
  - continuation latency (p50, p95, max), writer timing, the largest queue, and per-tick main-thread time against
    docs/17 (mean at most 25 ms, P95 at most 50 ms, P99 at most 100 ms);
  - writes per cycle against W11b2's benchmark, which made 48 writes for eight transfers. `RocketJournalBenchmark` is
    rerun unchanged as the writer baseline;
  - **the whole-root consumers.** During traffic, the run starts one governed F1 admission, one pre-F2 cancellation and
    one settlement through the existing escrow release-test probes. For each it reports the attempts, the refusals or
    stalls and the completion latency. It also reports the longest interval with no journal mutation, against the
    measured durations of the barrier and the ledger write. Under continuous traffic their wait is unbounded, so the run
    states whether each consumer completed. Root decides open question 2 on these numbers.

  The consumers run in the same sessions. That is 2 sessions with one restart check each: **4 starts**.
- **F3 cuts:** kill with A's snapshot committed and B's change in the held successor, and kill after UNKNOWN. Two
  restarts each; A is durable, B is at its last acknowledged phase, and nothing is duplicated. **2 cuts, 6 starts**.
- **N2 cuts:** after the refused reservation; after the removal's atomic move and before its continuation; and after an
  UNKNOWN removal. W11B3's fourth cut, a governed exact-source return, is added only if W11b2 has merged one. **3 or 4
  cuts, 9 or 12 starts** (W86R-14).
- **Lock time,** with W11B3 section 5's basis ((starts x 20 s + 15 min of setup + an 18-minute GameTest run) x 1.5):
  P1-F3 with 10 starts is about 0.9 hours, and P1-N2 with 9 or 12 starts is about 0.9 hours. As one leaf (19 or 22 starts
  and one GameTest run), it is about 1 hour. These are planning figures, not executed evidence.

**Existing tests (W86R-10).** Appendix C lists every existing test whose expectation changes, with the old expectation,
the new one and the reason. Every other existing test stays unchanged.

## 9. Risk and ADR text for Root, superseded text and open questions

**R-035, the clauses that change.** Root replaces the "持续时间与可用性" clause and, against evidence, removes F3 and N2
from "未关闭项" (W86R-6, W86R-7, W86R-13):

> 持续时间与可用性（P1-F3 之后）：每条转移记录只等待包含其当前版本的日志快照被确认，其他记录的写入不再暂停它。
> 写入线程同时只有一个进行中的快照和一个后继快照，所以一次推进最多等待三次写入：进行中写入的剩余部分、变更之前
> 已捕获的后继写入，以及捕获该变更的写入。按实测八转移写入 p95 约 125 ms，一次推进最多约 7 到 8 tick；最大合法
> 日志的一次写入约 117 tick，一次推进最多约 350 tick。所有记录共用一个文件：写入明确失败时，已确认记录的实体推进
> （倒计时、上升、转移、下降、着陆）继续，需要新写入的步骤和已排队的续体等待重试成功；写入结果未知、日志被阻断
> 或不可读，以及每次会话首次写入确认之前，所有转移暂停推进，与之前相同。托管结算、楼层回收、托管审计、受管火箭
> 的 F1 准入、F2 之前的取消和测试钩子仍要求整个日志已确认且没有排队的续体；其中 F1 准入、F2 之前的取消和结算
> 还要求在各自的异步屏障和账本写入期间日志没有任何变更：F1 准入被拒绝，玩家需要重新发起发射；F2 之前的取消和
> 结算停顿，并按各自的延迟重试；持续流量下这一等待没有上限。`failBackToSource` 先预留续体再改动来源、记录和
> 着陆台，预留失败时什么都不改（N2）。

W85's R-035 addition: "影响范围" at line 617 becomes "……P1-F3 之后，日志写入持续失败时其他航班继续推进已确认记录
的实体阶段，下一次需要写日志的步骤和已排队的续体（W85 的 M2 除外）等待存储修复。"

**ADR-065 implementation note (proposed).**

> **P1-F3 and P1-N2 (W86).** The transfer journal keeps one file, one writer and one exact acknowledgement. Each record
> slot, and the Moon-landing outbox, counts as acknowledged when the acknowledged snapshot was captured at or after the
> slot's last change in RAM. A rocket is actionable when its slot is acknowledged and it has no queued continuation and
> no held reservation. Live flights, recovery, reconnection and governed flight permits check that the rocket is
> actionable instead of a clean journal. Every ordinary journal mutation first reserves its continuation, one per
> logical rocket. The exceptions are W11b2's F2 put, settlement's O3J removal, the Moon-landing delivery, a write-back
> at a stop, and test fixtures; each runs under its own frozen or whole-journal rule. Without a failure, a step waits
> for at most three writes. UNKNOWN, a blocked or unreadable journal, and the time before a session's first
> acknowledgement still pause every transfer. A definite failure stops only steps that need a new write, and queued
> continuations wait for the retry. Escrow settlement, floor collection, the escrow audit, governed F1 admission,
> pre-F2 cancellation and the test hooks still need a clean journal with no queued continuation. F1 admission, pre-F2
> cancellation and settlement also need no journal mutation during their asynchronous barrier and ledger write: F1
> admission is refused, and the other two stall and retry. Under continuous traffic they can wait without bound.
> No saved format changes. This is a development implementation note, not owner acceptance or a Gate result.

**Superseded text (W86R-13, AGENTS.md section 10).** When the leaf lands, Root marks the following as superseded at the
old text:
- ADR-065 lines 1311-1314 ("Paused transfers (F3, open)"), by the note above.
- ADR-065 line 1317 (F3) and lines 1322-1323 (N2) under "Open before W11b3 or any admission", closed against the leaf's
  evidence.
- R-035's "持续时间与可用性" clause, and F3 and N2 in its "未关闭项".
- `W44-DESIGN.md` lines 350-353 ("the election generation pauses other flights for the whole-journal confirmation (F3,
  open)"). Other flights no longer pause; only other Moon landers wait in their terminal pose while ELECTED is
  unacknowledged (section 4). ADR-066's W44 note (lines 510-518) needs no change. Its "exact proof" and "exact
  confirmation" are a confirmation of a snapshot that holds the unchanged outbox, which section 2.1 gives.
- The W85-DESIGN.md lines of section 6, and the `W11B3-DESIGN.md` leaf table (lines 424 and 427, section 5).

**Open questions, each with an answer.** Questions 1, 2, 8, 9 and 10 are now Root's decisions.
1. *N2 in its own leaf, or in the P1-F3 leaf?* In the P1-F3 leaf, as its first, self-contained commit on today's queue
   (section 5). Only "one release" waits for the scheduler (decision 8).
2. *Convert the whole-root escrow consumers too?* Not in P1-F3. Root decides on the consumer numbers of the
   eight-transfer run (decision 5).
3. *Should governed permits keep the no-failed-write hold?* Yes. It is W11b2's hold. Because queued continuations wait
   while a write has failed, no governed continuation meets the hold. Revisit it with W11b2 after the merge.
4. *Let the writer replace an unstarted successor with a newer capture?* No. It would save at most one of the three
   write latencies of section 8's bound, and it changes the reviewed writer. The measured per-step write counts show
   what it would save.
5. *Should a definite failure keep acknowledged progress running?* Yes. The disk definitely holds A, and it is what
   stops rockets freezing in mid-ascent with their passengers.
6. *Who changes the GameTest log expectations when the governed warning count falls?* The implementer reruns the
   unchanged checker and reports the new count; Root decides on the expectations. Nothing is loosened.
7. *Make `resourceMutationUnproved` follow the per-record rule?* Yes: "not actionable", keeping the rules of :402 and
   :404, so that the interlocks and the scheduler never disagree (W86R-12).
8. *How does the run budget interact with the leases?* Through `actionable(L)`. A rocket with a queued continuation is
   not actionable, so no lease holder acts beside it (decision 1).
9. *Do continuations run during a failed write?* No, except those that opt in. P1-F3 opts in none; W85's M2 will
   (decision 2).
10. *What does a second Moon lander do while the election write is held?* It waits in its terminal pose, so W44's
    behaviour and crash order are unchanged (decision 4).

<div style="page-break-after: always;"></div>

## Appendix A. Sites

### A.1 The current gating at `a8d81d24`

| Site | Waits for | Why |
|---|---|---|
| `RocketTransferService.tick` :268 | When the journal is dirty or holds records, `retryChecked` must report the whole root acknowledged-current: operational, not dirty, and a equal to the RAM sequence (`RocketTransferSavedData:379-381`). Otherwise the tick returns before recovery (:269), reconnects (:270), governed dispatch (:271) and the live loop (:272-297). | R1 of the W11b2 design review: dependent effects run only after the exact acknowledgement, and a clean journal implies that every record is on disk. The call also drives the writer and its 200-tick retry spacing (`RocketTransferSavedData:372-377`). |
| `tickLive` :494 | `flightAllowed` and `acknowledgedCurrent()`. | The first transfer in sorted order that calls `put` dirties the journal, and every later one in that tick skips. One phase advance per acknowledgement server-wide: this is F3. |
| `tickSource` :515-569 | Nothing of its own. It runs under `tickLive`'s gate, and its countdown, ascent and transit steps change only the entity. | So entity-only progress also stops during every write. |
| `spawnDestination` :577 | `flightAllowed(SPAWN_DESTINATION)` and `acknowledgedCurrent()`. Then `updatePhase` puts DESTINATION_SPAWNED and places the entity in the continuation (:623-630). | Placement needs DESTINATION_SPAWNED on disk. |
| `failBackToSource` :668-698 | `flightAllowed(SETTLE_SOURCE)` and the bay gate. It then changes the source (:676-684) and removes T (:685) before `afterAcknowledged` (:686), which can refuse. | The pad release and the `liveTransfers` cleanup need the removal on disk. The order is N2. |
| `cancelCountdown` :249-253 | After the fail-back, it answers SUCCESS only if `acknowledgedCurrent()` (:252). Right after a removal that is false, so it answers JOURNAL_PENDING. | Today's whole-root test. |
| `releaseLandedReservation` :466-475 | The bay gate; then `remove(T)` (:468) and `afterAcknowledged` (:469), whose result it ignores. It is the production disassembly path until P1-F8 (`RocketDisassemblyService:136, 164`), and afterwards W44's test hook (`W85-DESIGN.md:327`). | The release follows the acknowledgement. |
| `updatePhase` :713-727, `prepareLaunch` :187-202 (put or replace), recovery (`RocketTransferRecoveryService` :357, :411, :459), schema migration (`RocketDestinationCompletion` :133-135) | A put, replace or remove, then `afterAcknowledged`. Most callers ignore its result. | The effect follows the acknowledgement. |
| Governed launch discard callback, `RocketTransferService:141-148` | It releases the tentative pad only if `actionAuthorityAvailable()`, T is absent for the request and resources are allowed (:143). | Only a definitely unarmed launch may release the pad. |
| `afterAcknowledged`, `queueEffect` (`RocketTransferSavedData` :427-449) | A continuation is keyed by the sequence after the mutation: one per sequence, two in total. It runs at once only when the whole root is current (:446). | One active write and one successor (`RocketJournalWriter:59-70`). |
| `canQueueEffect` :451 | Not stopping, no failed write, fewer than two continuations. | W44's election (`MoonLandingCompletion:61`), F4 (`EscrowGovernedDestination:68`) and W85's M0. |
| `pollWriter` :453-476 | Continuations run only while the whole root is current, rechecked before each one (:460). A continuation whose own generation was never exactly committed is discarded once a later one is acknowledged (:462-463). A committed one runs only if L's RAM record is still the captured object (:467). | Exact-generation proof: a coalesced generation has no acknowledgement of its own. |
| `actionAuthorityAvailable` :384, `acknowledgedGuard` :417, `acknowledgedLease` :391 | The whole root current (and, for the first, no failed write). The lease fails on any mutation of any record. | One lease for both the flight permits and the escrow writer. |
| `resourceMutationUnproved` :398-407, used by `flightAllowed` (`RocketTransferEscrowRules:27`) and `resourceMutationAllowed` (`RocketTransferService:442`) | Already per record: L's `RecordVersion` equals A's, or L is absent from A. Unproved when a failed write left no A (:402). An absent L counts as proved before the session's first A (:404). | F2's fix. It compares versions only, with no sequence (section 2). |
| Recovery: `recoverNext` :80, `recover` :301, `recoverById` :140, :147, :154, `tryReconnect` :217, and through them `fences.reevaluateOne` (`RocketTransferFences:99`) and `finishSourceSettlementIfComplete` :443-469 | The whole root current. | Recovery rebuilds or discards entities from the record. |
| `RocketTransferFences.classify` :48, `RocketTransferBayDispatch.prune` :37 | Session sets are pruned only while the whole root is current. | Never prune on a RAM-only removal. |
| Governed flight: `EscrowGovernedDispatch.acquire` :89-100 | `actionAuthorityAvailable`, the exact RAM record, a guard naming T, and an authority that stays valid only while `acknowledgedLease` holds. Otherwise `classify` answers HOLD `GOVERNED_JOURNAL_UNACKNOWLEDGED` (:68), and `RocketTransferFences.classifyGoverned` (:72-85) fences the record with one WARN (:241-250). `EscrowGovernedSourceFlight.tickChecked` (:37-40) and `EscrowGovernedDestination.checked` (:48-50) hold without that authority; the F4 request also needs `canQueueEffect` (:68). | The governed permits bind a checked journal lease. Any other record's write invalidates every governed authority, so part of continuation 25's eight unexpected `GOVERNED_JOURNAL_UNACKNOWLEDGED` warnings (`records/W11B2.md:4984`) are cross-record. |
| Governed admission: `EscrowGovernedPreparation.begin` :25-29, `EscrowFlightCheckpoint.begin` :80, :91, :96-121, `EscrowFlightJournal.noTransfer` :11-22 | `retryChecked`, a no-T guard, and the whole-root lease, rechecked in the barrier callback (:103) and by the ledger write (:112). | F1 needs checked absence of T, plus a scan of RAM for the root-wide serial (:17-19). |
| Pre-F2 cancellation: `EscrowPreF2Cancellation` :56-57, :72, :86 | `retryChecked`, then the whole-root `noTransfer` lease, rechecked in the barrier callback and by the ledger write. | F1 orphan recovery in production. |
| Escrow ledger: `EscrowCoordinator` :153, :300; `EscrowRuntime` :157; `EscrowSettlementRecovery` :52-138 (O3J at :96-102); `RocketEscrowAudit` :81-84; `EscrowSettlementObserver` :28, :51 | The whole root current, and the whole-root lease handed to the ledger writer. | Settlement and floor proofs compare a clean acknowledged journal. |
| Mutations without a continuation of their own: settlement's O3J (`EscrowSettlementRecovery:99-101`), the Moon-landing delivery (`FirstEventSources:43-46`), F2's put (`EscrowGovernedPreparation:39-40`) | O3J removes T and only calls `flushChecked`; O4 then waits for `acknowledgedCurrent()` (:102). The delivery changes the outbox and only calls `flushChecked`. F2 puts and then queues. | Their dependents are whole-root (O4) or not on the rocket side (DELIVERED), and F2's order is frozen. |
| W44: `MoonLandingCompletion` :50-53, :61, :84 | :52 queues the publication without a mutation when RAM holds this transfer's event. LANDED waits for the generation that holds ELECTED (:84). | The world-first event is durable before its effects. |
| Release-test hooks: `RocketFlightLifecycleController` :60, `FirstEventCaseExports` :31, `FirstEventCutSeams` :214, the escrow probes | The whole root current. | Cut fixtures. |
| RAM readers: `LandingPadService:87-91`, `SafetyReturnTransactions:22-23`, `StationManager:273-278`, `RocketTransferSessionDiagnostics`, `BetaOperationalReport` | Operational and RAM records only. | No acknowledgement involved. |

### A.2 The sites after P1-F3

| Site | After P1-F3 |
|---|---|
| `tick` :268 | Still calls `retryChecked` every tick to drive writes and their spacing, but no longer returns on its result. |
| `tickLive` :494, `spawnDestination` :577 | `flightAllowed` and `journal.actionable(L)`, with a version lease on that exact record. Several transfers can step in one tick; each reserves and queues its own continuation, and the writer coalesces them. |
| `tickSource` | Unchanged, under `tickLive`'s gate. |
| `failBackToSource` | Section 5. |
| `updatePhase`, `migrateCommitted` (`RocketDestinationCompletion:133`), the recovery removals (`RocketTransferRecoveryService:410, 458`), `releaseLandedReservation` :468 | Check, then `reserve(L)` before the first change, then the mutation and `afterAcknowledged` with the token. A refusal changes nothing. |
| `prepareLaunch`, legacy branch, put and replace (:156-202) | Reserves after the governed branch and before the debit. A refusal releases the selected pad and answers TRANSFER_JOURNAL_BLOCKED, as the branch's other refusals do. The governed branch takes no reservation, because F1 needs a clean root. |
| `cancelCountdown` :252 | The code comes from `actionable(L)`: JOURNAL_PENDING while the fail-back's continuation is queued, which is always the case right after a removal, and SUCCESS otherwise. |
| The governed launch discard callback :141-148 | The per-record pad guard of section 3. |
| Recovery `step` | Skips records that are not actionable, before `recoveryEntityChunksLoaded` can add a ticket. `recoverNext` drops its whole-root return (:80). |
| `recover` :301 | `reserve(L)` replaces the whole-root test, before any entity change. WAITING_FOR_PASSENGERS closes the token. KEEP_DESTINATION queues with it, and takes the immediate path when L's slot is acknowledged and nothing was put. |
| `recoverById` :140, :147, :154 | :140 uses the absence lease of the completed record's rocket; :147 uses `actionable(L)`; :154 keeps calling `retryChecked` to drive the writer, but gates on `actionable(L)`. |
| `tryReconnect` :217 | Drives the writer. It waits (returns false) only while the record that holds the player's assignment is not actionable, and no longer waits for other records. `finishSourceSettlementIfComplete` reserves before its removal. |
| `fences.reevaluateOne` | Considers only actionable records. |
| Fences :48, bays :37 | Prune a transfer ID once it is absent from RAM and the change that removed it is acknowledged with its rocket actionable. The slot entry keeps the removed ID until then (section 2.1). |
| `EscrowGovernedDispatch.acquire` and `Authority.valid()` | The version lease of the exact record, which requires `actionable(L)`, replaces `actionAuthorityAvailable`, `acknowledgedGuard` and `acknowledgedLease`. `!stopping && !failedWrite` stays (W11b2's hold). `classify` then holds only a record that is not actionable. |
| F4 request (`EscrowGovernedDestination:68`), W44 election (`MoonLandingCompletion:61`), W85's M0 | `canQueueEffect(L)`, which returns the reservation. |
| W44 publication (`MoonLandingCompletion:52`, :84) | :52 reserves, and :84 uses the election's token. Both depend on L's record and the outbox slot. |
| W44, a second lander (`MoonLandingCompletion:55-59`) | WAIT while the RAM slot holds another transfer's event and the durable view is not KNOWN with a non-empty outbox (section 4). |
| `resourceMutationUnproved(L)` | "L is not actionable", keeping the rules of :402 and :404 (section 2.1). |
| Whole-root consumers: governed F1 admission, pre-F2 cancellation, settlement, floors, the escrow audit and observer, the release-test hooks and the escrow probes | **Unchanged in kind**, but on `clean()`. `retryChecked` returns it, and `acknowledgedGuard`, `acknowledgedLease` and `actionAuthorityAvailable` are granted only on a clean root. O4's test (`EscrowSettlementRecovery:102`) and `requireJournal` (:132) use `clean()`, and so do the three hooks, instead of `acknowledgedCurrent()`. F1 admission, pre-F2 cancellation and settlement also need an interval with no journal mutation that covers their barrier and ledger write (section 9, open question 2). |
| `acknowledgedCurrent()` | Meaning unchanged; the GameTest fixtures keep using it. |
| RAM readers | Unchanged. |

## Appendix B. Continuation bodies and discarded callbacks

| Continuation (site) | What it tests today | After P1-F3 | Captured world state and its recheck (W86R-8) | Discarded callback |
|---|---|---|---|---|
| Legacy launch, `RocketTransferService:192-202` | `rocket.isAlive()`; `flightAllowed(TICK_SOURCE)`. | `flightAllowed` is per record (section 2.1). | `rocket`: also added to its world, with its chunk's entities loaded. Otherwise return false and leave PREPARED to recovery. | `completion.discarded()`, unchanged. |
| Governed F2, `EscrowGovernedPreparation:40-51` | The rocket alive, added and operational; the boundary; `authority()`, which today needs `actionAuthorityAvailable` and the whole-root guard and lease (`EscrowGovernedDispatch:91-99`). | `authority()` binds the version lease and keeps `!stopping && !failedWrite`. The body never runs during a failed write, so the hold never discards it. | Already rechecks the rocket and the boundary; unchanged. | `RocketTransferService:141-148`, with the per-record pad guard of section 3 instead of `actionAuthorityAvailable()`. |
| Governed F4, `EscrowGovernedDestination:72-76` | `tick` reacquires the authority, whole-root today. | Per record, as for F2. | `source`: alive and added. Otherwise return false; the dispatcher reads the source again on its next visit (`RocketGovernedSourceDispatcher:26-27`). | None. |
| `updatePhase`, `RocketTransferService:717-726` | `flightAllowed(action)`. | Per record. | By effect. PASSENGERS_TRANSFERRED moves passengers onto the captured `destination` (:638, :643-654), which must be alive, added, loaded and still the record's destination; otherwise drop the ID and leave the record to recovery. SOURCE_REMOVED finds the source afresh (:663). COMMITTED captures no entity. | None. |
| Spawn continuation, `RocketTransferService:623-630` | Through `updatePhase`. | Per record. | `destination` (not yet added) and `source`. Repeat the readiness and landing checks of :587-599 before `addFreshEntity`. Before the fail-back at :626, the source must be alive and added. On a failed recheck, drop the ID and leave the record to recovery, which keeps the lone source (KEEP_SOURCE). | None. |
| `failBackToSource`, :686-697 | The bay gate. | Unchanged; it depends on L's absence. | `safe` is used only in the log line. | `completion.discarded()`. |
| Recovery KEEP_DESTINATION, `RocketTransferRecoveryService:357-386` | The bay gate; `fences.actionAllowed` on fresh loaded matches; `destination.isAlive()` for an existing destination. | Unchanged predicates; queued with the reservation taken in `recover`. | A created `destination`: its chunk's entities are loaded, and no destination appeared since (`currentDestinations` is empty). Otherwise return and leave the record to the next recovery pass. | None. |
| Recovery source settlement, :411-416; reconnect settlement, :459-468 | The bay gate. | Unchanged. | None beyond IDs and log fields. | None. |
| Schema migration, `RocketDestinationCompletion:135-140` | `flightAllowed`. | Per record. | None. | None. |
| `releaseLandedReservation`, `RocketTransferService:469-473` | The bay gate. | Unchanged; queued with a reservation. | `rocket` only for the audit line. | None. |
| W44 publication, `RocketDestinationCompletion:98-114`, queued at `MoonLandingCompletion:52, 84` | The exact RAM record, `flightAllowed`, and the unique loaded DESCENT match. | Unchanged; it depends on L's record and the outbox slot. | Already rechecks the destination. The participants belong to the durable event. | None. |
| W85's M2 and M3 (P1-F8, not written) | `W85-DESIGN.md:212-218, 253-260`. | M2 opts in to run during a failed write; M3 reserves with `reserve(L)`. | W85 already rechecks the landed rocket against its pending snapshot at M2 and M3. | W85's removal and write-back callbacks, unchanged. |

## Appendix C. Existing tests whose expectations change

Every existing test not listed here stays unchanged. The tests that call the package-private `queueEffect(L, …)` keep
calling it, because it stays as a one-call reserve and queue (section 3).

| Test | Expectation today | After P1-F3 | Reason |
|---|---|---|---|
| `RocketJournalWriterTest.callbackAndTargetBoundsDoNotReplaceAnExistingContinuation`, line 183 | `assertTrue(queueEffect(L, …))` after `remove`: the removal gives a new generation key, and two keys fit. | `assertFalse`: L still has the continuation queued at line 181. | One continuation or reservation per rocket (section 3). |
| The same test, line 184 | `assertFalse`: the two-generation limit. | `assertFalse`, unchanged, now because of the per-rocket rule. | As above. |
| The same test, line 185 | `assertFalse(canQueueEffect())`: two generations are queued. | `assertTrue(canQueueEffect(L).isEmpty())`. The no-argument method is removed, and the per-rocket refusal replaces it. A0 adds the total-limit case with a limit of 2. | `canQueueEffect()` has no meaning without a rocket (section 3). |
| `RocketJournalContinuationTest.changedRecordAndUncapturedGenerationDropTheirRequests` (lines 38-55) | Both branches drop the request once. The branch without a capture drops it because its generation was never captured. | The assertions are unchanged. Both branches now drop the request because L's record changed. | Coalesced generations are no longer discarded (section 2.1). |
| W85's G8 and G12 (planned) | A write-back and no block; G8 reaches it through a discarded removal continuation. | Results unchanged. G8 now reaches M3 through M2's late branch. G12 already does, and still does. | Section 6. |
| W85's G11 (planned) | M2 does not restore; T is written back. | M2 restores within the limit, and no write-back happens. | Section 6. |
| W85's G13 (planned) | Live flights do not advance while the write-back fails. | Acknowledged flights keep advancing; their next journal step waits. | Section 6. |
| The GameTest log, `GOVERNED_JOURNAL_UNACKNOWLEDGED` | W11b2's GameTest log has eight unexpected warnings, and the checker fails on them (`records/W11B2.md:4984` at `a8d81d24`). `scripts/gametest_expected_log.json` has no entry for them. | Fewer, because another record's write no longer holds a governed record. The implementer reports the new count and the unchanged checker's result; Root decides on any expectation. | Open question 6. |
| `EscrowGovernedPreparationGameTests:92`, `EscrowGovernedSourceFlightGameTests:160`, `RocketSettlementIdleGameTests:134` | Each asserts the whole root after a single-rocket step. | Unchanged. They hold while nothing else in their batch writes the world journal. The leaf rechecks their batches; a failure is recorded for Root, never loosened (AGENTS.md section 5). | Whole-root assertions in multi-test batches. |
