# ADR-056 — Railgun cargo launcher and targeting (safe scope)

```yaml
status: PROPOSED
revision: 3
date: 2026-10-01
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.7.0
slices: [V170-RAIL-01]
development_dependency: ADR-016, ADR-043, ADR-054
```

## Context

The legacy railgun is an **item cargo launcher**: a linker stores another
railgun's coordinates and integer dimension; when powered, the railgun moves one
stack per tick into the destination's output hatch and clears its own slot in
the same tick, across two independently saved chunks. Every railgun permanently
force-loads its own chunk, and the destination world must already be loaded
([audit §2](../work/v1.7.0-legacy-audit.md)). The version plan asks for "a
railgun or targeting system within a safe scope".

## Decision

### 1. Safe scope

- The railgun moves **items only**, between two railgun endpoints of the same
  owner, through the ADR-054 §11 transit ledger.
- It never damages, moves or spawns entities, never touches blocks, never
  targets a position, a player or a chunk. **Targeting** means selecting one of
  the owner's own railgun endpoints (ADR-054 §9); there is no other target.
- An orbital strike or any weaponised use is **rejected** for v1.7 (§7).

### 2. Structure and state

- A structure-only multiblock (ADR-054 §2.1): controller
  `advancedrocketrycommunity:railgun`, pattern `machine_patterns/railgun.json`,
  at most 5 × 9 × 5, four rotations, no mirror; community-authored blocks.
- The controller is a `railgun` endpoint (ADR-054 §9). Its root holds:
  - an input buffer of 4 slots (insertable by automation, the payload source);
  - a receive buffer of 9 slots (extractable by automation, the claim target);
  - an energy buffer of 1,000,000 FE, input ≤ 50,000 FE per tick;
  - the ADR-054 §11 source state (`next_seq`, outbox ≤ 4) and receipts (≤ 64);
  - settings: selected destination, `auto` flag, redstone mode (`IGNORED`,
    `ON`, `INVERTED`, default `IGNORED`), minimum stack size 1..64 (default 1).

### 3. Route rule

A destination is eligible when:

1. it is an `ACTIVE` `railgun` endpoint other than the source (`NO_TARGET`);
2. it has the source's owner, unless an operator selects it (`TARGET_FOREIGN`);
3. both endpoints' bodies are available (ADR-054 §9) and in the **same star
   system** (ADR-043 root) (`ROUTE_OUT_OF_SYSTEM`, `BODY_UNAVAILABLE`).

The rule is evaluated at selection and again at escrow, from the index and the
live catalog; it needs no chunk of the destination.

| Class | When | Cost (FE) | Travel (ticks) |
|---|---|---|---|
| `LOCAL` | same Level and same body | `min(25,000 + 10 × d, 250,000)` | `min(max(20 + d / 64, 20), 200)` |
| `ORBITAL` | otherwise, same system | `250,000` | `600` |

`d` is the integer horizontal distance between the endpoints, `floor(sqrt(dx² +
dz²))`, computed in 64-bit integers. Costs are multiplied by
`endgame.railgun.energyPercent / 100` (10..400, default 100), so the largest
cost, 1,000,000 FE, still fits the buffer (review R1-L11). The class, cost and
travel are fixed at escrow and stored in the outbox entry. The legacy
per-tick requirement (up to 100,000 per tick while sending one stack per tick)
becomes a per-launch cost; this rebalance is deliberate.

### 4. Launch

A launch is triggered by a `launch` intent (`OPERATE`), or automatically when
`auto` is set and the redstone mode is satisfied. At most one launch per railgun
per 20 ticks, and at most `endgame.railgun.launchesPerTick` (≤ 4) on the server.

