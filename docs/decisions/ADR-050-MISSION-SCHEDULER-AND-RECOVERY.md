# ADR-050 — Mission scheduler, lifecycle and recovery

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.6.0
slices: [V160-SCHED-01, V160-SCHED-02, V160-REC-01]
related: [ADR-010, ADR-040, ADR-049, ADR-051, ADR-052]
```

## Context

ADR-010 froze one Overworld-owned `SatelliteMissionSavedData` with a monotonic
logical game-time clock, a deadline queue drained every 20 ticks (≤ 32
completions, ≤ 64 inspections), exact-once claims and replayed discovery. v1.6
adds survey, asteroid and gas missions, instances, reward snapshots and
cross-store delivery, and targets 500 (reference) and 1,000 (stress) missions.

The audit found that finished missions are never pruned: after 8,192 retained
records every launch and start returns `CAPACITY_REACHED` for good. The 4 MiB
root bound is checked only when saving, where an overflow throws, and the
existing per-type caps do not guarantee it.

## Decision

### 1. One registry, one clock

Satellites, missions, asteroid instances and research accounts stay in the one
registry so that every satellite↔mission↔instance transition is a single
in-memory mutation of one SavedData. There is no second mission file. The clock
remains ADR-010's monotonic logical game time. It advances with Overworld game
time while the server runs, never with wall time, and never moves backwards.
Downtime does not advance it. An operator's forward time jump advances it, and
the budgets in §4 then spread the resulting completions.

### 2. Mission records (schema 2)

Common fields: `mission_id`, `satellite_id`, `owner_id`, `definition_id`, `kind`
(`data`, `survey`, `asteroid`, `gas`), optional `target_body`, optional
`instance_id`, `seed` (64-bit, server-generated, ADR-052), `started_at`,
`completes_at`, `start_epoch` (ADR-051 save epoch), `status`, optional
`ready_at` and `resolved_at`, `reward_version` (≤ 200 chars), optional quarantine
{`reason` code, `previous_status`}. Kind payload:

- `data`: schema-1 fields (`research_yield`, `discovery_cost`, `discovery_required`);
- `survey`: 1..4 instance IDs created at start (ADR-051);
- `asteroid` / `gas`: reward snapshot (1..17 entries of plain item ID and count,
  total ≤ 1,728 items), `bound_terminal` (terminal UUID; Level key and position
  for display only) and `acknowledged` (ADR-051).

Each encoded mission record is ≤ 2 KiB. Schema-1 missions migrate to
`kind: data`, `seed: 0`, `reward_version: "legacy-data-v1"`, keeping every other
value.

### 3. Status machine

| From | Event | To | Side effects in the same mutation |
|---|---|---|---|
| — | start (idempotent by `mission_id`) | ACTIVE | satellite bound; asteroid instance ALLOCATED; survey instances PENDING |
| ACTIVE | deadline reached (scheduler only) | READY | none; completion never delivers rewards |
| READY | `data` claim | CLAIM_PENDING_DISCOVERY / CLAIMED | ADR-010/037 unchanged |
| READY | `survey` claim | CLAIMED | instances PENDING → AVAILABLE; satellite released |
| READY | `asteroid`/`gas` claim at the bound terminal | CLAIMED | materialization per ADR-051; instance DEPLETED; satellite released |
| ACTIVE, READY | cancel (owner at a terminal, or operator) | CANCELLED | satellite released; survey instances deleted; asteroid instance → AVAILABLE or EXPIRED; no reward, no refund |
| any unfinished | invariant failure (§8) | QUARANTINED | nothing else changes |
| QUARANTINED | operator release / cancel | previous status / CANCELLED | release only if §8 invariants now hold |

Replays of start, claim or cancel return the existing result with
`IDEMPOTENT`. Only the scheduler moves ACTIVE to READY. No client packet names a
status, time, seed or reward.

### 4. Scheduler pass

Every 20 ticks, in order, with one `setDirty` if anything changed:

1. advance the clock;
2. drain due deadlines: ≤ 32 completions, ≤ 64 queue inspections (unchanged);
3. expire ≤ 16 asteroid instances from a second deadline queue (ADR-051);
4. prune ≤ 64 finished records (§6).

Both queues are rebuilt once on load from the bounded record sets. A backlog is
never drained faster: 1,024 simultaneously due missions complete within 32 passes
(640 ticks). Diagnostics report queue sizes, backlog and maximum completion lag.
No pass reads or loads a chunk, and missions hold **zero** chunk tickets.
Unfinished counts are maintained incrementally; no operation scans every record.

### 5. Limits and rate

| Limit | Value | Change |
|---|---|---|
| Unfinished missions, global | 1,024 | unchanged |
| Unfinished missions per owner | 64 | new |
| Satellites, global / per owner | 4,096 / 256 | per-owner new (ADR-049) |
| Missions of every status | 3,072 | replaces 8,192 |
| Asteroid instances, global / per owner | 2,048 / 16 | new (ADR-051) |
| Mission start/claim/cancel intents per player | 1 per 10 ticks | new |

Refusals are explicit codes (`CAPACITY_REACHED`, `OWNER_LIMIT`, `RATE_LIMITED`,
`STORAGE_BUDGET`), never silent drops.

### 6. Storage budget and retention

The registry keeps the exact encoded size of every record and refuses a mutation
that would exceed a section budget (`STORAGE_BUDGET`) before applying it, so a
save cannot overflow:

| Section | Records × record bound | Budget |
|---|---|---|
| Satellites | 4,096 × 1 KiB | 4 MiB |
| Missions (all statuses) | 3,072 × 2 KiB | 6 MiB |
| Instances | 2,048 × 1 KiB | 2 MiB |
| Accounts + clock + counters | 4,096 × 256 B | 1 MiB |

The root bound rises from 4 MiB to **16 MiB** (13 MiB of sections plus framing).
This raises a safety limit on purpose: the old per-type caps did not keep the
root under 4 MiB, and the overflow was only detected by an exception at save
time. The 500/1,000-task performance report (C9) records actual root size and
save time.

Admission happens only at start: a start needs fewer than 3,072 missions in
total, fewer than 1,024 unfinished, and room in every budget, including the
instances a survey creates. Claims and cancels change a record in place, never
the count, so retention can never block them.

Retention: while more than 1,536 finished records exist, the oldest eligible
records by `resolved_at` are pruned, ≤ 64 per pass. `data`, `survey` and
CANCELLED records are eligible once 24,000 logical ticks old. CLAIMED
`asteroid`/`gas` records are eligible only when `acknowledged` (ADR-051) and
24,000 ticks old. QUARANTINED records are never pruned automatically. If nothing
is eligible, new starts return `CAPACITY_REACHED` until records age.

### 7. Cancellation and timeouts

- Owners cancel ACTIVE or READY missions at any terminal with the matching chip;
  operators can cancel by ID. Cancellation never pays a reward and never refunds
  consumed inputs.
- READY missions never expire; their rewards are kept until claimed or cancelled.
- AVAILABLE asteroid instances expire after their TTL (ADR-051).
- A resource mission whose bound terminal is missing stays READY and reports
  `TERMINAL_MISSING`; only an operator can rebind it (ADR-051).
- Satellites in `RECOVERY_REQUIRED` cannot start missions until recovered.

### 8. Restart recovery and invariants

On load, after the migration below, one bounded pass checks:

1. every unfinished mission's satellite exists, has the same owner, and names it
   as `current_mission`;
2. every satellite's `current_mission` is an unfinished mission;
3. ALLOCATED/PENDING instances and their mission reference each other one-to-one;
4. times, seeds, reward sizes and kind payloads are within bounds.

A failing mission becomes QUARANTINED with a reason. A satellite named by no
mission becomes `RECOVERY_REQUIRED`, and an orphan instance becomes QUARANTINED.
Nothing is deleted, completed or paid automatically. Operator commands (permission
level 2): `mission inspect|release|cancel <id>` and `mission rebind <id>
<terminal>` (ADR-051). Each writes one bounded audit log line.

A future root or record schema, an unknown kind or an over-bound file blocks the
registry and preserves the original payload (ADR-010 behaviour unchanged).

### 9. Migration

Root 2 → 3 migrates satellites 1 → 2 (ADR-049) and missions 1 → 2 (§2, with
`start_epoch: 0`), and adds an empty instance section, the save epoch (starting
at 1) and counters. It runs in the pre-server-start
`WorldDataMigrationService`, like ADR-040: validate first, back up the original
file once, stage, replace atomically, and never fall back to a non-atomic move.
Repeated startup of a root-3 file does not migrate again or create another
backup. A v1.5 host refuses root 3 as a future schema and keeps the file.

### 10. Definition changes

Missions and satellites keep their snapshots. Removing or changing a definition,
component, asteroid type or gas table refuses only new assemblies, launches and
starts (`DEFINITION_UNAVAILABLE`). In-flight missions complete, claim and cancel
with their snapshot, and survey instances keep their generated contents.

## Consequences

- One file carries all mission state; its worst-case size and save cost grow, and
  the budgets above are measured, not assumed.
- Fixes the latent permanent `CAPACITY_REACHED` of the existing registry.
- Quarantine prefers operator diagnosis over automatic repair.

## Verification

- A0: state machine and every refusal; replays; queue drain and expiry budgets;
  backlog (1,024 due at once takes exactly 32 passes); retention and pruning;
  storage-budget admission; incremental counters; schema-1 → 2 migration and
  future-schema blocking; invariant pass and quarantine; definition removal.
- A1: start → complete → claim/cancel per kind; restart mid-mission; zero chunk
  tickets after 1,000 synthetic missions.
- S1/S2 (C9): 100+ active missions across restart; forced stop before and after
  a claim; native pre-start migration of a v1.5 world with one backup.
- Performance (C9): 500 and 1,000 synthetic missions — pass time, root size,
  save time; the budget comes from `docs/17`.
