# W11b2 design 2: settlement successors and credited-world reconciliation

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

## 1. Fresh launch after settlement: N1 and F1

**Decision:** an acknowledged post-release launch has explicit, durable successor authority.
Receipt history alone is neither permission nor a reason to reject that launch. Ordinary protected-source
rollback remains forbidden. A transfer ID, game time, live-set membership or caller-supplied hash is
never the proof.

| Item | Frozen rule |
|---|---|
| Release anchor | O4 stores the decision/group ID, immutable decision digest and `release_generation` in the permanent receipt. The server may use it only from an exact acknowledged ledger snapshot. After a stop between replacement and callback, checked startup readback/baseline acknowledgement supplies that prerequisite; merely decoding a staged candidate does not. |
| Bounded lineage head | One receipt in a released group anchors the head for L (the lexicographically smallest member UUID); the other members reference it. The head contains L, owner, anchor/release generation, current physical UUID, bound resource floor/signature, monotonically issued launch serial, completed serial/outcome, and at most one pending `(T, launch_digest)`. Store it in the schema-2 root's `rocket_authorities` table: at most 64 heads, at most 1 KiB each, charged inside the existing 1 MiB root budget. A head needs an existing permanent receipt; it consumes no extra settlement slot. This small table keeps mutable election authority separate from immutable audit receipts without increasing their 2 KiB bound. No append-only list of later entities/transfers. |
| Repeated settlement | There is one effective head per L. O3 names its predecessor; O4 atomically supersedes that head with the new released group's head, carrying forward issued/completed serial watermarks. Older receipts still protect their missing identities/retired transfers but do not impose their old survivor as the current positive election. A missing-rocket/floor-abandonment receipt retires L and supplies no successor head. |
| Launch proof | After acknowledging O4, the coordinator checks the exact current survivor and journal authority, freezes the complete source/destination envelopes in section 5, and acknowledges a head update to ARMED with a fresh serial and T. Only then may the checked journal acknowledge that exact schema-3 PREPARED record. It binds ledger ID, head/group, release generation, serial, owner, source UUID/envelope digest and predetermined destination UUID/envelope digest. These two acknowledgements, in that order, prove the launch is post-release. |
| Lifetime | The head is READY before launch, ARMED during the exact transfer, and READY on the proved successor after handoff. Issued serials are never reused, including cancelled attempts; overflow refuses before mutation. Later flights use the same bounded head. A missing or contradictory journal/head is a hold, not a fresh launch. |
| Identity rule | `identityLocked`/`stateLocked` accept only the current head identity, or the exact source/destination authorized by the current ARMED head and checked transfer at that action. All other physical identities remain locked. A successful handoff replaces the current identity, rather than adding every future UUID to every old receipt. |
| Ordinary interlocks | ARMED is a logical lock for ordinary resource mutation, another launch, disassembly, kill and external movement/mount requests. Only the exact flight action permit can pass that head lock; pending escrow/transaction/bay locks still refuse. Do not make the entity globally idle to run a flight. |
| Destination wrapper | The new physical destination still starts at epoch 0; it does not inherit the source epoch/markers. For a receipt-governed flight it is bound to the same ledger at creation, with no markers, and its independent epoch-0 floor is proved at handoff. Neutral, unrelated transfers retain their existing behavior. |

