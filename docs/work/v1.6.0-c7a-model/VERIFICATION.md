# V160-SAT-01 (C7a) verification: satellite model, components and registry root 3

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`2752776`. Contracts: [ADR-049](../../decisions/ADR-049-SATELLITE-BLUEPRINTS-AND-ASSEMBLY.md)
§1–§4, §6–§7 and [ADR-050](../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md)
§2–§3, §10. The runtime identity becomes `1.20.1-1.6.0-dev`. No Gate, candidate or tag.

## Implemented

- **Kinds, stats, blueprints.** `SatelliteKind` (five kinds), `SatelliteStats` with
  the ADR-049 caps, `SatelliteBlueprint` (the legacy `data` label has no
  components), and `SatelliteKindState` (survey charge and scan parameters; solar
  multiplier and receiver link).
- **Component catalog.** `satellite_components` schema 1 is decoded strictly:
  unknown fields, fields of another role and out-of-bound values are rejected;
  items must exist and be unique; at most 64 files. It loads before the definition
  listener. An invalid reload keeps the last catalog, and an invalid first load fails.
- **Blueprint evaluator.** Slot layout, 64-bit sums, caps and requirements are
  reported in the frozen refusal order. Existing codes are reused
  (`INVALID_COMPONENTS`, `STAT_LIMIT`, `REQUIREMENT_UNMET`), and new
  `SatelliteOperationCode` constants are only appended (persisted ordinals are
  unchanged). Launch-time re-derivation from an identity's component list is
  provided.
- **Definitions schema 2.** Kind definitions share `satellite_definitions`,
  dispatched on `schema_version`. The catalog enforces at most 16 kind
  definitions, unique IDs across both schemas, known launch targets, and exactly
  one definition per primary component of the matching kind. The ADR-029 data
  path is unchanged.
- **Records.**
  - Satellite record schema 2: kind, orbit body, blueprint, kind state; bound 2 KiB.
  - Mission record schema 2: kind, seed, start epoch, reward version, optional
    instance and quarantine, and data/survey/resource payloads with per-kind key
    exclusivity; bound 4 KiB.
  - Asteroid instance record schema 1: bound 4 KiB.
  - The decoders fail closed: unknown kinds, mixed payload keys and future schemas
    block the registry and preserve its payload.
- **Registry root 3.** Satellites, missions, accounts, instances and `save_epoch`.
  Roots 1 and 2 are upgraded by copying records, so extra tags are kept:
  satellites get the legacy `data` label, missions get `seed 0`, `start_epoch 0`
  and `legacy-data-v1`. The upgrade adds an empty instance list and save epoch 1.
  It runs in the pre-start `WorldDataMigrationService` (validate, back up, stage,
  atomic replace), with its own format epoch `v1.6.0-satellite-missions`. The root
  bound is 16 MiB. Finished missions may outlive a removed satellite; unfinished
  ones may not.
- **Item identity schema 2.** Non-`data` kinds carry their kind and 1–8 component
  IDs. `data` identities keep the exact schema-1 bytes.
- The operator report adds `satellite_root_schema=3`.

## Implementation clarifications of the accepted contracts

These keep each contract's stated invariant. They are recorded for the C9 review.

1. **Save epoch (ADR-050 §2).** A write carries E + 1 only if the registry
   changed since the last persisted epoch; otherwise it carries E. E advances
   only after such a changed write returns without error.
   - The literal "every save writes E + 1" made `save(load(x)) ≠ x`, which broke
     the existing reload-stability and no-op-repair tests.
   - The invariant is kept: a mission started at epoch s is present in every
     file whose epoch is greater than s.
   - `saveEpochAdvancesOnlyAfterAChangedWriteSucceeds` covers it.
2. **Counters (ADR-050 §10)** are derived when a root loads, not persisted, so
   they cannot disagree with the records.
3. **`data` missions** started by v1.6 record seed 0 and reward version `data-v1`,
   because no algorithm consumes their seed. The server-seed source arrives with
   the seeded kinds in C8a.

## Tests

New: `SatelliteBlueprintsTest` (all 17 blueprint vectors from `examples.json`, and
identity re-derivation), `SatelliteComponentDefinitionTest`,
`SatelliteKindDefinitionTest`, `SatelliteRootThreeTest` and
`SatelliteItemDataSchemaTwoTest`. `SatelliteRootThreeTest` covers:

- legacy root 2 → 3 with extra tags kept, and root 1 → 3;
- shapes that fail closed;
- worst-case record sizes;
- the over-cap legacy load;
- the save epoch.

Existing tests changed to exact new values, with no assertion removed or loosened:

- the schema pins: satellite root 3 and its epoch;
- the per-file manifest schema in `StationWorldMigrationTest`;
- the populated legacy fixture now downgrades satellite records as it already
  does station records, and expects the legacy labels;
- `ModMetadataTest` version and description;
- the operator report line.

Measured (`ARCE_V160_RECORD_BOUNDS`, 128-character IDs): satellite 1,728 B
(bound 2,048), mission 3,733 B (4,096), instance 3,167 B (4,096). The over-cap
legacy root (`ARCE_V160_OVER_CAP_LEGACY`): 8,192 missions, 3,248,301 B as root 2,
3,936,609 B after migration, inside the 6 MiB missions budget.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew test` for the satellite, migration, planetary and star-system tests (iterative development runs) | First run: 12 failures, from the literal epoch rule, the legacy fixture and the schema pins. Then three failures in the new tests: the stale-queue capacity (H2, below), and two flush-harness issues (the writer needs the stable file name and the Minecraft bootstrap). All pass after the changes above. These iterative outputs were not captured into the evidence directory; this table is their only record |
| `gradlew clean build test runData runGameTestServer --offline --no-daemon --console=plain` | Exit 0, 4m50s. **1,071 JUnit tests / 200 suites executed** (not from cache), 0 failures. **260 required GameTests** passed. DataGen wrote nothing; generated diff empty |

The build log has 15 ERROR lines and 0 FATAL. They are the intentional
failure-injection GameTests and the missing `server.properties`, the same set as
the preparation run.

## Found and scheduled, not fixed here

A claim leaves its deadline-queue entry, and `schedule` throws at 1,024 entries.
This is review finding H2, fixed in C8a. The over-cap test drains the queue with
`completeDue`, as the server does every 20 ticks.

## Not done in C7a

- No new items, blocks, assets, launch paths, scans or receivers (C7b/C7c).
- No write-policy change or budgets (C8a).
- No native server run: the native pre-start migration of a v1.5 world is in C9.
- No independent review yet: C7 is reviewed as a whole after C7c.
