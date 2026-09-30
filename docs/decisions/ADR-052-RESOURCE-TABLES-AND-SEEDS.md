# ADR-052 — Resource tables, seeds and versioned rewards

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.6.0
slices: [V160-RES-01]
algorithm_versions: [survey-v1, asteroid-v1, gas-v1]
related: [ADR-029, ADR-043, ADR-049, ADR-050, ADR-051]
reference_vectors: docs/work/v1.6.0-preparation/examples.json
```

## Context

Legacy asteroid rewards use a static shared `Random`, float variability,
`Math.random()` bonus rolls and a shared base stack that is mutated. The gas
reward is a constant 64,000 mB per tank (see the
[audit](../work/v1.6.0-legacy-audit.md)). v1.6 needs data-driven resource tables,
seeds the client cannot influence, and rewards that can be reproduced and
audited from persisted inputs. Removing or changing a table must not change a
mission already in flight.

## Decision

### 1. Asteroid types

`data/<namespace>/asteroid_types/<path>.json`, schema 1, strict (unknown fields
rejected, ≤ 32 KiB per file, ≤ 64 types). `id` must equal the file's resource ID.

| Field | Bound |
|---|---|
| `weight` | 1..1,000 |
| `systems` | 0..16 unique root body IDs; empty means every system |
| `mass` | 1..4,096 items |
| `mass_variability_pct`, `richness_pct`, `richness_variability_pct` | 0..100 each |
| `base_item` | existing plain item, not air |
| `ores` | 1..16 entries {`item` existing plain item, `weight` 1..1,000}; unique; none equals `base_item` |
| `time_multiplier_pct` | 10..1,000 |

### 2. Gas tables

`data/<namespace>/gas_harvest/<path>.json`, schema 1, strict (≤ 8 KiB, ≤ 32
tables). `body` must be a catalog body with `gas_giant: true`; at most one table
per body. `products`: 1..8 entries {`item` existing plain item,
`amount_per_1000_ticks` 1..64}, unique items.

Both kinds of table reload together with the celestial catalog and item registry.
An invalid reload keeps the last complete tables and logs one aggregated error
(≤ 32 lines); an invalid initial load fails loading, like ADR-029. A table's
**version** is the first 16 hex characters of the SHA-256 of its raw resource bytes.

### 3. Seeds

- A mission seed is a 64-bit value from a server-owned `SecureRandom`, drawn at
  start and persisted (ADR-050). It is never derived from a client packet, a
  position, a player name or the time, and a replayed start keeps its first seed.
- `SplitMix64(s)`: `s += 0x9E3779B97F4A7C15; z = s; z = (z ^ (z >>> 30)) *
  0xBF58476D1CE4E5B9; z = (z ^ (z >>> 27)) * 0x94D049BB133111EB; return z ^ (z >>> 31)`
  with 64-bit wrapping arithmetic.
- `bounded(n)` for 1 ≤ n ≤ 2³¹: `(next() >>> 1) % n`.
- Domain constants (ASCII): `SURVEY01 = 0x5355525645593031`,
  `ASTTYPE1 = 0x4153545459504531`, `ASTYIELD = 0x4153545949454C44`.

### 4. `survey-v1`: instance generation

For a survey with seed `m` over system `S`: the candidate types are those whose
`systems` is empty or contains `S`, sorted by ID string (ascending, ordinal). With
the stream `A = SplitMix64(m ^ SURVEY01)`, instance `i` (0-based) has
`seed_i = A.next()`. Its type is picked with `T = SplitMix64(seed_i ^ ASTTYPE1)`:
`r = T.bounded(Σ weight)`, taking the first type whose cumulative weight
exceeds `r`. Its yield comes from `asteroid-v1` with `seed_i`.

### 5. `asteroid-v1`: yield

With `Y = SplitMix64(seed ^ ASTYIELD)` and integer arithmetic on non-negative values:

```text
dm   = Y.bounded(mass_variability_pct + 1) - mass_variability_pct / 2
mass = clamp(mass * (100 + dm) / 100, 1, 4096)
dr   = Y.bounded(richness_variability_pct + 1) - richness_variability_pct / 2
rich = clamp(richness_pct + dr, 0, 100)
ores = mass * rich / 100
repeat ores times: r = Y.bounded(Σ ore weight); count the first ore whose cumulative weight exceeds r
yield = [(ore item, count) for ores in file order if count > 0] + [(base_item, mass - ores) if > 0]
```

A yield therefore holds at most 4,096 items in at most 17 entries, from at most
4,098 draws.

Truncation to a craft: capacity = `cargo × 64` items; entries are taken in yield
order, `take = min(count, remaining)`, with a partial last entry allowed.
Duration:

```text
d = ceil(12_000 × time_multiplier_pct × config_pct × 10 / (100 × 100 × rating))
duration = clamp(d, 200, 72_000)
```

`config_pct` is the server config `asteroid_mission_time_percent` (10..1,000,
default 100). With rating 10 and both percentages at 100, a mission takes
12,000 ticks.

### 6. `gas-v1`: amount and duration

```text
capacity  = cargo × 64
rate      = max(1, amount_per_1000_ticks × rating / 10)      # items per 1,000 ticks
base      = clamp(ceil(capacity × 1000 / rate), 200, 72_000)
amount    = min(capacity, rate × base / 1000)
duration  = clamp(ceil(base × config_pct / 100), 200, 72_000) # gas_mission_time_percent
```

`gas-v1` uses no random draw; the mission still records its seed.

### 7. Versioned, auditable rewards

- Asteroid instances store `asteroid_type`, `table_version`, `seed` and the full
  yield at generation. Asteroid and gas missions store the reward snapshot at start.
- `reward_version`: `survey-v1`, `asteroid-v1/<type id>/<table version>` or
  `gas-v1/<table id>/<table version>`; `legacy-data-v1` for migrated data missions.
- Operator `mission verify <id>` recomputes an instance or mission reward from its
  stored seed and inputs. It reports `MATCH`, `VERSION_CHANGED` (the current table
  hash differs, so it cannot recompute) or `MISMATCH` (a defect, logged once).
  Verification never changes state.
- A new algorithm version gets a new name; `v1` results are never reinterpreted.

### 8. Built-in data

Four asteroid types use the legacy defaults as numeric reference: weights 20, 15,
2 and 1; masses 200, 200, 75 and 50; richness 30, 20, 20 and 20; variability 50.
The base item is cobblestone. LibVulpes ores are replaced by existing vanilla or
host items; no XML text is copied. One gas table maps the gas giant to hydrogen
canisters (`amount_per_1000_ticks` 8). Rebalancing later needs only a data change,
which gives a new table version.

## Consequences

- Rewards are pure functions of persisted inputs, so tests can pin them with
  vectors and operators can audit them.
- Players can see an instance's exact yield before they mine it. The legacy
  "uncertainty" display is not revived.
- Rewards come from nothing, but they are bounded per mission by cargo and
  duration and per owner by the instance limits in ADR-051.

## Verification

- A0: SplitMix64 against the published seed-0 outputs; `bounded`; the
  `examples.json` vectors for type selection, yield, truncation, asteroid and gas
  durations; table codec bounds; reload keeps the last complete table; version
  hashing; `mission verify` outcomes.
- A1: a survey generates instances identical to the vectors; the reward snapshot
  is unchanged after a table reload; a new seed is not accepted from the client.
