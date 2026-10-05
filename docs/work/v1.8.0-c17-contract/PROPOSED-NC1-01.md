# Private rocket NC1 BE-data computation leaf: proposed contract 01

Status: **PROPOSED / NOT ADOPTED / NOT A SOURCE ASSIGNMENT**.
Semantic source is `99c639d9f473ad713c56052fd517a44e394525b9`.
This specifies only the first computation leaf identified in the independent
T3 review, section A. It does not freeze the full T3/typed/resource contract.

## 1. Findings, assumptions and decisions still required

1. The existing BE-data ceiling is sufficient for a smallest first NC1 leaf:
   `RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES = 262_144`. It can inspect/frame all
   supported owned native shapes inside that ceiling. It cannot frame the entire
   1,048,576-byte snapshot, flight, Entity, transfer or journal; those later
   current-v1.8 purposes need separately reviewed source/API scope. Their absence
   here is not a narrowed delivery claim for the full shared prerequisite.
2. Root must adopt the specific NC1 grammar, ordering/fidelity/ownership rules and
   the exact private API below after independent contract review. The old byte
   limit is existing code, but a new canonical protocol and new helper are not.
   No numeric branch, hash domain, typed field table, bank/Entity cap or writer is
   selected. There is no arbitrary caller budget, purpose enum or reader ticket.
3. Exclusive stable transitive ownership is a **caller precondition**, not a
   permit supplied by the helper. Same-size races are not guaranteed detectable.
   No current live-world caller is admitted or added. Actual native backing/accessor
   behavior is trusted; exact Tag classes are not a sandbox against reflective
   internal-field replacement, malicious runtime transformations or reentrancy.
4. Negative-zero NC1 bytes are distinguishable and computable. Native factory/load
   normalization is a separate later reload-admission issue. This pure leaf must
   not turn a successful frame into operational persistence eligibility or apply
   a new old-cargo migration/refusal policy to existing loaders.
5. BoundedNbt and hatch K1/K2 are factual patterns, not sufficient implementations
   to call unchanged: eager pending-node traversal/list-child getId, hatch limits,
   recursive encoding and ceiling-sized allocation conflict with this particular
   proposed exact-class-first/lazy/exact-sized boundary. No existing file changes
   or cross-package API widening are authorized by this proposal.

These are technical contract/adoption prerequisites, not evidence of a newly
observed gameplay defect. Existing known source-preservation/R-021 problems and
the prior failed scratch cleanup remain separate and untouched.

### 1.1 Actual ADR implementation-order assessment

The following are fixed-source clauses, not permissions inferred from the peer
T3 A/B/C grouping or the words private/A0:

| Applicable accepted clause | BE_DATA-only consequence / retained prerequisite |
| --- | --- |
| ADR-065:19-28,40-41 | Contract acceptance is not runtime delivery; verify actual C16 deliveries used. This leaf uses no C16 machine, capability, resource consumer or registry; native NBT plus the already-existing RocketLimits are its dependencies. A later consumer cannot inherit that absence. |
| ADR-065:145-153 | Preflight native resources before copy/decode; unknown/root preservation and whole-chunk protection are still owner mechanisms. This helper performs shape computation only, not those protection writers or their admission. |
| ADR-065:257-259 | Independently reviewed scoped acceptance precedes numeric runtime implementation. This helper reads/emits numeric Tag bits, but selects no propulsion multiplier, rate, rounding, debit or runtime numeric behavior. |
| ADR-065:341-376 | Actual whole-snapshot mutation preparation/rate admission needs fair queue, byte/visit reservation and worst-case pure/native measurements. A helper with no caller cannot claim those costs satisfied or admit a mutation. |
| ADR-065:378-386 | Entity3/snapshot2/flight3/journal3 require full independently reviewed fields and migration fixtures. No schema bump, domain hash, migration or resource snapshot is introduced here. |
| ADR-065:1159-1171 | D1/D1-disassembly/D3 schema/recovery contracts and D3 forced-stop proof gate dependent interactions; C17b orbital implementation specifically waits for accepted D2-A checked-transition/wire. This helper is neither StationOrbitalPhase nor an orbital model/control/wire or resource interaction. The final sentence permitting non-dependent pure test/design does not itself authorize production source. |
| ADR-066:761-763,889-892,939-958 | First-event writer/durability precedes its implementation; exact runtime sub-contracts and C17 joint sky/journal dependencies remain. This leaf changes none of them and is not their completion authority. |

