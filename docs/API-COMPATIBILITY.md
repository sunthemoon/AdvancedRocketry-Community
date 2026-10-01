# Integration compatibility and support

Use this page when implementing an ARCE integration or changing a development
modpack's installed providers. It describes the **development API 1.8**,
not a stable release or a guarantee that arbitrary third-party mods work together.
For implementation examples and exact limits, read the
[public API guide](PUBLIC-API-GUIDE.md). For a buildable, isolated consumer, use the
[compatibility fixture](../compat-test-mod/README.md).

## Platform and installation boundary

| Combination | Scope |
|---|---|
| Java 17, Minecraft 1.20.1, Forge 47.4.10 | Development build and finite integration-test baseline |
| Forge 47.4.23 | Configured compatibility lane; historical results do not certify the current development artifact |
| Other Minecraft versions, Fabric or NeoForge | Not supported by this API distribution |
| API classifier on the compile classpath | Supported with ForgeGradle remapping and the platform libraries |
| API classifier installed as a mod or shaded into a consumer | Unsupported; the normal host mod supplies the runtime API classes |
| Matching host and integration on the required physical sides | Still subject to Forge dependencies, exact channel rules and the integration's own side checks |
| Arbitrary client/server build combinations | Not implied by API major/minor compatibility; menu framing and network protocols are separate contracts |
| 1.12.2 worlds or old mod binaries | No direct world or binary compatibility promise |

Declare the normal host as a loader dependency as appropriate for the integration.
`ApiVersions.check` compares an exact major and a minimum minor; it is not a loader,
network handshake, migration tool or sandbox. Check the minimum minor needed by
the features actually used. A runtime version check cannot make a class that is
absent from an older host safe to link before that check.

## Supported API inventory

Only these explicitly exported types are supported. Other Java `public` classes,
test hooks and implementation packages are not API. The classifier has **27 class
entries**, including the nested environment enum.

| Package suffix under `io.github.sunthemoon.advancedrocketrycommunity.api` | Minimum API | Exported types |
|---|---|---|
| `version` | 1.0 | `ApiVersion`, `ApiCompatibility`, `ApiVersions` |
| `rocket` (container adapters) | 1.1 | `RocketBlockEntityAdapter`, `RocketAdapterRegistrar`, `RegisterRocketAdaptersEvent` |
| `atmosphere` (boundaries) | 1.2 | `AtmosphereBoundary`, `AtmosphereBoundaryProvider`, `AtmosphereBoundaryRegistrar`, `RegisterAtmosphereBoundariesEvent` |
| `atmosphere` (equipment) | 1.3 | `SuitOxygenProvider`, `SuitEquipmentRegistrar`, `RegisterSuitEquipmentEvent` |
| `rocket` (components) | 1.4 | `RocketComponentDefinition`, `RocketComponentRegistrar`, `RegisterRocketComponentsEvent` |
| `rocket` (fuel) | 1.5 | `RocketFuelDefinition`, `RocketFuelRegistrar`, `RegisterRocketFuelsEvent` |
| `environment` | 1.6 | `AtmosphereProfile`, `EnvironmentSnapshot`, `EnvironmentSnapshot.Locus`, `EnvironmentQueries`, `ServerEnvironmentReadyEvent` |
| `satellite` | 1.7 | `SatelliteMissionDefinition`, `SatellitePayloadRegistrar`, `RegisterSatellitePayloadsEvent` |
| `endgame` | 1.8 | `EndgameEffect`, `EndgameEffectEvent` |

Registration events use the integrating mod's **MOD bus**, synchronously during
queued common setup on both physical sides. Definition/provider IDs belong to
the receiving mod. Duplicate claims and invalid registrations reject; keeping a
registrar does not extend its lifetime. Environment readiness is different: its
event uses the **FORGE bus**, once per logical server, and the query handle belongs
to that server/thread until stopping. Detached value objects do not grant mutable
registry or world access.

## What an integration can do

