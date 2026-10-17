# W90 design: governed flight readiness and the discovery margin

Design note for Root, 2026-10-17, for [TASK-W90-READINESS-DESIGN](../TASK-W90-READINESS-DESIGN.md). It is a proposal,
not an implementation, ADR change or risk acceptance. Product code was read only at `379525ea`. That commit is not on
this branch, so its paths are code spans, and line numbers refer to it. Vanilla and Forge facts come from
`forge-1.20.1-47.4.10_mapped_official_1.20.1.jar` in the Gradle cache, read with `javap -c -p`. The native evidence is
continuation 52's `native-01/governed-benchmark.json` (SHA-256 `510037b2…bda9028`, as in its record).

**Recommendation.** Option (a): one `arce_rocket_flight` ticket on every discovery-margin column of each recorded
end. The ticket type, distance, key and expiry stay as they are, and so does the readiness predicate. Two related
findings need Root's decision before production admission (section 7). Q1: F1 cannot start a flight to a destination
with no player nearby. Q2: landing admission counts fewer chunk columns than readiness does.

## 1. What the margin needs, and why

**Vanilla 1.20.1.**

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
  - Once loaded, `startTracking` puts the entities into the UUID index, so `level.getEntity(UUID)` finds them.
    `getEntities().get(type, AABB, …)` and `getEntitiesOfClass` also see them: both go through
    `EntitySectionStorage.forEachAccessibleNonEmptySection`, which skips only sections that are not accessible.
  - The entities are not in the tick list. `isPositionEntityTicking` is false, because it needs TICKING
    (`canPositionTick`, offset 5) and `inEntityTickingRange`, which is `TickingTracker` level 31 or less (offset 25).
- **Demotion is immediate.** `updateChunkStatus(HIDDEN)` stops tracking every entity at once, but the chunk stays
  LOADED until the next `processUnloads`. In that window `areEntitiesLoaded` is true while the entities are invisible.
  No public method reports TRACKED.
- **Empty Levels.** Even at level 31 entities tick only in a Level that is not empty. `ServerLevel.tick` skips the
  entity tick list and block entities after 300 ticks with no players and no forced chunks (offsets 295 to 410), and
  `isPositionEntityTicking` stays true meanwhile. Ticket expiry (`chunkSource.tick`) and entity loading
  (`entityManager.tick`) still run every tick.

**Callers of `recoveryEndsAlreadyLoaded`** (`RocketTransferEntities:194-220`):

| Caller | What it relies on |
|---|---|
| `RocketGovernedSourceDispatcher.tick` (`:22-33`, `:158-165`) | The predicate gates every F3 to F6 effect in `dispatchLoaded`. |
| `RocketGovernedLaunchBoundary.allows` (`:47`): F1 preflight and ARMED capture (`EscrowFlightCheckpoint:54`), F2, F3, returns, F4, F5, arrivals, `MoonLandingCompletion` | A census in both Levels: the two fixed UUIDs, plus an AABB scan over each margin for any rocket with logical ID L or either UUID, without an owner or snapshot filter. The count must equal the expected endpoints (`:48-74`). |
| `RocketTransferFences.loadedReason` (`:191`), `RocketTransferRecoveryService.recoverById` (`:155`) | Legacy fenced records only (governed ones return `GOVERNED_ACTION_REQUIRED` at `:168`). The same census, before elections that discard copies. |
| `RocketEntity.tick` (`:468`) → `RocketEscrowAudit.observe` → `EscrowRuntime.observeFlight` | Change detectors on the bound source and arrival, and `EscrowGovernedTerminal` invalidation when another copy of L ticks. No progress waits on a tick: the server tick drives every transition, and `governedObservation` moves on NBT save and load (`:335`, `:445`). |

So no caller needs margin entities to tick. Each needs every margin column loaded and visible at the moment of its
census. The bound entities stand in the origin column: placement, ascent and descent all use the origin x and z, and
a scanned origin is itself a rocket block (`RocketStructureScanTask:76`, `:124-130`). Entity-ticking is stricter than
needed, but it implies loaded and visible, and the public API reads it exactly from the visibility map the census
uses. `areEntitiesLoaded` alone is wrong during demotion. **Keep the predicate unchanged.**

`native-01` agrees. In all eight records both margins are 2 × 2 and all 64 columns are loaded. Every destination margin
has only its origin column entity-ticking. Four source margins are the same. The other four, at columns x 3 to 10,
have three or four columns ticking for an unrelated reason: they lie at x ≤ 9, which fits the Overworld spawn area.

## 2. Options