The separately pinned `ORBITAL-PHASE-ELIGIBILITY-DISPOSITION-01.md:5-10,27-33`
correctly refuses to relabel a D2-A-dependent production orbital model as A0.
Its restriction is not satisfied or changed here; that model and its full shared
prerequisite remain unadopted.

**Proposed eligibility conclusion for independent review:** no listed clause
requires a D2-A/typed-schema/first-event-writer ADR amendment merely to introduce
the two isolated, uncalled BE-data computation files specified here. A separately
reviewed, explicitly Root-adopted narrow contract and source assignment can
suffice, because the actual implementation object/dependencies are different,
not because computation/source admission is automatically exempt. Root should
record this exact scope interpretation in adoption. If that adoption instead
permits an orbital/typed/migration/live resource caller, silently changes a
selected boundary or waives applicable full verification, the argument no
longer applies: the full prerequisite or applicable ADR amendment is required.
An actual source assignment and later committed replay are still absent.

General AGENTS/version full-build/DataGen/GT and other applicable Required Gates
are **not waived** by the proposed tiny A0 command. A0 can be a bounded development
and source-review check while heavy runs are prohibited; it is not a substitute
for applicable integrated checks, source delivery or a Required Gate decision.

## 2. Existing facts versus proposed delta

Paths below are relative to `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`
at the fixed source object.

| Existing fact | Exact source / consequence |
| --- | --- |
| BE cap 262144; total snapshot cap 1048576 | `rocket/RocketLimits.java:9-10`; use only the former here, without changing either |
| BE payload copies before measuring, rejects outer x/y/z/id | `rocket/model/RocketBlockEntityPayload.java:16-30`; the new helper does not replace this constructor or inherit its adapter/schema validation |
| Old measurement is recursive NbtIo.write | `rocket/model/RocketNbtSize.java:14-22`; do not invoke it on uninspected graphs to implement this new inspector |
| Old snapshot hash uses its own key/value order | `rocket/model/RocketSnapshotHasher.java:20-99`; no old hash or codec is changed |
| Existing native preflight classes/UTF/typed-empty/NaN rules | `persistence/BoundedNbt.java:22-93`; facts retained, but line 52 calls child.getId before exact-child-class gating and its pending queue materializes all immediate children |
| Private hatch encoder is recursive, uses fixed ceiling buffer | `machine/classic/adapter/ClassicNbtCanonicalBytes.java:16-31,71-164,168-191`; do not import it or enlarge its limits |
| K1 unsigned UTF8 then original UTF16 tie | `machine/classic/adapter/ClassicNbtKeyOrder.java:11-29`; same proposed order, rocket-local implementation, no hatch API/dependency change |
| Native Item/Fluid conversion performs registry/serialization work | `machine/classic/resource/ClassicNativePayload.java:19-121`; explicitly outside this helper |

The prior proposed T3 `PROPOSED-FRAME-01.md:84-148,150-230` supplies the proposed
NC1 grammar and ownership facts; independent review `e20e9ad0...`, section A,
separates this computation from hash/typed/migration/writer work. Neither record
is accepted source authority. This leaf is a deliberate subset of its proposed
ten-purpose API, not an adoption of those ten caps or hash methods.

## 3. Exact proposed write scope and dependencies

Only two NEW source/test paths are needed:

1. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketNativeFrame.java`
2. `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketNativeFrameTest.java`

The primitive is a package-private final class, targeted at <=500 source lines.
Private/nested immutable result, enum, cursor and sink types remain inside it;
no general framework or separate public budget type. A test file exceeding500
lines needs responsibility assessment rather than expansion by default; an
additional test unit would require explicitly amended assignment scope.
Root may separately assign author progress/handoff records, not central/status
changes. This proposal assigns none of these files yet.

Production imports are JDK17, exact pinned standard `net.minecraft.nbt` types and
existing `rocket.RocketLimits` only. No hatch, registry, Item/Fluid, server, client,
world, event, capability, persistence service, network or GuardTicket dependency.
Do not change BoundedNbt/K1/K2/old size/hash/codec/cargo code.

Package-private placement means codecs in `rocket.persistence`, flight or Entity
packages cannot call it directly. A future typed projection in `rocket.model`
could reuse it under a reviewed larger-purpose/owner contract. Any bridge/public
surface and larger purpose must be explicit later work, not assumed present now.

## 4. Exact API, results and preconditions

```java
// Both methods and their enclosing class are package-private.
static Inspection inspectOwned(CompoundTag owned);
static byte[] encodeOwned(CompoundTag owned);

