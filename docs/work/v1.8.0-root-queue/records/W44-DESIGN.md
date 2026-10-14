# W44 design note: the two world-first advancements

Date: 2026-10-14. Status: round 2 design awaiting a fresh independent read-only review (AGENTS.md
section 13). It is not an implementation, accepted freeze or Gate evidence. Round 1 was authored by
a headless Claude session at `25f5ff4a`; this Codex revision starts from `a744aed4`.

## Round 2 changes

Review: `D:/GitHub/ARCE-Task-Evidence/root-queue/W44-review/REVIEW.md`, SHA-256
`bf8a200ede03566c674322ee2fa5d12c5d2330916beab6aec495fa771e8c379c`, verdict REDESIGN.
The following replaces the round 1 contracts; none of it claims runtime verification.

| Finding | Changed sections | Precise change |
|---|---|---|
| F1 (High) | 2.4, 2.5, 3.3, 6 | Every journal load requests a checked baseline, including ELECTED/DELIVERED with zero transfers. Proof remains unavailable until confirmation. FM10 has four retained-state/player-file variants, no new flights and two restarts. |
| F2 | 1.2, 2.3, 2.6, 3.2-3.3, 7, R3 | SOURCE_UNCONFIRMED is temporary, not EMPTY or corruption. Startup orders validation, service initialization, confirmation and election. All unavailable progression paths deny election; potentially first actions wait/refuse before resources. Retry scheduling is bounded and self-recovers. |
| F3 | 3.3, 3.5, 6, 7 | A connection-scoped bounded deferred queue retries actual grants after baseline, respawn or spectator exit, and discards disconnects. Only immutable receipt participants qualify. FX variants inspect real advancement progress. |
| F4 | 1.2-1.3, 4, 6, 10 | A separate root-only checked entry bypasses only station-transition gates, validates exact unchanged station/reservation/balance sets and the ELECTED-to-DELIVERED substitution plus clock, and shares one extracted writer/resolver. Station deletion does not prevent delivery. Every override-sensitive accessor/mutator is covered. |
| F5 | 2.6, 6 | Every native/client variant is enumerated with thread, gate/barrier, fixture, award oracle and two-restart result. FM9 waits under inherited F3; UUID-only headless cases claim no player award; client cuts use an external debugger, not the empty-server hook. |
| F6 | 2.2, 3.5, 7-8 | Receipt repair against an existing validated source is distinct from authority restoration/downgrade, which requires a consistent complete world snapshot. Managed-root migration backups are insufficient. Receipt-only revocation is removed. |
| Other review comments | 1.4-1.5, 2.2-2.4, 3.2, 5, 6, 9-10 | Exact epoch whitelist precedes CURRENT classification; near-limit migration refuses intact. Journal migration has an explicit type branch. Receipt durability is per kind. Eligibility access and bounded counting are explicit. R1-R6 and all deviations are answered; Moon negative coverage and private publication guards are preserved. |

Specification: ADR-066 section 6.2, from "World-first flags need versioned world state" to the end of
the section, with owner decision D3 (world-first semantics with recorded winning participants). D3 is
not reopened. This note changes no code, test, ADR, risk entry or status file. Section 7 holds the
text Root adds.

**Terms.** These terms are used throughout.
- *Writer confirmation* is W11b2's exact acknowledgement of one journal generation, or the known commit
  of one checked station or progression write.
- *Delivered* is the outbox state that ADR-066 calls "source acknowledgment": the source records that
  the matching progression receipt is durable.
- *Durable view* is a tagged result: KNOWN(EMPTY/ELECTED/DELIVERED), SOURCE_UNCONFIRMED or
  SOURCE_UNAVAILABLE. Healthy station RAM is loaded/known committed authority; a publish-failure
  override supplies the committed candidate instead. A quarantined unknown station update is
  unavailable. Journal authority is the latest writer confirmation, never unconfirmed RAM.

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
carries the participant list, actor and election disposition from section 3.3: ALLOW, NOT_FIRST or
DEFER. The delivery service refreshes source/progression availability before the call; the checked
entry rechecks it on the same server thread before constructing a candidate. There is no yield
between these checks and replacement. No unavailable/held progression path can return ALLOW.

The existing four-argument overload passes `FirstWarpRequest.NONE` and never elects. It stays
for the nine GameTest fixtures. An A0 source check asserts that `StationWarpService` is its only
production caller of the electing overload.

Inside `checked(...)` (`:491-566`), on the server thread and in one call:

1. Run the existing gates: operational, not quarantined, the observed state, the transition, the
   clock and the balance.
2. A DEFER request returns a visible temporary `WARP_FIRST_EVENT_UNAVAILABLE` refusal before any
   write, FE debit or cooldown. Elect only for a non-NONE ALLOW request, a known EMPTY source, an
   EMPTY RAM slot and a validated absent progression receipt. No later warp can win while the slot
   is `ELECTED` or `DELIVERED`; NOT_FIRST preserves the slot and follows the ordinary warp path.
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
   R1 retains these outcome semantics: the normal Linux path forces the directory, but the
   error/readback-COMMITTED path does not retry that force. Readback proves candidate identity, not
   directory durability. This is weaker than the journal/progression barrier; Windows has no
   portable directory-force guarantee. No power-loss guarantee or R-032 waiver is claimed.
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
- every registry mutation refuses until restart, including checked entries, `foldWarpCredits`,
  ordinary mutators, access generation and clock advancement; their separate operational gates
  must consult the override, not only `requireOperational`;
- the durable outbox accessor decodes the validated override, never stale EMPTY RAM. Other public
  registry reads that could authorize mutation report unavailable; they must not authorize from
  the half-published registry. Save and status can read the immutable committed candidate;
- the existing ERROR is logged once.

### 1.3 Receipt acknowledgement write

When the progression receipt is durable (section 3.3), the delivery service calls
`checkedDeliverFirstWarp(server, expectedEvent)`. This is a **root-only checked operation**, not
`checked(observed, unchangedStation, 0, null, ...)`: that existing method returns UNCHANGED before
encoding and requires a named station transition.
- **Entry gates.** Owning server thread; operational, no UNKNOWN quarantine or committed override;
  a durable exact matching receipt; and live root `ELECTED(expectedEvent)`. Exact already-DELIVERED
  returns idempotent UNCHANGED; a different event returns STALE. No station lookup is required.
- **Candidate.** Capture the entire current registry and one clock sample. Keep every station,
  reservation and balance exactly unchanged; substitute only `ELECTED(E)` with `DELIVERED(E)` and
  the sampled root clock. All immutable event fields and participants stay identical.
- **Validation.** Strict CURRENT root decode, existing byte/node limits, decoded station list,
  reservations and balance map exactly equal to the captured originals, exact expected outbox
  substitution, and decoded clock exactly equal to the sample. Require
  `clockFloor <= sample <= clock.current()` and every station epoch <= sample. No null replacement
  is passed through the existing station validator. Unexpected additions/deletions/debits fail
  validation before replacement.
- **Shared write.** Extract `StationCheckedCandidate` validation and `StationCheckedWriter`
  replacement/outcome resolution from the adapter (section 10). Both root-only and named station
  operations call the same one writer, `CheckedSavedDataFile.replace`, readback resolver, R1
  directory-force policy and publish-failure override. All other operations keep their current
  observed-station, exact named-transition/charged-relocation and UNCHANGED gates.
- **Publish.** Only the outbox and `clockFloor` change, together after known COMMITTED. Deleting the
  winning station before delivery changes the current registry snapshot but not the retained root
  event: acknowledgement validates that post-deletion snapshot, with no station recreation or FE
  change. FW11 explicitly exercises this.
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
- `SavedDataSchemaMigrator` gains one STATIONS case **before** generic epoch validation and CURRENT
  classification: exact schema 5, exact old epoch and no outbox. Validate the complete old payload,
  including its clock, under the old contract; copy all unrelated fields, add EMPTY, stamp the new
  epoch, then validate/budget-check the new payload. The pre-start service backs up before commit.
  New epoch without/with invalid outbox, old epoch with any outbox, unknown epochs and invalid old
  clocks refuse intact. No broad epoch fallback is allowed.

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