| Boundary | Required use of the proof |
|---|---|
| `RocketTransferFences.classify` | Run bay dispatch first, as today. Then consult the acknowledged head plus exact checked record, before tickets. A valid ARMED launch enters the successor dispatcher, not the legacy floor-recovery election. Settling/pending operations, exact-T floors and invalid/foreign wrappers still block. History without this proof retains the existing fail-closed classification. Set membership is only the result of classification. |
| `EscrowRecoveryFence.reason` | Receive a validated action-specific authority, not a boolean `fresh`. Preserve all existing legacy branches, including FLOOR_CONFLICT for a credited source that KEEP_SOURCE would overwrite. A new `RESUME_EXACT_SOURCE` action may retain/reconstruct the proved current source envelope and resume T; a new exact-handoff action may remove that unchanged source after destination authority. Neither is KEEP_SOURCE with an exemption. |
| Join/leave and callbacks | An expected, exact source/destination join invalidates cached observations and revalidates its head/record/envelope. It does not unconditionally fence a healthy successor. Unexpected identities, changed wrappers/resources, duplicate candidates or superseded acknowledgements hold before any destructive action. Deferred phase, passenger, spawn, removal and cleanup callbacks repeat the same final checks. |
| Cancellation/failed landing | A separate exact-source return action preserves the current source fuel, debits, snapshot and wrapper; it changes only flight/position under the checked plan. Prove the unchanged source plus destination absence, acknowledge the head outcome, then remove T. Never run legacy `stationarySource` over protected resources. See the same ordering in section 5. |

**Replay exclusion:** a pre-settlement record has no checked ARMED head issued after the release;
its schema/serial/digest cannot be supplied retroactively. Exact retired T remains refused. A completed
or cancelled serial cannot become ARMED again, and a superseded head cannot authorize a later flight.
A restored old T with a completed serial permits, at most, checked cleanup of its exact COMMITTED
reservation with the current loaded destination; it cannot debit, reconstruct or resume the source.
This is durable state-machine provenance, not cryptographic authentication against offline file edits.
Arbitrary per-file rollback remains outside D3-A's S2 claim.

**Required test:** strengthen `RocketSettlementIdleGameTests` to perform actual settlement O1-O4,
board/refuel, acknowledge launch, observe ASCENT and TRANSIT, spawn the different destination UUID,
transfer passengers, acknowledge source removal/COMMITTED, land and finish the head handoff. Assert
the exact debit once, retained source epoch, destination's independent epoch, one available rocket,
and a second launch from the successor. The unchanged log checker must pass: no new expectation for
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
| Existing released unbound receipts | Migration retains the original observations and creates a normalization-required hold. A checked, loaded-only normalization checkpoint establishes a bound head before unlock; an unavailable/changed survivor needs offline repair. No migration fabricates an O2 acknowledgement. |

Tests must cut before/after binding publication and its O2 acknowledgement, then perform two native
resource changes, save/restart and a complete flight. The original neutral resources and UUID are
preserved; a stale unbound or same-floor/different-signature copy is refused.

## 3. Per-settlement marker pruning: F4

| Item | Frozen rule |
|---|---|
| Exact O4 callback | Capture `(ledger_id, decision_digest, member_op_ids, O4_generation)` from the completed request. Enqueue that group's released receipts from the acknowledged snapshot, not `tags[0]` and not a rotating index. Verify all member receipts are released with that digest and no live/captured/unknown operation still owns a marker before removing it. |
| Independent work | A separate bounded pruning cursor visits one released group and at most one endpoint per tick, whether or not other intents are held. Deduplicate endpoints/member IDs within the group. O4 enqueues priority work but does not synchronously walk a 128-endpoint component. Keep queues bounded by the 64 receipts/operations; advance past unloaded, invalid or busy endpoints without tickets. |
| Safety | Prove chosen identity, bound wrapper/floor and signature at the relevant frontier. Remove only that group's retired marker IDs; preserve resources, epoch and all later/live markers. Validate the resulting contiguous suffix; a conflict holds that endpoint rather than removing a hole. |
| Restart | Rebuild pruning work from acknowledged released groups; no persisted cursor or callback is authority. Ordinary subsequent saving persists the pruned wrapper; a stop before it saves merely repeats pruning. |

Required A0/A1 case: A released first, B permanently held, then C released. C's callback names C;
A and C both eventually prune while B stays locked. Add chained groups, later live markers,
unloaded C followed by a load, and a stop after O4 replacement but before its callback.

## 4. Unknown loss and receipt migration: F5

