# C16a-03b physical shared hatch / controller preparation

Status: PROPOSED read-only preparation, 2026-10-04. No interface, runtime writer,
ledger unit, implementation task admission or version Gate is approved here.
Exact inspected inputs/checks are recorded in [INPUT-MANIFEST.json](INPUT-MANIFEST.json).

## 1. Fixed decisions and actual baseline

ADR-064 revision 4 and the accepted hatch leaf remain authoritative:

- Five new stable block/item IDs: `item_input_hatch`, `item_output_hatch`,
  `fluid_input_hatch`, `fluid_output_hatch`, `power_input_plug` in the project
  namespace. No piston movement. Existing rolling/precision/electrolyzer ports,
  IDs, roots and schemas **remain their existing implementations**.
- Controller UUID plus absolute Level position and exact bank kind identify
  resources. Item/Fluid hatches are facades, not resource carriers. Items from an
  assigned removed Item hatch drop at the controller once. Removed Fluid hatch
  banks remain at the controller and can reattach only at the same position/kind
  after valid formation. Unformation/unload never discards resources. Controller
  removal drops remaining Items and drains remaining Fluid.
- Bound unavailable/unsupported owners and pending transactions refuse ordinary
  player, explosion and Forge-aware entity removal without loading chunks.
  Item entity saves and physical FE transfer do not gain arbitrary-crash atomicity.

The independently qualified API text is SHA
`94d70229a6c2315aae207b88d411b890001bdeb85336327458c9ffc0a9f758a3`.
Actual root bank code matches all **14 frozen author Java files**, eight production
and six tests. Existing 39-test/84-observation evidence is historical independent
model evidence; no Java test was rerun for this preparation. Its qualified scope
is immutable bank values, transfer arithmetic and strict codec only. Native
serialization, decoding, getters and Item limits may call mod code. Simulation
is not a callback sandbox, reentrancy guarantee or authority to publish a result.

The current codec already freezes the resource-root fields/types:
`schema_version` Int=1, `machine_uuid` IntArray[4], `revision` nonnegative Long,
`banks` ordered Compound list (empty list TAG_End). Bank identity redundancies
`channel/kind` String and `x/y/z` Int must agree; Item `items` is exactly four
Compounds, Fluid `fluid` one Compound. Native Item Count is Byte; Fluid Amount
Int. Unsupported ForgeCaps are refused, not stripped. Empty payloads are empty
Compounds. Replacement adds/replaces banks, never implicitly deletes a key,
and advances revision once only when changed. Retained empty/inactive banks
still count. Long.MAX_VALUE changing operations, including simulation, refuse.

## 2. Smallest honest vertical slices

**03b adapter slice:** five physical hatch kinds, strict hatch codec, one narrow
loaded-owner capability boundary, controller checkpoint/formation adapter,
removal protections and shared guarded-save consumer. Exercise these through a
test-only hosted controller/pattern at first; do not introduce a persistent
placeholder gameplay controller ID or retrofit the v1.2 machines. This can verify
the adapter but cannot honestly establish a playable family machine/native
controller restart before there is an admitted real controller.

**First dependent gameplay slice:** a separately reviewed C16b lathe leaf is a
small useful consumer (accepted 2×1×4 layout, one ingot→two existing C15a rods,
300 ticks×20 FE). It avoids the cutting machine's additional saw-blade dependency
and the larger arc furnace. Exact authored pattern, hatch positions and crafting
recipe still require that leaf review; no upstream layout/code was imported here.
Lathe admission is not granted by this memo. Its Item/power path alone cannot
cover family Fluid facade/rebuild semantics: retain a test-only all-five-kind
fixture and later a real fluid-process consumer for those claims.

Implement/model-test before registration, then registered GameTests, then final
packaged dedicated-server two-restart/cross-chunk evidence. Five blocks existing
in a registry or a model unit count does not close C16a-03 or admit seven writers.

## 3. Public Java boundary to freeze, not implemented

Keep `machine/classic/resource` unchanged. Proposed small adapter vocabulary:

| Type / method family | Authority and required shape |
|---|---|
| `ClassicHatchKind` | Five block kinds; optional existing ClassicBankKind; explicit pattern role. Power has no bank/channel. Never cast power to a fifth bank kind. |
| `ClassicHatchIdentity` | Level ResourceKey, immutable hatch position, kind, optional exact MultiblockPartBinding, canonical derived bank key; captured transient hatch epoch. No client-created assignment or arbitrary remote lookup. |
| `ClassicControllerHost` | Actual loaded controller identity/state and guarded operations, not a public unrestricted resource setter. Backed by the owning BE; no inventory global. Downstream machine-specific code supplies immutable pattern/role data and durable busy/recovery state. |
| `ClassicFacadeAccess` | Complete Item/Fluid/Energy operation entry points, including native getters, validation and SIMULATE. Resolve fresh loaded owner; execute the whole callback-bearing operation under controller/hatch admission and finally release. No caller-held mutable bank or authority-bearing cached controller pointer. |
| `ClassicFormationCandidate` | Complete immutable prospective host state/assignments and bounded resource replacement. Prepared without publishing; exact old checkpoint/revision/epochs/live BE identities must still match at publication. |
| `ClassicRemovalPlan` | Validated owner/key, bounded native Item drop vector and aggregate replacement. Removal code owns one publication/drop attempt; an external caller cannot commit or replay it. No new arbitrary Item-entity durable coordinator. |

Concrete candidate Java signatures for independent technical review:

- `ClassicControllerHost.ownerView()` returns an immutable identity/status view;
  it grants no mutation or direct native payload reference.
- The adapter-internal `openFacade(ClassicHatchIdentity, ClassicAccessMode)`
  returns an explicit denial reason or one short-lived guarded
  `ClassicFacadeAccess`; it is not a public third-party lock/snapshot API.
  `ClassicControllerHost` supplies the typed guarded operation delegation to
  its Forge hatch adapter. Automation is the first supported mode. A caller
  cannot select a menu mode to bypass an actor, distance or permission check;
  menus require their separate admission.
- The access operation families are `readItem(int)`,
  `insertItem(int, ItemStack, boolean simulate)`,
  `extractItem(int, int, boolean simulate)`, `isItemValid(int, ItemStack)`,
  `readFluid()`, `fill(FluidStack, boolean simulate)`,
  `drain(FluidStack, boolean simulate)`, `drain(int, boolean simulate)`,
  `isFluidValid(FluidStack)`, `readEnergy()` and
  `receiveEnergy(int, boolean simulate)`. Kinds without that resource expose
  no corresponding capability. Read/validation results are also guarded.
  Fixed slot/tank counts/capacities, native slot limits and Energy direction
  queries have the same admission boundary; none invokes Item code outside it.
- The adapter resolves a fresh access scope for every operation. The scope
  cannot cross a tick, leave the server thread, be stored in a capability
  handle, or leak to callbacks; every native result copy finishes before close.
  No method returns a mutable bank or controller reference. Formation/removal
  entry points consume only privately prepared candidates and revalidate their
  captured live identity, not arbitrary externally supplied NBT/replacements.

A capability operation must require both actual hatch and controller chunks
**FULL** (`getChunkNow`), identical live BEs, same Level/UUID/generation, exact
kind/key, active assignment, supported complete roots, `FORMED`, current epochs,
and no preparation/application/recovery/reentrant lock. Acquisition is not enough;
every stale handler operation rechecks. All six sides and unsided query expose
the same bounded bank; there is no side bypass. Inputs automate insert/fill
only; outputs extract/drain only. Menu extraction from inputs belongs to a
separately validated server menu boundary, not the automation facade.

Do not wrap the old generic handlers and assume this is sufficient: their native
copies/getters can run outside a controller-local lock. Lock **before** external
input/native copy/limit checks and retain it through result getters/copies. This
includes `isItemValid`, `isFluidValid`, read getters and SIMULATE. Reentrant
`load`, binding replacement, destruction and mutation must refuse. Revalidate
the same loaded witnesses after callbacks and before commit; lifecycle changes
discard the candidate intact. This prevents supported nested host operations,
not arbitrary mod callbacks affecting unrelated world state or taking unbounded
execution time.

The admitted transfer API accepts bounded slices, not arbitrary large offers.
The facade must freeze its larger-request adaptation: validate original quantity
before any byte narrowing, preflight metadata, construct at most slot-capacity/
16,000-mB slices under the lock, and return exact caller remainder/accepted amount.
Never serialize an over-limit original Count to a plausible wrapped bank value.
Both Fluid drain overloads must preserve exact tagged-fluid equality. Invalid
negative/overflow/unknown/capability-bearing requests refuse without mutation.

