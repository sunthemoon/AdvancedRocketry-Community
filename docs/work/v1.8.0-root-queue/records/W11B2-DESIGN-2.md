# W11b2 design 2: settlement successors and credited-world reconciliation

## Revision 1

Date: 2026-10-14. Assignment: `TASK-W11B2-DESIGN-2R.md`, starting at `88be104b`.
The complete design-2 review `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-D2-review/REVIEW.md` was read and its SHA-256 verified:
`658fdf2a468c3eec165ee6a3a8efd0fc75b919e6ebb55ebc7971c088d56b9b81`.
Its H/M/L IDs below are distinct from the settlement review's F/N IDs. This revision
supersedes the original migration and source-reconstruction proposals, not historical evidence.

| Finding | Changed lines in this revision | Disposition |
|---|---|---|
| H1 | L43-L67, L259-L267 | Separate bounded ARMED entries cover every bound rocket; READY heads remain receipt-backed. |
| H2 | L56, L69-L71, L260-L261, L275-L285 | Choose F1 source checkpoint/force/readback before ARMED; specify board/refuel stop cuts. |
| M1 | L199-L217, L354 | Transfer root stays schema 2; only governed records use schema 3, independent of flight schema. |
| M2 | L104, L136-L141, L173, L353 | Replace unreleased escrow definitions directly; refuse/preserve pre-change development data, with no migration chain. |
| M3 | L186-L197, L262, L359 | Missing credited sources are REPAIR; source reconstruction belongs to W27f only. |
| L1 | L53, L116, L133, L171, L296 | Clarify the operation-UUID anchor, pruning order, typed observations/floors and W27a D5 projection. |
| L2 | L334, L357 | Name a console-origin implementation check and function/RCON refusal tests. |
| L3 | L339, L356 | One accelerated retry per operation per 200 ticks, with pending-attempt reporting. |
| L4 | L143-L160 | Explicit implementation prerequisite: field-by-field encoded-size arithmetic and pre-L1 settlement-space reservation. |
| L5 | L219-L226, L304-L308 | Bound transition hashing, 100/400/1,200-tick proof backoff and successor-hold logging. |
| L6 | L309-L315, L355 | Coalesce ledger updates under existing writer cadence; benchmark eight governed transfers. |
| L7 | L352-L359 | Expand proposed R-035 availability, downgrade, development-refusal and actor restrictions. |
| L8 | L67, L78-L80, L260 | Permit-gate cancel/fail-back; refuse a client T already named by protected history/journal before arming. |
| L9 | L84-L92 | Both settled and ordinary-L2 cases finish two flights, refuelling the successor between them. |

Date: 2026-10-14. Status: proposed contract for Root and independent review, not implementation,
risk acceptance or Gate evidence. Root is Claude; this is the assigned Codex design worker.
Base: `60343d82`, with executable source `9b40863c` and continuation 08's blocked result at
`e66360b0`. The only write scope is this note. Production admission remains disabled.

