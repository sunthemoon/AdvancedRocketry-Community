# C16a-03a bank model and codec qualification

Status: conditional model-only admission, pending independent review of this
qualification. This is not acceptance of a physical hatch/controller adapter,
world writer, native recovery result, completed ledger row or version Gate.

## Frozen scope

The revision-02 proposal and its historical reviews remain unchanged. The
author's 14 production/test Java files are identified by the owned-file
manifest `70eb63383543d95551cd2e83d9af4f8bbe5f1dc031b5fbba3219e50f90eae9a0`;
that manifest also contains seven task documents. Root integrated only those
14 Java files. The original proposal's callback-purity wording is qualified
below, not silently rewritten.

Subject to an independent result with no unresolved Critical/High/Medium,
admission covers only the immutable bank/key/aggregate model, bounded transfer
arithmetic and schema-1 codec described by that proposal. Stable root, fields,
native types, canonical key representations, UUID/revision rules, four Item
slots, 16,000-mB Fluid banks, 64-bank limit and 32,768-byte aggregate limit
remain unchanged. The codec's additional depth-16 and 8,192-node bounds are
admitted for this model only; no existing machine journal budget is enlarged.

## Callback and authority qualification

Transfer methods perform no explicit world or external-storage writes. Trusted
native Item/Forge serialization and deserialization may invoke callbacks;
native Item limits may also call Item code. These operations are **not**
callback-free, a sandbox, a callback execution-time bound or a guarantee of
reentrancy immunity. This qualification takes precedence over the original
proposal's broader statement that transfers have no external callbacks.

The model does not authorize a caller to commit a returned snapshot. Later
controller/capability adapters must enforce loaded-owner, formation, busy,
transaction and lifecycle rules, review their native callback/reentrancy
boundary and provide separate verification. The domain's simulation result
does not establish that a future adapter has no observable callback effects.

## Evidence and remaining work

Independent revision-02 source replay reports 39 JUnit tests passing, plus the
same 84 quantity observations against original and corrected implementations.
The original count-truncation High and its failing observations remain part
of the history; the correction checks positive counts before serialization.
The original API review reports no Critical/High/Medium and one Low concerning
callback wording. A separate review must determine this qualification's
disposition without replacing that report.

No physical hatch BlockEntity, capability facade, cross-chunk ownership,
formation, removal/drop, FE, menu, machine journal adapter, actual chunk writer
or native restart proof is admitted here. Rejected/unbounded-root disk
preservation remains the later writer's responsibility. Existing root build,
GameTest and native Tank results do not prove these future adapters.
