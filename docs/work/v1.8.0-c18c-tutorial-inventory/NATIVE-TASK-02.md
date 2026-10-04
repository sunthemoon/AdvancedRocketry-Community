# C18c-01-NATIVE-02: owned inventory advancement restart fixture

Date: 2026-10-04. Status: PROPOSED_FOR_INDEPENDENT_REVIEW.
Integrator: Root. This revises the test-only ownership contract, not gameplay.
The unchanged NATIVE-TASK-01 is historical and must not be implemented.
No source writes or runtime admission follow from this proposed task.

## Identity, dependencies and outcome

Verify the six committed inventory tutorials through the packaged dedicated
server's real inventory listener and vanilla player advancement save/load.
Source basis is `a5abc34809891d6b10724ee60ab9b5e97f036bae`; Root's integration
checkout is `bd0148e98d065df6ac77789eca0a5dae9b6a409a`. The future fixture must
have its own committed tested cohort. Neither existing artifact implements it.
The accepted ADR-066 section 6.2 and original C18c-01 task remain unchanged.

The old draft's independent Medium finding concerns offline player-record
ownership. Fresh criteria do not authorize constructor/join side effects on
existing records. The replacement requires both identities' non-mutating
admission before constructing either player, and an external driver-authored
receipt after verified first clean stop before allowing reload of those files.

