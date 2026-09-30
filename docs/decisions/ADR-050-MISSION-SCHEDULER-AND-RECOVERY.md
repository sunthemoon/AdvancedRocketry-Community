# ADR-050 — Mission scheduler, lifecycle and recovery

```yaml
status: PROPOSED
revision: 2
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.6.0
slices: [V160-SCHED-01, V160-SCHED-02, V160-REC-01]
related: [ADR-010, ADR-038, ADR-040, ADR-049, ADR-051, ADR-052]
```

Revision 2 answers the first independent review (H1–H4, H7, M4–M8, M15, L1, L2,
L13, L14); see the [preparation evidence](../work/v1.6.0-preparation/VERIFICATION.md).

## Context

ADR-010 froze one Overworld-owned `SatelliteMissionSavedData` with a monotonic
logical game-time clock, a deadline queue drained every 20 ticks (≤ 32
completions, ≤ 64 inspections), exact-once claims and replayed discovery. v1.6
adds survey, asteroid and gas missions, instances, reward snapshots and
cross-store delivery, and targets 500 (reference) and 1,000 (stress) missions.

The current code has these properties that matter here:

- `SatelliteManager` calls `flush(server)` after every launch, start, claim,
  cancel, discovery replay and every scheduler pass that completes a mission.
  `AtomicSavedData.flush` serializes the whole root, gzips it, fsyncs, reads it
  back, decodes and compares it, then moves it atomically, all on the main thread.
  ADR-038's discovery ordering between this registry and `CelestialSavedData`
  relies on those barriers.
- Finished missions are never pruned, so the registry refuses every new launch
  and start with `CAPACITY_REACHED` once 8,192 records exist.
- The 4 MiB root bound is checked at save time, where `AtomicSavedData` catches
  the exception, logs it once and keeps the data dirty. Persistence then stops.
- `cancel` leaves the deadline-queue entry. `schedule` throws at 1,024 queued
  entries after the maps have already changed.
- `finishRestore` requires every mission, finished or not, to reference an
  existing satellite.

## Decision

### 1. One registry, one clock

Satellites, missions, asteroid instances and research accounts stay in the one
registry, so every satellite↔mission↔instance transition is a single in-memory
mutation of one SavedData. The clock remains ADR-010's monotonic logical game
time. It advances with Overworld game time, one tick per server tick, never with
wall time, and never backwards; downtime does not advance it. `/time` changes
only the day time. If game time itself jumps forward (a `level.dat` edit or a
mod), the jump counts as elapsed time and §5 spreads the resulting completions.

### 2. Write policy and save epoch

- **Barrier flushes** (the existing `flush(server)`) remain only on the existing
  `data` paths that ADR-010/038 order across two authorities: `data` launch,
  start, claim and cancel, and discovery replay. Their behaviour is unchanged.
- **Every other mutation only marks the registry dirty.** That covers scheduler
  completions of every kind (a change from today; a lost completion is only
  repeated after a restart), all v1.6-kind operations, instance expiry, pruning,
  acknowledgements, receipt reconciliation, link changes, scan payments,
  decommission and quarantine. A coalesced flush runs at most once per 100 ticks
  while the registry is dirty; autosave and shutdown persist as usual.
- **Save epoch.** The registry holds `save_epoch` E, starting at 1. A flush or
  save writes E + 1 into the file; when the write returns without error,
  E becomes E + 1. On load, E is the value read from the file. Each mission
  stores `start_epoch` = E at its start. A mission is therefore present in every
  file whose epoch is greater than its `start_epoch` (used by ADR-051).
- **Cost.** C9 measures barrier and coalesced flush times at the 500- and
  1,000-mission roots and at a synthetic worst-case root. The docs/17 P95/P99
  MSPT budgets apply. If they are exceeded, the version stays blocked until a
  follow-up ADR moves the file write to one writer thread. The root would still
  be serialized on the main thread, and the epoch would advance on completion.
  This is a gate, not a waiver.

### 3. Records and size bounds

Mission records (schema 2), common fields: `mission_id`, `satellite_id`,
`owner_id`, `definition_id`, `kind` (`data`, `survey`, `asteroid`, `gas`),
optional `target_body`, optional `instance_id`, `seed` (ADR-052),
`started_at`, `completes_at`, `start_epoch`, `status`, optional `ready_at` and
`resolved_at`, `reward_version` (≤ 200 chars), optional quarantine {`reason`,
`previous_status`}. Kind payload:

- `data`: `research_yield`, `discovery_cost`, `discovery_required` (schema 1);
- `survey`: 1..4 instance IDs and `candidate_fingerprint` (ADR-052);
- `asteroid` / `gas`: reward snapshot (1..17 entries, item ID and count, total
  ≤ 1,728 items), `bound_terminal` (terminal UUID, plus Level key and position
  for display), `acknowledged`, optional `ack_epoch` (ADR-051).

