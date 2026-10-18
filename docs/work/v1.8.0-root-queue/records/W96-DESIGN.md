# W96: ordinary transfer retirement requires a durable survivor (P1-N3)

**Proposed design, 2026-10-18. Not implementation, independent review, risk acceptance or a Gate result.**
Authority: [TASK W96, Root decision R33](../TASK-W96-SOURCE-RETURN-DURABILITY-DESIGN.md).
Scope: v1.8 part 1, including ordinary flights in worlds without an escrow ledger.
Write scope: this note and [W96 report](W96.md). No production, test, ADR, risk, status or ledger edits.
The implementation and native refusal fixture belong to later assigned v1.8 work.

## 1. Baseline, evidence and invariant

All code citations below are at **`93eb3ad2`**. Entry HEAD `06953e08ff29b3ab50a1bf275308cc82e54099b2`
differs only by the W96 TASK, so inspected working-tree source has those same lines.
`J/` means `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`;
`S/` means `J/rocket/server/`, `F/` means `J/rocket/flight/`, and `E/` means `J/resourceescrow/`.
Every Java citation uses these repository-relative prefixes and inclusive line ranges.
Inputs include W86 sections 5/8, W91's R23 and landed integration, W92/W85 Design M, W90,
ADR-065 part 1 and its P1-F8 note. R23 preserves logical acknowledgement, not entity durability.

External evidence root: `D:/GitHub/ARCE-Task-Evidence/root-queue/W91K/`.
The following existing artifacts were read without modification; hashes are SHA-256.

| Artifact under that root | Hash | Observation, not a new execution |
|---|---|---|
| `native-02/N2_MOVED/results.json` | `1d1f571869902103d022e680c042c7d95e2d9951eecf97d63882c2b6090c0013` | FINDING on both restarts; B stays ASCENT at `(416.5,131.6,384.5)`. |
| `native-03/N2_UNKNOWN/results.json` | `5cf6efb88c0cb63bd7c6c4cc2f62894a5fa0fc0bb0bbaecf6fee4347082363fc` | FINDING on both restarts; B stays ASCENT at `(416.5,129.8,384.5)`. |
| `native-02/N2_GOVERNED/results.json` | `85a7399a4e64c57ae40cbfed25b8992a2c0acc72995282842161c6ac5d9ebf74` | Exact-source readback cut passes; ARMED and T remain, not an F7/F8 completion. |

