# ADR-003 — Budgeted atmosphere scanning

```yaml
status: PROPOSED
date: 2026-08-26
target_version: v0.4.0
```

## Context

Flood-filling sealed rooms can visit very large volumes and freeze a server. Rechecking every tick is unnecessary and dangerous.

## Decision

Block changes mark regions dirty. Scans run as resumable tasks with per-tick node budgets, a maximum total volume, and no forced chunk loads.

Initial states are VACUUM/BREATHABLE; complex gas simulation is deferred.

## Consequences

- Atmosphere changes may take several ticks.
- UI must expose PENDING/TOO_LARGE/OPEN states.
- Cache invalidation and chunk-boundary tests are core functionality.
