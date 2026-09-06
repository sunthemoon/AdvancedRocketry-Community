# ADR-016 — Process machine and multiblock kernel

```yaml
status: PROPOSED
date: 2026-09-06
requested_decider: sunthemoon
owner: sunthemoon
target_version: v1.2.0
depends_on:
  - v1.1.0 PASSED
supersedes: ""
```

## Context

The accepted v0.2.0 Electrolyzer is a reliable single-block vertical slice, but its
recipe, four-slot storage, water tank, process state, capability views, persistence and
menu are concrete to one machine. Other current devices independently repeat Forge
capability creation/invalidation and persistence lifecycle code. Turning the existing
502-line BlockEntity into a common superclass would preserve those fixed assumptions
rather than establish a reusable process model.

The fixed upstream 1.12 audit identifies ten process-style multiblock machines. Every
one depends on LibVulpes; their structures range from 1×2×3 to 5×5×5 and audited recipe
definitions range from zero to five Item inputs and zero to two Fluid inputs. Copying
the old hierarchy is outside the license/architecture boundary and would reintroduce
implicit structure, port and lifecycle behavior.

The v1.2.0 prerequisite is not currently satisfied. This ADR is therefore a contract
proposal only. Acceptance must not be inferred from the existence of this file and
would not by itself approve production work while the version prerequisite remains
open.

## Decision

If accepted after the prerequisite is satisfied, v1.2.0 will use a small internal
kernel with four independent contracts: process, port, multiblock pattern and
controller/part lifecycle. Minecraft BlockEntities remain adapters rather than domain
base classes. These contracts remain internal until the separate v1.3.0 public API
review.

### Stable identities and schemas

The following runtime identities become stable only when this ADR is accepted and the
corresponding implementation is merged:

| Purpose | Candidate identity/schema |
|---|---|
| Existing Electrolyzer block, BE and menu | `advancedrocketrycommunity:electrolyzer` unchanged |
| Existing Electrolyzer recipe type/serializer | `advancedrocketrycommunity:electrolyzing` unchanged |
| Existing Electrolyzer NBT root | `arce_machine`, schema 1 decoder retained |
| Rolling Machine block, BE and menu | `advancedrocketrycommunity:rolling_machine` |
| Rolling recipe type/serializer | `advancedrocketrycommunity:rolling` |
| Precision Assembler block, BE and menu | `advancedrocketrycommunity:precision_assembler` |
| Precision recipe type/serializer | `advancedrocketrycommunity:precision_assembling` |
| Process state NBT | `arce_process`, independent schema 1 |
| Recoverable commit NBT | `arce_process_journal`, independent schema 1 |
| Controller state NBT | `arce_multiblock`, independent schema 1 |
| Part binding NBT | `arce_part_binding`, independent schema 1 |
| Datapack patterns | `data/<namespace>/machine_patterns/<path>.json`, schema 1 |

The Java package layout and interfaces are not public API. v1.3.0 may expose adapters
without changing these persisted identities. Unknown future schemas are preserved and
blocked; no decoder silently rewrites them.

### Process definition and state

A `ProcessDefinition` is immutable data containing its stable ID, schema, duration,
energy per tick and bounded Item/Fluid requirement/product lists. It contains no world,
BlockEntity, callback or client-computed result. Candidate hard limits are:

```text
item inputs <= 8            item outputs <= 8
fluid inputs <= 8           fluid outputs <= 8
ingredient variants <= 32   resource ID chars <= 128
definition bytes <= 65536   duration ticks = 1..72000
energy/tick = 1..16384       checked total energy <= 2147483647
```

The states are `IDLE`, `RUNNING`, `WAITING_INPUT`, `WAITING_OUTPUT`,
`WAITING_ENERGY`, `REDSTONE_DISABLED`, `INVALID_RECIPE`, `UNSUPPORTED_DATA` and
`RECOVERY_REQUIRED`. Stable failure codes use lowercase resource-style names such as
`missing_item_input`, `output_blocked`, `chunk_not_loaded` and `stale_transaction`;
localized text is neither persisted nor accepted from the client.

Simulation reads immutable, revisioned port snapshots and returns a plan without
mutation. Commit compares the captured revision/fingerprint before changing any
resource. A mismatch rejects the complete plan and causes a new server-side simulation.

### Recoverable atomic completion

Final Item/Fluid consumption and production use a journal with a transaction UUID,
machine UUID, recipe ID, port revision and complete bounded before/after snapshots:

1. persist `PREPARED`;
2. persist `APPLYING` before resource mutation;
3. apply the complete after snapshot on the server thread;
4. persist `APPLIED`, then clear the journal.

Recovery compares actual resources with the bounded snapshots. Matching before applies
after once; matching after only finalizes; matching neither enters `RECOVERY_REQUIRED`
without discarding data. Replaying a transaction UUID cannot produce a second batch.
Energy consumed during ordinary progress and the progress counter remain independently
persisted; the final resource batch is never split across ad-hoc slot/tank operations.

### Port policy

Ports have kind `ITEM`, `FLUID` or `ENERGY`, mode `INPUT`, `OUTPUT` or explicitly
declared `BIDIRECTIONAL`, a channel, a bounded slot/tank range and a filter. Sides are
controller-local `FRONT`, `BACK`, `LEFT`, `RIGHT`, `TOP`, `BOTTOM` or `UNSIDED` and
are transformed from the controller facing by the Forge adapter.

Capability views are created once on `reviveCaps`, cached for queries and all
invalidated on `invalidateCaps`. Querying a capability cannot allocate a pattern scan,
load a chunk or bypass the active process lock. External insert/extract operations
advance the same resource revision used by process plans.

### Pattern and diagnostics

