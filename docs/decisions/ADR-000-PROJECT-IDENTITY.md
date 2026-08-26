# ADR-000 — Project identity and namespace

```yaml
status: ACCEPTED
date: 2026-08-26
target_version: v0.0.1
```

## Context

The original project is MIT-licensed, but a community rewrite must avoid appearing official or colliding with a future official port. Reusing the original mod id would simplify asset paths but increases identity and compatibility ambiguity.

## Decision

Default identity:

```text
repository: AdvancedRocketry-Community
display name: Advanced Rocketry: Community Edition
mod id: advancedrocketrycommunity
legacy namespace: advancedrocketry
Java package: io.github.sunthemoon.advancedrocketrycommunity
```

Use the original name only with visible unofficial attribution. Do not use the original mod id unless a later ADR is supported by maintainer communication and conflict analysis.

## Consequences

- Asset import scripts must rewrite namespace/path.
- Existing 1.12 IDs are not silently treated as compatible.
- The project can be renamed with lower technical cost.
- Datapacks written for a hypothetical official `advancedrocketry` 1.20.1 mod are not automatically compatible.

## Validation

- [ ] README/NOTICE/About consistent
- [ ] mods.toml consistent
- [ ] no original package root
- [ ] provenance records legacy namespace
