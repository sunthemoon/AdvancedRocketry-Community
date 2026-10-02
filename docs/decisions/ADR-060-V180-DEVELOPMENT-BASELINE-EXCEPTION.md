# ADR-060 — v1.8 development baseline exception

```yaml
status: PROPOSED
revision: 1
date: 2026-10-02
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.8.0
proposed_baseline: 55da6a58842762c382edce0a5a842d06bb76ff6e
expires: before v1.8.0 release-candidate freeze
recovery_condition: complete or explicitly disposition every inherited and current Required Gate before freezing a v1.8.0 candidate
supersedes: ""
```

## Context

[v1.8](../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md) lists four
prerequisites: the v1.2 machine kernel PASSED, the v1.3 public API PASSED, v1.7
PASSED with the main system contracts frozen, and an upstream code and asset
inventory that can be decided item by item. None of v1.2, v1.3 or v1.7 is
PASSED. The [v1.7 development handoff](../releases/v1.7.0/RELEASE-EVIDENCE.md)
records the implemented endgame systems with artifact-bound short verification;
it keeps V0/V1/V2, S2 with real players, reference-hardware performance, the
flush-cost gate (ADR-054 §7), the candidate-bound matrix and the final audit and
human decision open. [ADR-053](ADR-053-V170-DEVELOPMENT-BASELINE-EXCEPTION.md)
permits v1.7 only and explicitly excludes v1.8 classic content batches.
[ADR-018](ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md) schedules the full
acceptance campaign after the original content is complete, which is what v1.8
delivers, but it does not waive version prerequisites by itself.

The maintainer's standing direction (2026-09-30) is continued implementation,
recommended solutions when decisions arise, and verified development commits and
pushes without release tags. This decision applies that direction to one new
version. It does not invent a new approval message or extend any historical Gate
waiver.

## Decision

Use `55da6a58842762c382edce0a5a842d06bb76ff6e` (the v1.7 handoff commit) as the
immutable v1.8 development baseline on `codex/v1.8.0-classic-content`, while
inherited acceptance stays `IN_PROGRESS`. Permit only the existing
[Classic Content Completion scope](../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md),
under these contracts:

- [ADR-061](ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md): registry
  identity, material tags, the asset import pipeline and its sources, content
  validation, the recipe graph, and the per-batch minimum tests;
- [ADR-062](ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md): the
  per-unit dispositions of the legacy content, the deferred and rejected items
  with their player impact, and the C15–C18 batch plan.

The fourth prerequisite is met when ADR-061 and ADR-062 are ACCEPTED: the
[legacy inventory](../work/v1.8.0-legacy-inventory.json), the
[content ledger](../work/v1.8.0-content-ledger.csv) and the
[asset plan](../work/v1.8.0-asset-plan.csv) give every legacy unit and every
legacy asset one decision, and `scripts/validate_v180_content_ledger.py` checks
them against `legacy-manifest`. The first three prerequisites remain unchecked
release prerequisites.

Each batch freezes its own contract before its runtime work: a batch ADR, an
independent review and an acceptance, as for every earlier version. A batch
cannot change the dispositions of ADR-062 by itself; it proposes a revision.

All player-visible outcomes of the version remain required:

- the planned classic machines, items, armor, components, recipes, models,
  sounds and progression are obtainable and form a reachable technology path;
- every imported file has its source, license, hash and transformation recorded
  before it enters the tree;
- every DEFERRED and REJECTED item has an accepted ADR and a player-impact
  statement;
- each machine has happy-path, failure and restart tests and uses the v1.2
  kernel or an exception ADR;
- zero Critical/High findings.

A ledger row, a registered placeholder or passing unit tests alone cannot
substitute for these outcomes.

Retain short build/unit/DataGen/GameTest checks, independent actual-diff review
and finite save/restart checks for each changed behaviour. Known loss,
duplication or authority failures must be repaired; they are not deferred as
long-load testing. ADR-018 continues to schedule the full campaign; this
exception starts no remote, long-load or real-client campaign by itself.

Not authorized: PASSED/RELEASED or Gate PASS, candidate selection, tags,
publication, silent schema or API changes, asset imports without a provenance
record, imports from sources ADR-061 does not name, v1.9 parity-beta work,
terraforming, or claims that v1.7 evidence independently proves new content
behaviour. The earliest incomplete acceptance cursor remains v1.0.0.

## Risk, expiry and recovery

Integration defects may remain in uncompleted inherited checks. v1.8 changes
existing recipes (progression), adds fluids, materials and world generation to
existing Levels (new chunks only), and adds many block entities, so it touches
the machine kernel (ADR-016), planetary worlds (ADR-031, ADR-033), the rocket
component and fuel contracts (ADR-026, ADR-027) and the station contracts
(ADR-040, ADR-041, ADR-046). Preserve scoped commits, exact artifact and fixture
identities, independent findings and remaining obligations. Before a v1.8
candidate freeze, every inherited and current Required Gate needs evidence or a
separately approved precise disposition with owner, expiry and recovery
condition. This decision does not extend any older waiver. Without this
version-limited exception, v1.8 remains planning-only: read-only audit, test
design and data samples.

## Acceptance record

Not accepted yet. Acceptance needs independent contract review of ADR-060,
ADR-061 and ADR-062 and the maintainer's confirmation.

## Review history

- Revision 1: proposed with the C14 audit.
