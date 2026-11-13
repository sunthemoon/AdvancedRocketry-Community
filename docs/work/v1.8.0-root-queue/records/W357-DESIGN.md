# W357: signed per-fluid transfer accounting

Date: 2026-11-13. Status: **PROPOSED; independent read-only review and Root decisions are pending.**
The inspected source is `8e734616ce62bcecb33cf8b085547dfc6d51a858`.
[R434](OWNER-DECISIONS-20261113.md) commissions this design, not implementation or admission.
Fuel, the four cargo kinds and the bay remain disabled in production. No Gate or risk is accepted here.

## 1. Boundary and checked facts

The requirement is an account of a resource transfer, not an estimate of all fluid in a world, a flight-fuel
consumption history, or compensation that manufactures missing fluid. The account supplements, and never replaces,
the native signatures, fixed endpoint references, epochs, markers, invariant, journal lease and store proofs.

The historical gap is explicit in [W11B2](W11B2.md), immediately after continuation 07's cleanup: quantities and
payload hashes cannot reconstruct an unknown historical baseline. [Design 2, section 4](W11B2-DESIGN-2.md#4-unknown-loss-and-unreleased-schema-replacement-f5)
superseded the earlier `audited_loss`/`loss_known` representation with mandatory `accounting_status=UNKNOWN`.
The inspected code still implements that rule. This proposal supersedes only the fluid-accounting exclusion in
[W11CD, section 3.3](W11CD-DESIGN.md#33-settlement-of-a-cargo-operation), subject to Root's approval. It does not
reinterpret its existing `quantity` or `payload_amount` fields.

In the following sections, Java filenames without directories are in the
[resourceescrow package](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowLedger.java).
The relevant current boundaries are:

| Inspected implementation | Consequence |
|---|---|
| `EscrowCodec`, `EscrowSettlementIntent`, `EscrowSettlementTombstones`, `EscrowReceiptCodec` | The ledger and operations are schema 2, intents are schema 2, receipts are schema 3, and typed floors are schema 2. Receipt decoding accepts only UNKNOWN. No signed balance exists. |
| `EscrowLoaderEndpoint.observation`, `EscrowFluidCargoEndpoint.observation` | FLUID_TANK_V1 signs one device tank; its scalar amount alone does not identify its fluid. |
| `EscrowSettlementObserver`, `EscrowSettlementDisk`, `EscrowCargoReadback` | Rocket settlement observations sign the fuel vector and cargo-sensitive invariant. A cargo rocket's `quantity` is propulsion quantity, not cargo mB. The typed snapshot decode can also read the addressed cargo tank. |
| `EscrowReconciliationCheckpoint`, `EscrowFloors.retire` | C6 must prove complete current frontiers, including later applied members. Normal L2 deletes operations and may install a named floor; it does not create a permanent settlement receipt. |
| `EscrowSettlementRecovery`, `EscrowSettlementTombstones` | O1 freezes a choice; O2 proves survivors/absences; O3 installs receipts; O3J acknowledges exact journal removal; O4 releases; pruning removes only retired markers. |
| [W113 task](../TASK-W113-CARGO-RECEIPT-ALLOWANCE.md), [W114 and W114F](W114.md) | Root chose tighter actor/marker charges, not larger existing ceilings. The measured ordinary worst receipts were 1,833 bytes for fuel and 1,557 for cargo. The 90-cell matrix and mixed-component correction must be preserved and extended. There is no tracked `records/W113.md`; W113's external decision note is identified in W114. |

## 2. Quantities, signs and knowledge

### 2.1 Resource scope and identity

For a fuel fill, observe the source loader tank and **both native fluid banks** of the destination rocket. Only
the selected PRIMARY or OXIDIZER bank may change in the planned operation. Including the other bank detects a
fuel/oxidizer substitution that a scalar total would hide. Legacy ABSTRACT quantities are not mB and cannot become
fluid entries. An incompatible representation holds the operation instead of treating it as an empty vector.

For cargo, observe the device tank and exactly the rocket tank addressed by the immutable cargo cursor. The
cursor's snapshot UUID, coordinates and adapter remain mandatory. Other cargo tanks and propulsion are outside
this transfer account; existing whole-envelope/composite/invariant checks continue to protect them. No scan of
all snapshot tanks or world blocks is introduced. This is not a whole-rocket or whole-world zero-loss claim.

A key is `(canonical Fluid registry ID, canonical native-tag digest)`, with an explicitly distinct absent-tag
encoding. Hash the amount-free native envelope with the domain prefix `arce-escrow-fluid-key-1:`, including
whether Tag is absent; do not equate absent Tag with an empty compound. Ordering is ID then digest. Native
admission/readback still compares canonical typed tags;
the saved digest is not permission to materialize an unknown Fluid. Registry-independent durable decoding stays
separate from native materialization, as in R39. Unavailable fluids remain operation/component-local holds.

Each operation moves one such key, by `q` mB, where `1 <= q <= 1,000`. Fuel preparation has at most two distinct
keys because the source equals the selected bank's key; a later observation has at most three occupied units
(one loader tank and two banks). Their union has at most **five keys per fuel operation**. Cargo has one baseline
key and at most two observed keys, hence at most **three**. Empty units are known zero, not a synthetic fluid.
An unexpected replacement Fluid therefore gets its own signed entry instead of cancelling another Fluid's loss.

### 2.2 Balance identities

For key `f`, let `S0[f]` and `D0[f]` be the captured baseline amounts, and `S[f]`, `D[f]` the observed amounts in
the same scope. Positive delta means a gain at that endpoint:

```text
dS[f] = S[f] - S0[f]
dD[f] = D[f] - D0[f]
r[f]  = dS[f] + dD[f]
```

The planned deltas are `-q` at the source and `+q` at the destination for the moved key, zero for every other
key. Source and destination mean transfer direction, not BE and entity: unload makes the rocket the source.
For a proved exact partial application, let `aS,aD` be the source/destination prefix indicators for this member:

```text
outstanding[f] = q[f] * (aS - aD)
dS[f] + dD[f] + outstanding[f] = 0
```

Outstanding is a signed transfer obligation, **not stored fluid or proof of completed conservation**. Source-only
application gives `+q`; destination-only disk persistence gives `-q`, a remaining debit obligation. The latter
can occur because endpoint stores persist separately. Do not label it a settled zero-loss transfer. Normal
retirement requires both applied, outstanding zero, and the joint durable proof.

An operator can explicitly approve an observed residual. Record a signed `loss[f]`, positive for loss and
negative for gain, approved in the preview digest, and check:

```text
dS[f] + dD[f] + loss[f] = 0
```

Computing `loss=-r` alone proves nothing. It is an **operator-approved discrepancy**, not independent proof of
its cause or of safety. A nonzero value is never an automatic correction. Examples for a baseline source 5,000,
destination 0: final 4,000/1,000 gives 0; 4,000/900 gives +100; 5,000/1,000 gives -1,000. Replacing 100 water with
100 oxygen records water loss +100 and oxygen gain -100, never a zero scalar balance.

Knowledge is separate from durability and from the receipt's release flag:

| State | Meaning |
|---|---|
| `PLANNED` | The baseline and intended deltas are captured and committed by L1; no application or persistence is asserted. |
| `KNOWN` | Every endpoint quantity in scope and the original baseline are available. Residuals are computable, but only C6/O2 makes them durable observations. KNOWN is not synonymous with zero loss. |
| `UNKNOWN_BASELINE` | A pre-accounting record has no original typed baseline. Neither a hash nor current quantities can supply it. |
| `UNKNOWN_ENDPOINT` | A required endpoint is missing, unreadable or not proved. Keep known endpoint contributions; do not supply zero for the unknown contribution. |
| `NOT_APPLICABLE` | A diagnostic classification for item operations and bare floor retirement, not an assertion about their resource conservation. Their existing receipt bytes stay unchanged. |

For an absent endpoint, legacy `quantity=0` remains only the old missing-observation placeholder. An absence
proof establishes absence in its checked slot, not how much fluid was destroyed or where it went. That endpoint
could also contain an unenumerated Fluid; listed known keys are not an exhaustive missing inventory. ABANDON can
authorize relinquishing a claim while the loss remains UNKNOWN. Do not add `loss_known`, a scalar `audited_loss`,
or a zero numeric sentinel for an unknown contribution.

### 2.3 Chains and shared endpoints

Fuel admission projects unresolved earlier members. Capture this member's projected baseline, not the live
rocket amount before those predecessors. At C6, subtract the recorded deltas of proved later prefix members
from the complete observed frontier before comparing this member's expected AFTER. Check numeric continuity as
well as signature/epoch continuity. Multiple same-Fluid fills must not each charge the later frontier as their
own gain. OTHER or a broken continuity makes the component REPAIR; it is not a new baseline.

O1 covers an entire connected component. For its net report, count each physical endpoint/resource unit once:
take its earliest baseline, then its final frozen observation. Do not sum overlapping rocket totals from
multiple receipts. Deduplicate by the full typed `EscrowEndpoint.Key` and resource scope, never by UUID alone;
W114F's cross-kind UUID aliases remain distinct endpoints. If operations address different fuel banks on the same rocket, merge their full-bank vectors
once. Assign each key's component discrepancy to the lowest-sequence member whose local baseline/observation
union contains that key; other occurrences reference that owner rather than storing another adjustment. Thus
each row still holds at most five keys, even when a component contains more. The lowest-sequence member also
owns the bounded component member list. The existing smallest-UUID `anchor_op` still anchors READY authority;
these are different purposes. No group can release with a missing key-adjustment owner or different member set.
Individual receipt diagnostics must label shared adjustments as shared, not additive.
The settlement identity uses the deduplicated component deltas, not just the adjustment owner's local deltas;
each member's planned-transfer identity is checked separately.

## 3. Step-by-step recording and checks

| Boundary | Account and required check |
|---|---|
| Preparation, before any reservation | Derive typed baseline vectors from the same immutable native BEFORE/AFTER captures used by the binding. Verify the two projected deltas equal the plan for every key, with unchanged collateral units. Perform all row, O1, receipt and root size checks first. Reject without sequence/reservation/queue/writer/endpoint changes on failure. Existing fairness bookkeeping is not a resource mutation. |
| L1 | Commit operation and baseline account atomically in one checked ledger generation. Only its exact acknowledgement permits application. A staged or UNKNOWN candidate is not permission. |
| C2 apply | BEFORE-to-AFTER permits recompute and compare the typed delta as well as the native signatures. Applying only one endpoint changes the outstanding obligation, not an immutable baseline. No extra per-endpoint ledger write or mutable applied counter is required; markers/prefixes remain application authority. |
| C3 to C6 | Freeze the current frontier and numeric projection together. The own-store and stable typed readback must yield the same vector from the same decoded root used for signature/invariant comparison. Validate prefix-normalized deltas for each member and the deduplicated aggregate, with zero residual for automatic completion. |
| Normal L2 and C8 | Inside the guarded L2 candidate, recheck the exact checkpoint account and jointly live tokens. Delete the operation and its non-receipt-backed account atomically. Install the existing floor if required. C8 changes only markers and must not change quantities. Ordinary successful transfers do not acquire permanent receipt slots. |
| REPAIR | Preserve baseline, plan, payload and known diagnostics. A mismatch must not overwrite the baseline or retire the operation. Repeated observation cannot silently clear a manual reason. |
| Operator preview/confirm | Show per-key endpoint deltas, residual, missing flags and adjustment owner. A nonzero residual is a signed proposed loss/gain; an unknown contribution is printed UNKNOWN. Include all account projections and any approved discrepancy in a schema-3 intent commitment. Confirmation repeats the observations, actor/digest checks and existing 200-tick missing-endpoint interval. A component with any unknown contribution has UNKNOWN total status even if some keys are known. |
| O1 | Freeze all component account projections/adjustments and the confirmed intent in the same ledger generation as the settling operations. No resource is moved. A nonzero KNOWN discrepancy is allowed only because that exact discrepancy was confirmed. UNKNOWN abandonment is explicitly confirmed as unknown, not zero. |
| O2 | Normalization changes only the wrapper. Recompute typed numeric observations from both survivors, or the survivor and checked absence, and compare with O1. Proof renewal after restart uses the frozen account, never a new choice. Any changed vector invalidates the proof even if a scalar total is unchanged. |
| O3 | Install the receipt and its exact final account digest atomically while O2 proofs and the checked journal lease remain valid. No newly inferred loss is allowed here. |
| O3J and O4 | Journal removal remains checked. O4 revalidates the same account/proof commitment before release, without changing baseline, discrepancy, knowledge state or amount. Release of an UNKNOWN account does not make it KNOWN. |
| ABANDON | Retain the original claim and known side, with the missing side UNKNOWN. Store the moved amount as liability separately from any unknown loss. Keep current returning-identity tombstones. Do not rebuild or refund. |
| `settle-floor` | A bare schema-2 floor has no original baseline and no transfer liability. Keep its existing zero `payload_amount` as non-fill metadata and its UNKNOWN receipt; never infer zero historical fluid loss. |
| Pruning and floor collection | Marker pruning preserves all resource vectors and permanent account rows. Receipt-backed rows are never evicted in v1.8. Ordinary named floors remain identity/frontier protection, not numeric history; their collection requires the unchanged exact journal-absence proof. |

## 4. Saved representation and compatibility

### 4.1 Proposed form

Keep the existing 2,048-byte receipt and 12,288-byte operation ceilings. Put the bounded numeric account in the
**same ledger file**, not another SavedData file. A compact `fluid_accounts` list is keyed uniquely by operation
UUID. This avoids both repeated native tags in receipts and a new cross-file durability problem.

| Object | Proposed version and content |
|---|---|
| Ledger | Schema **3**, new epoch `v1.8.0-resource-escrow-fluid-accounting`. Add required `fluid_accounts`. Keep all authority tables, counters and existing count limits. |
| New fluid operation | Schema **3**. Add `accounting_baseline`, a 32-byte canonical digest of its baseline subrecord. Existing native binding, payload/cursor and hashes remain unchanged. The row UUID must match the operation. |
| Account row | Independent schema **1**. Closed short-key form: `v` (int), `op` (UUID), `b` (baseline compound), and optional `f` (frozen settlement compound). Baseline entries contain key ID/tag digest, source/destination initial amounts and planned signed deltas. Frozen entries contain the union keys, present/unknown masks, observed amounts, knowledge code, and signed adjustment only where known. Each shared key adjustment identifies its owner; only the component's lowest-sequence row stores its at most 64 member UUIDs. No free-text reason or native-tag copy is stored. |
| New fluid intent | Schema **3**, existing fields plus one 32-byte commitment over sorted `(member UUID, canonical account-row digest)` pairs. The ordinary intent digest includes it. The row does not contain the intent digest, avoiding a circular hash. Existing numeric observations remain in their old settlement projection. |
| New fluid receipt | Schema **4**, existing audit fields plus `accounting_digest` (32 bytes), and `accounting_status` KNOWN or UNKNOWN. The digest selects that operation's immutable final row, which includes the more precise knowledge reason. Preserve `payload_amount`: fill liability for fuel, zero for cargo. Actual cargo mB live in the account. Append the new field to the dictionary; never reorder its 47 existing entries. |
| Other formats | Existing item/floor operations and intents remain schema 2; their receipts remain schema 3/UNKNOWN. Existing floors stay schema 2, READY/ARMED layouts stay unchanged, and rocket/device schemas, signature kinds, payload hashes, transfer-journal schema and network protocols do not change. |

Rows have one owner even when O3 temporarily has both an operation and its receipt. New fluid operations require
the exact baseline digest. New fluid receipts require the exact frozen row digest and agreement on knowledge,
members and adjustment ownership. Refuse duplicate rows, missing references, orphans, unknown fields, malformed
hashes, negative amounts, excessive cardinality and an incorrect balance. Do not accept an absent row as legacy
UNKNOWN unless the explicitly decoded operation/receipt has its old version. Mixed-version paths are explicit,
not a catch-and-fallback reader. Append-only log records and a generic accounting framework are unnecessary.

### 4.2 Migration and NO_FILE

The production-world claim is conditional, not a census of user worlds. At the inspected source,
`EscrowCoordinator.PRODUCTION_ADMISSION` is false and each cargo production switch is false. First-transfer
creation goes through `EscrowLedgerCreation` after successful eligibility/reservation from NO_FILE.
`ResourceEscrowSavedData.existing` is load-only, and `EscrowLedgerStartup` distinguishes proven absence from
NOT_LOADED. The bay has a separate receipt store; it does not create this ledger. Thus a fresh ordinary world
that never used gated hooks or an enabled development build does not create RESOURCE_ESCROW. Restarting such a
world does not need an escrow migration or create an empty ledger. All five switches being off **does not remove
an existing ledger**, and the separate fuel switch must also remain off for this argument.

Migration concerns release-test/GameTest worlds, imported or manually edited worlds, and any world previously
run with admission enabled. Do not claim none exist merely because production admission is disabled today.

Recommended migration is a lossless **root 2 to root 3** upgrade in the pre-start checked migration boundary:

1. Validate the complete root-2 form using a pinned legacy reader and unchanged bounds. Keep a whole-world backup.
2. Retain original operation-2, intent-2 and receipt-3 encodings and their digest rules. Add an empty account list;
   do not change embedded member digests, reconstruct baseline amounts, or create READY authority.
3. Old live fluid records are explicitly UNKNOWN_BASELINE. Hold ordinary reconciliation before another C2 apply
   or L2 retirement. Infer that diagnostic from the old record version; do not rewrite its saved reason or bytes.
   Existing operator settlement can complete as a legacy UNKNOWN decision with existing proofs; an already
   frozen O1 resumes its original decision unchanged. Already released receipts stay released and UNKNOWN.
4. Preserve floors and heads unchanged. A new operation after migration captures a new baseline; this does not
   fill historical gaps in old receipts or floors. New schema-3 records never inherit the legacy exemption.
5. Validate the target root and all reservation invariants before any replacement. The new header can exceed a
   completely full old root; return a bounded migration refusal and preserve the original file, rather than
   truncate or increase 1 MiB. A root already violating existing canonical re-encoding/reservations is likewise
   not repaired by migration. The target write is checked and must be acknowledged before any action.

Support root 2 explicitly in `ManagedSavedDataType`/`SavedDataSchemaMigrator`, including its historical epoch;
do not accidentally enable root-1 or other legacy schemas through the generic legacy flag. Invalid or future
roots keep the current whole-world pre-start refusal/preservation policy. A refused migration leaves no partial
in-memory upgrade. A failed/UNKNOWN replacement does not expose authority; restart resolves the exact file.

### 4.3 Older JARs and R-044

| Reader | Expected behavior, separated from measured history |
|---|---|
| Current root-2 JAR, including C3 | Its managed-type version check rejects root 3 as future; its exact `EscrowCodec` also cannot decode the new form. It must not write a downgraded ledger. A new native downgrade test must verify this; it has not run. |
| Earlier escrow-aware development JARs | Root/epoch and exact operation/receipt readers reject unsupported new forms. Root-1-only readers already reject root 2. Do not promise all historical pre-start implementations have identical side effects without testing the pinned JAR. |
| JARs predating RESOURCE_ESCROW | There is no universal escrow refusal mechanism in a binary that does not know the file. It may ignore it and separately reject/quarantine newer entities or remap missing device IDs. This is unsupported, not a safe downgrade. |

R-044 is **not fixed**. [C3, sections 2.1 and 8.5](C3-SWITCH-REPORT.md#85-the-downgrade-family) records the old JAR
rewriting `level.dat` and `level.dat_old` before rejecting the future transfer journal. This proposal does not
change those files' write ordering or registry IDs. It adds a future escrow root as another rejection reason
for escrow-aware old JARs, potentially even when no governed transfer record exists. A refusal cannot be called
whole-world byte preservation. Keep `cargo-G2-REUSE` rows 3 to 5 and `w336-downgrade` FAIL, not waived or passed.
The [draft sign-off](R044-SIGNOFF-DRAFT.md) is unsigned; Root should review its scope for the additional ledger
schema boundary. Recovery remains the complete pre-upgrade/pre-downgrade world backup, never just this `.dat`.

## 5. Bounds and admission charges

All new limits below are proposals inside the unchanged existing ceilings, not measurements of implemented codecs.

| Field/work | Hard bound and accounting |
|---|---|
| Account list | At most 128 rows, and exactly the union of new fluid live-operation and permanent-receipt IDs: at most 64 + 64, counting O3 overlap once. A row is at most **4,096 uncompressed bytes**, depth 8, 256 nodes. Root remains 1,048,576 bytes, depth 24, 131,072 nodes. The count maximum does not promise all maximum-size objects fit together. |
| Keys and arrays | Baseline at most 2 keys; frozen union at most 5 (cargo 1 and 3). IDs retain `EscrowTags.id`'s 128 printable ASCII characters; digests are exactly 32 bytes, UUIDs exactly 16 bytes. Present/unknown codes are fixed enums/bitmasks, not strings. A baseline entry has exactly four longs (two initial amounts, two deltas). A frozen entry has up to two observed-amount longs, one optional known adjustment long and one adjustment-owner UUID; masks govern omissions. No other variable-length numeric array is allowed. |
| Groups | At most 64 members, at most 128 physical endpoints, at most 320 member/key entries before deduplication. Only the lowest-sequence row carries the member list, at most 64 UUIDs; this list is included in the 4 KiB/256-node bound. Account/intent digests commit the same member set. |
| Amounts | Use checked signed `long` arithmetic throughout. Loader units remain 0..16,000. Cargo amounts retain their native capacity checks. Fuel bank bound is `RocketLimits.MAX_BLOCKS * 1,000` (2,048,000). Absent amounts have no numeric value. Aggregate arithmetic is checked even though these limits are well below long overflow. |
| Existing envelopes | Native fluid/frame remains 8,192 bytes/depth 12/384 nodes; operation remains 12,288/depth 16/512 nodes; receipt remains 2,048/depth 16/128 nodes; endpoint 512; floor/head/ARMED 1,024. Do not copy native fluid NBT into every numeric key or the receipt. |
| Per-Level and tick work | Accounts belong to the overworld ledger, with no new per-Level persistent map or index. Reuse the existing maximum of 64 Level services, eight cheap reconciliation visits, at most one ordinary component capture and one publication per tick, existing cargo preparation reservations, and the independent one-endpoint pruning visit. Numeric projection reads only already decoded captured units, at most three units per fluid member; it adds no world traversal, ticket or native decode. O1/O2 component work remains bounded by 64 members/128 endpoints and the existing 16 MiB retained capture ceiling. Do not repeat a full account walk for every unchanged member visit. |

An account row's short-key layout fits comfortably in 4 KiB by construction: at most seven encoded key entries
(two baseline plus five frozen), each with a 128-byte ID, 32-byte digest and a fixed small number of longs/flags,
plus at most 1,024 bytes of group UUID payload and bounded framing. The implementation must pin the exact worst
encoding, depth and node count; this is not a substitute for real-codec measurement.

The existing dictionary has 47 fields. Appending `accounting_digest` gives the three-character wire key `f1b`.
One 32-byte NBT byte-array field adds **42 bytes** (type 1 + name length 2 + name 3 + array length 4 + data 32).
If no other field expands, W114's ordinary fixtures would become 1,875 bytes for fuel and 1,599 for cargo,
leaving 173 and 449 bytes. These are layout calculations, **not measurements or new pins**; UNKNOWN/KNOWN string
lengths, legacy names, neutral forms, mixed groups and the new account node must be encoded in the real tests.
The operation's baseline hash adds 58 bytes under the descriptive key `accounting_baseline`; the intent's new
commitment also costs bytes/nodes in its embedded copies. None may bypass the complete-operation bound.

Extend the shared `EscrowSettlementCapacity` preview and reservation, not admission alone. Charge each occupied
account at its 4 KiB ceiling, reserve future frozen growth, count O3 operation/receipt overlap once, and preserve
the existing full receipt, floor, READY and ARMED ceiling charges. R6 floor-retirement headroom remains available;
bare floor retirement creates no fluid row, so its own current charge need not grow. A candidate must prove O1,
O3 and O4 fit for its full component before L1. Both admission and unrelated-component freeze must use the same
rule. Reserve the group-owner member list even before a shared adjustment exists. Root occupancy may reduce useful
concurrency; do not reuse W114's counts as measured new capacity or raise a ceiling to recover them.

## 6. Failures, holds and logging

| Case | Required accounting and action |
|---|---|
| Write UNKNOWN at L1, O1, L2, O3 or O4 | Retain the exact candidate, rows, digest, reservations and locks. Do not publish a second baseline, release a receipt, prune a row, or advance based on live quantities. Existing checked readback/retry resolves old versus candidate; old acknowledgement is not acknowledgement of this candidate. Baseline knowledge may remain available while durability is UNKNOWN. |
| Missing endpoint | Ordinary reconciliation holds. ABANDON retains the known side and UNKNOWN loss, with absence/identity proofs and permanent negative protection. No refund and no assumed missing-side zero. An unavailable/unloaded slot is not proven missing. |
| Numeric mismatch on C2/C6 | Publish no new resource effect or L2. Persist component-local manual `ACCOUNTING_MISMATCH` REPAIR, retain the original account, and expose expected/observed signed diagnostics. Other components continue subject to the existing shared writer. |
| Mismatch after O1 | Hold the same decision before O3/O4; never change the frozen choice or manufacture a new discrepancy during readback. Offline repair is required if the choice cannot be restored. A new operator confirmation is possible only through a separately authorized replacement design, not this slice. |
| REBUILD_SOURCE / X03 | [C3 section 7.3](C3-SWITCH-REPORT.md#73-applicable-risks) and `EscrowCargoRecoveryCuts` show a pre-unload source snapshot of 5,000 versus latest cargo 4,000 plus device 1,000. Reconstructing the old source would create +1,000 water in that scoped balance. The existing FLOOR_CONFLICT must remain; a signature/account is not permission to rebuild. After normal L2 the transient account can already be gone, so the existing floor/identity fence, not a claimed retained numeric history, is decisive. X03 stays NOT_COVERED until the separately authorized whole-world repair campaign proves the repaired copy and two restarts. |
| cut-E2 / external entity record | Even a numerically zero residual cannot retire without the supported inline own-store/readback proof. Keep the account/operation and endpoint locks with BARRIER_EXTERNAL. Do not add `.mcc` support or bypass the inline-sector bound. This is not evidence of zero durable loss. |
| Oversize new candidate | Return the existing typed size/capacity refusal before state mutation; distinguish permanent cargo size from transient root capacity as existing C-E does. No truncation, asynchronous save exception or new terrain-save veto. |
| Malformed saved account/reference | Existing pre-start invalid-root policy refuses the whole world and preserves bytes. A semantic native mismatch discovered after structural decode is local REPAIR, not a newly invented whole-world corruption classification. |

Check arithmetic at capture, before every apply permit, after typed C6/O2 readback, and again in the guarded
L2/O3/O4 candidate derivation. The IO thread produces immutable typed vectors; the owner thread compares them
with the captured account and confirms authority. Merely extending the live observer but not the readback
validator would leave the same-total/wrong-Fluid hole. Preview/confirm and `inspect` must report per-fluid values,
knowledge and durability separately. Do not assert `loss=0` in existing success log lines without that check.

Use bounded result values for ordinary mismatch, bounds and capacity refusal. Codec exceptions may describe
invalid input at decoding boundaries, but must not implement ordinary save rejection. Before code merges, Root
must add the new holds/refusals to R-035 and an ADR-065 implementation note under AGENTS section 3.3.

New mismatch logging is at most one WARN `ARCE_ESCROW_ACCOUNTING_HELD` per operation per server session, plus one
INFO `ARCE_ESCROW_ACCOUNTING_RELEASED` if operator settlement later releases it. Remember at most 64 held IDs;
do not reset that allowance every 200-tick retry. Operator audit is one INFO per acknowledged O1 decision, with
at most 64 members/320 key rows and each key ID at most 128 characters; explicit inspect is demand-driven and
paginated to at most 16 key rows per response. No native tag body is logged. Migration refusal logs one ERROR
per start. Existing writer, vanilla/Forge save-error and reason-transition logging retain their existing limits
and may have no lifetime cap; the new local cap does not bound those inherited streams or repeated restarts.

## 7. Verification to implement

These are planned checks, not results. No test expectation is weakened to accommodate a new format.

### 7.1 Unit and format checks

Add pure arithmetic/account-codec tests for all four partial-application combinations, q=1/1,000, two fuel banks,
both cargo directions, zero/empty units, typed tag differences including absent/empty Tag, same-total different
Fluids, gains, losses, overflow, missing quantities, cardinalities, duplicate keys and mismatched references.
Check chains in the same bank, alternating banks, partial retirement and a deduplicated connected component.
Check shared adjustment ownership without double counting, and that a 64-member owner row stays bounded.

Pin root-2 migration fixtures with live, frozen-O1, unreleased-O3 and released receipts, bare floors, heads and
ARMED entries. Verify exact old operation/intent/receipt bytes and digests survive, UNKNOWN is not promoted,
new schema-3 rows cannot omit their proof, invalid/future roots are preserved, and a full target refuses without
replacement. Stop/retry tests cover the migration write and checked baseline, including UNKNOWN outcomes.

Existing expectations that need extension or version-specific fixtures include:

- `EscrowCodecTest`, `EscrowSettlementFormatTest`, `EscrowSettlementCompatibilityTest`,
  `EscrowCargoSettlementTest`, `EscrowCargoSettlementObservationTest`, `EscrowFluidDurableValidationTest`,
  `EscrowOperatorDiagnosticsTest`, `EscrowSettlementIntentTest` and `EscrowSettlementWriterTest`: new rows,
  versions and explicit knowledge; old UNKNOWN pins remain for legacy/item/floor forms.
- `EscrowReconciliationCheckpointTest`, `EscrowReconciliationBindingBoundaryTest`,
  `EscrowReconciliationRetirementTest`, `EscrowReconciliationActionTest`, `EscrowNativeBankPrefixTest`,
  `EscrowOrdinaryProofTest`, `EscrowRecoveryFenceTest` and writer/creation tests: numeric projection and no
  retirement/release before exact acknowledgement. Replayed callbacks must not attach another row.
- `EscrowReceiptChargeTest`, `EscrowMixedComponentCapacityTest`, `EscrowSettlementCapacityTest`,
  `EscrowCargoCapacityTest`, `EscrowItemCapacityTest`, `EscrowItemDimensionCapacityTest`,
  `EscrowLayoutBudgetTest`, `EscrowCodecIntersectionTest`, `EscrowPrepareFillTest`,
  `EscrowReviewReservationsTest`, `EscrowFlightTableReservationTest`, `EscrowReservationTransitionTest`,
  `EscrowReservationWalkTest` and `EscrowFlightBenchmarkCeilingTest`: repeat W113/W114's real-codec boundary
  measurements, all 90 cells, maximum actor compatibility, mixed UUID alias, sixth-fuel-marker rejection,
  queued/UNKNOWN/O1/O3/O4/root reservations and the exact 1 MiB benchmark. Re-pin measured intersections only;
  keep every ceiling, assertion purpose and refusal-side-effect check.
- Item settlement/compatibility tests retain their schema-3 UNKNOWN and zero diagnostic quantity. Their root
  framing changes to schema 3 only in new fixtures; item payload, receipt bytes and resource-signature pins do
  not change. The new row charge must not discount item operations in mixed components.

### 7.2 GameTests

Extend `FluidFuelLoaderEscrowGameTests` and `FluidFuelLoaderLedgerCreationGameTests` for both banks, first
NO_FILE creation and switch-off no-file/no-row behavior. Extend `FluidCargoAdmissionGameTests`,
`FluidCargoReconciliationGameTests` and `FluidCargoSettlementGameTests` for both directions, both survivor and
both ABANDON choices, neutral binding/veto, known loss/gain and unavailable fluids. Extend
`RocketSettlementNormalizationGameTests`, `EscrowOperatorGameTests`, existing pruning tests and
`CargoRecoveryAcceptanceGameTests` for account preservation across normalization, O3 restart, READY launch,
pruning, stale recovery and unrelated held components. Add a same-total/different-Fluid readback rejection and
prove the healthy component continues. Item and bay tests retain their resource/authority outcomes; no switch opens.

### 7.3 Native cuts, families and oracle changes

The 58-family inventory is generated by `run_v180_integrated_campaign.family_specs`; R438 requires every family
to run again on the integrated C4 candidate. New cases below are additions, not substitutes for existing rows.

| Existing family/oracle | What changes in its verification |
|---|---|
| `cargo-C-S1`, `cargo-C-S2`, `cargo-C-S3` | Fuel-independent fluid load/unload cuts now decode root 3 and verify L1 baseline rows, partial obligations, C6 zero balance, L2 account deletion, UNKNOWN retention, external-store holds and root-capacity refusal before effects. Keep K0-K10, D2 and R1/R3 behaviors. Shared `cargo_s1_oracle.py`, `cargo_storage_oracle.py`, `cargo_recovery_oracle.py`, `loader_cut_nbt.py` and their runner tests need explicit version-aware account checks. |
| `cargo-C-S4`, `cargo-C-S4-LOAD`, `cargo-C-S5` | Extend `run_v180_cargo_s4_s5_cuts.py` and `tests/test_cargo_s4_s5_cuts_runner.py`: new fluid receipts may be KNOWN with a zero signed residual, instead of mandatory UNKNOWN; missing-endpoint cases remain UNKNOWN. Preserve `payload_amount=0`. Check shared adjustment once, frozen account/digest through O1/O2/O3/O3J/O4, PRUNE, veto, both restarts and FF_REBUILD_SOURCE's unchanged protected conflict. |
| `cargo-B3-S1-REUSE`, `cargo-B3-S4-REUSE`, `cargo-B3-S5-REUSE`, `cargo-B3-S6-REUSE` | Loader baseline/readback, owner bounds, floor, missing and ordinary/governed-flight families retain their physical outcomes. Extend `loader_cut_oracle.py`, `loader_named_cut_oracle.py`, `loader_s6_integrity.py`, `loader_s6_abandon.py`, `loader_s6_normalize.py`, `loader_s6_chain.py` and `loader_s6_accounting`'s Java probe to distinguish transfer accounts from later flight fuel debit. Ordinary `LF_S6_F9_RESPAWN` stays NOT_COVERED. |
| `cargo-D-S1`, `cargo-D-S2`, `cargo-D-S2-LOAD`, `cargo-C-Q` | Item-account expectations do not become fluid KNOWN. Root decoding/framing and mixed/shared capacity expectations change; item contents, per-owner 16, authority and refusal assertions remain. Extend item/cargo contention oracles only where they inspect the root/receipt. |
| `cargo-G2-REUSE`, `w336-downgrade` | Keep the historical failures. Add root-3-ledger-only downgrade/restore cases with the pinned root-2 reader, separately from the existing transfer-journal-schema-3 failure. Verify whole-world bytes, refusal reason, full-backup restoration and two candidate restarts. |
| `r042-L_LEDGER`, `r042-U_LEDGER`, `r042-P_SERVER`, `r042-P_OWNER`, `w336-saturation-bay`, `w336-saturation-item-load`, `w336-saturation-item-unload`, `w336-saturation-fluid-load`, `w336-saturation-fluid-unload`, `w336-saturation-fuel` | Same missing/unloaded/capacity/shared-pool outcomes. Update valid synthetic ledger fixtures to the appropriate root version; include account rows in byte and ownership checks, without changing 64/server, 8/rocket, 16/owner or shared 256-coordinate limits. |
| Other `forced-*`, `released`, `exit`, `r042-N_BAY`, `r042-U_BAY`, `w336-timing-*` families | Rerun unchanged behavioral oracles on C4. They may transitively use the common ledger decoder, but bay receipt schemas/outcomes and timing budgets do not change. Performance collection stays in the owner's scheduled phase. |

The shared parser `run_v180_escrow_forced_stop.ledger` currently hardcodes root 2, and
`inspect_escrow_receipt.decode_receipt` hardcodes schema 3/UNKNOWN. Both need explicit old/new readers, account
reference validation, signed recomputation and negative-control tests. Do not simply broaden accepted versions
or replace `UNKNOWN` with `KNOWN` in a string assertion. Its callers include the escrow forced-stop,
reconciliation, pruning, phase, destination, arrival, terminal, return, source-clock and pre-F2 runners, plus
the rocket-audit and loader/cargo runners. Old fixture families may deliberately remain legacy UNKNOWN;
native fresh-fluid cases must not silently take that branch. Update `test_inspect_escrow_receipt.py` and all
affected runner unit tests. Audit downstream imports before writing the implementation TASK.

Add native cuts immediately before/after account-bearing L1 and O1 replacement, before/after account-checked
L2, before/after O3 and O4 replacement/callback, and before/after pruning persistence. Each takes two restarts.
Add known +loss, known -loss/gain, two-fluid substitution, chained-bank and ABANDON-unknown cases, exact UNKNOWN
candidate retries, and a mismatching typed stable readback that cannot release. Add root-2 upgrade cuts and
root-3 old-JAR refusal/whole-backup restoration. These are ordinary disposable-process cuts, not X01 power loss
or X02 destructive fault injection. X01/X02 and X03's operator repair remain separate and require their stated
authorization; accounting does not close them.

For each code slice run isolated new Minecraft-touching classes with `MinecraftBootstrap.initialize()`, then
the full unit suite. Integration runs `clean build`, `runData` plus clean generated diff, the full GameTest and
unchanged log checker, and native tests against a committed candidate/JAR hash. Add exact bounded expectations
for deliberately introduced logs and update the pinned manifest total, never its generic allowances. Heavy JVM
runs use the machine lock, disk checks and owned cleanup from WORKER-RULES. Actual runtime, row overhead and
reference-budget results must be measured, not inferred from this design.

## 8. Reviewable implementation slices and estimates

Estimates are focused engineer-days, excluding queue wait, remote/client scheduling and unforeseen findings.
Each slice includes targeted tests and a short commit/command/result record. Root assigns disjoint worktrees;
the sequence below is not permission for this worker to delegate or implement.

| Slice | Concrete files/areas | Estimate and exit evidence |
|---|---|---|
| A. Pure account and size proof | New `EscrowFluidAccount`, `EscrowFluidAccountCodec` and arithmetic tests; extend existing charge/layout tests using real encoders. | 1.5-2 days. Key/sign/chain/group cases and measured worst row/receipt/operation/O1 fit; return to Root if any bound cannot hold. |
| B. Saved form and migration | `EscrowLedger`, `EscrowCodec`, `EscrowOperation`, `EscrowReceiptCodec`, `EscrowSettlementIntent`, `EscrowSettlementTombstones`, `EscrowSettlementCapacity`, `EscrowLedgerWriter`; Root-owned `ManagedSavedDataType`/`SavedDataSchemaMigrator` integration. | 2-3 days. Root-2 fixtures remain byte/digest faithful; root-3 round trips, reservations and failed/UNKNOWN migration prove preservation. Another session reviews the saved-form diff before merge. |
| C. Native capture and normal completion | `EscrowLoaderAdmission`, `EscrowCargoPreparedCapture`/eligibility, `EscrowLoaderEndpoint`, `EscrowFluidCargoEndpoint`, `EscrowReconciliationChange`/action, `EscrowReconciliationCheckpoint`, `EscrowSettlementDisk`, `EscrowCargoReadback`, normal retirement and `EscrowFloors`. | 2-3 days. Real fuel/both cargo directions, prefix-normalized and stable-readback arithmetic, no-effect refusal and E2/UNKNOWN coverage. No new permanent normal-transfer history. |
| D. Operator lifecycle and diagnostics | Observer/proofs/recovery/operator/diagnostics/pruning files, `EscrowReason`, account commitment and receipt creation/release; Root adds ADR-065/R-035 text and manifest changes. | 1.5-2.5 days. O1-O4, signed nonzero approval, legacy/missing UNKNOWN, both ABANDON survivors, pruning and READY flight GameTests. |
| E. Oracles and native evidence | `inspect_escrow_receipt.py`, common native ledger decoder, the named loader/cargo/item oracles and runner tests; gated native fixture/probe additions only where necessary. | 2-3 days to implement targeted native coverage; allow another 1-2 days of serial execution/review for the complete candidate campaign and corrections. R-044 failures remain visible. |

Total implementation/targeted verification is approximately **9-13.5 engineer-days**, plus **1-2 days** for the
full serial candidate rerun and review. Performance/real-client campaigns and separately authorized X01-X03
work are not included. Do not leave a partially integrated new format reachable: keep admission off, stage the
writer/version activation until its consumers and migration work together, and run hosted full regression on
the integrated batch. A saved-form rollback after test worlds have written root 3 requires their whole backup,
not cherry-picking out the codec while keeping those worlds.

## 9. Proposed register/ADR text for Root

**R-035, signed fluid-accounting holds and format.** New fluid fills and cargo transfers retain a bounded typed
baseline and a signed per-fluid account in escrow root schema 3. A native arithmetic/readback mismatch holds
the operation's connected component, including its device and rocket, in manual REPAIR without changing or
discarding resources. A frozen settlement mismatch holds that same component before release. Duration is
unbounded until the exact frozen state/proofs can be restored or a stopped-server whole-world repair is
completed; restarting alone is not repair. New accounting diagnostics are capped at one held WARN per operation
per session (at most 64 remembered IDs), one release INFO when released, and one audit INFO per acknowledged
decision with at most 64 members/320 key rows. Existing writer and vanilla/Forge retry logs retain their
separate, potentially unbounded lifetime behavior. There is no new terrain/Level save veto and no expansion of
the shared 256-coordinate GuardedChunkSaves pool.

Root-2 development ledgers migrate without inventing typed historical quantities. Live old fluid records have
UNKNOWN_BASELINE and cannot automatically apply or retire through the new numeric path; existing audited UNKNOWN
settlement or whole-world offline repair is required. A valid old root whose upgraded encoding/reservation does
not fit is refused at whole-world pre-start, original files preserved, one ERROR per start, until the copy is
repaired/reduced through a compatible build or the complete backup is restored. Invalid/future root-3 accounts
use the existing whole-world pre-start refusal. New capacity checks refuse only the prospective admission
before resources, queue or writer change, and last until space is available; permanent 64-receipt exhaustion
still needs offline recovery, with no eviction. More account bytes can reduce concurrent byte capacity.

ABANDON records unknown missing contents honestly and keeps returning identities locked; signed accounting
does not authorize rebuilding an old source, refunding a loss or ignoring a floor. REBUILD_SOURCE/X03,
unobservable-storage/X02 and OS/power-loss/X01 residuals remain open. Root-3 creates an additional unsupported
downgrade boundary; old-JAR refusal does not guarantee world metadata preservation. Keep the existing R-044
FAIL results. Recovery requires stop, hash-backed backup of every Level's terrain/entities/POI, all global
data/journals/receipts/accounts/player data and world metadata, repair and conservation checks on a copy, two
saves/restarts with fresh barriers, then whole-world replacement. Never restore an isolated ledger or migration
backup. Risk registration and an independent read-only review precede any implementation merge; neither is
owner acceptance or admission approval.

## 10. Numbered Root decisions

1. **Approve the numeric scope and signed discrepancy policy?** Recommend both propulsion banks for fuel,
   addressed tank only for cargo, typed per-fluid keys, automatic zero-residual completion only, and explicit
   digest-confirmed positive-loss/negative-gain operator acceptance. Missing contents remain UNKNOWN after
   ABANDON. This neither supplies a whole-rocket cargo audit nor an item count balance. Item kinds need a
   separate item-ID/typed-tag/count account if Root later requires the same numerical auditability: their
   one-stack payload and signatures protect replay but cannot prove an unknown count baseline. Do not label
   their current diagnostic zero as that proof or add item accounting to this fluid slice.
2. **Approve the same-file account list rather than larger receipts or duplicated native tags?** Recommend the
   bounded 4 KiB rows, one fixed digest in each new fluid receipt, unchanged existing ceilings and shared full
   reservations. Slice A must supply real-codec measurements before version activation; a failed fit returns
   for a decision, not a raised bound. Root must accept the possibility of lower effective byte concurrency.
3. **Approve lossless root-2 migration with explicit legacy UNKNOWN holds?** Recommend retaining old embedded
   encodings/digests and the old UNKNOWN operator completion path, not direct rejection of every old test
   ledger and not fabricated baseline migration. This is a change from Design 2's historical direct-replacement
   policy and needs a Root-approved ADR implementation note. Explicitly decide the whole-world target-capacity
   refusal and its recovery before code; no implementation task should leave that policy implicit.
4. **Schedule the required review and native downgrade extension?** Recommend the different-engine read-only
   review required by R434 before dispatch, then the slices above and C4's complete pinned rerun. Root should
   update R-035/ADR-065 and review the unsigned R-044 draft's format scope before merging. No existing failed or
   NOT_COVERED row becomes passed by accepting this note.