A ticket on a column makes that column entity-ticking, ring 1 block-ticking and ring 2 FULL. Vanilla also reads ring
3 at a lower status, because `ChunkMap.prepareAccessibleChunk` takes range 1. With no player within 128 blocks these
chunks get no random ticks or spawning: `ServerChunkCache.tickChunks` needs `anyPlayerCloseEnoughForSpawning` or a
force-ticks ticket, and ours is not one. Entities and block entities still tick unless the Level is empty, and
scheduled block and fluid ticks run in block-ticking chunks. Each cell below gives entity-ticking / block-ticking /
accessible / read chunk counts.

| | Tickets per end | 2 × 2 end | Worst end (1 × 16) | Worst transfer | 64 transfers, worst |
|---|---|---|---|---|---|
| Today | 1 | 1 / 9 / 25 / 49 | the same, never ready | 2 tickets, 2 / 18 / 50 / 98 | 128 tickets, 128 / 1,152 / 3,200 / 6,272 |
| (a) | one per column, ≤ 16 | 4 / 16 / 36 / 64 | 16 / 54 / 100 / 154 | 32 tickets, 32 / 108 / 200 / 308 | 2,048 tickets, 2,048 / 6,912 / 12,800 / 19,712 |
| (b) radius r | 1, at distance 2 + r | r = 1: 9 / 25 / 49 / 81 | r = 15: 961 / 1,089 / 1,225 / 1,369 | twice the end | capped at r ≤ 2: 3,200 / 6,272 / 10,368 / 15,488 |
| (c) | 1, as today | 1 / 9 / 25 / 49 | not covered | as today | as today |

The benchmark's eight transfers with 2 × 2 ends would use 64 tickets and 64 / 256 / 576 / 1,024 chunks under (a),
against 16 tickets and 16 / 144 / 400 / 784 today.

- **(a) One ticket per margin column.**
  - *Safety:* the predicate, census, holds and guards are unchanged; (a) only makes the predicate reachable. The
    type, distance 2, key T and 400-tick expiry are today's, only the ACTIVE dispatch adds tickets, and adding one
    never loads a chunk synchronously.
  - *Column set:* one shared helper computes it from the record's immutable snapshot with the predicate's formula,
    `(min − 1) >> 4` to `(max + 2) >> 4`. Like `columnsAlreadyLoaded`, it refuses an empty or overflowing set or more
    than `MAX_LANDING_CHUNKS` (16) columns; such an end keeps only today's origin ticket. The origin column is always
    ticketed, so a fixture whose origin lies outside its bounds could reach 17.
  - *Cost:* at most 32 tickets per transfer, refreshed on each visit, so at most 256 refreshes per tick. A refresh
    is a hash lookup and a sorted-set lookup, with no tracker update while the level is unchanged. Machines and
    entities in the margin run for up to 20 s after the last visit, as the origin column does today.
  - *Release:* F8 removes the same recomputed set, and expiry is the backstop (section 4).
- **(b) One ticket per end, at a distance chosen from the margin radius.**
  - The predicate is unchanged, but the ticket entity-ticks chunks outside the margin, and a 1 × 16 margin needs
    radius 15.
  - Any lower cap leaves admitted shapes that are never ready, unless F1 refuses them, which is a new admission
    refusal.
  - Each square contains the margin, so (b) never costs less than (a). Release is one removal per end, at the same
    recomputed distance.
- **(c) Today's tickets, accepting loaded and visible for the non-origin columns.**
  - It **weakens** a secondary path: a copy of L in a column that does not tick never runs the tick-based
    `RocketEscrowAudit` placement transition or the `EscrowGovernedTerminal` invalidation. The census still refuses
    that copy at every effect, and its join still invalidates the authority, so governed code loses no guarantee it
    relies on. Made shared, (c) would weaken the legacy destructive elections the same way.
  - It needs a new access-transformer read of `PersistentEntitySectionManager.chunkVisibility` (the entity manager
    is already exposed).
  - It covers only columns within two rings of the origin. A 4 × 4 margin with the origin in a corner, or any long
    margin, would still wait forever.
  - Release is unchanged.

**Choose (a).** It alone covers every margin the predicate accepts, changes no check, and makes only the margin
entity-ticking.

## 3. What does not change

Option (a) keeps the F0 to F8 acknowledgement order, the store/force/readback proofs, the checked journal leases,
the executor holds (`afterClassification` refreshes nothing while held), the replay exclusion and every saved format.
Tickets are runtime-only, and their set derives from snapshots already in the record. It also keeps the ticket rule:
only the ACTIVE governed dispatch adds tickets, and only on server-recorded ends. Ordinary reconciliation, pruning,
held repair, the HOLD and CLEANUP routes and the legacy fences add none, and unloaded ends wait there without tickets.