Registries above a new growth limit load if their migrated encoding still fits the unchanged 4-MiB
root limit. Only new growth uses the storage refusal. Adding even EMPTY metadata to an old root can
exceed 4 MiB: refuse that migration intact before staging/commit, retain the original and backup,
and report the existing near-limit recovery boundary (FW10c). Never promise unchanged loading for
an over-budget migrated encoding. The first-warp commit check still applies; no root budget grows.

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

Pre-W44 jars, including v1.7.0, see root 3 as FUTURE and refuse startup. A downgrade requires a
consistent complete pre-upgrade world snapshot (section 7); the managed-root migration backup alone
does not include the corresponding terrain, entities, POI or player state and is insufficient.

Code changes:
- `ManagedSavedDataType` gives ROCKET_TRANSFERS its own current schema 3.
- `formatEpoch(2)` stays `v0.9.0-beta`; `formatEpoch(3)` is the new epoch.
- `validateRootShape` requires the outbox from root 3 onwards and forbids it below 3.
- `SavedDataSchemaMigrator` has an explicit ROCKET_TRANSFERS branch for roots 1/2: validate the old
  root using its existing checksum-first recovery rules, preserve every record/unrelated field,
  add EMPTY, stamp root 3/the new epoch and validate the result before staging. Merely changing
  `ManagedSavedDataType` would leave the generic stamp at root 2/Beta epoch and is not sufficient.
  Strict valid root 3 is CURRENT; the per-record runtime migration remains separate.

### 2.3 Checked landing-completion authority

The block at `RocketTransferService.java:651-658` becomes one call to a new
`MoonLandingCompletion.complete(server, journal, record, destination)`. It returns LANDED_NOW, WAIT
or ORDINARY. The class is new so that `RocketTransferService` stays under 800 lines. The steps:

| # | Check or action | On failure |
|---|---|---|
| 1 | The destination Level is `advancedrocketrycommunity:moon`. The source Level does not matter, so a Moon-to-Moon hop counts (choice R2). | Not Moon: ORDINARY. |
| 2 | Validate a safe actual completion. The journal is operational. The record is COMMITTED with a valid checksum. `flightAllowed(…)` passes, so no escrow fence or bay hold applies. Exactly one loaded match exists (`RocketTransferEntities.loadedMatches`), and it is the record's destination UUID. That entity is operational, with the same owner, logical ID and snapshot ID and hash (`isCommittedAuthority` without its state test). Its flight state is DESCENT with `elapsed ≥ DESCENT_TICKS`. | WAIT: the rocket holds its terminal pose and is retried next tick. A missing destination keeps the existing `liveTransfers.remove` and recovery path. |
| 3 | Refresh the availability/election disposition (section 3.3), then classify the RAM slot without treating it as proof. | DEFER (including source-unconfirmed or progression-unavailable with a potentially EMPTY slot): WAIT, no election. Known other-transfer event or permanent election hold: ORDINARY. This transfer's event: projection (2.5), still awaiting writer confirmation. |
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
  full validated immutable outbox read from the written payload, not just its ID. The tagged query
  `journal.durableFirstMoonLanding()` reads it. Missing acknowledgement is SOURCE_UNCONFIRMED,
  never EMPTY. Delivery and projection use this proof, never an unconfirmed RAM event.
- **Initialization independent of transfers.** Every valid journal load (also a new absent-file
  EMPTY root) starts SOURCE_UNCONFIRMED and schedules one baseline capture through the same writer
  at the first END tick, before the existing `isDirty() || !entries().isEmpty()` dispatch predicate.
  In particular loaded ELECTED **and** DELIVERED outboxes request it with `transfers=[]`, regardless
  of dirty state, players or new flights. This is a checked write/confirmation, not seeding the
  checkpoint from loaded or in-session RAM. The root event is retained while awaiting it.
- **Retry.** One active write and one coalesced successor remain the bound. On no confirmation or
  UNKNOWN, retain SOURCE_UNCONFIRMED and schedule at most one retry per 200 ticks, independent of
  transfer count; the first baseline request is immediate. A subsequent exact COMMITTED result
  restores the tagged known view and wakes deferred grants. An unrelated UNKNOWN after DELIVERED
  temporarily suspends grants/delivery, preserves the event and receipt, and self-recovers through
  this rule without offline repair. Failure never fabricates a known EMPTY view.
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
- the effect runs once a clean confirmation exists. After a restart, that is the explicit baseline
  in section 2.4, including retained-event worlds with no remaining transfer records;
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
| NOT_COMMITTED (definite) | ELECTED, dirty | Old generation | The rocket holds its terminal pose; no LANDED and no award. The existing journal ERROR is logged once per failure streak. | Crash reloads old disk; next actual completion may elect a new `event_id`. Clean stop inherits W11b2 drain/final-write rules and discards effects: projection runs on the next start only if E was actually committed, not during stop. A failed drain is not promised a successful final write. |
| UNKNOWN | ELECTED; the confirmation is cleared | Old or new | Same; no dependent effect. | Whichever generation is on disk; both are coherent. |
| A later retry COMMITTED | ELECTED | New | LANDED and the notifications run. | — |

RAM never moves the slot backwards (EMPTY, ELECTED, DELIVERED). That rule blocks conflicting
completion/election until confirmation or durable reload. Under inherited F3, an unconfirmed
whole-journal generation pauses other live flight dispatch: another rocket cannot finish an ordinary
landing during the delayed election. After confirmation it resumes and may land ordinarily, not
first (FM9). An isolated A0 election case may test two completion requests; it is not a native claim
that the second rocket flies through F3. A cut before source commit preserves no candidate UUID or
participant promise: only the next actual checked qualifying completion can elect, using its then
connected participants. FM1's same-list result is a deterministic fixture property, not that promise.

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
  stage, force, staged readback, then atomic move. There are two logical receipt insertions per
  world, not a bound of two physical writes: failures, confirmation retries and ordinary dirty saves
  can write again. There is no second writer or override of final `AtomicSavedData.flush`.
- **A receipt is durable** only when `flush(server)` returned and
  `CheckedSavedDataPolicy.forceDirectory(dataDir)` succeeded. A force failure calls `setDirty()`, and
  the receipt stays not durable until a retry, at most once per 200 ticks.
- **Per-kind proof.** Keep two session-local confirmed-receipt values, each either absent or the exact
  immutable event that passed the barrier. New RAM insertion is RECEIPT_PENDING; a matching RAM
  value after a failed flush/force is still pending, not an R(E) durable row. The coordinator captures
  the root generation, calls final `flush(server)`, then forces the directory; only after both return
  does it publish the exact captured receipts as confirmed. Server-thread serialization prevents
  a concurrent insertion. `onPersisted()` and a successful ordinary save alone do not confirm a new
  receipt. Retry this barrier for pending kinds even if an ordinary save cleared the dirty flag.
  An already confirmed other kind stays confirmed: every later monotonic candidate retains it
  exactly. A strictly validated loaded receipt initializes its own confirmed value, not a RAM edit.
- **Platform boundary.** This is the existing force policy: Linux requires directory-force success;
  Windows returns without a portable directory force. It is not a power-loss guarantee on Windows.
- **No wrong success is possible.** A commit that is reported failed but actually happened is
  harmless: the retry writes the same deterministic content.
- **Reload.** At start, the pre-start validator decodes the root strictly
  (`ManagedSavedDataPayloadValidator`). A future or invalid root refuses world startup and keeps its
  raw bytes, as for every managed root. A loaded receipt is durable.
