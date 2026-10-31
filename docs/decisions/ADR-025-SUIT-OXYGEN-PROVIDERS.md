# ADR-025 — Owned suit equipment and oxygen payloads

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
api_version: "1.3"
authorization: maintainer instruction to use recommended solutions
```

## Context and selected boundary

Life support currently recognizes four built-in armor pieces and reads the chest's
schema-1 `arce_space_suit_oxygen`. The engine and S2C HUD use 0–2,000 oxygen units;
one unit protects for twenty vacuum ticks and one whole canister adds 1,000 units.
These resource and display rules stay unchanged. Generic live ItemStack/capability
callbacks would allow partial mutations outside the host's resource commit.

Use fixed item-to-armor-slot mappings and callbacks on **detached owned NBT only**.
The host alone commits oxygen data and spends/refunds canister items. This supports
new integrations, not arbitrary existing capability-based oxygen tanks. External
providers must keep their payload authoritative rather than mirror another tank.
Forge's [capability lifecycle and synchronization](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/)
remain outside this API; no live capability, player, world or equipment reference
is supplied to a callback.

## Exported API and registration

Add three types under `api.atmosphere`; increase API minor to 1.3 under ADR-021:

- `SuitOxygenProvider`: `OptionalInt readOxygen(CompoundTag data)` and
  `CompoundTag writeOxygen(CompoundTag data, int oxygenUnits)`.
- `SuitEquipmentRegistrar`: functional `void register(ResourceLocation providerId,
  Map<ResourceLocation, EquipmentSlot> items, int payloadVersion,
  SuitOxygenProvider oxygenProvider)`.
- `RegisterSuitEquipmentEvent`: final MOD-bus event, constructor taking a registrar
  for host/tests, forwarding the same `register` method. Construction alone grants
  no host access.

During queued common setup, after item registration and before life-support
services, dispatch once per mod container on both physical sides. Listeners attach
in mod construction and register synchronously on the loading thread. IDs use the
receiving mod's namespace and are at most 255 characters. Item IDs may belong to
another mod; every item must exist, be unstackable and map to one of HEAD, CHEST,
LEGS or FEET. ArmorItem mappings must match their native slot. Built-in suit items
are reserved and cannot be replaced. Registration is atomic; duplicate IDs/item
claims, empty maps, unknown items, nonpositive payload versions, retained/foreign/
off-thread handles and calls after freeze/close reject loading.

Bounds: 256 providers, 64 items per provider, 1,024 external item mappings total.
The immutable catalog retains provider functions but no player/world references.
No callback executes during registration; callbacks are logical-server-only.
Providers may register subsets of slots. All four slots must be covered at runtime;
pieces from different providers or the built-in suit may mix. Only a CHEST mapping
supplies oxygen. Held/inventory items and wrong-slot/count stacks never count.
Registration does not itself make an item wearable; the integrating mod supplies
ordinary equipment behavior for non-ArmorItem items.

## Payload ownership and compatibility

New external chest data is stored only at ItemStack tag key `arce_suit_provider`:

```text
{schema_version: 1, provider: "namespace:id", payload_version: positive int,
 data: compound owned by that provider}
