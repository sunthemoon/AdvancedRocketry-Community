# W44 design note: the two world-first advancements

Date: 2026-10-14. Status: design for one independent read-only review (AGENTS.md section 13). It is
not an implementation or Gate evidence. Base `2d57f0d0`. The implementer is a headless Claude session.

Specification: ADR-066 section 6.2, from "World-first flags need versioned world state" to the end of
the section, with owner decision D3 (world-first semantics with recorded winning participants). D3 is
not reopened. This note changes no code, test, ADR, risk entry or status file. Section 7 holds the
text Root adds.

**Terms.** These terms are used throughout.
- *Writer confirmation* is W11b2's exact acknowledgement of one journal generation, or the known commit
  of one checked station or progression write.
- *Delivered* is the outbox state that ADR-066 calls "source acknowledgment": the source records that
  the matching progression receipt is durable.
- *Durable view* is what a source can prove is on disk. For the station registry that is its RAM,
  because RAM changes only after a known commit. For the journal it is the latest confirmed generation,
  not RAM.

## 0. Checked facts

These are static reads of the current tree, not native results.

| Fact | Where | Consequence for W44 |
|---|---|---|
| The journal root is schema 2 with the Beta epoch through the generic constant. Each record carries its own `schema_version` (2), independent of the root. | `ManagedSavedDataType.java:26-31,109-117`; `RocketFlightLimits.java:9`; `RocketTransferJournalCodec.java:22,43` | Root 3 is new. The record schema stays 2. |
| Journal RAM changes first. Every write (boundary flush, autosave, stop) is one capture of the whole RAM by the single W11b2 writer. Effects run only after the exact generation is confirmed. | `RocketTransferSavedData.java:142-167,239-262,300-348,363-399,415`; `RocketJournalWriter.java:20-29,47-85` | An outbox field in journal RAM is written by that writer. No second writer is needed. |
| The record reaches COMMITTED before descent. Descent completion is RAM-only today: `flight.land`, feedback and `rocketLanded` run in one tick with no journal write. | `RocketTransferService.java:471-475,642-658` | A checked completion must be added. COMMITTED is not a landing (ADR-066). |
| A rebuilt destination restarts in DESCENT from the record. | `RocketTransferEntities.java:76-94`; `RocketTransferRecord.java:260` | Every landing passes through the descent-completion branch. |
| Station checked updates are synchronous on the server thread. The live registry changes only after a known commit. UNKNOWN quarantines the registry, and ordinary saves then reassert RAM. | `StationRegistrySavedData.java:491-594,640-646` | The outbox can join the same candidate. Ordinary saves already serialize the durable view. |
| The station ordinary save is vanilla's in-place write (there is no `save(File)` override). | `StationRegistrySavedData.java` | This is an inherited torn-write risk. A torn file refuses startup (next row); it is never silently empty. |
| Every managed root is decoded semantically before start. A future or invalid root refuses world startup. | `WorldDataMigrationService.java:104-133`; `ManagedSavedDataPayloadValidator.java:16-44` | The progression root joins this rule. |
| Station root 5 (the orbital clock) exists in v1.8 development builds with epoch `v1.8.0-station-orbital-controls`. No release contains it; the last release evidence is v1.7.0 (root 4). | `ManagedSavedDataType.java:131-133`; `StationLimits.java:13`; `docs/releases/` | "Join 4 → 5 once" can no longer be done literally (section 9.1). |
| `AtomicSavedData` performs a synchronous checked replace, but it neither reads the target back nor forces the parent directory. | `AtomicSavedData.java:42-50`; `CheckedSavedDataFile.java:33-74` | Progression adds a directory force (section 3.2). |
| The `station_warp` recipients are the players in the Space Level inside the region who hold live VISIT access, filtered by `eligible`. | `StationWarpService.java:458-461,616-626`; `ClassicAdvancementEvents.java:63-95` | First-warp participants use the same predicate. |
| Nine GameTest call sites use `checkedRelocation(server, …)` directly as a fixture (solar, research, black hole, laser drill, planetary admission, railgun, elevator and sky tests). | `grep checkedRelocation(` | That overload must never elect (section 1.2). |
| `RocketTransferService` has 793 lines, `StationRegistrySavedData` 723 and `RocketTransferSavedData` 474. | `wc -l` | The new logic goes into new classes (section 10). |

## 1. Station warp outbox

### 1.1 Location and shape

The outbox is the root key `classic_first_warp` in `advancedrocketrycommunity_stations` root 5. It is
not stored in the 8-KiB `StationState`. Every key set is exact and every type strict.

| Key | Type | Rule |
|---|---|---|
| `schema_version` | int | 1 |
| `state` | string | `EMPTY`, `ELECTED` or `DELIVERED` |
| `event` | compound | Absent when `EMPTY`; otherwise required. |
| `event.kind` | string | `first_station_warp` |
| `event.event_id` | UUID | Minted with `UUID.randomUUID()` when the candidate is built. This is ADR-066's "immutable source transaction UUID"; the warp has no other transaction ID. |
| `event.station_id`, `event.actor_id` | UUID | The warped station and the confirmed actor. |
| `event.from_body`, `event.to_body` | string | Resource locations within the record's existing orbit-body bound. |
| `event.cost` | int | 1 to `MAX_WARP_ENERGY` |
| `event.orbital_tick` | long | The candidate's sampled clock (ADR-065 section 5.2.1). |
| `event.participants` | int array | 4n ints, n ≤ 4,096, UUIDs strictly ascending by `UUID.compareTo`. |
| `event.fingerprint` | string | 64 lowercase hex characters. It is SHA-256 over the UTF-8 kind, a 0 byte, the 16 `event_id` bytes, n as a 4-byte big-endian int, and then 16 bytes per participant. Decoding recomputes it. |