Power has 0–10,000 FE in its hatch root, no Item/Fluid revision or bank. External
receive is INPUT only, formation/busy/epoch gated; SIMULATE leaves FE unchanged.
Do not import rolling's 20,000 capacity or 1,000 receive limit as new family
numbers. Internal FE draw, plug Item removal/carrier policy and any per-call
limit need explicit adapter freeze; physical FE is outside the batch atomicity
claim. Controller removal does not migrate power into controller resources.

## 4. Formation / binding / removal boundaries

Reuse kernel immutable state, patterns, dirty queues and lifecycle concepts, not
their weaker defaults unchanged. Actual `ControllerBindingView.acceptsPartAccess`
permits `WAITING_UNLOADED`; family access must require FORMED. Kernel
`MAX_PARTS` is 4,095; family active hatches are at most **64**, independently from
at most **64 retained Item/Fluid banks**. A family LoadedPartBindingAccess must
recognize only the intended five kinds/roles. Validate complete pattern,
motor/coil tags, loaded instances, conflicts and full aggregate byte/bank limits
before any generation/binding publication. Reuse matching position/kind banks;
append genuinely new empty keys deterministically without reordering retained
banks or silently deleting inactive banks to make room.

Generic LoadedPartBindingGateway mutates part BEs and may invoke notification
callbacks before returning. Its rollback is not a whole controller/bank commit.
The family operation needs one lock, preflighted full checkpoint, captured part
witnesses, revalidation and failure disposition; all capability access stays
paused until publication. The minimal candidate refuses an own-UUID stale
generation during access/removal; it does not automatically repair it. A later
explicit bounded revalidation/repair path needs its own captured-generation
rule and evidence before enabling access. Foreign UUID, position/Level,
unsupported root and role mismatches never gain a bank by guessing.

Removal must identify/validate an assigned bank **before** automatic unbinding,
prepare the cleared aggregate, invalidate handles and process one bounded drop
vector at controller position. Fluid removal changes assignment only, leaving
bank and resource revision intact if resources did not change. Simple unbinding
does not invoke either removal operation. Controller removal clears/drains its
own full vector once, never iterates remote resources.

ADR-019 explicitly scopes Item-port drops to formed assigned ports and retains
controller resources on mismatch/unload. The C16 leaf says assigned bank while
inactive banks remain. Do not invent a last-owner/ownership-history migration:
an already inactive **unassigned** bank remains at its controller, not copied
into an unbound hatch or inferred from a global position search. If the intended
product behavior is instead to clear formerly assigned inactive banks when an
unbound hatch later breaks, an additional reviewed association field/policy is
needed. That interpretation is not selected by this memo.

Minimal proposed technical choice: no historical-owner field. Active assignments
plus the hatch's current `binding`/`bank_channel` describe only the current
association. Unformation clears active assignments and exact loaded bindings
but keeps banks; unavailable hatch roots cannot be mutated remotely and their
old bindings remain unavailable/stale and refuse until safely reconciled.
Subsequent ordinary removal of an unbound hatch does not clear an inactive
controller bank. Assigned removal verifies the exact pre-unbind UUID,
generation, kind/key and live assignment. A stale or unsupported bound hatch
refuses removal rather than falling back to unbound behavior. This preserves
the established assigned/inactive distinction; it is a proposed implementation
choice to review, not a new owner decision or physical API admission.

Freeze ordinary drop-spawn false/exception handling and avoid retries which
duplicate already spawned Items. Forge-aware block hooks and LivingDestroyBlock
event must agree with player/explosion refusal. Entity Item persistence is not
the same controller snapshot; do not promise atomic Item drops across crashes
or arbitrary callback/plugin effects. Administrative raw block/NBT replacement
and world editors are not normal supported removal APIs.

## 5. Persistence to freeze before the first writer

Resource schema remains exact and admitted; **no change** to its fields/types.
The following new adapter fields are proposals, not accepted encodings:

