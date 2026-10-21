# W127: the W27d split plan (the bay deployment protocol in sub-leaves)

Date: 2026-10-21. Task: [TASK-W127](../TASK-W127-W27D-SPLIT-PLAN.md). Base: shared commit `64b7e5b5`, branch
`codex/v1.8.0-w127`. Implementer: a headless Claude session. This is a plan only. It changes no code, test, ADR, risk
entry, status file or ledger, and it approves no Gate. The contract is the frozen [W27A-DESIGN](W27A-DESIGN.md).

Terms are the design's. L is the logical rocket, T its PREPARED transfer record, S the source entity, S' and T' their
consumed versions, and X a satellite. D0 to D7 are the steps of section 4, R0 to R4 the recovery stages of section 4.1,
and B1 to B18 the forced-stop cuts of section 9. S2 is the design's crash model: forced writes, then the process stops.
A0 means unit tests without a world; A1 means GameTests on a real server. Every fact below was read at `64b7e5b5`. Java
paths are relative to `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`.

## 1. Inventory

| Part (design section) | Merged at `64b7e5b5` | Missing for W27d |
|---|---|---|
| The 8.1 hook | `rocket/server/RocketTransferBayRecoveryGate.java:10-64` (claim, beforeAction, endpointChanged, escrowConflict); `RocketTransferBayDispatch.java:44-111` (claimed set, handoffs, permits); final boundaries in `RocketTransferEscrowRules.java:34-60`, `RocketTransferRecoveryService.java:145-511` and `RocketTransferFences.java:119-239`; stub tests `RocketTransferBayDispatchTest` (13) and `RocketTransferBayGameTests` (3). | A production gate. `RocketRuntime.installBayRecoveryGate` (`RocketRuntime.java:68-73`) has no production caller (ADR-065 line 1346). |
| D0 and D1 checkpoint (7) | `rocket/server/RocketTransferService.java:617-624` begins TRANSIT, audits `ascent_complete` and reclassifies. | The bay call; the file has 796 lines. While T is claimed, `tickLive` returns before its ticket renewal (`:555-567`), so nothing renews T's `FLIGHT_TICKET`; the coordinator must (R1). |
| Receipt store (2) | `resourceescrow/BayReceiptStore.java:66-118` (put, remove, BUSY) and `:186-210` (blocks, holdsRocket, acknowledged); startup index and fences in `BayReceipts.java:38-53, 90-119`; the shared authority's `tryReserve` and `tryRetain`; the N5 command. | A coordinator-facing API: put and remove are package-private, used only by test hooks. A group write: the writer captures at once when idle (`BayReceiptWriter.java:83-97`), so D4's "one receipt generation" needs a store call that queues several changes before one request. The removal guard: the writer takes a `beforeReplace` guard (`:79`), but the store passes none (`BayReceiptStore.java:135-138`). |
| Registry proof (3) | `satellite/persistence/SatelliteMissionSavedData.java:125-149` (sequence, watch, acknowledgement, checkpoint with directory force); the loaded acknowledgement in `SatelliteRegistryWriter.java:53-59`; `SatelliteRegistryProof.java:46-56`; fences at `:272, 293, 303, 309, 320`. | The coordinator's private permit. The fences and `SatelliteLaunchService.java:78` refuse DEPLOYMENT_PENDING for the bay's own INTENT, so D2 cannot even revalidate; W119 added no permit. |
| Launch service (7) | `satellite/service/SatelliteLaunchService.java:60-140`: REPLAY before FRESH, revalidation inside `register`. | The bay's common physical check. DATA checks `allows` and existence but not orbitability (`:96-98`); only non-data checks it (`:111-114`). |
| Transaction journal (8, B14) | `rocket/persistence/RocketTransactionSavedData.java:81-116`: `absenceStatus` and a synchronous `checkpoint`. | The D0 caller, its wait and the all-bay rejection. |
| Transfer journal (4, 8) | The off-thread checked writer (ADR-065 lines 1329-1336); `RocketTransferSavedData.java:523-530` gives T's acknowledged record digest. | The D5 rewrite. `put` refuses a changed checksum (`:333-338`), and `RocketTransferRecord.java:202-270` cannot replace snapshots. The file has 795 lines. |
| Consumption (4, D5) | `rocket/bay/SatelliteBayRoot.java:32-60, 127` (`consumed_op`); `rocket/model/RocketSnapshotPreparation.java:23-36, 90-92` (payload-only rebuild of one block); the runtime's one ADR-065 4.1.1 budget (`rocket/cargo/RocketCargoPreparations.java:16, 61`). | A preparation that replaces up to 8 bay payloads in one pass, and S' and T' publication. The D4 row's "section 4.1.1 queue" means ADR-065 section 4.1.1; W27A-DESIGN has no such section. |
| Proofs (D6, D7, 5) | `resourceescrow/EscrowBarrier.java:127-130` (`beginOrdinary`); W98's `rocket/server/RocketOrdinaryRetirement.java:64-91` (port) and `:50` (census of 1 + 2 x 64 slots); `RocketRetirementProof`. | An altitude and TRANSIT projection (W98's is stationary only, `:82-83`), the consumed-hash validator, the group token set and the guarded removal. |
| Reconstruction (4.1, R1 to R3) | `rocket/server/RocketTransferEntities.java:159-199` (loaded-only matches, exact hash, cap 64) and `:414` (altitude position); governed sources require UNCLAIMED (`RocketGovernedSourceDispatcher.java:36-37`). | The bay-resume constructor with T's source UUID. `rebuildSource` uses the stationary state (`:61-78`, called at `RocketTransferRecoveryService.java:329, 351`) and must not be used. Scoped hash tolerance and end loading under permits. |
| Gate lock (8) | `resourceescrow/RocketEscrowGate.java:11-13, 69-71`: `idle` and `logicalLocked` equal their escrow-only forms; `resourcesIdle` calls `escrowIdle` directly (`:15-18`). | The bay part, which changes about 25 production call sites. The interlocks never cancel a dismount (`RocketEscrowInterlocks.java:47-54`). |
| Status, messages, audit (6) | `rocket/bay/SatelliteBayStatus.java:16-68` (phase NONE, reason DEPLOYMENT_UNAVAILABLE); fixed slots in `SatelliteBayMenu.java:67-95`. | Every phase and reason with EN and zh text, the runtime rejection map (at most 4,096), and the `ARCE_BAY_CLAIM` line. |
| Benchmarks, native | None. `scripts/run_v180_satellite_bay_smoke.py` (W27b's S1 harness) is a pattern. | The four benchmarks of the W27d row and `scripts/run_v180_bay_forced_stop.py`. |

A read-only check passed: `RocketTransferBayDispatchTest`, `BayReceiptStoreTest`, `SatelliteRegistryProofTest` and
`RocketTransactionProofTest` ran at `64b7e5b5` with 54 tests and no failure.

## 2. Sub-leaves

Rules for all four:
- Admission stays disabled. Only the GameTest server and the gated release-test hooks can start D0; W27e decides.
- Each is a persistence slice: a one-page design note before code and a read-only review before merge (AGENTS.md
  section 13).
- **Closure rule.** Every durable state that a sub-leaf's gated entry can create is resolved by that sub-leaf's own
  in-session and restart code. A state that only a later sub-leaf handles is refused at D0 with a runtime reason, or
  waits as a runtime-only hold that changes no record. The later sub-leaf continues it at the next start, with no
  migration or repair.
- Placement (proposal): a pure-Java domain state machine in a new package; the Forge adapter `BayTransferRecovery`
  (the design's name) in `rocket/server`, which reaches the package-private transfer helpers; `RocketManager` owns the
  coordinator and reads receipts through `EscrowRuntime`, so `resourceescrow` gains no dependency on it.

### W27d1: the coordinator, the admission side and reject-only outcomes

- **Scope.**
  - The coordinator is created at `ServerStartingEvent` after the receipt index, installs the gate, and drops its RAM
    claims at stop and on the crash path.
  - All of D0: package decoding and owner agreement (bay, package and T's owner); F5 orbitability for every kind before
    kind validation (captured station body, legacy `space` refused); replay-first validation; the neutral-wrapper rule
    (BAY_SOURCE_PROTECTED); checked T; the B14 query with one checked write; 8 bays per rocket in sorted order; 32
    steps per tick on one round-robin queue keyed by L; a bay cache per snapshot (at most 128 visits).
  - D1: INTENT, or the re-arm of a REJECTED receipt with its `op`, through `tryReserve` and `tryRetain`;
    RECEIPT_WRITE_FAILED on a definite failure; the exact candidate retried on UNKNOWN.
  - The claim is HELD from the D0 enqueue, which is one line in `tickSource` before the existing reclassification.
    While waiting, the coordinator renews only T's recorded `FLIGHT_TICKET`.
  - D2 revalidates only, through a new registry permit bound to the session, `op` and X; no public request can
    authorize itself. Every outcome is REJECTED: a refusal keeps its reason (B9), and a validation that would succeed
    becomes REJECTED with DEPLOYMENT_UNAVAILABLE, which re-arms with the same `op` once W27d2 is merged. D0 refuses at
    runtime a package whose X already exists, because its restart promotion belongs to W27d2.
  - Exits: READY_SOURCE(T, live source) at the last REJECTED acknowledgement, or at once when D0 rejected every bay or
    the INTENT write definitely failed (Q1). At restart, INTENT with X absent becomes REJECTED (INTERRUPTED) and
    RELEASED_TO_ORDINARY; a different tuple becomes REPAIR; a blocked registry holds as REGISTRY_BLOCKED. INTENT with
    an equal X cannot arise under S2 here; if found, it waits at runtime unchanged.
  - The section 8 gate lock (Q3, Q8), the status slots with EN and zh text for each reason, and the claim audit line.
  - First, the registry benchmarks: one checkpoint with 32 watched registrations, the coalesced flush and autosave,
    each at a 16 MiB registry against docs/17 P95 and P99 (Q6).
  - The native harness, with the gated cut flags and the pause marker of section 9.
- **Leaves disabled:** registration, D3 to D7, reconstruction, copies and released flight.
- **Depends on:** the merged W27b, W27c1, W27c2, W27c3 and the hook.
- **A0:** the D0 matrix, including F5 for DATA and non-data; every D1 prefix for one and eight bays and a partial
  admission; the 64/8 and 4,096/128 limits through the real authority; permit scope and refusals; each INTENT restart
  row; the claim's start and end.
- **A1:** a real rocket with one data and one solar bay waits in TRANSIT, gets REJECTED, keeps both packages byte-equal
  and continues from T to `spawnDestination`; after a restart, RELEASED_TO_ORDINARY gives the ordinary stationary
  recovery; the nine section 8.1 tests with real bays; an escrow-bound rocket refused with BAY_SOURCE_PROTECTED and its
  message, while a hand-fuelled one reaches INTENT; mount, teleport, `/kill`, `/data`, disassembly and launch refused
  while INTENT exists; passengers and pending saturation as the registered risk entry states; the N5 leak after `/kill`.
- **Native:** B1, B9, B11, B12 at D1, B13's first-tick refusals with a durable INTENT, B14, B18 with INTENT and REJECTED
  writes, B10's hold half for INTENT, and B17 without registered members.
- **Size:** 10 to 13 new classes with 2,000 to 2,600 lines, plus about 200 changed lines in about 12 existing files;
  9 to 11 unit and 2 or 3 GameTest classes with 2,000 to 2,600 lines; a harness of about 700 Python lines with about 250
  test lines; one full GameTest (about 35 minutes) and about 9 native cases of three starts each.

### W27d2: the registered half for one copy

- **Scope.**
  - D2 registers under the permit; a fresh DATA launch's initial mission is checked from the launch result (F2).
  - D3: the checkpoint, retried at most every 200 ticks, REGISTRY_UNPROVED after three failures, and never a rejection
    after a RAM registration.
  - D4: freeze the group, compute S' and T' once through the runtime's 4.1.1 budget, publish every outcome in one
    receipt generation through the new store group call, and lift the decommission fences at its exact acknowledgement.
  - D5: a payload-only preparation for up to 8 bays; S' on the entity, and T' through a new journal mutation that keeps
    the transfer ID, phase, flight data and reservations, placed outside `RocketTransferSavedData` to stay below 800
    lines; a record method that replaces only the two snapshots and the checksum.
  - D6: the survivor-slot proof at the altitude position and TRANSIT, through W98's port and the barrier's ordinary
    quota, together with T's acknowledged record digest.
  - D7: the guarded removal (the store passes the token guard to the writer). A save, join, leave or movement
    invalidates the group, and D6 repeats (B16). READY_SOURCE(T') then spawns the destination from T'.
  - Restart for one copy: R0 claims T before ordinary recovery; R1 loads only the recorded ends under a permit (at most
    16 columns per end, one group per tick); R2 finds the exact source present (a lagging COUNTDOWN or ASCENT image gets
    TRANSIT from T without side effects) or absent (the bay-resume constructor with T's source UUID at altitude); R3
    completes mixed `(S',T)` and `(S,T')`; INTENT with an equal X promotes; the restart D0 serves bays without a receipt
    but never adds a member after the freeze; online passengers are remounted by permit, offline assignments stay in
    T; an escrow conflict becomes REPAIR.
  - A recognized neutral copy other than the exact source waits at runtime (DUPLICATE_PENDING) with no record change.
    Unexpected or protected copies become REPAIR, as designed.
  - It removes W27d1's interim REJECTED (DEPLOYMENT_UNAVAILABLE) outcome and its refusal of an existing X.
- **Leaves disabled:** consumption of copies with their absence census, and released flight.
- **Depends on:** W27d1, with its registry benchmarks within budget.
- **A0:** every D2 to D7 prefix for one and eight bays; mixed REGISTERED, REJECTED and REPAIR groups; group
  immutability; action, end, hash, phase and epoch refusals; refusals of the journal rewrite.
- **A1:** real data and solar rockets, each satellite registered once and each bay consumed; decommission after
  registration with X live and absent; the source present and absent after a restart; section 8.1 test 7 with
  consumption; the D5 benchmark on 2,048 blocks and 128 payloads.
- **Native:** B2, B3, B4 and B6 for one copy (with B6's protected-copy refusal), B8, B12 at D4 and D7, B13's checkpoint
  cases, B15, B16, B17 and B18.
- **Size:** 11 to 14 new classes with 2,800 to 3,600 lines, and changes in the receipt store, the registry permit,
  snapshot preparation, the journal and the transfer record; 2,800 to 3,400 test lines; one full GameTest; about 16
  native cases, 3 to 4 hours under the lock. It is the largest. Its halves cannot merge apart without stranding
  REGISTERED receipts, so Root may review it in two passes (receipts and registry; entity and journal) before one merge.

### W27d3: copies, the absence census and offline login

- **Scope:** recognition at both recorded ends (the exact UUID first, at most 64 matches, the cap a conflict); election;
  D5 on every recognized copy with the group's hashes; scoped removal of neutral non-survivors; absence proofs across
  the bounded end regions through W98's census; quarantine of an unmatched or out-of-bound copy, with the receipt
  kept; logins during a queued retirement routed without settlement; cold-chunk variants; the bounded recovery-read
  benchmark. W27d2's DUPLICATE_PENDING claims continue at the next start.
- **Leaves disabled:** released flight. **Depends on:** W27d2.
- **A0:** the recognition and election matrix, census limits, token invalidation with copies. **A1:** copies joining at
  both ends, offline login, cold chunks.
- **Native:** B5, B6's copy variants, B7, and B3 and B4 with copies and offline passengers.
- **Size:** 5 to 7 classes with 1,200 to 1,700 lines; 1,400 to 1,800 test lines; one full GameTest; about 8 native cases.

### W27d4: released flight (only if Root keeps it, Q5)

- **Scope:** for a coherent REPAIR or REGISTRY_BLOCKED group, force `released = 1` and require its exact acknowledgement
  before the first delegated step; delegate `tickLive`, destination publication, passenger moves, source removal and
  cleanup under exact permits that check the unresolved package signatures, consumed markers, ends, snapshot IDs,
  wrapper and journal digests; finish REGISTERED consumption before any package reaches the destination; restart
  dispatch of released receipts; a released receipt with T absent held for offline repair. The dispatch already
  classifies RELEASED_FLIGHT (`RocketTransferBayDispatch.java:50-53`).
- **Depends on:** W27d2; W27d3 first is recommended, because both change the coordinator and the transfer boundaries.
- **A0:** release eligibility and permit refusals. **A1:** a coherent REPAIR group flies with its packages and stays
  locked. **Native:** B10's release half.
- **Size:** 4 to 6 classes with 1,000 to 1,500 lines; 1,200 to 1,500 test lines; one full GameTest; about 4 native cases.

The four together are about 7,000 to 9,400 main lines. The estimates scale from W27c1, W119 and W98; they are not
measurements.

## 3. Order and what each proves alone

| Order | What it proves alone | Durable states it can create, each resolved by its own code |
|---|---|---|
| 1. W27d1 | INTENT is durable before anything else; the transfer waits and resumes with its packages intact; capacity, re-arm and the interlocks hold; the registry budget passes, or the stop rule fires before any registration exists. | INTENT and REJECTED; REPAIR and the REGISTRY_BLOCKED hold at restart, which are the designed offline states. |
| 2. W27d2 | Exactly-once registration and consumption for one copy, in session and across every single-copy restart. | REGISTERED, S' and T', and retirement. A planted copy only waits at runtime. |
| 3. W27d3 | Recognized copies are consumed and proven absent; offline passengers need not connect. | No new kind of durable state. |
| 4. W27d4 | A coherent REPAIR group flies on without retiring. | `released = 1`. |

W27e then packages B1 to B18 with two restarts each on one pinned JAR, and Root and the owner decide on admission.

## 4. Risks and open questions for Root

1. **Q1, the claim before the first receipt.** Section 8.1 defines UNCLAIMED as "no INTENT, REGISTERED, REPAIR or
   released receipt names L", but the merged dispatch keeps a claimed T until READY_SOURCE or RELEASED_TO_ORDINARY
   (`RocketTransferBayDispatch.java:63-66`). The plan holds T from the D0 enqueue and through an unacknowledged INTENT,
   and ends with READY_SOURCE(T, live source) when D0 rejects every bay or the INTENT write definitely fails. Root
   confirms this before W27d1.
2. **Q2, dismounts.** Risk entry C says the mount interlock cancels dismounting; the merged code never does
   (`RocketEscrowInterlocks.java:47-54`; ADR-065 line 1393, "A dismount is never cancelled"). A passenger can therefore
   leave the waiting rocket 48 blocks up and fall, while T keeps the seat. Root chooses before W27d1: keep this and
   correct risk entry C (the passenger-exit mitigation then becomes a question of a safe position, not of permission),
   or cancel dismounts during a bay hold, which changes W11b2's interlock decision.
3. **Q3, the lock while the index is NOT_LOADED.** `holdsRocket` needs a loaded store. Failing closed would lock every
   rocket for the session when the receipt file exists but did not load, while R-039 limits NOT_LOADED to admission
   and insertion. Recommendation through W27d3: while NOT_LOADED, hold every PREPARED transfer record (only a rocket in
   flight can carry a blocking receipt) and leave landed rockets free. This stops every flight for that session.
   W27d4 must revisit it, because a released receipt locks a landed rocket. Decide before W27d1, with the risk entry.
4. **Q4, interim behaviour.** W27d1's REJECTED (DEPLOYMENT_UNAVAILABLE) outcome and its refusal of an existing X, and
   W27d2's DUPLICATE_PENDING hold, follow the closure rule but are not in the frozen design. Root accepts or replaces
   them. Each interim REJECTED receipt keeps a retained slot (128 per owner) until it is re-armed or retired by the N5
   command.
5. **Q5, released flight.** Section 8 lets a coherent REPAIR or blocked-registry group fly on, and B10 tests it. If Root
   drops it for v1.8, W27d4 disappears, every REPAIR and REGISTRY_BLOCKED group holds until offline repair, and risk
   entry C's text changes. Decide before W27d2 ends.
6. **Q6, the stop rule and synchronous writes.** If W27d1's registry benchmarks miss docs/17 P95 or P99, W27d2 cannot
   start: an off-thread registry writer needs ADR-050 section 2's follow-up ADR first. D0 can also request a
   synchronous transaction-journal checkpoint, whose maximum-size cost R-040 records as unmeasured; W27d1 times it.
7. **Q7, size ceilings and shared classes.** `RocketTransferService` (796 lines) and `RocketTransferSavedData` (795) are
   at the 800-line threshold; `RocketTransferEntities` (609), `RocketTransferRecoveryService` (564) and `EscrowRuntime`
   (481) are near or above 500. Each sub-leaf extracts before it adds: W27d1 adds one call to `RocketTransferService`,
   and W27d2 puts the journal mutation in a new class. The receipt store (W27c1), the registry (W27c2) and the journal
   (W11b2) change in W27d1 and W27d2, so those reviews must cover them.
8. **Q8, the gate lock's reach.** Section 8 adds the bay part to `idle` and `logicalLocked`. `resourcesIdle` calls
   `escrowIdle` directly (`RocketEscrowGate.java:15-18`), so it needs the bay part too, while `escrowIdle` and
   `escrowLogicalLocked` stay escrow-only for permitted boundaries. About 25 production call sites change behaviour for
   a held L. Root confirms the mapping before W27d1.
9. **Q9, the risk register.** R-039 registers the receipt store's part of risk entry C (startup refusal, capacity,
   writer unavailability), and R-038's last sentence defers the rest to "W27c1 to W27d".
   - Before W27d1 merges, because W27d1 adds the hold (AGENTS.md section 3.3), Root registers the rest of C: the TRANSIT
     wait with no time limit under retryable storage failures; the rocket and package lock; REPAIR and REGISTRY_BLOCKED
     until offline repair; the passenger consequence after Q2; loaders stopping for every player when bay holds fill
     the 64 pending slots; refused warps of overlapping stations; BAY_SOURCE_PROTECTED; the NOT_LOADED scope after Q3.
   - Before W27d2 merges, because it is the first to register and retire, Root registers risk entry D: per-file
     rollback during INTENT can make X look INTERRUPTED and register it again; REGISTERED with X absent consumes by
     design; a stale copy outside every journal can bring a package back after retirement; OS crashes are outside S2.
   - Before W27d4 merges, C gains the released receipt's lock after a completed flight.
   - Before W27e: the neutral-source limit (N3), the passenger-exit permit and the per-owner pending limit (N4).
10. **Q10, W126.** [W126-DESIGN](W126-DESIGN.md) retires only a landed, COMMITTED T and requires the bay to be UNCLAIMED;
    a bay claim needs a PREPARED T. The two do not conflict.