The bounds are 128 KiB, depth 16 and 8,192 nodes (ADR-066). The fixed shape has depth 3 and about 15
nodes, and at n = 4,096 the participant array is 65,536 bytes. Participants are a flat array, so the
node count does not grow with n.

### 1.2 Joining the checked relocation candidate

`StationWarpService.commitChecked` (`:482-525`) captures the participants (section 5). It then calls
a new overload, `checkedRelocation(server, observed, target, cost, FirstWarpRequest)`. The request
carries the participant list, the actor, and `electionAllowed`, which is false only for the
SOURCE_ROLLBACK hold in section 3.3.

The existing four-argument overload passes `FirstWarpRequest.NONE` and never elects. It stays
for the nine GameTest fixtures. An A0 source check asserts that `StationWarpService` is its only
production caller of the electing overload.

Inside `checked(...)` (`:491-566`), on the server thread and in one call:

1. Run the existing gates: operational, not quarantined, the observed state, the transition, the
   clock and the balance.
2. Decide the election: `request != NONE && request.electionAllowed && firstWarp.state == EMPTY`.
   No later warp can win while the slot is `ELECTED` or `DELIVERED`.
3. If electing:
   - check n ≤ 4,096;
   - check that the encoded registry plus the exact outbox fits 4 MiB;
   - on failure, return the new `CheckedUpdate.FIRST_EVENT_OVER_BUDGET`. Nothing is written or
     debited.
4. Encode one candidate containing the relocated station, the debited balance, the clock sample and
   the outbox. The outbox is `ELECTED(event)` when electing and the unchanged RAM outbox otherwise.
   `encode(...)` gains an outbox substitute beside its station and balance substitutes.
5. `requireValidCandidate` (`:601-625`) also requires the decoded outbox to equal the expected
   outbox exactly.
6. Replace the file through the unchanged `CheckedSavedDataFile.replace`: stage, force, readback and
   atomic move. Then call `CheckedSavedDataPolicy.forceDirectory` on the data directory, inside the
   same try block. A force failure therefore takes the existing post-replace path:
   `resolveReplacement` reads the file back, and if the candidate is on disk the result is COMMITTED.
   This adds durability on Linux; the outcome semantics are unchanged (choice R1 for the reviewer).
7. Publish: `registry.relocateChecked(...)`, then `firstWarp = elected`, then `clockFloor = sample`.
   All three happen in the existing publish block, so FE, orbit, clock and outbox become visible
   together. `StationWarpService.commit` then fires `station_warp` exactly as today and notifies the
   delivery service (section 3).

The existing failure and outcome-unknown semantics are unchanged:
- **WRITE_FAILED** leaves RAM untouched, so the slot stays EMPTY. A later warp builds a new candidate
  with a new `event_id`.
- **OUTCOME_UNKNOWN** quarantines the registry until restart. RAM has no event, and ordinary saves
  reassert it.
- On restart, the disk holds either the coherent old root or the coherent new one. A warp whose
  outcome was unknown is never retried as a new transaction within the session.

One change is needed, in the publish-failure branch (`:554-561`, unreachable on the owning
thread). Today it reasserts stale RAM over a candidate that is known to be committed. That breaks
"never overwrite a checked first-event commit with stale memory" (ADR-066). The branch instead keeps
the committed candidate as `committedOverride`:
- `save(CompoundTag)` returns a copy of it;
- every registry mutation refuses (`requireOperational` also checks the override) until restart;
- the existing ERROR is logged once.

### 1.3 Receipt acknowledgement write

When the progression receipt is durable (section 3.3), the delivery service calls
`checkedDeliverFirstWarp(server, expectedEvent)`. This is a checked station update with no station
or balance change.
- **Candidate.** It equals RAM except that the outbox state is `DELIVERED`, and it carries the
  current clock sample as every checked update does.
- **Checks.** It reuses `checked`'s candidate validation, replacement, readback and directory force.
  It requires the RAM state `ELECTED` with exactly `expectedEvent`.
- **Write budget.** It is charged to `StationWriteBudget`.
- **WRITE_FAILED** leaves `ELECTED`. The delivery service retries at most once per 200 ticks.
- **OUTCOME_UNKNOWN** uses the existing quarantine until restart. RAM keeps `ELECTED`, and ordinary
  saves reassert it, which can regress a `DELIVERED` file to `ELECTED`. That is harmless, because the
  event is kept and delivery is idempotent.

### 1.4 Migration, done once

| Source root | Result |
|---|---|
| 1, 2, 3 or 4 | The existing `StationRegistryPayload.upgrade` path to root 5 also adds `classic_first_warp = {schema_version 1, state EMPTY}`. |
| 5 with epoch `v1.8.0-station-orbital-controls` and no outbox | The pre-release variant (section 9.1). It is migrated once by the pre-start service, with its usual backup and staged validation: add the EMPTY outbox and stamp the new epoch. |
| 5 with epoch `v1.8.0-station-first-warp` and a valid outbox | CURRENT; repeated starts are a no-op. |
| 5 with any other combination: the new epoch without an outbox, the old epoch with one, or an invalid outbox | INVALID_SCHEMA. Startup is refused and the raw file is kept. |

No supported old root carries a trustworthy first-warp marker. No root records that a warp occurred,
and ADR-066 excludes the current orbit. Every migration therefore initializes EMPTY: there is no
retroactive award, and the world waits for a real warp.

