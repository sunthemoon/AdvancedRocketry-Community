# W148: N3-B survivor, predecessor exclusion and Design M amendment

**Proposed, 2026-10-23. Design only; not a freeze, implementation, risk acceptance or Gate result.**
Assignment: [TASK W148](../TASK-W148-N3-B-DESIGN-AMENDMENT.md). Root is Claude; this is the assigned Codex
worker's note. Source baseline is **`25242ee343851ff830e7479ca7a441eed412b045`**, on
`codex/v1.8.0-w148`. Write scope is this file only; optional validation logs go under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W148/`. No production, test, ADR, risk-register, status or ledger edits.

The recommendation is **not to ship the pre-M1 destination save by itself**. It prevents the K2 loss but makes a
previously never-saved destination available to duplicate against K6 terrain. Couple it to a durable, retained
disassembly transaction and checked native completion, including restart recovery. I7 can be implemented separately:
prove the old source absent and the current destination durable **before atomic replacement**, not remove then put.

## 1. Authority, vocabulary and present-code map

Read [W96](W96-DESIGN.md), [R34](../TASK-W98-N3-A1-SOURCE-RETURN.md), [W98](W98.md), [W116](W116.md),
[W123](W123.md), [W130 including W130F](W130.md), [Design M](W85-DESIGN.md), and R-035 in the
[risk register](../../../11-RISK-REGISTER.md). R34 supersedes W96 on reservation lifetime, census, crew,
ordinary quotas and unsupported source-return storage. It explicitly did **not** freeze N3-B.
The W96 review's R6-R8 are material: pre-saving D changes the terrain failure population; a cold predecessor needs
a bounded loading policy; I6a is test-only. Review read at
`D:/GitHub/ARCE-Task-Evidence/root-queue/W96-review/REVIEW.md`, SHA-256
`e58ca443f3cfa7cad7b208d98e0bad8fc853c14ddb26391a73158219e3e1a338`.

Development is v1.8 under [ADR-060](../../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md).
The acceptance cursor remains v1.0; v1.2/v1.3/v1.7 prerequisites and v1.8 G0-G9 are not all approved.
This design imports no upstream code or assets and adds no provenance entry.

Notation: L is a logical rocket, T its old COMMITTED transfer, S the old source, D the current landed destination,
T' the next flight's PREPARED transfer, P the RAM-only Design M request, and U an existing-format DISASSEMBLY record
in `RocketTransactionSavedData` (a different journal). D0 is an older saved destination, possibly DESCENT or absent;
D1 is its current full stationary image. Fuel, debit history, cargo snapshot, identity and manifest are separate
invariants, not inferred from a flight-state enum. Intentional, consented fuel disposal is counted separately.

All source ranges below refer to the pinned baseline, not W96's older line numbers. Prefixes are repository-relative:
`J = src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`, `S = J/rocket/server/`,
`F = J/rocket/flight/`, `P = J/rocket/persistence/`, `X = J/rocket/transaction/`, `E = J/resourceescrow/`.

| Ref | Source and what it establishes |
|---|---|
| C1 | `S/RocketLandedDisassemblies.java:194-225,250-325,344-413,459-491`: plain removal; M1 removal; M2 restoration; M3 identical write-back; P's RAM region/slot reservation. No native survivor proof at these sites. |
| C2 | `S/RocketLandedDisassemblyRules.java`, methods `restore`, `classify`, `Progress.mayWriteBack`; `S/RocketDisassemblyService.java:137-180,317-409`: 200-tick request-age check, snapshot/consent, preflight, latch and rollback classification. |
| C3 | `S/RocketTransferService.java:90-98,157-231,528-537`; `F/persistence/RocketTransferSavedData.java:398-427`: ordinary next-flight creation replaces T in one root mutation; countdown/effects follow acknowledgement. I6a's caller is the FM12a release-test hook. |
| C4 | `X/RocketDisassemblyTransaction.java:80-181`, methods `rollback`, `write`, `removeJournalBestEffort`; `S/RocketTransactionExecutor.java`, method `disassemble`; `P/RocketTransactionSavedData.java:237-290`: U phase updates/removal dirty RAM; successful disassembly removes U before native world durability is proved. |
| C5 | `S/RocketTransactionRecoveryService.java`, methods `recoverOne`, `recoverLoaded`, `recoverBlocks`, `recoverEntity`; `X/RocketRecoveryDecision.java`: DISASSEMBLY RESTORED/COMMITTED chooses blocks; earlier phases choose an existing entity, or blocks if none survived. Recovery also removes U without a native completion proof. |
| C6 | `P/RocketTransactionSavedData.java:72-156`: W123 acknowledgement sequence, digest map, checked write and absence query; UNKNOWN stays unproved until a successful capture. Encoding/loading is not acknowledgement. |
| C7 | `S/RocketDestinationRetirement.java`; `S/RocketTransferRecoveryService.java:210-230`; `S/RocketFillRetirementPacing.java`: W130/F's BEFORE_FILL entry, destination projection, old-origin census, discard checks, three ticket steps per demand. |
| C8 | `S/RocketOrdinaryRetirement.java:40-53,194-233,285-413,443-477`; `S/RocketRetirementProof.java`; `S/RocketRetirementGuards.java`: W98/F and W116 attempt lifetime, complete-slot proof, origin-column policy, nested-entity rejection, backoff and crew rules. |
| C9 | `S/RocketTransferEntities.java:159-207,276-318,593-607`: capped discovery, landed authority and cargo-aware recognition. Recognition permits a ledger-bound same-structure/new-content image; it is not permission to mutate or retire it. |
| C10 | `E/EscrowChunkStore.java:44-118`; `E/EscrowBarrierBudget.java`: retained stores on the existing workers, terrain serialization/POI flush, entity captures, watch generations and shared quotas. Only entity capture currently has an ordinary facade. |
| C11 | `S/RocketManager.java:236-250`: flights tick before transaction recovery today. A retained U therefore needs a pre-dispatch exclusion check, not merely an eventual recovery tick. |

### 1.1 The remaining windows, including their restart

These are **source-inferred at this baseline**, not native results from W148.

| Window | Writes and crash point | Restart without the amendment |
|---|---|---|
| I6a plain release | C1 `removePlain` changes T to absent; kill after the transfer file's atomic move, before saving D1 or old S's removal. This can precede the owner-thread callback and includes UNKNOWN. | No T-driven recovery: absent D is lost, D0 DESCENT can stay inert, a stale saved S can return. A second restart cannot recreate lost authority. FM12a only; not a separate player disassembly path. |
| I6b pre-restoration | C1 M1 removes T and retains only P. K2 is the same disk gap before M2. K9 is a late/refused removal; K10 is normal stop, including save-off or final-writer failure. | P disappears on restart. T, if present, still owns recovery; otherwise only D0/S's disk images remain. A normal stop is not evidence all files were saved. |
| I6c clean failure/write-back | At K3 M2 made no change, or rolled back in RAM, and M3 queued identical T; kill before its move, after a NOT_COMMITTED result, or at UNKNOWN. K4 is after its move. | K3 has the no-T exposure for as long as write-back fails. K4 restores transfer authority but does not prove native blocks/entities. Reinserting T after uncertain restoration could rebuild over blocks and remains forbidden. |
| I6 terrain transaction | After T's acknowledged absence, C4 writes U phases in RAM, restores blocks, removes D and normally removes U, with no intervening checked native completion. K5: nothing native saved; K6: some/all terrain saved, D's old slot unchanged; K11: entity removal saved, terrain skipped by the save cooldown. | With no durable U: saved D plus restored terrain duplicates; absent D plus missing terrain loses blocks, possibly the entire rocket. With U, C5 can reconcile once, but its own unproved removal exposes the same gap on a second kill/restart. A durable FAILED/RESTORING U is not automatically a safely completed rollback. |
| I7 replacement | C3 builds T' from D's current source state and replaces old T. Kill before/after the atomic move or at UNKNOWN, before old S's removal reaches its entity file. | Old T can still name both old ends; T' names D and the *new* destination, not S. T' can rebuild D with captured refuelling, but cannot exclude the forgotten S. S may be a usable pre-debit FUELED duplicate, not just an inert in-flight entity. No empty transfer-root slot is needed for this failure. |

**What later slices already close:** W98/W116 protect I1-I3, with the R34/R42 residuals in R-035. W130/F implements
the D1-plus-old-origin proof for an ordinary, pristine, empty-crew landed rocket **only on first escrow-fill demand**.
If that retirement already completed, its old T has gone safely and there is no I7 replacement of that T. It neither
routes C1/C3 through its proof nor makes a later no-T disassembly safe. W123 makes U's *file writes* checked but
does not checkpoint U before C4 mutates, retain it through native proofs, or change C5 recovery. Escrow/cargo
fences, settlement and guarded payloads are separate authorities, not general ordinary-flight repairs.

## 2. Common pre-retirement proof, without broadening authority

Introduce narrowly scoped **terminal actions** behind the existing ordinary proof coordinator: plain release,
handoff to M1, and atomic replacement. Do not implement a global `remove`/`replace` hook and do not call BEFORE_FILL
to remove T as a shortcut to I7. Share capture/census code, not fuel admission or governed authority.

1. Revalidate the actual current T, operational/acknowledged logical slot, ordinary schema 1/2 and no native transfer,
   settled/non-live destination, owner/UUID/snapshot, current resources, lifecycle, pending transaction and bay/escrow
   boundaries. No overlapping ordinary proof, disassembly or new launch for L. Freeze the actual D1, **not T's old
   destination fuel/cargo**. A cargo-aware identity match alone never admits a marked/bound/protected rocket.
   Start with W130's unprotected/pristine restriction; retain existing governed/settlement routes and reject rather
   than reinterpret an escrow-bound cargo snapshot. Extending eligibility is a separate assigned decision.
2. Use one L entry within the shared 64-entry ordinary budget. Distinguish proof ownership from a journal
   reservation. Reserve before any candidate discard or journal mutation; release on wait/failure/backoff, as W98
   does. A COMMITTED T alone does **not** block refuelling today (C3 `resourceMutationAllowed`), so an accepted
   disassembly/launch needs an explicit pending-operation check in mutation/admission callers. Do not copy the
   source-return assumption that merely marking a record settled is sufficient for D.
3. Census D's origin slot, **S's origin even when no S is loaded**, and every observed discarded candidate slot;
   deduplicate by dimension plus column, including same-Level/same-slot ends. Refresh the bounded discovery before
   each attempt and recheck the same candidates/fences before discard. Preserve W98's off-origin-column refusal
   and overflow hold; reaching the capped 64-match result is not proof of a complete census. Do not scan a world.
   A survivor slot shared with an obsolete end must contain exactly D, not be incorrectly required empty.
4. The cold old launch site is a normal case. Propose extending **W130F's demand policy**, not governed margin
   tickets: server-recorded S origin ticket plus D origin refresh, lifetime 400 ticks, at most three ticket steps
   per user demand, at least 200 ticks between ticket steps for L, at most four starts/tick shared with BEFORE_FILL.
   Read bounds/save-off/storage/authority checks before ticketing. Never load client coordinates or add margin
   tickets. A ticket requests readiness; absence is checked only after entity sections load. Queue work on the
   coordinator tick, not in a packet/loader callback. On spent demand release tickets and finish the request as
   refused with T retained; a new validated request can try again. This is an explicit applicability extension of
   R52/R59 for Root to approve, **not already authorized for I6/I7**. Loaded-only proof remains possible without it,
   but would strand a destination-only player when S is cold.
5. Capture whole slots on the owner thread through the retained original store, preserving unrelated entities;
   wait for each own-store future, force that worker, decode/read back and validate exact D1 once and no obsolete
   L in the census, including nested passengers. Hold for oversized/unknown captures, never truncate. Recheck T,
   D1, passengers, relevant observations and token-aware boundaries on the owner thread immediately before the
   terminal action. Keep W98's supersession rules; an unrelated transfer/outbox write is not an L change.
6. Submit the terminal journal mutation while its reservation is held; consume it with the existing per-L
   acknowledgement continuation. Keep the operation interlock through acknowledgement/discard handling. Before
   mutation, stale proofs mean retry without retiring T. After mutation, UNKNOWN is **not rollback permission**:
   retain the submitted candidate and wait for checked resolution/restart. Do not reinsert an older T based on
   a missing callback. Plain-release effects, launch completion and pad changes execute once at their proper ack.

The proof is bounded exclusion for ordinary flight locations, not a global UUID census. An unloaded copy moved
outside the recorded origin columns by a bypass remains W130/W98's named residual; detecting an off-column copy
holds rather than legitimizes it. No claim that arbitrary commands, third-party direct file edits or partial-world
rollback are covered. W98-R1 shared-slot contention is unchanged and still requires its separately assigned fix.

### 2.1 Passengers and current resource identity

I6 disassembly requires D's actual manifest and riding list empty and all still-pending old delivery obligations
resolved by the existing recovery/placement rules. It must not erase an offline unresolved old assignment merely
because a newly empty runtime list is convenient. W130's existing empty-manifest fill rule is not changed by this note.
I6a uses the same conservative release guard. I7 retains the actual current assigned manifest in T'; check unresolved
old deliveries using R17/R34 placement, without undoing a remembered delivery on voluntary dismount. Offline
unresolved passengers wait; do not revive W116's withdrawn inference that stationary/COUNTDOWN means never flown.

Do not add a playerdata checkpoint or a persisted cancellation-intent marker in N3-B. R34's source countdown
exception remains source-only. The online-player saved-position/fall residual and loss of RAM delivery memory on
restart remain explicit; entity durability does not prove playerdata durability. The frozen entity proof must
continue to exclude nested saved player/rocket authority, not weaken W98's validator to fit crewed I7.

## 3. Design M amendment

### 3.1 I6a, M0, M1 and M3

**I6a:** replace C1's direct `removePlain` with section 2's D1/exclusion proof and the existing checked removal.
FM12a still observes pending before acknowledged absence, retains its outbox semantics, and restores no blocks.
No new public player action is implied.

**M0:** keep actor/range/owner, identity, fuel-consent quote, registry signature, read-only terrain preflight and
duplicate-request checks. Admit proof work as DISASSEMBLY_PENDING while T remains. Recheck the snapshot and
preflight after proof; a changed consented fuel amount never causes disposal. Refusal before admission changes
no entities, fuel, T or pad claims. A pending request is not success.

**M1:** only after section 2 proves D1 and exclusion, create/transition P and remove T with `queueRemoval`, retaining
P's destination region and slot. **Retain the original 200-tick request-age check**; do not reset it after proof,
as W96 suggested. If preparation consumes it, end without mutation while T remains, or take M3 if removal has
already been submitted. A fresh request needs fresh consent. UNKNOWN/late removal never authorizes restoration.
W91's M2 exception for a definitely failed *other* record's write stays limited to that existing continuation;
it supplies no acknowledgement of U or native storage.

There is a concrete availability cost: W130F can need a second cold-site ticket step at 200 ticks, so it cannot
reliably fit proof plus M1 inside the original destructive request's lifetime. For disassembly, do that cold-site
preparation **before accepting/consuming the destructive request**: the first demand only prepares the recorded
origin and reports pending preparation; once entity sections are ready, require a fresh normal request/confirmation
and a fresh proof within its unchanged deadline. Preparation has no permission to restore blocks and uses only the
same three-step demand/400-tick ticket bounds; release on abandonment or expiry, never renew merely while waiting
for a click. This two-step interaction and its feedback need Root approval with the ticket policy. Do not promise
one-click cold disassembly, retain a consent quote beyond 200 ticks, or make a player travel to S to load it.

**M3:** retain identical T, schema-1 compatibility, insertion guards, checked write-back and one-way latch.
Before any terrain mutation, M3 is as today. After terrain mutation, a RAM rollback result alone will no longer
be sufficient: section 3.2 must prove rollback's native state and U's checked absence before writing T back.
If restoration is uncertain, U alone owns it; never reinsert T. A refused write-back leaves a durable D1 rather
than an orphaned DESCENT image, but does not fabricate a reservation or success.

### 3.2 The second transaction: retain U until native completion

**Required dependency for enabling the pre-M1 D1 save on player disassembly.** This changes the disassembly executor
and its restart recovery, not merely Design M's callback. It must also cover ordinary no-T disassembly, including
rockets already retired by W130/I6a; otherwise the unsafe route is still reachable. Assembly mutation ordering and
governed/escrow disassembly are not redesigned. Root must assign this scope explicitly.

For a no-T request, prove the current standalone D1 in its origin slot before step D1 below, with checked transfer
and transaction absence and the same bounds/eligibility guards; do not synthesize a T or an unknown predecessor.
Thus a never-saved assembled/retired rocket is not a missing rollback authority. This does not repair an earlier
assembly or historical orphan defect. The resumable executor must replace the synchronous `Request.restore()`
completion contract, and own-U checks must exclude only its exact transaction/identity from the ordinary
pending-recovery refusal. Neither a blanket "ignore pending transactions" nor two recovery owners may bypass it.

Use U's existing full snapshot, owner, physical entity ID, region, transaction type and phases. No new saved key or
schema is required for this proposal. A retained record is already recognized by old readers; older jars nevertheless
retire it unsafely, so downgrade reintroduces the defect and is not a safe recovery procedure.

| Step | Required order and authority |
|---|---|
| D0: admit | Check the existing W123 transaction absence query for L/snapshot/entity and non-overlap. If WRITE_REQUIRED, request one checked checkpoint and re-query; a RAM-empty map is insufficient. A remaining U is PENDING even when acknowledged. UNKNOWN/NOT_COMMITTED/blocked remains UNPROVED. Check capacity and support before M1 where possible; recheck after T's ack. No restoration before T's checked absence. |
| D1: durable rollback authority | After T's ack, write U as DISASSEMBLY/LOCKED with the exact D1 snapshot/owner/entity and empty target, and obtain W123's current acknowledgement containing that exact record digest. Until then change no blocks and do not remove D. D1's native image remains the rollback resource authority; U holds the footprint. If the request expires here, clear U through checked absence without restoring and take M3. |
| D2: bounded restoration | Set the restoration latch before the first mutation. Keep D frozen and present while restoring the snapshot in bounded steps; retain U in pre-RESTORED phases. No `removeJournalBestEffort` on this path. Capture/prove each affected **full terrain chunk**, including block states and accepted BE/resource payloads, through the original terrain worker with own-store completion, force, readback and supersession checks. Do not mistake a save event, dirty clear, cooldown expiry or `save-all` return for proof. |
| D3: commit block authority | Only after complete terrain proof and final live checks, write U RESTORED and obtain its exact current checked acknowledgement. The existing restart rule already selects BLOCKS for RESTORED. Once RESTORED can have been captured, do not regress U or claim a clean rollback on UNKNOWN: wait for resolution/restart. This is a second one-way decision, distinct from P's restoration-start latch. |
| D4: retire entity | With RESTORED acknowledged, remove D in RAM without drops and prove its full entity slot excludes it, including nested/duplicate identity checks. Retain U while doing so; retain/revalidate terrain proofs and the footprint exclusion. A vanilla entity-first save or a partial terrain save cannot erase the durable U. |
| D5: retire U, publish | Only when the complete native block projection and entity absence are proved and still valid, remove U and perform a checked transaction-journal write. Hold the footprint/entity interlocks until W123 proves current absence. A stale/root-mismatched acknowledgement or UNKNOWN cannot unlock it. Publish one success and fuel-disposal result, then release P/region/tickets; no successful result at RAM `execute` return. |
| DR: certified rollback | Before D3 is submitted, remove only the blocks installed by this attempt, retain D1 and U, then prove the empty target terrain plus exact D1 entity image. Remove U with checked absence **before** M3 may reinsert T. A failed removal, changed block/BE, missing survivor or unproved store is not clean rollback; retain U and hold. After D3 submission, use block-authoritative completion, never M3. |

**W123 stays the acknowledgement source.** Presence checks inspect `acknowledgedCurrent()`, the current mutation
sequence and the exact U digest in `acknowledgement().records()` (the shared acknowledgement's record map); a small
read-only predicate may wrap these existing facts. Do not add per-record acknowledgement semantics to U or treat
`checkpoint()` returning COMMITTED alone as permission for a later state. U currently uses whole-root sequencing,
unlike transfer T's per-L scheduler. Unrelated U mutations can therefore require another checked capture; preserve
that distinction and aggregate writes instead of a per-entry synchronous-write loop. Preserve sticky UNKNOWN,
non-operational-root preservation and the existing synchronous writer; no second executor or file.

**Restart is part of D1-D5, not an afterthought.** Reconstruct footprint/entity exclusion from every retained ordinary
DISASSEMBLY U before flight dispatch, entity interaction, hopper/capability access or ticking restored machines.
Do not allow C5 to mutate a transaction already owned by the live resumable executor. A pre-RESTORED U with D
selects ENTITY: remove exact restored blocks, prove empty terrain and D, then checked-remove U. RESTORED/COMMITTED
selects BLOCKS: validate the whole target, fill only missing blocks, remove matching D copies, prove terrain and
entity absence, then checked-remove U. Repeated recovery, including a kill *during recovery*, uses the same order.
If no entity survived an earlier-phase U, retain the existing BLOCKS fallback; historical partial worlds are not newly certified
by this proposal. Conflicting/unknown payloads hold without overwriting them. After restart there is no P or fuel
consent to reconstruct, and no T is invented; completing an already durable U is recovery, not a new request.
On normal stop, release only transient attempt resources; preserve the unresolved U/candidate and its native
interlocks through final saving. Never reinsert T merely because the resumable executor is stopping. Result
notifications are at most once in the live request, not a new durable exactly-once notification promise after a crash.

**Isolation is a prerequisite, not supplied by the file writer.** Until D5/DR's acknowledged absence, neither D nor
the restored footprint may be consumed, moved, broken, exploded, ticked into a new resource state or operated by
players, hoppers, pipes or other admitted adapters. Pending U must also exclude overlapping assembly, pad selection
and arrival, including after restart. An ordinary region lock today only excludes other transactions; it does not
prove these conditions. The implementation leaf must establish bounded server-owned guards for the *existing
supported block/BE set*, including reentrant neighbour/placement hooks and already acquired capabilities, and test
them. Full chunk saves remain allowed; guards affect the footprint, not unrelated contents of those chunks.

If a supported native object cannot be held safely with existing Forge hooks/adapters, **refuse before restoration**
and return the specific gap to Root; do not deploy pre-M1-only disassembly, silently narrow a whitelist, suppress
whole-Level saving, or introduce ASM/Mixin to complete this design. This dependency is the reason the terrain leaf
must be reviewed independently. A mere final readback without isolation can duplicate inventory extracted from an
unfinished snapshot. Missing guard coverage is a blocker to enabling the combined path, not an accepted residual.

## 4. I7: replacement while retaining old-end exclusion

The section 2 entry retains **old T and both old-end references** through discovery, any safe discard of S, its
absence proof and D1's proof. Neither the provisional T' nor a newly selected pad replaces that authority early.
Create/revalidate the new flight plan, fuel calculation, destination selection/claim, request ID and actual current
manifest against the frozen D1 before replacement; on a changed input discard this request/proof and re-authorize,
not launch using an old quote. Hold the new pad only under existing reservation rules and release it exactly once
on a pre-replacement refusal. The old pad/reservation belongs to T until replacement is acknowledged.

Perform **one `journal.replace(T.id, T')`**, then its existing per-L `afterAcknowledged` continuation. Never remove T,
acknowledge an empty slot, and then put T'. T' captures D1 as its source, including actual post-landing fuel/debits
and the current snapshot; ordinary fuel debit remains the existing calculation, not an extra proof-stage debit.
No countdown movement, success feedback, old-membership cleanup or old-pad release before T' acknowledgement.
The successor and old proof share an explicit per-L pending-operation owner, rather than two independent helpers
that can retire/rebind each other's record. Final token-aware guards must recognize their own reservation.

At a kill before replacement, old T + D1 + absent S recovers D. At/after the move or UNKNOWN, disk has old T or T';
both have a durable source/survivor and S already absent. If new source return recovery is selected it still passes
W98/W116 before retiring T'. No callback is needed to prove the missing old S. A failed/rejected replacement restores
old T in RAM only where the existing atomic API guarantees no replacement was submitted; UNKNOWN never does.

When no old T exists (for example after W130's completed retirement), use the existing fresh-launch path, not a
synthetic old transfer. Do not weaken cargo/escrow guards to extend this amendment to governed records. Preserve
T' failure recovery, named-pad occupancy and first-Moon outbox behaviour.

## 5. Bounds, waits and proposed risk-register input

**No numeric limit, timeout, schema, lease, network protocol or checksum change is proposed.** Keep source-site W98
backoff unchanged: BUSY/superseding save retry at most once per real next barrier tick; IO/force/bounds/veto/deadline
200 ticks; cheap crew/unloaded/save-off polls 20 ticks without a reservation. Attempts last at most 100 ticks, four
active, 64 entries shared, `MAX_SLOTS = 1 + 2 * 64 = 129` (a ceiling, not a complete-census claim). Destination ticket
steps retain W130F's 200-tick spacing and three-per-demand cap; cheap proof retry cannot replenish ticket demand.
The disassembly request's 200-tick age is not extended, nor is it a timeout that releases uncertain authority.

Keep shared storage ceilings: one capture/server tick, 32 pending, 16/type/Level/20 ticks, 64 Levels, ordinary
sub-quota eight pending and four/Level/batch, 4 MiB/decoded slot, 16 MiB capture bytes, 128 watches and existing
depth/node limits. D2 needs a narrow ordinary **terrain** capture facade using C10, no fabricated escrow endpoint,
ledger, additional worker or governed checkpoint labels. Charge terrain and entity work together against the
ordinary sub-quota and each existing per-type/global cap; this adds a consumer, not headroom. Require a final proof
set within the existing watch/capture bounds; oversize footprints wait/refuse rather than drop chunks or raise limits.
W98-R1's known same-slot invalidation remains observable; no governed-admission claim is made.

Bound world work using the existing 2,048-block snapshot/32,768-volume limits and a shared
`RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK` (256) cursor for new multi-tick terrain inspection/mutation, rather than
256 per entry. Keep transaction recovery at one selected transaction per tick; counts include rollback/recovery.
The U journal still has at most 256 entries. Its synchronous checkpoint is bounded by the existing root envelope;
aggregate at most one requested checkpoint per owner tick and measure worst-case tick cost. A proof timeout releases
attempt tokens/watches, **not U, terrain exclusion or restoration authority**. Retrying cannot bypass guards or quotas.

### 5.1 Player/operator contract (proposal for Root's R-035/ADR text)

Existing diagnostics are kept; names marked **new** below are proposed reason/site fields, not implemented messages.
No new save veto, quarantine, truncation or data discard is proposed, and no exception is a normal save-control result.
Native captures write full affected slots/chunks (with unrelated contents preserved); gameplay exclusion is narrower.

| Condition | Visible result, scope and duration | Log volume and recovery |
|---|---|---|
| Invalid actor/owner/range, stale consent, forbidden bay/escrow state, capacity or unsupported schema | Existing refusal; no mutation or new ticket. One request/L; ordinary protection already applying remains. | Existing refusal/audit. Correct input/ownership or use the owning recovery service; do not bypass fences. |
| Pre-retirement proof or cold S pending | Launch JOURNAL_PENDING; disassembly DISASSEMBLY_PENDING; FM12a remains pending/false. D stays stationary. Only L's accepted operation and relevant pad are held; related mutation rejects while pending. | Existing `ARCE_TRANSFER_RETIREMENT_WAIT/RETRY/HELD`, with **new sites** for I6/I7, once per L/reason transition, one PROVED on success. No per-tick logging. Demand spent returns existing journal/region refusal, releases only its own tickets and transient operation, retains T; retry with a new authorized request. |
| Save-off, unsupported storage, unloaded/oversize census, unresolved crew, conflict | T retained; no R34 R5(c) unproved fallback for these new destination sites. No finite completion guarantee. Disassembly expires without starting at its original deadline; I7 can finish a refused demand rather than hold a reservation forever. The operation remains unavailable while the cause remains. | One save-off WARN/Level per episode, unsupported-storage WARN/Level/session, bounded per-L reason transitions. Save-on, supported storage, passenger reconnect or legitimate loaded evidence permits a new/retried proof. Never claim save-off was overridden. |
| Transfer removal/replacement unacknowledged | Existing per-L wait; UNKNOWN/startup-baseline failure retains the existing journal-wide pause semantics. No duplicate result, rollback or timeout release. M3 keeps P until its exact write-back ack. | Existing journal diagnostic and `ARCE_LANDED_DISASSEMBLY_UNACKNOWLEDGED` once/P/stage. Repair storage then checked retry; restart reads actual durable authority. |
| U checkpoint/native terrain/entity proof failure or save-off after D1 | DISASSEMBLY_PENDING while live; overlapping region and affected D unavailable, other rockets/regions continue subject to existing journal/global quotas. Duration unbounded until proven completion/rollback. No chunk/Level save rejection. | **New** `ARCE_DISASSEMBLY_DURABILITY_HELD transaction=... phase=... reason=...` once/U/reason transition (and once after restart); INFO once on recovery/completion. Retry IO at 200 ticks, readiness poll at 20. Repair storage/load the recorded footprint; retry through U, not T. |
| Terrain/identity/resource conflict, unsupported guard, uncertain restoration | Before mutation, refuse with T/D unchanged. After mutation, retain U and footprint exclusion; no T write-back. Do not publish fuel disposal or restored-inventory access. | Existing `ARCE_LANDED_RESERVATION_NOT_RESTORED` plus the U hold reason, not a WARN per tick. Require operator inspection; a mismatch is not permission to erase a foreign block or recopy a consumed inventory. |

On persistent conflict/corruption or historical orphan: stop, preserve originals and hashes for every Level's terrain,
entities, POI, both journals, escrow ledger, playerdata and metadata; repair a consistent whole-world copy and verify
conservation plus two restarts before replacement. Do not restore a journal file alone. A new backup of an already
inconsistent world preserves evidence, not lost data. R-032's power-loss, unobserved storage corruption and partial
rollback exclusions, R34 playerdata residual and off-origin bypass residual remain. Whole-root `clean()` consumers
can still wait during bounded attempts and existing journal failures; no claim of zero cost to other work.

## 6. Verification to implement, not results claimed here

Use current classes `RocketOrdinaryRetirementTest`, `RocketDestinationRetirementTest`, `RocketFillRetirementDemandTest`,
`RocketLandedDisassembliesTest`, `RocketLandedDisassemblyRulesTest`, `RocketLandedWriteBackJournalTest`,
`RocketTransactionProofTest` and `RocketRecoveryDecisionTest` as regressions, not as proof that the new protocol exists.
Add separate pure state-machine tests for terminal actions, U completion and restart ownership; run each new class
alone, then the full unit suite. No weakened assertions or timeout increases.

**Unit/A0 coverage:** assert the ordered writes/captures/acks of sections 2-4, including failure, UNKNOWN, supersession,
stop, late callback, reservation exhaustion and mutation-before-reserve rejection at every edge. Cover same slot,
empty S origin, 64-match saturation, census/watch/byte bounds, wrong owner/UUID/cargo/fuel/debit/manifest, unsupported
storage and save-off at *each* capture. Check retained-T/retained-U state after every refusal and no leaked token,
ticket or proof. Test unrelated T/outbox writes versus unrelated U writes, sticky UNKNOWN and exact-record presence
versus checked absence. Pin no repeated fuel debit, no consent refresh and exactly-once completion.

**GameTest/A1 coverage:** real plain-release, zero/fuelled player disassembly, clean and failed rollback, quick relaunch,
same/cross-Level and named/unnamed pads. Start with old S saved and then unloaded, with D saved DESCENT, saved LANDED,
or never saved, and D refuelled/drained after landing. Observe the real files before T retires/replaces. Test a copy
loading during proof, two concurrent operations plus an unrelated flight, copy on the same slot, off-column refusal,
storage decorator, save-off, superseding vanilla save, and governed work on both another slot and the same slot.
Keep W98-R1 visible; do not silently turn its existing failure into a new proof claim.

For U, use a footprint crossing chunks with containers, fluids, powered/ticking blocks and reentrant placement
effects. While held and after reload, try hopper/pipe extraction, already cached capabilities, menu clicks, break,
explosion, overlap assembly and competing arrival. Assert exact inventories remain inaccessible and unchanged until
checked publication, while unrelated blocks in the same chunks still operate/save. Kill/recreate the recovery owner
between native captures; verify it does not race a live executor. Exercise partial terrain rollback, late 200-tick
M2, callback exceptions/Error and M3 refusal. New logs get exact batch-scoped expectations; existing log checking stays.

### 6.1 Native S2 cut matrix

Each row is a case family; each listed boundary and applicable initial image is an independent cut, **with two
restarts**, not one example standing for the family. Pin committed source/JAR, command, fixture, all raw journal,
terrain/entity/POI/player files needed for the bounded census and hashes. Pause/kill at the actual write boundary;
do not save/flush the world to make the oracle pass. First restart permits legitimate recovery; second must remain
idempotent. For recovery-boundary variants, kill restart 1 at that boundary and use restart 2 to recover again.
Keep the original case's unmodified cut copy as evidence, and action forks separate from conservation observations.

| Family | Cuts and decisive observations on both restarts |
|---|---|
| B-PRE, for I6a, I6b and I7 separately | Before survivor/absence own store; after store before force; after readback before terminal mutation. Include S still saved in a cold old dimension, D never saved and DESCENT-only D. Before completed proof T remains; a cut that persisted S's absence must also retain a recoverable D or T. |
| B-PLAIN (I6a) | Before/after removal move, UNKNOWN, after ack before effects. Require one standalone current D, no old S within census, no lost resources and unchanged first-Moon outbox semantics. Do not label FM12a as player-disassembly evidence. |
| B-M (I6b/c) | K1, K2; clean failure K3 before write-back move and at UNKNOWN; K4; K9 late/refused removal; K10 normal stop with saving on/off and final-write failure. D1 must already be durable before T can disappear; no terrain restoration on expired/invalid consent. Old S is absent. T present/absent follows actual file, not callback state. |
| B-U-INTENT | U LOCKED before/after checked replacement and UNKNOWN, before first block; mid-restoration before and after an incremental terrain save. Durable pre-RESTORED U + saved D selects rollback-to-D, or a no-mutation abandoned request leaves D. No usable restored partial inventory may escape. |
| B-U-COMMIT | Each terrain store/force/readback; U RESTORED before/after move and UNKNOWN; D removal before/after store/force/readback; U retirement before/after move, UNKNOWN and after ack before feedback. Either recoverable U or fully proved blocks/no D must exist. Check fuel disposal only on block-authoritative completion. |
| B-TERRAIN (old K5/K6/K11) | K5 before native completion; K6 one/all footprint chunks saved while D is still on disk; K11 entity absence plus a terrain chunk in its 10-second cooldown. Include **never-saved-before-request D** as well as saved D and a two-chunk partial-save footprint. The preproof intentionally changes D's disk presence; U must prevent a usable duplicate, not merely change a loss into a duplicate. |
| B-ROLLBACK/RECOVERY | K8 rollback failure and exceptions before/after latch; rollback terrain store/force/readback; U rollback retirement/UNKNOWN; M3 afterward. Also kill restart recovery after partial repair, after endpoint proof and after U removal move. A second restart must not reopen the old native gap, resurrect D beside blocks or lose the remaining footprint. |
| B-REPLACE (I7) | T' before/after atomic move, UNKNOWN, after ack before countdown and before any new source save. Seed saved pre-debit S and unsaved/refuelled D, with named-pad and same-slot variants. Old T or T' must recover exactly one authority; no forgotten S, no stale fuel refund/debit, manifest retained. Include pre-replacement refusal and a new pad stolen/invalidated while proof waits. |
| B-HOLDS/CONTROLS | Hard stop in save-off, unsupported storage, saturated census, spent cold-site demand and unresolved-crew waits; rerun W91K N2_MOVED/N2_UNKNOWN and N3_STORE/FORCE/REMOVAL. Rerun W130/F retirement cases and applicable governed F7/F8/O3J/cargo controls without altering their oracles. Add two-client reconnect/dismount/disconnect cases; FakePlayer is not V2 and entity proof is not playerdata proof. |

At every cut separately count blocks, items, fluids, energy where present, complete snapshot content, current fuel and
debit history, passengers and both physical/logical identities. Inspect old and new ends plus U/T, not just the
survivor's state or a log label. Record supported-protocol conservation separately from documented unguarded external
edits. Findings remain findings, especially K6, unsupported storage, off-column copies and playerdata.

Future implementation verification follows WORKER-RULES: offline isolated tests, full unit suite, serialized full
build/GameTest (and runData if data changes), unchanged log checker, machine lock, C:/D: >= 10 GiB, committed-source
native runs and removal of only test-created copies. No such runtime verification was performed for this note.

## 7. Proposed leaves and decisions for Root

All leaves below are **planned**, with no implementation evidence. No delegated agent was started by W148.

| Leaf | Observable outcome and write area | Dependencies / acceptance / must not do |
|---|---|---|
| N3-B1: predecessor-aware terminal proof and I7 | Ordinary proof ports/coordinator and launch adapter retain T until D1/exclusion proof, then replace once. | Root approves destination demand/crew/refusal policy; isolated tests + B-PRE/B-REPLACE/B-HOLDS, two restarts. Preserve W98/F backoff and W130F caps; no global journal hook, source fallback change, cargo/escrow admission or terrain feature. |
| N3-B2: retained U and native completion | Ordinary disassembly executor, bounded footprint/resource exclusion, terrain ordinary facade, W123 presence predicate and restart recovery implement D0-D5/DR. | Independent persistence review before integration; A0/A1 and B-U/B-TERRAIN/B-ROLLBACK including recovery kills. Prove all admitted adapters can be held. Do not change assembly ordering, saved format, budgets, leases or governed code; do not enable a pre-M1-only player path. |
| N3-B3: I6 integration | Plain hook uses proof; Design M adds preproof then delegates to B2, preserves request age and strengthens M3's rollback certification. Also routes no-T ordinary disassembly to B2. | B1 proof support and B2 verified on committed code; B-PLAIN/B-M and full combined terrain matrix. No silent new timeout, manual orphan repair or Gate claim. I6a can be reviewed separately as test-hook work, never counted as production coverage. |
| N3-B4: combined cut/regression evidence | Harnesses/oracles and one concise implementation record for the combined source. | B1-B3 committed; full specified cuts, independent actual-diff review, guard coverage and measured tick/capture cost. No source edits disguised as fixture changes, forced saves, downgraded assertions or opening W98-R1/governed admission. |

Root decisions requested before implementation: (1) accept the terrain dependency, not pre-M1-only deployment;
(2) explicitly extend R52/R59 demand ticketing to I6/I7 within unchanged shared caps, including the separate
non-destructive cold-disassembly preparation and fresh confirmation; (3) accept destination
unsupported-storage refusal rather than R34 source fallback, and the scoped pending-operation/footprint exclusions;
(4) approve the existing-format U/checkpoint/recovery scope, including all ordinary no-T disassembly. If any would
require a new schema, unguardable supported object, increased budget or extra loader authority, return a concrete
amendment for review instead of silently changing it. ADR/risk text and release decisions remain Root/owner work.

## 8. W148 execution and verification record

- Preparation: `git status --short` was empty; branch and full HEAD matched the assignment. `git worktree list` and
  `Get-CimInstance Win32_Process` identified this worker, Root and W116F in their assigned worktrees. No foreign file,
  process, branch or lock was changed. Source inspection used `Get-Content` and `rg`; source paths were resolved
  before documenting them. The initially guessed `docs/versions/v1.8.0.md` did not exist; the actual version file is
  `docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md`. No runtime probe was needed.
- Completed scope: the source-pinned I6/I7 inventory, Design M/terrain amendment, preserved constraints, operator
  impact, test matrix and proposed leaves above. This note is the sole implementation record for this design task.
- Not completed: production implementation, test additions/execution, fresh independent review, Root decisions,
  ADR/R-035/status/ledger updates, push or merge. Existing historical results cited here were not rerun.
- Validation: `python -B scripts/validate_repository.py --require-approved-identity` passed, exit 0: **45 passed,
  zero pending, warnings or failures**. The new note was staged so the tracked-Markdown check included it.
  `git diff --check` and `git diff --cached --check` passed; `git diff --cached --stat` showed only this note.
  Validator log: `D:/GitHub/ARCE-Task-Evidence/root-queue/W148/validator-w148.log`, SHA-256
  `614892fd6cdd5945f0ff5ba5be19305cd0abde01200d2077a17f0cf8a9b8e28a`.
  The design is delivered by the local commit adding this file, with its parent/source pin above; no uncommitted
  runtime snapshot is claimed as evidence. No Gradle, GameTest or native server was started: TASK W148 explicitly
  requests source inspection and repository validation. No temporary server, export tree or heavy-JVM lock was created.
- Risk/Gates: the source-inferred windows remain in production. Isolation and restart proof are substantial
  dependencies, not existing W123 features. **The current version's complete Required Gate set is not satisfied.**
  The next action is the fresh independent review specified by TASK W148, then Root's decisions and assigned v1.8
  leaves; this note grants no authority to implement them.