Inputs: [W11b2 continuation 08](W11B2.md#continuation-08),
[W11a sections 1, 3-7](W11A-DESIGN.md), [W11b2 sections 2, 5-8](W11B2-DESIGN.md),
[W27a sections 4.1 and 8](W27A-DESIGN.md), and
[ADR-065 sections 4, 8 and 10](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md).
The settlement review is `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-settle-review/REVIEW.md`;
its verified SHA-256 is `9ec5c86fa0ed873444ac90668f86fcdc14dee470ffadd70d2d514252f667ba08`.
Finding numbers below refer to that review, not the earlier part-1 review.

## 1. Fresh bound-rocket launch after release: N1 and F1

**Decision:** every ledger-bound rocket, including ordinary acknowledged L1/L2 releases without a receipt,
needs explicit, durable launch authority after release. Use a bounded ARMED table for flights,
separate from receipt-backed READY identity heads; never create permanent heads for ordinary L2.
Receipt history alone is neither permission nor a reason to reject that launch. Ordinary protected-source
rollback remains forbidden. A transfer ID, game time, live-set membership or caller-supplied hash is
never the proof.

| Item | Frozen rule |
|---|---|
| Release anchor | O4 stores the decision/group ID, immutable decision digest and `release_generation` in the permanent receipt. The server may use it only from an exact acknowledged ledger snapshot. After a stop between replacement and callback, checked startup readback/baseline acknowledgement supplies that prerequisite; merely decoding a staged candidate does not. |
| Bounded READY heads | The receipt of the lexicographically smallest member operation UUID anchors the head for logical rocket L; the other members reference it. Store L, owner, anchor/release generation, current physical UUID, bound resource floor, signature kind/signature and invariant-envelope digest, issued/completed serial and outcome in `rocket_authorities`: at most 64 heads, at most 1 KiB each inside the 1 MiB root. A head needs a permanent receipt and consumes no extra settlement slot. Its positive election is suspended while L has an active ARMED entry. No pending flight payload or append-only identity history lives in a head. |
| Bounded ARMED table | `armed_flights` contains at most `RocketFlightLimits.MAX_ACTIVE_TRANSFERS` (64) entries, at most 1 KiB each inside the same root budget, one active entry per L. Each binds ledger/L/owner, source UUID/binding/epoch, T, server-issued serial, arm generation, launch/source/destination digests, fixed destination UUID, checked journal guard, optional READY anchor/release generation and outcome. Full envelopes live in the governed journal, not this table. Reserve count and worst-case bytes before F1; terminal entries awaiting cleanup still count. No receipt is required, and no ledger is created for neutral unrelated flight. |
| Repeated settlement | There is one effective head per L. O3 names its predecessor; O4 atomically supersedes that head with the new released group's head, carrying forward issued/completed serial watermarks. Older receipts still protect their missing identities/retired transfers but do not impose their old survivor as the current positive election. A missing-rocket/floor-abandonment receipt retires L and supplies no successor head. |
| Launch proof | After exact O4 release or ordinary L2/C8 release, prune retired markers, freeze E/E' and lock the source against manifest/resource/position changes. F1 must own-store, force and stable-readback the complete source at E, including boarding/refuelling and its pruned wrapper. Keep its token and checked journal lease valid at ARMED capture and replacement. Acknowledge ARMED with a fresh server serial; only then acknowledge that exact schema-3 PREPARED record and start flight. A receipt-backed entry additionally binds its current head/release anchor; an ordinary bound entry uses its validated binding and checked idle journal, not an invented receipt. |
| Lifetime and pruning | A root-wide nondecreasing `issued_launch_serial` supplies unique serials even without a head; issued serials are never reused, including cancellations, and overflow refuses before mutation. An entry progresses ARMED to a checked HANDED_OFF/CANCELLED outcome at F7. F8 first acknowledges journal removal or exact replacement proving old T absent, then acknowledges ledger pruning of only that terminal entry. Restart resumes these prerequisites from checked baselines; UNKNOWN never permits pruning. A held entry is not evicted to admit another flight. READY heads survive and advance only where receipts require elections. |
| Identity rule | `identityLocked`/`stateLocked` use the exact active ARMED entry and checked record for every bound flight. A receipt-backed L also requires its READY anchor; without an active entry its current head is the positive election. Ordinary bound rockets need no permanent election table after F8, but retain binding, floor and duplicate protections. A successful receipt-backed handoff replaces the head identity rather than appending UUIDs to old receipts. Missing/contradictory authority is a hold, never evidence of a fresh launch. |
| Ordinary interlocks | Active ARMED locks ordinary resource mutation, another launch, disassembly, kill and external movement/mount requests. Only the exact flight action permit passes this lock; escrow/transaction/bay locks still refuse. Terminal entries authorize cleanup only and keep a new launch waiting unless it reserves a separate slot for the checked COMMITTED-predecessor replacement in F2. Do not make the entity globally idle to run a flight. |
| Destination wrapper | Every bound flight creates its destination bound to the same ledger, at independent epoch 0 with no inherited markers; prove that frontier at F6. A receipt-backed flight also installs the destination as its READY identity at F7. Neutral unrelated transfers retain schema-1/2 behavior and create neither ARMED nor a head. |

| Boundary | Required use of the proof |
|---|---|
| `RocketTransferFences.classify` / `RocketEscrowGate.idle` | Run bay dispatch first, as today. Then consult the acknowledged ARMED entry plus exact checked record before tickets, and require a READY anchor only for receipt-backed L. All bound launches enter the successor dispatcher, not the legacy floor-recovery election. Active ARMED makes ordinary idle false. Settling/pending operations, exact-T floors and invalid/foreign wrappers still block; history without the proof remains fail-closed. Set membership is only a classification result. |
| `EscrowRecoveryFence.reason` | Receive validated action-specific authority, not a boolean `fresh`. Keep legacy FLOOR_CONFLICT when KEEP_SOURCE would overwrite a credited source. `RESUME_EXACT_SOURCE` retains the present checkpointed source and resumes T; it cannot reconstruct a missing source. Exact handoff removes only that unchanged recognized source after destination authority. Neither action exempts KEEP_SOURCE. |
| Join/leave and callbacks | Expected source/destination joins invalidate cached observations and revalidate ARMED/record/envelope and any READY anchor. Unexpected identities, changed resources/wrappers, duplicates or superseded acknowledgements hold before destructive actions. Deferred phase, passenger, spawn, removal and cleanup callbacks repeat final checks. |
| Cancellation/failed landing | `cancelCountdown` and `failBackToSource` for blocked pad or spawn failure use a separate exact-source return permit, not ordinary `idle()` and not legacy `stationarySource`. Preserve source fuel/debits/snapshot/wrapper, change only plan-authorized flight/position, then prove source plus destination absence, acknowledge CANCELLED and clean up. Unknown publication remains a hold. |

Source freezing gates adapter mutations, not physical dismount: never cancel a player's dismount.
Departure invalidates an unacknowledged capture/proof; after ARMED it uses the checked passenger/
return action instead of rewriting the frozen manifest through ordinary mutation.

**Replay exclusion:** a pre-release record has no exact checked ARMED entry issued after release;
its schema/serial/digest cannot be supplied retroactively. Exact retired T remains refused. A completed
or cancelled serial cannot become ARMED again, and a superseded head cannot authorize a later flight.
A restored old T with a completed serial permits, at most, checked cleanup while its matching terminal
entry/current receipt-backed head still proves that outcome; it cannot debit or resume the source.
Treat T as client-supplied intent, not provenance: before F1, refuse T named by any receipt guard,
exact-T floor, existing journal record, retained ARMED entry or current committed-debit history.
A missing ARMED entry never makes a stale schema-3 record eligible for legacy recovery.
This is durable state-machine provenance, not cryptographic authentication against offline file edits.
Arbitrary per-file rollback remains outside D3-A's S2 claim.

**Required test:** strengthen `RocketSettlementIdleGameTests` to perform actual settlement O1-O4,
board/refuel, acknowledge launch, observe ASCENT and TRANSIT, spawn the different destination UUID,
transfer passengers, acknowledge source removal/COMMITTED, land and finish the head handoff. Assert
the exact debit once, retained source epoch, destination's independent epoch, one available rocket,
and refuel the successor u2 to advance its epoch, then complete a second flight, landing, F7 handoff
and checked cleanup (or COMMITTED-predecessor replacement). Repeat the same two-flight case after
ordinary L2 without any receipt (test L2 now, C8 on integration). The unchanged log checker must pass: no new expectation for
FLOOR_PROOF_REQUIRED, FLOOR_CONFLICT or ENDPOINT_JOIN_PROOF_REQUIRED. Add A0 replay/cancel/serial
cases and S2 cuts on both sides of each new acknowledgement in section 5, with two restarts.

## 2. Unbound chosen survivor: F3

**Decision:** normalize an unbound chosen rocket to a ledger-bound epoch-0 survivor before O2;
do not exempt unbound state from equality checks or reset its signature after an arbitrary mutation.

| Step | Rule and stop behavior |
|---|---|
| O1 | Intent schema 2 freezes both the observed neutral wrapper and the prescribed normalized wrapper `(ledger_id, epoch=0, markers=[])`. Resources, owner, physical reference and snapshot remain identical. Preview/confirmation includes this normalization in its digest; it cannot change an acknowledged choice. |
| Before O2 | One private settlement action publishes only that wrapper binding, after O1 acknowledgement. The observer accepts exactly the old or normalized form while applying that step, then freezes only the normalized form for proofs. Restart repeats the binding idempotently; all other differences retain the hold. |
| O2-O4 | Own-store/force/stable-readback proves the bound form. The receipt and head record it, so O4 cannot release an unproved normalization. The first native resource mutation advances 0 to 1 atomically with fuel; later mutations remain monotonic. |
| Pre-change development receipts | Refuse and preserve their raw escrow root under section 4; do not migrate them, initialize a head or fabricate an O2 acknowledgement. New fixtures exercise normalization before their first O2. |

Tests must cut before/after binding publication and its O2 acknowledgement, then perform two native
resource changes, save/restart and a complete flight. The original neutral resources and UUID are
preserved; a stale unbound or same-floor/different-signature copy is refused.

## 3. Per-settlement marker pruning: F4

| Item | Frozen rule |
|---|---|
| Exact O4 callback | Capture `(ledger_id, decision_digest, member_op_ids, O4_generation)` from the completed request. Enqueue that group's released receipts from the acknowledged snapshot, not `tags[0]` and not a rotating index. Verify all member receipts are released with that digest and no live/captured/unknown operation still owns a marker before removing it. |
| Independent work | A separate bounded pruning cursor visits one released group and at most one endpoint per tick, whether or not other intents are held. Deduplicate endpoints/member IDs within the group. O4 enqueues priority work but does not synchronously walk a 128-endpoint component. Keep queues bounded by the 64 receipts/operations; advance past unloaded, invalid or busy endpoints without tickets. |
| Safety | Prove chosen identity, bound wrapper/floor and signature at the relevant frontier. Remove only that group's retired marker IDs; preserve resources, epoch and later/live markers. If pruning a later group before an earlier group would leave a hole or a suffix not ending at the current epoch, skip it and retry after the earlier group prunes; this ordering wait is not sticky REPAIR. Actual identity/resource conflicts still hold. |
| Restart | Rebuild pruning work from acknowledged released groups; no persisted cursor or callback is authority. Ordinary subsequent saving persists the pruned wrapper; a stop before it saves merely repeats pruning. |

Required A0/A1 case: A released first, B permanently held, then C released. C's callback names C;
A and C both eventually prune while B stays locked. Add chained groups, later live markers,
unloaded C followed by a load, and a stop after O4 replacement but before its callback.

## 4. Unknown loss and unreleased schema replacement: F5

**Decision:** loss is UNKNOWN until separately proved; accepting current quantities is not proof of
zero loss. Signed per-fluid accounting remains outside this implementation slice; production admission
remains disabled.

| Receipt schema 3 field | Meaning |
|---|---|
| `accounting_status = UNKNOWN` | Mandatory for the present accept-current, abandon and floor-retire paths. Do not emit `loss_known` or `audited_loss`, including a zero placeholder. Future KNOWN accounting requires its own versioned per-fluid proof contract. |
| `payload_amount`, `payload_hash` | Frozen operation liability/payload, not missing contents or net loss. Preserve a floor receipt's zero payload amount as a distinct non-fill case. |
| Chosen observations | Preserve actor/name, choice, member/guard IDs, decision digest, before-choice observations and checkpointed survivor identities/epochs/signatures/quantities. Intent schema 2 observations, receipt schema 3 observations and READY/transfer floors all carry signature kind plus invariant-envelope digest (section 5). Quantities are observations, never a conservation verdict. |
| Release/authority | Add the O4 release generation and the bounded head/reference of section 1. The audit part never changes when the head advances. A schema-3 reader must reject unknown accounting enums and must not default missing status to KNOWN. |

| Data boundary | In-place replacement/refusal policy |
|---|---|
| New test-hook ledger | Define unreleased ledger root 2, operation 2, intent 2 and receipt 3 directly, with mandatory bindings, typed observations, UNKNOWN accounting, READY/ARMED tables and `issued_launch_serial`. No released build creates these stores; no migration chain or legacy escrow reader is added. |
| Any pre-change development escrow data | Refuse whole-world startup fail-closed and keep the raw escrow file unchanged, including old released receipts and O1/O3 operations. Do not initialize/normalize/extend old objects or recompute frozen digests. Regenerate disposable test fixtures; retained development worlds need the stopped-server whole-world-copy procedure. Never alter sealed evidence. |
| Future/malformed/oversize or ambiguous new data | Preserve raw root and refuse under managed-root policy. No truncation, history eviction, inferred launch or fabricated release. Repeated startup refusal must preserve the same raw-file hash. |
| Production journal/entity data | Preserve existing schema-1/2 transfer checksums and entity schema-1/2-to-3 compatibility. Section 5.2 governs record versioning; this escrow-only refusal is not a production journal-root migration. |

### 4.1 Encoded-size prerequisite for implementation (L4)

The 2 KiB receipt, 1 KiB head/ARMED entry, 2 KiB binding, 12 KiB operation, 4 KiB transfer
metadata and 1 MiB root are ceilings, not evidence that their simultaneous maxima fit.
The implementation task must supply field-by-field worst-case canonical-NBT byte/node/depth
arithmetic and encoder boundary tests before finalizing these layouts. Count tag names, headers,
UUID/long fields, maximal strings, digest/kind pairs, both endpoint references/guards, native Fluid
tags up to 8 KiB, and the complete embedded O1 intent in every member operation. Include all
receipt choices, full READY/ARMED tables, terminal cleanup entries and maximum legal transfer
snapshots. This design session has not measured or proved those sizes.

Before L1, preflight the operation plus native binding plus a worst-case one-member O1 intent,
receipt/head/ARMED growth and root reservations; connected-group growth must also fit before a
later admission connects it. Thus a large Fluid tag cannot first be credited and only then discover
that no settlement fits. F1 similarly reserves its ARMED/terminal outcome and governed-record
growth before checkpoint/arming. Refuse without mutation when reservations cannot fit. Use the
same exact-generation ledger writer for all table updates; if the unchanged limits do not fit,
return a layout redesign to Root, never raise a bound or silently drop fields.

## 5. Credited-world reconciliation and transfer recovery

### 5.1 Canonical signatures and the native mutation envelope

| Use | Frozen representation |
|---|---|
| Native propulsion endpoint signature | `FUEL_VECTOR_V1 = RocketFuelState.resourceSignature()`. Use it consistently for normal prefix classification, floor comparisons and new native settlement frontier signatures. Never call it on legacy scalar fuel. Existing operation before/after signatures and payload hashes remain byte-for-byte unchanged. |
| Existing composite signature | `ROCKET_COMPOSITE_V1 = hash("arce-escrow-rocket-1:" + L + ":" + snapshotHash + ":" + FUEL_VECTOR_V1)`. Keep the named projection and historical evidence values; new typed readers never compare unlike kinds or treat either digest as invertible. New settlement observations use the canonical fuel projection plus invariant-envelope digest. Pre-change escrow files are refused, not rehashed/migrated. |
| Complete live envelope E | Schema 1: physical UUID/type, L, owner, Level/chunk/exact Pos, assembly/snapshot ID and complete current immutable snapshot/content hash, complete flight data/current target/origin/state/plan/manifest, complete fuel banks/native tags/descriptor and revision, committed debit IDs, complete escrow wrapper, and native motion flags required by the fixed rocket adapter. Canonical sorted-key NBT digest includes every field, not merely `totalAmount`. No generic restoration of arbitrary vanilla or third-party entity NBT. |
| Invariant-envelope projection | Versioned digest of physical UUID/type, L, owner, assembly/snapshot ID and current snapshot content hash; physical references remain separately checked. Persistent intent/head/floor observations use this projection alongside the typed fuel signature, not a frozen manifest/clock/Pos hash that would forbid normal boarding. Complete E/E' still validates those mutable fields for each action. Native propulsion fill preserves this projection; a future snapshot-changing action needs its own explicit binding update. |
| Persisted mutation binding | New operation schema 2 carries a <=2 KiB `native_binding`: signature kind, physical/owner/reference/snapshot bindings, invariant-envelope digest, before/after wrapper/frontier digests and expected mutation digests. Existing frozen Fluid payload supplies the exact delta. Full E/E' are immutable runtime captures, not two full snapshots in the <=12 KiB operation. Propulsion fill preserves snapshot geometry/content and non-resource flight fields; it updates only the selected bank, propulsion revision, wrapper epoch and next marker. |
| Binding admission | Every new operation's acknowledged L1 includes its complete native binding; there is no legacy operation-augmentation write or compatibility path. Preserve canonical fuel/payload hashing, independently version the invariant-envelope digest, and reject pre-change operations under section 4. No operation digest authorizes source reconstruction. |
| Apply authority | A private one-use server-thread permit binds session, acknowledged member/binding digests, current journal lease, physical reference, E digest and computed E' digest, action and next per-endpoint prefix link. Validate it at publication. Publish fuel and wrapper/marker together without a public `idle()` exemption or live-NBT load. No unrelated field/resource changes or historical refund. |

For a PREFIX endpoint, validate the entire chain, then apply only its unapplied links in ascending
per-endpoint order. BEFORE applies all missing links; AFTER only proves. Wrong native Fluid/tag,
revision, marker order, epoch, owner, snapshot, reference or unaffected bank is OTHER_STATE/REPAIR.
Unloaded ends wait without tickets; loaded missing, duplicate, relocated or unsupported resource
endpoints are manual conflicts. Preserve W11a's sticky REPAIR semantics. The first driver supports
only FLUID_PROPULSION_FILL with real native rockets and the explicitly gated registered fixture
source; it neither adopts arbitrary BEs nor creates a production loader/ledger.

### 5.2 Missing-source boundary and independent transfer versions

**Decision:** ordinary reconciliation is loaded-only. In W11b2, a missing credited source is REPAIR,
including during an otherwise valid ARMED flight. The F1-checkpointed source remains in its recorded
chunk column until authorized removal; deletion/pick-up bypasses do not authorize reconstruction.
Credited-source reconstruction is deferred to W27f's separate design/evidence, not specified here.

| Case | Permitted authority/action |
|---|---|
| Stationary fill source/rocket missing | No reconstruction. L1's Fluid delta, endpoint signature and marker cannot reconstruct a BE inventory or a whole credited rocket. Keep MISSING_ENDPOINT and the component locked for settlement/offline repair. |
| Missing source before authorized removal | After bounded recorded-slot discovery, keep MISSING_ENDPOINT/REPAIR, the ARMED entry and connected resources locked. No automatic source creation, historical fallback, refund or new debit. W27f alone may later design a credited-source reconstruction protocol. |
| Destination-authoritative phase | Keep destination reconstruction: checked DESTINATION_SPAWNED may precede its first entity save under S2. Resume/publish only the predetermined UUID and complete destination envelope of that current ARMED T after bounded loaded-end/conflict checks and own-store/force/stable-readback absence in recorded slots. Its frozen exact debit is not applied again. Before the acknowledged source-removal phase, an unexpectedly missing source still requires REPAIR. |
| READY/completed head, old transfer, missing wrapper or incomplete envelope | No missing credited-identity reconstruction. A post-handoff rocket may have changed resources after the frozen flight, so that flight is no longer a reconstruction source. Existing schema-1/2 protected transfers retain FLOOR_CONFLICT/manual repair; neutral legacy recovery remains unchanged. |
| New destination versus retained source | Keep the present source wrapper/epoch exactly; never recreate it in W11b2. A new destination has its own ledger-bound epoch 0. Resource mutation after F7 advances that destination's epoch, not the old source's. |

The `ROCKET_TRANSFERS` root stays schema 2, with no managed-root migration-chain change.
Only records governed by an ARMED entry (and, when applicable, a READY head) use record schema 3;
the added `native_transfer` envelope has its own schema 1. With production admission disabled,
only test/release-test hooks can create the required ledger/bound state and thus these records.
No-ledger worlds continue writing schema-1/2-compatible records; an older jar must still read their
root and records, subject to the already documented entity-schema downgrade restriction.
An older jar refuses/preserves governed schema-3 records; record refusal can block that journal's
launch/recovery, but it must not be caused by a root bump in unrelated worlds. Add this to R-035.
Reuse existing source/destination snapshot and flight bodies by canonical references instead of
duplicating them. Persist <=4 KiB added metadata for exact wrappers, positions, signature kinds,
head/launch bindings and the preselected destination UUID, within unchanged transfer record/root
bounds. Schema-3 checksum/digest explicitly covers this new authority and UUID; phase/action proofs
also bind the checked current whole-record digest. Keep schema-1/2 checksum readers unchanged.
Replace `RocketTransferRecord`'s record-schema-equals-flight-schema invariant with independently
validated record, envelope and embedded flight versions. Existing flight schema 2 need not be
renumbered to invent new semantics. Finish active legacy flights with their old checksums; never
upgrade an incomplete protected record by inventing its wrapper. Admission/refusal and preservation
fixtures must cover maximum legal old snapshots, unknown schemas, production-root downgrade and
existing entity upgrade/repeated-save behavior, not an unreleased escrow migration.

Only state transitions (COUNTDOWN/ASCENT/TRANSIT/DESCENT/return and publication/removal) are bound
actions that validate complete E and compute the exact next E'. Per-tick ascent/descent position
interpolation checks its cached bound plan, phase and revision without rehashing the whole envelope.
Mutation/save/join/leave observations invalidate that cache; transitions revalidate before effect. The
immutable record retains the resource/identity authority and allowed progression, not one timestamp
that must equal every later live state. Resume a present source or reconstruct the authorized
destination from its recorded progression, then
rebase the phase clock through a non-resource action. Never recreate a player or replace playerdata:
the saved manifest supplies assignments, and only separately validated online players may remount.

### 5.3 Ordinary reconciliation order and forced-stop cuts

Every row is server-owned; each acknowledgement is cut before/after replacement, after replacement
before callback, and after callback where it releases a lock. Each S2 case has two restarts.

| Step | Action/acknowledgement | Forced-stop or fault result |
|---|---|---|
| C0 validate/bind | Validate the whole connected component, loaded registration/occupancy, owners, prefixes and exact checked journal guard. Freeze the new operation's native binding and preflight section 4.1 reservations. | No acknowledged L1: no mutation authority. Pre-change operations are refused, not augmented. Unknown writes hold the exact candidate. |
| C1 L1 | Acknowledge the new PREPARED operation/binding and capacity reservations before either endpoint changes. Restarted L1 records already supply this prerequisite. | Old root: no debit; replaced root: recover only its recorded prefixes. |
| C2 apply | One-use permit applies the next exact BEFORE/PREFIX delta and publishes resources/epoch/marker atomically per endpoint. There is no durable apply acknowledgement. | Any combination of exact prefixes rolls only missing links forward; OTHER/missing stays locked. |
| C3 freeze | Freeze complete current frontiers for all affected endpoints. No chained apply/admission may mutate them through L2; one physical slot capture covers all its targets. | No proof survives a restart. |
| C4 own store | Capture one endpoint chunk/tick through its original worker and retain the exact own-store future; event acceptance or absence in RAM is not acknowledgement. | Store failure/veto keeps L1/frontiers locked; never omit an unserializable endpoint. |
| C5 force | After that future succeeds, force the same original worker. | Failure or superseding monitored save invalidates the proof. |
| C6 readback | Stable bounded own-slot readback matches complete identity/binding and the canonical contiguous frontier. Joint live tokens must cover both ends of every proposed retired member; validate the exact current journal lease too. | Individual/obsolete frontier proof cannot retire anything. |
| C7 L2 | Capture and replace the checked ledger generation removing only the jointly proved contiguous retirement prefix, installing required exact-T floors. Recheck every token and journal lease at capture and immediately before replacement. | Definite failure repeats proofs; UNKNOWN retains candidate/locks. Before replace: L1 remains. After replace: no replay, even before callback. |
| C8 release/prune | Only the exact L2 callback prunes its retired IDs, retains epochs/resources and unlocks ends with no remaining operation. Drop old proof tokens; remaining chains need a newly frozen frontier. | Stale markers after a stop are removed by validated watermark/identity/receipt rules, never used to infer a former identity. |

`RocketEscrowAudit.attempt` may report `proved=true` only from C7's exact successful retirement
acknowledgement for that attempt and its requested members. C6 supplies tokens internally but reports
no scheduler success while L2 is pending. It must not return true after apply, one-sided
proof, loaded absence or reconstruction. Scheduler `Result.proved` may clear only a retryable stall;
it is not L2 and never directly unlocks or retires. A settled/manual operation stays on its own path.
Revalidate on a superseding callback and never report success for a newer/different attempt. If the
scheduler has already removed the retired record, dropping its obsolete callback is harmless;
retirement/release authority remains the coordinator's exact acknowledgement.

### 5.4 Every-bound-rocket flight order and forced-stop cuts

| Step | Action/acknowledgement | Forced-stop result |
|---|---|---|
| F0 release prerequisite | Settlement: O1, optional neutral binding, joint O2, O3, matching O3J, O4/head acknowledgement, then exact-group pruning. Ordinary bound rocket: exact L2/C8 acknowledgement and validated retired-marker pruning, with no receipt/head requirement. | Repeat the corresponding release prerequisite; no flight from pending operations. Preserve O1-O4/C0-C8 cuts and binding/release-anchor cuts. |
| F1 checkpoint and arm (H2 option a) | Validate binding and any READY head, checked idle journal (or exact F7-terminal COMMITTED predecessor) and no pending resource/transaction/bay claim. Refuse replayed client T before locking; fix destination UUID and reserve capacity. After pruning, freeze complete E/E' including boarded manifest, refuelled fuel/epoch and wrapper; lock all source changes. Own-store E through the original entity worker, await its future, force it and stable-readback the recorded slot. Revalidate the complete source proof token and journal lease at capture and immediately before replacement; only then acknowledge an ARMED entry consuming a server serial. No resource change or COUNTDOWN before F2. | Before successful checkpoint or ARMED replacement: no launch authority/effect; discard only unacknowledged preparation. After ARMED replacement (even before callback), disk holds E or a permitted later progression. Restart takes checked baselines and fresh proofs, then resumes matching T, or acknowledges CANCELLED for exact unchanged E plus checked no-T state. Cut both sides of store/force/readback and ARMED replacement/callback after boarding/refuelling, without an extra fixture save. Never reuse a serial or infer launch from RAM. |
| F2 prepare | Checked schema-3 PREPARED record acknowledges the exact ARMED tuple/envelopes before COUNTDOWN. It may replace only the exact F7-terminal COMMITTED predecessor; retain the predecessor's terminal entry until that acknowledgement proves its old T absent. | Matching T/ARMED and E/permitted progression resume through the successor dispatcher, with fresh observations, not protected-source rollback. Cut journal replacement/callback; obsolete predecessor callbacks authorize cleanup only. |
| F3 forward source | Transition-bound COUNTDOWN/ASCENT/TRANSIT preserves frozen fuel/wrapper; bounded interpolation follows the cached plan. | Restart retains the F1-checkpointed source at E or an allowed progression, revalidates and resumes without debit/refund. An unexpectedly missing source is REPAIR, never reconstruction. Cut each phase acknowledgement/effect. |
| F4 destination | Checked DESTINATION_SPAWNED records the fixed destination authority before publication/join. Initialize its exact destination envelope and ledger-bound epoch-0 wrapper under one permit. | Repeat publication only after bounded discovery/absence checks; do not generate a different UUID. Cut phase replacement/callback and entity publication/proof. |
| F5 phases | Existing checked PASSENGERS_TRANSFERRED, SOURCE_REMOVED and COMMITTED snapshots precede their exact passenger/removal/phase effects. Permits compare ARMED/record, optional READY anchor and all loaded candidates; unexpected protected copies are not discarded. | Resume the acknowledged side; a source absent after authorized removal is expected, but phase acknowledgement alone cannot prove absence or destination storage. Cut replacement/callback/effect. |
| F6 handoff proofs | After landing, fresh joint own-store/force/readback proves destination frontier and source disappearance in recorded slots, with checked COMMITTED T lease. Unresolved source or assigned-passenger authority, including an offline passenger, retains the wait. | Entry remains ARMED and destination resources locked. Cut each proof on both sides. |
| F7 handoff | Checked ledger update records HANDED_OFF/CANCELLED and completes the serial under jointly valid F6 tokens at capture/replacement; update READY successor UUID/typed frontier/invariant digest only if a head exists (destination epoch 0 for handoff, preserved source epoch for cancellation). Receipts stay immutable. Exact callback releases the successor's resource lock. | Before replace: ARMED/proofs repeat. After replace: terminal authority permits cleanup only, never resume/debit/remove again. Receipt-backed election is now the new head. Ordinary bound destinations have no new permanent head. |
| F8 cleanup and ARMED pruning | After F7 acknowledgement, checked journal removal or the exact next F2 replacement proves completed T absent and releases dependent pad/tickets. Then a checked ledger generation prunes only its exact terminal entry, with journal lease rechecked at capture/replacement. A next launch revalidates binding/optional READY and reserves its new serial/slot; predecessor terminal slots remain charged until pruned. | Cut journal replacement/callback and terminal-prune ledger replacement/callback. Restart repeats cleanup/pruning only; never resume source/debit from a terminal entry. UNKNOWN keeps the exact entry and capacity. |

A cancelled exact-source return follows F6-F8 with the unchanged source as successor and a proved
absent destination. Its checked outcome must name cancellation, not COMMITTED flight completion;
it does not mint a destination or refund from an old snapshot. Changed fuel/wrapper or unknown
publication remains a hold. All final boundaries, including login/duplicate election/manual recovery,
consult this dispatcher; generic `keepOne`/`discardAll` is not its proof.

**Why H2 option (a):** checkpointing the exact E before ARMED makes the launch manifest, current
fuel/epoch and pruned wrapper durable without defining a second, weaker PREPARED-cancel comparison.
After the checkpoint, allowed flight transitions cannot overwrite that credit with a historical
snapshot. Before it, an interrupted board/refuel has only ordinary native-save guarantees and no
new launch authority; do not label that unsaved preparation as a frozen-observation conflict.
Required S2 cuts include board/refuel then immediate stop; pruning then stop; both sides of each
F1 store/force/readback boundary; ARMED replace-before-callback; and both sides of F2 replacement/
callback followed by COUNTDOWN/ASCENT stop. Do not insert an autosave to make these pass. Perform
two restarts per cut, check manifest/epoch/fuel according to the last durable boundary, one available
rocket and no repeated debit; a valid post-ARMED case resumes or cancels by its exact checked outcome,
not permanent REPAIR. Actual external deletion or contradictory resources still require REPAIR.

### 5.5 Contract deltas and evidence boundary

| Existing text not followed literally | Smallest proposed change for Root's approval |
|---|---|
| W11a section 1's single composite rocket signature | Type the two existing projections; use fuel-vector canonical prefix signatures plus complete-envelope validation. Retain every old digest and its kind. No silent recomputation of an operation or floor hash. |
| W11a section 4's no historical fallback and protected-source fence | Retain both for resource reconciliation/legacy transfers. Add complete-envelope, ARMED successor actions for every bound flight, with optional READY election. Missing credited sources remain REPAIR; only destination reconstruction is authorized. |
| W11b2 section 6's fresh epoch-0 identity and permanent chosen UUID wording | Keep independent epoch 0; bind every bound destination and replace receipt-backed READY only through F6/F7. Ordinary L2 requires no permanent head. Historic receipt identities remain negative protections. |
| W11a section 1 / W11b2 section 8's unreleased escrow formats | Replace definitions directly with section 4's current schemas; refuse/preserve pre-change development roots instead of adding a migration/augmentation/head-initialization chain. Root records this scope in R-035. |
| W27a sections 4.1/8's neutral-only source | Preserve `BAY_SOURCE_PROTECTED`, bay-first ordering and private permits. This envelope is not permission to admit a credited bay source; source reconstruction and credited consumption await W27f's separate snapshot, epoch/floor and D5 rewrite design/evidence. |
| W27a section 8: "D5 changes L's escrow signature but not its epoch" | This describes the composite snapshot projection. Under typed signatures, D5 may change composite/invariant-envelope digests while leaving the fuel-vector digest unchanged. W27f must specify any credited epoch/floor update; do not apply the neutral-source wording to credited flight. |
| ADR-065 section 4.1's pending flight/transfer schema-3 transition | Keep transfer root 2 and legacy checksums; version only governed record 3/envelope 1 independently of embedded flight 2. Existing production entity compatibility remains; no network protocol or general migration framework is added. |

Retain reconciliation's 8 visits/tick, one endpoint-chunk serialization/tick, existing barrier batch,
writer cadence, disk-reader and 16 MiB/16,384-visit preparation budgets. Cache immutable decoded
intents/bindings by acknowledged generation; invalidate on relevant state/store changes. No per-tick
full decode/rehash of 64 intents. Transfers load only server-recorded ends under existing bounded
flight tickets after positive classification; ordinary reconciliation/pruning/held repair add none.
F1/F6 proof failures, F1/F7 write failures and successor-dispatch waits reuse 100/400/1,200-tick
backoff (hard/manual conflicts remain sticky); F1 write outcomes retain the writer's UNKNOWN rules
and exact candidate. Keep the existing ledger-unavailable retry gate (200 ticks). Log
`ARCE_ESCROW_SUCCESSOR_HOLD` once per `(L,T,serial,phase,reason,backoff)` transition, not each
position tick. Healthy flights emit no hold/fence warning. Cached bindings never bypass final checks.
Coalesce independent head/ARMED/outcome/prune updates into one immutable generation per writer start,
retaining exact per-request acknowledgements, one in-flight snapshot/one successor and starts at most
once per 10 ticks. Include the 1 MiB root encode and all pruning generations in costs, with no
full-root re-encode on every decoded intent. Before admission, benchmark eight concurrent governed
transfers under docs/17 reference load, periodic saves and other competing escrow work; record
main-thread capture/encoding, allocations, queue age and writer/hand-off latency. Writer unavailability
blocks launches/handoffs; never bypass the dependency or increase the cadence to conceal it.
Measure capture/hash/allocation and reference-load MSPT before admission; subdivide work or lower
cadence if it fails, without weakening limits or omitting proofs.

Required evidence includes mono/bipropellant and chained prefixes, mixed save order, orphan/foreign/
duplicate/missing/relocated identities, changed NONE/named journal guards, same-slot targets,
superseded tokens, veto, FAILED/UNKNOWN/stale callbacks, loaded-only fairness and every C/F cut above.
Use real native rocket/source stores and unchanged conservation/log assertions; settlement cuts do
not substitute for ordinary C0-C8 evidence. All are planned tests, not results of this design session.

## 6. Disposition of F6-F11 and missing commands

| Finding | Implementation disposition |
|---|---|
| F6 | Fix: assert the actual hold reason on each restart. Retain the existing non-floor cases as frozen-observation-mismatch evidence, and add separately checkpointed source states with a reinstalled veto to prove save-refusal across restarts. Do not relabel the old results. |
| F7 | Fix: `status` reports held/released/pruning counts and bounded reason summaries; add permission-2 `/arce escrow inspect <operation>` and `/arce escrow retry <operation>` as defined below. No online rescind of O1. |
| F8 | Fix status/preview/refusal and R-035 wording to count every member of accept-current, abandon and floor settlement. Retain the 64 permanent receipt cap for bounded storage: all choices refuse at exhaustion and need whole-world offline repair; no quota eviction or owner risk acceptance is inferred. |
| F9 | Fix: cut floor and all other O1 choices on the final source, add named-transfer fill guards and truly empty entity slots. Update the old intent runner to follow eventual O2-O4 without removing its pre-O1/retained-intent assertions. |
| F10 | Fix: cache generation-bound immutable intents, use independent bounded cursors, skip writer/barrier/journal waits before expensive observations, and revalidate immediately at capture/replacement. Measure unchanged docs/17 budgets; no same-tick whole-component rehash loop. |
| F11 | Fix: keep player UUID actors and typed console actors. Implementation item L2: require the exact non-entity server-console source and an unsuppressed-output stack; source type/name/permission alone is insufficient because tick/load functions share them. Pin the actual console-entry stack in tests; if provenance cannot be established by the current command API, use a dedicated console entry and refuse the generic non-player route. Test permission-4 tick/load functions, suppressed stacks, command blocks and RCON refused for both preview/confirm; permission-2 inspect/retry remains bounded. |

| Command | Required behavior |
|---|---|
| `inspect <operation>` | Read-only, no ledger creation/tickets. Show its connected group, persisted/runtime phase and reason, O2 proved/waiting slot counts, journal acknowledgement state, next retry, receipt/head state and UNKNOWN accounting. Bound output to four detailed members plus counts; no native payload/position dump. |
| `retry <operation>` | For retryable proof/write waits, advance the next eligible attempt once, at most one accepted acceleration per operation per 200 ticks, using the same fair loaded-only queue/budgets. If already queued/in-flight, report "attempt pending"; during the command cooldown report its next eligible tick. Do not reset failure count or automatic backoff progression; log at most one line per accepted request. Settlements resume only frozen intent. Manual mismatch, veto, invalid/relocated identity or retired operation refuses; never clear REPAIR, alter O1, prune, cancel UNKNOWN or claim proof. |

F2 remains a merge/regression prerequisite: retain P1F's production/legacy interlocks and scalar
`sameResources`; the current-ledger idle fix must not undo those. Re-run its suites plus the real
full-flight/log test and settlement matrix on the integrated source. F12's up-to-date execution
distinction and generic same-epoch stale-copy residual remain recorded, not new proof claims.

## 7. Proposed R-035 update and design-session delivery

### 7.1 Text for Root (proposal, not risk acceptance)

| Required risk detail | Proposed addition/correction |
|---|---|
| Scope | Retain existing per-world checked journal/entity-schema-3 behavior and production admission-off. Add component holds, permanent receipt identities/retired transfers, <=64 receipt-backed READY heads and <=64 ARMED/terminal flight entries covering all bound rockets. Unexpected identity/envelope locks affect only associated rockets/records. Terrain veto still affects the whole chunk; 256 denial coordinates saturate to later terrain saves throughout that Level while entity/other stores may continue and diverge. |
| Unreleased-data refusal (M2) | Ledger, intent, operation and receipt definitions change directly with no migration chain; only test hooks create them. A development world holding any pre-change escrow data is refused fail-closed at whole-world startup, and its raw escrow file is preserved unchanged. Repeated starts do not rewrite it. New disposable fixtures may be regenerated; retained development worlds require whole-world-copy repair, not deletion of the old ledger. This does not add refusal to production worlds without a ledger. |
| Version/downgrade boundary (M1) | Journal root remains schema 2, with no root bump/migration. Only governed test-hook flights write record schema 3, independent of embedded flight schema. An older jar still reads no-ledger schema-1/2 journal data, but refuses/preserves governed schema-3 records and may block their journal's launch/recovery. Existing schema-3 rocket-entity downgrade quarantine remains a separate production-visible restriction; downgrade requires a consistent whole-world backup. |
| Duration/availability | O1 cannot be rescinded online; veto/changed observations may hold indefinitely. Receipts have no online collection: 64 settled members per world's lifetime, all choices counted, exhaustion refuses new settlement. ARMED capacity includes terminal cleanup and UNKNOWN entries; a failed ledger writer blocks bound launches and handoffs until exact acknowledgement/storage recovery, never by timeout. Destination resources stay locked from landing through F6/F7, indefinitely if an assigned passenger remains offline. Proof/UNKNOWN holds survive restart; unexpectedly missing credited source before authorized removal is REPAIR. F1 checkpoint makes post-ARMED E durable rather than accepting H2's ordinary crash hold; pre-checkpoint preparation has only native-save guarantees. Keep part-1 F3/F8/F9/N2 open until evidence closes them. |
| Logs/operator visibility | One bounded `ARCE_ESCROW_SUCCESSOR_HOLD` line per phase/reason/backoff transition, no per-tick polling log or lifetime transition cap. Inherited Forge/vanilla/store/force errors remain uncapped. Permission-2 status/inspect expose phase, reason, retry, ARMED/head capacity and writer wait; retry is limited to one acceleration/operation/200 ticks and never bypasses veto/REPAIR. Healthy settled and ordinary-L2 flights produce no new fence warning. |
| Accounting/actors | Historical zero-loss fields are not conservation evidence. New receipts say UNKNOWN; preview/confirm/audit disclose possible retained loss/duplication. Signed per-fluid accounting remains open. Only player UUID actors and verified actual console actors may settle; RCON-only administration cannot settle as a non-player actor, though it can inspect/rate-limited retry. No risk or admission is accepted by this note. |
| Recovery | Repair retryable IO and resume checked work; frozen mismatch, missing/invalid authority, pre-change development format or permanent receipt exhaustion needs stopped-server whole-world-copy repair. ARMED saturation waits for valid cleanup, never evicts held authority. Back up/hash every Level's region/entities/POI, global data/journals/ledger, player data and metadata; preserve raw originals, validate identities/prefixes/conservation, run two save/restart cycles and fresh barriers, then replace the complete stopped world. Never restore/delete only escrow/journal or a migration backup. |
| Limits | W11b2 never reconstructs a missing credited source; that requires W27f. Only the exact predetermined destination of a current ARMED flight may be reconstructed after acknowledged destination authority and fresh absence proofs, never an older credited snapshot. R-032's unobservable writes, direct-store bypass, OS/power loss and arbitrary per-file rollback remain excluded. No new save veto or R-021 acceptance is proposed. |

### 7.2 Original design-session delivery record (historical)

This record describes `9b889a14`; Revision 1 replaces its migration/source-reconstruction proposals
and its request for a first independent design review. It does not change the historical command results.

| Item | Actual result |
|---|---|
| Completed scope | This seven-question design note, based on the actual source, continuation 08 and hash-verified independent review. Only this file changes; no upstream code/assets are imported. |
| Commands executed | `Get-Content`/`rg` source and contract reads; `git status --short`, `git branch --show-current`, `git worktree list`, `git log -3 --oneline` and running-process inspection: assigned clean branch at `60343d82`, no other worker targeting this worktree. `Get-FileHash .../REVIEW.md -Algorithm SHA256`: exact TASK hash matched. |
| Environment observations | OpenPet CLI `event thinking`: HTTP 503; no app data edited. The generic `docs/versions/v1.8.0.md` name was absent; the actual `V1.8.0-CLASSIC-CONTENT-COMPLETION.md` was read. Some exploratory `rg` paths were absent; actual codec/source files were located and read. The first multiline `python -B -c` document-check invocation failed with SyntaxError through the pyenv batch shim; retrying with a single-line encoded argument passed. |
| Verification | Inline `python -B -c` documentation check: PASS, seven numbered sections, five existing local links, exact review hash, one changed file and no trailing whitespace. `git diff --check`: PASS. `git diff --exit-code 60343d82 -- src scripts tests build.gradle gradle.properties docs/decisions docs/status docs/11-RISK-REGISTER.md`: PASS, executable and forbidden central files unchanged. `git add -- <note>`, `git diff --cached --check`, `git diff --cached --stat` and `git diff --cached --name-only`: only this new note staged, whitespace check PASS. |
| Tests/code not changed | No production/test/runner code or new runtime test results. Gradle build/test/runData/GameTest, native S2, dedicated server and V1/V2 were not run for this design-only assignment; section 13's slice regressions apply to the later implementation/integration. No machine lock or temporary server copy was created. |
| Still incomplete/known risks | All described fixes/envelope/migration/reconciliation work awaits approval and implementation. Continuation 08's failed log check and GroundSurvey timing occurrence remain open; permanent holds/capacity exhaustion and UNKNOWN accounting are not accepted. Root alone edits ADR/risk/status/ledger files. |
| Required Gates/next v1.8 action | Not all G0-G9 are satisfied, and this note approves none. Root obtains the independent read-only design review, freezes any accepted contract deltas, then assigns the remaining W11b2 implementation and C/F cut/full-flight evidence with admission disabled. No W11b3/bay implementation, push or shared-branch merge in this task. |

The delivery commit contains only this note and is identified by Git history for this path; it is not
a self-referential commit hash embedded in its own contents.

### 7.3 Revision 1 delivery record

| Item | Actual result |
|---|---|
| Completed scope/write scope | Read the complete design-2 review/note and revised only this note for H1-H2, M1-M3 and L1-L9. No production/test/runner, ADR/risk/status/ledger or upstream asset changes. The local revision commit is identified by `git log -1 -- <note>` after delivery. |
| Preparation/read commands | `Get-Content` read TASK/WORKER-RULES, governance/version/Gate/quality documents, review/note and relevant ADR/design/source sections. `rg`/`rg --files` located contracts/constants/code; two guessed source filenames were absent and actual files were located. `git status --short`, `git branch --show-current`, `git worktree list`, `git log -4 --oneline` and bounded process inspection found a clean assigned branch at `88be104b`, with other workers in separate worktrees and none sharing this assignment. Review `Get-FileHash -Algorithm SHA256` matched the TASK exactly. |
| Verification commands/results | Inline `python -B -c` document check: first run caught H1's range ending on a blank line; corrected and reran PASS for 14 finding mappings/changed-line coverage, seven numbered sections, five existing local links, exact review SHA-256, whitespace and one-file scope. `git diff --check` and `git diff --exit-code 88be104b -- . ':(exclude)docs/work/v1.8.0-root-queue/records/W11B2-DESIGN-2.md'`: PASS. `git add -- <note>`, `git diff --cached --check`, `git diff --cached --stat` and `git diff --cached --name-only`: PASS, only this note staged. The local commit is identified by path history; no push/merge is performed, and clean status is checked before delivery. |
| Tests and environment | No tests added/modified or runtime evidence claimed. Gradle clean/build/test/runData/GameTest, S1/S2 and V1/V2 were not run for this design-only assignment. OpenPet MCP thinking returned connection refused; no app data changed. No heavy-JVM lock, server copy, temporary file, other agent, push or shared-tree merge was created. |
| Design choices/not completed | Every bound flight uses bounded ARMED, with optional receipt READY; F1 checkpoints complete E before launch. Keep journal root 2; replace unreleased escrow schemas directly with raw-preserving startup refusal; missing source remains REPAIR. L4 arithmetic/encoder/admission tests and L2 console-provenance tests are explicit implementation prerequisites, not evidence supplied here. All runtime implementation and C/F cuts remain pending. |
| Risks/Gates/next action | No G0-G9 approval. Continuation 08's failed log check/GroundSurvey timing and part-1 open items remain open; permanent holds, UNKNOWN accounting, passenger/writer availability and capacity limits are not accepted. Root re-reads revised section 1 and F1 in 5.4, updates its owned ADR/R-035 records as needed and assigns remaining W11b2 implementation/evidence within v1.8.0; no full second design review is requested. |
