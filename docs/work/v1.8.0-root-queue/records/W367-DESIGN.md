# W367: X03 whole-world repair after FF_REBUILD_SOURCE

Date: 2026-11-17. Status: **DESIGN ONLY; independent review and Root decisions pending.**
Inspected commit: `daba8849ebd05902faa4e5fc43455b6a437cfcc1`. This note implements no repair,
changes no admission switch, and closes no risk, native row or Required Gate. Root commissioned it in
[TASK-W367](../TASK-W367-X03-REPAIR-DESIGN.md), under R460. The parent plan is
[X01-X03-ISOLATION-PLAN, section 5](X01-X03-ISOLATION-PLAN.md#5-x03-the-operators-whole-world-repair-after-rebuild_source).

## 1. Diagnosis and authority, store by store

[W142](W142.md), particularly R73, the preserved-source R74 continuation and R76, distinguishes two inputs.
R73's new source snapshot UUID allowed an actionable historical copy and 10,000 mB. R74 preserved the real
source identity; R76 accepted its **protected refusal**, not a playable repaired world. This proposal accepts
only the second shape. It must refuse R73's shape, not delete its extra rocket or hide its extra water.
[W326 N084/X03](W326-MATRIX.md) and [C3 sections 7 and 8.6](C3-SWITCH-REPORT.md#7-fluid_cargo_unload-block_variantloader4)
still do not demonstrate repair.

| Store | Corrected FF_REBUILD_SOURCE state and authority | Proposed offline action |
|---|---|---|
| Escrow ledger | The ordinary 1,000 mB unload completed L2 and C8. The fixture requires no operation, floor, receipt, READY head or ARMED flight, and launch serial 0. The admission guard was NONE, so L2 did not install a named-transfer floor. The existing ledger ID still binds both endpoints. | Preserve the complete ledger file, including writer generation, byte-for-byte. No floor is removed, invented or lowered. |
| Transfer journal | One stale legacy PREPARED record T was replayed **after** that retirement. It names the actual physical source, logical rocket, owner, source snapshot UUID and origin, but contains pre-unload 5,000 mB cargo. Its destination is a relocated historical snapshot, with no physical destination. | Remove exactly T from the copied journal's `data/transfers` list after all checks. Preserve root schema, every other field, outbox and unrelated record. Keep T's original bytes in the immutable backup and its typed representation in the repair report. |
| Rocket entity | Exactly one physical rocket R remains at the original source placement. Its current snapshot has the same source UUID and structure, but the cargo change produces a different content hash. Its escrow section is bound to the ledger at epoch 1 with no markers. It has 4,000 mB water and, separately, 1,000 mB propulsion fuel. The fixture creates a stationary fueled rocket; its saved flight is not the historical TRANSIT flight inside T. | Preserve the entire entity region and entity NBT byte-for-byte offline, including `RocketEntityData`, flight, fuel, passengers and escrow. Require FUELED, no plan, no active transfer and no passenger assignments. No flight normalization is needed or authorized. |
| Tank inside the rocket snapshot | One `pressurized_tank_v1` payload contains the current 4,000 mB water. This is physical cargo represented inside R, not an additional world tank. | Do not edit the payload or its hash. |
| Unloader and terrain | The bound UNLOAD device contains 1,000 mB water, epoch 1 and no markers. No corresponding pressurized tank has been restored into terrain at either recorded end. | Preserve terrain, device identity, tank, binding and escrow bytes offline. |
| Other stores | Assembly/disassembly transactions, pad claims, bay receipts, rejoin sidecar, player data and other dimensions must contain no competing claim for these identities. | Read and cross-check; do not edit. Unknown or conflicting authority refuses the repair. |

There is **no missing 1,000 mB** to refund. Physical cargo is `4,000 + 1,000 = 5,000 mB`; T's historical
5,000 is a recovery description, not another owned inventory. Replacing the current rocket payload with that
description would make physical cargo 6,000 mB. Materializing a whole historical rocket beside R is worse.
Propulsion is a different fluid account and must not be added to the water total.

The authority is the complete, consistent native world: exact current endpoints, ledger binding/epoch,
retirement evidence and exclusive identities together, not any single file. Historical snapshot contents
cannot override credited endpoints. In
[EscrowRecoveryFence](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowRecoveryFence.java),
a source-side keeper with nonzero epoch is protected even when `floors` is empty. This explains
FLOOR_CONFLICT's name without implying that a durable floor exists. The earlier ENDPOINT_PROOF_REQUIRED
and a RETRY_LATER report's zero match counters do not prove source absence.

## 2. Choice: a narrow offline tool, not settlement or a new game command

The audited settlement is preferable where eligible. It is not eligible here:

- [EscrowOperatorDiagnostics](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowOperatorDiagnostics.java)
  reports no selectable operation/floor. ACCEPT_CURRENT and ABANDON select a connected component of existing
  operations; they cannot settle a bare journal entry. The intent also explicitly excludes FLOOR_CONFLICT.
- [EscrowSettlementOperator](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowSettlementOperator.java)
  requires a real stored floor for TRANSFER_FLOOR_RETIRE.
  [EscrowSettlementObserver](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowSettlementObserver.java)
  additionally requires a COMMITTED record, a matching protected destination, and absence at both ends.
  Here T is PREPARED, there is no floor, and R is present.
- [EscrowSettlementRecovery](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowSettlementRecovery.java)
  reaches journal removal only after frozen O1, own-store O2 and installed O3. O4 releases the exact decision;
  pruning only removes retired markers. A maintenance start changes none of those eligibility rules.

Therefore propose a small operator-invoked **offline planner and copier**, with a separate `plan` and
digest-confirmed `apply` mode. These are proposed modes, not runnable instructions today. It creates a fresh
complete candidate directory; it never patches the source, backup or active server directory in place. The
sole semantic edit for this case is removing verified stale T. That is an audited consequence of whole-world
validation, **not permission to delete a journal entry on its own**. It neither replays recovery nor creates
an escrow operation, READY head, receipt, account row or floor to manufacture online eligibility.

Reuse the typed NBT representation in [loader_cut_nbt.py](../../../../scripts/loader_cut_nbt.py), checked reads
and evidence helpers, rather than a new dependency. Validate all journal fields against
[RocketTransferJournalCodec](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/persistence/RocketTransferJournalCodec.java)
and the matching candidate's codecs. Preserve NBT types and all non-selected subtrees; compare them before
and after serialization. No region writer is required. Its decompressor and schema checks must reject
ambiguous or invalid input rather than silently repair it.

There is no new in-game command, packet or permission level. Offline authority is filesystem access plus
the operator's recorded identity, reason and explicit plan digest confirmation. The tool takes exclusive
world locks, rejects aliasing/symlinks/reparse points/hard-link sharing and resolves every path beneath its
declared roots. It cannot accept an arbitrary NBT patch, replacement snapshot, amount, `--force`, or public
skip-check option. One invocation handles one T. Logs contain one summary for planning, one for application
or refusal, and at most four bounded details (160 characters each); full manifests go to a size-bounded
external report, not repeated console dumps or per-tick logs.

## 3. Procedure and checks

This is the proposed procedure for approval, not a supported repair already available to operators.
It follows [R-035](../../../11-RISK-REGISTER.md) and its accounting paragraph, and
[ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md).
Do not operate on a running server, restore one store, rebuild a resource or refund one.

1. **Stop and identify.** Disable automatic restart and deny players access. Stop the source server and
   prove its process has exited and all world/session locks can be acquired. Record the source commit,
   candidate JAR SHA-256, Forge/Java versions, configuration hashes and world identity. The repair handles
   only the schema/feature combination that this pinned candidate supports; it does not upgrade a world.
2. **Keep the entire original.** With locks held, inventory every regular file in the complete world,
   including all Levels' terrain, entities, POI, `.mcc` files, all global data/journals/ledgers/accounts,
   receipts, sidecars, playerdata, advancements, stats, datapacks and metadata. Record relative names,
   sizes and SHA-256, plus a canonical manifest digest. Make a complete backup B, independently re-hash
   it, and retain the original and B unchanged. A SavedData migrator's partial backup is not B.
3. **Copy and inspect.** Copy B to a separate analysis world, verify its entire manifest against B,
   and inspect it offline. Enumerate **all** dimensions, entity-region header slots (not only 132/133),
   nested entity passengers and all terrain block entities. Census R's physical/logical/snapshot IDs,
   T's two ends, tank locations and device identity across the complete file set. Search every related
   managed store for references. Prove precisely the section 1 state, and verify the retained before-unload
   witness/checksums rather than treating current amounts as a historical baseline. Missing history may
   leave accounting UNKNOWN, but missing evidence of exclusive current authority refuses this tool.
4. **Freeze a plan.** Produce an external, immutable repair-plan version 1. It binds B's manifest digest,
   tool/candidate hashes, ledger schema/ID, T's exact record digest, R/device identities, native signatures,
   epochs, cursor, per-fluid/tag observations, accounting knowledge, operator and reason. It contains the
   exact edit `/data/transfers[T.transfer_id] -> absent`, and explicit no-edits for ledger, entities and
   terrain. Include expected output subtree hashes and a finite file-write list. The operator reviews and
   confirms this digest, including the discrepancy/UNKNOWN classification; confirmation cannot waive a
   failed structural or conservation check.
5. **Apply to a fresh copy.** Reverify all input hashes and locks; create candidate C from B, never from a
   partially modified attempt. Remove exactly T, stage and force the new compressed journal, decode and
   compare it with the approved typed candidate, atomically replace that file and re-read it. Preserve
   its wrapper/DataVersion and first-Moon-landing outbox. Any failed/UNKNOWN write, force, close, replace
   or readback leaves C **unapproved**; keep diagnostics and rebuild a fresh C after correction. Do not
   resolve ambiguity by running it. Reapplying to an already repaired input refuses as a different
   manifest, rather than silently succeeding against a new authority.
6. **Verify offline.** Only `data/advancedrocketrycommunity_rocket_transfers.dat` may differ between B and
   C, and only T may differ in its decoded payload. Every other file and the file-name set must match
   B exactly. Recheck ledger, full-world census, current resource vectors, zero restored tanks, absence
   of T and unchanged entity flight. Store the new manifest and semantic diff outside the world.
7. **First save and restart.** Start C in isolation using the matching candidate, with players and
   automation excluded. Do not set `escrowScenario`/`escrowCase`; they would rerun the seed fixture.
   A read-only gated probe verifies the ledger baseline, loaded source/device identity and journal
   absence with fresh own-store/readback barriers. No previous runtime proof token survives. Require
   no FLOOR_CONFLICT or other hold, and no unexpected writer/region failure. Run `save-all flush`, wait
   for exact checked writer acknowledgement and native IO completion, then stop cleanly and start C
   again. Decode the stopped files and compare resources, identities and permitted changes.
8. **Second save and restart.** Repeat the same checks, fresh barriers, explicit flush, acknowledged
   completion and clean stop; then start C a second time. Thus there are an initial candidate start
   **plus two restarts**, not two logins or two saves in one JVM. Read-only verification after each
   restart must show one unchanged R, no restored tank, water 4,000/1,000 and T absent. Stop cleanly
   for the final manifest and full-world comparison. A hold, changed resource or failed readback at
   either cycle prevents approval; a successful server process exit alone proves nothing.
9. **Functional verification and conservation.** On a separately hash-verified descendant of the
   twice-restarted C, run the ordinary unloader and launch through production entry points. Use only
   the existing release-test admission gate in the native harness; do not enable production admission.
   Save/read back the completed unload: R 3,000 mB, unloader 2,000 mB, one R and no world tank. Then
   launch and land with the configured real route; record actual propulsion debits separately per
   fluid, not as missing water. No privileged resource edits, direct success-state setters or bypass
   of escrow authority are allowed. This verification descendant is not silently substituted for C.
10. **Replace the whole world.** Only after both cycles, independent oracle checks and operator approval,
    stop all candidate/source processes, reverify original/B/C hashes and exclusive locks, and promote
    **the entire** validated C. Stage it on the target filesystem before a stopped-server directory
    swap; retain the original directory and B. If promotion fails or is interrupted, keep the server
    stopped and verify whole directories before retrying. Never copy just the repaired journal back
    into a live or divergent world. Rollback is also whole-world, including metadata and player data.

### 3.1 Bounded work and refusals

Initial proposed offline work limits are 64 Levels, 100,000 files, 256 GiB total input, 4,096 region files,
1,024 header slots per region, 1,024 objects per decoded chunk and passenger depth 8. Hashing is streamed
with a 1 MiB buffer; decoding uses the existing stricter per-root/record/region/NBT limits. Limit exhaustion
refuses without truncating the census; it is not permission to infer absence. These are tool work limits,
not increased runtime limits. Slice X03-A must test the selected values before the tool is approved.

Every following condition refuses planning/application or, after starting C, refuses promotion:

- Any running writer, unavailable lock, path overlap, traversal, alias, nonregular file, linked file,
  unreadable/unknown-presence input, changed input/manifest, incomplete copy, unverified backup, or
  insufficient space (C: and D: each at least 10 GiB, plus room for full copies and write staging).
- Exceeded work/size/depth limits, unknown region compression, unsupported external entity records,
  corrupt/future/noncanonical schemas, invalid checksums, missing adapters/fluids or ambiguous decoding.
  All files are backed up even when a record format makes this narrow repair refuse.
- Anything other than the exact legacy PREPARED T: governed/native envelope, destination identity,
  other transfer for the logical rocket, inconsistent IDs/source snapshot/origin/owner, unverified
  historical witness, passenger assignments, active flight/plan, or a relocated/non-FUELED R.
- Missing, duplicate or relocated R/device; another same-logical rocket anywhere; physical historical
  tank/rocket; a nonempty restored footprint; unsupported/multiple cargo tanks; changed fluid/tag,
  cargo amount, propulsion vector, collateral inventory, binding, epoch or marker state.
- Any related active escrow operation/account, floor, receipt, READY/ARMED authority, assembly or
  disassembly transaction, bay receipt/claim, pad reservation or rejoin obligation. Unrelated entries
  may remain only when the full reference audit proves disjointness; they cannot be deleted to fit.
- Unconfirmed/mismatched/expired-by-input-change plan, invented baseline or zero, unexplained resource
  delta, changed non-selected NBT, extra file changes, missing evidence, uncertain storage outcome,
  startup/migration refusal, stale proof, persistent hold, failed save/restart or failed ordinary action.

Refusal keeps original/B intact. After an apply error the partially produced candidate is quarantined,
not repaired piecemeal. No exceptions are used as normal world-save vetoes, and this tool introduces no
runtime terrain/Level save refusal. Its refusal is local to making/promoting a copy, until a fresh valid
input and plan are available. Existing server holds and logs remain unchanged.

### 3.2 Exact file-difference boundaries

Offline application has the single-file allowlist in step 6. Starting Minecraft necessarily writes
metadata; claiming all world files remain byte-identical after starts would be false. Before an evidence
run, the harness must freeze a second, **exact relative-file list** for runtime saves: `session.lock`,
`level.dat`, `level.dat_old`, the named journal/ledger paths and the particular terrain/entity/POI region
files and other managed roots that the fixed fixture actually saves. A dry run establishes the names;
Root reviews them before the campaign. No directory wildcard, automatic acceptance of observed changes,
or whole-world allowlist is permitted. Required semantic checks still apply inside allowed files;
permission to save a region is not permission to modify its other objects or cargo.

For the native functional descendant, freeze its additional route/destination file names separately
before running it. At every stopped checkpoint, compare the complete manifest/file-name set and every
file outside that stage's list byte-for-byte. Within runtime-written files, compare canonical typed NBT
and permit only named clock/metadata, approved flush bookkeeping, unchanged ledger semantics, or the
explicit ordinary action's deltas. Unexpected world generation or unrelated machine activity fails the
test, rather than expanding its list afterwards. Report these three lists and all actual differences.

## 4. Root 2 and activated root 3: accounting without invented history

[W357 section 6](W357-DESIGN.md#6-failures-holds-and-logging) explicitly says the transient numeric row may
already have disappeared at L2. At this inspected head, account models/codecs exist, but
`EscrowAccountingFormat.ENABLED` is false: writers still emit root 2, root 3 is refused at pre-start, and
no migration is activated. Supporting root 3 in an offline parser is not permission to start it on that JAR.

| Form | Treatment by this repair |
|---|---|
| Today's root 2 | Preserve its bytes and UNKNOWN settlement semantics. There is no retained fluid account for this retired transfer. The external repair audit records `UNKNOWN_BASELINE`/UNKNOWN for the original transfer account; it does not create a numeric zero or a permanent ledger receipt. The original fixture's before witness separately establishes the native conservation oracle. |
| Fresh root 3 after reviewed activation | Run only on an accounting-enabled candidate with capture, settlement, diagnostics and native readers integrated. A normally retired unload has no operation/account row; preserve `fluid_accounts` exactly, including unrelated rows. Do not invent an operation ID to attach a replacement row or resurrect the deleted transient account. The external repair audit still records historical-account UNKNOWN when no durable baseline survives. |
| Root-2 history migrated to root 3 | Preserve UNKNOWN_BASELINE; a legacy record/hash is not a typed fluid baseline. If a related live record/account remains, this narrow tool refuses and requires a separately assigned settlement/repair, rather than fabricating its knowledge or release. |
| A retained known account/receipt outside this narrow case | Do not rewrite its immutable baseline, commitment or frozen decision. The approved accounting lifecycle must record a typed signed discrepancy confirmed by operator digest, or UNKNOWN if a baseline/endpoint is missing. That is a separate slice, not an alternative mode that weakens this repair's preconditions. |

The external versioned repair audit includes a resource account, clearly distinguished from an
`EscrowFluidAccount` row. It identifies `(fluid registry ID, native-tag digest)`, with absent and empty
tags distinct, baseline provenance, known endpoint contributions, status and optional signed discrepancy.
For an available typed baseline, `loss[f] = S0[f] + D0[f] - S[f] - D[f]`: positive means loss, negative
means gain. It is recorded only with the operator's confirmation of those exact values in the plan digest.
For missing historical contributions, omit the numeric discrepancy and record UNKNOWN; a journal's 5,000
mB source does not establish an original device balance by itself. The exact fixture's independently
captured 5,000/0 -> 4,000/1,000 witness can justify an explicitly confirmed zero discrepancy in this external
audit, but never an invented zero in a missing ledger account. The offline edit itself moves **zero**
water/fuel, verified independently of that historical knowledge classification.

This narrow X03 oracle admits no actual discrepancy: a nonzero loss/gain refuses repair/promotion even
if an operator would accept it in some other settlement. Accounting is not authority to rebuild an old
source or waive the physical census. Preserve the root-3 bounds (2,048-byte rows, depth 5, 65 nodes and
14-member lists), receipt lifetime/capacity and schema identities; no new persistent public ID is added.

## 5. New native family and independent oracle

Propose family `cargo-X03-REPAIR`, separate from the existing unchanged C-S5 oracle in
[run_v180_cargo_s4_s5_cuts.py](../../../../scripts/run_v180_cargo_s4_s5_cuts.py). Pin committed source, final
candidate/JAR hash, tool and runner hashes, configuration and every raw checkpoint. Reuse
[EscrowCargoRecoveryCuts](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCargoRecoveryCuts.java)
to produce the original FF_REBUILD_SOURCE forced stop and both protected restarts. Capture the actual
FLOOR_CONFLICT and the 4,000/1,000 endpoints before applying anything. Retain B and all hashes; perform
the complete procedure on C and record both new repair restarts as distinct JVMs. Fixture properties are
absent for C; verification probes observe but do not repair its saved resources/authority.

An independent raw-file oracle must not call the planner's success predicates. It decodes every entity
region slot across every Level, terrain/tank data and all relevant managed roots, then checks:

1. Water is 5,000 mB throughout repair, at 4,000/1,000 until the functional unload, then 3,000/2,000.
   No historical journal snapshot is counted as physical inventory; no unrelated fluid can cancel water loss.
2. The same physical/logical/owner/source snapshot identity exists once, with its native escrow epoch,
   binding and unchanged propulsion. There is no restored or duplicate physical tank/rocket in another slot,
   dimension, passenger subtree or at either recorded footprint.
3. T alone is absent, no replacement T/floor/receipt/READY/ARMED was fabricated, all unrelated authority
   is unchanged, and source readback and mutation checks pass with fresh proofs after each restart.
   No FLOOR_CONFLICT or other blocking reason is present. Log silence alone is insufficient.
4. An ordinary unload really applies and retires, then a launch really progresses and lands. The native
   fixture's admission gate enables only its target operation; the production switches remain disabled.
   Verify launch consumption from its typed propulsion debit, and retain final raw files/logs/results.
5. Complete per-stage manifests show no extra/missing/changed file outside the frozen lists, and no
   unapproved NBT changes inside them. Verify B and the source hashes again after all runs.

Run a root-2 case and, after activation, a fresh-root-3 case and an upgraded retired-root-2 case. Never
fake activation with a world schema edit. Add unit controls for every refusal category, preservation of
unknown NBT fields/types, disjoint entries, corrupted/truncated gzip, write failure and stale plan replay.

**Required native negative control.** Use a separate disposable copy and a test-only coordinator mutation
that omits the final unchanged-file check (no shipped skip option). After the second clean stop, mutate
one bounded, unrelated POI record outside the frozen runtime write list, keeping it valid NBT. All target
resource/identity checks remain enabled. The independent oracle must fail specifically on that file's
hash/change and forbid promotion; the unmutated coordinator must reject the same copy. This demonstrates
that a playable, conserved target is not sufficient. Unit controls separately show that missing/duplicate
identities, skipped journal removal and invented account zero cannot be approved.

Evidence records commands, exit codes, manifests, native observations, negative-control reason and
source/JAR hashes. Keep logs/results/manifests, not extra source archives or disposable server trees.
Respect the 50 MB/file and 100 MB/evidence limits; external large artifacts need SHA-256 and location.
All native servers, complete builds, runData and GameTests use the worker machine lock and owned cleanup.
X01/X02 storage/power-loss residuals remain separate; a successful X03 run does not cover them.

## 6. Proposed slices and estimate

All rows below are **planned**, not assigned or implemented by W367. Estimates are focused engineer-days,
excluding lock wait and candidate/review scheduling. Existing files are linked; proposed filenames are
code spans because they do not yet exist.

| Slice | Scope, dependencies and files | Exit check | Estimate |
|---|---|---|---|
| X03-R. Design review | Root obtains a separate read-only review of this note and decides section 8; Root owns any ADR-065/R-035 update before implementation. | Actual source checks and all findings resolved or explicitly open. | 0.5 day. |
| X03-A. Offline planner/apply | New `scripts/repair_v180_rebuild_source.py` and `tests/test_repair_v180_rebuild_source.py`; reuse `loader_cut_nbt.py` and evidence/read helpers without changing runtime code. Depends on X03-R. | Exact one-record edit, whole-copy/lock/digest/refusal/preservation tests, no-effect failures and interrupted output tests. A different session reviews this saved-data writer before merge. | 1-1.5 days. |
| X03-B. Native family/oracle | New `scripts/run_v180_x03_repair.py` and `tests/test_run_v180_x03_repair.py`; small gated observational/ordinary-action additions in `EscrowCargoRecoveryCuts.java` or its existing probe helpers only if required. Depends on X03-A and an approved finite runtime list. Existing C-S5 assertions stay intact. | Protected seed, two repair save/restart cycles, full-world independent oracle, real unload/launch and required negative control. | 1-1.5 days, plus about 2-4 hours serial machine time. |
| X03-C. Root-3 integration | The two new scripts/tests above and existing shared accounting decoder only if needed; no format or activation change. Depends on the reviewed accounting activation/candidate. | Fresh and migrated retired cases; missing-baseline UNKNOWN, signed audit values and future-schema refusal tests. | 0.5 day, plus serial reruns. |
| X03-D. Operator guidance/evidence | Root-owned guidance/ADR/risk and X03 report/matrix updates, plus this worker's short implementation records as separately assigned. Depends on native evidence and owner approval of wording. | Committed-source evidence reviewed; no premature PASS/admission claim. | 0.5 day. |

Total after this note is about **3.5-4.5 engineer-days**, plus serial execution and candidate wait. The parent
plan's 2.5-3.5 days included a smaller procedure estimate; the extra scope here is strict whole-world/file
verification and both ledger forms. Reuse can reduce this, but no new public command, generic NBT editor,
account-history framework, automatic repair, new save veto or non-X03 repair mode is included.

## 7. Draft operator guidance, not approved for publication

> Stop the server and preserve a hash-verified backup of the complete world, including every dimension,
> player file, ledger, journal and metadata file. Do not delete the transfer entry, reset an epoch, restore
> one store or reconstruct the historical tank. Use only the reviewed repair tool and matching build on a
> complete copy. Review its identities, amounts, UNKNOWN/discrepancy account and exact edit before confirming
> the plan digest. If any check refuses, keep the server stopped and retain the original evidence. Verify
> conservation and exclusive identities, then complete two acknowledged saves and restarts with fresh
> readbacks. Promote only the entire validated copy, keeping the original backup. Restarting alone is not
> repair; the historical snapshot is not a refund. This procedure is available only after its tool and
> native evidence are approved, and it does not enable disabled production features.

## 8. Numbered questions for Root

1. **Approve the narrow one-record offline tool rather than a settlement extension?** Recommend yes:
   select only the corrected, retired, stationary R74/R76 shape; preserve ledger/entities/terrain, and
   refuse every competing authority. Generic FLOOR_CONFLICT repair is a different assignment.
2. **Keep the repair account external when L2 already deleted its row?** Recommend yes: preserve both
   ledger forms and record UNKNOWN historical knowledge unless a typed witness supports explicitly
   confirmed signed values. Never resurrect an operation or manufacture a root-3 account/receipt.
3. **Which candidate can produce final evidence?** Recommend the final accounting-enabled C4 or its
   successor, with root-2 and activated-root-3 cases. Today's disabled-accounting build can exercise root
   2 only as development evidence; it cannot qualify the root-3 repair by changing the world schema.
4. **Approve the exact runtime file lists and negative-control boundary before the native campaign?**
   Recommend a reviewed dry-run-derived list with semantic checks inside it, and the POI-tamper control
   above. Keep the one-file offline boundary distinct from unavoidable Minecraft save changes.
5. **Accept the revised slice estimate and arrange review/publication approval?** Recommend scheduling
   X03-R before code, a separate saved-data-writer review before merge, and owner approval of section 7
   only after native evidence. X03 remains NOT_COVERED until Root reviews that evidence; no Gate or
   production-admission approval follows from this note.
