# ADR-002 — Transactional rocket assembly and transfer

```yaml
status: PROPOSED
date: 2026-08-26
target_version: v0.5.0
```

## Context

A block-built rocket moves world blocks, inventories, passengers, and entities. Partial failure can duplicate or destroy data. Cross-dimension transfer adds crash windows.

## Decision

Both assembly/disassembly and dimension transfer use explicit phase-based transactions with durable recovery journals where needed.

The client never supplies the authoritative snapshot. Third-party BlockEntities are denied unless an adapter is approved.

## Consequences

- More implementation work before flight is visible.
- Failure injection and restart testing are required.
- Critical duplication bugs have a defined invariant and recovery path.
