# ADR-027 — Item fuels and preserved Fuel Loader batches

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
api_version: "1.5"
acceptance_basis: maintainer authorization to follow recommended current-version implementation solutions; independent contract review incorporated
```

## Context and recommended decision

The loader accepts only one built-in 500-unit cell and later creates one empty
canister. Schema 1 stores a small item-state enum, not an item identity. Registered
fuels therefore require real item preservation and a migrated batch/remainder
representation, not just an unused API registry. This is COMP-02 under ADR-020/021;
it neither changes the original-feature acceptance schedule nor approves a release.

## Public contract

API 1.5 preserves all sixteen exports and adds three types in `api.rocket`:

- `public record RocketFuelDefinition(long units, Optional<ResourceLocation> remainderItem)`:
  immutable values, 1..2,048,000 existing abstract fuel units per whole item;
  non-null Optional, empty means no remainder, otherwise exactly one item with
  default native metadata. IDs are at most 255 characters and cannot be air.
- `RocketFuelRegistrar`, a public functional interface with `void register(ResourceLocation definitionId,
  Set<ResourceLocation> items, RocketFuelDefinition definition)`.
- `RegisterRocketFuelsEvent`: final MOD-bus event with a public constructor taking
  a non-null registrar, forwarding that signature through `void register(...)`.
  Record accessors are `units()` and `remainderItem()`. Null arguments fail with
  `NullPointerException`, invalid values/claims with `IllegalArgumentException`,
  and expired, reentrant or wrong-thread handles with `IllegalStateException`.

Use the existing queued-common-setup, owner/thread-bound, synchronous event
pattern on both physical sides. Handles expire on owner change/close. Definition
IDs use the listener's namespace; input/remainder item IDs may use other namespaces
but must be known non-air items. The built-in rocket fuel cell is reserved and
keeps its 500 units/empty-canister remainder. Duplicates/overlapping input claims,
invalid items, reentrant/late/off-thread calls and limits fail atomically.
Limits: 256 external definitions, 1..64 inputs per definition, 1,024 inputs total;
the host built-in entry is outside those external quotas. No callbacks are retained.

Definitions apply to the whole item, regardless of its metadata; they never drain
capabilities or compute fuel from NBT. The host preserves metadata while queued,
then deliberately consumes that entire one item. The fixed remainder is not a
copy of arbitrary input capabilities. A remainder may itself be a registered fuel;
output state prevents automatic re-burning. Same-JVM mod code is not sandboxed.

## Runtime and persistence

Keep one slot and the existing 6-block, owner-bound, loaded-entity search,
eligible flight states, tank capacity and **25 units/tick** transfer rate. No
client-authoritative mutation, packet or chunk-loading change is introduced.
Survival/player and automation consume one item; creative insertion retains the
existing instabuild convention. Input simulation never mutates either side.

On first eligible transfer, capture the definition ID, original units and actual
remainder (`new ItemStack(resolvedItem, 1)`, not `getDefaultInstance()`) into a
batch, then remove the input. Validate the complete remainder and native round-trip
before removing anything; preparation failure retains the input. Remaining batch units
and remainder no longer depend on the current registration. Partial/full tanks or
unavailable targets cannot lose buffered units or issue an early duplicate remainder.
Only completion produces one output (or empties the slot for no-remainder fuel).
Missing registration leaves an unconsumed input available for extraction by both
players and automation. Changed
definitions affect only newly consumed inputs, not already captured batches.

Write schema **2** under existing `arce_fuel_loader`, with:

- `schema_version` int 2; `slot_role` int (0 empty, 1 input, 2 output);
- `item` native single-item compound, empty for an empty slot;
- `buffered_units` long; optional `owner_id` and `target_rocket_id` UUID arrays;
- when buffered, `batch` compound with `definition` ID string, `total_units` long
  and `remainder` native single-item compound (empty means none).

Buffered work and a nonempty slot are mutually exclusive. `remaining <= total`
and target requires buffered work plus owner. A batch exists iff buffered units
are positive. Input/output/remainder native compounds preserve IDs, Count=1,
ordinary `tag` and `ForgeCaps` without invoking external fuel providers.
The native input/remainder/item payload is bounded to 4 KiB, depth 16 and 256
nodes; the whole loader root is bounded to 16 KiB, depth 20 and 1,024 nodes.
No high-unit fuel increases per-tick work or rocket capacity. Schema-1 buffer
validation remains capped at 500; only new validated schema-2 batches use the
larger, existing rocket-capacity bound.

Read valid schema 1 explicitly: EMPTY/FUEL_CELL/EMPTY_CANISTER become the matching
empty/input/output state; buffered legacy fuel becomes the built-in frozen batch
with the saved remaining units and one pending empty canister. Preserve owner and
target. Retain the legacy codec for fixtures/migration; all live writes use schema 2.
Unknown fields/roles, malformed/future data and unresolved native item IDs block
operation rather than turning into air/default fuel. Bounded blocked roots,
including malformed non-compound Tags, are saved verbatim. Already-loaded
oversized/deep roots are quarantined without copying or inspecting item metadata;
`saveAdditional` passes the original Tag reference to the outgoing parent. It
must not throw, omit the root or replace it with an empty sentinel: native chunk
saving catches block-entity serialization exceptions and may save without that BE.
Ordinary survival removal is refused in `onDestroyedByPlayer`, before block
mutation, so an oversized inventory item is not created. These roots require a
backup/offline repair. Passthrough avoids deliberate omission/normalization; it
does not guarantee that vanilla can serialize arbitrary preexisting corrupt data.
Native persistence of these quarantined roots is outside the operational payload
budget and is not a durable recovery claim. Diagnostics never stringify raw Tags.
Successful known-item decoding must
round-trip its complete native item representation; normalization that loses
unknown metadata blocks use and preserves the raw root.

Operations that actually produce a loader item under its native loot table carry
the resource-bearing/blocked bounded schema root in
the dropped loader's BlockEntityTag instead of separately duplicating inventory.
Replacing the loader restores that root and owner; an obsolete target is still
revalidated. Creative destruction/no-drop operations retain ordinary Minecraft
destruction semantics. Old binaries recognize schema 2 as future and keep it while
placed; downgrading and breaking new-format loaders is unsupported. Roll back to
the pre-upgrade world backup, not a forced down-conversion.

The existing `survives_explosion` loot condition remains unchanged; this is not
recovery from explosions, commands or other no-drop destruction. The old
`onRemove` inventory emission is removed rather than duplicated alongside the
carried state. Restored ownership is not overwritten by placement.

## Verification and boundaries

Test registration/numeric limits and isolated API compilation; all legacy states,
new round-trips, invalid/future/missing-item preservation and item-size bounds;
real player/automation insertion, exact transfer/remainders including remainder
that is also fuel, denied ownership/range/state, partial buffer restart, changed
and missing definitions, and loader drop/place resource conservation.
The independent consumer uses only API/platform types and native gameplay/NBT.
Retain full build/DataGen/GameTest results, finite dedicated clean-process captures
and independent raw-data readback. No new upstream content or artwork is required.

The existing loader and rocket persist in separate world files. This contract
does not make their updates power-loss atomic or establish crash-cut durability;
clean restart evidence must not be presented as that guarantee. Cross-file crash
recovery, real clients, remote/full-parity and long-load acceptance remain open.
G0-G9, old Gate evidence, flight schemas and release authority are unchanged.