Code that needs to know the epoch:
- `ManagedSavedDataType.formatEpoch(5)` returns the new epoch.
- `validateRootShape` requires the outbox exactly when the epoch is the new one.
- `SavedDataSchemaMigrator` gains one STATIONS case: schema 5 with the old epoch is MIGRATED, not
  CURRENT.

### 1.5 Reserved headroom

ADR-066 asks for headroom to be reserved "before accepting an eligible first-event request or taking
resources". `StationStorageBudget` reserves the 128-KiB outbox bound permanently. When the slot is
EMPTY the reserve is the outbox's future room; after election it covers the actual bytes, which the
fast-path upper bound does not otherwise count.

| Limit | Today | W44 |
|---|---|---|
| Growth (`GROWTH_LIMIT`) | 4 MiB − 256 KiB | 4 MiB − 256 KiB − 128 KiB |
| New balance entry (`admitsBalanceEntry`) | 4 MiB | 4 MiB − 128 KiB |
| Root bound | 4 MiB | unchanged |

Registries that are already above a new limit load unchanged. Only their new growth uses the existing
storage refusal, and the first-warp commit check in step 3 of section 1.2 still applies. The 4-MiB
root budget is not raised.

## 2. Moon landing outbox

### 2.1 Location and shape

The outbox is the root key `classic_first_moon_landing` in `advancedrocketrycommunity_rocket_transfers`
root 3, beside `transfers`. It is not part of a transfer record. Its `schema_version` and `state` are as
in section 1.1. The event has exact keys:

| Key | Type | Rule |
|---|---|---|
| `kind` | string | `first_moon_landing` |
| `event_id` | UUID | Minted at election. |
| `transfer_id`, `logical_rocket_id`, `owner_id` | UUID | Taken from the COMMITTED record. |
| `terminal_rocket_id` | UUID | The destination entity UUID at completion. It is an audit field; recovery keys on `transfer_id` (section 2.5). |
| `record_checksum` | string | The record's 64-hex checksum, which binds the event to the record's immutable authority. |
| `level` | string | `advancedrocketrycommunity:moon` |
| `terminal_origin` | int array | 3 ints: the destination snapshot's source origin. |
| `completed_at_game_time` | long | The Moon Level game time at completion. |
| `participants` | int array | 4n ints, n ≤ 16 (`RocketFlightLimits.MAX_PASSENGERS`), strictly ascending. |
| `fingerprint` | string | As in section 1.1. |

The bound is 4 KiB, depth 16 and 8,192 nodes. It is tighter than ADR-066's 128 KiB because the
passenger bound wins (ADR-066: "existing lower passenger/server bounds still win"). The journal limit
is 64 records × `MAX_TRANSFER_RECORD_NBT_BYTES` + 65,536 (`RocketFlightLimits.java:22-25`). The outbox
fits inside that fixed 65,536-byte root allowance, so the reservation is structural:
- no request or flight can exceed it;
- no new refusal is needed;
- the record and journal budgets are unchanged.

An A0 test encodes the maximum journal (64 records at the maximum size, as in
`RocketJournalBenchmark`) plus a 16-participant outbox, and asserts that the result is within the
unchanged limit.

### 2.2 Journal 2 → 3 migration

| Source root | Result |
|---|---|
| 1 | The existing stamp path, now to 3, with `migrated_from_schema = 1` as today, plus the EMPTY outbox. |
| 2 | Copy, add the EMPTY outbox, stamp 3 with epoch `v1.8.0-first-moon-landing`. Records are untouched, and record-1 COMMITTED records keep migrating at runtime under the old checksum-first rule (`RocketTransferService.java:673-688`). |
| 3, valid | CURRENT; repeated starts are a no-op. |
| 3 without the outbox, 2 with it, or an invalid outbox | INVALID_SCHEMA. Startup is refused and the raw file kept. |

No trustworthy Moon-landing marker exists in roots 1 or 2. A COMMITTED Moon record is a landed
reservation, which ADR-066 says "is not itself a Moon landing", and the entity's LANDED state is not
journal authority. Migration therefore always initializes EMPTY.

Pre-W44 jars, including v1.7.0, see root 3 as FUTURE and refuse startup. A downgrade needs the
pre-migration backup.

Code changes:
- `ManagedSavedDataType` gives ROCKET_TRANSFERS its own current schema 3.
- `formatEpoch(2)` stays `v0.9.0-beta`; `formatEpoch(3)` is the new epoch.
- `validateRootShape` requires the outbox from root 3 onwards and forbids it below 3.

### 2.3 Checked landing-completion authority

The block at `RocketTransferService.java:651-658` becomes one call to a new
`MoonLandingCompletion.complete(server, journal, record, destination)`. It returns LANDED_NOW, WAIT
or ORDINARY. The class is new so that `RocketTransferService` stays under 800 lines. The steps:

| # | Check or action | On failure |
|---|---|---|
| 1 | The destination Level is `advancedrocketrycommunity:moon`. The source Level does not matter, so a Moon-to-Moon hop counts (choice R2). | Not Moon: ORDINARY. |
| 2 | Validate a safe actual completion. The journal is operational. The record is COMMITTED with a valid checksum. `flightAllowed(…)` passes, so no escrow fence or bay hold applies. Exactly one loaded match exists (`RocketTransferEntities.loadedMatches`), and it is the record's destination UUID. That entity is operational, with the same owner, logical ID and snapshot ID and hash (`isCommittedAuthority` without its state test). Its flight state is DESCENT with `elapsed ≥ DESCENT_TICKS`. | WAIT: the rocket holds its terminal pose and is retried next tick. A missing destination keeps the existing `liveTransfers.remove` and recovery path. |
| 3 | Classify the slot from RAM. | Another transfer's event, or a SOURCE_ROLLBACK hold (section 3.3): ORDINARY, which is today's immediate in-RAM landing. This transfer's event: the projection in section 2.5. |
| 4 | Require `journal.canQueueEffect()`. The writer must have a continuation slot and no failed write. | WAIT. |
| 5 | Snapshot the event: `aboard(destination)` filtered by `eligible`, sorted (section 5); the identity fields in section 2.1; the fingerprint. | — |
| 6 | Set `journal.electFirstMoonLanding(event)`. RAM goes from EMPTY to ELECTED, `mutationSequence++` and `setDirty()`. | — |
| 7 | Queue `journal.afterAcknowledged(server, logical, publish, discarded)`. `publish` does what lines 652-657 do today: `updateFlightData(flight.land(t))`, `positionAtOrigin`, `RocketFlightFeedback.landed`, `auditPhase("landing_complete")`, the ordinary `rocketLanded` for the currently aboard players, and the delivery notification. | — |

While the rocket waits for confirmation it holds the terminal pose (`positionAtOrigin`) in DESCENT. It
is not LANDED, so `cleanupLandedIfComplete` cannot release its reservation (`:670`). Passengers may
dismount, because dismounting is never cancelled.

The election mutates no record. The continuation's guard therefore sees the same record object, and
the per-rocket journal guards of W11b2-P1F are unaffected. Like every journal write, the election
generation pauses other flights for the whole-journal confirmation (F3, open). This happens once per
world.

### 2.4 Use of the W11b2 writer and continuations

- **Capture.** `RocketJournalWriter.Snapshot` becomes `(sequence, records, firstMoonLanding)`. The
  outbox is an immutable Java record whose participants are an immutable list, so capture stays O(1)
  and safe across threads.
- **Encoding.** `RocketTransferSavedData.encode(records, outbox, target)` runs on the writer thread,
  as today.
- **Confirmation.** `RocketJournalCheckpoint.Acknowledgement` gains `firstMoonLanding`, which is the
  outbox state and `event_id` read from the written payload. The new query
  `journal.durableFirstMoonLanding()` reads this field. Delivery and projection use it, never RAM.
- **Continuations.** LANDED and its notifications are an `afterAcknowledged` continuation (step 7).
  The `DELIVERED` transition is a RAM mutation followed by `flushChecked`. Delivery polls
  `durableFirstMoonLanding()` and needs no continuation.
- **What is not added.** There is no new thread, no new file, and no direct `CheckedSavedDataFile`
  call from landing code. Stop, abort and the 200-tick retry behave as in W11b2.

### 2.5 Recovery when the entity's chunk state lags

If the journal holds this transfer's event (`transfer_id` equal) but the entity is still in DESCENT,
the completion is *projected*. This covers two cases:
- the entity chunk was saved before the election and the server stopped before the next chunk save;
- the destination was missing and recovery rebuilt it in DESCENT with a new UUID, through the
  existing rebind write.

Projection means:
- `complete` queues the same `publish` effect through `afterAcknowledged`, with no RAM mutation,
  election or new `event_id`;
- the effect runs once a clean confirmation exists. After a restart, that is the W11b2 baseline write;
- `terminal_rocket_id` stays as recorded. The unique authoritative rocket is the record's current
  destination, which the existing recovery and duplicate election already choose;
- projection writes only the entity, never the journal, so a lagging entity cannot overwrite the
  journal.

The reverse lag cannot happen. LANDED is applied in RAM only after confirmation, so no chunk can hold
LANDED for an eligible landing whose election is not durable.

Supersede and prune. After LANDED, the existing release, disassembly and return-flight `replace` may
remove the record. The outbox is root-level, so it stays. ADR-066 forbids discarding the only
completion authority before reconciliation. This design meets that because removal already requires
a LANDED stationary entity (`isReplaceableLandedAuthority`), and the outbox is never removed.

### 2.6 What a failed or unknown write leaves behind

| Outcome of the election generation | RAM | Disk | Player-visible | Restart |
|---|---|---|---|---|
| NOT_COMMITTED (definite) | ELECTED, dirty | Old generation | The rocket holds its terminal pose; no LANDED and no award. The existing journal ERROR is logged once per failure streak. | Crash: the old disk, so the rocket completes again and elects again with a new `event_id`. Clean stop: `stop()` drains and writes the final RAM, then projection runs. |
| UNKNOWN | ELECTED; the confirmation is cleared | Old or new | Same; no dependent effect. | Whichever generation is on disk; both are coherent. |
| A later retry COMMITTED | ELECTED | New | LANDED and the notifications run. | — |

RAM never moves the slot backwards (EMPTY, ELECTED, DELIVERED). That rule is what blocks a conflicting
completion or election until the durable reload, as ADR-066 requires. Another Moon landing that
completes while ELECTED is unconfirmed lands ordinarily, at once, and is not first. If the server then
crashes before the election is durable, that landing still is not first: it never had a checked
completion. The winner is whichever checked completion is durable first after the restart.

## 3. Progression SavedData

### 3.1 Root and bounds

The root is `advancedrocketrycommunity_classic_progression`, schema 1, epoch
`v1.8.0-classic-progression`. It is a new `ManagedSavedDataType.CLASSIC_PROGRESSION` (introduced in
v1.8.0, no legacy schema), shaped like ENDGAME and RESOURCE_ESCROW. Its required tags are
`first_moon_landing` and `first_station_warp`, both compounds.