- **No in-session quarantine.** No in-session path quarantines this root. If a bug made it
  non-operational, delivery holds PROGRESSION_UNAVAILABLE and elections fail closed. The service
  rechecks at most once per 200 ticks; it may resume only after operational, strictly validated
  authority is restored, never by constructing an EMPTY fallback. Malformed persisted data still
  follows startup refusal/offline repair. No receipt is synthesized while unavailable.
- **Monotonic RAM.** A receipt only goes from occurred 0 to 1, and is never edited or removed online.

### 3.3 Delivery state machine

`ClassicFirstEventDelivery` is a server-lifetime service, cleared on stop, with no static world state.
It runs at most one step per kind on the server END tick, reading each source's tagged durable view.

**Startup and election order.**
1. The pre-start migration/semantic validators either accept all managed roots or refuse startup.
2. Before login handlers or travel admission can elect, initialize the server-lifetime service,
   both sources and the progression store. Loaded valid progression receipts are confirmed;
   healthy loaded station authority is known; journal authority begins SOURCE_UNCONFIRMED.
3. Schedule the journal baseline even with zero entries (2.4). Before its confirmation, queue eligible
   login reconciliation and deny Moon election; do not compare an absent acknowledgement to a receipt
   as though it were EMPTY. Station election can proceed only with its own known source and validated
   progression state; it does not depend on an unrelated Moon baseline.
4. Refresh availability and permanent holds before each election attempt, not only in a later END
   tick. ALLOW requires known EMPTY source, EMPTY RAM and a validated absent receipt. Known occurred
   source or a permanent hold gives NOT_FIRST (ordinary later travel, no election). A possibly EMPTY
   source without proof or an unavailable progression store gives DEFER (Moon WAIT; station visible
   pre-debit refusal). An unavailable progression store **never** yields ALLOW, even if RAM is EMPTY.
5. Confirmation/barrier recovery automatically re-evaluates temporary holds and deferred logins.
   No travel callback may bypass these initialization or availability checks.

| Source durable view | Receipt | Action |
|---|---|---|
| SOURCE_UNCONFIRMED (no acknowledgement, startup or UNKNOWN) | Any | Temporary wait: no award, receipt insertion, DELIVERED or election; bounded writer retries. Not corruption. |
| SOURCE_UNAVAILABLE (for example station UNKNOWN quarantine) | Any | No award, DELIVERED or election. Existing station quarantine clears on validated restart, not offline repair by default. |
| Any | PROGRESSION_UNAVAILABLE | Temporary fail-closed wait; no award, receipt write, DELIVERED or election. Bounded availability recheck. |
| KNOWN EMPTY | validated absent | Nothing; election may be ALLOW only after the full gates above. |
| KNOWN EMPTY | validated occurred | HOLD SOURCE_ROLLBACK. Only this proven comparison establishes rollback; travel may proceed as not first. |
| KNOWN ELECTED(E) | absent | Insert R(E), confirm its barrier, award online eligible participants, then request DELIVERED. |
| KNOWN ELECTED(E) or DELIVERED(E) | matching RAM receipt, RECEIPT_PENDING | Retry the receipt barrier; no award or DELIVERED until confirmed. |
| KNOWN ELECTED(E) | durable R(E) | Idempotent online award, then request DELIVERED. |
| KNOWN ELECTED(E) or DELIVERED(E) | validated R(F), F != E | HOLD RECEIPT_MISMATCH. |
| KNOWN DELIVERED(E) | durable R(E) | No new receipt/acknowledgement. Immediate/deferred login grants still run (3.5). |
| KNOWN DELIVERED(E) | validated absent | HOLD RECEIPT_MISSING. Never rebuild it automatically. |

Rules that apply to every row:
- **Permanent data holds.** SOURCE_ROLLBACK, RECEIPT_MISMATCH and RECEIPT_MISSING award nothing,
  insert no receipt, request no DELIVERED and elect nothing. They log one WARN
  `ARCE_FIRST_EVENT_HELD kind= reason=` per kind/reason/start and require offline repair (7).
- **Temporary availability holds.** SOURCE_UNCONFIRMED and PROGRESSION_UNAVAILABLE are rechecked
  without a corruption verdict. The source/progression retry is at most once per 200 ticks, with
  one active operation per source and the journal's single coalesced successor. Log one WARN per
  kind/reason/start and one INFO on recovery, not every tick. Retry cadence bounds work, not lifetime:
  there is no expiry that discards authority or requires offline repair after N attempts. Station
  UNKNOWN uses its existing restart-only quarantine. Pending player eligibility is not corruption.
- **No second authority.** Progression never chooses a winner. It can only refuse an election
  (or defer it while unavailable), so a rolled-back/unavailable store cannot produce a second winner.
- **R3 is replaced:** every progression-unavailable path denies election. Operational-only refusal
  was fail-open and is not retained.
- **Diagnostics.** The read-only command `/arce progression status` (permission 2) shows, for each
  kind, the tagged source state, per-kind receipt proof, hold reason, retry deadline, deferred online
  count and participant count. It must not print unconfirmed RAM as durable EMPTY.

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
| After DELIVERED | DELIVERED(E) and R(E) | Immediate or deferred login grants; startup source confirmation still precedes them. |
| Unknown source write | EMPTY, or ELECTED(E) | One of the first two rows. Never two events. |

No player file can be ahead of its receipt, because awards follow the durable receipt (I2).

### 3.5 Login grant

On `PlayerLoggedInEvent` (after vanilla has loaded the player's advancements), the service attempts
each kind with all four conditions:
- the receipt has occurred and is durable;
- the kind is not held;
- the source's durable view is ELECTED or DELIVERED with the same event;
- the player is `eligible` and in the receipt's immutable participant array (binary search,
  O(log n)).

The participant lookup reads only the immutable receipt (binary search); source proof is checked for
exact matching authority, not used to recapture participants. No current members, later joiners or
live passenger list can change the winning set.

**Bounded deferred reconciliation.** A login skipped for baseline/receipt-barrier pending, temporarily
unavailable authority, death or spectator status retains one pending bit per kind for that **current
player UUID and connection-instance token**, only if a strictly decoded immutable receipt identifies
that participant. A stale disconnect must not remove a newer connection's entry. This
lookup schedules work but does not prove durability or authorize an award. A nonparticipant or
already-complete eligible player is removed immediately. At most two entries per connected player
and **4,112 entries total** (4,096 warp plus 16 Moon participants) are possible; the cap follows
immutable receipt bounds rather than a configurable connection count. Every disconnect removes
that connection's entries. No offline UUIDs are enqueued. A reconnect creates a new entry after
advancements load. If no decoded receipt is yet available at login, its later confirmation/readiness
starts the one-time bounded participant cursor below, which finds still-connected eligible players
and schedules ineligible ones; it does not require another login or a speculative unbounded queue.

Use a fair online queue: examine at most 16 entries per END tick, and recheck an entry no more than
once per 20 ticks. Respawn/return-to-survival need no new login event: the queue resolves the current
player-list instance and eligibility again. Baseline/receipt confirmation wakes entries for that
kind within the same bounded queue, not by looping over the receipt. Permanent holds suspend queued
grants; stop clears them. Pending bits do not expire while connected, so a long death/spectator or
baseline delay does not silently lose recovery. At P connected players, after readiness a fair full
pass takes at most `20 + ceil(2P / 16)` ticks; retry/cut tests measure that bound without increasing
existing timeouts. A disconnect during deferral grants nothing offline and retains no player object.