The payload is the whole stack in the first input slot, in slot order, whose
count is at least the minimum stack size. The launch is the ADR-054 §11 escrow:
payload and cost leave the railgun in one tick, the record is registered after
the entry is persisted, and the travel time starts at registration. Refusals
before escrow change nothing: `SYSTEM_DISABLED`, `NOT_AUTHORIZED`, `NO_PAYLOAD`,
`NO_TARGET`, `TARGET_FOREIGN`, `BODY_UNAVAILABLE`, `ROUTE_OUT_OF_SYSTEM`,
`INSUFFICIENT_ENERGY`, `OUTBOX_FULL`, `TRANSIT_LIMIT`, `ROOT_FULL`.

### 5. Arrival

The destination claims arrived records into its receive buffer during its
reconciliation pass whenever its chunk is loaded and the whole payload fits
(ADR-054 §11). An unloaded destination simply leaves the record `ARRIVED`; the
cargo waits without loading anything. A destination that was removed leaves the
record `ARRIVED` with `DESTINATION_MISSING`; only an operator redirect (for
example back to the source) or purge resolves it.

### 6. Menus, visuals and audit

- Menu (ADR-054 §4 device view): buffers, energy, destination (owner and
  operators: label, body, Level,
  position, class, cost and travel), `auto`, redstone mode, minimum stack size,
  outbox and in-transit counts, receipts, last code. Buttons: destination
  previous/next, launch, auto toggle, redstone mode, minimum stack size ±1/±16.
- Visuals: a launch flash and one upward streak at the source; an arrival flash
  at the destination when a claim happens while it is loaded. At most 8
  concurrent railgun effects per client. No entity is spawned (the legacy
  `EntityItemAbducted` copy is not revived).
- Audit: escrow, registration, arrival, claim, reconciliation results and
  refusals, as ADR-054 §11 and §13.

### 7. Threat model

| Threat | Control |
|---|---|
| Weaponised use, entity or block damage | None exists; targets are owned endpoints only |
| Duplication or loss across Levels | ADR-054 §11 escrow, persistence signals, epochs, receipts and total reconciliation; residuals as listed there |
| Chunk loading | None; cargo waits in the ledger |
| Sending to others' bases | Owner-only destinations; operators may select any |
| Ledger growth | 256 records, 32 per owner, 4 outbox entries per railgun, root admission (ADR-054 §10) |
| Automation spam | 20-tick cadence per railgun, ≤ 4 launches per tick server-wide |
| Contention on one destination | Claims are serialized on the server thread; whole-payload room and 64 receipt slots per destination; waiting records stay `ARRIVED` |

## Deferred and rejected

Recorded in `PORTING_MATRIX.md`:

- Orbital strike, weapon or entity launching: **rejected** (safe scope, plan §4).
- Linker items with raw coordinates: **rejected**; endpoint selection replaces
  them.
- Interstellar cargo: **deferred**; cargo stays inside one star system, like
  rocket routes (ADR-043).

## Verification

- A0: route rule and classes (reference vectors), cost and travel arithmetic at
  bounds, payload selection, refusal order, cadence and per-tick cap.
- A1: launch and claim between two railguns in one Level and between a planet
  and a station; destination unloaded then loaded; destination removed then
  operator redirect; two sources into one destination; foreign destination
  refused; out-of-system destination refused; disabled switch keeps arrivals
  claimable; zero tickets.
- S2 (C13): ADR-054 §11 crash cuts with a railgun pair.

## Rollback

Disable by config; arrivals still complete. Removing the code loses escrowed and
in-transit cargo (ADR-054 §14).

## Review history

- Revision 1 (`10e3d2d`): independent contract review round 1 asked for changes.
- Revision 2 answers it, together with root findings S1–S4 and finding F02 of the
  external v1.3–v1.6 deep-test report, one commit per finding; see
  [review-01-dispositions](../work/v1.7.0-preparation/review-01-dispositions.md).
- Revision 3: accepted by review round 2; only cross-references to ADR-054's
  round-2 answers changed; see
  [review-02-dispositions](../work/v1.7.0-preparation/review-02-dispositions.md).
