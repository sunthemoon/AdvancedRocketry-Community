# v1.4 save, data-pack and wire compatibility

Use complete backed-up world copies. This is development evidence, not a
whole-world upgrade guarantee or direct 1.12.2 compatibility.

## Contracts

| Area | Current contract | Operator boundary |
|---|---|---|
| Celestial definitions | Schema 2; missing/explicit 1 uses legacy decoder | Stable IDs remain; strict new fields require schema 2, absent opt-ins default false |
| Physical worlds | Fixed startup dimension keys; optional body Level mapping | Metadata does not create/delete a Level; never reuse removed identities |
| Body/Level ledger | `advancedrocketrycommunity_planetary_bindings.json`, independent schema 1, 128 retained entries | First adoption pins the configuration then present; no historical remap reconstruction or deletion to bypass conflicts |
| Discovery and missions | Existing schema-2 SavedData roots and schema-1 records retained | Shared discoveries/private research; paid receipts and captured fees remain authoritative |
| Stations and rockets | Existing station UUID/orbit/member data and saved rocket targets retained | Discovery grants neither membership nor a surface to gas giants |
| Public Java API | 1.7, unchanged classifier bytes from v1.3 | API version is independent of wire, save and mod versions |
| Channels | Exact-match flight 8, celestial display 2, life support 1, rocket visual 1 | Upgrade host clients/server together; old NAV flight 7 is not this build |
| Satellite opening data | Inherited marker -1 / format 1, generation and bounded target dictionary | Matching channel labels alone do not establish menu compatibility |
| Sky resources | Client profile schema 1 | No save migration; startup dimension-type effects opt into the renderer |

Legacy celestial defaults preserve old environment behavior. Schema-2
`environment_effects` and `discovery_required` accept only literal booleans,
defaulting false when absent. Radiation remains metadata. An older strict
schema-2 build can reject these fields even though the schema number is the
same. Downgrade means restoring the complete pre-upgrade world plus matched
data packs and binaries, not selectively copying newer stores into an old world.
Inherited [v1.3 provider/Fuel Loader boundaries](../v1.3.0/MIGRATION-REPORT.md)
still apply.

## Reload, removal and recovery

Celestial definitions/routes publish as one validated generation. A malformed,
over-budget or incompatible candidate retains the previous pair; initial
invalid data fails closed. This is not an atomic reload of all Forge listeners.
Restore missing IDs and valid related routes/satellite targets together. The
body/Level ledger reserves removed mappings; physical world directories and
station records are never deleted by a celestial reload.

The ledger's distinct `advancedrocketrycommunity_planetary_bindings.json.pending`
file blocks startup after interrupted staging; it is not automatically promoted
or overwritten by the research-receipt retry policy. Keep the world stopped and
take a complete backup before inspecting/comparing or quarantining that pending
file, or restore a known complete backup. Never discard the accepted ledger.
See [ADR-032](../../decisions/ADR-032-PERSISTENT-PLANETARY-BINDINGS.md); this policy
is separate from the two SavedData `.arce-pending` files described below.

Discovery keeps at most 128 historical IDs, including removed bodies. At
capacity, a new paid claim stays pending; repeated clicks cannot free capacity.
Keep backups and resolve data with the operator. Do not erase prior IDs, change
identity bindings or discard research records to make a retry appear successful.

Research receipt, discovery and final completion are saved in that order using
checked same-directory atomic replacement. Startup and bounded runtime replay
repair paid pending or historically completed discovery claims without another
award. An unavailable body waits for the same identity; unclaimed READY missions
are not automatically awarded. Missing discovery repaired from a paid receipt
uses world time, not the distinct mission scheduler clock.

An `.arce-pending` scratch file is not a second authority and must not be
promoted manually. Failed storage/future data stays retryable or blocked, not
reported as completed. These writes target process-interruption recovery on a
filesystem supporting atomic moves. File forcing/renaming is not a portable
power-loss or directory-metadata durability guarantee. Never mix the two stores
from different backups. Other chunk/inventory/rocket transactions are unchanged.

## Actual migration and interruption observations

[MIG-02](../../work/v1.4.0-mig-worlds/VERIFICATION.md) uses a preserved 34-file
v1.3 development world, copied before any execution. Its original terminal and
accounts survive; a station and rocket are explicitly created on the copy by
the old runtime. Four clean processes cover upgrade, coherent removed-content
restart/restoration and final restart. The original 34 files remain unchanged.
Closed-world readback checks cargo/fuel/terminal/station data; live checkpoints
do not pretend active Anvil files are closed. The first failed live capture is
retained, not discarded. The historical consumer is not the later v1.3 handoff
consumer; exact input identities are in the original report.

[MIG-03](../../work/v1.4.0-mig-cuts/VERIFICATION.md) holds four selected actual
replacement boundaries with an external JDI observer before an owned-process
kill. Before restart, the authority files are not repaired. Unpaid READY remains
unpaid; paid receipt/discovery/final-completion cuts recover without duplicate
research. Native account values remain 20/120/100; repeated claims do not repay.
The initial incorrect scratch-cleanup assertion and observer failure limitations
are retained. This is finite **S2 process-cut** evidence for those two stores,
not every interruption point, hardware power loss or all subsystems.

The handoff rebuild matches MIG-01/02/03 artifacts and reuses those records;
it does not rerun packaged servers. Representative Beta/stable-to-candidate
upgrades, remaining original subsystems, real players and final candidate S2
acceptance still require separately bound evidence.