Only the count per end changes: from one to the margin's column count, at most 16. I read design 2's "existing bounded
flight tickets" as the existing type, distance and expiry under an explicit bound. If Root reads it as the two origin
tickets, which continuation 32's TASK fixed ("keep the existing ticket bounds"), then (a) changes that rule. The
remaining choices would be a capped (b) or (c), each with a new F1 refusal (Q3).

## 4. Release

- **F8.** `releaseFlightTickets` (`RocketGovernedCleanupDispatcher:62`) removes, for each end in its own Level, the
  same set the refresh adds, origin included, at distance 2 with key T.
  - Ticket equality is (type, level, key, force-ticks), and each column has its own set, so another transfer's ticket
    on a shared column is untouched.
  - Removing an absent ticket does nothing, so a repeated release is safe.
  - The F2 callback's `keepLoaded` origin tickets (`RocketTransferService:136-137`) are part of the set.
- **Different Levels.** Each end's tickets live in its own Level's `DistanceManager`, and each end is resolved
  separately. A missing Level is skipped: it can hold no tickets.
- **Restart.** `arce_rocket_flight` is a plain `TicketType` with a 400-tick lifespan. It is neither a FORCED ticket nor
  managed by `ForgeChunkManager`, so it is never saved, and a restart starts with no flight tickets (the comment at
  `:56`). Afterwards only ACTIVE visits add tickets, from the same function and the same record. If the record is
  gone, `state.record` is null and nothing was added.
- **Expiry.** `Ticket.timedOut` holds once more than 400 ticks have passed since the last `addTicket`, and
  `purgeStaleTickets` runs in every Level's `chunkSource.tick`, empty or not. So a ticket outlives its record's last
  ACTIVE visit by at most 400 ticks, whatever ended the visits: F8, a hold, a CLEANUP or HOLD route, a successor T, a
  refusal, or a journal waiting on `retryChecked`.
- **No leak.** Refresh and release use one function, and the expiry backstop does not depend on F8. No ticket keeps
  chunks loaded after the transfer stops being ACTIVE.

## 5. Players

- **Today.** After F2 the source shows its countdown (`countdownAccepted`), then stays on the pad indefinitely in
  COUNTDOWN or FUELED, as in `native-01`. Nothing is logged and no message is sent, and `/rocket inspect <transfer>`
  shows PREPARED.
- **Under (a).** The wait is the load time of a player-free end, normally a few ticks, and the 60-tick countdown
  usually hides it. A short wait is healthy, so it logs nothing, as design 2 requires.
- **Recommendation.**
  - After 400 ticks continuously not ready, log one INFO line `ARCE_GOVERNED_ENDS_WAITING transfer= logical= phase=
    source=k/n destination=k/n`, and one `ARCE_GOVERNED_ENDS_READY transfer= waited=` when the wait ends. That is at
    most two lines per wait across at most 64 records, never one per tick. Use INFO, not WARN: continuation 32 requires
    a never-loading end to raise no fence WARN.
  - Add a read-only `/rocket inspect` field: ready columns per end and the flight ticket count. It adds no tickets.
  - Send no player message in this slice.

## 6. Tests and JVM starts

