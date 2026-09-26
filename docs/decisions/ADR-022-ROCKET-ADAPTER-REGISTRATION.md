# ADR-022 — Frozen rocket adapter registration

```yaml
status: ACCEPTED
date: 2026-09-26
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
accepted_by: sunthemoon
accepted_at: 2026-09-26
api_version: "1.1"
```

The maintainer authorized recommended implementation decisions. This decision
applies that authorization to the planned v1.3 third-party BlockEntity boundary;
it does not approve release Gates or change ADR-018's full-test scheduling.
V130-ROCKET-01 has verified exception containment and recovery prerequisites.

## Supported surface

Add three types under `api.rocket`, alongside the unchanged version signatures:

- `RocketBlockEntityAdapter`: `boolean canMove(BlockEntity)`,
  `CompoundTag capture(BlockEntity)`, `boolean restore(BlockEntity, CompoundTag)`.
- `RocketAdapterRegistrar`: `void register(ResourceLocation adapterId,
  Set<ResourceLocation> blockEntityTypes, int payloadVersion,
  RocketBlockEntityAdapter adapter)`.
- `RegisterRocketAdaptersEvent`: final non-cancellable Forge mod-bus event,
  implementing `IModBusEvent`, with constructor `(RocketAdapterRegistrar)` and
  `register` forwarding the same four arguments. The constructor is for the host
  and test harnesses; constructing an event does not grant access to a registry.

The event is sent once to each mod during common-setup enqueued work, before the
host constructs/installs its production RocketManager. Host-issued registrars
bind ownership to the receiving ModContainer, not a caller-supplied namespace.
Use the event synchronously; retained handles, later events, another thread and
post-freeze registrations are rejected. Client loading registers the same catalog;
world callbacks execute only on a logical server thread in loaded regions.

## Validation, ordering and budgets

Adapter IDs must belong to the receiving mod; IDs/types are at most 255 characters.
Versions are positive integers. Type sets are nonempty and copied; referenced
BlockEntity types must exist. Reject duplicate adapter IDs and type mappings,
including reservation of the vanilla adapter ID and vanilla chest/barrel types.
Limits: 256 external adapters, 64 types per adapter, 1024 total type mappings.
Invalid registration fails loading instead of silently dropping an integration.

Keep the legacy vanilla adapter first, including its existing Chest/Barrel
subclass matching. External providers match exact BlockEntity type IDs, with no
priority framework. Subclasses already handled by the legacy adapter remain
legacy-handled. Movable/forbidden tags and host validation remain mandatory;
registration does not by itself make a block movable.

`canMove` and `capture` must not mutate state. `restore` may modify only the new
target BlockEntity's owned data. No provider may load chunks, spawn entities,
write files, schedule mutations or modify unrelated world resources through these
callbacks. These are trusted same-JVM integration rules, not sandbox enforcement.

Host copies and validates returned data. Each external callback has a 5 ms
returned-time budget, measured with a monotonic clock: an overrun is rejected
after return and warned once per adapter. This does not preempt a blocked callback
or promise a whole-tick bound for arbitrary external code. Runtime exceptions,
null and invalid data reject the operation; `Error`/OOM are not sandboxed.
The existing bounded snapshot, scan and NBT limits are not increased.

## Persistence and failure

Do not change snapshot schema 1, journal schema 2 or legacy
`advancedrocketrycommunity:vanilla_container_v1` payloads/hashes. New external
IDs store an envelope inside the existing payload `data`:

```text
{payload_version: positive-int, data: provider-compound}
```

Only these envelope keys are accepted. The provider body cannot contain root
`x`, `y`, `z` or `id`; wrapping must not bypass identity-field validation. The
complete envelope counts toward the existing 262144-byte payload limit and
1048576-byte snapshot limit. This is post-return validation, not a pre-allocation
or arbitrary-depth NBT sandbox.

Registry-independent decoding retains opaque external data. Before mutation,
missing adapters or a mismatched/malformed envelope block restoration and retain
entity/journal authority. Reinstalling the same provider/version permits retry.
There is no passive payload migration or automatic interpretation of newer
versions. A provider changing its payload format must maintain its supported
format or obtain a separately specified migration; incrementing a version alone
does not migrate existing worlds.

Host checked cleanup, payload readback and recovery apply to external providers.
They do not compensate arbitrary side effects or guarantee cross-chunk fsync
atomicity. Confirm entity removal before exposing restored block inventories.

## Artifacts and verification

Increase API minor to 1.1. Preserve the three metadata types' JDK-only compilation
and consumer test. Add the three platform-facing types to the explicit classifier
allowlist, using identical runtime bytes. Compile a second consumer against only
the classifier plus Minecraft/Forge platform dependencies, never main/internal
classes. Consumers must not shade or install the classifier as a second mod.

Verify owner/conflict/capacity/phase/thread behavior; envelope versions, identity
fields and limits; callback failures/slow returns; unchanged vanilla payloads;
and actual mod-bus registration reaching production scanning/restoration. Bounded
build, DataGen, GameTest and dedicated checks remain necessary. A complete
external compatibility mod, uninstall/reinstall and cross-dimension consumer
coverage follow as V130-ROCKET-03, not inferred from a local test provider.

No atmosphere/equipment/fuel/satellite API is frozen by this decision. API
deprecation policy remains [ADR-021](ADR-021-PUBLIC-API-VERSION-POLICY.md).
