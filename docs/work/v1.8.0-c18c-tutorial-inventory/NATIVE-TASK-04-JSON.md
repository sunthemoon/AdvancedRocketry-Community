# C18c Task04: bounded JSON syntax consumer

Date: 2026-10-05. Milestone: v1.8.0. Task ID: C18c-01-NATIVE04-JSON.
Contract owner/integrator: Root. Author: c16a04_fluids.
Reviewer: a different agent after the actual source handoff.
Status: CONTRACT_FROZEN; implementation has not started.
Code baseline: `95c43b8588a33c51bf0c7017ea92beedd48833e1`.
Author checkout will be created by Root after this contract is published.

## Outcome and exclusive write scope

Consume already acquired immutable JSON bytes with fixed structural budgets,
returning an owned immutable syntax observation, not a resource/record authority.
Only four NEW author files:

1. `scripts/classic_inventory_fixture_json.py`
2. `scripts/test_classic_inventory_fixture_json.py`
3. `docs/work/v1.8.0-c18c-tutorial-inventory/NATIVE-PROGRESS-04-JSON.md`
4. `docs/work/v1.8.0-c18c-tutorial-inventory/NATIVE-HANDOFF-04-JSON.md`

Existing properties/phase01 modules, Java, schema, registry, configuration,
network, build, CI, ledger, state and governance files are read-only. The author
does not commit/push or create a native host. Root owns task/adoption/integration.
No file opening, path selection, CLI, writer, receipt freshness, player/host
admission, NBT, serializer, field-schema validation or driver is implemented.

## Exact private parsing contract

API: `parse_fixture_json(raw: bytes, role: JsonRole) -> JsonObservation`.
Require exact immutable `bytes` and the fixed private role enum; no arbitrary
context label, budget, path or callback can be supplied. Empty/oversize input
refuses before decoding. Preserve exact raw bytes and SHA-256, without rewriting.

| Role | Raw bytes | Root-inclusive depth | Nodes | Entries |
| --- | ---: | ---: | ---: | ---: |
| external | 16384 | 12 | 2048 | not applicable |
| report | 16384 | 8 | 2048 | not applicable |
| advancement | 262144 | 16 | 32768 | 2048 |
| stats | 131072 | 16 | 16384 | 2048 |
| usercache | 262144 | 8 | 16384 | 1000 |

Equality is allowed; overflow refuses, never truncates or filters. The first
four roles require an object root; usercache requires an array. Report parsing
is payload syntax only, not a file/log origin or the 393216-byte cohort boundary.

Root depth is 1. Every value/container is one node. At object depth d, each
decoded key is one string node at d+1 and its value is one node at d+1; a pair
is not another node. Array elements are at parent+1. Empty containers, null and
booleans count. Syntax punctuation and internal Python representation fields do
not count. Enforce node/depth budgets before admitting an excess node or descent;
whole-document json.loads followed by an unrestricted-tree postwalk is excluded.

Use strict UTF-8 without BOM or encoded surrogates and standard JSON grammar.
Only space/tab/LF/CR may surround the single value. Reject trailing content,
comments, trailing commas, invalid escapes, literal C0 in strings, nonstandard
numeric tokens/grammar and decoded duplicate object keys. Decode paired escaped
surrogates to scalars; refuse isolated/reversed/mispaired escapes in keys/values.
Escaped C0, DEL/C1, noncharacters and canonically distinct Unicode are structural
strings, not field authority. No NFC, case, path, slash or ID normalization.
Preserve encounter order and all unknown members, fully counted.

Return deeply immutable ordered object pairs, tuple arrays, scalar strings,
booleans/null and immutable JsonNumber records. Numbers retain validated exact
lexemes and integer versus fraction/exponent grammar. Do not eagerly convert
huge integer tokens or change the interpreter's global integer conversion guard.
Fraction/exponent numbers retain a finite binary64 projection as an explicitly
lossy observation; overflow refuses, underflow/signed zero keep their lexemes.
Neither projection nor Python bool grants an integer/exact decimal field type.
Later fixed-field validators remain outside this leaf.

Advancement entries count every root member except exact `DataVersion`; reserved
and unrelated fields still count structurally. Stats requires an exact `stats`
object and object category values for its entry observation: sum all category
member counts, including unknown categories/stat IDs; no missing-object default
or coercion. Usercache entries count every root array element, including values
whose later profile schema will be rejected. No malformed-element dropping.

Errors use fixed codes only: JSON_ROLE, JSON_BYTES, JSON_ENCODING, JSON_SYNTAX,
JSON_DUPLICATE, JSON_STRING, JSON_NUMBER, JSON_DEPTH, JSON_NODES, JSON_ROOT,
JSON_ENTRIES. Context is the fixed role (or none for invalid role), never caller
text. Parser-owned error messages/args/code/role contain no input, key/token/value,
decoder text, filesystem path or arbitrary exception text; suppress exception
chaining with from None. This constrains the parser's diagnostic surface, not
an arbitrary caller/debugger's source-line, traceback or local-variable output.
Representation details may be private; they cannot create new authority tokens.

## Evidence and validation

The full underlying proposal and independent review are pinned in the
[Root disposition](NATIVE-REVIEW-DISPOSITION-04-JSON.md). Original native line
annotations have a separate factual correction; no parser policy changes there.
This task explicitly selects the proposed entry/scalar/counting definitions,
including the missing-stats-object clarification above. Native unknown-string
compatibility is not asserted; this is a strict test-owned consumption subset.

Actual source controls must cover all five caps at equality/overflow, key/depth
and empty-container accounting, incremental refusal, strict grammar/Unicode,
decoded duplicate keys, immutable ownership, number grammar/extremes and fixed
non-echo errors. Cover all three entry ceilings and unknown/malformed entries.
Independently test resource ordering rather than only a post-hoc count.
Run the new suite plus unchanged phase01 and properties-input suites; new
fixtures and all TEMP/TMP/TMPDIR must be under an owned project-parent D leaf.
No old rejected cleanup target is eligible for a retry. Preserve original red
attempts, raw outputs, exact source before/after pins and actual exit codes.
Report counts as observations, not a preassigned verdict or test-method total.

Root integrates only after independent actual-source review and fixed-commit
replay, then commits/pushes source and its records before starting another leaf.
No Java/full-regression result is rebound to these pure Python checks. C18c-01
delivery, native inventory/restarts, R-021, ledger and G0-G9 remain open.