| Feature | Supported behavior | Important boundary | Finite implementation evidence |
|---|---|---|---|
| Container transport | Capture owned BlockEntity data and restore into a host-created target | Exact type registration plus movable tags; forbidden blocks still reject; vanilla chest/barrel precedence stays | [registration](work/v1.3.0-adapter-registration/VERIFICATION.md), [flight](work/v1.3.0-external-flight/VERIFICATION.md), [recovery](work/v1.3.0-external-recovery/VERIFICATION.md), [fault cases](work/v1.3.0-compatibility/VERIFICATION.md) |
| Atmosphere boundary | Compile a block-state-only sealed/permeable/default rule | No world/time-dependent callbacks; built-in rules and tags retain documented precedence | [boundary checks](work/v1.3.0-atmosphere-boundaries/VERIFICATION.md) |
| Suit equipment | Register armor slots and an owned NBT chest oxygen tank | Existing capacity/consumption; not a mirror of arbitrary fluid/energy capabilities | [equipment checks](work/v1.3.0-suit-equipment/VERIFICATION.md) |
| Rocket components | Supply immutable mass, thrust, capacity and roles | Captured rocket statistics do not change until reassembly; no new movement authority | [component checks](work/v1.3.0-rocket-components/VERIFICATION.md) |
| Item fuels | Consume a whole item into abstract fuel units and an optional distinct remainder | Not a fluid/capability drain or NBT-dependent callback; unfinished batches retain captured values | [fuel checks](work/v1.3.0-rocket-fuels/VERIFICATION.md) |
| Environment queries | Read configured surface or indexed station-orbit context without loading chunks | Not effective entity gravity, local room oxygen, travel or build permission | [environment checks](work/v1.3.0-environment-queries/VERIFICATION.md) |
| Satellite payloads | Manufacture a registered payload and run the existing research mission | Fixed manufacturing roles; no arbitrary reward/mission callbacks; started tasks retain snapshots | [satellite checks](work/v1.3.0-satellite-payloads/VERIFICATION.md) |
| Endgame effects | Veto a laser drill layer, a gravity field or an elevator ride before it affects the world | Cancel only; no device access, no way to change the effect; zones, stations, spawn protection and break events still apply | [ADR-054 section 5.1](decisions/ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md) |

These records identify their actual commits, artifacts and scenarios. An older
per-feature result is not certification of every newer artifact, actual third-party
modpack, forced crash, integrated-server session, real-client UI or multiplayer
interaction. Unknown combinations remain unverified rather than implicitly supported.

## Failure, replacement and removal

- **Rocket adapters:** a failed scan must leave the source alone. Failed restoration
  uses checked no-drop cleanup and keeps authoritative captured data if restoration
  is unavailable. Missing providers, block/type registrations, tags or payload
  versions can prevent recovery. Restore the matching dependencies; do not clear a
  journal or delete the rocket to silence the condition. Opaque retention protects
  captured snapshots/journals, not every ordinary third-party block or deleted item.
- **Oxygen providers:** malformed or incompatible owned data stays untouched.
  Invalid callbacks disable the provider until server restart; failed debits do
  not grant protection. Removing registration differs from removing the item's mod.
- **Components and fuels:** new definitions apply to new work. Existing numeric
  snapshots or consumed fuel batches remain captured. Unknown native loader items
  are quarantined rather than silently converted to air; bounded drop/placement
  recovery has explicit limits in the guide.
- **Satellite payloads:** missing definitions block new work, not completion of
  existing snapshotted tasks. Unregistered known payload items remain extractable;
  unresolved native items block and preserve the terminal root.
- **Endgame effect listeners:** a cancelled batch changes nothing and the device
  reports `TARGET_PROTECTED`; removing the listening mod removes only its veto.
- **Atmosphere rules and environment queries:** remove the registration/consumer,
  not saved world identity. A missing atmosphere rule uses ordinary fallback;
  callers must release expired environment handles instead of reusing a prior server.

Use a copy and retain backups before changing installed mods or formats. Positive
payload versions are exact-match identifiers, not automatic migration instructions.
Fuel Loader schema 2 has an explicit old-format migration; reverting a binary and
breaking new-format machines is not a supported downgrade procedure. Native
explosions/no-drop destruction, arbitrary same-JVM side effects and cross-file
power-loss atomicity are not general recovery guarantees.

Callbacks that are allowed at runtime execute on the owning server thread. A
5 ms returned-time check can reject a slow result **after it returns**; it cannot
interrupt a callback that hangs. NBT bounds constrain returned/persisted data,
not a provider's allocations before return. Do not run untrusted mod code on the
assumption that these contracts provide process isolation.

## Reporting and compatibility changes

For an integration defect, include the exact host/consumer JAR hashes, Java/Forge
versions, provider IDs and payload versions, smallest reproducing mod list,
relevant server/client logs, reproduction steps and a disposable world copy where
appropriate. Remove credentials from logs and do not publish private world data.
Identify whether failure occurred during registration, capture, restoration,
reload, claim or restart. Report Community Edition issues to this project, not
the original Advanced Rocketry maintainers.

Supported additions increase the API minor. Existing signatures and documented
behavior remain compatible within a major. Removal/incompatible changes require
a new major, a deprecation in at least one publicly released minor of the previous
major, replacement guidance and migration notes. No fixed support duration or
remote Maven publication is implied. See [the version policy](decisions/ADR-021-PUBLIC-API-VERSION-POLICY.md).
