# ADR-044 — Station warp as logical orbit relocation (revision 3)

```yaml
status: ACCEPTED
revision: 3
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-09-30
acceptance_basis: maintainer standing goal to complete the project with recommended solutions, after independent re-review of revision 2 ("accept with changes"); all nine required changes applied in this revision
target_version: v1.5.0
development_dependency: ADR-040, ADR-041, ADR-043
amends: ADR-040 (warp fields; "no speculative warp fields yet"; "current root 3 accepts only record 2" becomes the schema table in §6), ADR-041 (adds the named transition "orbit relocation" and balance invariance for existing transitions)
implements: V150-WARP-01 contract for WARP-02..05 and UI-01..03
station_registry_schema: 4 (record 2 and reservation 1 unchanged)
```

Revision 1 was rejected (energy paid after the commit, core identity, rocket
rule). Revision 2 kept logical relocation with energy in the station registry and
was accepted with changes. This revision applies those changes; see §9.

## 1. Logical relocation

Warp changes only the station's `orbit_body`. Cell, region, pad, blocks,
entities, players and the station's configured gravity stay exactly as they are;
gravity is stored per station and does not change with the orbit. The
orbit-derived context changes on the next query: the orbited body, solar
intensity, and routes, quotes and discovery of the orbit. No block, chunk or
entity is moved, loaded, scanned or queried. The plan's "target copy / source
delete" wording is resolved as relocation (plan §17).

Disclosure: every system is a label over the same Space Level. Cells of different
systems are at least 256 unclaimed columns apart, and players, blocks and
disassembled rockets can physically cross that gap. Warp buys an orbit context,
not physical isolation.

## 2. Energy: one store, folded credits

- **Balance.** New registry root field `warp_energy`: a list of
  `{station_id, energy}` entries.
  - Only positive balances are stored; an entry that reaches 0 is removed.
  - A zero, negative or over-bound value, a duplicate, an unknown station, or
    more than 4,096 entries blocks the registry (fail closed).
  - Each balance is at most 10,000,000 FE.
  - Budget: each entry is at most 64 bytes, so the full list is at most 256 KiB.
    A credit that would add an entry while the encoded registry is within 256 KiB
    of the bound is refused.
  - Growth admission (amended after the WARP-02 implementation review, F2):
    4,096 records fit only when they are small (1.59 MB measured for minimal
    records); records at their bounds (about 2.3 KB each) reach the 4 MiB bound
    near 1,850 stations. Creating a station, committing a reservation, and adding
    a member, invitation or owner transfer are therefore refused once the encoded
    registry would leave less than 256 KiB of the bound, and so is a relocation to
    a longer orbit identifier. The balance list always fits, and an ordinary save
    never fails on size.
- **Warp core.** `advancedrocketrycommunity:warp_core` is a stateless terminal.
  Its block entity exists only to expose the Forge Energy capability and stores
  nothing but its schema version (1). `receiveEnergy` accepts energy only when
  all of these hold:
  - the core's Level is the fixed Space Level;
  - the call is on the server thread;
  - the core is inside a committed station region (one `findAt`);
  - the registry is operational and not quarantined;
  - the station's balance plus its pending credit is below the cap;
  - the station's per-tick allowance of 200,000 FE is not used up.

  In every other case it accepts 0.
  - `simulate=true` changes nothing and does not use up the allowance.
  - `canExtract` is false and `canReceive` is true.
  - `getEnergyStored` returns 0, so no station's balance is exposed to probes;
    `getMaxEnergyStored` returns the cap.
  - Loot is the plain block with no block-entity data.
  - Several cores in one region feed the same balance. A core in a gap, in
    another Level or in a foreign station credits nothing, or credits the station
    whose region it is actually in.
- **Folding.** Accepted energy goes into a bounded pending-credit map on the
  server thread (at most 4,096 entries, cleared at stop after the final fold). The
  map is folded into the registry every 200 server ticks, immediately before
  every warp request and commit check, and in `ServerStoppingEvent` before the
  stop save. A fold is an ordinary registry mutation (dirty, ordinary save),
  never a flush. A continuously charging station therefore dirties the registry
  at most once per 10 s, not every tick (plan §13.3).
- **Deletion and reuse.** Deleting a station removes its balance (audited). A
  reused cell starts at 0, and a carried core carries nothing.