| Key | Type | Rule |
|---|---|---|
| `first_moon_landing`, `first_station_warp` | compound | One receipt each. The key fixes the kind. |
| `<receipt>.occurred` | byte | 0 or 1. When 0, no other key is allowed. |
| `<receipt>.source` | string | `rocket_transfers` or `stations`. |
| `<receipt>.event` | compound | An exact copy of the source outbox `event`, decoded by the same codec. Its kind must match the key, and the fingerprint is recomputed. |

The whole root is limited to 256 KiB, depth 16 and 16,384 nodes; a receipt holds at most 4,096
participants. The managed-type IO quota is raised to `RocketLimits.MAX_TOTAL_NBT_BYTES` by the
existing constructor, and the 256-KiB bound is enforced by the codec. The root is created lazily by
the first receipt. An absent file means that no receipt exists.

### 3.2 Checked write and reload

- `ClassicProgressionSavedData extends AtomicSavedData`. Writes are synchronous on the server thread:
  stage, force, staged readback, then atomic move. They happen at most twice per world, plus ordinary
  saves when the root is dirty.
- **A receipt is durable** only when `flush(server)` returned and
  `CheckedSavedDataPolicy.forceDirectory(dataDir)` succeeded. A force failure calls `setDirty()`, and
  the receipt stays not durable until a retry, at most once per 200 ticks.
- **No wrong success is possible.** A commit that is reported failed but actually happened is
  harmless: the retry writes the same deterministic content.
- **Reload.** At start, the pre-start validator decodes the root strictly
  (`ManagedSavedDataPayloadValidator`). A future or invalid root refuses world startup and keeps its
  raw bytes, as for every managed root. A loaded receipt is durable.
- **No in-session quarantine.** No in-session path quarantines this root. If a bug made it
  non-operational, delivery would hold with the reason PROGRESSION_UNAVAILABLE.
- **Monotonic RAM.** A receipt only goes from occurred 0 to 1, and is never edited or removed online.

### 3.3 Delivery state machine

`ClassicFirstEventDelivery` is a server-lifetime service, cleared on stop, with no static world state.
It runs one step per kind on the server END tick, reading each source's durable view.

| Source durable view | Receipt | Action |
|---|---|---|
| EMPTY | absent | Nothing. |
| EMPTY | occurred | HOLD SOURCE_ROLLBACK. Elections of this kind are refused, so the warp or landing proceeds as not first. |
| ELECTED(E) | absent | Write R(E). Once it is durable, award (section 3.4), then request DELIVERED. |
| ELECTED(E) | R(E) | Award (idempotent), then request DELIVERED. |
| ELECTED(E) or DELIVERED(E) | R(F), F ≠ E | HOLD RECEIPT_MISMATCH. |
| DELIVERED(E) | R(E) | Steady state: login grants only. |
| DELIVERED(E) | absent | HOLD RECEIPT_MISSING. The receipt is never rebuilt automatically. |

Rules that apply to every row:
- **What a hold does.** A hold awards nothing, writes nothing, requests no DELIVERED and elects
  nothing. It logs one WARN, `ARCE_FIRST_EVENT_HELD kind= reason=`, per kind and reason per server
  start. Only offline repair clears it.
- **No second authority.** Progression never chooses a winner. It can only refuse an election
  (SOURCE_ROLLBACK), so a rolled-back source file cannot produce a second winner.
- **The SOURCE_ROLLBACK refusal is consulted only while progression is operational** (choice R3).
- **Diagnostics.** The read-only command `/arce progression status` (permission 2) shows, for each
  kind, the source state, the receipt state, the hold reason and the participant count.

### 3.4 Order of source, receipt and award

The four invariants:
- **I1.** A receipt is created only from a source event in its durable view.
- **I2.** Awards go only to participants of a durable receipt that matches the durable source event.
- **I3.** DELIVERED is requested only after the receipt is durable.
- **I4.** Source slots and receipts never move backwards online.

Awards use `ClassicEventTrigger` with two new events, `first_moon_landing` and `first_station_warp`.
Vanilla records a criterion once per player, so a repeated trigger is a no-op without a toast. "No
double award" therefore means no second winning set. I4 and the single-slot election rule rule that
out.

| Cut | Disk after the cut | After the restart, the same at both restarts |
|---|---|---|
| Before the source commit | EMPTY | No event and no award. The next qualifying event elects. |
| Source committed, no receipt | ELECTED(E) | R(E) is written from E. The same participants are awarded online or at login. DELIVERED follows. |
| Receipt committed, before the award | ELECTED(E) and R(E) | Award at login, then DELIVERED. |
| Award given, player files not saved, before DELIVERED | ELECTED(E) and R(E) | Login re-grant (idempotent), then DELIVERED. |
| After DELIVERED | DELIVERED(E) and R(E) | Login grants only. |
| Unknown source write | EMPTY, or ELECTED(E) | One of the first two rows. Never two events. |

No player file can be ahead of its receipt, because awards follow the durable receipt (I2).

### 3.5 Login grant

On `PlayerLoggedInEvent` (after vanilla has loaded the player's advancements), the service grants
each kind that meets all four conditions:
- the receipt has occurred and is durable;
- the kind is not held;
- the source's durable view is ELECTED or DELIVERED with the same event;
- the player is `eligible` and in the receipt's immutable participant array (binary search,
  O(log n)).

The grant reads only the receipt. It never reads current members, later joiners or the live source
participants. Because it is idempotent, it covers a player whose advancement file lags. One
consequence: an operator's `/advancement revoke` of a world-first advancement is granted again at the
player's next login. Removing it permanently needs an offline edit of the receipt (section 8).

## 4. Ordinary saves

