# Public API: versioning, rocket containers and atmosphere boundaries

Use the API classifier when compiling an integration against supported ARCE
types. The API version is **1.2**. Version metadata remains JDK-only in
`io.github.sunthemoon.advancedrocketrycommunity.api.version`:

| Type | Purpose |
|---|---|
| `ApiVersion` | Immutable positive major and non-negative minor |
| `ApiCompatibility` | Compatible, major mismatch, or minor too old |
| `ApiVersions` | Host version and pure compatibility checks |

`api.rocket` additionally exports `RocketBlockEntityAdapter`,
`RocketAdapterRegistrar` and `RegisterRocketAdaptersEvent`, requiring the
Minecraft 1.20.1 / Forge platform. Other project packages are implementation
details, even when their Java types are `public`. `api.atmosphere` exports the four
state-boundary types described below. Equipment, fuel and satellite extension APIs
are not yet exported.

## Compile and run

Build with Java 17:

```text
./gradlew apiJar
```

The resulting `build/libs/*-api.jar` is a **compile-only** dependency. Add that
classifier through ForgeGradle's `fg.deobf` dependency handling, for example
`compileOnly fg.deobf("io.github.sunthemoon.advancedrocketrycommunity:advancedrocketry-community:${arceVersion}:api")`.
Supply an actual Maven or local artifact repository; this project does not imply
an already published remote package. The consumer also declares its Forge
platform dependency. See [ForgeGradle dependency handling](https://docs.minecraftforge.net/en/fg-6.x/dependencies/).

Install the normal ARCE mod JAR in the game at runtime. Do not install the API
classifier as a mod, or shade/bundle its classes into an integration. The normal
mod JAR supplies exactly the same API classes. Integrations still declare their
loader dependency on ARCE; a Java metadata check does not replace Forge's
dependency or Minecraft compatibility checks.

## Check compatibility

```java
ApiVersion required = new ApiVersion(1, 0);
ApiCompatibility compatibility = ApiVersions.check(ApiVersions.current(), required);
if (compatibility != ApiCompatibility.COMPATIBLE) {
    // Disable the integration and report the incompatibility to its user.
}
```

Import these types from the package above. A requirement specifies an **exact
major** and **minimum minor**. A different major returns `MAJOR_MISMATCH` first;
an insufficient minor within the same major returns `MINOR_TOO_OLD`. Invalid
version components throw `IllegalArgumentException`; null check arguments throw
`NullPointerException`.

These methods are JDK-only, thread-safe, side-independent and do not access a
world, network, file or mod loader. They do not validate third-party behavior.
API versions are independent of mod versions, packet protocols and save schemas.
Call `current()` instead of embedding a host-version constant.
Rocket adapter integrations require at least `new ApiVersion(1, 1)`.

## Register a container adapter

During the integrating mod's constructor, register a listener on its **MOD bus**:

```java
modBus.addListener((RegisterRocketAdaptersEvent event) -> event.register(
        ResourceLocation.tryParse("yourmod:cargo_inventory"),
        Set.of(ResourceLocation.tryParse("yourmod:cargo_container")),
        1,
        new CargoAdapter()));
```

`CargoAdapter` implements the three methods of `api.rocket.RocketBlockEntityAdapter`:

- `canMove(BlockEntity)`: read-only eligibility for this source inventory.
- `capture(BlockEntity)`: return only the inventory/data owned by this provider.
- `restore(BlockEntity, CompoundTag)`: restore that data into the host-created
  target; return false to reject. The host verifies a matching capture afterward.

The adapter ID must use the listening mod's namespace. The type set contains
registered **BlockEntity type IDs**, not block IDs (even when their names happen
to match). Registration does not override `rocket_movable`/`rocket_forbidden`
tags. Add supported block IDs to the movable tag through a data pack.

The event is dispatched during queued common-setup work, once per mod. Use it
synchronously; do not retain it, register asynchronously or wait for a server
event. The catalog freezes before the production rocket service is installed.
Duplicate IDs/types, absent types, nonpositive versions and invalid ownership
fail loading. Limits are 256 external adapters, 64 types per adapter and 1024
total external type mappings. Vanilla Chest/Barrel support, including existing
subclass matching, takes precedence and is not replaceable through this API.

Callbacks run on the logical server thread for loaded blocks. They must not
load chunks, schedule mutations or modify other blocks/entities/files. `canMove`
and `capture` must be read-only; `restore` may change only the new target's owned
state. Each callback has a 5 ms returned-time budget: a late result is rejected
and warned, but a non-returning callback cannot be preempted. Runtime exceptions
are contained; arbitrary same-JVM side effects, Java Errors and memory exhaustion
are not sandboxed.

## Preserve saved inventories

Use a stable adapter ID and positive payload version. The host wraps new external
data as `{payload_version, data}` without changing the old vanilla payload format.
Provider data must not contain root `x`, `y`, `z` or `id`; the entire envelope is
limited to 262144 bytes and also counts against the snapshot's 1048576-byte limit.
These are post-return size checks, not allocation limits on provider code.

Missing providers and mismatched versions preserve opaque saved data and prevent
destructive restoration. Reinstall the matching provider/version to retry.
Increasing a payload version alone does **not** migrate old data. Keep backward
support or define an explicit migration before changing an installed format.
Failed world cleanup may require recovery/operator inspection; the API does not
promise arbitrary-world or cross-chunk power-loss atomicity.

This preservation applies to data already captured in rocket snapshots and
transaction journals, not ordinary third-party blocks left in the world or item
types removed from the registry. Keep a world backup before changing installed
mods. If recovery is waiting on an absent integration, retain the rocket and
journal and restore the matching mod, block/BlockEntity registrations, movable
tags and payload version; do not delete recovery records to clear the condition.
Forge's missing-registry handling is separate from ARCE's opaque snapshot policy.

## Register an atmosphere boundary

State-boundary integrations require at least `new ApiVersion(1, 2)`. Subscribe to
`api.atmosphere.RegisterAtmosphereBoundariesEvent` on the integrating mod's MOD bus
during construction:

```java
modBus.addListener((RegisterAtmosphereBoundariesEvent event) -> event.register(
        ResourceLocation.tryParse("yourmod:airlock_rule"),
        Set.of(ResourceLocation.tryParse("yourmod:airlock")),
        state -> state.getValue(AirlockBlock.OPEN)
                ? AtmosphereBoundary.PERMEABLE : AtmosphereBoundary.SEALED));
```

The listener's owner-bound `AtmosphereBoundaryRegistrar` accepts a provider ID,
registered **block IDs**, and an `AtmosphereBoundaryProvider`. The provider ID must
use the listening mod's namespace. Duplicate IDs and overlapping block claims are
loading errors, including claims from the same owner. Do not retain the event/handle
or register asynchronously or reentrantly.

The host compiles **all possible states** once during queued common setup on both
physical sides. Rules must depend only on immutable block state: no world or block
entity lookups, time, mutable external state, files, networks or tag queries.
Tags are not bound at this loading phase and remain dynamic runtime rules. The host
retains immutable results, not callbacks; scans and reloads never invoke providers.

`AtmosphereBoundary.DEFAULT` uses the host's fluid/air/collision fallback;
`SEALED` stops traversal; `PERMEABLE` permits it even through a full collision cube.
The host's unloaded/build-height guards, sealing tag, built-in door/trapdoor/gate
state and permeable tag remain higher priority. For dynamic boundaries, encode
airtightness in BlockState and issue ordinary neighbor notifications when changing
the state or replacing the block. Silent changes without notifications are unsupported.

Limits: 256 providers, 64 blocks per provider, 1,024 blocks total, 4,096 states per
registration, 16,384 states total, IDs at most 255 characters. Each callback must
return a non-null value within 5 ms; accumulated callback time is capped at 1 second
per registration. A failure rejects the entire registration. Returned-time checks
cannot interrupt a hung callback or sandbox arbitrary same-JVM mod side effects.

Tag reload revokes cached room authority and pending scans, then rescans under the
existing budgets. Removing only the provider restores legacy classification after
restart; removing its blocks also invokes normal Forge missing-registry behavior.
The API adds no saved payload or packet. Position-dependent/BlockEntity rules and
equipment oxygen mutation are not part of this interface. See
[the boundary contract](decisions/ADR-024-STATE-BASED-ATMOSPHERE-BOUNDARIES.md).

## Compatibility policy

Supported API additions increment the minor version. Existing signatures and
documented behavior remain compatible within a major. Removal or incompatible
changes require a new major and a prior publicly released minor with deprecation,
a replacement and migration notes. See [the version policy](decisions/ADR-021-PUBLIC-API-VERSION-POLICY.md).

## Verify an integration boundary

`./gradlew test` checks the API archive allowlist, final runtime/classifier class
identity, version semantics, and isolated consumer compilation. The positive
fixture also runs with only JDK and classifier classes; the negative fixture
must fail specifically because an internal package is unavailable. These are
metadata checks. Platform-facing positive/negative fixtures additionally compile
with only the classifier and platform libraries. The development-only
[adapter test mod](../src/adapterTest/) exercises a registered inventory through
production commands and entity interaction; it is not shipped in the main JAR.
Full external-provider uninstall/restart and other integration systems remain
separate compatibility coverage. Contract details are in
[ADR-022](decisions/ADR-022-ROCKET-ADAPTER-REGISTRATION.md).

For an actual ForgeGradle classifier consumer, use the independent
[compatibility fixture build](../compat-test-mod/README.md). It reuses the fixture
source files but resolves the API from a Maven repository, not host source or
compiled output. Its separately reobfuscated JAR is installed alongside the normal
host JAR on a disposable dedicated server. Do not install both that JAR and the
development source-set fixture, or install the API classifier as a third mod.