record Inspection(Status status, long namedBytes, long nodes,
                  int deepestTag, int deepestContainer, Reason reason) { }
enum Status { SHAPE_VALID, REJECTED }
enum Reason {
    NONE, NULL_INPUT, TYPE, KEY, LIST, UTF, NUMBER_BITS,
    CYCLE, BYTES, NODES, DEPTH, CHANGED
}
```

All types are nested private/package-private internal types. No persisted/public
ID, new root/schema/key, hash, UUID, domain, signature or API permit is introduced.

- `owned` is an exact CompoundTag, transitively exclusively owned and stable
  across every inspection/encoding pass. Null or a CompoundTag subclass refuses.
  The caller also preserves the graph while using its result; naming it Owned
  does not acquire ownership or confer live-thread permission.
- SHAPE_VALID has exact named-empty-root byte size, occurrence count and depths;
  reason NONE. REJECTED has all four numeric fields zero and one fixed reason.
  This discards partial measurements as authority, not input data.
  Root Tag depth is1, each child Tag depth is parent+1. Container depth counts
  only CompoundTag/ListTag ancestors including the current container/root;
  arrays are scalar-shaped Tags for this metric. Reject reasons identify the
  encountered condition; multiple independently invalid conditions need not
  have a deterministic winning reason across native map iteration orders.
- `encodeOwned` first successfully inspects the input. Refusal throws
  IllegalArgumentException with only fixed ASCII `Rocket NC1 refusal: <Reason>`;
  no key/value, path, subtype content, native class name or input exception message.
  Do not attach an input-bearing cause. JVM fatal/allocation failures are not
  turned into SHAPE_VALID or swallowed as data repair; finite computation bounds
  are not a guarantee that the process has sufficient heap.
  There is no file/log/callback, error recovery, default, copy or normalization.
- Success returns one fresh caller-owned byte array exactly `namedBytes` long.
  No Tag/array/backing scratch alias or static cache is exposed or retained. On
  failure no partial byte array escapes; the input is left untouched.
- Structural inconsistency actually observed between passes or a concurrent
  modification exception may produce CHANGED. Stable-count comparisons do not
  prove that a same-size change, transient mutation or same-array write did not
  occur. Correctness under such races is outside the stated precondition.
- Inspection is only shape/emission computation, never adapter/schema/registry
  validation, raw-record authenticity, native reload, loaded-owner or commit
  admission. Arbitrary supported metadata keys remain metadata; x/y/z/id and
  unknown owner fields are not semantically judged by this generic shape helper.

## 5. Exact framing rules selected for proposed adoption

NC1 is named-empty-root **uncompressed native NBT layout**, not SNBT/JSON/hashCode.
Root bytes are `0A 00 00` followed by compound payload. Big-endian throughout.

| Exact native class / ID | Payload |
| --- | --- |
| ByteTag1 / ShortTag2 / IntTag3 / LongTag4 | signed 1/2/4/8-byte value |
| FloatTag5 / DoubleTag6 | DataOutput float/double emitted bits; reject raw bits differing from Java canonicalized NaN bits |
| ByteArrayTag7 | nonnegative signed I32 length + exact bytes |
| StringTag8 | U16 modified-UTF length + original UTF16-unit encoding |
| ListTag9 | element type byte + nonnegative signed I32 count + ordered unnamed payloads |
| CompoundTag10 | sorted `(type byte, modified-UTF name, payload)` children + End0 |
| IntArrayTag11 / LongArrayTag12 | nonnegative signed I32 count + ordered signed I32/I64 elements |

Class equality precedes every child accessor/type inference. Reject foreign Tag
subclasses and named End, without calling their getId/write/copy/equals/hashCode/
toString. Containers use exact native accessors only. All native writers and
recursive equality/copy are absent from production implementation.

Keys are unique exact Java Strings; null key/child refuses. List nonempty subtype
is1..12 and every child's exact class ID matches. Empty list admits only subtype0;
typed empty remains untouched and refuses, because its native writer changes it.
Arrays are one Tag occurrence, not a list of scalar Tags. Their data/order are
byte-charged and read without exposing source arrays.

Modified UTF encodes each original UTF16 unit: NUL=C0 80; 1..127 one byte;
128..2047 two; other units three, including lone surrogates. A surrogate pair is
six bytes. Each key/value must fit65535 modified-UTF bytes before allocation or
emission. No fallback empty, replacement, trim or Unicode normalization.

Compound order compares unsigned ordinary Java UTF8 bytes, then original unsigned
UTF16 units and unit length on a byte tie. Only ordering uses ordinary UTF8;
emission uses original modified UTF. Lone-surrogate replacement ties therefore
do not collapse distinct keys. Old natural String hash order is unchanged.

Canonical positive quiet NaN and infinities are not silently zeroed; noncanonical
NaN raw bits refuse to prevent write canonicalization. Positive/negative zero
have different pure output bytes. Native factory/load zero normalization is a
separate unadopted operational boundary, not a new finite-only numeric policy.
Private-constructor fixtures prove only emission mechanics, not gameplay/load
eligibility. Root adopts these pure rules only, not persistent signed-zero use.

## 6. Bounds, traversal and allocation

One fixed computation purpose: BE_DATA named bytes <= existing262144. No caller
cap, broad purpose enum or additional bank/Entity/hash-domain constants.

- Counts use checked LONG arithmetic before allocation: root named header3;
  compound terminator1; named child header3+MUTF(name); all payload widths above.
  Tag nodes count every occurrence, including root; keys are not separate nodes.
  Derived node ceiling262144 and root-depth1 maximum Tag/container depth262144
  are nonrestrictive safety counters, not new old-cargo quotas. Every admitted
  occurrence occupies at least one payload byte; arrays count once.
  The inductive lower bound is payloadBytes>=occurrenceNodes: each scalar/array
  contributes at least1 byte/one node, each list contributes5 plus its children's
  payloads/one plus their nodes, each compound contributes1 plus named-child
  headers/payloads/one plus their nodes. Repeated aliases are expanded on both
  sides. Tag/container depth<=occurrenceNodes, and namedBytes adds the root3.
  Thus these derived counters cannot exclude an otherwise admitted finite frame
  under the existing byte ceiling. This argument does not admit malformed old
  data, cycles or unsafe native-reader depth.
- An active-ancestor IdentityHashMap/set tracks only container identities. Reject
  a cycle; remove ancestors on exit. A repeated sibling/descendant alias is
  traversed/counts/emits at each occurrence. Do not deduplicate or use Tag.hashCode.
- Both passes are iterative with lazy per-container cursors. Do not recurse or
  enqueue all children/maxNodes at once. Do not preallocate maxNodes/depth slots.
  Inspect uses no output buffer and need not sort keys. It charges UTF/arrays and
  rejects before a copy/native writer/encode path.
- After successful inspection, allocate exactly its byte size, at most262144.
  Encoding writes canonical keys with a bounded output cursor. Re-check structural
  counts/type/length/depth and final byte/node counts; discrepancy refuses with no
  partial result. The buffer may itself become the successful caller-owned result
  because it is newly allocated per call and not retained elsewhere.
- Collect key references/ordinary-UTF8 ordering bytes only for visited compounds,
  lazily under inspected counts. At any active path, retained key references are
  bounded by measured nodes; total retained ordinary-UTF8 bytes by measured named
  bytes. Cursor/ancestor counts are bounded by measured container depth. Do not
  copy child graphs or allocate a map entry per all occurrences in advance.
  These count bounds are not measured heap/GC, CPU or fair tick-work proof.
- Standard string lengths and array arithmetic are checked against remaining
  named bytes before building UTF caches/output. Arithmetic overflow refuses;
  no arithmetic wrap, truncated string, approximate sizeInBytes or compressed cap.

Absolute native reader depth512 and enclosing data/list/Entity wrappers are NOT
this pure limit. For example, an empty-key compound chain can fit262144 bytes
while greatly exceeding512; this leaf computes it iteratively but confers no
native loader permission. No reader context ticket is manufactured.

This is not a runtime caller or a scheduling framework. Any future live consumer
must account for both passes, sorting/comparisons/temporary copies under existing
ADR065 work budgets and prove actual allocation/runtime context. This task does
not declare synchronous worst-case work safe on a server tick.

## 7. Later NC1 joins and compatibility boundary

The same grammar/order/bit rules preserve later byte joins for subgraphs <=262144.
Golden frames are versioned private computation fixtures, not persisted roots.
Whole snapshot/flight/transfer/Entity/journal framing, incremental hashes, typed
projections, shallow omission and registered domains require separate contracts
and source scope. Do not concatenate standalone named children as a substitute
for a complete parent frame or add a wrapper to cargo for this helper.

BE payloads already have262144 named-byte cap, but old code does not impose the
derived structural preflight or emission-refusal rules. No existing capture,
load/save/restore/migration call is replaced. This proposal cannot declare every
previously legal old native payload compatible: typed empty, overlong UTF, raw
NaN, deeply wrapped data, already normalized bits and arbitrary adapter behavior
need separately pinned old/native fixtures and T5 preservation work. Refusal here
retains the caller's in-memory reference; it does not preserve absent raw bytes
or guarantee an outgoing old serializer will retain them.

All numeric selectors, full typed schemas/bank/Entity caps, old hashes/checksums,
world writers, hold/release, GuardTicket, R-021, ownership acquisition, early raw
capture, migration, checked durability, O3/C17b and version Gates remain outside.

## 8. Required verification after separate adoption/source assignment

### Actual Java A0 source checks

Compile explicit helper/test and existing RocketLimits with a Root-selected
**pinned JDK17**, actual mapped Forge47.4.10/JUnit and required existing dependency
classpath. Root must provide exact command/classpath/slot and D-only output/temp
before execution. A tiny javac/JUnit run is an explicit alternative, not permission
to start Gradle/full build/GT/server while disk/heavy-run constraints prohibit it.
No current Java permission exists. Keep command timestamps/exit/fresh JUnit counts,
source pins, actual allocation/time observations and all first failures. A0 does
not prove native gameplay or persistence. Independent actual-source review and
Root fixed-commit replay are separate requirements before delivery recording.

### Mandatory helper/test subjects

1. All12 exact native types, big-endian widths/root/end, array byte accounting;
   fixed golden bytes and deterministic permutations, including malformed UTF16
   ordering ties and supplementary versus private-use ordering.
2. Small bounded native differential: compare scalar/string/array/list-of-scalars
   payloads with their pinned native write(DataOutput), compose outer keys in the
   chosen canonical order. For nested compounds, traverse the test oracle in that
   order rather than assume native map iteration is canonical. NbtIo.write total
   size may be a separate small safe size comparison, not byte-order equality.
3. Typed empty, mismatched subtype/null/foreign child/root/End, cycle versus alias;
   foreign spy methods remain uncalled and no recursive copy/equality precedes
   refusal. Unknown metadata keys are not owner-schema validated.
4. MUTF65535 exact/one-unit-over boundaries, surrogate/NUL, raw NaN/infinity/zero
   controls. Native factories/load normalization and private-constructor emission
   fixtures are separately labelled; do not weaken bit assertions or invent old
   provenance. Refusal leaves input list subtype/numeric bits/arrays unchanged.
5. Exact262144 and over-cap arrays/dense byte lists/compound chains. Dense/deep
   tests exercise iterative inspection/encoding without invoking recursive native
   writers on unsafe depth. Confirm derived node/depth counters do not import
   hatch8192/depth25 or implicit512. No maxNodes-sized scratch allocation.
6. Fresh output/input nonmutation/no retained alias, fixed errors/no input echo;
   changing size/subtype during a controlled pass produces refusal where observed,
   but stable-owner precondition is not replaced by a same-size-race detector.
7. Measure actual worst-case admitted output and lazy cursor/key scratch behavior;
   record limits of heap/GC/time measurement. Pure tests do not close fair tick,
   raw/native reader, old compatibility, migration or resource-writer requirements.

No full frame/hash/typed/cargo bridge implementation, file/world/registry callback,
server/native/client/network/cleanup target or accepted policy is changed by this
contract. Root may freeze this exact narrow technical leaf only after a different
agent's contract review and explicit adoption; source assignment follows later.