Record bounds are derived from the field maxima with 128-character IDs:
**mission ≤ 4 KiB** (worst case about 3.7 KiB), **instance ≤ 4 KiB** (about
3.1 KiB), **satellite ≤ 2 KiB** (about 1.7 KiB). An asteroid type, gas table,
component or definition whose worst-case record would exceed a bound is
rejected when it loads. Bounds are enforced when a record is created or changed,
never only when encoding. A save therefore cannot fail on size.

Schema-1 missions migrate to `kind: data`, `seed: 0`, `start_epoch: 0`,
`reward_version: "legacy-data-v1"`, keeping every other value.

### 4. Status machine

| From | Event | To | Side effects in the same mutation |
|---|---|---|---|
| — | start (idempotent by `mission_id`) | ACTIVE | satellite bound; asteroid instance ALLOCATED; survey instances PENDING |
| ACTIVE | deadline reached: scheduler pass, or the existing lazy completion inside a `data` claim | READY | none; completion never delivers rewards |
| ACTIVE, READY | ADR-051 reconciliation finds a receipt | CLAIMED | the claim's side effects, no items |
| READY | `data` claim | CLAIM_PENDING_DISCOVERY / CLAIMED | ADR-010/037/038 unchanged |
| READY | `survey` claim | CLAIMED | instances PENDING → AVAILABLE; satellite released |
| READY | `asteroid`/`gas` claim at the bound terminal (ADR-051) | CLAIMED | reward delivered; instance DEPLETED; satellite released |
| ACTIVE, READY | cancel (§8) | CANCELLED | satellite released; no reward, no refund; instance per §8 |
| unfinished | invariant failure (§9) | QUARANTINED | queue entries removed; satellite binding kept |
| QUARANTINED | operator release / cancel | previous status / CANCELLED | release only if §9 now holds; cancel follows §8 |

Replays of start, claim or cancel return the existing result with `IDEMPOTENT`.
No client packet names a status, time, seed or reward. QUARANTINED counts as
unfinished for every limit and invariant.

### 5. Scheduler pass and queues

Every 20 ticks, in order: advance the clock; drain deadlines (≤ 32 completions,
≤ 64 inspections); expire instances (≤ 16 expiries, ≤ 32 inspections); prune
(≤ 64 removals, ≤ 128 inspections). All three queues are ordered by the time an
entry becomes actionable. An entry that is inspected but not yet eligible goes
back into the queue at its next eligible time.

Queue integrity: the entry is removed when its mission is cancelled,
quarantined or claimed before its deadline. Capacity is checked before any map
mutation, and a start that cannot be scheduled leaves no change. A queue never
holds more entries than there are records it serves. Queues are rebuilt once on
load. A backlog is never drained faster than the budget: 1,024 simultaneously
due missions complete in 32 passes (640 ticks). Diagnostics report queue sizes,
backlog and maximum completion lag. No pass reads or loads a chunk, missions hold
**zero** chunk tickets, and counters are maintained incrementally, with no full
scan per operation.

### 6. Limits

Server config values, bounded by these maxima, which are also the defaults
(docs/17 §5 requires configurable hard limits):

| Limit | Maximum/default | Note |
|---|---|---|
| Unfinished missions, global | 1,024 | unchanged |
| Unfinished missions per owner | 64 | new |
| Finished missions retained per owner | 128 | new; prunes that owner's oldest eligible |
| Missions of every status | 3,072 | replaces 8,192 for admission only (§10) |
| Satellites, global / per owner | 4,096 / 256 | per-owner is new |
| Asteroid instances, global / per owner (live) | 2,048 / 16 | ADR-051 |
| Start, claim and cancel intents per player | 1 per 10 ticks | new |

Refusals are explicit codes (`CAPACITY_REACHED`, `OWNER_LIMIT`, `RATE_LIMITED`,
`STORAGE_BUDGET`), never silent drops. Per-owner limits apply at admission only.

### 7. Storage budget and retention

Byte budgets per section: satellites 4 MiB, missions 6 MiB, instances 2 MiB,
accounts, clock and counters 1 MiB. Count limits (§6) apply as well. When a record
is admitted, the registry reserves the largest size that record can reach during
its lifecycle: the claim, acknowledgement and quarantine fields. Later
transitions therefore never fail with `STORAGE_BUDGET`. A server with very long
IDs reaches the byte budget before the count limits. The root bound rises from
4 MiB to **16 MiB** (13 MiB of sections plus framing). The old bound was not
enforced before mutation and did not match the count caps.

Retention: a finished record becomes eligible for pruning 1,200 logical ticks
after `resolved_at` (the idempotent-replay window). CLAIMED `asteroid`/`gas`
records additionally need a durable acknowledgement (`ack_epoch` < E,
ADR-051). Pruning runs while more than 1,536 finished records exist globally, or
while an owner holds more than 128. QUARANTINED records are never pruned
automatically. Sustained throughput is at least 2,048 finished records per 1,200
ticks. An operator may `mission purge <id>` a CLAIMED record that is not
acknowledged because its terminal is gone; the audit line says that the reward
may be lost if that terminal never materialized it.

### 8. Cancellation and timeouts

