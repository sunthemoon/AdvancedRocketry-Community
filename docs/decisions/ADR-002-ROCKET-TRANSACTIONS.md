# ADR-002 — Transactional rocket assembly and transfer

```yaml
status: ACCEPTED
date: 2026-09-01
deciders:
  - sunthemoon
target_version: v0.5.0
```

## Context

A block-built rocket moves world blocks, inventories, passengers, and entities. Partial failure can duplicate or destroy data. Cross-dimension transfer adds crash windows.

## Decision

Both assembly/disassembly and dimension transfer use explicit phase-based transactions with durable recovery journals where needed.

The client never supplies the authoritative snapshot. Third-party BlockEntities are denied unless an adapter is approved.

ADR-006 supplies the v0.6 fixed-destination authority boundary, journal phases,
passenger handling, and four-case cross-Level recovery policy.

## Consequences

- More implementation work before flight is visible.
- Failure injection and restart testing are required.
- Critical duplication bugs have a defined invariant and recovery path.

## Validation

- [x] v0.5 assembly/disassembly rollback and restart recovery
- [x] v0.5 exact material/container conservation and replay rejection
- [ ] v0.6 cross-Level transfer and passenger recovery (ADR-006)