- **Cost.** COMMON config (the mod's existing config type, integrator-registered):
  - `stations.warpCostInSystem`, default 2,000,000 FE;
  - `stations.warpCostInterstellar`, default 8,000,000 FE;
  - both bounded to 100,000..10,000,000, so a cost of 0 is impossible (plan §4).

  Interstellar cost applies between systems and when leaving an unavailable orbit
  body. The cost is computed from the catalog and config captured at commit,
  never from the client.

## 3. Registry-level commit

**Predicate.** `StationState.isOrbitRelocationOf(previous)` is true only when
`orbit_body` differs and every other field is equal: region, environment,
identity, owner, name, cell, pad, created time, members and invitations. It does
not use `sameAuthorityAs`, which includes the orbit.

**Commit check.** A commit requires all of the following. The registry-level
`checkedRelocation(observed, target, cost)` itself checks authority, the observed
state, the exact relocation and the balance. The commit step (§4) must first fold
and check the target and the confirmed cost; the registry method accepts any
target identifier (clarified after the WARP-02 implementation review, F4).
- the registry is operational and not quarantined;
- the live state equals the observed state;
- the target is valid at commit;
- the live balance after a fold is at least `cost`;
- `cost` equals the confirmed cost (§4);
- the transition is exactly one orbit relocation.

**Candidate and publish.**
- The candidate is the live registry with the relocated state substituted and this
  station's balance set to `live - cost`; an entry that reaches 0 is removed.
- Verification decodes the candidate and checks that the relocated state and this
  station's balance are as expected, and that every other balance and every other
  state is unchanged.
- The write uses ADR-041's checked path.
- Publish is **one** synchronized model method that swaps the state and the
  balance together, with no fallible step between them. A publish failure after
  the write is quarantined and reported as an unknown outcome (ADR-041).
- The existing growth and gravity transitions now also verify that the
  candidate's balance list equals the live one.

**Crash-cut matrix** (the warp is one atomic file replacement):

| Cut | On disk after the cut | Outcome |
|---|---|---|
| Before replacement | Old orbit, old balance | No warp, no charge |
| Caught failure, file is not the candidate | Old file | No warp, no charge; registry marked dirty to re-save it |
| Caught failure, file equals the candidate (replaced despite error) | New orbit, debited | Warp published, charged once |
| Outcome unreadable, then an ordinary save | Old orbit, old balance (memory re-saved) | No warp, no charge; updates quarantined until restart |
| Outcome unreadable, crash before any save | New orbit and debit if the move happened, otherwise old | Consistent: warped and charged, or neither |
| Publish failure after replacement | New orbit, debited | Quarantined. Memory is re-saved on the next save, giving old orbit and no charge, unless a crash comes first. Consistent either way |
| Torn ordinary save (non-atomic vanilla write) | Unreadable registry | Start refused, fail closed (pre-existing class); restore from backup |
| After replacement | New orbit, debited | Warp charged exactly once |
| Countdown in progress (memory only) | Unchanged | No warp, no charge |

A warp can never be charged twice, and never happen without being charged.

**Disclosure (charging).** Forge Energy moves between separately saved stores.
For each station and each crash:
- energy accepted but not yet folded is lost, bounded by 200,000 FE × 200 ticks
  and capped at 10,000,000 FE;
- energy folded into the registry after the energy source's last chunk save can
  be supplied again after the crash, capped at 10,000,000 FE.

ARCE flushes save the registry often, while source chunks are saved on unrelated
schedules, so duplication is the likelier direction. Restoring the pre-upgrade
backup discards all balances.

## 4. Entry point, confirmation, countdown and limits

- **Request.** `/arce station warp <body_id>` requires:
  - the connected player's own non-silent command source (ADR-040/041);
  - the owner or an operator, in the committed region with their chunk loaded;
  - that the player is looking at a warp core in that region (server ray pick
    within 5 blocks);
  - warp enabled, the station not in cooldown, and the in-motion rule (§5)
    passing.

  The reply shows the station, the current body and the target, the cost class,
  the cost and the balance (after a fold). It warns that docked rockets move with
  the station, that the target system has no rocket routes when that is true, and
  that the warp cannot be undone for free.
- **Confirmation.** `/arce station warp confirm <station_id>` within 10 seconds.
  - At most one pending confirmation per player and 128 globally.
  - Each expires after 200 ticks and is cleared on logout and at stop.
  - It is bound to the actor, the observed state, the target, the cost and the
    cost class.
- **Countdown.** 200 ticks, reported to online members.
  - At most one per station and 64 globally, held in memory only.
  - `/arce station warp cancel` (owner or operator, local rule) cancels it.
  - It is discarded at stop; the owner logging out does not cancel it.
  - An upgrade during a warp is trivial, because nothing persists before commit.
- **Commit.** At most one commit per server tick; others due in the same tick
  wait for the next tick and are fully rechecked. The countdown aborts, with the
  reason shown to online members, if any of these fails:
  - warp is enabled;
  - the registry is operational and not quarantined;
  - the station equals the confirmed state (`STALE` on any change);
  - the confirming actor is still the owner, or still an operator according to
    the server ops list (a de-op or an ownership transfer cancels);
  - the target is present, orbitable, known and not the current orbit;
  - the cost and cost class equal the confirmed ones (a catalog or config reload,
    or a removed orbit body, cancels and requires new consent);
  - the balance covers the cost after a fold;
  - the in-motion rule passes.
- **Cooldown.** 100 ticks per station after a commit or a failed write (as in
  ADR-041).
- **Kill switch.** `stations.warpEnabled` (COMMON config, default true) refuses
  requests, confirmations and commits. Charging, stations and access are
  unaffected.
- **Evacuation.** A station whose orbit body is unavailable may warp to any valid
  target at interstellar cost.

## 5. Rockets and passengers

- **Passengers** stay in place and see the new context. There is no teleport.
- **Docked rockets move with the station.** For a `TravelTarget.Station` source,
  launch admission no longer compares the rocket's saved `current_body`
  (`PlanetaryFlightAdmission.allows` lines 48-52). The planner already takes the
  source body from the station record, plans are recomputed at launch, and the
  client never sends a quote, so no stale-quote rule is needed. These existing
  checks stay: the current target is the station, the station at the rocket's
  position has that ID, and the Level and snapshot dimension match. New flight
  and transfer records store the station's current orbit body as `current_body`.
- **In-motion rule.** Exposed through the port
  `StationRocketAuthority.inMotion(station)`, implemented by the rocket module and
  wired at startup (the ADR-041 pattern; no new cross-module imports). It works in
  memory, with no entity query and no chunk load. It **blocks** (returns true)
  when any of these hold:
  1. the transfer journal is not operational;
  2. the post-start recovery pass has not yet classified every journal record;
  3. a record overlapping the region is in `DESTINATION_SPAWNED`,
     `PASSENGERS_TRANSFERRED` or `SOURCE_REMOVED`, or in `PREPARED` without
     having been returned to a stationary source by recovery
     (`WAITING_FOR_PASSENGERS` does not block);
  4. a `COMMITTED` record overlapping the region is still descending: game time
     is below its scheduled arrival (countdown start + 160) plus `DESCENT_TICKS`
     plus 20.

  Landed reservations do not block. Invariant: every flight that can still move
  has a journal record. Crash windows can leave orphaned rocket entities with no
  record; those cannot fly and do not affect warp. Assembly transactions do not
  block.
- **Deletion.** Station deletion's rocket guard now fails closed when the journal
  is not operational (same slice).

## 6. Persistence and migration (amends ADR-040)

Station registry acceptance by root schema:

| Root | Epoch | Accepted content |
|---|---|---|
| 1 | (legacy) | legacy record 1, reservation 1 |
| 2 | `v0.9.0-beta` | legacy record 1, reservation 1 |
| 3 | `v1.5.0-orbital-station` | record 2, reservation 1 |
| 4 | `v1.5.0-station-warp` (current) | record 2, reservation 1, **required** `warp_energy` |

- Root-shape validation is chosen by schema.
- The pre-start migration (ADR-040) upgrades root 3 to 4 by adding an empty
  `warp_energy` list, and chains roots 1 and 2 to 4 through record 1→2. The
  backup manifest records the source and target schema of each file.
- Older builds refuse root 4; restore uses the complete pre-upgrade backup.
- The plan's `save_schema` line becomes "station root 4 / record 2 /
  reservation 1; other managed roots 2".
- Native evidence: the STATION-04 root-3 world upgrades to root 4 and restarts; a
  second restart is idempotent.

## 7. Content, UI and sky

- **Warp core.** Original block, model and recipe; no imported art or code.
  - The model uses the existing machine-casing textures.
  - The recipe (v1.5 DataGen) is four machine casings, four advanced circuits and
    one data storage unit, so the core follows the existing machine progression.
  - Generated assets and data go in `src/generated/v1.5/resources`, and name keys
    in the `advancedrocketrycommunity_v150` language namespace.
- **UI.** Commands are the interface. Feedback uses chat and action-bar messages:
  request, confirm, countdown at 10, 5, 3, 2 and 1 seconds, commit, and abort
  reasons. A screen (UI-01) needs its own bounded, versioned payload. GUI scale
  does not apply without a screen.
- **Sky.** The generic Space sky stays until ORBIT-03.

## 8. Plan traceability (sections 8–15)

| Plan item | Where |
|---|---|
| §8 Upgrade during warp | Trivial: countdowns are memory-only (§4) |
| §8/§9/§14.5 v1.4 and root-3 fixture migration to root 4, lossless | WARP-02 unit fixtures; native STATION-04 world → root 4 (§6) |
| §9 WarpState transitions; each stage recoverable; charged once | WARP-02 unit tests, including the crash-cut matrix as JUnit fault injection |
| §9 Multi-star routes and discovery requirements | STAR-02/03 (done, ADR-043) |
| §10 Two stations warping at once to different targets | WARP-05 GameTest (commits serialized, one per tick) and MIG-02 native |
| §10 Owner disconnect and reconnect; passengers online and offline | WARP-04 GameTest (countdown survives logout; offline data untouched) |
| §10 Docked rocket saved and restored across a warp | WARP-04 GameTest and native |
| §10 Catalog reload or target removal during countdown aborts safely | WARP-03 GameTest (a cost-class change and target removal both cancel) |
| §10/§12 Non-member, forged or stale requests; no client-supplied energy or cost | UI-03 and WARP-03 tests |
| §11.2 Countdown and failure feedback | WARP-03 GameTest capturing member messages |
| §11.3 No data mixed between stations | WARP-05 two-station GameTest (balances and orbits independent) |
| §11.5 GUI scale | Not applicable (no screen) |
| §12.4 Region coordinates never load chunks | By design; UI-03 checks loaded-chunk counts |
| §12.5 Orbital weapons and elevator write interfaces | Not applicable (none exposed) |
| §13.2/§13.3 Bounded preparation; no full dirty every tick | §2 folding. WARP-05 test: ten charging stations dirty the registry at most once per 200 ticks. Plus a measured ordinary save at 4,096 stations |
| §13.4 10/100-station performance | ORBIT-04 scale test (done) and WARP-05 commit timing |
| §14 Stations around any data body; two systems warp end to end | WARP-03 GameTest and native (Earth orbit ↔ Tau Ceti e) |
| §14 Each stage force-stopped without loss or duplication | Crash-cut matrix tests (WARP-02) and native restarts (§6) |
| §15 Migration report; multiplayer permission report | WARP acceptance packet: migration VERIFICATION, and the permission matrix from UI-03 tests |
| Stateless-core cases (two cores, gap, other Level, foreign station, carried core, cell reuse, deletion drops the balance, simulate, off-thread) | WARP-02/03 unit tests and GameTests |
| §11.4/§11.1 Real-client cache, position and sky; multiplayer; video | V1/V2 in ACC-02 (not available in this environment; stays open) |

## Non-goals

Physical copies, other Levels, warp without a core or energy, remote initiation,
fuel items, per-system time, physical isolation of systems, elevator logistics,
and warping rockets or players separately from a station.

## Rollback

Warp commits are ordinary root-4 registry changes, and older builds refuse root 4.
Restore the complete pre-upgrade backup (ADR-040), which also discards balances.

## 9. Review history

- **Revision 1** was rejected: energy was paid after the commit, cores had no
  identity, and the rocket rule was wrong.
- **Revision 2** was accepted with changes. The re-review found no Critical or
  High issues and confirmed feasibility: the journal has the needed fields, and
  the admission change is one filter.
- **Revision 3** applies all nine required changes:
  - N1: registry-level predicate, candidate checks and a single publish;
  - N2: folding;
  - N3: settled PREPARED records do not block, and the rule fails closed until
    recovery has classified every record;
  - N4: one commit per tick, cooldown, caps and a minimum cost;
  - N5: Level, thread and simulate rules, and no exposure to probes;
  - N6: schema table and migration;
  - N7: consent and authority rechecks, and the no-route warning;
  - N8–N11 and N13: text corrections;
  - N12: traceability and content.

Both reports are archived in `docs/work/v1.5.0-star-warp-contracts/`. Acceptance
is not a Gate approval.

- **Implementation review of WARP-02** (2026-09-30, `9969550`): accept with
  changes. Two text corrections follow from it, and neither changes the decision:
  - §2 growth admission (F2), which replaces the incorrect "4,096 records fit"
    premise;
  - §3 the split between registry and commit-step checks (F4).

  See `docs/work/v1.5.0-slices-review/`.
