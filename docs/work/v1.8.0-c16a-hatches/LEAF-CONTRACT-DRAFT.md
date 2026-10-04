# C16a-03 family resource and hatch contract draft

Date: 2026-10-03. Status: proposed; no downstream implementation admission.
Author: root integrator. Scope: ADR-064 sections 1/11 and
[fluid-retention amendment](PROPOSED-CHANGE-001.md).

## Scope and existing boundaries

This is the shared boundary needed by the seven C16b/c controllers, not a
LibVulpes compatibility layer. Existing rolling/precision/electrolyzer ports,
process definitions, shared journals, pattern schemas and public packets do
not change. New code belongs to `machine/classic`; the root integrator owns
registration, common configuration, providers, protocol and status integration.
Machine-specific recipes, patterns and menus require their own reviewed leaves.

## Bank identity and resources

- Five stable block/item IDs are `item_input_hatch`, `item_output_hatch`,
  `fluid_input_hatch`, `fluid_output_hatch`, and `power_input_plug` in the
  `advancedrocketrycommunity` namespace. Their blocks do not move by piston.
- A controller's UUID identifies one machine instance. A resource bank is keyed
  by the hatch's absolute x/y/z in the controller's Level and its exact input
  or output Item/Fluid kind. The key is independent of binding generation,
  rotation and physical BE lifetime. Coordinates are internal server data,
  never accepted as client-supplied bank assignments. A controller is not a
  movable resource owner without a separate adapter contract.
- The stable textual bank channel is `<kind>.<x>.<y>.<z>`, where kind is one of
  `item_input`, `item_output`, `fluid_input`, `fluid_output`; signed decimal
  coordinates have canonical integer spelling. It must fit the existing
  64-character process-channel bound. Duplicate keys or noncanonical aliases
  refuse the root rather than merge inventories. Power plugs have no bank.
- At most 64 retained Item/Fluid banks, including inactive banks, live in
  `arce_classic_resources`. An Item bank contains exactly four slots, each at
  most `min(64, Item max stack size)`; a Fluid bank contains exactly one Fluid
  stack at most 16,000 mB. Empty banks have an explicit kind and key.
- Store the full bounded native Item/Fluid payload, not only its registry ID.
  Reject unregistered IDs, invalid counts, unsupported nested capability
  payloads, or a payload that cannot decode and re-encode without changing it.
  Ordinary supported Item metadata is not silently stripped; tagged Item and
  Fluid equality governs merges. Inputs and outputs never exchange roles.
- One nonnegative long resource revision covers all banks; every successful
  external or internal mutation advances it once. Simulation does not mutate
  or advance it. Revision exhaustion refuses mutation intact. Prepare and
  encode the aggregate replacement before publishing any changed bank.

## Exact facade access

A hatch is a facade, never a persisted Item/Fluid resource carrier. Resolve its
owner through an already-FULL loaded chunk, then require this exact loaded hatch
BE, controller BE, machine UUID, generation, kind, bank key, active assignment,
supported roots and `FORMED` state. The generic kernel's `WAITING_UNLOADED`
access permission is not enough for this family. No missing chunk is loaded.
An unsupported binding never becomes an empty/unbound inventory automatically.

Every capability handle has an epoch; invalidation, binding replacement,
unload/removal and controller load invalidate old handles. Every operation
rechecks owner access, not just capability acquisition. All sides including the
unsided query expose the same bounded bank. Item/Fluid input automation inserts
only, output automation extracts only. Controller menus can remove inputs or
take outputs through an independently validated server menu. Neither can
mutate resources during preparation/application/recovery or a reentrant call.
Power plugs own 0–10,000 FE but also require the exact formed binding for
external access. Power transfer remains outside Item/Fluid crash atomicity.

## Formation and removal

Validate a complete kernel pattern, roles, all loaded parts, conflicts and
aggregate bank limits before publishing a new generation or assignment. All
four motor tiers and coils are resolved from their accepted tags. Allocate an
empty bank only for a genuinely new key; matching existing keys reuse the same
bank. Unformation, binding-generation changes and unload retain resources.

