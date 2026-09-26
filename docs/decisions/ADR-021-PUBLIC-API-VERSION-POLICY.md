# ADR-021 — Minimal public API version policy

```yaml
status: PROPOSED
date: 2026-09-26
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
accepted_by: ""
accepted_at: ""
initial_api_version: "1.0"
```

## Context and boundary

There is no project-owned exported API package or independent API artifact in
the inspected [Java tree](../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/)
and [build](../../build.gradle). Existing Java `public` classes are internal
implementation types, not a stable compatibility promise. The first slice
needs an explicit small boundary before external registration hooks are added.

This policy is proposed, not frozen. It does not authorize implementation without
the separately approved development baseline, and it does not freeze later
adapter/provider signatures or commit to unimplemented gameplay.

## Proposed first exported surface

Only the following types in
`io.github.sunthemoon.advancedrocketrycommunity.api.version` are exported by the
first slice; these names are proposed and do not exist yet:

| Type/member | Contract |
|---|---|
| `ApiVersion(int major, int minor)` | Public Java 17 record with `major()` / `minor()` accessors; positive Java `int` major and non-negative Java `int` minor; invalid construction throws `IllegalArgumentException` |
| `ApiCompatibility` | Enum: `COMPATIBLE`, `MAJOR_MISMATCH`, `MINOR_TOO_OLD` |
| `ApiVersions.current()` | Returns the host's immutable API version, initially 1.0; consumers call it rather than relying on inlined public numeric constants |
| `ApiVersions.check(ApiVersion offered, ApiVersion required)` | Pure compatibility check; null arguments throw `NullPointerException`; no world, mod-loader, file or network access |

`required` denotes one exact major and a minimum minor. A major mismatch is
reported first. For equal majors, `offered.minor < required.minor` returns
`MINOR_TOO_OLD`; otherwise the result is `COMPATIBLE`. This is a metadata check,
not proof that a provider follows runtime contracts or that arbitrary binaries
can load. Loader dependencies and integration behavior need their own checks.

`ApiVersions` is a final utility with a private constructor and the two static
methods listed above. `ApiVersion` retains the normal record value semantics;
the enum's three constants are the complete result set for API major 1.

All three types use only JDK types, are usable on either physical side and have
no mutable world state. Nothing else becomes public API by package adjacency,
Java visibility, existing test use or inclusion in the main JAR.

## Version and deprecation policy

- API major/minor is independent of mod SemVer, Minecraft/Forge compatibility,
  channel protocol numbers and persistent schema versions.
- Within a public API major, preserve existing source/binary signatures and
  documented behavior. Adding supported exported types, members or documented
  API capabilities must increase the API minor, so a minimum-minor requirement
  can distinguish hosts with and without those additions. Adding content through
  an unchanged API is not itself an API addition. Do not add mandatory abstract
  methods to an existing provider interface.
- A correction that restores documented behavior need not increase the API
  minor, but its mod release must identify the correction.
- Removal or incompatible change requires a new API major. Before removal,
  deprecate in at least one publicly released minor of the prior API major,
  document the replacement and provide a migration note. This is an API release
  condition, not a wall-clock support-duration claim.
- Experimental future types are not implicitly stable. Any future exported
  package/type must be added explicitly by its scoped contract decision; do not
  advertise unimplemented managers, mutable registries or test hooks as API.

## Packaging and first-slice verification

Propose an `api` classifier JAR containing only the explicit exported types.
The normal mod JAR contains the same classes for runtime. Consumers use the API
artifact as compile-only input and must not bundle/shade those classes or install
the classifier as a separate mod. No Minecraft/Forge/client classes are required
to compile this first metadata-only API.
Required license/notice files and standard JAR metadata are permitted; the
exported-class allowlist does not remove licensing obligations.

The first slice includes a separate consumer compilation task/project whose
compile classpath contains only the API classifier and JDK, not the main mod
JAR, source output or internal packages. Its successful compilation is a boundary
check, not the complete Forge compatibility test mod required later in v1.3.

Finite verification for this slice:

1. Same-major sufficient minor accepts; older minor rejects; either direction
   of major mismatch rejects; negative/zero-invalid fields and null arguments
   follow the specified failures without integer arithmetic overflow.
2. Independent consumer compiles against the classifier. An intentional import
   of an internal type fails that isolated compilation; this is a negative
   fixture, not a permitted source dependency.
3. The classifier contains only the explicit exported classes, and common API
   classes reference no client/Forge/Minecraft implementation types.
4. The normal build remains valid; no save roots, channel protocols or existing
   provider behavior are changed. No client or long-load campaign is introduced
   solely for immutable version metadata.

Existing channel protocols remain
[celestial 1](../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/network/CelestialNetwork.java),
[life support 1](../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/network/LifeSupportNetwork.java),
[visual 1](../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/network/RocketVisualNetwork.java)
and [flight 6](../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/network/RocketFlightNetwork.java).
Their exact-match checks are not replaced by this Java API compatibility rule.

## Later contracts remain separate

Before opening container/provider registration, define registration ownership,
freeze/matching order, bounded payload versions, partial-placement rollback,
missing-provider preservation, thread/side and callback failure rules. A timing
check after a synchronous callback returns cannot enforce preemption of one
that never returns; no same-JVM sandbox or arbitrary side-effect rollback is
promised by this version policy.

The full [v1.3 scope](../versions/V1.3.0-PUBLIC-API-COMPATIBILITY.md) remains:
rocket adapters, atmosphere/equipment, fuel/components, environment/body-context,
satellite extension points and a real compatibility mod. The metadata slice
does not substitute for those deliverables or any Required Gate.