| Store | What an ordinary save writes | Why it cannot overwrite a checked first-event commit with stale memory |
|---|---|---|
| Station registry (vanilla in-place) | RAM, which includes the outbox. | The outbox changes only after a known commit (section 1.2), so RAM is the durable view. UNKNOWN keeps the existing reassertion of that view (ADR-041), which contains no unconfirmed event. The publish-failure branch writes the committed candidate (section 1.2). |
| Transfer journal (the W11b2 writer) | A capture of the whole RAM. | RAM is monotonic for the slot, and generations are serialized: one active, one successor, never backwards (`RocketJournalWriter.java:47-58`). So every later generation contains the slot. `stop()` drains and writes the final RAM. |
| Progression (`AtomicSavedData`) | A checked write of RAM. | Receipts only ever move from 0 to 1. |
| Rocket entity (chunk save) | The entity's RAM. | LANDED for an eligible landing exists only after the journal confirmation. A stale DESCENT entity is projected from the journal (section 2.5) and never written back to it. |
| Player advancements (vanilla) | The player's criteria. | They are never treated as an authority. A lag is repaired by the login grant. |

`setDirty()`, `ChunkDataEvent.Save` and `ItemStack.save` are never used as barriers. Awards and
DELIVERED depend only on writer confirmations and on receipt durability.

## 5. Participants

| | Moon landing | Station warp |
|---|---|---|
| Who | Real connected players riding the unique authoritative destination rocket and listed in its seat manifest (`RocketTransferEntities.aboard`, `:305-312`). | Real connected players in the Space Level whose block X and Z lie in the committed station region and who hold live VISIT access (`access.allowed(station, uuid, hasPermissions(2), VISIT)`). This is the set `station_warp` uses (`StationWarpService.java:616-626`). |
| Eligibility | `ClassicAdvancementEvents.eligible`: not a FakePlayer, not disconnected, alive, not a spectator, and the player list's current instance. | Same. |
| Captured | In the completion tick, before the RAM election (section 2.3, step 5). | In the commit tick, inside the single synchronous checked call (section 1.2), so the capture is the exact completion set. |
| Bound | ≤ 16, the passenger bound. | ≤ 4,096 (ADR-066). |
| Preflight | Not needed: boarding bounds the set at 16, and the outbox fits the root allowance (section 2.1). | Only while the slot is EMPTY. `quote` (`:532-581`), which serves the request, the confirmation and the commit, counts the present eligible visitors and checks the registry room. Over a limit, it refuses with the new `StationManagementCode.WARP_FIRST_EVENT_OVER_BUDGET`. |
| Recheck | The exact set is captured once. | `checked()` rechecks the exact set and the candidate size. Over a limit, the result is the same code, with nothing written, debited or put on cooldown. |
| Visible refusal | None exists. | The command result and the existing abort notice (W22), in EN and zh_CN. The code is a new, additive persisted identifier. |
| Empty set | The event is recorded as occurred with no recipients. | Same. |
| Joining later, rejoining, or membership | Never added. | Never added. |

## 6. Native cut plan

These cases add to W11b2's harness pattern (`run_v180_rocket_journal_failures.py`). Each one halts
with `Runtime.halt` at a gated boundary. The gate is a new release-test property,
`arce.releaseTest.firstEventCut=<ID>`, which also requires the existing hooks flag and the
empty-dedicated-server check. Each case restarts twice. After each start the runner reads the raw
journal, station and progression roots and the player and advancement files, and asserts the exact
winner, event fields, participants, FE, orbit and fuel.

Headless natives have no players, so a gated hook injects a fixed participant UUID list. That tests
persistence order, not eligibility; A1 GameTests and the real-client case FX cover eligibility.

Save-order variants:
- **a**: `save-all flush` runs just before the halt, so player advancements, entity chunks and every
  dirty SavedData are written.
- **b**: no flush.