Item-hatch removal clears its assigned bank once and drops those Items at the
controller; unformation alone does not clear it. Fluid-hatch removal retains its
bank; rebuilding at the same position with the same kind can reattach it after
valid formation. Rotation alone does not change a bank's identity: a legal new
formation reuses it only when the absolute position and exact kind still match.
A hatch at a different position or of a different kind does not inherit it.
Inactive banks continue to count toward limits. Do not automatically discard
them to make a new formation fit. Controller removal follows ADR-064's Item
drops/Fluid drain. Bound unavailable/unsupported owners and pending transactions
refuse ordinary player/explosion/Forge-aware destruction; never force loading.
This does not promise durable atomicity between arbitrary Item entities and a
chunk save, or across independently saved FE/automation stores.

## Persistence shape and transaction adapter

All new roots use independent `schema_version: 1`, with strict field/type,
collection, depth and byte preflight before recursive copies. No v1.2 root or
schema is reinterpreted. Limits remain 65,536 bytes for `arce_classic_machine`,
32,768 for `arce_classic_resources`, and 4,096 for `arce_classic_hatch`.

- Resources: machine UUID, revision and a unique ordered bank list containing
  canonical key/kind/position and exactly four native Item slots or one native
  Fluid payload. UUID must match the owner root. No second inventory in hatch
  NBT, block drops, menu sync or a global collection.
- Machine: UUID, nonnegative generation/batch ordinal, selected transform,
  lifecycle state and at most 64 active hatch assignments, plus process ID,
  JSON signature, progress/refusal and bounded chosen chance outputs. Any
  prepared family's exact native payload plan resides here in the same
  controller snapshot as resources and the unchanged kernel journal.
- Hatch: optional exact `MultiblockPartBinding` identity and derived bank key;
  power kind additionally stores FE. Unbound newly constructed Item/Fluid
  hatches carry no bank and no resources. Deserialized missing required roots
  require repair; they are not treated as new empty objects.

The kernel snapshot has at most 64 balances and cannot represent native
metadata. A family adapter selects only a batch's participating slots/tanks
and concrete alternatives, staying within that bound; it does not snapshot
all 256 possible Item slots or inflate `MAX_ENTRIES`. Retain the corresponding
full native before/after payloads and selection in the family machine root so
recovery cannot recreate default Items and lose metadata. Cross-check both
representations, revision and transaction ID. Preflight both complete root
sizes and the existing 65,536-byte kernel journal before `PREPARED`; excess
pauses intact. Retained `PREPARED` plans are never rebuilt from current tags,
patterns, banks or random choices. Chance selection/retry follows ADR-064
section 9.4 and still needs a machine-process leaf review.

Unsupported, corrupt, oversized or conflicting roots stay verbatim and disable
tick/capability/removal. Oversized serialized roots refuse ordinary save with
the original persisted chunk intact, using the existing guarded-save pattern.
New IDs have no existing v1.x placeholder mappings; absent/new construction
does not justify treating an unsupported persisted payload as empty.

## Intended minimal interfaces and evidence

The implementation should expose immutable bank-key/kind/resource snapshots,
strict codec results and one loaded-owner facade boundary. Controllers own
bank replacement, assignment, revision and transaction locking. Machine
adapters supply their pattern roles and selected completion plan. Do not add a
generic remote inventory framework or per-hatch resource persistence.

Before C16b/c write tasks start, review the actual domain interfaces/codecs and
run their contract tests; this draft alone does not freeze Java APIs. Required
evidence includes aggregate limits, metadata-preserving native plans, stale
epochs/generations, bank conflicts, all-tier formation, paused transaction
mutation/removal, both cross-chunk unload orders, rebuild identity and at least
two packaged restarts. Menus/JEI, chance-process recovery and per-machine
patterns remain separate admission checks, not completed by a bank test.
