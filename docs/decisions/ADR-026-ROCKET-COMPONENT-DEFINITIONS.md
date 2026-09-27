# ADR-026 — Immutable rocket component definitions

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
api_version: "1.4"
accepted_by: sunthemoon
accepted_at: 2026-09-27
acceptance_basis: maintainer authorization to follow recommended current-version implementation solutions
```

## Context

The existing scan resolves four exclusive role tags to fixed block metrics and
persists their aggregate values plus passenger anchors in snapshot schema 1.
Flight schema 2 uses that captured capacity and route fuel calculation uses the
captured mass. An external component needs numeric definitions, not a live world
callback or permission to bypass movement/flight validation.

The maintainer's recommended-solution authorization accepts this additive
current-version contract under ADR-020/021. This does not accept a release, change
inherited Gates or schedule the deferred full original-feature campaign. Custom
fuel inputs and loader migration remain the separate `V130-COMP-02` slice.

## Exported surface

API minor becomes **1.4**, preserving the thirteen existing exported classes.
Add three types in `io.github.sunthemoon.advancedrocketrycommunity.api.rocket`:

| Type | Contract |
|---|---|
| `RocketComponentDefinition(long mass, long thrust, long fuelCapacity, boolean engine, boolean seat, boolean guidance)` | Immutable Java record with ordinary value/accessor semantics; no host internal dependency |
| `RocketComponentRegistrar` | Functional `void register(ResourceLocation definitionId, Set<ResourceLocation> blocks, RocketComponentDefinition definition)` |
| `RegisterRocketComponentsEvent` | Final MOD-bus event constructed with a registrar; forwards the same `register` signature |

Per block: mass is **1..1,000,000**, thrust **0..1,000,000**, capacity
**0..2,048,000**, in existing abstract mass/thrust/fuel units. Only an `engine`
may have nonzero thrust. An engine with zero thrust is permitted; normal total
thrust validation still applies. Multiple roles are explicitly allowed in one
definition (for example an engine with an integrated tank); each boolean adds at
most one corresponding component/anchor per block. All-false zero-thrust/capacity
defines a structural mass override. Invalid construction throws
`IllegalArgumentException`; no floating-point values or per-tick callbacks exist.

Numeric bounds are additive API input bounds, not increases of existing world,
scan or flight limits. Every aggregate fuel capacity above the existing
**2,048,000** flight limit is rejected as `FUEL_CAPACITY_EXCEEDED` before extracting
world blocks. Existing block-count, palette, volume, passenger and NBT limits
remain unchanged. Snapshot/flight validation and route selection stay server-owned.
More than sixteen seat-role blocks may contribute snapshot anchors, but actual
passenger capacity stays `min(seatCount, 16)`. A numerically valid high-mass
definition can still produce an unflyable rocket under the unchanged route fuel
limit; registration is not a guarantee that every resulting build can travel.

## Registration and precedence

Use synchronous owner-bound dispatch during queued common setup, after block
registration, on both physical sides. Subscribe during mod construction. The
host freezes the catalog before rocket services are installed. As with the
existing registrations, handles are loading-thread-only and expire on the next
owner dispatch or closure; do not retain events or register asynchronously.
This follows the platform's [queued lifecycle guidance](https://docs.minecraftforge.net/en/1.20.1/concepts/lifecycle/).

- Definition ID must use the receiving mod's namespace; all IDs are at most 255
  characters. Block IDs may belong to another mod or vanilla.
- One definition ID maps a nonempty set of known non-air block IDs to the same
  numeric value for **every state** of each block. This is intentionally static:
  state/world/BlockEntity-dependent metrics are outside this API.
- Reject duplicate definition IDs and overlapping block claims atomically. No
  last-writer-wins or partial registration; rejected IDs/blocks are not reserved.
- Reserve the host's `rocket_motor`, `rocket_fuel_tank`, `rocket_seat` and
  `guidance_computer` block IDs. Built-in definitions remain governed by existing
  tags, not external overrides.
- Limits: **256 definitions**, **64 blocks per definition**, **1,024 blocks total**.
  Close clears mutable builders; the frozen catalog contains only immutable
  definitions/mappings and no world references. Null arguments are rejected.
- For other blocks an explicit definition takes precedence over legacy role
  tags. With no explicit definition, the existing tag values, tag-conflict
  rejection and structural mass default are unchanged. Definitions do not alter
  tag reload behavior of the legacy fallback.
- Registration does **not** grant movability, remove a forbidden restriction,
  supply a missing BlockEntity adapter, load chunks or bypass player/owner/state
  checks. The normal scan applies these restrictions before resolving metrics.

As ordinary in-process Java code, registration listeners and caller collections
are trusted to terminate and obey the loading contract; there is no same-JVM
sandbox/preemption claim. No integration callback is retained for scan/tick use.

## Persistence, missing integration and downgrade

No component definition ID or opaque payload is added to a save. During assembly,
the server materializes numeric contributions into the existing snapshot totals
and one anchor per seat block. Existing content hashing includes these totals and
anchors; flight fuel capacity must match the snapshot. Keep snapshot schema 1,
flight schema 2 and all network protocols unchanged.

An already assembled rocket retains those captured totals after restart, changed
definitions or omitted component registration. This is deliberate **snapshot
semantics**, not live revocation: no provider is needed to interpret numeric
mass/thrust/capacity or operate that saved rocket. Disassembly restores ordinary
block states; the next assembly uses the then-installed definitions/tags. Public
registration IDs are stable diagnostic identities, not saved foreign keys.

Removing a mod's actual blocks is different: existing unknown-block recovery
retains the snapshot and refuses unsafe placement. Forge registry and binary
compatibility still govern removal/downgrade; this API does not promise arbitrary
mod uninstall. An older compatible host can read the unchanged numeric snapshot;
an integration requiring API 1.4 must still enforce its loader/API requirements.
The original saves are not rewritten to current catalog values on read.

## Verification and boundaries

- Unit-test numeric edges, aggregate capacity, owner/namespace, unknown/air/reserved
  blocks, duplicate/overlap atomicity, count caps, defensive copying, thread and
  closed/retained/reentrant handles. Verify API-only positive/negative compilation
  and exact classifier/main byte identity.
- Forge tests use actual scans and assembly with external definitions, including
  all component contributions and seat anchors, mixed built-in components,
  forbidden/non-movable/unsupported/unloaded cases and over-capacity rejection.
- An independently compiled mod exercises the public event and existing gameplay
  commands/interactions, without internal imports. Bounded packaged restarts
  retain captured stats with registration present/absent, restore original blocks,
  and distinguish current-definition reassembly from saved snapshot behavior.
- Run clean build, DataGen, GameTest, consumer packaging, short native persistence
  checks and independent applicable review/reruns. Real GPU/two-client, full
  migration/power-loss and long-load acceptance remain unexecuted until scheduled;
  this contract does not convert them into passes.

No upstream code or art is imported. Fixture content uses legal runtime references
to existing blocks; no official visual resource is copied. No custom fuel,
fluid capability or advanced propulsion implementation is implied by this slice.