| ID | Setup and halt point | Expected after both restarts |
|---|---|---|
| FM1 | Moon landing: after the RAM election, before the writer's move. | Slot EMPTY at reload. The same transfer completes and elects again with a new `event_id`, the same participants and one receipt. |
| FM2a/b | After the election move, before LANDED. (a) The entity chunk was saved in DESCENT before the cut. (b) The entity was never saved, so it is rebuilt. | Projection only: no second election, one receipt, LANDED once. |
| FM3 | After the progression move, before the award. | The award happens at login, then DELIVERED. |
| FM4a/b | After the award, before the DELIVERED generation. | (a) The player file holds the award. (b) The login grant re-grants it. In both, one receipt and then DELIVERED. |
| FM5 | After the DELIVERED generation. | Steady state; the restart writes nothing new. |
| FM6 | The election write's committer moves and then throws, and the readback is made unreadable (UNKNOWN); then halt. | Whichever generation is on disk; exactly one winner; no award before the restart. |
| FM7 | Persistent NOT_COMMITTED on the election (the W11b2 failure-policy pattern). | Pending at both restarts: no LANDED and no receipt. Without the injection, the rocket lands and wins. |
| FM8 | Persistent progression write failure. | No award and no DELIVERED at either restart; delivered without the injection. |
| FM9 | Two rockets, with the first one's election held by a gated writer delay; the second lands meanwhile. | The first is the winner. The second landed ordinarily. |
| FM10 | After DELIVERED, the winning rocket is disassembled or relaunched (its record is removed or replaced); then halt. | Outbox and receipt unchanged. |
| FM11 | Historical worlds: journal root 1, and root 2 holding a COMMITTED Moon reservation. | Root 3 with an EMPTY slot, the backup made, no award. The second start is a no-op. A v1.7.0 jar refuses the migrated world. |
| FW1 | Warp: after staging, before the move. | No warp, slot EMPTY, FE unchanged. |
| FW2 | After the move, before publish. | ELECTED(E), orbit moved, FE debited, the receipt, then DELIVERED. |
| FW3 | After the progression move, before the award. | As FM3. |
| FW4a/b | After the award, before the DELIVERED write. | As FM4. |
| FW5 | After the DELIVERED write. | Steady state. |
| FW6a/b | Relocation UNKNOWN. (a) The ordinary save reasserted the old RAM before the halt. (b) Halt before that save. | (a) No warp. (b) Warp E. Each has exactly one winner. |
| FW7 | DELIVERED write UNKNOWN: quarantine, ordinary save of ELECTED, then halt. | Delivered again after the restart; no additional award. |
| FW8 | A registry filled to within the reserve, or a gated participant count over 4,096. | The refusal is visible, with no FE or orbit change. Unchanged after the restart. |
| FW9 | A second station warps while the slot holds E. | Not first. Unchanged after the restart. |
| FW10 | Historical worlds: station root 4 and pre-release root 5. | EMPTY, the backup made, the repeat start a no-op. A pre-W44 development jar refuses the new epoch. |
| FP1 | A future, and separately an invalid, progression root. | Startup refused at both restarts; raw bytes unchanged. |
| FP2 | The progression file is deleted while both sources are DELIVERED. | HOLD RECEIPT_MISSING; no award and no election. |
| FP3 | A receipt whose event differs from the source event. | HOLD RECEIPT_MISMATCH. |
| FP4 | The station file is replaced by a pre-warp copy while R(warp) exists. | HOLD SOURCE_ROLLBACK. A new warp is not first. |
| FX | Real-client run (the owner's harness, two clients): one passenger and one watcher; halt at FM4b. | After the restart, only the passenger is granted, at login. |

The plan has 26 cases (27 runs with the a/b variants), each with one cut and two restarts. FX also
serves as the V2-style capture evidence. None of these cases has been executed yet.

## 7. Proposed risk entry and ADR-066 note (Root edits both)

**Risk entry (new ID): world-first sources and the classic progression receipt store.**

- **Scope.** The station registry (root 5 with the new epoch), the transfer journal (root 3), the new
  progression root, and the `one_small_step` and `first_station_warp` advancements.
- **Startup refusal.** A future or invalid progression root, or a malformed outbox in either source
  root, refuses whole-world startup and keeps the raw bytes. This follows the existing managed-root
  rule, with one bounded migration diagnostic per start. It lasts until offline repair.
- **Delivery holds.** A missing or mismatched receipt, or a source rollback, holds that kind: no award,
  no DELIVERED, and no election of that kind. One WARN is logged per kind and reason per start. The
  hold lasts until offline repair; a restart alone is not repair.
- **Pending landing.** While the journal cannot confirm the election, the winning rocket holds its
  terminal descent pose and is not LANDED, and no other landing can win. The existing journal ERROR
  is logged once per failure streak. It lasts until the write succeeds (retried every 200 ticks) or
  the server restarts.
- **Warp refusal.** A first-event warp over budget is refused visibly (`WARP_FIRST_EVENT_OVER_BUDGET`),
  with no FE or orbit change.
- **Station quarantine.** An unknown DELIVERED write quarantines the station registry until restart.
  These are the existing semantics, applied to one more write per world.
- **Publish-failure hold.** In the unreachable publish-failure branch, registry mutations are refused
  until restart, and ordinary saves write the committed candidate.
- **Reduced headroom.** Station growth room shrinks by 128 KiB.
- **Downgrade.** Pre-W44 jars refuse journal root 3 and station root 5 with the new epoch, so the
  pre-migration backup is needed.
- **Re-grant.** A revoked world-first advancement is granted again at login.
- **Recovery.** Stop the server. Back up the whole world: data files, player data, advancements,
  regions and entities, with hashes. Repair only a copy, either by restoring a consistent set of the
  journal, station and progression files (never one file alone), or by correcting the receipt to
  match its source. Restart twice and check that there is exactly one winner per kind.
- **Not a save veto.** No serializer throws, and no chunk or player save is refused. Inherited risks:
  the station registry's in-place ordinary save can be torn (startup is then refused, not silently
  emptied); R-032 (failures the mod cannot observe, OS crash, power loss, per-file rollback, and
  Windows directory force); R-035 (F3 journal pauses).

**ADR-066 implementation note, "W44: world-first sources" (proposed text).**
> This note records the design that section 6.2 requires before runtime. It changes no decision.
>
> - **Station.** The station registry root 5 gains a required `classic_first_warp` outbox and the
>   epoch `v1.8.0-station-first-warp`. The unreleased pre-W44 root 5 migrates once before start, with
>   a backup. The outbox joins `checkedRelocation`'s single candidate, and its election is decided
>   inside that call.
> - **Journal.** The transfer journal gains root schema 3 with a `classic_first_moon_landing` outbox.
>   The per-record schema stays 2.
> - **Moon completion.** A Moon descent completion that is eligible to win is elected in journal RAM
>   and written by the existing W11b2 writer. LANDED and its notifications run only after the exact
>   confirmation. A lagging entity is projected from the journal.
> - **Progression.** The receipt store is a new managed schema-1 root, written by the synchronous
>   checked path plus a directory force. Delivery is source event, then durable receipt, then award,
>   then DELIVERED. Login grants read only the immutable receipt.
> - **Not done.** Participants are never compacted. No supported old root carries a trustworthy
>   marker, so every world starts EMPTY.
>
> The protection scope, durations, logs and recovery are those of risk entry [new ID]. Native cut
> evidence and independent implementation review remain open.

## 8. Owner questions

