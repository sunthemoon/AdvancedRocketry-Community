# ADR-053 — v1.7 development baseline exception

```yaml
status: PROPOSED
revision: 3
date: 2026-10-01
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.7.0
proposed_baseline: ab10fb53a580e53a9a1c24097a487f2a7c54ad52
expires: before v1.7.0 release-candidate freeze
recovery_condition: complete or explicitly disposition every inherited and current Required Gate before freezing a v1.7.0 candidate
supersedes: ""
```

## Context

[v1.7](../versions/V1.7.0-ENDGAME-SYSTEMS.md) lists four prerequisites: the
v1.2 machine kernel PASSED, the v1.3 public API PASSED, v1.6 PASSED with the
orbit and mission context frozen, and an independent ADR and threat model for
every endgame system. None of v1.2, v1.3 or v1.6 is PASSED. The
[v1.6 development handoff](../releases/v1.6.0/RELEASE-EVIDENCE.md) records
implemented satellite and mission features with artifact-bound short
verification; it keeps V0/V1/V2, S2 with real players, reference-hardware
performance and the candidate matrix open.
[ADR-048](ADR-048-V160-DEVELOPMENT-BASELINE-EXCEPTION.md) permits v1.6 only and
explicitly excludes v1.7 production. ADR-018 defers the full acceptance campaign
until original content is complete, but does not waive version prerequisites by
itself.

The maintainer directs continued implementation, recommended solutions when
decisions arise, and verified development commits and pushes without release
tags (2026-09-30). This decision applies that standing authorization to one new
version; it does not invent a new numbered approval message or extend any
historical Gate waiver.

## Decision

Use `ab10fb53a580e53a9a1c24097a487f2a7c54ad52` (the v1.6 handoff commit) as the
immutable v1.7 development baseline on `codex/v1.7.0-endgame-systems`, while
inherited acceptance stays IN_PROGRESS. Permit only the existing
[Endgame Systems scope](../versions/V1.7.0-ENDGAME-SYSTEMS.md). Public and
persistent contracts are accepted separately before each slice is implemented:

- [ADR-054](ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md): the shared
  authority, protection, rate, energy, persistence, transit and audit framework;
- [ADR-055](ADR-055-ORBITAL-LASER-DRILL.md): the orbital laser drill;
- [ADR-056](ADR-056-RAILGUN-CARGO-AND-TARGETING.md): the railgun cargo launcher
  and its targeting;
- [ADR-057](ADR-057-BLACK-HOLE-GENERATOR.md): black-hole power;
- [ADR-058](ADR-058-AREA-GRAVITY-CONTROLLER.md): the area gravity controller;
- [ADR-059](ADR-059-SPACE-ELEVATOR-LOGISTICS.md): space-elevator logistics,
  built on [ADR-045](ADR-045-SPACE-ELEVATOR-ENDPOINT-CONTRACT.md).

The fourth prerequisite is satisfied only when those six are ACCEPTED; the first
three remain unchecked release prerequisites. The coverage table
(`docs/work/v1.7.0-contract-coverage.md`) maps every version-document test and
acceptance item to a contract section or a recorded disposition. The deferred
and rejected items are rows in `PORTING_MATRIX.md`.

All player-visible outcomes remain required:

- each of the five representative systems forms a complete, playable loop,
  obtainable through the recipes and progression of ADR-054 §16;
- every high-risk system has its ADR, threat model, configuration and audit;
- every system can be disabled by the server without breaking world load;
- permission, protection, recovery and performance checks pass;
- no forced or persistent chunk loading: the only tickets are the bounded,
  expiring elevator ride-arrival tickets (ADR-059 §8) and vanilla's own
  teleport ticket;
- zero Critical/High findings.

Metadata, a diagnostic command or passing unit tests alone cannot substitute for
these outcomes.

Retain short build/unit/DataGen/GameTest checks, independent actual-diff review
and finite save/restart/authority checks for each changed behaviour. Known loss,
duplication, grief or authority failures must be repaired; they are not deferred
as long-load testing. ADR-018 continues to schedule the full campaign; this
exception starts no remote, long-load or real-client campaign by itself.

Not authorized: PASSED/RELEASED or Gate PASS, candidate selection, tags,
publication, silent schema/API changes, unrecorded asset imports, v1.8 classic
content batches, terraforming, weaponised orbital strikes, or claims that v1.6
evidence independently proves new endgame behaviour. The earliest incomplete
acceptance cursor remains v1.0.0.

## Risk, expiry and recovery

Integration defects may remain in uncompleted inherited checks, and v1.7 touches
the station warp and deletion paths (ADR-044, ADR-045), the gravity path
(ADR-041) and the public API version (ADR-021). Preserve scoped commits, exact
artifact and fixture identities, independent findings and remaining obligations.
Before a v1.7 candidate freeze, every inherited and current Required Gate needs
evidence or a separately approved precise disposition with owner, expiry and
recovery condition. This decision does not extend any older waiver. Without this
version-limited exception, v1.7 remains planning-only.

## Review history

- Revision 1 (`10e3d2d`): accepted by contract review round 1 without required changes.
- Revision 2 aligns the outcome list with ADR-059 §8's ride-arrival tickets and
  ADR-054 §16's recipes (reviews R1-M4, R1-M12); see
  [review-01-dispositions](../work/v1.7.0-preparation/review-01-dispositions.md).
- Revision 3: accepted by review round 2; changed only by round-2 Lows or by
  numbers that follow ADR-054's round-2 answers; see
  [review-02-dispositions](../work/v1.7.0-preparation/review-02-dispositions.md).