- `arce_classic_hatch` schema 1: `schema_version` Int, exact `kind` String,
  optional bounded exact `binding` Compound, `bank_channel` String only for a
  bound Item/Fluid kind, and required `energy` Int only for power. No Item/Fluid
  root/payload in hatch NBT, loot or block Item. Validate kind against actual block
  and key against actual position. Whole-root absence on persisted load requires
  repair/save refusal; it is not explicit new unbound construction.
- `arce_classic_machine` schema 1: controller Level/position anchoring, UUID,
  generation, selected rotation/no-mirror, lifecycle, unique active assignment
  list (power included), batch ordinal and process/refusal/native-plan frame.
  Freeze exact field names/native types, empty idle form and all process forms
  with the first shared process consumer. One immutable checkpoint publication
  must serialize machine state, resources and existing kernel journal together.
  Do not persist a second `arce_multiblock_controller` authority or move an owner
  merely by loading its NBT at a different position.
- Keep the existing journal schema and 64 balances. Proposed participating Item
  channel `<bank-channel>.s0`…`.s3` distinguishes four slots; Fluid can use the
  bank channel. Concrete alternatives/full native before/after payloads must
  accompany that selected <=64-entry plan in machine root. Do not snapshot all
  256 possible Item slots, resolve an old plan from current tags, inflate journal
  limits or recreate default untagged Items. Chance output choice is a later leaf.

Exact candidate idle-host field spelling and native types for that review:

| Field | Candidate encoding / validation |
|---|---|
| `schema_version` | TAG_Int, exactly 1. |
| `machine_uuid` | TAG_IntArray of exactly four native UUID words, matching resources. |
| `owner_level` | Canonical ResourceLocation String <=128 chars; equals the live Level key. |
| `owner_position` | Compound with exactly `x/y/z` TAG_Int; equals this controller BE. |
| `generation` | Nonnegative TAG_Long; FORMED requires positive. |
| `rotation` | TAG_Int, one of 0/90/180/270; no mirror field/interpretation. |
| `formation_state` | Exact existing kernel lifecycle enum String. |
| `assignments` | Compound List <=64 unique positions; empty is TAG_End; each entry has `x/y/z` Int, exact five-kind block ResourceLocation `kind` String, and `bank_channel` String only for Item/Fluid. No channel for power. |
| `batch_ordinal` | Nonnegative TAG_Long; changing at exhaustion refuses intact. |
| `process` | For the adapter-only idle fixture, exactly Compound `{phase: "idle"}`. No recipe, progress or selected native plan inferred from its absence. |

Map the five full block-kind IDs explicitly to their four bank kinds and pattern
roles: `item_input`, `item_output`, `fluid_input`, `fluid_output`, and
`energy_input` for power. The pattern role is not the position-derived bank
channel or the participating-slot journal channel. Require all assignment kinds,
positions/channels, live pattern roles and owner identities to agree. A bank's
existence is not an active assignment. Resource owner UUID and machine UUID
must match; Level/position anchoring prevents moving an owner by loading NBT.
Supported assignments follow the kernel retained-state invariant: FORMED,
WAITING_UNLOADED and BINDING_CONFLICT may retain them; other supported states
have none. That does not interpret a rejected raw root as a supported state.

`arce_classic_hatch` has exactly `schema_version` Int=1 and the full block ID
`kind` String for an explicitly new unbound Item/Fluid hatch. A bound Item/Fluid
root additionally has `binding` Compound and canonical `bank_channel` String.
Power additionally requires `energy` Int 0..10,000 in bound/unbound roots;
binding is optional and power never has `bank_channel`. The embedded binding
retains the kernel inner shape: `schema_version` Int=1, `controller_level`
String, `controller` Compound of x/y/z Int, `machine_instance_id` canonical UUID
String and positive `generation` Long. Preflight the **whole hatch 4,096-byte
root** before passing the inner binding to its old 65,536-byte codec. Unknown
fields, wrong native types, kind/position/channel mismatches and unsupported
bindings refuse; they do not become a new empty object. New construction is an
explicit constructor event, distinct from a persisted root missing on load.

This idle checkpoint is a testable **candidate subset**, not permission to ship
an incomplete family process schema. Non-idle state, an existing nonempty
kernel journal or native plan cannot be normalized to idle. Retain/pause it
until a separately reviewed complete active frame exists. Freeze the complete
active field/type variants before C16b/c process writers are delegated; do not
silently publish incompatible active schema-1 interpretations or claim pending
transaction/native restart coverage from an idle test host.