No owner decision blocks implementation, and D3 is not reopened. Root may want the owner to see
three player-visible consequences:
1. **No retroactive award.** Worlds that already warped or landed on the Moon before upgrading award
   only the next real event, because no old root records a trustworthy marker. ADR-066 already
   decides this; it is listed for awareness.
2. **Revocation does not stick.** A world-first advancement revoked by an operator returns at the
   participant's next login (section 3.5). The idempotent login grant cannot tell a revocation from
   a lagging file. If the owner wants revocation to stick, that needs a new operator command to edit
   a receipt. It is not designed here.
3. **Development worlds migrate.** Pre-W44 v1.8 development worlds with station root 5 are migrated
   once, with a backup (section 9.1). They are not refused. This is a Root choice that touches the
   owner's real-client test worlds.

## 9. ADR text that cannot be followed literally

1. **"Join C17's station registry 4 → 5 migration once"** (ADR-066 section 6.2, and ADR-065 section
   5.2.1 lines 594-596). Root 5 was frozen by W07 without the outbox, and v1.8 development worlds
   already hold it.
   - *Smallest change:* keep schema number 5, give the outbox-bearing root 5 the new epoch, and
     migrate the old-epoch pre-release root 5 once, before start, with a backup (section 1.4).
   - *Why it is safe:* no release ever contained root 5, and pre-W44 jars refuse the new epoch, so a
     development downgrade cannot silently drop the outbox.
   - *Unchanged:* ADR-065's "a current root 5 lacking clock is malformed" still holds.
   - *Alternative:* root 6 is simpler to read, but it is the "second root bump" that ADR-066 forbids
     and would need an amendment.
2. **"Extend C17's journal 2 → 3 migration once"** (ADR-066 section 6.2; ADR-065 section 4.1). No
   C17 journal 3 was ever frozen or scheduled (W12 record, lines 146-153), and snapshot schema 2
   already carries the resources. *Smallest change:* W44's root 3 is "transfer journal 3". The note
   should state that the root schema and the per-record schema are separate, so a later C17 cargo
   change bumps the record schema, not the root.
3. **"No later warp can win…" together with the ADR-041 publish-failure reassertion.** The current
   unreachable publish-failure branch would overwrite a known commit with stale RAM. *Smallest
   change:* the `committedOverride` in section 1.2.
4. **Clarifications, not changes:**
   - the Moon outbox bound is 4 KiB because the 16-passenger bound wins;
   - compaction is permitted but not used;
   - "source acknowledgment" is the state named DELIVERED here.

## 10. Implementation shape and dependencies

| Area | New or changed |
|---|---|
| Shared | New package `progression/classic/firstevent`: the `FirstEvent` record with its codec and fingerprint, `FirstEventOutbox` (state and event), `ClassicProgressionSavedData`, `ClassicFirstEventDelivery` (tick and login), and the status command. `ClassicEvent` gains 2 events, and `Milestone.ONE_SMALL_STEP` and `FIRST_STATION_WARP` change from PENDING to EVENT. `runData` must run (generated advancement JSON). |
| Station | The outbox field, candidate substitution, publish, `checkedDeliverFirstWarp` and override in `StationRegistrySavedData`, kept under 800 lines with the codec in the shared package. `StationStorageBudget`, `StationRegistryPayload`, `ManagedSavedDataType`, `SavedDataSchemaMigrator`, `StationWarpService` (capture, preflight, the new code) and the language entries. |
| Journal | `RocketTransferSavedData` (outbox, elect, deliver, durable view), `RocketJournalWriter.Snapshot`, `RocketJournalCheckpoint.Acknowledgement`, the encoder, and the new `rocket/server/MoonLandingCompletion`. The net change to `RocketTransferService` is 5 lines or fewer. |
| Tests | A0: codecs at each bound, fingerprints, both migrations, the election and delivery state machines over every row of section 3.3, budgets, and the source check from section 1.2. A1: one GameTest batch for both kinds. Real elections touch the GameTest world's shared slots, so other cases use detached SavedData files, as W11b2 did. The assertions `!done(FIRST_STATION_WARP)` in `ClassicMilestoneAdvancementGameTests.java:363` and `ClassicCrystallizerAdvancementGameTests.java:60` test the PENDING state, which W44 removes. They are replaced on purpose by the new batch; this is not a weakening. The Python validators that pin journal or station root schemas or epochs need updating. S1/S2: section 6. |
| Depends on | The W11b2 part-1 writer and continuations (merged). The open F3 applies to the election generation. There is no dependency on escrow admission, W11b3 or the bay. |

Choices the reviewer should confirm:
- **R1:** the directory force after a station replace;
- **R2:** a Moon-to-Moon hop counts;
- **R3:** SOURCE_ROLLBACK refuses elections only while progression is operational;
- **R4:** no compaction;
- **R5:** the epoch-based migration of pre-release root 5 (section 9.1);
- **R6:** the WAIT behaviour (terminal pose) while the election is unconfirmed.

## Record

- **Done:** design only. The output is this file.
- **Commands:**
  - `git status`, `git worktree list` and `git log`: the worktree was clean on branch
    `codex/v1.8.0-w44-world-first-design` at `2d57f0d0`;
  - read-only file reads and searches, `wc -l`, and `ls` of `scripts/` and `docs/releases/`;
  - `git diff --check` before the commit.
- **Not run:** no Gradle, GameTest or native command, because the task forbids production and test
  code.
- **Choices where the ADR was open:** the six choices R1 to R6 above, and section 9.
- **Not done:** the independent review, the implementation, and the risk and ADR edits, which belong
  to Root.
- **Risks:** section 7.