- `data` and `survey` missions: the owner at any terminal with the matching chip,
  or an operator by ID. A survey cancel deletes its PENDING instances.
- `asteroid` and `gas` missions: the owner only at the bound terminal, after that
  terminal's reconciliation (ADR-051). An operator cancel by ID, or the cancel of
  a QUARANTINED resource mission, moves an allocated instance to **QUARANTINED**,
  never to AVAILABLE, and writes an audit line. The operator can later
  `instance release <id>` after checking the terminal. An owner cancel after
  reconciliation returns the instance to AVAILABLE if it has not expired.
- Cancellation never pays a reward and never refunds consumed inputs.
- READY missions never expire. AVAILABLE instances expire after their TTL
  (ADR-051). A resource mission whose bound terminal is missing stays READY with
  `TERMINAL_MISSING` (ADR-051).
- Satellites in `RECOVERY_REQUIRED` start nothing until an operator runs
  `satellite recover <id>`.

### 9. Restart recovery and invariants

One bounded pass on load checks:

1. every ACTIVE, READY, CLAIM_PENDING_DISCOVERY or QUARANTINED mission names a
   satellite that exists, has the same owner and has this mission as
   `current_mission`. Otherwise the mission becomes QUARANTINED
   (`MISSING_SATELLITE`) and the satellite is not changed;
2. a satellite's `current_mission`, when present, names an unfinished or
   QUARANTINED mission. Otherwise the satellite becomes `RECOVERY_REQUIRED`, with
   its `current_mission` cleared;
3. ALLOCATED and PENDING instances and their mission reference each other one to
   one. Otherwise the instance becomes QUARANTINED;
4. times, seeds, sizes and kind payloads are within bounds.

Finished missions and instances may reference decommissioned satellites or pruned
missions. The current restore rule is relaxed accordingly. Nothing is deleted,
completed or paid automatically. A future root or record schema, an **unknown
kind** or an over-bound file blocks the whole registry and preserves the original
payload (ADR-010 behaviour; ADR-049 uses the same rule).

Operator commands (permission level 2), each writing one bounded audit line:
`mission inspect|verify|release|cancel|purge|rebind`, `satellite recover`,
`instance inspect|release`.

### 10. Migration

Root 2 → 3 migrates satellites 1 → 2 (ADR-049) and missions 1 → 2 (§3). It adds
the instance section, `save_epoch` (1) and the counters. **All root-3 record
shapes are frozen here and are implemented in full by C7**, including sections
that C8 fills later; no root 4 is planned for v1.6. It runs in the pre-server-start
`WorldDataMigrationService` as in ADR-040: validate first, back up once, stage,
replace atomically, never fall back to a non-atomic move. The service keeps at
most five backups, so every v1.6 upgrade adds one. Repeated startup of a root-3
file does not migrate again.

A legacy root may exceed the count limits (up to 8,192 missions, owners above the
per-owner limits). It loads, is migrated unchanged in count, and admission
refuses new starts until retention has drained it. The byte budgets hold, because
a v1.5 root is at most 4 MiB. A v1.5 host refuses root 3 in its pre-start
validation, which blocks the whole world's startup. The recovery is the
pre-upgrade backup.

### 11. Definition and celestial changes

Satellites keep their blueprint and kind-parameter snapshots (ADR-049), and
missions and instances keep theirs. Removing or changing a definition,
component, asteroid type or gas table refuses only new assemblies, launches and
starts (`DEFINITION_UNAVAILABLE`). If a satellite's orbit body is removed, scans
return `BODY_UNAVAILABLE`, solar output is 0, and asteroid and gas starts are
refused. In-flight missions are unaffected. A craft's system is recomputed from
the current tree at each start; instances keep the system they recorded.

## Consequences

- New v1.6 paths add no barrier flush; the existing discovery barriers stay.
- The registry cannot silently stop saving because of size, and it cannot lock at
  a retained-record count.
- Quarantine prefers operator diagnosis to automatic repair.

## Verification

- A0: status machine, refusals and replays; queue removal on cancel, quarantine
  and early claim; start/cancel churn beyond 1,024 cycles; atomic schedule
  failure; the backlog of 1,024 in 32 passes; expiry and pruning inspection
  budgets; per-owner retention; lifecycle-size reservation; worst-case record
  encode/decode at every bound; save-epoch semantics across save and load;
  schema 1 → 2 migration; an over-cap legacy root; future-schema and unknown-kind
  blocking; invariants and quarantine; definition and body removal.
- A1: start → complete → claim/cancel for each kind; two players settling in the
  same tick; hours of logical time with the owner offline (bounded completions,
  no extra reward); zero chunk tickets after 1,000 synthetic missions.
- S1/S2 (C9): 100+ active missions across restart; forced stops around claims;
  native pre-start migration of a v1.5 world with one backup, and of a fixture
  holding 8,192 missions and an owner with 300 satellites.
- Performance (C9): 500 and 1,000 missions — pass time, root size, and barrier
  and coalesced flush times against docs/17.
