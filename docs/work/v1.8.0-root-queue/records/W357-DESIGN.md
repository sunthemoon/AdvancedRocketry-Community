# W357: signed per-fluid transfer accounting

Date: 2026-11-13. Status: **REVISED after W357R, under Root decisions R440 and R441.** W357R is the independent
read-only review of this note at `4cf28c497` (verdict APPROVE-WITH-CHANGES; `REVIEW.md` in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W357-review/`, SHA-256
`21f26acc525e4945d521f0edb05e9e342fd1dfad16bbd7373caede34599ddc14`). This revision resolves its findings 1 to 9 and
records Root's four answers in section 10. Nothing in this note is implemented.
The inspected source is `8e734616ce62bcecb33cf8b085547dfc6d51a858`; the code cited by the revision was read at
`4cf28c497`, which changes documentation only.
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

Amounts are **aggregated per endpoint and key**. For the rocket endpoint of a fuel fill, the amount of key `f` is the
sum of `f` over both banks. This conserves each key and still catches a fuel-for-oxidizer substitution, because
those keys differ. It does not by itself prove which bank holds the fluid. The code does not exclude the same key in
both banks: `RocketFluidFuelCatalog.parse` (lines 67 to 69) rejects only a duplicate `(role, Fluid)` pair, so a
configured catalog may allow one Fluid as both BIPROPELLANT_FUEL and OXIDIZER, and `BankSelection.plan` (lines 53 to
61) refuses AMBIGUOUS_BANK only when both compatible banks have room. A move of one key between the two banks is
therefore invisible to the account. Bank placement stays protected by the existing fuel-vector signature:
`RocketPropulsionFuel.resourceSignature` (lines 27 to 30) hashes the PRIMARY and OXIDIZER bank signatures
separately and in order, and each bank signature covers its Fluid, tag and amount. The account never replaces that
check.

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

A proved partial application that the operator accepts is recorded the same way. Under ACCEPT_CURRENT, an
obligation that is still outstanding at O1 is frozen as the signed discrepancy, `loss[f] = outstanding[f]`. The
preview shows it as a **half-applied transfer** and names the side that was applied. The third example above is the
destination-only case: `outstanding = -1,000`, so the receipt's account records a gain of 1,000. The source-only
case records a loss of `+q`. A receipt therefore never reads as a completed transfer with zero residual when only
one side was applied.

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

Admission projects unresolved earlier members. Capture this member's projected baseline, not the live
amount before those predecessors. At C6, subtract the recorded deltas of proved later prefix members
from the complete observed frontier before comparing this member's expected AFTER. Check numeric continuity as
well as signature/epoch continuity. Multiple same-Fluid fills must not each charge the later frontier as their
own gain. OTHER or a broken continuity makes the component REPAIR; it is not a new baseline.

These two rules, the projected baseline and the later-prefix subtraction, apply to **every endpoint that has a
prefix chain**: the rocket's banks, the loader tank, the cargo device tank and the addressed rocket cargo tank.
Cargo is not exempt. `EscrowSettlementCapacity.CARGO_ADMISSION_PER_ROCKET` is 1, which limits one rocket to one
unsettled cargo operation, but one device can serve several rockets in turn, a loader tank can feed several fills,
and decoding accepts up to eight markers on an endpoint. An endpoint with a single marker is the chain of length
one, and the same code path handles it.

A projected baseline is built only on predecessors whose own baseline is known. Admission therefore **refuses a new
fluid operation whose prospective component contains a legacy schema-2 fluid record** (a record without an account
row, section 4.2). The refusal is the ordinary typed admission result before any reservation, sequence, queue,
writer or endpoint change, and it lasts until the legacy record is settled or retired.

A connected component can still mix kinds of member, because a new fluid member and an item member can share a
rocket or a device (the W114F mixed component), and a later item operation or a migrated root can connect a
schema-3 fluid member with a legacy schema-2 fluid member. The rule for every such component is:

- the settlement intent is schema 3 whenever at least one member has an account row, and schema 2 only when no
  member has one;
- the intent's account commitment covers exactly the row-bearing members, in member order;
- item members and legacy fluid members have no row and keep schema-3 UNKNOWN receipts; only row-bearing members
  get schema-4 receipts;
- a component with any legacy fluid member has total status UNKNOWN, whatever its row-bearing members know;
- an item member does not change the fluid total status, and its own receipt stays UNKNOWN as today.

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
| Operator preview/confirm | Show per-key endpoint deltas, residual, missing flags and adjustment owner. A nonzero residual is a signed proposed loss/gain; an unknown contribution is printed UNKNOWN. A remaining outstanding obligation is shown as a half-applied transfer and proposed as its signed discrepancy (section 2.2). Include all account projections and any approved discrepancy in a schema-3 intent commitment. Confirmation repeats the observations, actor/digest checks and existing 200-tick missing-endpoint interval. A component with any unknown contribution has UNKNOWN total status even if some keys are known. |
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
| Intent of a component with at least one account row | Schema **3**, existing fields plus one 32-byte commitment over the `(member UUID, canonical account-row digest)` pairs of exactly the row-bearing members, in member order. The ordinary intent digest includes it. The row does not contain the intent digest, avoiding a circular hash. Existing numeric observations remain in their old settlement projection. Every member of the component, including item and legacy members, embeds this same intent, as `validateComponents` requires. |
| New fluid receipt | Schema **4**, existing audit fields plus `accounting_digest` (32 bytes), and `accounting_status` KNOWN or UNKNOWN. The digest selects that operation's immutable final row, which includes the more precise knowledge reason. Preserve `payload_amount`: fill liability for fuel, zero for cargo. Actual cargo mB live in the account. Append the new field to the dictionary; never reorder its 47 existing entries. |
| Other formats | Item, floor and legacy fluid operations remain schema 2, and their receipts remain schema 3/UNKNOWN in every component. Their intent remains schema 2 when no member of the component has an account row; in a mixed component they embed the component's schema-3 intent. Existing floors stay schema 2, READY/ARMED layouts stay unchanged, and rocket/device schemas, signature kinds, payload hashes, transfer-journal schema and network protocols do not change. |

**Mixed components.** Section 2.3 gives the rule. In saved form it means:

- An operation's own schema (2 or 3) and the schema of the intent it embeds are independent. The decoder selects
  the intent reader from the intent's own version, and a schema-2 operation that embeds a schema-3 intent keeps
  every other byte of its schema-2 form.
- A schema-3 intent with an empty commitment set is invalid, and so is a schema-2 intent in a component that has a
  row-bearing member.
- The commitment lists the row-bearing members only. A row for an item or legacy member is an orphan and is refused.
- O3 writes a schema-4 receipt for each row-bearing member and a schema-3 UNKNOWN receipt for each other member.
- When the component has a legacy fluid member, every schema-4 receipt in it carries `accounting_status` UNKNOWN,
  and its frozen row records the knowledge reason UNKNOWN_BASELINE for the component total. The row's own known
  per-key amounts are kept as diagnostics and are never reported as a component balance.

**Receipt schemas 3 and 4 in the lock predicates.** `EscrowSettlementTombstones` compares `schema_version` with the
single constant `SCHEMA` at five places: `installed` (line 53), `logicalLocked` (line 102), `identityLocked`
(line 119), `chosen` (line 150) and `endpointLocked` (line 160). A missed site would silently weaken a protection,
for example `chosen` would ignore a released schema-4 receipt and `minimumEpoch` would return 0. Slice B therefore
replaces every such comparison with **one shared settlement-receipt test** (a single method that accepts exactly
schemas 3 and 4 and nothing else), used by every lock and release predicate in the package. No predicate keeps an
equality with one constant, and no predicate distinguishes the two schemas: a schema-4 receipt locks, releases and
orders exactly as the schema-3 receipt with the same audit fields does. Only the codec and the account-reference
validation read the version to choose a layout.

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

1. Validate the complete root-2 form using a pinned legacy reader and unchanged bounds. The **whole-world backup
   is the operator's step before the upgrade**; the migrator does not make it. The migrator's own backup
   (`WorldDataMigrationService`, lines 195 to 224) copies only the managed SavedData files and their `_old`
   siblings. It refuses with `BACKUP_LIMIT` when `MAX_BACKUPS` (5) migration backups already exist. That refusal
   becomes reachable for every world that has an escrow ledger, and section 9 registers it.
2. Retain original operation-2, intent-2 and receipt-3 encodings and their digest rules. Add an empty account list;
   do not change embedded member digests, reconstruct baseline amounts, or create READY authority.
3. Old live fluid records are explicitly UNKNOWN_BASELINE. Hold ordinary reconciliation before another C2 apply
   or L2 retirement. Infer that diagnostic from the old record version; do not rewrite its saved reason or bytes.
   Operator preview must recognize this version-derived hold as an eligible conflict even when the preserved
   record phase is PREPARED; its member digest still commits those original bytes. It can complete as a legacy
   UNKNOWN decision with existing proofs. An already frozen O1 resumes its original decision unchanged. Already
   released receipts stay released and UNKNOWN. A component that consists only of legacy and item members keeps a
   schema-2 intent. If such a record later shares a component with a row-bearing member, the mixed-component rule
   of sections 2.3 and 4.1 applies: the intent is schema 3, the commitment covers the row-bearing members only,
   the legacy member keeps its schema-3 UNKNOWN receipt, and the component's total status is UNKNOWN.
4. Preserve floors and heads unchanged. A new operation after migration captures a new baseline; this does not
   fill historical gaps in old receipts or floors. New schema-3 records never inherit the legacy exemption.
   Admission refuses a new fluid operation whose prospective component contains a legacy schema-2 fluid record
   (section 2.3), so no new baseline is projected from a predecessor whose own baseline is unknown.
5. Validate the target root and all reservation invariants before any replacement. The new header can exceed a
   completely full old root; return a bounded migration refusal and preserve the original file, rather than
   truncate or increase 1 MiB. A root already violating existing canonical re-encoding/reservations is likewise
   not repaired by migration. The target write is checked and must be acknowledged before any action.

Support root 2 explicitly in `ManagedSavedDataType`/`SavedDataSchemaMigrator`, including its historical epoch;
do not accidentally enable root-1 or other legacy schemas through the generic legacy flag. As written before
W357R, that sentence could not pass the existing pre-start validator. Slice B makes these four changes in the
[migration package](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/migration/SavedDataSchemaMigrator.java):

1. **A dedicated escrow branch in `SavedDataSchemaMigrator.migrate`,** in the manner of `migrateTransferJournal`
   (line 155). Today RESOURCE_ESCROW has no branch, so a supported older root would fall through to the generic
   tail, which stamps `schema_version = 2` and the epoch `v0.9.0-beta`. The escrow branch performs steps 1 to 5
   above and never reaches the generic tail.
2. **Separately pinned epochs in `ManagedSavedDataType.formatEpoch(int)`.** Today it returns `EscrowLimits.EPOCH`
   for every escrow schema (lines 183 to 185), and `firstEpochSchema()` returns the current schema when
   `hasLegacySchema()` is false, so after the bump a root-2 file's epoch would not be checked at all. Schema 2
   returns the root-2 epoch as its own pinned literal, which must not follow `EscrowLimits.EPOCH` when that
   constant changes. Schema 3 returns `v1.8.0-resource-escrow-fluid-accounting`. The migrator checks the root-2
   epoch on the source before it migrates, and refuses a root-2 file with any other epoch as invalid.
3. **`supportsSchema` limited to exactly 2 and 3 for RESOURCE_ESCROW.** `hasLegacySchema()` stays false for this
   type (line 206), because the generic flag would open schema 1. Root 1, root 0 and every root above 3 stay
   unsupported.
4. **The baseline check split in two.** Today `discoverSources` validates the migrated payload with
   `ManagedSavedDataPayloadValidator.validate(type, payload, source)`, and for RESOURCE_ESCROW (lines 40 to 46) that
   calls `EscrowLedgerBaseline.require(source, loaded)`, which requires the bytes on disk to equal
   `EscrowCodec.encode(loaded)`. During a migration the file is root 2 and `loaded` is root 3, so every migration
   would be refused with "Escrow checked baseline differs from loaded authority". Instead, (a) the source file is
   compared with the **pinned root-2 re-encoding** of what the pinned root-2 reader decoded, which proves the file
   is canonical root 2; and (b) the migrated root 3 is **validated on its own**: structure, bounds, references,
   reservation invariants and an encode/decode round trip. A root-3 file on disk keeps today's single check against
   its root-3 re-encoding. Neither half is skipped, and neither is satisfied by the other.

A root-1 escrow file is still refused before start with its bytes preserved, as today, and a unit test pins that
(section 7.1). Invalid or future
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
| Account list | At most 128 rows, and exactly the union of new fluid live-operation and permanent-receipt IDs: at most 64 + 64, counting O3 overlap once. A row has one **row ceiling** in uncompressed bytes, with depth 8 and a node bound. The ceiling is not fixed by this note: Slice A measures it (see below), W357R expects about 2,048 bytes, and it is never above the 4,096 bytes first proposed. Root remains 1,048,576 bytes, depth 24, 131,072 nodes. The count maximum does not promise all maximum-size objects fit together. |
| Keys and arrays | Baseline at most 2 keys; frozen union at most 5 (cargo 1 and 3). IDs retain `EscrowTags.id`'s 128 printable ASCII characters; digests are exactly 32 bytes, UUIDs exactly 16 bytes. Present/unknown codes are fixed enums/bitmasks, not strings. A baseline entry has exactly four longs (two initial amounts, two deltas). A frozen entry has up to two observed-amount longs, one optional known adjustment long and one adjustment-owner UUID; masks govern omissions. No other variable-length numeric array is allowed. |
| Groups | At most 64 members, at most 128 physical endpoints, at most 320 member/key entries before deduplication. Only the lowest-sequence row carries the member list, at most 64 UUIDs by count and at most the largest real component that Slice A measures; this list is included in the row ceiling and node bound. Account/intent digests commit the same member set. |
| Amounts | Use checked signed `long` arithmetic throughout. Loader units remain 0..16,000. Cargo amounts retain their native capacity checks. Fuel bank bound is `RocketLimits.MAX_BLOCKS * 1,000` (2,048,000). Absent amounts have no numeric value. Aggregate arithmetic is checked even though these limits are well below long overflow. |
| Existing envelopes | Native fluid/frame remains 8,192 bytes/depth 12/384 nodes; operation remains 12,288/depth 16/512 nodes; receipt remains 2,048/depth 16/128 nodes; endpoint 512; floor/head/ARMED 1,024. Do not copy native fluid NBT into every numeric key or the receipt. |
| Per-Level and tick work | Accounts belong to the overworld ledger, with no new per-Level persistent map or index. Reuse the existing maximum of 64 Level services, eight cheap reconciliation visits, at most one ordinary component capture and one publication per tick, existing cargo preparation reservations, and the independent one-endpoint pruning visit. Numeric projection reads only already decoded captured units, at most three units per fluid member; it adds no world traversal, ticket or native decode. O1/O2 component work remains bounded by 64 members/128 endpoints and the existing 16 MiB retained capture ceiling. Do not repeat a full account walk for every unchanged member visit. |

An account row's short-key layout has at most seven encoded key entries (two baseline plus five frozen), each
with a 128-byte ID, 32-byte digest and a fixed small number of longs/flags, plus the group member list and bounded
framing. That is a layout argument, not a measurement, and it does not fix the ceiling.

**Slice A fixes the row ceiling by measurement, before Slice B is written.** With the real codec it measures:

- the true worst account row: seven key entries with maximum-length IDs, every optional field present, and the
  member list of the largest real component;
- the largest real component. An intent must fit inside a 12,288-byte operation, so a real component has far fewer
  than 64 members. Slice A reports the largest member count whose intent still fits, and the member list is bounded
  by that count.

Slice A then proposes the **smallest row ceiling that fits** the worst real row and that component, with the
matching depth and node bound. Root's answer 2 (section 10) makes that measured value the ceiling. If the worst
real row, receipt, operation or O1 candidate does not fit an existing ceiling, the result comes back to Root as a
failed fit. It is never resolved by raising a ceiling.

The existing dictionary has 47 fields. Appending `accounting_digest` gives the three-character wire key `f1b`.
One 32-byte NBT byte-array field adds **42 bytes** (type 1 + name length 2 + name 3 + array length 4 + data 32).
If no other field expands, W114's ordinary fixtures would become 1,875 bytes for fuel and 1,599 for cargo,
leaving 173 and 449 bytes. These are layout calculations, **not measurements or new pins**; UNKNOWN/KNOWN string
lengths, legacy names, neutral forms, mixed groups and the new account node must be encoded in the real tests.
The operation's baseline hash adds 58 bytes under the descriptive key `accounting_baseline`; the intent's new
commitment also costs bytes/nodes in its embedded copies. None may bypass the complete-operation bound.

Extend the shared `EscrowSettlementCapacity` preview and reservation, not admission alone. Charge each occupied
account row **once, at the row ceiling**. The ceiling is the bound of the complete row, so the later frozen part and
the group-owner member list are already inside that one charge, from L1 on, and nothing further is reserved for
them. Count O3 operation/receipt overlap once, and preserve
the existing full receipt, floor, READY and ARMED ceiling charges. R6 floor-retirement headroom remains available;
bare floor retirement creates no fluid row, so its own current charge need not grow. A candidate must prove O1,
O3 and O4 fit for its full component before L1. Both admission and unrelated-component freeze must use the same
rule. Root occupancy reduces useful
concurrency; do not reuse W114's counts as measured new capacity or raise a ceiling to recover them.

**Pinned counts that the format moves.** Today each unsettled operation is charged 12,288 + 2,048 bytes
(`EscrowSettlementCapacity`, lines 142 to 157), so one more charge per fluid operation lowers the number of fluid
operations that fit in the root. The new header also adds bytes: the epoch string is longer and `fluid_accounts`
is a required list. Slice A reports, before Slice B is written, the old value, the new value and the real-codec
measurement of every pinned count the format moves:

- the 61 admitted records in `EscrowCargoCapacityTest` (line 177);
- the fixture of `EscrowFlightBenchmarkCeilingTest` (line 13), a root of exactly 1,048,576 bytes with 64 records
  and 256 floors, which no longer fits as it stands and whose fixture has to change, not only an expected value;
- the point at which the native saturation families `w336-saturation-fluid-load`, `w336-saturation-fluid-unload`
  and `w336-saturation-fuel` reach the byte refusal.

Root records the accepted numbers in a decision row before Slice B merges, under R441's re-pinning rule
(section 10). Each re-pin is recorded with its old value, its new value and its measurement. No ceiling, limit or
assertion purpose changes, and no refusal side-effect check is removed (AGENTS.md section 5).

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

New mismatch logging is one WARN `ARCE_ESCROW_ACCOUNTING_HELD` per held operation, plus one
INFO `ARCE_ESCROW_ACCOUNTING_RELEASED` if operator settlement later releases it. At most 64 operations are held at
once, because the ledger has at most 64 operations, so the remembered set has at most 64 IDs. An ID is **forgotten
when its operation leaves the ledger**, by release or retirement, and at no other time; the 200-tick retry does not
reset it. An operation ID is never reused, so one operation logs the WARN at most once per server session. There is
no fixed total per session: it is bounded by the number of operations that are admitted and then mismatch, and each
mismatch holds its device and rocket until an operator acts. Operator audit is one INFO per acknowledged O1 decision, with
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
Check shared adjustment ownership without double counting, and that the owner row of the largest real component
stays inside the row ceiling. Check that amounts are aggregated per endpoint and key when both banks hold the same
key, and that a move of that key between banks leaves the account unchanged while the fuel-vector signature differs.
Check that an accepted half-applied transfer freezes `loss = outstanding` in both directions.

Mixed components (section 2.3), both cases:

- A new fluid member and an item member in one component: the intent is schema 3, the commitment holds exactly the
  fluid member's row, the fluid receipt is schema 4, the item receipt is schema 3 UNKNOWN with its bytes unchanged,
  and a row for the item member is refused as an orphan.
- A schema-3 fluid member and a legacy schema-2 fluid member in one component (built as a fixture, because
  admission refuses to create it): the intent is schema 3, the commitment holds only the schema-3 member's row, the
  legacy member's receipt is schema 3 UNKNOWN, the schema-4 receipt's status is UNKNOWN, and the component total is
  UNKNOWN.

Also check that admission refuses a new fluid operation whose prospective component contains a legacy schema-2
fluid record, with no reservation, sequence, queue, writer or endpoint change, and that a component without any
row keeps a schema-2 intent.

Receipt schemas in the lock predicates (section 4.1): one parameterized test runs `installed`, `logicalLocked`,
`identityLocked`, `chosen` with `minimumEpoch` and `stateLocked`, `endpointLocked`, and every other lock or release
predicate that reads a receipt, on the same receipt encoded once as schema 3 and once as schema 4, released and
unreleased, and expects equal results for each pair. A second test pins that a receipt of any other schema is
treated by all of them exactly as today.

Pin root-2 migration fixtures with live, frozen-O1, unreleased-O3 and released receipts, bare floors, heads and
ARMED entries. Verify exact old operation/intent/receipt bytes and digests survive, UNKNOWN is not promoted,
new schema-3 rows cannot omit their proof, invalid/future roots are preserved, and a full target refuses without
replacement. Stop/retry tests cover the migration write and checked baseline, including UNKNOWN outcomes.

For the four migrator changes of section 4.2, add unit tests that:

- a **root-1 escrow file is still refused** before start, with its bytes preserved, and `supportsSchema` is true
  for exactly 2 and 3;
- a root-2 file migrates through the escrow branch and the result carries schema 3 and the root-3 epoch, never the
  generic tail's `schema_version = 2` or `v0.9.0-beta`;
- a root-2 file with a wrong epoch is refused, and `formatEpoch(2)` still returns the pinned root-2 literal after
  `EscrowLimits.EPOCH` changes;
- a root-2 file that is not byte-equal to its pinned root-2 re-encoding is refused, a migrated root that fails its
  own validation is refused, and a valid root-2 file passes the pre-start validator instead of failing with
  "Escrow checked baseline differs from loaded authority";
- a migration that finds five migration backups is refused with `BACKUP_LIMIT` and leaves the ledger unchanged.

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
  queued/UNKNOWN/O1/O3/O4/root reservations and the exact 1 MiB benchmark. Re-pin measured intersections only,
  under R441's re-pinning rule (sections 5 and 10): `EscrowCargoCapacityTest`'s 61 and the benchmark's exact
  1 MiB fixture move, Slice A reports their new values, and Root accepts them in a decision row before Slice B
  merges. Keep every ceiling, assertion purpose and refusal-side-effect check.
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
| `r042-L_LEDGER`, `r042-U_LEDGER`, `r042-P_SERVER`, `r042-P_OWNER`, `w336-saturation-bay`, `w336-saturation-item-load`, `w336-saturation-item-unload`, `w336-saturation-fluid-load`, `w336-saturation-fluid-unload`, `w336-saturation-fuel` | Same missing/unloaded/shared-pool outcomes, and the same kind of capacity refusal with the same side-effect checks. The count at which `w336-saturation-fluid-load`, `w336-saturation-fluid-unload` and `w336-saturation-fuel` reach the byte refusal is expected to fall, because each fluid operation is charged one more row. That is checked, not assumed: Slice A reports the new counts, and Root accepts them in a decision row before Slice B merges (section 5). Update valid synthetic ledger fixtures to the appropriate root version; include account rows in byte and ownership checks, without changing 64/server, 8/rocket, 16/owner or shared 256-coordinate limits. |
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
| A. Pure account and size proof | New `EscrowFluidAccount`, `EscrowFluidAccountCodec` and arithmetic tests; extend existing charge/layout tests using real encoders. | 1.5-2 days. Key/sign/chain/group cases and measured worst row/receipt/operation/O1 fit. Reports the largest real component, the proposed smallest row ceiling, and the new value of every pinned count of section 5. Returns to Root if any bound cannot hold. |
| B. Saved form and migration | `EscrowLedger`, `EscrowCodec`, `EscrowOperation`, `EscrowReceiptCodec`, `EscrowSettlementIntent`, `EscrowSettlementTombstones` (the shared receipt test), `EscrowSettlementCapacity`, `EscrowLedgerWriter`; the four migrator changes of section 4.2 in `ManagedSavedDataType`, `SavedDataSchemaMigrator`, `ManagedSavedDataPayloadValidator` and `EscrowLedgerBaseline`. | 2-3 days. Root-2 fixtures remain byte/digest faithful; root-3 round trips, reservations and failed/UNKNOWN migration prove preservation; a root-1 file stays refused. Its own read-only review by another session precedes the merge. |
| C. Native capture and normal completion | `EscrowLoaderAdmission`, `EscrowCargoPreparedCapture`/eligibility, `EscrowLoaderEndpoint`, `EscrowFluidCargoEndpoint`, `EscrowReconciliationChange`/action, `EscrowReconciliationCheckpoint`, `EscrowSettlementDisk`, `EscrowCargoReadback`, normal retirement and `EscrowFloors`. | 2-3 days. Real fuel/both cargo directions, prefix-normalized and stable-readback arithmetic, no-effect refusal and E2/UNKNOWN coverage. No new permanent normal-transfer history. |
| D. Operator lifecycle and diagnostics | Observer/proofs/recovery/operator/diagnostics/pruning files, `EscrowReason`, account commitment and receipt creation/release; Root adds ADR-065/R-035 text and manifest changes. | 1.5-2.5 days. O1-O4, signed nonzero approval, half-applied acceptance, legacy/missing UNKNOWN, mixed components, both ABANDON survivors, pruning and READY flight GameTests. The receipt work (schema-4 creation and release) gets its own read-only review by another session before it merges. |
| E. Oracles and native evidence | `inspect_escrow_receipt.py`, common native ledger decoder, the named loader/cargo/item oracles and runner tests; gated native fixture/probe additions only where necessary. | **3-4 days** to implement targeted native coverage, as W357R advises: the shared parser has more than ten callers and 58 families are rerun. Allow another 1-2 days of serial execution/review for the complete candidate campaign and corrections. R-044 failures remain visible. |

Total implementation/targeted verification is approximately **10-14.5 engineer-days**, plus **1-2 days** for the
full serial candidate rerun and review. Performance/real-client campaigns and separately authorized X01-X03
work are not included.

**Order (Root's answer 4).** Slice A is dispatched first and alone, because its measurements decide the row
ceiling and the re-pinned counts. Slices B to E follow it. Every saved-form slice, which means Slice B and Slice D's
receipt work, gets its own read-only review by another session before it merges (AGENTS.md section 13). Root
writes the R-035 and ADR-065 text before any code merges.

Do not leave a partially integrated new format reachable: keep admission off, stage the
writer/version activation until its consumers and migration work together, and run hosted full regression on
the integrated batch. Slices B to D merge behind one activation constant that stays off, so that the writer keeps
emitting root 2 and no test world migrates until Slice D is merged. Each slice TASK names that constant and a test
that pins it off. A saved-form rollback after test worlds have written root 3 requires their whole backup,
not cherry-picking out the codec while keeping those worlds.

## 9. Proposed register/ADR text for Root

**R-035, signed fluid-accounting holds and format.** New fluid fills and cargo transfers retain a bounded typed
baseline and a signed per-fluid account in escrow root schema 3. A native arithmetic/readback mismatch holds
the operation's connected component, including its device and rocket, in manual REPAIR without changing or
discarding resources. A frozen settlement mismatch holds that same component before release. Duration is
unbounded until the exact frozen state/proofs can be restored or a stopped-server whole-world repair is
completed; restarting alone is not repair. New accounting diagnostics are one WARN per held operation; at most 64
operations can be held at once. An operation's ID is forgotten when the operation leaves the ledger, by release or
retirement, so there is no fixed total per session. There is also one release INFO when a held operation is
released, and one audit INFO per acknowledged
decision with at most 64 members/320 key rows. Existing writer and vanilla/Forge retry logs retain their
separate, potentially unbounded lifetime behavior. There is no new terrain/Level save veto and no expansion of
the shared 256-coordinate GuardedChunkSaves pool.

Root-2 development ledgers migrate without inventing typed historical quantities. Live old fluid records have
UNKNOWN_BASELINE and cannot automatically apply or retire through the new numeric path; existing audited UNKNOWN
settlement or whole-world offline repair is required. A valid old root whose upgraded encoding/reservation does
not fit is refused at whole-world pre-start, original files preserved, one ERROR per start, until the copy is
repaired/reduced through a compatible build or the complete backup is restored. Invalid/future root-3 accounts
use the existing whole-world pre-start refusal. A root-1 ledger stays refused. The migrator's own backup covers the
managed SavedData files only; the whole-world backup is the operator's step before the upgrade. When five migration
backups already exist, the migration is refused with `BACKUP_LIMIT` (`ARCE-BETA-2004`). The scope of that refusal is
the world, it lasts until the operator archives old migration backups, it logs one ERROR per start, and it leaves
every file unchanged. A new fluid operation is refused at admission while its prospective component contains a
legacy fluid record; that refusal affects only the prospective operation and lasts until the legacy record is
settled or retired. Under ACCEPT_CURRENT a half-applied transfer is recorded as its signed discrepancy, never as a
completed transfer. New capacity checks refuse only the prospective admission
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

## 10. Root's decisions (R441)

Root answered the four questions of the first version of this note in decision R441, after W357R. The answers
below replace the open questions. They are Root's design decisions under AGENTS.md section 13. They are not owner
acceptance, a Gate result or approval of admission.

1. **Scope and discrepancy policy: approved as recommended,** with W357R's findings 5, 6 and 8 written in
   (sections 2.1, 2.3 and 2.2). The scope is both propulsion banks for fuel, the addressed tank only for cargo,
   typed per-fluid keys aggregated per endpoint and key, automatic completion only at zero residual, and explicit
   digest-confirmed signed acceptance of a loss, a gain or a half-applied transfer. Missing contents remain
   UNKNOWN after ABANDON. There is **no item count account in v1.8**, because R-035 names only per-fluid
   accounting. Item receipts keep their UNKNOWN status and their diagnostic zero is not a balance.
2. **The same-file account list: approved, on the condition of Slice A's measurements.** The row ceiling becomes
   the smallest measured size that fits the worst real row and the largest real component (section 5). A failed
   fit comes back to Root, never as a raised ceiling. Existing ceilings and the shared full reservations do not
   change.
3. **Root-2 migration with legacy UNKNOWN holds: approved,** with findings 1, 2 and 7 resolved (sections 4.2, 2.3
   and 4.1, and section 9). Old embedded encodings and digests are retained, and no baseline is fabricated. The
   whole-world pre-start refusal of a root that no longer fits is accepted, with the original files preserved. A
   root-1 file stays refused.
4. **Schedule: approved.** Slice A is dispatched first and alone, then Slices B to E, with a read-only review of
   each saved-form slice (section 8). Root writes the R-035 and ADR-065 text before any code merges. No existing
   failed or NOT_COVERED row becomes passed by this note, and the R-044 downgrade failures stay visible.

**Re-pinning rule (R441).** Capacity counts that move only because the format deliberately grows may be re-measured
with the real codec and re-pinned. These are the 61 concurrent fluid operations, the 1 MiB benchmark fixture and
the saturation families' byte refusal (section 5). Each re-pin is recorded with the old value, the new value and
the measurement. No ceiling, limit or assertion purpose changes, and no refusal side-effect check is removed
(AGENTS.md section 5). Root accepts the new numbers in a decision row before Slice B merges.

No question in this note remains open for Root. The open items are measurements and work: Slice A's numbers, the
R-035 and ADR-065 text, the review of the unsigned R-044 draft's scope for the new ledger schema boundary, and the
slices themselves.