**Decision:** loss is UNKNOWN until separately proved; accepting current quantities is not proof of
zero loss. Signed per-fluid accounting remains outside this implementation slice; production admission
remains disabled.

| Receipt schema 3 field | Meaning |
|---|---|
| `accounting_status = UNKNOWN` | Mandatory for the present accept-current, abandon and floor-retire paths. Do not emit `loss_known` or `audited_loss`, including a zero placeholder. Future KNOWN accounting requires its own versioned per-fluid proof contract. |
| `payload_amount`, `payload_hash` | Frozen operation liability/payload, not missing contents or net loss. Preserve a floor receipt's zero payload amount as a distinct non-fill case. |
| Chosen observations | Preserve actor/name, choice, member/guard IDs, decision digest, before choice observations and checkpointed survivor identities/epochs/signatures/quantities. Report their signature kind explicitly (section 5). Quantities are observed values, never a total conservation verdict. |
| Release/authority | Add the O4 release generation and the bounded head/reference of section 1. The audit part never changes when the head advances. A schema-3 reader must reject unknown accounting enums and must not default missing status to KNOWN. |

| Existing test-world data | Migration/refusal policy |
|---|---|
| Schema-2 receipt | Preserve its old digest, original observation signatures and legacy accounting fields as bounded legacy audit data; classify its accounting UNKNOWN even when it says `loss_known=1, audited_loss=0`. Keep chosen/missing identities and retired-T protection. Never rewrite sealed external evidence. |
| Released receipt | Initialize no usable head merely from `released=1`. After the checked ledger/journal baselines, require the exact loaded survivor, original floor checks, retired-marker validation/pruning and a new own-store checkpoint, then acknowledge a head-initialization generation. Store that generation as the new release anchor. An existing stale retired guard record or an unproved journal absence must be resolved, not adopted as a new launch; positively acknowledged absence is the expected retired state. |
| O1/O3 in progress | Keep the original intent/member digests and locks, dispatch its legacy validation exactly, and require fresh proofs on restart. Add normalization/head metadata only in an acknowledged extension; do not silently replace the frozen decision with a new preview. |
| Schema-1 tombstone, ambiguous heads, future/malformed/oversize data | Retain the raw root and refuse upgrade/start under the managed-root policy; report whole-world-copy repair. Never discard a receipt, evict history, truncate it or reinterpret an old digest as a new signature kind. |

Advance the ledger root to schema 2 with an explicit root-1 migration (same ledger UUID, issued watermark,
preserved legacy operations and a bounded `rocket_authorities` table), intent to schema 2 and receipt
to schema 3. Old operation bodies retain their
original encoded digests/signatures/payload hashes. The migrated/new wrapper has a separately named
versioned digest. Preflight exact receipt/operation/root bytes plus reserved head growth before any
write. Reserve each head's worst-case ARMED/handoff size as well as receipt growth; head changes use
the same exact-generation ledger writer, not a second store. If the existing 2 KiB/12 KiB/1 MiB bounds
cannot fit, preserve the original and hold/refuse;
Root must review a redesign rather than raise a bound. Migration runs on a whole-world copy, writes
through the checked writer, is idempotent and needs two native restarts/downgrade-refusal fixtures.

## 5. Credited-world reconciliation and reconstruction

### 5.1 Canonical signatures and the native mutation envelope