- **Unit, column set (server-free).**
  - A margin inside one chunk gives 1 ticket, the origin column.
  - One border gives 2; both give 4 (continuation 52's margin 63 to 66 gives columns 3 to 4 on both axes).
  - At the bound, 1 × 16 and 4 × 4 each give 16.
  - 1 × 17, 5 × 4 and overflowing coordinates give no margin tickets.
  - For random regions within the bound, the columns `columnsAlreadyLoaded` inspects equal the ticket set.
- **Unit, release symmetry** with a recording ticket sink: refresh then release on a two-Level record leaves every
  sink empty, and repeated refreshes and releases change nothing. `RocketBenchmarkReadinessTest` stays as the
  predicate's pin.
- **GameTest, border-crossing flight** (own batch, 1,600-tick timeout).
  - Template: `EscrowGovernedReadinessGameTests`. Its origins sit at x ≡ 8 (mod 16), so its margins never cross a
    border. Use x ≡ 15 at both ends, giving 2 × 2 margins.
  - Put the destination on an empty Moon: no players, no forced chunks.
  - Until Q1 is decided, a test-only `TicketType` holds both margins up to the F2 acknowledgement, then is removed.
  - Assert that `flightTickets(server, T)` (`RocketTransferFenceGameTests:90`) reaches 8 and that no other ticket
    remains on the margins. Then assert F4 to F7, landing, the checked idle survivor, one rocket, the exact debit
    once, and `flightTickets == 0` after F8.
- **GameTest, release.**
  - A held ACTIVE record reaches zero tickets within 402 ticks.
  - An in-process restart, as continuation 32's fixtures do it: remove T's tickets and reload the checked roots. The
    flight re-tickets the same 8 columns and lands, and F8 leaves zero.
  - The native two-restart check belongs in W11b2's benchmark restart, not in a separate run.
- **JVM starts.** Two targeted unit runs and one whole suite; two or three filtered GameTest runs; then one full
  `runGameTestServer` (about 25 minutes, under the heavy-JVM lock). That is about six, with no native server start
  while the restart check is folded into the benchmark; three more if not.

## 7. Risk and ADR text, and open questions

**R-035** has no flight-ticket sentence today. Add this after its 持续时间与可用性 part:

> 区块加载：受管飞行记录经检查分类为 ACTIVE 后，为服务端记录的两端在发现边距（区域最小坐标 −1 至最大坐标 +2）的每个区块列各加一张 `arce_rocket_flight` 区域票（距离 2，400 tick 过期，按转移 ID 区分）；每端至多 16 张，每次转移至多 32 张，64 个转移至多 2,048 张；无玩家时每次转移至多 32 个实体刻区块、108 个方块刻区块、200 个可访问区块。F8 在各端所在 Level 释放同一集合，过期只作兜底；加载票不持久化。普通对账、修剪、被扣留的修复及非 ACTIVE 记录不加票，未加载的端照旧无票等待。

**ADR-065**, W11b2 part 1 implementation note, under "The section 8.1 hook": "Flight tickets (W90): only after a
checked ACTIVE classification, one `arce_rocket_flight` region ticket (distance 2, 400-tick expiry, keyed by
transfer) on each discovery-margin column of each server-recorded end, at most `MAX_LANDING_CHUNKS` per end. F8
removes the same set in each end's Level, and expiry is the backstop. Tickets are not persisted. Ordinary
reconciliation, pruning and held repair add none."

**Q1. F1 and F2 with an unloaded destination.**

- **Problem.** F1's preflight evaluates the whole boundary synchronously at request time (`EscrowFlightCheckpoint:54`,
  `:106-108`). Pad selection gives the destination's region chunks only a one-tick FULL load
  (`RocketLandingPadSelector:381-386`), so their entities are not loaded yet. The boundary is false, `begin` returns
  false, and the player sees `TRANSFER_JOURNAL_BLOCKED`. Nothing may ticket the destination before the F2 record
  exists, so a governed launch to a Level with no player near the pad is refused. This predates (a), which does not
  fix it. Continuation 52's runs kept a player on the Moon; the finding comes from reading the source, and no run has
  shown it.
- **Options.**
  - (A) F1 and F2 keep requiring the source end, but no longer the destination. The first post-F2 visit tickets the
    destination under (a), and every F3 to F6 effect waits for both ends' full census. One case changes: a stale copy
    of L in an unloaded destination margin, which F1 refuses today, becomes a post-F2 hold before ASCENT, which the
    existing exact-source return can cancel.
  - (B) Warm-up tickets on the server-selected pad, keyed by request, with a retryable "destination loading" refusal.
    This changes the section 3 ticket rule and needs new rate limits and release paths.
  - (C) Keep F1 as it is. Admission then stays impractical.
- **Recommended: (A), together with Q2.** Without Q2, (A) would arm destinations whose margin can never be ready.
  Root decides, because (A) changes a guard in F1 and F2.

**Q2. Admission bound.** Landing selection and named pads count region columns (`RocketLandingPadSelector:73`, `:158`,
`:221`, `:364-379`; `NamedLandingPadStaging:67-69`), but readiness counts margin columns. A region three columns wide
can have a margin five wide, so some admitted pads can never become ready. **Recommended:** every pad path and F1's
source check use the shared margin helper, and refuse with the existing `chunk_budget` reason.

**Q3. Reading of "existing bounded flight tickets".** **Recommended:** accept (a) as within design 2's rule, and record
the new bound with the R-035 text above.

**Q4. Ticket budget.** **Recommended:** no new cap. The existing limits (64 transfers, 2 ends, 16 columns) bound the
count. Measure ticket refreshes and loaded chunks in the eight-flight benchmark against the docs/17 budgets.

**Q5. Player message.** **Recommended:** none now. Revisit after Q1 if waits are ever seen to outlast the countdown.
