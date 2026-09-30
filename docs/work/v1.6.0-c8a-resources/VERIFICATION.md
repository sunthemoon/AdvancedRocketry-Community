# V160-RES-01 (C8a-2) verification: resource tables, algorithms and `mission verify`

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`bbc0f53` (C7c). Contract: [ADR-052](../../decisions/ADR-052-RESOURCE-TABLES-AND-SEEDS.md)
revision 2, with the record bounds of
[ADR-050](../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md) §3. No
Gate, candidate or tag. C8a-1 (ADR-050 write policy, limits, budgets,
retention and operator lifecycle commands) follows separately.

## Implemented

- **Tables** (`satellite/resource`).
  - `asteroid_types` (≤ 32 KiB per file, ≤ 64 types) and `gas_harvest` (≤ 8 KiB,
    ≤ 32 tables) are decoded strictly from their raw bytes: unknown or missing
    fields, wrong JSON types, non-integral or out-of-range numbers, unknown or
    air items, duplicate ores, an ore equal to the base item, duplicate systems
    and a type `id` that differs from its file are rejected.
  - A version is the first 16 hex characters of the SHA-256 of the raw
    resource bytes, read off-thread by a `PreparableReloadListener` so the hash
    is of the exact file.
  - Cross-checks: systems are root bodies, a gas table names a gas giant, at
    most one table per body, and every type and table passes the record-fit
    check. The check encodes the worst-case instance and mission with the
    table's real item IDs and 128-character values for everything else.
  - An invalid reload keeps the last complete tables and logs one aggregated
    error (≤ 32 lines); an invalid initial load fails loading. The listener
    runs after the celestial catalog and the satellite definitions.
- **Algorithms.**
  - `SplitMix64` and `bounded(n)`.
  - `survey-v1`: candidates sorted by the full ID string, the fingerprint,
    per-instance seeds and weighted type choice.
  - `asteroid-v1` yield, truncation to `cargo × 64`, and the asteroid
    duration.
  - `gas-v1` rate, base, amount and duration.
  - All in signed 64-bit arithmetic.
- **Seeds.** `MissionSeeds` wraps one server-owned `SecureRandom`; the seeded
  mission starts that use it arrive in C8b.
- **`/arce satellite admin mission verify <id>`** (permission level 2) accepts
  a mission or instance ID. It recomputes the reward from the stored seed and
  inputs and reports `MATCH`, `VERSION_CHANGED`, `INPUTS_UNAVAILABLE` or
  `MISMATCH`. It changes nothing and writes one bounded
  `ARCE_SATELLITE_VERIFY` line (a warning for `MISMATCH`). Data missions have
  no seeded reward and report `INPUTS_UNAVAILABLE`.
- **Built-in data** (v1.6 DataGen, decoded by the runtime codec before it is
  written): four asteroid types with the legacy numbers and one hydrogen table
  for the gas giant (8 per 1,000 ticks).

## v1.6 balance decisions (data, not contract)

| Type | Weight | Mass | Richness | Richness var. | Time % | Ores (weight) |
|---|---|---|---|---|---|---|
| `small_asteroid` | 20 | 200 | 30 | 50 | 100 | iron 10, copper 10, coal 5 |
| `light_asteroid` | 15 | 200 | 20 | 50 | 100 | iron 5, gold 5, redstone 5, lapis 3 |
| `rich_asteroid` | 2 | 75 | 20 | 30 | 150 | gold 5, diamond 4, emerald 1 |
| `strange_asteroid` | 1 | 50 | 20 | 50 | 200 | nether quartz 10, nether gold 5, ancient debris 1 |

All four use mass variability 50, cobblestone as the base item and every system.

## Tests

- `ResourceAlgorithmsTest`, against the accepted vectors in
  `docs/work/v1.6.0-preparation/examples.json`:
  - the published SplitMix64 seed-0 outputs, the reference streams and
    `bounded`;
  - all four survey vectors (types, seeds, yields and fingerprints, including
    the mixed-namespace case, where `ResourceLocation.compareTo` would give the
    other order);
  - truncation, the six asteroid durations and the five gas vectors;
  - the largest possible yield stays within 17 entries and 4,096 items.
- `ResourceTablesTest`:
  - the committed built-in files decode with raw-byte versions and
    cross-check;
  - the SHA-256 prefix is pinned (`abc` → `ba7816bf8f01cfea`);
  - ten malformed variants, an unknown item, an oversized file and an
    out-of-range gas amount are rejected;
  - a non-root system, a gas table on Earth and two tables for one body are
    rejected;
  - measured `ARCE_V160_TABLE_RECORD_FIT instance=3149 mission=3716` for 17
    entries with 128-character IDs;
  - a type whose record would exceed its bound is rejected.
- `RewardVerifierTest`: every outcome for an instance, a survey (including a
  pruned and a tampered instance), an asteroid mission (the truncated yield, a
  pruned instance, a removed satellite, an inflated reward) and a gas mission
  (amount 576 for rating 10 and cargo 9, a changed version, a removed table, a
  wrong amount).

In the GameTest server the listener logs `Accepted resource tables generation 1:
4 asteroid types, 1 gas tables`.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew test` for the new resource tests (iterative development runs) | First run: 1 failure. The record-fit test's oversized type made the record encoder throw instead of reporting a size. `SatelliteRecordFit` now reports such a record as not fitting (`DOES_NOT_FIT`); no assertion changed. These iterative outputs were not captured into the evidence directory; this table is their only record |
| `gradlew runGameTestServer` (development check) | 271 required GameTests passed; the tables listener accepted the built-in data |
| `gradlew clean build test runData runGameTestServer --console=plain` | Exit 0, 4m. **1,115 JUnit tests / 209 suites executed**, 0 failures. **271 required GameTests** passed. The new generated data was staged before the run; DataGen rewrote it byte-identically (generated diff empty) |

The log has 15 ERROR lines and 0 FATAL, the same intentional set as C7.

## Repository validators

Run on the staged tree after packaging (log in `packaging/out/validation.log`,
outside the archive): the first `validate_repository.py
--require-approved-identity` run failed on a wrong ADR-052 link in this record
(44 passed, 1 failed); the link was corrected and the rerun passed (45 passed,
0 failed). `validate_v1plus_planning.py`, `validate_bootstrap_provenance.py`,
`python -m unittest tests.test_v1plus_planning` and `git diff --cached --check`
exit 0.

## Not done in C8a-2

- No mission uses the tables yet: survey, asteroid and gas starts, instances
  and delivery are C8b.
- The ADR-050 write policy, queue integrity, limits, byte budgets, retention,
  load invariants and the remaining operator commands are C8a-1.