| Use | Frozen representation |
|---|---|
| Native propulsion endpoint signature | `FUEL_VECTOR_V1 = RocketFuelState.resourceSignature()`. Use it consistently for normal prefix classification, floor comparisons and new native settlement frontier signatures. Never call it on legacy scalar fuel. Existing operation before/after signatures and payload hashes remain byte-for-byte unchanged. |
| Existing composite signature | `ROCKET_COMPOSITE_V1 = hash("arce-escrow-rocket-1:" + L + ":" + snapshotHash + ":" + FUEL_VECTOR_V1)`. Keep existing receipts' values and mark their kind. A reader projects the same validated complete envelope to the required kind; it never compares unlike kinds or treats either digest as invertible. Old settlement observations use the composite projection; old native operations use the fuel projection. |
| Complete live envelope E | Schema 1: physical UUID/type, L, owner, Level/chunk/exact Pos, assembly/snapshot ID and complete current immutable snapshot/content hash, complete flight data/current target/origin/state/plan/manifest, complete fuel banks/native tags/descriptor and revision, committed debit IDs, complete escrow wrapper, and native motion flags required by the fixed rocket adapter. Canonical sorted-key NBT digest includes every field, not merely `totalAmount`. No generic restoration of arbitrary vanilla or third-party entity NBT. |
| Persisted mutation binding | New operation schema 2 carries a <=2 KiB `native_binding`: signature kind, physical/owner/reference/snapshot bindings, invariant-envelope digest, before/after wrapper/frontier digests and expected mutation digests. Existing frozen Fluid payload supplies the exact delta. Full E/E' are immutable runtime captures, not two full snapshots in the <=12 KiB operation. Propulsion fill preserves snapshot geometry/content and non-resource flight fields; it updates only the selected bank, propulsion revision, wrapper epoch and next marker. |
| Legacy operation augmentation | For an exact already-loaded stationary native endpoint, validate the old body/prefix, owner/reference, journal guard and complete E; acknowledge a versioned binding extension before applying/proving it. Preserve the old body digest/signatures. If that binding cannot be uniquely established, remain in REPAIR. An old operation hash alone cannot authorize reconstruction. |
| Apply authority | A private one-use server-thread permit binds session, acknowledged member/binding digests, current journal lease, physical reference, E digest and computed E' digest, action and next per-endpoint prefix link. Validate it at publication. Publish fuel and wrapper/marker together without a public `idle()` exemption or live-NBT load. No unrelated field/resource changes or historical refund. |

For a PREFIX endpoint, validate the entire chain, then apply only its unapplied links in ascending
per-endpoint order. BEFORE applies all missing links; AFTER only proves. Wrong native Fluid/tag,
revision, marker order, epoch, owner, snapshot, reference or unaffected bank is OTHER_STATE/REPAIR.
Unloaded ends wait without tickets; loaded missing, duplicate, relocated or unsupported resource
endpoints are manual conflicts. Preserve W11a's sticky REPAIR semantics. The first driver supports
only FLUID_PROPULSION_FILL with real native rockets and the explicitly gated registered fixture
source; it neither adopts arbitrary BEs nor creates a production loader/ledger.

### 5.2 Reconstruction boundary

**Decision:** ordinary resource reconciliation is loaded-only; missing credited flight-source
reconstruction is supported only by the new complete transfer authority, not by an escrow operation
or a historical assembly snapshot. These are distinct actions and proof paths.

| Case | Permitted authority/action |
|---|---|
| Stationary fill source/rocket missing | No reconstruction. L1's Fluid delta, endpoint signature and marker cannot reconstruct a BE inventory or a whole credited rocket. Keep MISSING_ENDPOINT and the component locked for settlement/offline repair. |
| Missing source of a valid ARMED schema-3 flight | Checked journal record plus current acknowledged head supplies the complete frozen source E, its unchanged wrapper and exact UUID. Require bounded loaded-end discovery, no conflicting recognized copy/UUID collision, and fresh own-store/force/stable-readback absence proofs in the recorded slots. Reconstruct that UUID, current snapshot, owner, L, source fuel/debit ledger and wrapper at the recorded altitude in TRANSIT for T; do not re-run countdown/burn, issue a new request, reset an epoch or use `stationarySource`. Reconstruction stays locked until its own new proof. |
| Destination-authoritative phase | Never reconstruct/roll back the source. Resume or reconstruct only the predetermined destination UUID and complete destination envelope for that current ARMED T, after the same bounded absence/conflict checks. Its exact once-only fuel debit is frozen in the acknowledged record; creation does not debit again. |
| READY/completed head, old transfer, missing wrapper or incomplete envelope | No missing credited-identity reconstruction. A post-handoff rocket may have changed resources after the frozen flight, so that flight is no longer a reconstruction source. Existing schema-1/2 protected transfers retain FLOOR_CONFLICT/manual repair; neutral legacy recovery remains unchanged. |
| New destination versus restored source | Restore the source wrapper exactly, including its epoch; a new destination has its own epoch 0, bound only as specified in section 1. A resource mutation after head release advances that destination's epoch, not the old source's. |

