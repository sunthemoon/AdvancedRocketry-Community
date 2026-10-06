# ADR-067 - Read-only seal detector leaf

```yaml
status: ACCEPTED
revision: 1
date: 2026-10-06
owner: sunthemoon
deciders: [sunthemoon]
target_version: v1.8.0
revisits: [ADR-024, ADR-066]
scope: C18a-SEAL-01
```

## Context

ADR-066 section 3.1 defines a six-block state-based boundary measurement and
adjacent supplied-cell query, without scan requests or force-loading. Its
section 9 also lists whole-batch dependencies. The detector does not consume
those machine/equipment resources, but no exception is stated. Separately,
ADR-024 requires registered-block interaction invalidation, and the current
atmosphere event adapter marks doors and registered boundaries dirty on
right-click before item use. An item-only measurement would therefore activate
a block or invalidate room authority before observing it.

This decision adopts only the two scoped qualifications below. The complete
implementation choices, bounds and disjoint ownership are in the
[leaf task](../work/v1.8.0-c18a-seal-detector/TASK.md). Proposal and source
reviews are separate; no source or art is assigned by this decision alone.

## Dependency qualification to ADR-066 section 9

For C18a-SEAL-01 only, the read-only seal detector depends on the existing
ADR-024 compiled boundary catalog, loaded-cell adapter and lifecycle-owned
atmosphere manager, plus its reviewed ordinary recipe/resources. It neither
depends on nor completes steel-fan/carbon-brick acquisition, fluid conversion,
vent/scrubber migration, station mutation/access authority or equipment/finder
integration. Those prerequisites remain mandatory for their dependent C18
interactions; this exception delivers none of those units.

## Interaction qualification to ADR-024

The exact registered detector interaction is excluded from preemptive
right-click invalidation only when block activation is denied and the detector
does not mutate the block. The item routing listener on both logical sides
sets `useBlock=DENY`; it never sets `useItem=ALLOW`, clears another listener's
cancellation or overrides an existing item-use denial. The atmosphere adapter
uses a private exact-item predicate before any world read to skip only that
detector preemptive mark. Its existing constructor retains the false predicate.

All other interactions and every real block/state/neighbor mutation retain
existing synchronous invalidation. Query-local environment/cache maintenance
and provider validity checks retain their existing behavior. This is not a
claim that ordinary world ticks or third-party same-JVM mods stop mutating data.

## Compatibility and boundaries

The additive stable item ID is `advancedrocketrycommunity:seal_detector`.
The leaf uses existing native item-use admission, both real hands and a shared
two-tick item cooldown. It checks the loaded clicked cell and face-adjacent cell
without loading, then reports boundary and known supplied-state separately.
One block is not room certification; ambient breathable air does not prove an
active supplying provider, and unavailable/pending is not a positive result.

No public API/classifier, network protocol, schema, equipment/HUD, resource
reserve, vent consumption, inventory or persistent truth store changes.
The item has no owned NBT, capability or durability; arbitrary existing held
data stays untouched. The NEW ordinary recipe, bilingual feedback and original
resource design require actual source/data/resource review and verification.
Persisted registration identity may not later be removed without normal
content-identity/migration disposition. This is not a rollback-by-deletion rule.

## Verification and authority

Independent review of the exact draft/task precedes the
[scoped adoption](../work/v1.8.0-c18a-seal-detector/ADOPTION-01.md).
Source assignment is separate. Authority is limited to the user's prior
conversation instruction: "授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认".
A change to acquisition, block activation, claim access, oxygen meaning or
persistence outside these exact choices requires separate disposition.

Actual source review must verify admission/query ordering, bounded reads,
DENY/cancellation behavior, detector-only versus ordinary invalidation,
lifecycle closure, recipient isolation and nonmutation. A fixed committed
uncached clean build, deterministic repeated DataGen and unfiltered GameTests
remain required; restart and real-client evidence are separate. No Required
Gate is waived, no existing ADR acceptance is broadened, no R-021 risk is
accepted and no ledger unit is delivered by this decision.
