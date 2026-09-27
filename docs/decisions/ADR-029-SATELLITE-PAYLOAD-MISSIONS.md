# ADR-029 — Declarative satellite payload missions

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
api_version: "1.7"
acceptance_basis: maintainer authorization to follow recommended current-version implementation solutions
```

## Decision

V130-SAT adds a payload-to-research-mission extension to the existing terminal,
not a general satellite scripting framework. ADR-010's scheduler, authoritative
identity, research/discovery transaction and mission snapshots remain unchanged.
Chassis, solar module, blank control chip, energy cost and generic bound package
remain host-owned. The data-storage slot accepts a registered payload item;
one whole untagged payload is consumed per assembly. Custom rewards, callbacks,
capability-bearing/NBT-configured payloads, orbital entities and future satellite
content are outside this slice.

API 1.7 preserves the 24 existing exports and adds three `api.satellite` types:

- `SatelliteMissionDefinition(int durationTicks, int researchYield,
  int discoveryCost, List<ResourceLocation> allowedTargets)`: immutable record.
  Duration 20..72,000 ticks; yield/cost 1..10,000, yield >= cost; targets 1..16,
  unique, IDs at most 128 characters. Constructor copies the list. Nulls fail
  with `NullPointerException`, invalid values with `IllegalArgumentException`.
- Public interface `SatellitePayloadRegistrar`, with `void register(ResourceLocation definitionId,
  ResourceLocation payloadItem, SatelliteMissionDefinition mission)`.
- Public final `RegisterSatellitePayloadsEvent`: non-cancelable synchronous MOD-bus event in
  queued common setup on both physical sides. Public non-null registrar constructor;
  its public `void register` method has the same signature as the registrar.

Registration is owner-namespace and loading-thread bound. At most 15 external
definitions/payload items, in addition to the built-in data satellite. Definition
IDs and input item IDs are bounded to 128 characters. Input items must exist and
must not be air or any of the terminal's other fixed components, package, chip,
redstone or built-in data-storage item. Items may belong to another namespace.
Duplicate IDs/items, foreign definition namespaces and capacity violations fail
atomically. Handles expire at the next receiving mod and at freeze. Retained,
off-thread and reentrant registration is rejected. No registered mission/reward
callbacks run during assembly, scheduling or reward delivery; Forge loading failures remain
loading failures, not a promise to sandbox malicious same-JVM code.
Null registration arguments throw `NullPointerException`; invalid values,
conflicts and budgets throw `IllegalArgumentException`; closed, stale, reentrant
or off-thread calls throw `IllegalStateException` before lookup/mutation.
Payload matching requires a bounded exact native ItemStack round-trip with no
serialized tag or ForgeCaps state; ItemStack capability callbacks remain native
Forge behavior, not user-supplied mission/reward callbacks.

## Data and player integration

Registration supplies defaults under the stable definition ID. Existing schema-1
`data/<namespace>/satellite_definitions/<path>.json` can override these defaults.
The merged catalog retains the existing maximum of 16 definitions (including
unbound datapack definitions); all targets must resolve in the celestial catalog.
An invalid candidate retains the last complete catalog, or fails initial loading.
Datapack definitions without a registered item remain legal for legacy identities;
registration adds an assembly recipe, not exclusive ownership of datapack content.

Assembly binds the payload's definition ID into the existing package/control-chip
identity. Subsequent launch/mission targets come from that identity, not the
built-in data satellite. The server still validates loaded nearby terminal,
owner, input/output, power, definition, target and mission state. Shift-click and
automation use the same component matching. Tagged payloads are not accepted.

Menus receive a bounded snapshot of up to 16 definition IDs and 16 target IDs per
definition. The wire format deduplicates targets into at most 128 IDs (the
celestial body limit), followed by per-definition target indices. IDs use bounded
128-character UTF reads/writes; the complete extra-data buffer must stay within
Forge's 32,600-byte limit, including position and its length prefix. Test the
maximum-length/maximum-count case rather than only normal fixtures.
A synchronized definition index selects the appropriate target list
when the chip/payload changes. Menu-open data gains an explicit format marker;
legacy readers reject it rather than interpreting a different target. Server
menus close/reject actions when their catalog generation changes, requiring a
reopen after reload. C2S buttons remain fixed bounded intent IDs. No new public
world-mutation API or client-provided position/definition is added. Mixed old/new
development-build menus are unsupported; upgrade host on both ends together.

## Persistence and missing extensions

No satellite, mission, item identity or terminal root schema changes: the existing
IDs and snapshotted scalar fields express this extension. Accepted reload only
affects future missions. Removing a definition blocks new launches/starts;
already launched missions can complete/claim their preserved snapshot, including
idempotent discovery replay. If a datapack deliberately retains the definition,
existing identities can continue missions even without the assembly registration.

An unregistered but still-existing payload item in a saved terminal remains
extractable but cannot assemble. Unknown item IDs are detected before native
ItemStack decoding can silently erase them. The complete bounded terminal root
is quarantined and preserved. Native removals that actually yield the terminal
item carry the bounded raw root exactly once and suppress separate inventory
emission. Placement restores the exact raw root, bypassing BlockItem's default
merge. Restore the item mod to recover. Wrong-tool/no-drop removals, explosions
and destructive creative/operator world edits are not recovery guarantees.
Invalid/future roots also stay blocked. Terminal roots are preflighted before
recursive operations: 64 KiB, depth 20, 2,048 nodes; individual native items are
4 KiB, depth 16, 256 nodes. Inventory requires Size=6, at most six compound entries,
unique in-range integer Slot values, positive byte Count within the native item
limit and known bounded IDs. Decoded items must round-trip without normalization.
Bounded invalid raw tags are copied unchanged; oversized/deep input is retained
by reference for save passthrough, never copied/decoded or carried as an item.
Normal survival removal of an uncarryable root is refused before breaking.
Reference passthrough does not promise that native storage can serialize arbitrary
corrupt data; preserve a backup before operator repair.
This is
not arbitrary cross-file power-loss atomicity or a new escrow protocol.

## Verification and rollback

Verify bounded registration and failure atomicity; catalog merge/reload; built-in
assembly, custom payload manufacturing/launch/claim; missing-definition snapshots;
menu selection/reload rejection; owner/replay denial; terminal save/reload and
unknown-item quarantine; API-only publication/consumer. Run short build, DataGen,
GameTest and finite native restart/removal checks, then independent review.
Real-client visuals/multiplayer and full original-feature/load campaigns remain
scheduled under ADR-018, not silently treated as passed.

Rollback removes the additive API and matching consumer together. Preserve worlds
before downgrading: older hosts cannot assemble the new payloads and may reject
terminal inventories. After public release ADR-021's deprecation rules apply.
No upstream source or assets are imported. Menu/event integration follows the
[Forge menu documentation](https://docs.minecraftforge.net/en/1.20.1/gui/menus/)
and [event documentation](https://docs.minecraftforge.net/en/1.20.1/concepts/events/).