Transfer root/records become schema 3; the added `native_transfer` envelope has its own schema 1.
Reuse existing source/destination snapshot and flight bodies by canonical references instead of
duplicating them. Persist <=4 KiB added metadata for exact wrappers, positions, signature kinds,
head/launch bindings and the preselected destination UUID, within unchanged transfer record/root
bounds. Schema-3 checksum/digest explicitly covers this new authority and UUID; phase/action proofs
also bind the checked current whole-record digest. Keep schema-1/2 checksum readers unchanged.
Decouple record/envelope version from embedded flight schema: existing flight schema 2 need not be
renumbered to invent new semantics. Finish active legacy flights with their old checksums; never
upgrade an incomplete protected record by inventing its wrapper. Admission/refusal and preservation
fixtures must cover maximum legal old snapshots, unknown schemas and repeated migration.

Flight-state clocks/Pos evolve only through the declared COUNTDOWN/ASCENT/TRANSIT/DESCENT/return
actions. Each action validates its current complete E and computes/binds the exact next E'; the
immutable record retains the resource/identity authority and allowed progression, not one timestamp
that must equal every later live state. Reconstruct from its recorded TRANSIT/DESCENT form, then
rebase the phase clock through a non-resource action. Never recreate a player or replace playerdata:
the saved manifest supplies assignments, and only separately validated online players may remount.

### 5.3 Ordinary reconciliation order and forced-stop cuts

Every row is server-owned; each acknowledgement is cut before/after replacement, after replacement
before callback, and after callback where it releases a lock. Each S2 case has two restarts.

| Step | Action/acknowledgement | Forced-stop or fault result |
|---|---|---|
| C0 validate/bind | Validate the whole connected component, loaded registration/occupancy, owners, prefixes and exact checked journal guard. Freeze supported native binding; acknowledge any legacy binding extension before use. | No extension acknowledgement: no new mutation authority. Unknown writes hold the exact candidate. |
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

### 5.4 Post-release flight order and forced-stop cuts