Every successful attempt checks vanilla advancement progress: if incomplete, trigger the criterion,
then require actual completion in the test oracle. Repeated attempts are idempotent, with no extra
winning set or reward (these advancements have empty rewards). This applies to ELECTED and DELIVERED
sources, so steady-state delivery does not strand a temporarily ineligible login. In the initial
ELECTED award pass or authority-readiness recovery, look up at most 16 participant UUIDs per tick
with one bounded cursor per kind, which continues after DELIVERED and is not reset each tick.
Coalesce readiness notifications while that cursor is active. Online ineligible participants enter
the same connection-scoped queue; offline participants await login. DELIVERED may follow receipt
durability even if some recipients remain
offline/ineligible; queued login recovery is independent of the acknowledgement.

A manual `/advancement revoke` may therefore be granted again. **Supported permanent revocation is
not designed here.** Editing only the receipt changes its immutable event/fingerprint and holds the
whole kind for mismatch; editing both authorities would require a separate audited product/recovery
procedure, not a receipt-only edit instruction.

## 4. Ordinary saves

| Store | What an ordinary save writes | Why it cannot overwrite a checked first-event commit with stale memory |
|---|---|---|
| Station registry (vanilla in-place) | Healthy acknowledged RAM, or the committedOverride with its co-committed clock. | Known commits include the outbox. UNKNOWN reasserts the pre-attempt authority (ADR-041); delivery is unavailable until validated restart. All override-sensitive gates/accessors avoid stale RAM (1.2). |
| Transfer journal (the W11b2 writer) | A capture of the whole RAM. | RAM is monotonic for the slot; generations are serialized: one active, one successor, never backwards (`RocketJournalWriter.java:47-58`). Every later generation retains it. Stop inherits its drain/final-write conditions, discards effects and does not turn a failed write into proof. |
| Progression (`AtomicSavedData`) | A checked write of RAM. | Receipts only move from 0 to 1. An ordinary save does not confirm a new per-kind receipt before the explicit directory-force barrier (3.2). |
| Rocket entity (chunk save) | The entity's RAM. | LANDED for an eligible landing exists only after the journal confirmation. A stale DESCENT entity is projected from the journal (section 2.5) and never written back to it. |
| Player advancements (vanilla) | The player's criteria. | They are never authority. Immediate or deferred login grant repairs lag, including a login before baseline or while dead/spectating. |

`setDirty()`, `ChunkDataEvent.Save` and `ItemStack.save` are never used as barriers. Awards and
DELIVERED depend only on writer confirmations and on receipt durability.

## 5. Participants

| | Moon landing | Station warp |
|---|---|---|
| Who | Real connected players riding the unique authoritative destination rocket and listed in its seat manifest (`RocketTransferEntities.aboard`, `:305-312`). | Real connected players in the Space Level whose block X and Z lie in the committed station region and who hold live VISIT access (`access.allowed(station, uuid, hasPermissions(2), VISIT)`). This is the set `station_warp` uses (`StationWarpService.java:616-626`). |
| Eligibility | `ClassicAdvancementEvents.eligible`: not a FakePlayer, not disconnected, alive, not a spectator, and the player list's current instance. Make this predicate explicitly public/shared before use by the new subpackage; package-private access does not extend to subpackages. | Same single predicate for capture and deferred grants. |
| Captured | In the completion tick, before the RAM election (section 2.3, step 5). | In the commit tick, inside the single synchronous checked call (section 1.2), so the capture is the exact completion set. |
| Bound | ≤ 16, the passenger bound. | ≤ 4,096 (ADR-066). |
| Preflight | Boarding bounds the set at 16; completion validates that bound before retaining the list, and the outbox fits the root allowance (2.1). An invalid excess fails closed; never truncate. | Only for a potentially first request. `quote` counts eligible visitors with saturation at 4,097 **before retaining a list**, checks availability and registry room, and refuses over budget with `WARP_FIRST_EVENT_OVER_BUDGET`. Commit recaptures with the same bounded count/list builder. |
| Recheck | The exact set is captured once. | `checked()` rechecks the exact set and the candidate size. Over a limit, the result is the same code, with nothing written, debited or put on cooldown. |
| Visible refusal | None exists. | The command result and the existing abort notice (W22), in EN and zh_CN. The code is a new, additive persisted identifier. |
| Empty set | The event is recorded as occurred with no recipients. | Same. |
| Joining later, rejoining, or membership | Never added. | Never added. |

## 6. Executable native/client cut inventory (not yet run)

### 6.1 Harness, gates, fixtures and oracles

Extend W11b2's runner pattern (`run_v180_rocket_journal_failures.py`), without changing its safety
restriction. The identifiers below are the complete variant inventory, not shorthand expansions.

| Code | Executable meaning |
|---|---|
| H | Headless cut gate: `arce.releaseTest.firstEventCut=<ID>`, existing `advancedrocketrycommunity.releaseTestHooks`, dedicated server, and **zero connected players** via the existing endpoint check. Inject fixed sorted UUIDs only after this gate. Never weaken it for client runs. |
| X | Real-client controlled debugger/external stop, **no H cut property**. On an isolated server process owned by the harness, a breakpoint at the named source/receipt/grant line emits ID, event UUID, thread and sequence/progress; suspend only that thread for delay cases. Stop that owned PID externally at the recorded boundary. Do not call the empty-server endpoint or add a production client halt bypass. |
| V | Pre-start semantic validation fixture. No in-game halt is needed; record the validator rejection/success boundary and process exit. Two restart attempts still apply. |
| W / S / P | Journal writer thread / server thread / pre-start migration thread respectively. Station and progression barriers are synchronous S; never issue `save-all` from a blocked W thread. |
| M | Complete isolated Moon world with one COMMITTED record, loaded safe terminal DESCENT destination and a fixed 2-UUID participant list via H. One is the synthetic winner and one later observer is outside that list. No connected players. |
| WARP | Complete isolated station world with two stations, known FE/orbit/clock/reservations and fixed eligible-region participant UUIDs via H. A1/client runs separately prove real eligibility. |
| T0 | Complete isolated real-client fixture: actual recorded Moon event/receipt with participant A and nonparticipant B, winner legitimately disassembled, `transfers=[]`. Preserve/hashes all world files. FM10a-d explicitly seed the retained root as ELECTED or DELIVERED with the identical immutable E/R(E); this state fixture is not claimed as a newly executed landing. An absent advancement file means no file; stale means valid player JSON missing this criterion. No new flight is allowed. |
| C-M / C-W | Two actual eligible clients: A aboard the unique Moon rocket, B watching without a seat; or A authorized/present at the winning station, B outside its region/without VISIT. Use actual completion, not injected UUIDs or `/advancement grant`, to produce the event. Initial player files lack the first-event criterion. |
| N | **No player-award claim.** Inspect raw root schemas, exact E/participants/fingerprint, receipt proof/ordering, DELIVERED, transfer/FE/orbit/fuel and negative no-award-before-barrier service state. UUID-only dispatch with zero recipients is not an award test. Player files are untouched. |
| A | Actual award oracle: inspect vanilla `AdvancementProgress` for `classic/one_small_step` or `classic/first_station_warp`, verify A completed and B did not, and after an explicit player save parse each player's advancement JSON for that exact criterion. Inspect before release and after recovery when the row calls for a negative/positive result. Receipt presence/trigger invocation alone cannot pass A. |

At every cut, retain the boundary marker, exact raw roots and hashes before restarting. For a
move/force boundary, identify whether directory force and server confirmation happened; a move alone
does not imply a journal acknowledgement. Every row has **two subsequent restart attempts** of the
same complete fixture, without importing a fresh world between them. Verify exact immutable E where
it was committed; where a candidate was uncommitted, no E is promised. Maintain failure injections
only in rows that explicitly say so. A successful first recovery is saved, then the second start
must be idempotent. Baseline writes on both starts are permitted/required; "stable" never means zero
physical writes. Persistent-failure rows also remove the injection after the second inspection to
prove online recovery without offline data edits.

