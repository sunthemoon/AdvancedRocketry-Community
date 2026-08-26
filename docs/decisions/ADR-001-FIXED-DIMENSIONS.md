# ADR-001 — Fixed Moon and Space dimensions for the MVP

```yaml
status: PROPOSED
date: 2026-08-26
target_version: v0.3.0
```

## Context

Advanced Rocketry historically supports dynamically defined planets. Minecraft/Forge 1.20.1 dynamic registries and world loading make arbitrary runtime dimensions a high-risk foundation, especially for saves and dedicated servers.

## Decision

The MVP registers fixed Moon and Space dimensions through data. Celestial bodies are logical domain objects and may map to a Level or a region in a shared Level.

No arbitrary runtime Level registration before a post-v1.0 ADR and prototype.

## Consequences

- Earth–Moon–Space core loop can be made stable.
- `planetDefs.xml` imports definitions but cannot create arbitrary runtime dimensions.
- Future planets may use generated datapacks requiring restart or shared-dimension instances.
