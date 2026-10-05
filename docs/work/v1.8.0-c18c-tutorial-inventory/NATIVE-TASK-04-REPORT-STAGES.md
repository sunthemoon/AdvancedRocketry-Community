# C18c Task04: remaining private report-field observations

Date: 2026-10-05. Task ID: C18c-01-NATIVE04-REPORT-STAGES.
Status: FROZEN_FOR_PRIVATE_IMPLEMENTATION by the paired Root disposition.
No host/native/receipt permission. Author/integrator: Root. Code basis:
`1770f5869d39157463a5136f2ac8a692f9c6dc33`.

## Outcome, boundary and proposed exclusive write scope

Observe all nonterminal Task04 report fields from already acquired immutable
bytes. This completes the private report-field family alongside the committed
terminal consumer, not the native fixture, transport, cohort or restart proof.
The isolated author scope is exactly four NEW paths:

1. `scripts/classic_inventory_fixture_stages.py`
2. `scripts/test_classic_inventory_fixture_stages.py`
3. `docs/work/v1.8.0-c18c-tutorial-inventory/NATIVE-PROGRESS-04-REPORT-STAGES.md`
4. `docs/work/v1.8.0-c18c-tutorial-inventory/NATIVE-HANDOFF-04-REPORT-STAGES.md`

Existing source/tests/contracts, Java, build, registry, schemas, generated data,
state, ledger and Git remain author read-only. No IO, CLI, path, callbacks,
process, logger-origin check, marker, receipt, sequence validator or authority
surface is added. READY_FOR_STOP and FAILED refuse here; the existing terminal
consumer remains unchanged. No combined dispatch or raw-file reader is included.

## Exact private API and observations

`parse_stage_report(raw: bytes) -> StageReportObservation` invokes the committed
`parse_fixture_json(raw, JsonRole.REPORT)` exactly once before field validation.
Keep its role, raw bytes/hash, byte/depth/node limits and diagnostics unchanged.
JSON errors propagate. A syntactically valid but invalid stage shape raises
`FixtureStageError` with sole message/args/code `RECORD_SHAPE`, using `from None`.
Do not echo input, paths, keys, values or chained decoder exceptions. This limits
consumer diagnostics, not arbitrary caller debugging or same-process mutation.

Require exact decoded key sets at every object, no missing/extra members,
coercion, default insertion, silent loss or spelling normalization. Object key
encounter order has no field-validation meaning; ordered arrays retain their
specified order. Return frozen, slotted records; collection members are tuples.
Keep the original immutable raw bytes/hash. Do not expose permission-bearing
handles or inferred success/ready/owned/origin/freshness flags.

`StageReportObservation` fields: `raw`, `sha256`, `schema`, `run_id`, `phase`,
`event`, `index`, `payload`. The last field is exactly `PreflightPayload`,
`PlayerStagePayload` or `DisposedPayload`. Additional records below use the
listed names as their exact fields. Strings/bools/integers retain their exact
decoded values. Position members retain their validated immutable `JsonNumber`
observations, including lexeme and the syntax parser's labelled projection.

## Common envelope and phase table

Exact common fields: schema, run_id, phase, event, index, payload.
Schema is integer grammar with numerical value 1. Run ID is a lower-case hex
UUID of length 36 in exact 8-4-4-4-12 hyphenation, with no extra version/variant
restriction. Phase is exactly seed or reload. Event/index must match one row:

| Event | Seed index | Reload index | Payload family / goal |
| --- | --- | --- | --- |
| PREFLIGHT | 0 | 0 | PreflightPayload |
| INITIAL | 1 | absent | PlayerStagePayload / null |
| CONTROL | 2 | absent | PlayerStagePayload / null |
| ACQUIRE | 3..8 | absent | PlayerStagePayload / goal at index - 3 |
| LOADED | absent | 1 | PlayerStagePayload / null |
| REPLAY | absent | 2..7 | PlayerStagePayload / goal at index - 2 |
| REMOVED | 9 | 8 | PlayerStagePayload / null |
| DISPOSED | 10 | 9 | DisposedPayload |