```

Exactly those four envelope keys are accepted. A missing root means empty data
and must read as present zero oxygen; its first successful refill creates the envelope.
All callbacks receive independent copies, never the saved tag. `readOxygen` is
pure, returns a present 0–2,000 or `OptionalInt.empty()` for item-local invalid or
unsupported owned data, and must not mutate its argument. Empty refuses both use
and refill without disabling the provider or overwriting that item; null and
present out-of-range values are provider faults. `writeOxygen` may mutate its
detached argument or return another compound, and must represent exactly the
requested 0–2,000 units while preserving other owned fields. Functions must be
deterministic from their arguments, without world/file/network/global side effects,
retained mutable data references or recursive host calls.

The complete envelope is limited to 16,384 uncompressed NBT bytes, depth 16 and
256 tag nodes, checked before recursive copying/serialization or provider input.
Post-write readback must be present and exactly equal to the requested units;
empty at this point is a provider fault, not acceptance of its generated data.
Returned payloads are also bounded before copying. Provider callbacks have a 5 ms
returned-time budget each; throwing, null/invalid/out-of-range results, mutated read
inputs, oversized output or failed post-write readback reject the operation.
Timing cannot preempt a non-returning callback, and arbitrary same-JVM side effects,
Java Errors or allocation exhaustion are not sandboxed.
Mutated read arguments are bounded again before recursive equality checks.

Unknown provider IDs, unsupported schema/payload versions and malformed data give
no usable oxygen and cannot be overwritten. Matching providers may resume after
restart. A failure preserves the original subtree byte-for-byte semantically;
unrelated ItemStack NBT is untouched. Changing a provider ID/version does not
migrate it; retain the old format or supply a separately reviewed migration.
Built-in suit data remains on its original key/schema, with no read/write changes.
Removing only registration preserves retained items and data; removing the item
mod itself invokes Forge's normal missing-registry behavior and is not covered.

## Server commit and failure lifecycle

Host player handling checks logical server/thread and the actual worn chest.
Read at most one registered chest payload per player life-support tick. A resource
update is prepared on detached data, checked by another read, and committed only
while the original chest identity/count and original owned subtree are unchanged.
The host copies the accepted payload so retained callback objects cannot alter it.
No callback runs for absent/invalid/future/disabled providers or built-in suits.

The engine decides breathability and the existing damage/oxygen cadence. A failed
oxygen debit is recomputed as zero usable oxygen using the original cadence phase,
so it cannot protect for that interval for free. Creative/spectator players and
breathable environments do not debit. Whole-canister use commits the validated
increment before consuming one canister and returning one empty shell; rejection
does neither. Creative refill behavior remains unchanged. Reentrant operations
reject before mutation. No new packet, capacity, scan, chunk load or player search
is introduced; the existing S2C snapshot reports the authoritative result.
The actual held oxygen canister identity/count and hand are revalidated after
callbacks and before the resource commit; invoking item use with another held
item cannot provide oxygen.

Callback contract failures disable that provider for the server session and issue
one bounded diagnostic with provider ID/reason, never provider payload or exception
text. Invalid saved input disables only that item, not every owner's equipment.
Server-stop cleanup clears disabled-provider state; immutable registrations remain
available for another integrated-server lifecycle. Login/logout lifecycle and the
existing player-state map remain host-owned.
Disabled providers contribute neither armor-piece mappings nor oxygen. A failure
during debit also recomputes the piece count before producing damage/status.

## Verification and rollback

Verify ownership/conflicts/bounds/closure; finite payload codec, empty initialization,
future/version/missing preservation, callback failures/timing/read mutation/retained
references/reentrancy and atomic post-write readback. Keep all built-in oxygen tests.
Forge tests exercise a real server player's mixed/partial/wrong-slot equipment,
oxygen/damage cadence, whole refills, rejected debits and creative/breathable cases.
An independent classifier-only fixture registers equipment and saves/reloads its
native ItemStack NBT on a finite dedicated run with a skipped-provider phase.
Independent review reruns applicable key tests. This is not V1/V2, forced power
loss, arbitrary foreign capability compatibility or the deferred full campaign.

Old hosts ignore the new root and do not recognize external suits, but retain item
NBT only if the item/integration mod can still load on that older host. API 1.3
binary dependencies may prevent startup on an older API. Restore the matching host/provider to
resume. No existing save migration or historical Gate approval is implied.

## Authorization and review

Accepted under the maintainer's recommended-solution instruction. Independent
read-only contract review distinguished item-local invalid data from provider-wide
faults using OptionalInt, clarified disabled armor mappings and wearable-item
responsibility, and required bounds before mutated-input equality. Those changes
are included before runtime implementation. This freezes the API contract only,
not release acceptance or any Required Gate.

## Additive revision note: enchanted native armor (v1.8.0)

**Superseded by [ADR-025 additive revision 1](PROPOSED-ADR-025-R1-ENCHANTED-NATIVE-ARMOR.md)**, which
the owner accepted on 2026-10-31 (decision D16). The revision states the complete rule. That includes
the `equipment.classicEnabled` condition, disabled providers and the shared canister target, which
this note omits. Where the two differ, the revision applies. This note is kept as written.

ADR-066 section 5.1 (owner decision D1) extends host eligibility and asks for the extension to be
reviewed as an additive revision of this ADR. This note records it; it changes no status, public
signature or external bound of this ADR.

- **Rule.** The host also counts an otherwise unclaimed native `ArmorItem` when the stack is safe
  (unstackable, count 1, bounded NBT), worn in its own slot, and enchanted with
  `advancedrocketrycommunity:space_breathing` at exactly level 1. Eligibility is checked for each live
  worn stack (`EnchantedSuitOxygen.eligible`); no item-to-slot mapping is registered.
- **Precedence.** A registered external provider always wins; the enchantment never claims or aliases
  an owned provider root, and no provider is registered per enchantment.
- **Payload.** Only the enchanted chest owns `arce_enchanted_suit_oxygen`: strict schema 1, oxygen
  0..2,000, with this ADR's detached commit, debit and refill checks. Unsupported or corrupt data is
  unusable and preserved. Other item NBT and the built-in oxygen root are untouched.
- **Review.** The W03 review (`D:/GitHub/ARCE-Task-Evidence/root-queue/W03-review/REVIEW.md`, SHA-256
  `4b74bf776c585444e8332a89a410baa612030ceb41e6861f6ba06c102b64f73f`) covered the rule; its findings
  M1 and L1 were fixed by W06. Delivery and tests are in
  `docs/work/v1.8.0-c18b-equipment/VERIFICATION.md`.

This is a development implementation note. It approves no Required Gate, version or release.