| Limit | Fixed versus proposed |
|---|---|
| Bank kinds/slots/amounts | Four kinds; four Item slots <=min(64,native max), one Fluid <=16,000 mB; registered IDs <=128 chars. Fixed. |
| Resources | <=64 retained banks; 32,768 named-root encoded bytes; depth16 /8,192 nodes. Fixed qualified model. |
| Machine/hatch/journal bytes | 65,536 /4,096 /65,536 respectively. Fixed; preflight independently and together before PREPARED, no larger journal. |
| Structural adapter caps | Initial proposal: hatch depth12/nodes256; machine depth24/nodes16,384. Must be reviewed with actual framing/boundary fixtures; not yet frozen or existing kernel budgets. |
| Pattern/assignment | Kernel axes1..16/4,096 cells; family <=64 hatch assignments, four rotations/no mirror. Fixed. |
| Scheduling | Reuse bounded dirty/footprint design. Proposed family-total budget <=32 controllers /8,192 cells per server tick, <=16,384 tracked controllers /1,000,000 cell references, not seven independent copies. Separate recipe lookup limits remain ADR64's32/controller/256/Level and require their own fair queue. |

Preflight depth/nodes/encoded bytes before recursive native copy/equality/decode.
Retain every unsupported/corrupt root verbatim and disable tick/access/removal.
MISSING and UNBOUNDED resource decode statuses require guarded save refusal;
keep the original parent/root identity, not a fresh UUID/empty fallback. Register
the new machine/hatch/resource consumer with the shared Level-lifetime sticky
guard; list-only chunk scanning is insufficient. No production/native reset or
time-based retry is introduced. Supported counters/epochs fail closed at
exhaustion; reload/BE replacement must not revive an old handle.

## 6. Required tests and evidence, not executed here

1. Codec boundary tests: all exact wire types/fields, absent-versus-explicit-new,
   future/corrupt/mismatched UUID/kind/position, 64/65 banks/assignments,
   32,768/4,096/65,536-byte and depth/node edges, maximal IDs/channels and native
   Item count narrowing. Same supported quantities/tags before and after encode.
2. Capability tests on every side/unsided query: all modes/slots/drain overloads,
   larger requests, tag inequality, full output, no-op/simulation/revision limits.
   Original input objects unchanged; supported getter results detached.
3. Native callback probes for copy/serialization/Item limits/validation getters:
   nested drain/fill/FE, load, binding replacement/removal/unload. Nested host
   operations refuse; simulation/query does not commit or advance revision.
4. Formation tests: all motor tiers/coils, rotations/no mirror, same versus
   different position/kind, retained empty/inactive banks, conflict, stale owner,
   reused/removed/replaced BE, rollback exceptions and candidate aggregate overflow.
5. Removal tests: assigned Item bank drops once at controller, Fluid retained and
   exact-key rebuild reattaches once, unformation alone leaves resources,
   unavailable/unsupported/pending/reentrant owner vetoes all supported hooks.
   Test spawn failure policy rather than assume generic Containers calls atomic.
6. Cross-chunk both unload/reload orders: hatch first and controller first;
   no chunk loads/force tickets from queries/removal, all family access pauses
   when any needed part is unloaded, retained roots unchanged and old handles
   remain inert after new binding/BE instances.
7. Final packaged real-controller S1: at least two clean same-world restarts with
   Item+Fluid tags/resources/controller process/journal identity on actual disk;
   unknown/future and oversized whole-chunk oracle/unchanged marker separately.
   Forced-stop Item/Fluid batch cut verification belongs to the process leaf;
   FE/drop entities retain their explicit exclusions.

## 7. Outcome / open boundaries

Feasible as a bounded shared adapter without a LibVulpes replica. The six freeze
groups above—public operation authority, controller/hatch exact schema,
formation/skew/removal ordering, larger-offer slicing/FE, reentrancy lifecycle,
and actual guarded writer—are technical implementation dependencies, not already
qualified model capabilities. No new owner question is required for the
minimal **assigned-bank** interpretation; any broader inactive-history removal
interpretation must be confirmed instead of silently encoded.

Actionable write-task sequence after independent technical acceptance:

