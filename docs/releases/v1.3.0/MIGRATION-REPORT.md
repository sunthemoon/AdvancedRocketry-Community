# v1.3 save, removal and wire compatibility

Use a copy of a backed-up world for development upgrades. No candidate-bound
whole-world migration or rollback approval exists. Direct 1.12.2 loading is
not supported by this work.

## Persisted and runtime contracts

| Area | v1.3 behavior | Evidence / operator boundary |
|---|---|---|
| Vanilla container snapshots | Existing vanilla payload format is unchanged | External provider envelopes do not rewrite the vanilla representation |
| External rocket inventories | Stable adapter ID plus exact positive payload version; host wraps `{payload_version, data}` | Missing provider/version/type/tag prevents destructive restoration and retains captured data; reinstall matching dependencies |
| Suit oxygen | Owned envelope schema 1 with provider ID, exact payload version and bounded detached data | Malformed/incompatible payload stays untouched; no automatic arbitrary-provider migration |
| Rocket components | Existing captured numeric snapshot remains authoritative until reassembly | Changed definitions do not retroactively recalculate saved rockets |
| Fuel Loader | Existing `arce_fuel_loader` root now writes schema 2 through `FuelLoaderStorage`; valid schema 1 explicitly migrates | Frozen partial batch retains remaining units/remainder/owner/target; unknown or malformed data blocks operation |
| Environment query | No new saved root, packet or per-position cache | Expired server handles must be discarded; detached values are not mutable world state |
| Satellite | Existing terminal, mission and item identity schemas remain; started tasks keep duration/reward/discovery snapshots | Missing definition blocks new work; unknown native terminal items preserve the root before ItemStack decoding |

The [fuel contract](../../decisions/ADR-027-ROCKET-ITEM-FUELS.md) is the actual
schema migration. `FuelLoaderPersistence` deliberately remains the old schema-1
reader/fixture codec; live writing uses
[`FuelLoaderStorage`](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/fuel/FuelLoaderStorage.java).
An API minor increase itself is not a save migration. No existing host tags or
configuration IDs were globally renamed, and no numeric dimension identity is
introduced.

## Wire compatibility is separate

The exact-match channel versions remain celestial **1**, life support **1**,
rocket visual **1**, and rocket flight **6** (flight 6 predates this API work).
The satellite menu nevertheless changed: `SatelliteTerminalTargets` writes
marker **-1**, format **1**, catalog generation and a bounded shared target
dictionary. See [ADR-029](../../decisions/ADR-029-SATELLITE-PAYLOAD-MISSIONS.md).
Legacy readers reject the new frame; identical channel labels do not make
mixed development builds safe. Upgrade matching host client/server together.
Catalog reload invalidates old open menus; reopen rather than submitting stale
selection state.

## Removal, quarantine and rollback

Known unregistered input items remain extractable where documented. Unknown
native items are checked before lossy decoding. Bounded quarantined loader or
terminal roots can travel in eligible native block-item drops and exact
placement; uncarryable roots refuse normal survival removal. Keep backups before
operator repair. Wrong-tool/no-drop destruction, creative edits, explosions or
arbitrary corrupt native serialization are not recovery guarantees.

Removing a provider is not the same as removing its item's or block's mod.
Opaque rocket snapshots/journals do not protect every ordinary missing world
block or item. Increasing the provider payload version does not migrate old
data automatically. Downgrading and breaking new-format Fuel Loaders is
unsupported; restore the pre-upgrade world copy rather than forcing a conversion.

## Evidence and missing proof

The [recovery](../../work/v1.3.0-external-recovery/VERIFICATION.md),
[fuel](../../work/v1.3.0-rocket-fuels/VERIFICATION.md) and
[satellite](../../work/v1.3.0-satellite-payloads/VERIFICATION.md) records retain
actual native bytes and independent readback. Schema-1 tests include deliberate
legacy-root fixtures; they are not an untouched historical full-world upgrade.
Synthetic journal stages and normal save/stop/restart are **S1**, not forced
process-kill/power-loss **S2**. Fuel Loader and rocket still persist in separate
world files; clean resource conservation does not prove their writes atomic.

Candidate work must include original stable/Beta world copies, full root/schema
inventory, repeat migration, future/downgrade rejection, supported recovery cuts
and exact before/after resources. Preserve originals and every failed attempt.
Inherited v1.2 Precision migration evidence remains bound to its own artifacts,
not silently upgraded into v1.3 candidate proof.
