# C16d graph forward 15: limited test-side evaluator contract

2026-10-08. Root proposal, not adopted. Source association:
ead0ece21da8ccd189c4df5512e3a335b7f171ee. The independent leaf15 readiness
report recommends this conditional leaf; full CONTRACT-02 and graph/access
findings are not accepted or closed. Only two NEW test-side files and tests:

src/test/java/io/github/sunthemoon/advancedrocketrycommunity/recipegraph/RecipeGraphReachability.java
src/test/java/io/github/sunthemoon/advancedrocketrycommunity/recipegraph/RecipeGraphReachabilityTest.java

Java17/JDK-only package-private final helper; both files under 500 lines each.
No main-source caller, Forge, Minecraft, resource/file/JSON extraction, adapter,
SCC, hard-lock diagnosis, route expansion, report encoder, registry or assets.

## Interface

```java
static Evaluation evaluate(Set<String> nodes, Set<String> roots,
        List<Producer> producers, Limits limits);
record Producer(String id, List<List<String>> slots, List<String> outputs) { }
record Limits(int nodes, int producers, int slotsPerProducer, int membersPerSlot,
        long memberOccurrences, long outputOccurrences, long workUnits) { }
sealed interface Evaluation permits Success, Failure { }
record Available(int round, String producer, List<String> slotMembers) { }
record Success(SortedMap<String, Available> available,
        SortedSet<String> unavailable, long workUnits, int rounds) implements Evaluation { }
record Failure(Code code, String subject) implements Evaluation { }
```

Nested names are implementation-local, not a new public or persisted API.
Root constants: DEFAULT_LIMITS = (16,384;32,768;64;1,024;1,048,576;1,048,576;8,388,608).
Limits fields may range from zero to their corresponding default ceilings;
negative or larger construction arguments throw IllegalArgumentException.
IDs are opaque nonempty ASCII strings, at most 256 characters, with no control
characters or whitespace (characters 33..126 only). They are not assertions
that a real item, producer, dimension or capability exists. No ID-family grammar.

The input containers are ordinary finite, stable standard-library collections
holding String/Producer values; concurrently changing or adversarial collection
implementations are not supported. Producer constructors do not copy/traverse
unqualified lists; evaluation validates raw sizes/counts before owned copies
or indexing. Root/node/member/output references must all be declared. Producer
IDs are unique. Each producer needs at least one slot and one output; each slot
needs at least one member. Ungated facts are explicit roots; zero-slot producers
are rejected in this subset, not silently turned into round-zero facts.
Repeated references in DIFFERENT slots remain different requirements. Repeated
members inside a selector are allowed and count toward raw member limits/work.
Repeated outputs within one producer are rejected; distinct producers may share
outputs. All raw outputs count toward the separate output-reference ceiling.

Use typed Code values: NULL_INPUT, INVALID_ID, UNKNOWN_REFERENCE,
DUPLICATE_PRODUCER, DUPLICATE_OUTPUT, EMPTY_SLOTS, EMPTY_SELECTOR, EMPTY_OUTPUTS,
LIMIT_NODES, LIMIT_PRODUCERS, LIMIT_SLOTS, LIMIT_MEMBERS,
LIMIT_MEMBER_OCCURRENCES, LIMIT_OUTPUT_OCCURRENCES, LIMIT_WORK.
Null collection/value/limit input returns Failure(NULL_INPUT,...), not a partial
graph or unchecked NullPointerException. Failure subject is a bounded location
description (maximum 256 ASCII characters), not an echoed unbounded bad ID.
Invalid-input first-failure selection may follow validation traversal; this
leaf claims permutation invariance of valid complete results, not a canonical
ordering among several independent malformed-input findings.

## Forward semantics and work

Roots are available at round 0 with producer=null and empty slotMembers.
For each producer, its ordered slots are AND requirements; members within each
slot are OR alternatives. It may apply in round k only if every slot has an
available member of rank strictly less than k. A slot's witness is the member
with the smallest rank, breaking rank ties with Java String.compareTo. Available
output rank is the minimum such k over producers; producer-ID lexical order
breaks equal-rank credit ties. slotMembers uses original slot order. Already
available roots/outputs are never overwritten. rounds is the greatest available
rank, or zero for an empty/roots-only result. Unseeded cycles remain unavailable;
seeded cycles may become reachable. No cycle classification is returned.

Use an occurrence-index/counter worklist, not repeated full producer scans:
process only each newly available node once, in ascending node ID within its
round, visit its raw (producer,slot) member occurrences, and satisfy each slot
once. Producers becoming ready are applied once in sorted ID order in the NEXT
round. All ready producers of that round see the same prior frontier. Their
outputs become that next round's frontier; no same-round cascade. Members
arriving in later rounds never replace an already selected earliest witness.

One forward work unit is charged before each node pop, raw member-occurrence
visit, producer application and output-reference visit, including visits whose
slot is already satisfied or whose output already available. Limit overflow
returns LIMIT_WORK with no Success/partial available set. Work and total raw
occurrence counters use long. Validation, copying, indexing and canonical sorting
are separately bounded by the structural and 256-character limits; workUnits
does not pretend to measure all CPU, allocations, source expansion or encoding.

Bounds are checked before owned allocations/visits they guard. Per-container
size checks precede element traversal; aggregate member/output accounting
precedes copying/indexing those references. Validate IDs/null/references without
an unbounded deep copy, then create bounded owned collections. Success maps,
sets and witness lists are immutable detached outputs, unaffected by subsequent
input mutations. No mutable static state or unbounded queue/recursion.

## Verification and scope boundary

Actual JUnit tests cover empty/roots-only graphs, chain prior-round ranking,
AND/OR and repeated slots, rank and lexical witness/producer ties, multi-output
credit, unavailable/seeded cycles, equivalent OR encodings, permutations,
ownership, every illegal-input code, exact versus over-bound structural counts,
and small Limits work-quota exact/overflow. Small independent synchronous-round
reference calculations in tests are acceptable oracles; they are not used by
the production helper and do not need the optimized algorithm's work counts.
Exact work expectations derive directly from the four counted events, including
already-satisfied occurrences/already-available outputs. Default work overflow
need not be fabricated: smaller test-only Limits exercise it. No report/SCC or
real recipe coverage claim follows from synthetic fixtures.

Root obtains independent review/adoption of this bounded contract, publishes
the decision, then registers a new clean source worktree and a fresh small
Claude writer restricted to the two NEW files plus its handoff. No worker JVM,
Git/central writes or nested delegation. Root preserves source with a commit;
a different agent reviews actual code/diff and executes real targeted JUnit
on measured disks, followed by applicable short integration checks.

This leaf adopts no station-owner/temporal model, portal roots, carbon/steel
balance, recipe mapping, extractor, native observation or new game policy.
Raw catalog R2, complete work/report R3, hard-lock R4, seed-round R5 and full
access-contract issues remain open. These private mathematical limits are not
a relaxation of live-data, network or world-scan budgets. User standing
delegation permits Root recommended technical choices under independently
reviewed contracts without unresolved Critical/High/Medium; major semantics
remain separately confirmed. No ledger delivery, runtime risk, Required Gate
or full recipe-graph readiness is implied; v1.8 remains IN_PROGRESS.