Headless `save-all flush` variants invoke the normal save routine on S at the stated pre-DELIVERED
hook, before requesting a new acknowledgement. No save command is run while W is blocked waiting
for S. For X/A saved-player variants, the debugger invokes the existing player/advancement save
routine **in the paused S frame** before stopping, and records return plus advancement-file hash;
it does not queue a console command that cannot run on a paused server thread. The journal writer
is not suspended in these S cut cases. Unflushed variants stop immediately without that invocation.
Player save ordering is tested only by X/A rows; headless a/b rows exercise root/entity ordering,
not invented recipients. Fixture-only debugger invocation is not a new production hook.

### 6.2 Moon variants

| ID | Cut thread, barrier and gate | Fixture | Award oracle | Expected at restart 1 and restart 2 |
|---|---|---|---|---|
| FM1 | W, before election atomic move, H | M, old root saved before election | N | Initially EMPTY/no receipt. Next actual completion may elect a new E; fixed fixture reproduces the same list, not the old candidate UUID. Second start retains that new E. |
| FM2a | W, after election move+directory force, before S confirmation/LANDED, H | M, DESCENT entity chunk saved before election | N | Same E; confirm/project to LANDED, one receipt then DELIVERED; second start no new event. |
| FM2b | W, same barrier as FM2a, H | M, destination entity chunk never saved | N | Rebuild/rebind unique destination, confirm/project same E (audit terminal UUID unchanged), then stable receipt/DELIVERED. |
| FM3 | S, after receipt flush+force, before recipient dispatch, H | M | N | Same E/R(E), then DELIVERED on both starts. No login/award claim. |
| FM4a | S, after zero-recipient dispatch, before DELIVERED request; save-all completed, H | M, entity/root flush variant | N | Same E/R(E), reconcile acknowledgement once; stable second start. No player was awarded. |
| FM4b | S, same dispatch boundary, no save-all, H | M | N | Same E/R(E), lagging entity projected if necessary; stable second start. Actual award-before-save is FX-Mb. |
| FM5 | W, DELIVERED move+force confirmed on S, H | M | N | Same DELIVERED E/R(E); each startup baseline confirms it, no new receipt/winner. |
| FM6a | S, after W reports UNKNOWN, before retry, H | M, election move happened then throw/unreadable readback | N | Disk ELECTED E recovers unchanged. Before restart no award/LANDED; second start same E. |
| FM6b | S, after W reports UNKNOWN, before retry, H | M, injected failure before move but outcome unreadable | N | Disk EMPTY, no candidate UUID survives. Next checked completion elects new E; second start retains it. |
| FM7 | W, NOT_COMMITTED before election move, H | M, failure remains through both restarts | N | Both stay pending, no LANDED/receipt. Removing injection confirms a completion online; no offline repair. |
| FM8a | S, failed receipt replacement before move, H | M with source E, failure remains through both restarts | N | Both have no durable receipt/DELIVERED. Removing injection completes delivery. |
| FM8b | S, receipt move succeeded but directory force failed, before retry, H | M, new RAM receipt pending; force fault removed for restart | N | Before cut no award/DELIVERED. Valid loaded R(E) may confirm on restart and deliver; second start same E/R(E). Never demand a permanent failure after a validated reload. |
| FM9 | S, after second rocket's resumed ordinary landing, H; W delay before first election move | M plus a second descending rocket | N | During delay second rocket is paused under F3. Release W, confirm first E, then second lands ordinarily. Both restarts keep only first E and unchanged later-transfer authority. |
| FM10a | X on W, initial startup baseline before acknowledgement, external stop without release | T0, ELECTED E, absent A advancement file | A | Restart 1 again delays baseline: A logs in, defers, then release grants actual criterion without reconnect/new flight; B does not. Save A and finish receipt/ack with zero records. Restart 2 keeps E and completed A. |
| FM10b | X on W, same unacknowledged baseline cut | T0, ELECTED E, stale A advancement file | A | Same as FM10a, repairing the stale criterion in restart-1's login session and retaining unrelated criteria. |
| FM10c | X on W, same unacknowledged baseline cut | T0, DELIVERED E, absent A advancement file | A | Restart 1 delays then confirms source despite zero records; deferred login awards A, no new receipt/flight. Save A; restart 2 same E/R(E)/criterion. |
| FM10d | X on W, same unacknowledged baseline cut | T0, DELIVERED E, stale A advancement file | A | Same as FM10c; actual advancement repaired, not merely unchanged outbox/receipt. |
| FM11a | P, migration commit/startup boundary, V | Historical journal root 1 with no trusted marker | N | Root 3/EMPTY, records preserved, managed backup made; second start CURRENT/no second migration. Old jar refuses on a disposable copy. |
| FM11b | P, same migration boundary, V | Historical root 2 with COMMITTED Moon reservation | N | Root 3/EMPTY, no fabricated landing; checksum-first record recovery preserved. Second migration is a no-op; old jar refuses copy. |

### 6.3 Station variants

| ID | Cut thread, barrier and gate | Fixture | Award oracle | Expected at restart 1 and restart 2 |
|---|---|---|---|---|
| FW1 | S, after staged validation before relocation move, H | WARP | N | EMPTY, old orbit/FE/clock, no receipt; no winner unless a new actual qualifying warp is requested. Both reload old authority. |
| FW2 | S, after relocation move before publish, H | WARP | N | Disk E co-committed with orbit/debit/clock; same R(E) then DELIVERED; second start identical event/balance. |
| FW3 | S, after receipt flush+force before dispatch, H | WARP | N | Same E/R(E), then root-only acknowledgement; second start stable. No actual award claim. |
| FW4a | S, after zero-recipient dispatch before DELIVERED; save-all completed, H | WARP | N | E/R(E) survives, acknowledgement completes once, stable second start. |
| FW4b | S, same boundary without save-all, H | WARP | N | Same receipt/ack result. Player-file ordering is FX-Wb, not this synthetic case. |
| FW5 | S, after known DELIVERED publish, H | WARP | N | Same DELIVERED E/R(E), FE/orbit/reservations unchanged at both starts. |
| FW6a | S, relocation UNKNOWN then completed ordinary reassertion before halt, H | WARP, move happened/unreadable readback | N | Old EMPTY/orbit/FE reasserted, no retained candidate E. Only a later real warp can elect new E. Do not claim the unknown candidate survived. |
| FW6b | S, relocation UNKNOWN before ordinary save, H | WARP, known fixture move happened/unreadable readback | N | Disk E/relocated orbit/debit recovers, same E after both starts. This outcome is not journal UNKNOWN semantics. |
| FW7 | S, DELIVERED UNKNOWN then ordinary reassertion of ELECTED before halt, H | WARP with durable R(E) | N | Reload E/R(E), acknowledge again; second start DELIVERED same E. Receipt/idempotence is checked, not a zero-recipient award. |
| FW8a | S, preflight/root-budget refusal before replacement, H | WARP, root fixture whose exact candidate exceeds unchanged bound | N | Visible OVER_BUDGET, EMPTY and unchanged FE/orbit at both starts; no partial debit. |
| FW8b | S, saturated participant-count refusal before list retention, H | WARP, gated count 4,097 | N | Same refusal, no truncation/list overrun/debit; both reload unchanged. A1 proves the real counting predicate. |
| FW9 | S, second station's ordinary warp after first E is confirmed, H | WARP | N | Only first E retained; second warp affects its own orbit/FE but not winner. Both restart stable. |
| FW10a | P, station migration commit/startup boundary, V | Old station root 4 | N | Root 5/new epoch/EMPTY, old stations/reservations/FE preserved, backup made; second CURRENT. Old jar refuses disposable migrated copy. |
| FW10b | P, same boundary, V | Exact old-epoch root 5, valid clock, no outbox | N | One whitelisted epoch migration, clock/fields preserved, EMPTY; second CURRENT and pre-W44 jar refuses copy. |
| FW10c | P, new candidate budget validation before stage/commit, V | Legal near-4-MiB old root that cannot fit added EMPTY metadata | N | Refuse intact at both starts, original hashes/clock/FE unchanged; no enlarged budget or partial migration. |
| FW10d | P, epoch/shape validation refusal, V | New-epoch root 5 missing outbox | N | Both startups refuse; raw bytes unchanged. |
| FW10e | P, whitelist validation refusal, V | Old-epoch root 5 already containing outbox | N | Both refuse; do not classify it CURRENT or silently restamp it. |
| FW10f | P, epoch validation refusal, V | Root 5 with unknown epoch | N | Both refuse intact. Invalid old clock is an additional A0 whitelist rejection test, not silently migrated. |
| FW11 | S, root-only DELIVERED candidate after winning station deletion, H | WARP E/R(E), delete winner via normal API before acknowledgement | N | No station lookup/recreation; exact post-deletion stations/reservations/FE plus clock and DELIVERED E validate. Both starts same root event and no second winner. |