1. Author only new `machine/classic/hatch` model/codec/access/lifecycle modules
   and their tests; keep the qualified eight bank production classes unchanged.
   Start with pure admission/checkpoint values and an injected loaded-BE witness
   gateway, then Forge BE/Block adapters. Every mutable state is tied to a live
   BE or server lifecycle, not an unrestricted static map.
2. Test the complete five-kind family through a test-only host. Root integrator
   alone supplies five DeferredRegister/BlockItem entries, generated-art/loot/
   translation hooks and event registration from reviewed proposals. Record all
   new art provenance before generation. Registration does not authorize recipes,
   menus, JEI or a player-facing fake controller.
3. Independently review actual diff and run codecs, callback/lifecycle probes
   and registered GameTests. Only then freeze the shared exact APIs/roots for the
   first C16b lathe consumer. The active process/native-plan variant must be
   reviewed before that writer, not completed opportunistically in the facade.
4. Use that real consumer for the packaged cross-chunk restart/removal fixture;
   retain the all-five-kind fixture and a later fluid-process consumer. C16a-03
   remains partial until its full contract/native obligations are evidenced.

Only this memo and input manifest were written in fresh Temp. Actual checks:
read-only source/docs/git inspection and Python ZIP/hash/static string arithmetic;
all 14 bank Java files match frozen r2. No production edit, upstream import,
Java/Gradle/native run, new test implementation or acceptance occurred. Earlier
native audit packets remain unchanged. Full v1.8 Required Gates are not satisfied.
The first manifest probe failed its author's guessed maximum participating
channel length (51); direct calculation found 50. That failed assertion and
the corrected static result are recorded, not treated as a model/runtime failure.

## 8. Factual baseline index

All path hashes and exact sizes are in the companion manifest. Java paths below
are relative to `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`.

- `docs/work/v1.8.0-c16a-hatches/LEAF-CONTRACT-DRAFT.md`:48-85 fixes FULL,
  exact owner/assignment, FORMED, facade access and assigned-bank removal;
  88-128 fixes independent roots and full native prepared plans.
- `docs/work/v1.8.0-c16a-hatch-core/API-ACCEPTANCE.md`:25-38 qualifies native
  callbacks/commit authority; `VERIFICATION.md`:21-29 records the completed
  independent qualification rather than the earlier pending header alone.
- `machine/classic/resource/ClassicResourcesCodec.java`:17-27,52-83 fixes
  schema, byte/depth/node and native root/list types;
  `ClassicNativePayload.java`:24-38,59-70,113-120 checks count before narrowing,
  rejects ForgeCaps and canonicalizes bounded IDs;
  `ClassicResources.java`:50-72 bounds replacements and revision exhaustion.
- `machine/multiblock/lifecycle/ControllerBindingView.java`:20-22 permits
  WAITING_UNLOADED generically; `MultiblockControllerState.java`:23,105-109
  gives the broader part limit/retained-state invariant. These are not the new
  family's strict access/64-hatch admission by themselves.
- `machine/multiblock/lifecycle/forge/LoadedPartBindingGateway.java`:67-115
  mutates targets then returns;119-137 unbinds loaded targets only;
  `ServerLevelPartBindingAccess.java`:25-30 uses hasChunkAt. A family FULL/locked
  checkpoint bridge must be separately authored/reviewed.
- `machine/port/forge/ProcessItemPortHandler.java`:54-58,92-101 and
  `ProcessFluidPortHandler.java`:56-85 perform native copies/validation around
  delegated resource access. `machine/precision/PrecisionAssemblerPortBlockEntity.java`:
  376-393 resolves identity/generation and 396-405 constructs the old one-slot
  views. Reusing names does not verify the complete new callback boundary.
- `docs/decisions/ADR-019-PRECISION-RESOURCE-OWNERSHIP.md`, Decision and Removal
  sections, distinguishes assigned formed-port drops, mismatch retention and
  old migration repair from new family construction.
- ADR-064:305-314 and `material/MaterialCatalog.java`:101-104 provide the
  proposed first-consumer reason: the lathe's accepted size/rate and existing
  copper/iron rods, not an imported or already-authored classic pattern.
Next current-version action: independently review/freeze this adapter boundary,
implement it in an exclusive new scope, and qualify the first C16b consumer.