Normative proposal companions are the following frozen files under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-native-proposal-8a05f49bd1/`:

| File | SHA-256 | Contract role |
|---|---|---|
| `OWNERSHIP-PROPOSAL-01.md` | `5314b0ca92dac51fdeda7b7bb1d22db97af435a86c12b2410dcb0ade73d4d581` | Exact authority, paths, phase, lifecycle and bounds |
| `PROTOCOL-01.json` | `1685e9a5404558c5b5fc35e17911118fb6f5b2d1426d8f98e663960fdbe211bf` | Fixed observational fields, order and parser limits |
| `CHECKPOINT-01.md` | `5245142500e51ed4183fcb4f2a1fa0d842081b7dde75cc1f8112883ae852f8cd` | Actual source/static checks and limitations |

The bundle manifest is
`cc689403716863d8885092c1e26ab5eee720621d06058c1581166d37d03af2ae`.
Root must preserve these exact companions with the eventual disposition, not
leave a mutable external proposal as the only contract record. Contradictions
or missing fields require revision and review rather than implementation guesses.
The author's 18 Python specification checks are not fixture/native tests.

## Proposed technical adoption and unchanged prohibitions

Subject to independent review, Root proposes the narrowly scoped read-only
reflection described in proposal section 4: the actual GameProfileCache name,
UUID and requests maps, file and entry profile; PlayerList stats/advancements
maps. Resolve only the exact declared mapped/SRG pairs, descriptors and native
class assumptions. Fail closed on inaccessible, ambiguous or foreign state.
No setters, public mutating cache lookups, eviction, network profile lookup,
AT/coremod or general reflection framework is permitted. Test-only MRU/count
reads may establish refusal nonmutation but confer no ownership authority.
Packaged resolution remains a separately required runtime observation.

No direct advancement award/grant/CriteriaTriggers call, record repair/reset,
old-record adoption, gameplay SavedData/store, reward simulation, arbitrary
identity/path/coordinate/item arguments or persistent mutable player map.
No changes to existing player helpers, frozen six tutorial source files,
production save writers, shared guards, equipment APIs or world-first awards.
No original world, original player record, other-agent file or sealed evidence
may be edited or deleted. Failed admitted actions have no rollback promise;
their copy is not eligible for reuse, reload or ownership-receipt creation.

## Fixed phase and ownership requirements

Use only `arce classic-inventory release-test seed` and `reload`, with both
explicit opt-ins, exact permission-4 console authority, actual server thread,
ordinary dedicated Overworld, no connected players, fixed loaded chunk `(0,0)`
and already-air foot/head cells. Hook cannot load chunks or change terrain.
Only the driver may issue the fixed copy-local `forceload add 8 8` command.

Use the exact two role/UUID/name/position tuples and six goals in PROTOCOL-01.
Both identities' eight native record paths, temporary-file prefixes, online
UUID/name, local cache tuples and bounded live maps are checked before any
ServerPlayer, Connection or EmbeddedChannel is constructed. Expired profile
entries are collisions, not an eviction opportunity. Check both snapshots
before construction. Empty PlayerList progress caches are required in each
fresh host; online absence alone is insufficient.

Root-authored sealed cohort inputs bind the named source server, world, JAR,
libraries and committed source. The copy-only driver rejects overlap/reparse
redirection/existing output and creates the fixed run marker once. Paths derive
from the actual server/world directories; marker strings cannot select another
world. First seed requires absence of all fixed player records and collisions.
Only complete seed reports, explicit owned disposal, real clean exit 0 and
bounded stopped-file inspection permit the driver to create the fixed receipt
once. The hook never writes that receipt. It authorizes only these two test
identities in this same copy, never supplies or recreates advancement progress.

Reload requires exact marker/receipt/run/path/artifact/native-file bindings
before construction. Inspect real loaded progress with empty inventory before
any acquisition, then replay ordinary inventory changes. Compare live replay
milliseconds and stopped native second-resolution timestamps appropriately.
Remove targets through the normal listener before save/removal and require
actual empty inventories, unchanged experience, isolated control and empty
tutorial rewards. Other legitimate vanilla/recipe progress is not reset.

Own handles capture each resource immediately after successful construction.
Partial join/disposal acts only on the exact owned objects, including unlisted
player listeners/entity, Connection and EmbeddedChannel. Validate actual listed,
connected, open/active and drained/released observations; finally attempts all
owned cleanup even after a failure. Never infer complete disposal from a method
return or successful command label. Failure suppresses completion and receipt.

## Fixed protocol, budgets and stopped-record evidence

Use the exact authoritative INFO logger/server-thread JSON framing and fields,
12 seed plus 11 reload rows, finite failure row and strict unknown/duplicate/
order/type rejection in PROTOCOL-01. Parser tests must bind actual packaged log
headers, not substring success labels. Hook reports observations; the driver
independently inspects actual stopped native records.

All byte/depth/node/count/path limits in the companion are mandatory before
unbounded allocation/deserialization. In particular, fixed native gzip/NBT
input is limited to one member with no trailing bytes, 262,144 compressed and
1,048,576 expanded bytes, depth 32 and 65,536 nodes. Empty native Inventory is
named List tag 9, subtype 0, signed count 0, independently established from
vanilla's own writer. Root UUID must be the exact native IntArray length 4.
These are this fixture's admission rules, not shared hatch or gameplay policy.

One monotonic cohort deadline is 300 seconds: intake 60, each host 110, final
validation 20. Within a host: startup 60, action including cleanup 20, stop 30;
each bound is capped by the remaining cohort time. Hook checks its 10-second
operation deadline between bounded stages, without promising native preemption.
No extending deadlines, retry-as-success, swallowed failure or unknown ERROR/
FATAL tolerance is permitted. Preserve actual partial outputs and failures.

## Future isolated write scope

After exact leaf review and Root's disposition, Root creates a new D worktree
at the then-declared committed base and assigns one delegated implementation.
These are the only proposed new implementation files, under the existing
`io/github/sunthemoon/advancedrocketrycommunity` package where applicable:

- `gametest/ClassicInventoryAdvancementNativeFixture.java`;
- `gametest/ClassicInventoryFixturePlayers.java`;
- `gametest/ClassicInventoryFixtureOwnership.java`;
- `gametest/ClassicInventoryFixtureRecords.java`;
- corresponding narrowly scoped tests for those four classes under `src/test`;
- `scripts/run_v180_classic_inventory_smoke.py`;
- `scripts/test_v180_classic_inventory_smoke.py`;
- this task directory's new `NATIVE-PROGRESS-02.md` and `NATIVE-HANDOFF-02.md`.

Prefer existing bounded utilities where their actual policy fits; do not add a
generic parser/cache/framework or mix observation, ownership and lifecycle in
one giant class. Over 500 lines requires responsibility review; over 800 needs
an ADR. If a helper cannot fit the explicit scope, propose its exact need first.
Root owns central registration, generated resources, ledgers/status, contract
dispositions and every commit/push. Workers cannot alter build/Git history or
earlier frozen files. Tests do not expand admitted runtime scope.

## Required source and runtime verification

Independent source review and rerun key checks before integration. Test all
eight file collisions, temporary prefix, both identity/name/cache collisions,
orphan progress cache, free-first/colliding-second and unavailable authority/
phase/loaded area. Refused preflight must create/join/write/lookup nothing.
Exercise malformed/partial/future/changed receipt, binding/path/hash/shape,
bounded native and JSON rejection, partial construction/join/channel/final
disposal failures and strict report/deadline controls. Assert ordinary listener,
loaded-before-acquisition progress, replay precision and empty rewards.

Commit source before delivery evidence. Run clean build/test/runData, generated
diff, the complete required GameTests and focused Python controls on the exact
cohort. Package and run the two clean dedicated hosts with source/world/JAR/
library pre/postchecks, exact commands/time/exit/raw logs, rows, driver receipt
and actual stopped UUID records. Independent result review must distinguish
this connected mock from actual client, multiplayer, V1/V2 or GPU evidence.

Only after the six whole-unit obligations pass may Root consider their ledger
delivery. This task cannot close composite playerTick, other advancements,
R-021, cross-store/crash durability, shared writer admission or G0-G9.
Whole v1.8 remains IN_PROGRESS /IMPLEMENTING and release acceptance is separate.

## Temporary storage and cleanup

Helpers/output/process-local TEMP/TMP/TMPDIR/Java temp are under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/`, not C. Check C and D each have at least
10,000,000,000 free bytes immediately before Java/native work. Do not change
global settings or kill unrelated Java processes. Preserve compact logs,
results and fixed fragments rather than full source/runtime/world archives.
Only new task-owned stopped copies are cleanup candidates after checked native
literal-path/reparse/ownership/preservation checks. Tool-policy rejection is
recorded as unexecuted cleanup, never bypassed by another executable or shell.
Previously rejected C/native04/native05 targets remain separately documented.