| Step | Action/acknowledgement | Forced-stop result |
|---|---|---|
| F0 release prerequisite | O1 acknowledgement, optional neutral binding, each joint O2 proof, O3 receipts, exact matching journal removal O3J, O4 release/head acknowledgement, then exact-group pruning. | Repeat only the frozen settlement; no fresh launch before O4. Preserve existing O1-O4 cuts and add binding/release-anchor cuts. |
| F1 arm | Freeze E/E', validate READY head, checked journal and no pending resource/transaction/bay claim; choose T/destination UUID. Checked ledger update consumes serial and freezes ARMED launch digest after O4. No resource change. | Without T acknowledged, an unchanged proved source plus checked no-T state can acknowledge cancellation; otherwise hold. Never reuse the serial or infer launch from RAM. |
| F2 prepare | Checked schema-3 PREPARED journal record acknowledges the exact ARMED tuple/envelopes. Only its exact callback starts source COUNTDOWN. | A restart with matching T/head resumes through the successor dispatcher; no legacy protected-source rollback. Cut on both sides of journal replace and callback. |
| F3 forward source | Action-bound COUNTDOWN/ASCENT/TRANSIT progression preserves frozen fuel/wrapper. A missing source uses section 5.2: absence store/force/readback, exact reconstruction publication, then a fresh source store/force/readback. | Re-prove after restart; no resource release or new launch/debit. Cut each reconstruction/absence/source-proof boundary. |
| F4 destination | Checked DESTINATION_SPAWNED records the fixed destination authority before publication/join. Initialize its exact destination envelope and ledger-bound epoch-0 wrapper under one permit. | Repeat publication only after bounded discovery/absence checks; do not generate a different UUID. Cut phase replacement/callback and entity publication/proof. |
| F5 phases | Existing checked PASSENGERS_TRANSFERRED, SOURCE_REMOVED and COMMITTED snapshots precede their exact passenger/removal/phase effects. Permits compare head/record and all loaded candidates again; protected unexpected copies are not discarded. | Resume the acknowledged side; phase acknowledgement alone is not proof of source absence or destination storage. Cut each replacement/callback/effect. |
| F6 handoff proofs | After landing, fresh own-store/force/readback proves the exact destination frontier and source disappearance in recorded slots together, with checked COMMITTED T lease. Unresolved source/passenger authority retains the wait. | T/head remain ARMED; no resource mutation is available at the destination. Cut each proof on both sides. |
| F7 head handoff | Checked ledger update sets READY to the destination UUID/bound epoch-0 floor/signature and completes the serial, under jointly valid F6 tokens at capture/replacement. Original receipt observations remain immutable. Exact callback releases the successor. | Before replace: ARMED/proofs repeat. After replace: only the new head is positive authority; old source identity stays locked. |
| F8 cleanup | Only after F7 acknowledgement may the matching COMMITTED reservation be removed/replaced by checked journal write. Exact callback releases pad/tickets; a subsequent flight must revalidate its READY head and issue a new serial. | Stop between F7/F8 retries cleanup only, not source rollback or fuel debit. F8 replacement-before-callback retries dependent cleanup. |

A cancelled exact-source return follows F6-F8 with the unchanged source as successor and a proved
absent destination. Its checked outcome must name cancellation, not COMMITTED flight completion;
it does not mint a destination or refund from an old snapshot. Changed fuel/wrapper or unknown
publication remains a hold. All final boundaries, including login/duplicate election/manual recovery,
consult this dispatcher; generic `keepOne`/`discardAll` is not its proof.

### 5.5 Contract deltas and evidence boundary

| Existing text not followed literally | Smallest proposed change for Root's approval |
|---|---|
| W11a section 1's single composite rocket signature | Type the two existing projections; use fuel-vector canonical prefix signatures plus complete-envelope validation. Retain every old digest and its kind. No silent recomputation of an operation or floor hash. |
| W11a section 4's no historical fallback and protected-source fence | Keep them for resource reconciliation and legacy transfers. Add only the complete-envelope, current-head successor dispatch with exact reconstruction/action permits; it never chooses a historical snapshot as current credit. |
| W11b2 section 6's fresh epoch-0 transferred identity and permanent chosen UUID wording | Keep independent epoch 0; bind receipt-governed destinations and replace the bounded current head only through F6/F7. Historic identities remain negative protections, not perpetual positive UUID elections. |
| W27a sections 4.1/8's neutral-only source and missing complete-envelope authority | This note supplies the W11b2 envelope/reconstruction prerequisite, not permission to admit a credited bay source. Preserve `BAY_SOURCE_PROTECTED`, bay-first hook ordering and private permits. Enabling credited consumption still requires W27f's separate before/after snapshot, epoch/floor and D5 rewrite design/evidence. Do not treat the new flight witness as a bay permit. |
| ADR-065 section 4.1's pending flight/transfer schema-3 transition | Review the exact nested envelope/independent versioning and migration here before writing it. Existing active legacy flight semantics/checksums remain unchanged; no new network protocol, cargo capability or general framework. |