### 6.4 Availability, corruption and per-kind proof variants

| ID | Cut thread, barrier and gate | Fixture | Award oracle | Expected at restart 1 and restart 2 |
|---|---|---|---|---|
| FP1a | P, progression schema refusal, V | Future progression schema | N | Refuse twice, raw bytes unchanged. |
| FP1b | P, semantic progression refusal, V | Current schema with invalid receipt | N | Refuse twice, raw bytes unchanged. |
| FP2 | S, hold classification after startup confirmation, H | Both sources DELIVERED, progression deliberately absent in isolated complete fixture | N | RECEIPT_MISSING for both, no award/ack/election at both starts. Baseline does not rebuild receipts. |
| FP3 | S, mismatch classification, H | Valid source E and different valid receipt F | N | RECEIPT_MISMATCH both starts, immutable roots preserved, no election. |
| FP4 | S, known-EMPTY/occurred comparison after source validation, H | Isolated station rollback fixture, validated old EMPTY root with R(E) | N | SOURCE_ROLLBACK only after comparison. New warp NOT_FIRST, no new event, both starts hold. |
| FP5 | W, first baseline blocked before acknowledgement, H; release before final S cut | Current Moon ELECTED E/R(E), clean root, zero transfers | N | While blocked SOURCE_UNCONFIRMED, not rollback, no award/ack/election. Confirmation self-recovers to same E/DELIVERED on both starts without offline repair. |
| FP6 | S, unrelated journal write reports UNKNOWN after DELIVERED, H; retry restored before cut | Moon DELIVERED E/R(E), unrelated transfer mutation | N | Proof temporarily unavailable, no rollback verdict/grant/ack. Bounded retry restores exact E; both starts retain it and unrelated authority. |
| FP7a | S, unavailable progression election attempt before write, H | Known EMPTY sources/absent receipts, runtime availability fault (not malformed disk) | N | Both deny ALLOW and defer potential first travel before resources. Remove fault after second inspection: first actual completion elects once without offline repair. |
| FP7b | S, unavailable progression delivery attempt, H | Known ELECTED E/durable R(E), runtime availability fault | N | Both wait, no award/DELIVERED/new election. Remove fault: same E delivers online. |
| FP8 | S, committed station candidate publish failure; ordinary override save before halt, H | WARP, failed RAM publication, durable Moon receipt already confirmed | N | Override never exposes stale EMPTY. Every mutation/credit/clock gate refuses; save reasserts committed E. Reload yields E and normal acknowledgement. A0 separately checks other-kind confirmed proof while a new receipt force fails. |

### 6.5 Real-player grant variants

For each X row, the initial run stops at the named boundary; restart 1 exercises the specified
reconciliation and saves A's actual result; restart 2 verifies stable advancement/source/receipt.
All rows also verify B and a later joiner remain unawarded. FM10a-d are additional X/A cases.

| ID | Cut thread, barrier and gate | Fixture | Award oracle | Expected at restart 1 and restart 2 |
|---|---|---|---|---|
| FX-Ma | X on S, actual grant returned before DELIVERED; debugger player-save invocation completed before external stop | C-M, A player file now saved | A | A already complete on restart; no new toast/winner, B negative. Second start unchanged. |
| FX-Mb | X on S, same actual-grant boundary, stop before player save | C-M, saved file still lacks criterion | A | First restart repairs A through immediate/deferred login; B negative. Second start retains actual saved completion. This replaces original FX/FM4b award claim. |
| FX-Wa | X on S, actual grant before root-only DELIVERED; debugger player-save invocation completed | C-W | A | Saved A stays complete, B negative, same E/FE/orbit across both starts. |
| FX-Wb | X on S, actual grant before DELIVERED, stop before player save | C-W | A | First restart grants lagging A, second retains saved grant; no second warp/winner. |
| FX-Rm | X on S, dead-login deferral checkpoint with DELIVERED Moon E/R(E), external stop | C-M retained world, A dead at restart-1 login | A | While dead no grant; respawn in same session grants within queue bound, B negative. Second start complete; no reconnect needed for recovery. |
| FX-Rw | X on S, same dead-login deferral checkpoint, external stop | C-W retained world, A dead at login | A | Same deferred respawn result for station criterion and stable second start. |
| FX-Sm | X on S, spectator-login deferral checkpoint with DELIVERED Moon E/R(E), external stop | C-M retained world, A spectator at login | A | Spectator negative, transition to survival grants in same session within bound. Second start complete. |
| FX-Sw | X on S, same spectator-login deferral checkpoint, external stop | C-W retained world | A | Same deferred eligibility result for station criterion. |
| FX-Dm | X on W, baseline held through A login then A disconnect, stop before release | C-M retained world, stale A criterion | A | Pending entry removed on disconnect; no offline award/file mutation. On restart 1 reconnect and confirm baseline, A grants; restart 2 stable. |
| FX-Dw | X external stop after spectator A's deferred login/disconnect, before eligibility returns | C-W retained world, stale A criterion | A | Disconnect removes entry, no grant offline. Restart 1 survival reconnect grants; restart 2 stable. |

The inventory contains **58 individually named variants: 44 headless/validation and 14 real-client**,
with **116 subsequent restart attempts** (including intentional startup refusals). The old "26
cases/27 runs" denominator and implicit a/b expansions are removed. A0 rows/state-table checks and
A1 eligibility checks are additional, not included in this native/client denominator. X tests need
actual two-client logs/advancement files and recorded GPU/environment details for V2 evidence; H
tests never substitute for V1/V2. No row has been executed by W44 or W44R.

## 7. Proposed risk entry and ADR-066 note (Root edits both)

**Risk entry (new ID): world-first sources and the classic progression receipt store.**

- **Scope.** The station registry (root 5 with the new epoch), the transfer journal (root 3), the new
  progression root, and the `one_small_step` and `first_station_warp` advancements.
- **Startup refusal.** A future or invalid progression root, or a malformed outbox in either source
  root, refuses whole-world startup and keeps the raw bytes. This follows the existing managed-root
  rule, with one bounded migration diagnostic per start. It lasts until offline repair.
- **Permanent data holds.** A validated absent receipt against DELIVERED, an exact-event mismatch or
  a validated known-EMPTY source against an occurred receipt holds that kind: no award, receipt
  insertion, DELIVERED or election. One WARN per kind/reason/start. Offline repair is required;
  ordinary missing writer confirmation is never a rollback diagnosis.
- **Temporary waits.** SOURCE_UNCONFIRMED and PROGRESSION_UNAVAILABLE deny election, awards and
  DELIVERED but retain all authority. Every load schedules the zero-transfer-independent baseline;
  each source/progression retry is bounded to once per 200 ticks, with one active write and one
  journal successor. One WARN per kind/reason/start and one recovery INFO. Recovery resumes online
  after proof/availability returns; no retry limit converts a temporary wait into offline repair.
  Potentially first travel waits/refuses before resources; ordinary non-first travel can proceed
  only after source availability permits its existing adapter gates.
