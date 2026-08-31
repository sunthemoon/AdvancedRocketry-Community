# ADR-001 — Fixed Moon and Space dimensions for the MVP

```yaml
status: ACCEPTED
date: 2026-08-31
target_version: v0.3.0
```

## Context

Advanced Rocketry historically supports dynamically defined planets. Minecraft/Forge 1.20.1 dynamic registries and world loading make arbitrary runtime dimensions a high-risk foundation, especially for saves and dedicated servers.

## Decision

The MVP registers fixed Moon and Space dimensions through data. Celestial bodies
are logical domain objects and may map to a Level or a region in a shared Level.

Definitions use a project-owned, bounded Codec loader. A complete candidate
catalog is decoded and validated before it replaces the active immutable
catalog. Data reload may change display/logic definitions, but it never adds,
removes, or replaces a loaded `Level` at runtime.

Stable world identities are:

```text
minecraft:overworld
advancedrocketrycommunity:moon
advancedrocketrycommunity:space
```

All code and persisted state use `ResourceLocation` / `ResourceKey<Level>`.
Legacy numeric dimension IDs are import-report metadata only and never become
runtime or persistent identity.

Cross-dimension discovery/visit state lives in schema-versioned Overworld
`SavedData`. The Space Level is void worldgen with a bounded command-created
safe platform; Moon uses a fixed, restart-stable flat test surface. Both remain
developer destinations until rocket travel is implemented.

No arbitrary runtime Level registration before a post-v1.0 ADR and prototype.

## Consequences

- Earth–Moon–Space core loop can be made stable.
- `planetDefs.xml` imports definitions but cannot create arbitrary runtime dimensions.
- Future planets may use generated datapacks requiring restart or shared-dimension instances.

## Rejected alternatives

- Runtime arbitrary Level registration: rejected for lifecycle, save, and
  dedicated-server recovery risk.
- One Level per imported XML planet: rejected because import data must not
  mutate dynamic registries.
- Numeric dimension IDs: rejected because they are not stable identities in
  modern Minecraft.
- Reusing the legacy static dimension manager: rejected because it mixes
  definitions, world state, network state, and lifecycle.

## Validation

- [x] Codec round trip and bounded field tests
- [x] parent existence, duplicate ID, and cycle rejection
- [ ] Moon/Space keys available on dedicated GameTest server
- [ ] SavedData restart evidence
- [ ] reload preserves the last valid catalog on invalid input
- [ ] static scan finds no persisted numeric dimension identity