For each ordinary case, `disk-0.json`, `disk-1.json` and `disk-2.json` contain two physical rockets,
one remaining transfer (A's), B's full 1,000 fuel, unchanged structure and an empty passenger manifest.
B's expected origin is `(416.5,101,384.5)`. UNKNOWN explicitly reports sequence 10, no acknowledged
sequence and `acknowledgedCurrent=false`; disk nevertheless lacks B's T. No duplicate was observed.
The cases use unnamed Moon arrivals; they prove neither named-pad release nor crewed return.
Their tested commits are `24964de8` and `44eb006b`, respectively, not this note's source baseline.

Notation: **T** = transfer record; **L** = logical rocket; **S0** = pre-return saved source (or absent);
**S1** = stationary source at its snapshot origin, full source resources, no active transfer;
**D0/D1** = older/current destination images. An entity image includes UUID, owner, logical identity,
snapshot, native resource data, fuel/debit history, manifest and pose, not only `flight.state`.

**Retirement invariant:** before disk can forget T, it must already contain the selected survivor
and the absence of its obsolete copies, or another durable authority must provide that recovery.
RAM mutation, a chunk being loaded, a save request and journal acknowledgement are four different facts.
The observation window starts at the removal's atomic move, not its owner-thread continuation.
The defect predates W91: reserve-before-mutation fixes admission, not this cross-store ordering.

## 2. Inventory: all transfer-removal/advance classes

The production search covers `remove`, `replace`, `migrateCommitted`, phase puts and their callers.
Assembly/disassembly's separate v0.5 transaction journal is not a transfer journal; its interaction
is included in I6. First-Moon outbox updates do not themselves retire a transfer.

| ID and source | Disk before / after the boundary | What restart does today |
|---|---|---|
| **I1: ordinary fail-back**, `S/RocketTransferContinuations.java:24-46`. Triggers: countdown cancellation (`S/RocketTransferService.java:240-279`), blocked/missing destination or create failure (`:639-681`), rejected publication (`:691-705`). | T + S0; RAM becomes S1, then disk becomes no T + S0 until entity save. S0 may be COUNTDOWN, ASCENT, TRANSIT, stationary, or absent. Publication failure can leave a DESTINATION_SPAWNED T although the callback passes the older PREPARED object. | With T, recovery selects from persisted presence. Without T, an old in-flight source stays inert; an absent source is lost. `S/RocketTransferRecoveryService.java:76-90,208-218` is record-driven; `J/rocket/entity/RocketEntity.java:467-484` does not resume flight. This includes both measured FINDING cuts. |
| **I2: source recovery**, `S/RocketTransferRecoveryService.java:291-348,416-437`. KEEP_SOURCE, REMOVE_DESTINATION_KEEP_SOURCE and REBUILD_SOURCE all reach removal when all passengers are online (vacuously true for empty crew). | T + S0/D0 becomes no T + the same old disk images, before return/rebuild/discard saves. Rebuild may have no saved source at all. A discarded destination or extra source may still be on disk. | T retained: retry the four-case decision. T absent: inert S0, loss if no survivor, or reappearing discarded copies with no reconciliation authority. KEEP_SOURCE applies even to COMMITTED when only source exists; this is not limited to PREPARED. |
| **I3: last passenger reconnect**, `S/RocketTransferRecoveryService.java:215-288,460-494`. | Earlier recovery kept T for offline seats. Login moves a player, then `finishSourceSettlementIfComplete` removes T when everyone is online; neither source nor player save precedes it. | Same source-image gap as I2 if S1 is unsaved; otherwise S1 survives but the record-based reconnect assignment is gone. `allPassengersOnline` proves presence, not successful placement or durable playerdata. Destination-authoritative phases do not use this cleanup. |
| **I4: launch and forward phase advances**, `S/RocketTransferService.java:178-208,571-576,690-739`; `S/RocketTransferContinuations.java:50-64`. | PREPARED is checked before countdown; DESTINATION_SPAWNED before spawn; PASSENGERS_TRANSFERRED before player moves; SOURCE_REMOVED before source discard; COMMITTED before terminal ticking. Native images can lag every step. | T still contains both snapshots/resources. `F/RocketTransferRecoveryDecision.java:16-29`: both present use the phase; source-only keeps source even after destination authority; destination-only keeps destination even in PREPARED; neither rebuilds the phase's side. This is recovery authority, not proof the forward effect reached disk. All source outcomes then inherit I2. |
| **I5: COMMITTED retained/rebound/migrated**, `S/RocketDestinationCompletion.java:121-164`; `S/RocketTransferRecoveryService.java:349-401`; `F/persistence/RocketTransferSavedData.java:350-381`. | Landed/refueled D1 and discarded S may be RAM-only; T remains. Recovery can put a destination UUID before publication; schema-1 migration retains both snapshots and COMMITTED authority. | D present: keep current entity resources, not stale journal fuel (`S/RocketTransferEntities.java:275-297,526-541`). D absent: rebuild recorded arrival; post-arrival unsaved edits are not durable. Old S alone: KEEP_SOURCE. No no-T window until I6/I7 retires T. W77 retains a rejected rebuild's acknowledged destination UUID. |
| **I6a: landed plain release**, `S/RocketLandedDisassemblies.java:184-220`; service caller `:524-525`. | COMMITTED T + D0/absent -> no T + D0/absent, before any survivor proof. | W44 FM12a test-hook path, not player disassembly. Saved DESCENT may stick; never-saved D is lost; stale S may survive. Removing the callback does not close the disk window. |
| **I6b: Design M M1/M2**, `S/RocketLandedDisassemblies.java:239-317`. | M1 removes T; P is RAM-only. M2 restores blocks only after acknowledgement. At K2: no T, old terrain, D0 or no D. | Saved LANDED D survives without T; saved DESCENT D can be inert; no saved D means loss. After restoration, old D plus saved blocks can duplicate, or unsaved blocks can be lost. These latter terrain/entity splits are the separately documented v0.5 class, not solved by a pre-M1 survivor save. |
| **I6c: M3 write-back/refusal/stop**, `S/RocketLandedDisassemblies.java:319-403,450-487`. | After clean failure or no restoration: no T until identical T is checked again; during failed/UNKNOWN write-back this gap is unbounded. After uncertain mutation M3 deliberately does not reinsert T. | K3 has I6b's no-T exposure; K4 recovers from restored COMMITTED T. Refused insert or stop can leave no T. Do not reinsert after uncertain restoration: it could rebuild over blocks. Normal stop is not proof every store was saved. |
| **I7: COMMITTED replacement by next flight**, `S/RocketTransferService.java:178-208`; `F/persistence/RocketTransferSavedData.java:398-426`. | One checked root changes old COMMITTED T to new PREPARED T, capturing current D1 as new source. No empty journal slot is serialized. Old S may still be saved; old D may be absent or pre-refuel. | New T can recover/rebuild its source at the old destination with captured resources. It no longer names the previous flight's source end. A stale old S can therefore escape its census; this is lost exclusion authority, not a simple no-T gap. Pre-replacement proof must include old S absence. |
| **I8: governed return/phase/F8**, `S/RocketTransferContinuations.java:26-27`; `E/EscrowGovernedReturn.java:90-168`; `E/EscrowGovernedTerminal.java:98-135`; `E/EscrowGovernedCleanup.java:12-48`. | Return keeps T and ARMED; destination absence is proved, source is changed, and the returned source/absence slots are proved again. F6 proofs precede F7's checked terminal ledger; only CLEANUP permits F8 removal. | Before terminal publication, exact ARMED/T and endpoint projections classify recovery. After F7, terminal/READY ledger authority survives T removal; F8 requires acknowledged absence before pruning. The W91K cut is after source readback with ARMED/T intact. It is protected by own-store/force/readback plus retained authority, **not N2's ordinary reservation**. Leave all governed semantics unchanged. |
| **I9: settlement O3J**, `E/EscrowSettlementRecovery.java:120-171`. | O2 endpoint proofs and checked O3 precede named T removals. O4 waits for per-member acknowledged journal state and retained proofs. | Durable settlement intent/ledger owns recovery and exclusion while T is absent. Keep R23's no-continuation exception and leases; do not route this through ordinary return cleanup. |

I1-I3 are the same unprotected source-retirement class. I6 has the same pre-retirement gap plus
a second, independent terrain transaction. I7 loses an old end's exclusion authority.
I4/I5 are not inherently no-authority transitions; their unsafe source-removal branches are I2.
I8/I9 already have a separate durable authority. A global `remove()` hook would conflate these classes.

For completeness, **I8's advancing writes** are F2's PREPARED put
(`E/EscrowGovernedPreparation.java:66-91`), F4's DESTINATION_SPAWNED put
(`E/EscrowGovernedDestination.java:95-118`) and F5's PASSENGERS_TRANSFERRED, SOURCE_REMOVED and
COMMITTED advances (`E/EscrowGovernedPhases.java:87-142`). F2 follows checked ARMED and precedes
countdown; its full-queue fallback deliberately retains PREPARED. F4 can survive with only F1's
saved source; it revalidates that projection and proves destination absence before publishing.
F5 acknowledges the phase before its dependent move/discard; source removal can still lag on disk.
Restart retains native T plus ARMED and revalidates exact endpoints/permits, or holds a contradiction;
it does not use the ordinary four-case fallback. F6/F7 must still prove terminal native state before F8.

## 3. Options and crash outcomes

### A. Prove the survivor's own entity store before removing T

Retain T while changing the source, serialize through its existing entity manager/store, retain the
specific store future, force the same worker, read the slot from disk and verify the exact projection.
Also prove the absence of discarded copies in their possible recorded slots; a source-only proof
does not cure I2's duplicate or I7's forgotten predecessor. Do not write a one-entity replacement list
over a slot containing unrelated entities. Reuse the storage mechanism, not escrow admission authority.

Existing mechanism: `E/EscrowChunkStore.java:43-96`, `E/EscrowEntityStore.java:24-50`,
`E/EscrowBarrier.java:98-191`, `E/EscrowSaveMonitor.java:18-35`. It has retained own futures,
superseding-save checks and bounded readback; neither `setDirty` nor `saveEverything` supplies these.
It currently has escrow lifecycle/decorator dependencies; it is **not** a drop-in ordinary return API.

| Crash | Restart outcome under A |
|---|---|
| Before/while survivor or absence stores complete | T remains; current recovery matrix chooses the persisted side. No successful durable return has been reported. |
| All proofs succeeded, removal not moved / definitely NOT_COMMITTED | T + durable S1, no obsolete copies; re-run source recovery, then retire idempotently. |
| Removal moved, callback not run, or removal UNKNOWN | Disk has T or no T; both have S1 and proven exclusion. No orphaned in-flight image and no need to replay a refund. |
| After removal acknowledged | S1 is the standalone authority. Subsequent ordinary gameplay has its ordinary save semantics. |

No new saved format. Cost: one checked journal removal, plus K entity slot serializations/stores/readbacks
and batched forces, where K is the bounded survivor/exclusion slot set, not merely one rocket tag.
Failures keep a per-L pending return. Players see a stationary but temporarily unavailable rocket and a
pending result; offline crew can extend the wait indefinitely. Passenger durability is a separate guard (section 5).

### B. Keep an existing record until durability is established

Use T itself as recovery authority throughout A. PREPARED already returns a source-only rocket and
rebuilds source when neither side exists. However **no existing phase unconditionally means return**:
destination-only chooses destination for every phase. DESTINATION_SPAWNED through COMMITTED choose
destination if both/neither exist. Failed publication already has a later phase (I1).
`F/RocketTransferRecord.java:209-214` allows only next-phase advance;
`F/persistence/RocketTransferSavedData.java:333-337` rejects phase regression and checksum rebinding.
Do not rewrite a later record to PREPARED or use remove/put to evade these checks.

Retaining the actual T needs no format change, and permits restart to re-decide **until** A completes.
It does not promise a durable cancellation intent before the source checkpoint. Keeping T for one tick,
until an autosave request, or indefinitely without observing disk is not a finite retirement protocol.
A dedicated RETURNING phase/tombstone would make return intent durable before mutation, but requires
format/version, checksum/decoder, migration/downgrade and authority-matrix changes. That is not the minimal fix.
Its extra checked write would still not prove entity absence or protect disassembly's terrain stores.

### C. Load-time repair of an in-flight rocket with no T

Possible narrow rule: only after the journal's startup baseline/operability and L's acknowledged absence,
with no pending L work, no escrow authority/fence/tombstone/marker, valid own snapshot/dimension/identity,
and an already-loaded safe origin, return that entity to its snapshot origin. Preserve its **own** fuel,
debits and structure; never infer a refund. Examine COUNTDOWN/ASCENT/TRANSIT/DESCENT explicitly.

No format change; zero journal writes, bounded owner-thread work and a later ordinary entity save.
Crash before that save repeats repair; after it the rocket is stationary. It repairs W91K's existing B
image cheaply, but cannot recover a missing entity, remove an unloaded duplicate, reconstruct missing
passenger authority, distinguish I7's obsolete source, or distinguish a stale disassembled D from live D.
A missing/corrupt/unacknowledged journal is not acknowledged absence. World rollback is not evidence of cancellation.
It also changes intentional no-record states and can present a duplicated rocket as usable.
**Do not enable C as automatic production repair.** Inspection/offline whole-world repair remains the answer
for already-orphaned saves. C plus B still cannot establish the retirement invariant; C plus A adds only
historical repair, with those ambiguities. A+B is the recommended future-operation fix.

### Cross-cutting comparison

| Concern | A+B | B alone / dedicated phase | C alone |
|---|---|---|---|
| W91/R23, N2 | Reserve L before mutation; hold through proof, consume on removal continuation. Unrelated L remains schedulable. No second acknowledgement database. | Existing T needs a pending-work lock; new phase needs a checked write and the same scheduling. | Must wait for acknowledged absence and no pending work; no inference from a dirty RAM map. |
| W92 Design M | A pre-M1 checkpoint can close K2/K3's missing/stale-D gap; removal still precedes block restoration. Does not close K5/K6/K11 terrain splits. Requires Root's explicit Design M amendment. | Keeping COMMITTED while restoring blocks is unsafe: it can rebuild a second rocket. New disassembly intent is a different design. | Can move/revive a stale D beside restored blocks; unsafe as a general remedy. |
| W90 tickets | Proofs are loaded-only. Keep ordinary origin-ticket policy; no borrowed governed warm-up/margin tickets. Unloaded required evidence means wait, not absence. | A record alone supplies no persistence/readiness proof. | Join cannot authorize arbitrary chunk activation from entity NBT. |
| Crew / multiplayer | Retain manifest/record for offline or unresolved players; persist delivery before forgetting its only authority. Per-L exclusion, no world-wide flight pause. | Retention preserves reconnect authority but may indefinitely block a flight slot. A new phase still needs passenger semantics. | No record means current login recovery has nothing to search; repair cannot safely teleport offline players. |
| Governed paths | Reuse low-level proof facilities only after compatible lifecycle integration; never route native records through ordinary rules. | Do not change governed phases/ledger or R23 exceptions. | Exclude all governed/protected endpoints, including missing-ledger repair states. |

## 4. Recommended ordering and guards

Recommend **A+B for I1-I3**, then the same pre-retirement proof for I6a/I6b and I7, subject to Root's
Design M amendment. No change to forward phase ordering, COMMITTED retention, or governed paths.
Do not declare the inventory closed if I6/I7 remain assigned but unimplemented.
The mechanism is a small server-lifetime ordinary-retirement adapter, not a second escrow ledger.

1. On the server thread, fetch the **current** T by L/transfer ID, not a stale callback argument.
   Require operational journal, L's acknowledged version, live lifecycle, ordinary schema 1/2 without
   `native_transfer`, escrow/bay permission and no overlapping retirement/disassembly work.
   Check source identity, owner, snapshot, resources and complete candidate census. For publication
   refusal, inspect the actual DESTINATION_SPAWNED record and confirm no destination was admitted.
   Unexpected phase/identity/resources means hold, not an unconditional refund from stale T.
2. Reserve capacity for bounded retirement work and `reserve(L)` **before** changing entities, T,
   pad claims, live membership or feedback. A refused reservation changes nothing (N2).
   At most one pending operation per L, 64 pending operations/server. Store the reservation in the
   operation; an asynchronous operation cannot let try-with-resources close it on method return.
3. Remove L from forward dispatch under this pending operation, retaining T and its exclusion region.
   For I1-I3, perform the existing selected source return/rebuild and discard only proven obsolete
   candidates; freeze the exact S1 projection. Preserve existing resource semantics, never add a second
   debit/refund. Remount online assigned passengers with checked placement; offline assignments remain.
   For I6/I7, reconcile obsolete sources under the existing COMMITTED recovery/fence rules before
   freezing the actual landed/current resources, not T's older arrival fuel. Do not restore blocks.
   I7 keeps this current landed entity as new source and proves old-source exclusion before replacement.
4. Establish the bounded entity-slot census: recorded source/destination discovery columns and the
   pre/post-move slots of selected/discarded entities. Deduplicate shared slots and same-Level ends.
   Require valid server-recorded bounds, loaded entity sections and unique survivor identity. At the
   match-count bound, or outside the bounded recorded area, hold for repair; never treat truncation as absence.
   No unbounded world scan. A present in-memory entity is not proof an old disk slot no longer names L.
5. Capture complete slot lists on the owner thread, validate, retain own store futures, force the original
   workers and decode/read back off-thread. Require S1 exactly once and no other L/obsolete physical ID
   in the census, including nested entities. Verify complete resource and manifest projections.
   The final owner-thread check requires valid store observations, unchanged T/selected survivor,
   bay/escrow boundary and passenger guard. No world/Entity access from the IO thread.
6. Only then remove T (or atomically replace it for I7), and consume the held reservation with
   `afterAcknowledged`. Keep the pending-operation interlock until L's **absence/replacement** is
   acknowledged. The normal per-record change sequence advances; do not call a second success callback
   just because an entity proof succeeded. Unrelated outbox/record traffic does not invalidate L's evidence.
7. On acknowledgement, recheck the relevant boundary, finish live/settled membership and pad release,
   publish one success/result and release proof watches/pending state. I6 enters unchanged M1/M2 ordering;
   start its existing 200-tick restoration deadline at actual M1 removal, not the earlier proof request.
   M3's identical write-back, uncertain-restoration latch and no-reinsert rule remain mandatory.
8. Before removal, failed capture/IO/readback, superseding relevant save, unload, changed identity,
   disconnect or stopped lifecycle keeps T. Discard stale captures and retry with bounded backoff.
   Do not keep resetting S1's timestamp or replaying remount/feedback each tick. After removal submission,
   keep internal mutation blocked through acknowledgement; a later normal save must preserve the frozen
   survivor/exclusion projection. Unexpected external edits are a held conflict, never permission to
   reinsert stale T. UNKNOWN may already mean absence; wait for checked retry or restart, not rollback.
9. Normal stop closes only this operation's tokens/watches; final journal capture retains T if removal
   was not submitted. If submitted, the native prerequisites already hold. Crash loses only scheduling.
   Restart uses existing T recovery or the already-saved standalone survivor; no persisted pending map.

**Reservation-aware guards are necessary:** `F/persistence/RocketTransferSavedData.java:462-478`
already has `actionable(L, reservation)`. `S/RocketTransferEscrowRules.java:16-35` uses the normal busy
check and would refuse an operation's own held token. Provide a narrowly scoped token-aware recheck
for this adapter; do not temporarily release the token or weaken guards for other callers.
Offline passenger waits must not prevent the adapter's own reconnect work from running (section 5).

**Storage integration:** `E/EscrowEntityStore.java:24-27` rejects an already-decorated store.
Do not independently stack an ordinary wrapper over it, create a second IOWorker, or create an escrow
ledger in ordinary worlds. Factor/share the existing retained-store/watch service at server/Level
lifetime with explicit users and shutdown; escrow validators, quotas and governed authority stay unchanged.
Unsupported third-party storage must retain T with a diagnostic, not overwrite storage or claim proof.
This shared infrastructure change requires the independent review R33 names.

**Budgets and cost:** the checked census is at most 16 columns/end (32 distinct recorded slots), plus
no unrecorded relocation; a wider legacy case is held, not silently clipped or given new tickets.
Keep at most one proof group active globally and one owner-thread capture per tick; queued L values
keep T without spinning. Preserve the existing 4 MiB decoded-slot, 16 MiB in-flight capture, 32 pending
barrier and 128 watch bounds; at most 16 entity captures/Level/20 ticks and one worker force/batch.
These are ceilings, not permission to expand existing escrow use; shared reservations must count both users.
For K slots, an uncontended empty-crew return adds K stores/readbacks and batched forces to the existing
one journal removal. Normal two-end vertical flight needs one or two origin stores plus any exclusion
census slots required by its geometry. No extra journal phase write is needed. Measure bytes and latency;
do not promise a millisecond bound. Retry transient failures no more than once/200 ticks; log once per
L/reason transition and once on recovery. A blocked proof must yield its active-group slot on backoff.
Only that L loses gameplay availability, but `clean()` consumers see outstanding work and can starve;
record that additional wait in F3's consumer timing. No timeout authorizes removal or frees authority.

## 5. Passenger and compatibility decisions

Source entity proof includes the seat manifest, **not playerdata**. Rocket persistence deliberately
excludes a player `RootVehicle` rocket (`J/gametest/RocketPassengerPersistenceGameTests.java:39-80`).
Do not undo that anti-duplication rule. Ordinary `movePassenger` ignores `startRiding`'s result
(`S/RocketTransferEntities.java:349-367`); a new guard must verify actual placement, not copy that assumption.

For I1-I3, an empty manifest can retire after entity proofs. With crew, preserve T until each assigned
player is connected/alive, correctly delivered to the survivor's Level/safe origin, and that delivery
has a checked playerdata checkpoint. No editing or synthesizing offline player files; offline means wait.
The needed narrow online-player save/readback adapter is **new work**, not something the escrow entity
barrier already proves. It must preserve the full normal player payload, verify dimension/position and
no embedded rocket, detect a newer player save/disconnect and retain T on any failed/unknown checkpoint.
Proposed optional-proof bounds: one player capture/tick, at most 16 assignments/return, 4 MiB decoded
payload, depth 64 and 262,144 nodes, counted within the shared 16 MiB in-flight budget. An oversized
player save still follows normal vanilla saving; only retirement proof is refused, with T retained.
Force/read back the normal player file without changing its format; bracket it with a per-player save
generation and keep the complete serialized payload immutable. Benchmark this new bounded adapter.
If this prerequisite is not implemented, crewed retirement remains blocked, not accepted on `allOnline`.

On restart, reconstruct pending source-return work from recovery's selected survivor before reconnect
dispatch; a source-only later-phase T must not send the reconnect to its absent destination.
Pending-return login processing must recognize its own L/token, retry placement without normal
`actionable(L)` rejecting its own reservation, and recapture changed manifest/player observations.
Disconnect before final removal keeps T; after checked delivery/removal the player can reconnect safely
at the returned source and reboard (existing board handles assigned seats, `S/RocketFlightService.java:455-465`).
Automatic post-retirement remount is not promised: no T or player RootVehicle carries that instruction.
Never cancel voluntary dismount; invalidate a pending placement observation and wait/recheck instead.
Two players and simultaneous returns must not share completions, steal seats or adopt each other's UUIDs.

I6's pre-removal checkpoint must also not forget unresolved destination passenger delivery; I7's new T
retains the currently assigned manifest. Governed cancellation has its own unmounted/aboard rule
(`E/EscrowGovernedTerminalPassengers.java:25-65`); none of the ordinary rules above changes it.
No ordinary record/rocket schema change is proposed. Existing schemas load as before; old jars still
understand saved data but reintroduce the unsafe ordering. Already-orphaned saves are not repaired by A+B.
The guarantee is process-kill S2 ordering, not power-loss, post-proof disk corruption or partial-world rollback (R-032).

## 6. Implementation leaves and tests (all planned)

| Leaf | Outcome / scope | Dependencies and verification |
|---|---|---|
| N3-A | Shared bounded retained-store service and ordinary retirement coordinator; I1-I3, including online-player checkpoint. No new persisted phase. | Independent design review and Root's risk/ADR text first. Unit state-machine/storage tests and source-return A1/S2 below. |
| N3-B | Apply pre-retirement survivor/exclusion proof to I6a/I6b and I7 without changing restoration order. | N3-A; explicit Design M amendment. Old K2 loss is replaced by saved-survivor expectation; v0.5 residuals stay visible. |
| N3-C | Native refusal/named-pad matrix and final independent actual-diff review. | N3-A/B committed; R33's release-test-only lowerable continuation limit, default 128 unchanged. Two restarts/cut. |

**Unit:** enumerate phase x persisted-presence decisions, including source-only COMMITTED and
destination-only PREPARED; distinguish recoverable authority from completed return. Pin mutation order:
reserve -> mutate -> own-store completion -> force -> readback -> retirement -> journal ack -> effects.
Inject failure/UNKNOWN at each boundary, empty slot, superseding/failed older store, stale callback T,
rebuild UUID, wrong owner/snapshot/debits, duplicate/nested census, shared slot, cap and unload.
Assert no mutation on reservation refusal/stop/abort; exactly-once token/watch release and no global busy
fallback. Exercise unrelated L/outbox writes, 64 scheduled callbacks/tick, late proof and clean-consumer wait.
Test player checkpoint failure, offline/reconnect/disconnect, placement refusal and no RootVehicle authority.
Retain existing journal, governed lease/return/terminal, landed-latch and W90 ticket tests unchanged unless
Root explicitly authorizes the documented Design M expectation changes. Run new classes alone, then full unit suite.

**GameTest:** real ordinary cancel/pad-block/create-failure/spawn-veto returns with no escrow ledger;
all I2 actions, including discarded destination/extra source and never-saved rebuild; I3's last reconnect;
two returns plus unrelated advancing flight; proof IO delay/refusal, source unload and named-pad occupancy;
I6 plain and M1/M3 with DESCENT-only/never-saved prior image; I7 with an old source still on disk and
refueled current destination. Verify blocks/items/fluids/energy/fuel/debits, UUID and manifest separately.
Two real clients are additionally required for playerdata/reconnect evidence; FakePlayer tests are not V2.
Check no new tickets for held/unloaded evidence, no modified governed ticket sets, no fresh ledger in ordinary worlds.

**Native S2 matrix:** fresh committed-source build; dedicated test-owned empty server; machine lock;
record hash, command, persisted entity-region census/journal/playerdata and two restarts for every cut.
No save after the boundary under observation; a logged callback alone is not the oracle.

| Cut family | Required cuts and oracle |
|---|---|
| I1 / W91K | Re-run N2_MOVED and N2_UNKNOWN unchanged: B must be FUELED at `(416.5,101,384.5)`, same UUID/owner/full resources, exactly one B, A unchanged, on both restarts. Also kill before own store, after store-before-force, after readback-before-removal; T must still own recovery. Cover countdown cancel and publication refusal's later T. |
| N2 admission | Lower only the gated fixture limit; cut after refusal with source/T/pad/live identity unchanged. Named-pad variants cut after move and UNKNOWN; release exactly once after acknowledgement, no early reuse while return is pending. |
| I2 | Separate KEEP_SOURCE, REMOVE_DESTINATION_KEEP_SOURCE, REBUILD_SOURCE and source-only COMMITTED fixtures. Cut after each removal move and UNKNOWN with earlier S0/absent and saved discarded D/extra source. Both restarts have one stationary source, no obsolete copy; never-saved rebuild must survive. |
| I3 / crew | Cut last reconnect after player move-before-checkpoint, after player checkpoint-before-removal, and removal move/UNKNOWN. Include online+offline and disconnect/reconnect of either of two clients. T persists until all delivery prerequisites; no lost/duplicated player or rocket and no fabricated offline save. |
| I4/I5 control | Cut each acknowledged forward phase before its effect/native save; cut rebind and schema migration. Existing four-case authority remains; any source recovery then passes I2. Cut COMMITTED with both/one/neither saved end to expose, not hide, the existing preference rule. |
| I6a/b/c | Plain removal; M1 K2; clean-failure M3 K3 before move and UNKNOWN; K4 after write-back; late/refused removal K9 and normal-stop K10. Pre-restoration cuts must retain one saved current destination, never an orphaned DESCENT or missing entity. Run old K5/K6/K11 separately: terrain/entity residuals remain findings, not N3 passes. |
| I7 | Cut replacement before/after atomic move and UNKNOWN with previous S still on disk at setup and D not yet saved/refueled. Old/new T each recover one rocket; former source absent before old authority can disappear; exact fuel/debit history preserved. |
| I8/I9 controls | Repeat governed exact-source readback cut, F7/F8 and O3J existing cuts. ARMED/terminal/settlement proofs, leases, authority and ticket behavior remain unchanged. No ordinary adapter takes over. |

The unprotected sibling paths are source-inferred until these cuts run. Their expected outcomes above
are acceptance criteria, not manufactured observations. K2/K3 expectation changes strengthen safety;
K5/K6/K11 are not weakened, erased or promoted to PASS by this proposal.

## 7. Proposed Root text and decisions

**R-035 addition (proposal; append after the existing part 1 history):**

> P1-N3（R33，待实施与独立审核）影响所有世界的普通火箭，不限于有托管账本的世界。
> W91K 的两个原生切割证明：回退删除日志已到磁盘、实体返回尚未保存时，两次重启都留下无记录的
> ASCENT 火箭。来源恢复、最后乘客重连也有同类删除窗口；着陆记录释放和下一航次替换还会失去
> 实体重建或旧副本排除权威。建议保留原记录，证明幸存者原生保存、旧副本不存在及必要的在线乘客
> 交付，再删除或替换并等待逐记录确认。无新持久化格式，不改变受管返回、F6/F7/F8 或 O3J。
> 影响范围为每个逻辑火箭；原生捕获会保存包含其他实体的完整实体区块，但不新增区块或 Level 保存否决，
> 不截断、不隔离、不丢弃玩家数据，也不以异常代替常规拒绝结果。未证明时保留日志和该火箭的操作互锁；
> 离线乘客、不可用存储、未加载或超界的证明区域可无限期等待，整体 clean 消费者也可能等待。
> 最多 64 个待处理返回，按既有存储预算执行；失败每 200 tick 至多重试一次，每个火箭/原因转换一条
> 日志，恢复一条，不逐 tick 刷屏。修复存储或加载已记录区域后重试；冲突、旧孤儿或局部文件回滚需
> 停服保留原件与哈希，在一致的整世界副本上修复并验证两次重启。不得仅补回一个日志文件。
> Design M 的预删除证明须另获明确修订；它不解决 v0.5 拆解后的地形/实体独立保存残余，不构成风险接受。

**ADR-065 W11b2 part 1 addition (proposal):**

> Add P1-N3 to the open-before-admission list, separate from N2's continuation admission. Checked journal
> retirement is not a native entity checkpoint. Ordinary source-return/recovery retirement must retain
> its actual record until its survivor, obsolete-copy absence and required passenger delivery are durable;
> only then may the normal checked removal/replacement release dependent effects. The proposed adapter
> is bounded and server-lifetime-owned, changes no saved phase/schema, and preserves R23's acknowledgement
> source. It neither creates escrow authority nor alters governed exact-source return, F6/F7/F8 or O3J.
> Closure needs the W91K findings corrected on committed code, sibling-path cuts, independent review and
> explicit handling of Design M's pre-retirement window. Existing terrain/disassembly residuals and R-032
> remain separate. This records a required correction, not an accepted implementation or Gate approval.

**Open questions, with recommended answers for Root:**

1. Scope I1 only? **No.** N3-A covers I1-I3; N3-B explicitly addresses I6/I7 or they remain named open items.
2. Existing phase or new format? **Keep actual T, no new phase.** A durable cancellation-intent guarantee
   earlier than the native checkpoint would be a separate format decision; do not silently promise it.
3. Historical orphans? **No automatic C.** Preserve evidence and use whole-world repair; A+B prevents new cases.
4. Crew? **Do not equate online with durable.** The narrow player checkpoint and its proposed optional-proof
   bounds are explicit implementation prerequisites; review and native verification precede crewed retirement.
5. W92 change? **Approve a pre-M1 survivor checkpoint explicitly**, keep removal-before-restoration and M3's
   latch. Do not reopen the entire v0.5 transaction in this leaf or claim its duplication/loss risks fixed.
6. W90 / wide legacy geometry? **No new margin tickets or enlarged bound.** Hold out-of-bound/unknown evidence
   with an actionable diagnostic. Root must accept this per-rocket availability change, not hide it as success.
7. Production protection? **No new save veto or forced ledger.** Readback/capture refusal retains T; unsupported
   storage or uncertain external edits require repair. New shared storage lifecycle must preserve governed behavior.
8. Admission? **Still closed.** R33 supplies a v1.8 correction mandate, not permission to mark any Gate passed.