- **Deferred grants.** A login before confirmation or while dead/spectating retains at most two
  entries per connected participant and 4,112 total, checked at most 16 per tick and once per entry
  per 20 ticks. Receipt readiness uses at most two one-time bounded cursors. No
  historical/offline sweep or per-tick log. Respawn/eligibility change resolves it in the same
  session; disconnect/stop discards queue entries, not receipt participants.
- **Pending landing.** While the journal cannot confirm the election, the winning rocket holds its
  terminal descent pose and is not LANDED, and no other landing can win. The existing journal ERROR
  is logged once per failure streak. It lasts until the write succeeds (retried every 200 ticks) or
  a validated restart permits recovery. Inherited F3 can pause all live flights during the
  unconfirmed whole-journal write; this design does not secretly remove that global pause.
- **Warp refusal.** A first-event warp over budget is refused visibly (`WARP_FIRST_EVENT_OVER_BUDGET`),
  with no FE or orbit change.
- **Station quarantine.** An unknown DELIVERED write quarantines the station registry until restart.
  These are the existing semantics, applied to one more write per world.
- **Publish-failure hold.** In the unreachable publish-failure branch, registry mutations are refused
  until restart, and ordinary saves write the committed candidate.
- **Reduced headroom.** Station growth room shrinks by 128 KiB.
- **Downgrade.** Pre-W44 jars refuse journal root 3/new station epoch. Restoring an old JAR requires
  a compatible **consistent complete world snapshot**, not the managed-root migration backup. That
  backup preserves discovered managed roots and their `_old` files for migration auditing/repair;
  it is not sufficient to downgrade a world which has since evolved.
- **Re-grant.** A revoked world-first advancement can return at login/deferred reconciliation.
  Supported permanent revocation is not designed here; receipt-only editing causes mismatch.
- **Receipt repair to existing authority.** Stop the server, preserve a hashed complete copy of the
  original world, and work only on another copy. Validate both source roots, all related transfer/
  station authority and progression before changing anything. If an existing validated occurred
  source E is the accepted authority, repair the receipt to that exact E/immutable participant set
  and recomputed fingerprint; do not choose another winner or alter source/resource authority.
  Preserve the other receipt. Record the justification/patch, strict-validate and restart the copy
  twice, checking matching E, actual participant advancement recovery and unchanged resources.
  If the source is EMPTY/unconfirmed/inconsistent, this is not a receipt-repair case.
- **Restoring old authority.** Rollback recovery/downgrade must restore ONE consistent complete
  snapshot: all Levels' terrain/regions, entities and POI, all global authorities (journal, station,
  progression, escrow when present and every other managed root), player data/advancements, and
  level/world metadata. Keep hashes/originals, validate and restart a disposable copy twice before
  use. Neither three selected roots nor managed migration backups may be mixed with progressed
  world/player files. If no consistent snapshot or validated authority exists, stop for audited
  recovery; do not fabricate EMPTY, remove receipts or restore one authority independently.
- **Not a save veto.** No serializer throws, and no chunk or player save is refused. Inherited risks:
  the station registry's in-place ordinary save can be torn (startup is then refused, not silently
  emptied); R-032 (failures the mod cannot observe, OS crash, power loss, per-file rollback, and
  Windows directory force); R-035 (F3 journal pauses).

**ADR-066 implementation note, "W44: world-first sources" (proposed text).**
> This note preserves the owner-confirmed product decision D3. It revises the technical freeze:
> the literal station 4-to-5 wording is amended to the whitelisted pre-release epoch migration,
> and the ordinary-journal-save wording is clarified to ordered complete snapshots. Root must
> record/approve these technical clarifications and the risk entry before runtime; this proposal
> itself is neither an accepted freeze nor a risk waiver.
>
> - **Station.** The station registry root 5 gains a required `classic_first_warp` outbox and the
>   epoch `v1.8.0-station-first-warp`. The unreleased pre-W44 root 5 migrates once before start, with
>   a backup. The outbox joins `checkedRelocation`'s single candidate, and its election is decided
>   inside that call. Root-only ELECTED-to-DELIVERED shares the station writer/resolver while
>   requiring unchanged stations/reservations/FE and the sampled clock; it needs no surviving
>   winning station. Named-transition gates remain for every other update. Known-commit publish
>   failure saves the committed override and denies all mutation, never reasserts stale EMPTY.
> - **Journal.** The transfer journal gains root schema 3 with a `classic_first_moon_landing` outbox.
>   The per-record schema stays 2; roots 1/2 use an explicit migration branch, not the generic Beta
>   stamp. Every load confirms one baseline independent of transfer count. Until confirmation,
>   SOURCE_UNCONFIRMED is a temporary fail-closed state, not known EMPTY or corruption.
> - **Moon completion.** A Moon descent completion that is eligible to win is elected in journal RAM
>   and written by the existing W11b2 writer. LANDED and its notifications run only after the exact
>   confirmation. A lagging entity is projected from the journal.
> - **Progression.** The receipt store is a new managed schema-1 root, written by the synchronous
>   checked path plus a directory force. Delivery is source event, then durable receipt, then award,
>   then DELIVERED. Per-kind confirmation requires the full receipt barrier, not `onPersisted()`.
>   Every progression-unavailable path denies election. Immediate and bounded deferred login grants
>   use only immutable receipt participants after matching source proof and actual eligibility.
> - **Ordinary saves.** One ordered journal writer may persist pending monotonic RAM authority;
>   later generations retain every known committed event and effects still require exact proof.
>   This does not literally encode only the acknowledged generation or add a second writer.
> - **Bounds/history.** Participants are never compacted. Supported old roots with no trustworthy
>   marker initialize EMPTY; current roots retain their events. Migrations which cannot fit the
>   unchanged whole-root limit refuse intact, even if the old encoding was within budget.
> - **Recovery.** Receipt correction against existing validated authority is separate from restoring
>   old authority. Restoration/downgrade needs a consistent complete world snapshot, including all
>   Levels, global roots/escrow, player files and metadata; managed migration backups alone are not
>   sufficient. No supported receipt-only revocation procedure is provided.
>
> The protection scope, durations, logs and recovery are those of risk entry [new ID]. Native cut
> evidence and independent implementation review remain open.

## 8. Owner questions

No new owner product decision is requested, and D3 is not reopened. Independent round 2 review and
Root's technical ADR/risk updates still block runtime implementation. Root should expose these
three player-visible consequences:
1. **No retroactive award.** Worlds that already warped or landed on the Moon before upgrading award
   only the next real event, because no old root records a trustworthy marker. ADR-066 already
   decides this; it is listed for awareness.
2. **Revocation does not stick.** A world-first advancement revoked by an operator returns at the
   participant's next login (section 3.5). The idempotent login grant cannot tell a revocation from
   a lagging file. Permanent revocation would need a separately designed/audited product procedure
   covering immutable source and receipt authority. An operator command editing a receipt alone
   is not sufficient and is not proposed.
3. **Development worlds migrate.** Pre-W44 v1.8 development worlds with station root 5 are migrated
   once when the exact whitelist and unchanged root budget pass (section 9.1); malformed or
   near-limit unencodable fixtures refuse intact. This technical clarification touches real-client
   development worlds and needs Root's ADR update. The migration backup is not a downgrade snapshot.

## 9. ADR text that cannot be followed literally