A pattern contains a stable ID, schema, dimensions, one controller anchor, a complete
cell set and allowed transforms. Matchers are closed to exact block ID, block tag, air,
controller, typed port and an optional wrapper around one bounded matcher. Arbitrary
predicates, scripts, class names and recursive matchers are rejected.

```text
axis length <= 16
total cells <= 4096
ports <= 64
returned diagnostics <= 32
rotations = 0/90/180/270 degrees around local Y
mirror = optional reflection across controller-local X
```

Local positive axes are right, up and back relative to the controller. Transform and
inverse-transform must preserve the anchor and every cell. Before reading a world block,
the validator checks that its chunk is loaded. Any unavailable cell returns
`WAITING_UNLOADED`; it does not force a chunk load, bind a partial structure or report a
false disassembly.

Validation returns `FORMED`, `MISMATCH`, `WAITING_UNLOADED`, `LIMIT_EXCEEDED` or
`INVALID_DEFINITION` plus bounded diagnostics with reason, local/world position,
expected matcher summary and actual block ID only when already loaded.

### Controller and part lifecycle

Controller state owns a machine instance UUID, monotonically advanced generation,
selected transform, formation state, bounded part positions and independent schema.
Part binding stores `ResourceKey<Level>`, controller `BlockPos`, instance UUID,
generation and schema. Numeric dimension IDs and global static world maps are forbidden.

Formation validates the complete loaded pattern before a bounded bind transaction.
Block changes enqueue a deduplicated dirty controller; a configured per-tick budget
drains the queue. Idle controllers do not rescan every tick. Unload changes state to
unknown/waiting rather than disassembled. Confirmed mismatch, controller removal or
dimension removal unbinds loaded parts without clearing their inventories.

### Migration, menu and optional integration

The v0.2–v1.1 Electrolyzer remains a single block with its existing player behavior,
IDs, side rules and schema 1 decoder. Migration converts state without selecting or
executing a recipe, consuming energy or producing output and is idempotent on a second
load. Its accepted 50-cycle material ledger remains a regression oracle.

Menus synchronize only bounded summaries: progress, duration, energy, formation state,
failure code and port counts. Clients cannot submit progress, results, recipe choice or
formation state. Menu requests validate player, distance, permission, controller
generation and loaded chunks.

JEI consumes an immutable display view from client-only compat code. Its absence causes
no common-class linkage. The view is not a v1.3 public extension contract.

### Representative machines and order

1. Rolling Machine proves ordinary 2×3×5 multiblock formation and mixed Item/Fluid/
   Energy ports without depending on Electrolyzer internals.
2. Electrolyzer proves behavior- and save-compatible migration into the kernel.
3. Precision Assembler proves a 3×3×4 pattern and two-to-five Item input planning.

Only abilities used by at least two representatives enter the shared kernel. Special
Chemical Reactor NBT/enchantment behavior, Crystallizer gravity logic and the 5×5×5 Arc
Furnace remain adapters or later machines rather than core abstractions.

## Alternatives

### Generalize `ElectrolyzerBlockEntity` into a base class

Small initial diff, but fixes four slots, one tank, one recipe shape and one menu into
the inheritance contract. Rejected.

### Recreate LibVulpes machine APIs

Would make legacy structures superficially easy to port, but expands license review,
restores implicit lifecycle/network behavior and violates the scoped-infrastructure
rule. Rejected.

### Consume inputs at recipe start without a journal

Simpler runtime state, but interruption between input removal and output insertion can
lose resources and cannot distinguish replay from completion. Rejected.

### Treat unloaded cells as mismatches

Simplifies the validator but disassembles cross-chunk machines during normal unload and
can lose port/controller relationships. Rejected.

### Make every machine poll its structure each tick

Avoids dirty-event wiring but violates the idle and world-traversal budgets. Rejected.

## Consequences

### Positive

- Three materially different machines test the abstractions before public API work.
- Final resource changes have explicit crash/replay semantics.
- Pattern validation is deterministic, bounded and chunk-load safe.
- Electrolyzer worlds retain their stable identities and fail closed on future data.
- Capability behavior is explicit and independently testable by side.

### Negative

- Journal snapshots add NBT and recovery complexity to a batch completion.
- Parts need generation-aware bindings and event-driven invalidation.
- Pattern JSON and diagnostics require additional DataGen/client presentation work.
- Existing Electrolyzer code must temporarily retain a compatibility decoder and adapter.

## Validation required before acceptance

- [x] Fixed-commit upstream machine and recipe audit with SHA-256 verification.
- [x] Current Electrolyzer schema, IDs, side policy and tests inventoried.
- [x] Newly authored process, journal, pattern, binding and port samples parse and satisfy
  their documented structural invariants.
- [ ] `v1.1.0` is marked `PASSED` by the maintainer, or a separate explicit sequencing
  exception is accepted.
- [ ] Maintainer confirms representative machines, candidate IDs and numeric limits.
- [ ] Independent review confirms no v1.3 public API or LibVulpes implementation leaked
  into the contract.

## Implementation validation after acceptance

- Pure Java simulate/commit/recovery and overflow tests.
- Pattern rotation/mirror/limit/unloaded tests and GameTests.
- Three-machine resource conservation, capability, unload and restart matrix.
- Existing Electrolyzer GameTests and schema 1 fixture upgrade.
- DataGen determinism and JEI absent/present startup.
- Short 100-machine checks followed by the deferred reference workload before release.
- G0-G9, independent review and human release approval.

## Revisit when

Revisit before adding dynamic/vertical rotations, recursive matchers, movable controllers,
cross-dimension structures, shared networks, recipe scripting, third-party public APIs or
new resource kinds. Those changes require migration and belong to a later scoped ADR.