Retain reconciliation's 8 visits/tick, one endpoint-chunk serialization/tick, existing barrier batch,
writer cadence, disk-reader and 16 MiB/16,384-visit preparation budgets. Cache immutable decoded
intents/bindings by acknowledged generation; invalidate on relevant state/store changes. No per-tick
full decode/rehash of 64 intents. Transfers load only server-recorded ends under existing bounded
flight tickets after positive classification; ordinary reconciliation/pruning/held repair add none.
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
| F11 | Fix: keep player UUID actors; allow non-entity preview/confirm only from the actual server console with a typed console actor. RCON, command blocks and functions may inspect/retry but not settle until separate stable principal identities are designed; handle expected lookup/refusal exceptions as bounded command errors. |

| Command | Required behavior |
|---|---|
| `inspect <operation>` | Read-only, no ledger creation/tickets. Show its connected group, persisted/runtime phase and reason, O2 proved/waiting slot counts, journal acknowledgement state, next retry, receipt/head state and UNKNOWN accounting. Bound output to four detailed members plus counts; no native payload/position dump. |
| `retry <operation>` | Schedule one fair loaded-only attempt for a retryable proof/write wait, retaining backoff/budgets. An existing settlement can only resume its frozen intent. Manual mismatch, Level save veto, invalid/relocated identity or retired operation is an explanatory refusal; never clear REPAIR, alter an O1 choice, prune, cancel UNKNOWN or mark proof from the command. |

F2 remains a merge/regression prerequisite: retain P1F's production/legacy interlocks and scalar
`sameResources`; the current-ledger idle fix must not undo those. Re-run its suites plus the real
full-flight/log test and settlement matrix on the integrated source. F12's up-to-date execution
distinction and generic same-epoch stale-copy residual remain recorded, not new proof claims.

## 7. Proposed R-035 update and design-session delivery

### 7.1 Text for Root (proposal, not risk acceptance)

| Required risk detail | Proposed addition/correction |
|---|---|
| Scope | Retain the existing per-world checked transfer-journal/schema-3 entity behavior and production admission-off boundary. Add per-component settlement/resource holds, permanent receipt identities/retired transfers, and the bounded post-release head. Unexpected identities and unproved envelopes lock only their rocket/connected records, not unrelated released groups. Terrain save refusal still affects the whole chunk, and saturation at 256 denial coordinates affects later terrain saves throughout that Level; entity/other stores can continue and diverge. |
| Duration/availability | O1 cannot be rescinded online; a permanent veto/changed observation may hold forever across restarts. Receipt history has no online collection: 64 settled operation members per world's lifetime, counting every choice, and exhaustion refuses every new settlement. Successor-head/proof/UNKNOWN holds persist until exact recovery; they are not cleared by restart alone. Keep the separately open part-1 F3 global journal-pause cost and F8/F9/N2 issues until new evidence closes them. |
| Logs/operator visibility | One bounded line per reason/state/backoff transition, no every-tick polling log and no lifetime cap for continuing transitions/restarts. Inherited Forge/vanilla/store/force errors remain uncapped. Permission-2 status/inspect expose phase, hold reason, retry and capacity; retry never bypasses a veto/manual conflict. Healthy post-release flights must produce no new fence warning. |
| Accounting | Existing legacy zero-loss fields are not conservation evidence. New receipts explicitly say UNKNOWN; preview/confirm/audit must disclose that the selected current quantities may retain loss or duplication. Signed per-fluid accounting remains open and settlement/admission cannot be enabled on this note alone. |
| Recovery | Repair retryable storage faults and let checked work resume; frozen mismatch, missing/invalid authority, capacity exhaustion or Level-lifetime veto requires stopped-server whole-world-copy repair. Back up/hash every Level's region/entities/POI, global data/journals/ledger, player data and metadata; preserve raw originals, validate identities/prefixes/conservation, run two save/restart cycles and fresh barriers, then replace the complete stopped world. Never restore/delete only the escrow/journal or migration backup. |
| Limits | A complete, current acknowledged flight envelope can reconstruct only its exact active identity, never an older credited snapshot. R-032's unobservable writes, direct-store bypass, OS/power loss and arbitrary per-file rollback remain excluded. No new save-veto mechanism or acceptance of R-021 is proposed. |

### 7.2 Delivery record

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