1. **"Join C17's station registry 4 → 5 migration once"** (ADR-066 section 6.2, and ADR-065 section
   5.2.1 lines 594-596). Root 5 was frozen by W07 without the outbox, and v1.8 development worlds
   already hold it.
   - *Smallest change:* keep schema number 5, give the outbox-bearing root 5 the new epoch, and
     migrate the old-epoch pre-release root 5 once, before start, with a backup (section 1.4).
   - *Why it is safe:* no release ever contained root 5, and pre-W44 jars refuse the new epoch, so a
     development downgrade cannot silently drop the outbox. This is only an intact-refusal boundary,
     not permission to downgrade with managed files alone; section 7 requires a full snapshot.
     The whitelist must precede generic epoch/CURRENT checks and validate old clock plus new budget.
   - *Unchanged:* ADR-065's "a current root 5 lacking clock is malformed" still holds.
   - *Alternative:* root 6 is simpler to read, but it is the "second root bump" that ADR-066 forbids
     and would need an amendment.
   - *Review response:* keep R5 to minimize changes to unreleased identities, but Root must explicitly
     amend the literal migration text before runtime. It is not an unchanged 4-to-5 migration.
2. **"Extend C17's journal 2 → 3 migration once"** (ADR-066 section 6.2; ADR-065 section 4.1). No
   C17 journal 3 was ever frozen or scheduled (W12 record, lines 146-153), and snapshot schema 2
   already carries the resources. *Smallest change:* W44's root 3 is "transfer journal 3". The note
   should state that root and per-record schemas are independent. Later record-only cargo changes
   need not bump the root; a future change to root shape still requires its own root migration.
   Keep the explicit 1/2-to-3 migration and checksum-first record recovery (2.2), not merely a new
   catalog constant. This is a minimal technical clarification, not a new cargo framework.
3. **"No later warp can win…" together with the ADR-041 publish-failure reassertion.** The current
   unreachable publish-failure branch would overwrite a known commit with stale RAM. *Smallest
   change:* the `committedOverride` in section 1.2. Keep it, including every mutation/credit/clock
   gate, save and durable-view accessor; checking only `requireOperational` is insufficient.
4. **Clarifications, not changes:**
   - the Moon outbox bound is 4 KiB because the 16-passenger bound wins;
   - compaction is permitted but not used;
   - "source acknowledgment" is the state named DELIVERED here.
5. **"Ordinary saves must serialize the latest acknowledged source authority."** The inherited
   journal writer serializes ordered whole-RAM generations, which can contain pending authority.
   Keep that single writer and monotonic event snapshots; exact confirmations still guard effects.
   Root must clarify the phrase to prohibit stale overwrite rather than require a second
   acknowledged-only encoder. This changes technical wording, not D3 or its participant semantics.

## 10. Implementation shape and dependencies

| Area | New or changed |
|---|---|
| Shared | New package `progression/classic/firstevent`: `FirstEvent` with codec/fingerprint, `FirstEventOutbox`, tagged source proof, `ClassicProgressionSavedData` with per-kind confirmation, `ClassicFirstEventDelivery`, a separate bounded deferred-grant queue and status command. Expose the single existing eligibility predicate deliberately. `ClassicEvent` gains two events; the two milestones change PENDING to EVENT. Implementation runs `runData`; W44R adds no JSON/code. |
| Station | Before adding root-only delivery, extract candidate expectation/validation into `StationCheckedCandidate` and the existing replacement/readback/outcome resolver into `StationCheckedWriter`, both using the same adapter-owned authority and writer policy. No second writer/file/thread. Give the resolver an operation diagnostic context (root acknowledgement can name event ID without a live station). `StationRegistrySavedData` delegates named and root-only operations, publishes authority and owns the override. Update budget/payload/migration, warp capture/preflight and both refusal translations. Keep each class <= 800 lines; inspect any class > 500 for mixed responsibilities. No size exception is requested. |
| Journal | Extend `RocketTransferSavedData`, snapshot/checkpoint and encoder for outbox, tagged proof and unconditional startup/retry baseline. Add `rocket/server/MoonLandingCompletion`; keep the service below 800 by moving completion logic, not relying on an arbitrary five-line net change. Supply narrow callbacks/access to the existing private `flightAllowed` guard and audit operation. Recheck authoritative record/entity and current escrow/bay fence immediately before final LANDED publication; discard/defer stale continuations under the existing W11b2 guards. |
| A0 tests | Codecs/max encodings/fingerprints, exact migration whitelist and near-limit intact refusal, current-root no-op, checksum-first record recovery, every 3.3 row, zero-record baseline, unrelated UNKNOWN, all unavailable election gates, per-kind RAM-pending/flush/force/ordinary-save proof, bounded deferred queue fairness/disconnect, root-only unchanged sets/clock/substitution/station deletion, override gates/accessors and unchanged named-transition refusal coverage. Pin source caller and root schema/epoch validators without weakening assertions. |
| A1 tests | Isolated first-event fixture batch, with detached source/progression files so shared GameTest elections cannot steal another test's slot. Preserve negative precommit/cancelled/ineligible/watcher/later-joiner coverage, and actual passenger/authorized-region positives after proof. Existing negatives `!done(ONE_SMALL_STEP)` at `ClassicMilestoneAdvancementGameTests.java:311`, and `!done(FIRST_STATION_WARP)` there at `:363` and `ClassicCrystallizerAdvancementGameTests.java:60`, must be relocated/reframed with explicit EMPTY/non-electing or not-yet-confirmed fixture state; retain their behavioural negatives plus new positive world-first assertions. Do not simply remove them because PENDING changes. S1/S2 and real-client grant inventory: section 6. |
| Depends on | The W11b2 part-1 writer and continuations (merged). The open F3 applies to the election generation. There is no dependency on escrow admission, W11b3 or the bay. |

### R1-R6 review responses

| Choice | Round 2 disposition and reason |
|---|---|
| R1 | Keep normal-path station directory force and inherited outcome resolver. Explicitly best-effort on error/readback-COMMITTED: no retry or directory-durability proof there, unlike the journal/progression barriers. Changing station outcome semantics would exceed this minimal slice; inherited R-032 and Windows limitations remain open (1.2, 7). |
| R2 | Keep Moon-to-Moon hops: safe actual committed Moon rocket descent satisfies ADR-066, which imposes no Earth-departure condition (2.3). Surface visits/reservations still do not count. |
| R3 | Replace operational-only refusal with fail-closed availability/election dispositions. Unconfirmed is not EMPTY; unavailable progression never authorizes a winner (3.3). Ordinary confirmation recovery needs no offline repair. |
| R4 | Keep no compaction: only two bounded events, preserving complete immutable participants avoids a second recovery dependency; permitted compaction is optional, not required. |
| R5 | Keep exact old-epoch root-5 whitelist, with validation before CURRENT, one backed-up migration and unchanged root budget. Root must amend technical wording before runtime; no partial-root downgrade guidance (1.4, 7, 9). |
| R6 | Keep terminal-pose WAIT/no LANDED until proof and keep reservation; never cancel dismounts. State inherited F3's all-flight pause and startup/source-unconfirmed behavior explicitly, with bounded baseline/write retries (2.3-2.6). No hidden F3 performance fix. |

## Round 1 record (historical, superseded by round 2 above)

- **Done:** design only. The output is this file.
- **Commands:**
  - `git status`, `git worktree list` and `git log`: the worktree was clean on branch
    `codex/v1.8.0-w44-world-first-design` at `2d57f0d0`;
  - read-only file reads and searches, `wc -l`, and `ls` of `scripts/` and `docs/releases/`;
  - `git diff --check` before the commit.
- **Not run in round 1:** no Gradle, GameTest or native command; that author's design-only run
  provided no runtime evidence. Round 2 commands/results are recorded in `W44R.md`.
- **Round 1 choices:** R1-R6 and the original deviations; the round 2 response table supersedes
  operational-only R3 and qualifies R1/R5/R6. This historical record does not approve the revision.
- **Not done:** the independent review, the implementation, and the risk and ADR edits, which belong
  to Root.
- **Risks:** section 7.
