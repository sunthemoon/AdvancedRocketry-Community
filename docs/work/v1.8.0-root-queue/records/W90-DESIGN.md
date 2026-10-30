# W90 design: governed flight readiness and the discovery margin (revision 2)

Design note for Root, revision 2, 2026-10-17, for [TASK-W90R-READINESS-REVISION-2](../TASK-W90R-READINESS-REVISION-2.md).
Revision 1 (for [TASK-W90-READINESS-DESIGN](../TASK-W90-READINESS-DESIGN.md), merged at `af4a7351`) had an independent
review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W90-review/REVIEW.md` (Codex, APPROVE-WITH-CHANGES, SHA-256
`498009e8…0328d`), and Root decided R21, `D:/GitHub/ARCE-Task-Evidence/root-queue/W90/ROOT-DECISION-R21.md` (SHA-256
`1322ab42…91ee`). This note is a proposal, not an implementation, ADR change or risk acceptance.

Product code was read only through Git at `379525ea`. That commit is not on this branch, so its paths are code spans and
its line numbers refer to it. Continuation 53's pending pad reservations were read at `453094c3` on
`codex/v1.8.0-w11b2-c53`. The vanilla facts are revision 1's `javap` reads of the mapped Forge 47.4.10 jar. No `javap`
ran for revision 2, because continuation 53 held the heavy-JVM lock; the Forge sources jar's `DistanceManager` patch was
read with `unzip`.

**Changes from revision 1.**

1. Option (A) and its claim that the exact-source return cancels a contradictory copy are withdrawn. R21 item 4 is
   recorded: (A) is rejected, (B) is chosen in principle, and (C) applies until (B) exists (sections 7 and 8; review
   R1).
2. The admission finding is restated as "an unready destination with no other ticket providing readiness", not "no
   player nearby". `native-03` armed all eight launches because of its START tickets and contained margins (review R2,
   R21 item 7).
3. One checked column set serves readiness, governed admission, refresh and release. New governed admission requires
   the origin column, so the 17-ticket exception is gone. A recorded invalid end is a new executor hold, and release
   always includes the origin (section 3; review R3, R21 item 2).
4. The margin tickets are an explicit change of the flight-ticket budget, with corrected bounds (section 2; review R4,
   R21 item 1).
5. Expiry is "the first purge with age greater than 400 ticks", and zero owned tickets is not chunk eviction (section
   4; review R5, R21 item 7).
6. New section 6: the Q2 budget for governed launches only, with its refusal keys and text (R21 item 3).
7. New section 7: the destination warm-up, Q1 option (B) (R21 item 4).
8. Wait logs and the `/arce rocket inspect` field follow R21 item 6 (section 5).
9. New sections on the review answers, the saved format, tests and open questions (sections 8 to 12).

**Status.** Root accepted option (a) as a budget change (R21 item 1), and its implementation together with items 2, 3,
5 and 6 follows the W11b2 merge as one W90 leaf. Section 7 is the design of Q1 option (B) for its own independent
review. It recommends that a loading destination refuses the launch with a "preparing the destination" message, and
the player launches again with a fresh request ID. Until (B) is implemented, a governed launch to an unready
destination stays refused, and production admission stays disabled.

## 1. What the margin needs, and why

**Vanilla 1.20.1** (revision 1's reads, unchanged).

- **Levels.** `DistanceManager.addRegionTicket` creates a ticket at level 33 − distance, and both the chunk ticket
  tracker and `TickingTracker` add one level per Chebyshev ring. Our distance 2 gives 31 on the ticket's column, 32 on
  ring 1 and 33 on ring 2. `ChunkLevel.fullStatus` maps 31 or less to ENTITY_TICKING, 32 to BLOCK_TICKING, 33 to FULL
  and above 33 to INACCESSIBLE. `Visibility.fromFullChunkStatus` gives TICKING for ENTITY_TICKING, TRACKED
  (accessible, not ticking) for FULL and BLOCK_TICKING, and HIDDEN otherwise.
- **Level 32 or 33.** The chunk's entities are visible once loaded, but they can still be loading.
  - Promotion calls `PersistentEntitySectionManager.updateChunkStatus`, which queues an asynchronous
    `EntityPersistentStorage.loadEntities` (PENDING).
  - `processPendingLoads`, in `entityManager.tick()` every Level tick and in empty Levels too, adds all of the chunk's
    entities at once and marks it LOADED. Until then `areEntitiesLoaded` is false and the entities are in no query.
  - Once loaded, the entities are in the UUID index and in the AABB queries, which skip only inaccessible sections.
  - They are not in the tick list: `isPositionEntityTicking` needs TICKING and `TickingTracker` level 31 or less.
- **Demotion is immediate.** `updateChunkStatus(HIDDEN)` stops tracking every entity at once, but the chunk stays
  LOADED until the next `processUnloads`. In that window `areEntitiesLoaded` is true while the entities are invisible.
- **Empty Levels.** `ServerLevel.tick` skips the entity tick list and block entities after 300 ticks with no players
  and no forced chunks. Ticket expiry and entity loading still run every tick.
- **Verification boundary.** The review re-checked the region-ticket level, ticket equality including force-ticks, the
  strict expiry predicate, pending-load completion, the Forge empty-Level condition, player and force gating of chunk
  ticks, and that Forge persists only forced tickets. It could not re-disassemble the `ChunkLevel`/`Visibility`
  thresholds, accessible-section traversal, the demotion window, all `ServerLevel.tick` branches or the purge and read
  halo while the lock was held. Revision 2 relies on one more fact from revision 1's read: re-adding an equal ticket
  resets its age (section 7.3). The quiet-machine re-read stays open (section 12, question 8).

**Callers of `recoveryEndsAlreadyLoaded`** (`RocketTransferEntities:194-220`):

| Caller | What it relies on |
|---|---|
| `RocketGovernedSourceDispatcher.tick` (`:22-33`, `:158-165`) | The predicate gates every F3 to F6 effect in `dispatchLoaded`. |
| `RocketGovernedLaunchBoundary.allows` (`:47`): F1 preflight and ARMED capture, F2, F3, returns, F4, F5, arrivals | A census in both Levels: the two fixed UUIDs, plus an AABB scan over each margin for any rocket with logical ID L or either UUID, without an owner or snapshot filter (`:48-74`). |
| `RocketTransferFences.loadedReason`, `RocketTransferRecoveryService.recoverById` | Legacy fenced records only (governed ones answer `GOVERNED_ACTION_REQUIRED`). The same census, before elections that discard copies. |
| `RocketEntity.tick` → `RocketEscrowAudit.observe` | Change detectors and terminal invalidation. No progress waits on a tick. |

No caller needs margin entities to tick. Each needs every margin column loaded and visible at its census.
Entity-ticking is stricter than needed, but it implies loaded and visible, and it is the exact public test; loaded
alone is wrong during demotion. **The predicate stays as it is** (R21 item 1). In `native-01` all eight records had
2 × 2 margins with all 64 columns loaded and only the origin column of each destination entity-ticking.

## 2. The flight-ticket budget (option (a), R21 item 1)

Design 2's "existing bounded flight tickets" and continuation 32's "keep the existing ticket bounds" meant the two origin
tickets per transfer, and `RocketTransferEntities:385` says so ("no per-footprint ticket expansion"). Revision 1 called
(a) a reading of that rule; that was wrong (review R4). R21 item 1 **changes the budget** for governed records:

- one `arce_rocket_flight` region ticket per column of each end's checked column set (section 3), with the existing
  type, distance 2, key T and 400-tick lifespan;
- at most 16 per end, 32 per transfer and 2,048 at 64 transfers; the dispatcher visits at most 8 records per tick
  (`VISITS_PER_TICK`), so at most 256 refresh calls per tick;
- only the checked ACTIVE governed dispatch adds the margin tickets, never while the record is held. The F2 callback's
  two origin tickets (`RocketTransferService:136-137`) stay as a named exception;
- legacy, ungoverned flights keep their two origin tickets, and the readiness predicate does not change.

The table gives cumulative chunk influence as entity-ticking / block-ticking / accessible / read counts. "Read" is ring
3, which `ChunkMap.prepareAccessibleChunk` reads at a lower status. **The counts assume disjoint margins and
already-generated terrain**; overlap lowers them, and they are not measured memory.

| | Tickets per end | 2 × 2 end | Worst end (1 × 16) | Worst transfer | 64 transfers, worst |
|---|---|---|---|---|---|
| Before W90 | 1 | 1 / 9 / 25 / 49 | the same, never ready | 2 tickets, 2 / 18 / 50 / 98 | 128 tickets, 128 / 1,152 / 3,200 / 6,272 |
| (a), R21 item 1 | one per column, ≤ 16 | 4 / 16 / 36 / 64 | 16 / 54 / 100 / 154 | 32 tickets, 32 / 108 / 200 / 308 | 2,048 tickets, 2,048 / 6,912 / 12,800 / 19,712 |

- **Effect on the margin.** With no player within 128 blocks these chunks get no random ticks or spawning
  (`ServerChunkCache.tickChunks` needs a nearby player or a force-ticks ticket, and ours is not one). Entities and block
  entities tick unless the Level is empty, and scheduled block and fluid ticks run in block-ticking chunks. They keep
  running until the tickets expire, at the first purge with age greater than 400 ticks after the last refresh (about
  20 s only at 20 TPS).
- **Benchmark.** The eight-flight benchmark's 2 × 2 contained margins would use 64 tickets and 64 / 256 / 576 / 1,024
  chunks, against 16 tickets and 16 / 144 / 400 / 784 today. They do not measure border-crossing or 16-column margins,
  which admission needs (R21 item 5).
- **Rejected alternatives.** (b), one ticket per end at distance 2 + r, never costs less than (a) and needs r = 15 for a
  1 × 16 margin. (c), accepting loaded and visible, weakens the tick-based audit and covers only columns within two rings
  of the origin.

## 3. One checked column set (R21 item 2)

**Definition.** One server-free helper, here called `RocketDiscoveryColumns`, is the only source of margin columns. Its
core takes the origin x and z and the snapshot's bounds, so a pad candidate can be checked before its height is known;
`of(snapshot)` delegates to it.

- **Region:** origin plus bounds in x and z, with exact arithmetic.
- **Rectangle:** chunk x from `(min.x − 1) >> 4` to `(max.x + 2) >> 4`, and z likewise, with `Math.subtractExact` and
  `addExact` on `int`. These are exactly the columns `endAlreadyLoaded` reads today (`RocketTransferEntities:202-203`).
- **Bound:** width and depth at least 1 and width × depth at most `MAX_LANDING_CHUNKS` (16), computed in `long`.
- **Origin column:** `(origin.x >> 4, origin.z >> 4)`. `containsOrigin` is true when it lies in the rectangle.
- **Result:** VALID (the rectangle and `containsOrigin`) or INVALID with a reason, `ARITHMETIC` (any overflow) or
  `OVER_BUDGET`. Refresh and release iterate the rectangle in one fixed order.

**Uses.**

| Use | Rule |
|---|---|
| Readiness: `recoveryEndsAlreadyLoaded`, shared with legacy recovery | VALID, and every rectangle column loaded and entity-ticking. This re-expresses today's predicate with the same answer for every input: where today's `int` arithmetic would wrap, both answer false. Legacy recovery does not change. |
| New governed admission: every relocated pad candidate and F1's source (section 6) | VALID **and** `containsOrigin`; otherwise refused before terrain access. |
| Margin refresh (ACTIVE governed dispatch) | The rectangle of a VALID end that contains its origin column. |
| A recorded governed end that is INVALID or lacks its origin column | Executor hold `DISCOVERY_MARGIN` (below); no margin ticket. |
| Release (F8 and every other removal) | The origin column always, plus the rectangle when the end is VALID and contains its origin. |

So an end holds at most 16 tickets, the origin among them, and a release makes at most 16 removal calls per end. The
16-chunk limit is applied to the margin, not raised (ADR-065: "no operation raises the existing 16 landing-chunk …
limits").

**A recorded governed end that fails the helper.**

- **Which records.** Production admission is off, so only GameTest and release-test worlds, decoded fixtures and
  records written before this check can hold one. A record admitted after the change cannot, because F1 refuses the same
  set.
- **Detection.** In `RocketGovernedSourceDispatcher.tick`, the `ready` supplier first evaluates the helper for both
  ends. If either fails, it calls `executorHolds.hold(server, record, DISCOVERY_MARGIN)` and answers not ready.
  `afterClassification` (`:158-165`) then skips `execute`, sees `held` and refreshes nothing. The existing order
  (ready, execute, held, refresh) is kept, and the hold is registered before the refresh test.
- **Reason and log.** A new `EscrowGovernedExecutorHolds.Reason.DISCOVERY_MARGIN`. The existing WARN
  `ARCE_GOVERNED_EXECUTOR_HOLD transfer=<T> reason=DISCOVERY_MARGIN` is written once per (flight, reason) per process
  start, because the `reported` set is runtime-only: at most 64 lines per start.
- **Effect.** The record keeps ARMED, its phase, the source lock, its pad reservation, any named-pad claim and one of
  the 64 transfer slots. Nothing ascends, publishes, debits, returns or discards. Origin tickets from this process's F2
  callback expire at the first purge with age greater than 400 ticks, and nothing refreshes them.
- **Duration and recovery,** as R17 item 6's holds: the hold lasts for the process, and a restart raises it again at
  the first visit with one more WARN. No online path changes it, since `/arce rocket recover` does not act on governed
  records. Repair is R-035's offline whole-world procedure. The record is never deleted, rewritten or bypassed, and no
  saved-format check is relaxed. Scope: one transfer.

## 4. What does not change, release and expiry

Option (a) keeps the F0 to F8 acknowledgement order, the store, force and readback proofs, the checked journal leases,
the executor holds, the replay exclusion and every saved format. Ordinary reconciliation, pruning, held repair, the
HOLD and CLEANUP routes and the legacy fences add no ticket, and unloaded ends wait there without tickets.

- **F8.** `releaseFlightTickets` (`RocketTransferEntities:411-421`, called at `RocketGovernedCleanupDispatcher:62`)
  removes, in each end's own Level, the release set of section 3, at distance 2 with key T.
  - Ticket equality is (type, level, key, force-ticks), so another transfer's ticket on a shared column is untouched.
  - Removing an absent ticket does nothing, so a repeated release is safe.
  - A missing Level is skipped, since it can hold no tickets, and the other end is still released.
- **Restart.** `arce_rocket_flight` is a plain `TicketType`, neither FORCED nor managed by `ForgeChunkManager`, so it is
  never saved. After a restart only ACTIVE visits add tickets, from the same helper and the same record.
- **Expiry.** F8 explicitly removes the exact tickets. Without a further refresh they expire at the first
  `purgeStaleTickets` whose age is greater than 400 DistanceManager ticks (`now − createdTick > 400`, so the first
  expired age is 401), about 20 s only at 20 TPS. Between a hold, a non-ACTIVE route or a delayed cleanup and that
  purge, the existing tickets stay.
- **Zero owned tickets is not eviction.** Other tickets (players, spawn, other transfers, warm-ups) and pending unload
  or save work can keep the chunks loaded. Tests assert ticket absence, not chunk unloading.

## 5. After F2: wait logs and the inspect field (Q5, R21 item 6)

- **No player message** for the post-F2 wait. The 60-tick countdown normally hides it.
- **Diagnostic state.** For each live governed record (at most 64, in RAM, cleared at F8 and on restart): the tick at
  which it stopped being ready.
- **Log.** After 400 ticks continuously not ready, one INFO line, `ARCE_GOVERNED_ENDS_WAITING transfer= logical=
  phase= cause= source=k/n destination=k/n`. When the wait ends, one `ARCE_GOVERNED_ENDS_READY transfer= waited=`. The
  cause is computed once, when the line is written, from read-only checks:
  - `LOADING`: the readiness predicate is false;
  - `CENSUS`: the predicate holds but the launch boundary refuses.

  Executor holds keep their own WARN, and journal or ledger storage waits keep their existing logs, so the INFO pair
  never reports them. INFO, not WARN, because continuation 32 requires a never-loading end to raise no fence WARN.
- **Inspect.** `/arce rocket inspect <transfer>` gains
  `margin=source:k/n,destination:k/n wait=<NONE|LOADING|CENSUS|HOLD:reason> tickets=<n>`.
  - k/n reads only the recorded rectangle, with `areEntitiesLoaded` and `isPositionEntityTicking`. `invalid` replaces
    k/n for an INVALID end.
  - `tickets` is the size of the set the dispatcher refreshed at its last visit (0 while held or not ACTIVE). It comes
    from the dispatcher's own bounded state, not from the test-only reflective scan
    (`RocketTransferFenceGameTests:90-115`).
  - The field adds no ticket and loads nothing.

## 6. Admission budget for governed launches (Q2, R21 item 3)

- **Scope.** `prepareLaunch` computes `governed = rocket.escrowSection().ledger() != null` before pad selection. This is
  the test that already chooses the governed branch (`RocketTransferService:130`). Legacy launches keep today's
  region-column selection and get no source check, so no released behaviour changes. The callers are player intents,
  the named-pad intent, and the admin and release-test launches; all of them reach `prepareLaunch`.
- **F1's source,** first, before pad selection (so before any terrain access, named-pad claim or F1 source store): the
  helper on `rocket.snapshot()` must be VALID and contain the origin column. Otherwise the request answers
  `RocketFlightRequestResult.failure(LAUNCH_CHUNK_BUDGET, plan.requiredFuel())`, a new request code.
  - The source refusal cannot use the detail key: `RocketFlightRequestResult` (`:9-10`) accepts only
    `message.advancedrocketrycommunity.pad.reason.*`, which `RocketFlightService.report` prints as "Landing pad refused:
    %s".
- **Surface pads** (`RocketLandingPadSelector.select`). For each candidate, after `candidate()` (arithmetic and the
  world border) and before `loadChunks` and `originAtSurface`, the helper runs on the candidate's origin x and z. It
  replaces the region test at `:73` for governed launches.
  - The eight candidates are 64 blocks apart, a multiple of 16, so they share one chunk alignment and one count.
  - The first budget failure therefore ends selection, before any terrain load, with the detail
    `message.advancedrocketrycommunity.pad.reason.chunk_budget`.
- **Station default pad** (`selectStation`). After the region and station-region checks and before `loadChunks`
  (`:157-161`), the helper on the relocated snapshot replaces the region count. The failure detail is `chunk_budget`;
  today it is audit text only.
- **Named station pads** (`NamedLandingPadStaging.unavailable`, `:67-69`). The helper replaces the region count in the
  same place: after the block budget, and before the bounds check, `getChunkNow` and block reads. The key is already
  `chunk_budget`.
  - Pad resolution, clearance, support, identity, claim, station-region and occupancy rules stay, and the count change
    loads no chunk.
  - `recheck`, used after F2 by `landingAvailable`, keeps today's region test. A recorded end that fails the helper is
    already held (section 3).
- **How results carry the keys.** A destination failure travels through the existing `selected.refusalKey()` path
  (`RocketTransferService:122-127`): `LANDING_PAD_UNAVAILABLE` with detail `chunk_budget`. The player sees the
  action-bar text and the system message "Landing pad refused: Landing footprint exceeds the 16-chunk budget." / "着陆平台
  拒绝着陆：着陆覆盖范围超过 16 区块预算。" (existing). A source failure uses the code alone, shown by `notify`.
- **New text** (v1.8 language provider):

  | Key | English | Chinese |
  |---|---|---|
  | `flight.advancedrocketrycommunity.request.launch_chunk_budget` | The rocket's launch area exceeds the 16-chunk budget | 火箭发射区域超过 16 区块预算 |

- **Language mechanics.** New `RocketFlightRequestCode` constants are appended. Codes are never saved or sent; the
  server sends translated components.
  - `V180LanguageProvider` adds the new keys, as it does for `journal_pending` (`:32`).
  - The v0.6 `FlightLanguageProvider` is no longer run by `BootstrapDataGenerators`. Its exhaustive switches gain cases
    only so that it compiles, and no file under `src/generated/v0.6` changes.

## 7. Destination warm-up (Q1 option (B))

### 7.1 Purpose and what it keeps

> **W207 implementation note (2026-10-30).** The synchronous surface/station terrain loading described below is historical W90 behavior, not W207's governed request behavior. W207 uses loaded-only selection and, when needed, bounded pre-selection TERRAIN preparation; non-governed loading and named-pad loaded-only selection remain unchanged. The original proposal below is retained as history. See [W207](W207.md) and the W207 amendment in [R-035](../../../11-RISK-REGISTER.md); neither note grants admission or Gate approval.

- **The problem.** F1's preflight evaluates the whole launch boundary synchronously at request time
  (`EscrowFlightCheckpoint:49-57`, `:106-108`). F1 repeats it at ARMED capture and in the arm callback (`:57`, `:63`),
  and F2 repeats it before publication and the countdown permit (`EscrowGovernedPreparation:42-49`).
  - Surface and station selection load only the region's terrain (`RocketLandingPadSelector:381-386`), and named-pad
    selection loads nothing.
  - So a governed launch to an **unready destination with no other ticket providing readiness** is refused with
    `TRANSFER_JOURNAL_BLOCKED`.
  - Continuation 52's `native-03` armed all eight launches only because its fixture put START tickets on every
    destination slot and kept each margin inside its slot's chunk.
- **What the warm-up does.** Before F1, it tickets the server-selected destination's checked column set, so that the
  unchanged census can pass when the player launches again.
- **What it keeps:** every F1 and F2 check, the full census in both Levels, the store, force and readback proofs, the
  leases, the ARMED serials, the replay exclusion and the acknowledgement order. It changes only whether the
  destination margin is loaded and entity-ticking when those checks run.
- **Ticket rule.** The warm-up adds tickets only before F1. After F2, only item 1's margin tickets and the F2 callback's
  origin tickets are added. It is an explicit exception to design 2's "only ACTIVE adds tickets" (R21 item 4) and is not
  part of option (a).

### 7.2 When it starts and what it allocates

> **W207 implementation note (2026-10-30).** The selected rectangle, distance-2 tickets and decision table below describe the selected stage. A governed request may first allocate TERRAIN capacity for the server-owned candidate footprint, using plain distance-0 (level-33) tickets with no selected-region reservation, named-pad claim or F1 acceptance. At most one pending selection advances per server tick. Selection success removes those tickets and starts the distance-2 (level-31) selected-margin WARMING stage, retaining the original allocation start, captured lifetime and initiating request. It does not launch; a later fresh launch request still repeats full admission and selection.

The decision runs in `RocketTransferService.prepareLaunch`, governed branch, immediately before
`EscrowGovernedPreparation.begin`, after all the existing validation:

1. `RocketFlightService.request`:
   - the rate limiter (8 intents per 20 ticks per player);
   - access: the rocket is loaded, alive and operational, its chunk is loaded, and it is within interaction distance
     (`:501-514`);
   - authorization: owner, creative or operator;
   - the named-pad menu check;
   - the replay ledger's `begin(requestId)` (`:161-197`).
2. `launch`: the rocket is FUELED, the targets are valid, the player has station VISIT access, and the plan passes
   (route, discovery and fuel).
3. `prepareLaunch`:
   - the journal is operational and resource mutation is allowed;
   - the request is not replayed and has no non-replaceable predecessor;
   - the transfer limit of 64;
   - the destination Level exists;
   - section 6's source budget;
   - pad selection, with the reservations of section 7.4, and its destination budget.
4. **New, read-only:** `begin`'s own side-effect-free preconditions:
   - the rocket is operational and idle;
   - L's retry schedule is ready;
   - governed authority is available for L;
   - L's journal lease is available, checked without `retryChecked`, which can start a write;
   - the barrier is available;
   - L's guard exists with no transfer;
   - L is not locked, and fewer than 64 F1 launches are pending.

   These checks move into one method that `begin` also calls, so the two cannot drift. If they fail, no warm-up is
   allocated or bound, an existing W is left to its deadline, and the request continues exactly as today.

**Allocation.**

- **Ticket type.** One new `TicketType<UUID>` named `arce_rocket_warmup` (natural order, lifespan
  `DESTINATION_WARMUP_TICKS` = 600). Its name must not contain `arce_rocket_flight`, which the test-only `flightTickets`
  helper matches as a substring.
- **Tickets.** At distance 2, with key L, one on each column of the selected destination's checked rectangle (VALID and
  containing its origin by section 6, so at most 16), in the destination Level.
- **RAM record:**
  - L;
  - the requesting player: carried from `RocketFlightService.request`, and none for admin and release-test launches;
  - the source Level and source entity UUID;
  - the destination `ResourceKey<Level>`, the exact destination region and the rectangle;
  - the request ID that started it, the start tick and the state.

**Decision** (W is L's warm-up; "ready" is the readiness predicate on the selected destination snapshot, the same
function the census uses; "slot" means fewer than 8 warm-ups):

| Case | Action | Player sees |
|---|---|---|
| No W; ready; slot free | Start W, then `begin` at once; bind W to T on acceptance, release it if `begin` refuses | `JOURNAL_PENDING`, then the result |
| No W; ready; no slot | `begin` without a warm-up, as today | as today |
| No W; not ready; slot free | Start W; release any named-pad claim | `DESTINATION_LOADING` |
| No W; not ready; no slot | Nothing allocated; release any named-pad claim | `DESTINATION_LOADING_LIMIT` |
| W for the same Level and region; ready | `begin`; bind W to T or release it | `JOURNAL_PENDING`, then the result |
| W for the same Level and region; not ready | Nothing changes | `DESTINATION_LOADING` |
| W for another Level or region | Release W (`REPLACED`), then apply the "No W" rows | as those rows |

The first row also warms a destination that is ready at request time. F1's store and ARMED write and F2's
acknowledgement take several ticks, and the census runs again in each of them. Without a warm-up, a destination that
is ready by chance (a player, another transfer's tickets) can stop being ready in that window, and the launch then
becomes a checked pre-F2 cancellation after ARMED was written (question 1).

### 7.3 Cap, one per rocket, lifetime and repeated requests

> **W207 implementation note (2026-10-30).** TERRAIN counts with WARMING/READY/BOUND toward the same eight-global/two-per-owner ceilings and configured lower caps. The captured 200-to-600-tick allocation deadline spans both stages without renewal. Adding the selected set once means once on entering that stage, not once for all stages: candidate-set changes and promotion remove and add tickets. Vanilla expiry is a per-ticket backstop measured from that ticket's addition, so the historical allocation-age-601 conclusion below does not hold across stages; explicit release still uses the original allocation deadline. Matching unresolved TERRAIN requests return loading without repeating unavailable terrain checks, renewing or launching; its initiating request ID remains rejected through promotion. Lifecycle release also covers TERRAIN. The original proposal below is retained, not a replacement for R-035's scoped amendment.

- **Global cap:** `MAX_DESTINATION_WARMUPS` = 8, counting every live state. Like the other flight limits it is a fixed
  constant in `RocketFlightLimits`. Eight matches the eight fixed pads per surface Level and the eight-flight reference
  workload.
- **One per logical rocket:** the map is keyed by L.
- **Lifetime: 600 ticks from start, never renewed.**
  - Re-adding an equal ticket (same type, Level, key and force-ticks) resets its age in `DistanceManager.addTicket`.
    So the service calls `addRegionTicket` for a warm-up's columns exactly once, at its start, and no repeated request,
    bind or poll adds them again.
  - The service releases W explicitly at age 600. The vanilla purge at age greater than 600 is the backstop, so no
    warm-up ticket outlives age 601.
- **Repeated requests.**
  - While W lives, a request for the same destination that is not ready gets `DESTINATION_LOADING`, with no ticket
    call, no new key and no change to the start tick.
  - A reused request ID gets `REQUEST_REPLAYED` from the existing ledger.
  - A request for another destination replaces W, releasing first. It needs a fresh, fully validated request, so it
    cannot run faster than the rate limiter. Each replacement costs at most 32 ticket calls, fewer than the
    synchronous terrain loads its own pad selection already makes.
- **An abandoned warm-up** (no further request) ends at its deadline at the latest. A new one for L can start only
  after the old one has ended, and only from a new request.

### 7.4 Keeping the pad, and continuation 53's pending reservations

> **W207 implementation note (2026-10-30).** The reservation, kept-pad recheck and no-gap statements below apply only once selection succeeds and the selected warm-up starts. TERRAIN owns capacity and candidate-footprint tickets, not a reserved landing region or named-pad claim. Promotion establishes the selected reservation without resetting the allocation clock; the earlier TERRAIN interval is not part of the reserved interval. The later fresh request still revalidates terrain, occupancy, reservations and admission. Named-pad rules below are unchanged.

- **Reservation.** W reserves its exact destination region in RAM. Continuation 53 (`453094c3`) feeds every pad path
  from `RocketTransferService.landingReservations(journal, pending)`: the journal's destination regions plus
  `EscrowFlightCheckpoint.pendingDestinations()`, held from F1's acceptance until F2 or discard. W90 adds a third input:
  the warm-up regions of every logical rocket except the requester's L.
- **The relaunch selects the same pad.** L's own relaunch does not see its own W as a reservation. The deterministic
  selector therefore returns the same first free candidate (same Level and region), unless terrain, an occupant or the
  request changed.
- **No gap.** From the bind, continuation 53's pending entry for L reserves the same region until F2, and from F2 the
  journal entry does. The handover then drops W's reservation, so the region stays reserved from W's start to F8.
- **Named pads.** `NamedLandingPadStaging.select` claims the pad for the request ID during selection (`:40`).
  - A `DESTINATION_LOADING` or `DESTINATION_LOADING_LIMIT` refusal releases that claim at once
    (`LandingPadService.release(server, plan)`, as the `!started` path does today, `RocketTransferService:150-152`).
    So no saved claim survives the wait.
  - W's reservation makes another rocket's selection of the same pad refuse with the existing `reserved` reason, and
    the relaunch claims the pad again under its new request ID.
  - Named-pad selection keeps its loaded-only rules. It still refuses an unloaded pad (`identity`, `unloaded`); the
    warm-up runs only after selection succeeded on already-loaded pad chunks.
- **Final guards.** The journal's own overlap check, `ensureNoAuthorityConflict`, stays the final guard, and continuation
  53's journal-guard test stays.
- **Restart.** Nothing is saved. W, its reservation and its tickets are RAM state or plain tickets.

### 7.5 Request identity: two options

**(R) Refusal and relaunch. Recommended.**

- r1 ends with `DESTINATION_LOADING`. `RocketFlightService.request` finishes it as FAILED in the replay ledger
  (`:224`), so r1 can never be reused (`REQUEST_REPLAYED`).
- The flight screen sends a fresh UUID with every intent (`RocketFlightNetwork:100`). The player's next press is
  therefore r2, and r2 becomes T if F1 begins.
- r2 runs every check from scratch (section 7.7). No request stays ACTIVE across the wait, no countdown is stored, no
  saved claim is held, and nothing launches without a player action.
- The cost is one more press, and the READY message (section 7.8) tells the player when.

**(I) Internal continuation.**

- r1 would stay ACTIVE in the replay ledger, and the server would start F1 by itself once the margin is ready.
- It needs a pending-request lifecycle: a timeout, a way to cancel (the rocket is not in COUNTDOWN, so today's cancel
  does not apply), finishing r1 on every end, and a rule for a requester who has left or lost access.
- The countdown built at r1's time would be stale. Its start tick feeds F1's checkpoint and F3's countdown, so the
  continuation would have to rebuild the plan and countdown. That is a relaunch under the old ID, without the player's
  validation at that time.
- Named-pad claims are saved in the landing-pad index, so holding r1's claim across the wait could leave a claim with
  no journal record after a crash.

**Recommendation: (R).**

### 7.6 Release

Every release removes the warm-up ticket at distance 2 with key L on each column of W's rectangle. It does so in W's own
destination Level, found by its key and skipped if absent, since an absent Level holds no tickets. The same call drops W
and its reservation. Removing an absent ticket does nothing, so a double release is safe.

| Event | When | From state |
|---|---|---|
| Handover (completion) | T's first ACTIVE visit that refreshed T's margin tickets, right after that refresh in the same tick, so the destination columns never lose their ticket | BOUND |
| The launch ends before publication | `begin` refuses, or the `discarded` callback runs (`RocketTransferService:142-149`) | BOUND |
| Replaced | A later request for L selects another Level or region | WARMING, READY |
| Disconnect | The requesting player logs out (`RocketFlightService.onPlayerLoggedOut`) | WARMING, READY (a bound launch no longer needs the player) |
| Rocket removal | The service tick finds the source entity missing or removed in its source Level | WARMING, READY (after F2 the source is removed by design) |
| Expiry | Age 600 server ticks; the vanilla purge at age greater than 600 is the backstop | every state |
| Stop and restart | The map is dropped and the tickets are not saved. Where the runtime is cleared while the Levels stay loaded (in-process fixtures), each W is released first | every state |

- **Other refusals.** A later request for L that is refused before the decision (rate limit, range, fuel) does not reach
  W, so W keeps running until its deadline.
- **A bound W whose T never reaches an ACTIVE refresh,** for example because T is held or routed to HOLD, ends at
  expiry.
- **The service tick** runs at the start of `RocketTransferService.tick`, before its early returns for a blocked
  journal (`:273-279`). So expiry, disconnect and removal releases keep working while the journal is blocked.

### 7.7 Fresh revalidation before F1

- The relaunch r2 repeats every check in section 7.2, items 1 to 4. That includes a new pad selection against current
  reservations, terrain and occupancy, and section 6's budgets.
- F1 then evaluates the unchanged `RocketGovernedLaunchBoundary.allows` over both ends:
  - both Levels, and readiness of every column of both sets;
  - the fixed source and destination UUIDs;
  - every rocket with L or either UUID in both margins, without an owner, snapshot or validity filter;
  - transaction overlap.
- W's tickets and its READY state are never evidence for any check; the census reads the world.
- A stale copy of L that W loads is refused by that census before ARMED, and nothing discards it.

### 7.8 Player text, logs and inspect

Player text. The request codes appear on the action bar through `notify`, like every other code; the READY message
appears there too.

| Key | English | Chinese |
|---|---|---|
| `flight.advancedrocketrycommunity.request.destination_loading` | Preparing the destination; launch again when it is ready | 正在准备目的地，就绪后请再次发射 |
| `flight.advancedrocketrycommunity.request.destination_loading_limit` | Too many destinations are being prepared; try again shortly | 正在准备的目的地过多，请稍后再试 |
| `message.advancedrocketrycommunity.flight.destination_ready` | Destination ready; launch again to depart | 目的地已就绪，请再次发射 |

- **READY.** The service polls each WARMING warm-up once per tick (at most 16 column reads each). When its margin is
  first fully loaded and entity-ticking, the state becomes READY and the requesting player, if online, gets the READY
  message once.
- **Logs.** One INFO line per transition and at most four per warm-up:
  `ARCE_GOVERNED_WARMUP event=<STARTED|READY|BOUND|RELEASED> logical=<L> request=<r> transfer=<T|none> level=<D>
  columns=<n> age=<ticks> reason=<…>`.
  - The RELEASED reasons are `HANDOVER`, `LAUNCH_ENDED`, `REPLACED`, `DISCONNECTED`, `ROCKET_REMOVED`, `EXPIRED` and
    `STOPPED`.
  - The existing per-request INFO line, `ARCE_FLIGHT_INTENT … code=`, already records each `DESTINATION_LOADING` and
    `DESTINATION_LOADING_LIMIT` refusal, so a cap refusal adds no line.
  - There is no WARN, because a warm-up is normal operation. The GameTest log checker counts only WARN and ERROR, so no
    expectation changes.
- **Inspect.** `/arce rocket inspect <uuid>`:
  - When no journal entry has that transfer ID but a warm-up exists for logical rocket `<uuid>`, the command prints one
    line instead of its failure: `Warm-up logical= state= request= transfer= level= columns=k/n age= remaining=
    player=`.
  - The transfer line of a T bound to a warm-up gains ` warmup=BOUND remaining=<ticks>` until the handover, and
    ` warmup=none` after it.
  - Both are read-only: no ticket, no load and no ledger creation.

### 7.9 Cost at the global cap

- **Tickets.** At most 16 per warm-up and 128 at the cap. Each is added once and removed once.
- **Chunk influence** at the cap, with section 2's assumptions:
  - 2 × 2 margins: 32 / 128 / 288 / 512;
  - worst shape (1 × 16): 128 / 432 / 800 / 1,232.
- **Together with 64 worst-shape transfers:** at most 2,176 tickets and 2,176 / 7,344 / 13,600 / 20,944 chunks. This is
  an upper bound, because a bound warm-up covers its own transfer's destination columns.
- **Terrain.** A cold destination may need its margin and read ring generated. Ticket-driven generation runs on the
  world-generation workers, not the server thread. Today's selection already loads, and if needed generates, the region
  columns synchronously on the server thread, and W90 does not change that.
- **Server thread per tick:** at most 8 warm-ups, each with one deadline compare, one player lookup, one entity lookup
  and, while WARMING, at most 16 column reads; at most 152 lookups in all. A start or release makes at most 16 ticket
  calls.
- **docs/17.** These are plain, bounded, non-forced tickets that are always released. They are not the "unexpected
  force-loaded chunks" that [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md) section 4 budgets at zero.
- **Measurement.** The reference run for R21 item 5 adds a cold-destination case with 2 × 2 and 1 × 16 margins. It
  measures MSPT at the warm-up start, the ticks to READY, loaded chunks and RSS.

## 8. Answers to the review findings

| Finding | What changed, or why not |
|---|---|
| **R1** (High): (A)'s cancellation does not exist | (A) and the automatic-return claim are withdrawn, and R21 item 4 is recorded. (B) keeps the pre-arm census, so a copy of L that F1 can see is refused before ARMED, and section 7.7 tests it. A copy that appears in a ready margin after F2 keeps today's behaviour: the record stays ACTIVE and refreshes within item 1's bound, Q5 reports it as `CENSUS`, and it is not an executor hold (question 5). Tests assert ticket counts and never infer them from `HELD`. |
| **R2** (Medium): "no player" and `native-03` | The status, section 7.1 and the record now say "an unready destination with no other ticket providing readiness". `native-03` is attributed to its START tickets and contained margins. Its Moon player only keeps the Level from being empty. No native run has shown the cold product-entry case; section 10 adds it. |
| **R3** (Medium): the extra origin column | Section 3: one checked set, the origin column required for new governed admission, a `DISCOVERY_MARGIN` hold for a recorded invalid end, and release always including the origin. The 16 bound is not expanded. Section 10 adds tests for an outside origin and an invalid persisted record. |
| **R4** (Medium): a budget change | Section 2 calls it an explicit change of the origin-only budget, as R21 item 1 decided, and gives bounds of 16, 32 and 2,048 with 256 refresh calls per tick, in sections 2, 3 and 11. |
| **R5** (Low): expiry wording | Sections 2 and 4: the first purge with age greater than 400 ticks, about 20 s only at 20 TPS. Zero owned tickets is distinguished from eviction, and the 402-tick test asserts ticket absence. |

The review's Q1 answer becomes section 7, Q2 becomes section 6 (governed only), Q3 is R21 item 1, Q4 is R21 item 5,
and Q5 is section 5.

## 9. Saved format

Neither change adds a saved record, key, schema or root, so neither needs a migration.

- **The column set** is derived from the immutable snapshots already in each record.
- **Margin and warm-up tickets** are plain `TicketType`s, never saved.
- **The `DISCOVERY_MARGIN` hold, the Q5 wait state, the warm-up map and its region reservations** live in RAM and are
  dropped on stop. Continuation 53's pending reservations are RAM state too.
- **New request codes** are never saved or sent. Language keys are resources in the v1.8 generated root, and no
  historical root changes.
- **Named-pad claims** keep today's lifetime: a refused request claims and releases in the same call.

## 10. Tests and JVM starts

**W90 leaf (R21 items 1 to 3, 5 and 6).**

- **Unit tests, column set (server-free):**
  - a margin inside one chunk gives 1 column, the origin's; one border gives 2; both give 4 (margin 63 to 66 gives
    columns 3 to 4);
  - 1 × 16 and 4 × 4 give 16; 1 × 17 and 5 × 4 give `OVER_BUDGET`;
  - negative coordinates;
  - the exact `max + 2` boundary (max.x 13 stays in column 0, and 14 reaches column 1);
  - `int` overflow at both ends gives `ARITHMETIC`;
  - an outside-origin snapshot, where the bounds miss relative 0 across a border, gives `containsOrigin` false;
  - for random regions within the bound, the readiness columns equal the refresh set, and the release set adds only
    the origin;
  - the re-expressed predicate equals today's formula on random and edge regions.
- **Unit tests, release** with a recording ticket sink:
  - two Levels, overlapping ends, two T values sharing columns, and an absent Level (the other end is still released);
  - repeated refreshes and F8 change nothing;
  - an invalid end's release removes only its origin.
- **Unit test, `afterClassification`.** An already-held record, and a hold raised by the `ready` supplier during the
  visit, both make zero refresh calls.
- **Unit tests, admission:**
  - the surface selector stops at the first budget failure with `chunk_budget` and no terrain access, counted with a
    recording Level;
  - the station and named paths answer `chunk_budget` before any load;
  - F1's source answers `LAUNCH_CHUNK_BUDGET` before selection;
  - a legacy launch that a governed one would refuse still selects.
- `RocketBenchmarkReadinessTest` stays as the predicate's pin.
- **GameTests,** in existing escrow batches, with no new batch name (376 names now; about 384 reshuffles the run
  order):
  - **Border-crossing flight.**
    - Setup: origins at x ≡ 15 (mod 16) at both ends, giving 2 × 2 margins. The destination is an empty Moon aged past
      the 300-tick idle threshold, with no player, START or FORCED support.
    - Until the warm-up exists, a test-only ticket holds both margins through F2 and is then removed; this does not
      test Q1.
    - Assertions: the flight tickets reach 8, no other ticket remains on the margins, and the flight passes F4 to F7,
      lands as one checked idle survivor and debits the exact fuel once. After F8, `flightTickets` is 0.
  - **Invalid persisted records,** a 1 × 17 destination and an outside-origin destination, decoded through the codec.
    Each gets a `DISCOVERY_MARGIN` hold and one WARN, with a new expectation in `scripts/gametest_expected_log.json`.
    There are no margin tickets and no ascent, debit or discard, and the record is unchanged.
  - **Release.** A held ACTIVE record has zero tickets within 402 ticks. An in-process restart, done as continuation
    32's fixtures do (remove T's tickets, reload the checked roots), re-tickets the same 8 columns and lands, and F8
    leaves zero.
  - **Native restart** belongs in W11b2's benchmark restart. An in-process reload cannot prove that tickets are not
    persisted.

**Warm-up leaf (Q1 option (B), after its own review).**

- **Unit tests,** on a server-free state machine with a recording ticket sink and a fake clock:
  - a start adds exactly the set, once;
  - the cap of 8 and one warm-up per L;
  - repeated requests make no ticket call and keep the start tick;
  - a replacement releases first;
  - every release event removes exactly the set in the right Level and drops the reservation;
  - expiry at 600, an absent Level, and the requester's own region excluded from reservations.
- **GameTests through `RocketRuntime.requestFlightIntent`** (the product entry):
  1. **Cold-destination launch through F8.**
     - Setup: the empty Moon as above, with border-crossing margins.
     - The first request answers `DESTINATION_LOADING` and adds n warm-up tickets, with no ARMED. The READY message
       arrives once.
     - A second request, with a fresh ID, answers `JOURNAL_PENDING` and passes F1 and F2. At the handover tick there
       are zero warm-up tickets and both flight sets are in place.
     - The flight then passes F3 to F8 and lands as a checked survivor with the exact debit once, leaving zero tickets
       of both types.
  2. **Repeated requests.** Five fresh requests while WARMING, spaced to pass the rate limiter, each answer
     `DESTINATION_LOADING`; the ticket count and start tick do not change and no key is added. A reused ID answers
     `REQUEST_REPLAYED`.
  3. **Disconnect.** The requesting mock player logs out, the warm-up is released, and the reservation is gone. A BOUND
     warm-up survives a disconnect and hands over.
  4. **Cap.** With eight rockets warming, a ninth answers `DESTINATION_LOADING_LIMIT` and gets no ticket; after one
     release the ninth can start.
  5. **Stale copy.**
     - Setup: a copy of L is saved in a destination margin column that is not loaded.
     - The warm-up loads it, and the relaunch is refused by F1 (`TRANSFER_JOURNAL_BLOCKED`).
     - There is no ARMED, PREPARED, debit, discard or cancellation, and the copy is still there. The warm-up is released
       with zero tickets.
  6. **Restart.** A WARMING warm-up, then an in-process runtime clear: zero warm-up tickets and no reservation, and a
     new request starts a fresh warm-up. The process-restart part goes with the native benchmark restart.

  Also: expiry without a relaunch (zero tickets by age 601), rocket removal, replacement, a second rocket that picks the
  next pad, and a named pad (claim released on refusal, another rocket's selection refused as `reserved`, the relaunch
  claims it).
- **JVM starts.** Each leaf needs two targeted unit runs, one whole unit suite, two or three filtered GameTest runs and
  one full `runGameTestServer` under the heavy-JVM lock: 6 to 7 Gradle invocations, which may start more JVM processes.
  A native restart outside the benchmark would add about three.

## 11. Risk and ADR text for Root

**R-035.** Proposed sentences, to add after its 持续时间与可用性 part when each leaf merges.

> 区块加载（W90，Root 决定 R21 第 1 至 3 项）：受管记录经检查分类为 ACTIVE 且未被扣留时，受管调度为服务端记录的两端，在各自检查列集合（区域最小坐标 −1 至最大坐标 +2 覆盖的区块列，至多 16 列且包含原点列）的每一列加一张 `arce_rocket_flight` 区域票（距离 2，以转移 ID 为键）。这是对原先每次转移两张原点票的预算的明确修改：每端至多 16 张，每次转移至多 32 张，64 个转移至多 2,048 张；在地形已生成、边距互不重叠的假设下，每次转移累计至多影响 32 个实体刻、108 个方块刻、200 个可访问和 308 个读取区块（不是实测内存）。F2 回调原有的两张原点票作为具名例外保留；普通对账、修剪、被扣留的修复、HOLD 与 CLEANUP 路由和旧版围栏不加票；未受管的旧版飞行仍只用两张原点票。F8 在各端所在 Level 移除原点票和同一列集合；不再刷新时，票在年龄超过 400 tick 的第一次清理时过期（20 TPS 时约 20 秒）；拥有的票归零不等于区块已卸载。加载票不持久化。受管发射的地表、空间站和指名平台选台以及 F1 源端使用同一列集合，在访问地形之前拒绝：目的地沿用 `chunk_budget` 原因，源端使用新的 `LAUNCH_CHUNK_BUDGET`；旧版发射的选台不变。已记录的受管端若列集合无效或不含原点列，成为执行器扣留 `DISCOVERY_MARGIN`：不加票、不推进，每个（飞行，原因）每次启动一条 WARN，保留 ARMED、阶段、源锁、着陆预留和一个转移名额，直到离线整世界修复（与 R17 第 6 项的扣留相同）；影响范围为单个转移，生产世界因准入关闭而没有此类记录。

> 目的地预热（W90 Q1 选项 B，随其实现登记）：受管发射通过频率、交互距离、所有权、重放、计划、Q2 预算和选台检查后，若服务端选定目的地的检查列集合尚未全部加载并实体刻，则为该集合每列加一张 `arce_rocket_warmup` 区域票（距离 2，以逻辑火箭 ID 为键，固定寿命 600 tick，从不续期），在该区域上保留内存预留，并回复“正在准备目的地”；玩家用新的请求 ID 再次发射，所有检查和 F1、F2 的完整普查重新执行。全服至多 8 个预热，每枚逻辑火箭至多 1 个，每个至多 16 张票（全服至多 128 张；最坏累计影响 128、432、800、1,232 个区块）；名额已满时回复“正在准备的目的地过多”，不加票。在 T 第一次 ACTIVE 刷新之后、F1 或 F2 在发布前结束、改选其他目的地、请求者断线、火箭移除、到期和停服时，在该 Level 移除同一集合；预热、预留和票都不持久化，重启后不存在。这是对“只有 ACTIVE 调度加票”的明确例外，不改变 F1、F2 的检查、证明、租约、重放排除或确认顺序，F2 之后除 R21 第 1 项的票外不加任何票。

**ADR-065,** W11b2 part 1 implementation note, under "The section 8.1 hook". Proposed bullets:

- **Flight tickets (W90, Root decision R21 item 1).** This is an explicit change from two origin tickets per transfer.
  - After a checked ACTIVE classification, and never while the record is held, the governed dispatch refreshes one
    `arce_rocket_flight` region ticket (distance 2, keyed by transfer) on each column of each server-recorded end's
    checked discovery-margin column set: at most 16 per end, 32 per transfer and 2,048 at 64 transfers.
  - The F2 callback's origin tickets remain a named exception, and legacy flights keep their two origin tickets.
  - F8 removes the origin ticket and the same set in each end's Level. Without a refresh, the tickets expire at the
    first purge with age greater than 400 ticks. Tickets are not persisted.
  - Ordinary reconciliation, pruning, held repair, the HOLD and CLEANUP routes and the legacy fences add none.
- **Checked column set (R21 item 2).**
  - One server-free, overflow-checked helper defines the columns for readiness, governed pad admission, F1's source,
    refresh and release.
  - New governed admission requires at most 16 columns, including the origin column. The 16-chunk limit is applied to
    the margin and not raised.
  - A recorded governed end that fails the helper becomes executor hold `DISCOVERY_MARGIN`: no margin ticket, no
    further refresh, one WARN per flight and reason, and offline repair. Its scope is one transfer.
- **Governed admission budget (R21 item 3).**
  - For governed launches, surface, station and named-pad selection count margin columns before terrain access and
    refuse with `chunk_budget`, and F1's source refuses with `LAUNCH_CHUNK_BUDGET`.
  - Legacy selection is unchanged.
- **Destination warm-up (R21 item 4, after its own review).**
  - Before F1, a launch whose server-selected destination is not ready gets one plain `arce_rocket_warmup` ticket per
    column of that destination's checked set: distance 2, keyed by the logical rocket, a 600-tick lifetime that is never
    renewed, at most 8 warm-ups and one per rocket. The player launches again with a fresh request ID.
  - It is released at handover, launch end, replacement, disconnect, rocket removal, expiry and stop.
  - It is an explicit exception to "only ACTIVE adds tickets", and F1 and F2 keep every check and proof.
  - It does not change "Pads do not force chunks or take independent flight tickets": the warm-up ticket is a
    non-forced, non-persistent launch-preflight ticket keyed by the logical rocket, never held by a pad.

## 12. Open questions

Each question has a recommended answer.

1. **Should a ready destination also get a bound warm-up** (section 7.2, first row)? **Yes.** It closes the window in
   which a destination that is ready by chance stops being ready during F1 and F2. The cost is at most 16 tickets until
   the handover, normally under a second.
2. **Should each player have a limit within the global cap?** **Yes: two warm-ups per requesting player.** It costs one
   count, and it stops one player with many rockets from holding all eight slots and delaying everyone else's cold
   launches.
3. **Should named pads take part?** **Yes.** The warm-up runs only after named selection succeeded on already-loaded pad
   chunks, and selection itself still never loads. The margin is a readiness need, not a selection need. If Root wants
   named destinations strictly loaded-only, they keep today's refusal instead.
4. **What about a source end that is not ready at request time?** This happens with a skinny source margin beyond the
   simulation distance. **Leave it out of the warm-up, as R21 item 4 limits it to the destination.** In the W90 leaf,
   replace its misleading `TRANSFER_JOURNAL_BLOCKED` with a source-labelled code, `LAUNCH_AREA_INACTIVE`: "Part of the
   rocket's launch area is not active; stay near the rocket and launch again" / "火箭发射区域有部分未激活，请留在火箭附近并再次发射".
5. **Should a census conflict after F2 become an executor hold?** **Not in W90.** It keeps refreshing within item 1's
   bound, and Q5's log and the inspect field show it as `CENSUS`. Revisit if it is ever seen. A hold would let the
   margin unload without resolving the copy.
6. **Are a 600-tick lifetime and a cap of 8 right?** **Keep them.** Change them only from the R21 item 5 measurement,
   never to make a test pass.
7. **Should the limits be configurable?** docs/17 section 5 asks for configurable hard limits. **Keep fixed constants,
   like the other `RocketFlightLimits`;** a configuration key would add log expectations and a test matrix without a
   current need.
8. **When should the vanilla re-read happen?** **Before the W90 leaf's code, on a quiet machine:** `javap` on the
   classes the review could not re-read, plus `DistanceManager.addTicket`'s age reset, which section 7.3's non-renewal
   relies on.