The six goals in exact order are namespace `advancedrocketrycommunity:classic/`
plus root, block_press, rolling, electrolysis, suited_up and warp_core. Checking
a row's declared index and goal is not checking previous/next rows, origin,
launch binding, deadlines or actual inventory/progression.

## Fixed role table

| Role / array order | UUID | Name | Position |
| --- | --- | --- | --- |
| earned / first | 180c1800-0000-4000-8000-000000000001 | ArceC18Owned | (8.5, 200, 8.5) |
| control / second | 180c1800-0000-4000-8000-000000000002 | ArceC18Control | (10.5, 200, 8.5) |

Every role-ordered array has exactly two members. Identity observations have
exact fields role, uuid, name, matched to this table. A matching string is not
native identity, ownership or admission evidence.

## PreflightPayload

| Exact field | Private shape |
| --- | --- |
| world | exact minecraft:overworld |
| run_marker_sha256 | lower-case 64 hex |
| receipt_sha256 | null for seed; lower-case 64 hex for reload |
| identities | ordered two Identity observations |
| file_presence | tuple of exactly eight JSON booleans, preserving values |
| profile_map_counts | two integer-grammar values 0..1000 |
| pending_requests | integer grammar, value 0 |
| native_progress_cache_counts | two integer-grammar values 0 |
| online_count | integer grammar, value 0 |
| loaded_chunk | two integer-grammar values [0, 0] |
| cache_snapshot_sha256 | lower-case 64 hex |
| host_admission | HostAdmission observation below |

### HostAdmission

| Exact field | Private shape |
| --- | --- |
| revision | integer grammar, value 3 |
| world_data_game_type | exact adventure |
| raw_spawn | three integers: x/z -79..94, y 64..256 |
| shared_spawn | three integers in the same ranges, numerical values equal raw_spawn |
| border_contains_collision_region | JSON boolean observation |
| minimum_height | integer grammar, value -64 |
| maximum_height | integer grammar, value 320 |
| view_distance | integer grammar, value 2 |
| simulation_distance | integer grammar, value 3 |
| loaded_envelope | four integers [-5, -5, 5, 5] |
| loaded_chunks | integer grammar, value 121 |
| loaded_entities | integer grammar, value 0..1024 |
| player_dimensions_bits | two exact strings [3f19999a, 3fe66666] |
| constructor_iteration_upper_bound | integer grammar, value 256 |
| setup_mode | exact seed_add for seed; reload_retained for reload |
| forced_target_membership | exactly 121 JSON boolean observations |
| properties | PropertiesObservation below |

### PropertiesObservation

Exact seven fields: configured_bytes (integer 1..16384), configured_sha256,
boot_sha256, live_bytes (integer 1..16384), live_sha256, logical_map_equal
(JSON boolean), reader_version (integer 1). All hashes are lower-case 64 hex.
No supplied hash is interpreted as a checked file identity or past-byte proof.

## PlayerStagePayload

Exact fields: goal (null or the phase/index-selected exact goal above), players
(two ordered PlayerState observations). PlayerState's exact fields:

| Field | Private shape |
| --- | --- |
| role, uuid, name | corresponding fixed role table strings |
| listed | JSON boolean observation |
| permission_level | integer grammar, value 0 |
| non_fake | JSON boolean observation |
| game_mode | exact creative |
| item_counts | exactly ten integers 0..64, protocol item order |
| other_nonempty_slots | integer 0..41 |
| total_experience | integer 0..2147483647 |
| done | exactly six JSON boolean observations |
| obtained_ms | exactly six null-or-integer values 0..9223372036854775807 |
| obtained_seconds | exactly six null-or-integer values 0..9223372036854775 |
| criteria_keys | exactly six tuples, each exactly (has_inventory,) |
| native_location | NativeLocation below |

Ten-item order: minecraft:crafting_table, then namespace
advancedrocketrycommunity plus small_plate_press, rolling_machine, electrolyzer,
space_suit_helmet, space_suit_chestplate, space_suit_leggings, space_suit_boots,
warp_core; finally minecraft:cobblestone. All six-element arrays are in the
fixed goal order. Timestamp pairs are both null, or both integers with seconds
equal floor(ms / 1000). Do not infer done, awards or semantic inventory changes
from the pairs or permit receipt-based restoration.

