# ADR-012 — v0.9.0 Beta compatibility and feature-freeze contract

- Status: `ACCEPTED`
- Date: 2026-09-03
- Decision owner: repository owner
- Accepted: 2026-09-03
- Applies to: `v0.9.0`

## Context

`v0.9.0` is the first Beta milestone. Its purpose is to make the existing
v0.2–v0.8 gameplay reliable enough for a small dedicated server, not to add
another content system. The repository already has independent schemas and
fail-closed decoders, but it does not yet provide a world-level pre-migration
backup transaction or a Beta support boundary.

The version plan requires migration fixtures for v0.5–v0.8, two-hour soak
evidence, Forge baseline/latest coverage, optional JEI present/absent coverage,
malicious-packet testing, and an explicit zero-Critical/High decision.

## Decision

### Feature freeze

The only allowed production changes in v0.9.0 are:

- fixes for reproducible defects or violated security/performance bounds;
- one-way save migration, backup, validation, and recovery infrastructure;
- bounded configuration, logging, diagnostics, and player-readable failures;
- optional compatibility adapters isolated from core domain code;
- tests, profiling hooks, release automation, and documentation needed to
  prove the Beta Gates.

New celestial systems, machines, satellite types, destinations, weapons,
terraforming, asteroid gameplay, dynamic dimensions, and research-tree
expansion remain out of scope. Any exception requires a separate accepted ADR.

### Save compatibility

- The supported valued-world upgrade is the accepted v0.8.0 Alpha schema to
  v0.9.x Beta.
- v0.5–v0.7 formats remain executable fixtures so their individual codecs and
  migration steps cannot regress, but direct valued-world support starts at
  v0.8.0.
- Managed global SavedData is inspected before normal service startup. An
  upgrade creates a bounded, immutable backup before any replacement.
- Migration is sequential, staged, reread for validation, and committed with
  same-directory atomic moves where supported. A partial commit restores every
  managed original from the backup; failure never substitutes empty data.
- Future, malformed, oversized, or unsupported schemas fail closed with a
  stable diagnostic ID and recovery instructions.
- Downgrade and direct 1.12.2 world loading are unsupported.

### Network compatibility

Existing exact-match SimpleChannel protocol versions remain unchanged. Beta
does not silently broaden protocol compatibility. A protocol change requires
capability negotiation and a separate ADR.

### Optional compatibility

JEI integration is isolated under a compatibility package and declared as an
optional dependency. Absence must not change core initialization. The exact
tested JEI build and recipe visibility are recorded in the compatibility
matrix; other modpack combinations remain unsupported without a minimal
reproduction.

The Beta candidate pins the official 1.20.1 Forge API/runtime build
`15.56.0.205`. API artifacts are compile-only; the runtime is opt-in for the
compatibility client and is never bundled into the ARCE JAR.

### Release discipline

The release candidate is built as `1.20.1-0.9.0-beta.1`. A tag or public
pre-release can only follow G0–G9 evidence, explicit owner acceptance, required
PR checks, merge, and byte-for-byte post-merge reproduction.

## Consequences

- Startup migration becomes a small world-file transaction rather than an
  implicit side effect of whichever subsystem loads first.
- Backups consume bounded disk space and are retained after a failed upgrade.
- Beta claims are limited to Java 17, Minecraft 1.20.1, Forge 47.4.10, the
  recorded 47.4.23 advisory lane, and the recorded optional-JEI build.
- Core gameplay receives regression and soak work rather than new content.

## Rejected alternatives

### Keep Alpha's fail-closed loaders without a world backup

Rejected because it cannot demonstrate rollback after a partial multi-file
upgrade and does not satisfy the Beta valued-world commitment.

### Support every pre-Beta test world directly

Rejected because pre-v0.5 worlds were explicitly disposable and the resulting
matrix would be wider than the evidence available for a safe promise.

### Make JEI a hard dependency

Rejected because the core mod and dedicated server must remain operational
without optional client-facing integrations.