NativeLocation's exact fields: dimension (minecraft:overworld), position
(three finite JSON-number observations numerically equal the role position),
motion_bits (three exact 0000000000000000 strings), rotation_bits (two exact
00000000 strings), vehicle_present (JSON boolean observation).

## DisposedPayload

Exact field players: two ordered DisposalState observations. Each has exact
fields role/uuid (fixed role table), listed, connected, channel_open,
channel_active, drained_messages, release_attempt_completed. All five status
fields are JSON booleans. drained_messages is integer 0..2048. The last flag is
an observation, not the return value of finishAndReleaseAll or proof that no
unowned/other resources remain.

## Adopted private numeric and boolean technical selections

Integer fields require the parser's integer grammar, never Python bool/string,
fraction/exponent grammar or binary64 coercion. Numerically zero -0 is accepted
where zero lies in the range, preserving raw bytes. Signed ranges admit the
negative values listed above. Check sign, bounded significant length and range
lexically before conversion; do not call int on unbounded digits or change the
interpreter's integer guard. Numerical equality does not rewrite raw spelling.

For fixed positions only, integer, fractional and exponent JSON grammars are
allowed when their *exact decimal numerical value* equals the fixed coordinate.
Use a bounded lexical decimal comparison; do not obtain equality by binary64
rounding, convert an unbounded integer/exponent, use an unrestricted Decimal
context or impose another token ceiling. Existing REPORT byte limits bound
inspection. Example equivalences such as 200, 200.0 and 2e2 remain admissible;
a distinct tiny fractional residue rounding to 200 in binary64 does not.

All boolean-valued fields preserve either JSON truth value as observations,
including fields whose successful native protocol requires true or false.
This mirrors the committed terminal action_complete=false distinction. It
does **not** change native emitter/admission/complete-cohort requirements:
forced membership, border inclusion, non-fake checks and logical-map equality
must actually pass there; native vehicle absence and actual disposal must also
be independently established. The private parser must not call itself a full
PROTOCOL-03 validator or grant reuse/stop permission from shape acceptance.
These private decimal/boolean selections are adopted by the paired disposition,
not native emitter, complete-cohort or host authority.

## Normative input and unchanged limits

Task04 and its accepted dispositions remain mandatory. Full cumulative
PROTOCOL-03.json is at
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-native-proposal03-5bb27f098e/`, SHA-256
`fc692c32fc3854d80cdbf6ead77fe4c3103925e3a603d68bea43e93504e63edc`.
The committed JSON/terminal/private-file tasks are not broadened or replaced.
REPORT <=16384 bytes, root-inclusive depth <=8, key-inclusive nodes <=2048;
equality is allowed, and parsing enforces limits before fields even for unknown
keys. All 23-row/393216-byte cohort, host/stage/time, ownership, native/NBT and
source-review obligations remain unchanged and unimplemented here.

## Verification and later publication

The exact private contract has the paired independent technical review.
This task/disposition must be published before Root records a fresh isolated
author checkout at an explicit committed baseline. No implementation is assigned
by an uncommitted document or by the technical review alone.
Root retains central files and is the only committer/non-force pusher.

Actual source tests must cover every event/phase/field and wrong scalar/container,
exact keys/arrays/order/constants, UUID/hash spelling, numeric grammar/bounds,
long tokens, signs, exact decimal positions, timestamp null/floor consistency,
boolean false observations, fixed non-echo errors, immutable/raw ownership and
unchanged inherited JSON structural refusal. Check absence of IO/CLI/authority.
Run new tests plus unchanged terminal/JSON/phase01/properties suites; a different
reviewer reruns applicable tests. Record observed counts, not predetermined
verdicts. Root commits/pushes exact source and replays that commit before result
publication. No Java/Gradle/native is needed or permitted for this private leaf.

Fresh helpers, process TEMP/TMP/TMPDIR and owned fixtures belong under a new
project-parent D evidence leaf; Python uses -B. Preserve attempts, pins, raw
argv/exits and qualified cleanup. Do not touch source worlds, other agent work,
unowned processes or old policy-refused targets. C18c native delivery, R-021,
ledger and v1.8 G0-G9 remain open. No player progress is granted/restored/reset.
