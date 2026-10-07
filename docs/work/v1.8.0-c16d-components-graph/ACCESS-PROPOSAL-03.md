# C16d graph access proposal 03: station existence, physical access, admitted travel

Task: CL16D-GRAPH-ACCESS-03 ([TASK-03](D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c16d-components-graph/TASK-03.md)).
Date: 2026-10-08. Author: delegated Claude worker (Opus 5.5), not Root. Status: PROPOSAL,
unreviewed. It covers one leaf: the context/access part of the revision-02 graph (CONTRACT-02
§5.1-§5.3, TEST-DESIGN-02 §3). It does not rewrite the rest of the graph and does not settle
R2-R5, steel bootstrap, tag witnesses, the planner/catalog binding or the full contract.

Fixed source: `2e397a49c92a323eb1886a538e226765e7f4edc9` (src identical to `65dd8211`). Paths
below are relative to `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`.

## 1. Problem (independent R1)

CONTRACT-02 §5.1 defines `cap:station:<body>` as "a committed station exists **and** a player can
be aboard", and §5.2 item 2 emits it from `cap:surface:<body>` plus the deployment kit alone.
Revision-02 route rule 1 also emits raw `cap:orbit:<to>` from a planner quote. The source shows
three distinct facts that the graph conflates:

| Fact | What the source does | Evidence |
| --- | --- | --- |
| Existence | Kit `use` calls `StationRuntime.createForPlayer`, reports, consumes one kit. Nothing else. | `station/content/StationDeploymentKitItem.java:21-47` |
| | Creation resolves the body whose **surface** Level the player stands on (`PlanetarySurfaceResolver.find(..., false)`), fails `INVALID_SOURCE` otherwise, and passes the player UUID as owner. | `station/service/StationManager.java:76-89` |
| | The service checks registry, orbitable body, owner limit, Space Level; reserves, generates, commits. It never moves the player. | `station/service/StationCreationService.java:41-79`; orbitable filter at `StationManager.java:64-70` |
| Physical access | Survival launch accepts only `TravelTarget.Station` or `TravelTarget.BodySurface` destinations; anything else is `INVALID_DESTINATION`. A station destination also needs VISIT (owner, member or operator). | `rocket/server/RocketFlightService.java:250-260`; `station/service/StationAccessService.java:21-35` |
| | Admission rejects any source other than BodySurface/Station and any destination other than an existing surface Level or an existing station whose orbit body is orbitable. A Station source needs the rocket physically inside that station's committed region. | `rocket/server/PlanetaryFlightAdmission.java:30-72` |
| | Navigation lists surfaces (`supportsSurfaceArrival`, not Space) and VISIT-accessible stations only; no Orbit target is offered. | `rocket/server/RocketNavigationService.java:61-68` |
| | A station arrival lands on the station's `landingPad`; a footprint wider than `2 * StationLimits.PLATFORM_RADIUS + 1` fails. | `rocket/server/RocketTransferService.java:78-92`; `rocket/transfer/RocketLandingPadSelector.java:109-118` |
| | A player travels only when assigned a seat; no free seat is `NO_SEAT_AVAILABLE`. Launch/cancel need the rocket owner (or creative/op), within interaction range. | `RocketFlightService.java:158-171,402-415,445-454` |
| Station travel (warp) | Request needs the owner/operator in the committed region, chunk loaded, looking at a warp core within 5 blocks. | `station/warp/StationWarpService.java:169-183,474-483`; ADR-044 §4 lines 166-172 |
| Pure quote | The planner can quote `TravelTarget.Orbit` (`RocketTargetFlightPlanner.java:126-135`), but no survival path admits it. A Station target resolves to `RouteAnchor.orbit(orbitBody)` (`:136-146`). | as cited |

The independent finite control reproduced the false derivation (REPORT-02 R1). No Minecraft
travel was run by that review or by this proposal.

## 2. Graph actor

All access capabilities describe **one survival actor**: a non-operator, non-creative player who
owns every rocket and station the graph derives. This matches the paths above. The owner can
launch (`authorized`), visit and build on its own stations (`StationAccessService.allowed`), and
request warp (owner/operator only). Membership granted by another player, operator commands,
creative instabuild and developer travel (`celestial/service/SafeCelestialTravel.java:15`,
"developer travel") are **not producers**. A row that needs one of them is out of scope and must
say so.

## 3. Capability vocabulary (replaces CONTRACT-02 §5.1 rows 2-3)

| Capability | Meaning | Source of truth |
| --- | --- | --- |
| `cap:surface:<B>` | unchanged: the actor can stand on body B's surface Level | CONTRACT-02 §5.1 |
| `cap:station_exists:<B>` | **new.** A committed station owned by the actor, with current `orbitBody` = B, can exist in the registry. Says nothing about where the actor is. | `StationState.orbitBody`, owner = creator UUID |
| `cap:station_aboard:<B>` | **new.** The actor can stand inside the committed region of an owned station whose `orbitBody` is B, in the Space Level, and an actor-owned rocket can be docked in that region | `stations.findAt(x, z)`; `PlanetaryFlightAdmission.java:44-56` |
| `cap:orbit:<B>` | **withdrawn.** No survival producer can emit it (§1). Contract validation fails with `unsupported_capability:orbit` if a row's slot or output names it. | — |

`cap:station:<B>` from CONTRACT-02 is retired. The successor must not keep it as an alias,
because a single name would bring the conflation back. A row that references it fails
validation with `retired_capability:station`.

Invariants:

- I1. Nothing derives `cap:station_aboard:<B>` without an admitted rocket arrival at a station
  whose orbit body is B, or a warp that started from an earlier `cap:station_aboard:*`. By
  induction, every aboard derivation includes at least one station-boarding flight (P3).
- I2. Station existence never implies presence. Presence at a surface or station never implies
  existence of a station elsewhere.
- I3. A planner quote alone never emits a capability. Each travel producer's slots also need the
  survival admission conditions in §1 for its exact source/destination **kinds**.
- I4. No producer emits a Level-only capability (CONTRACT-02 §5.1, kept).

## 4. Producers (replaces CONTRACT-02 §5.2 items 1-3; portals unchanged)

Common travel slots, abbreviated **ROCKET(r)** below. For one committed test blueprint r of the
route row (G-OPEN-7):

- its parts and its fuel (CONTRACT-02 §5.2 item 1, unchanged);
- static row predicates, checked once when the row is built and **not** as slots:
  `seats(r) >= 1` (otherwise the row is disabled with `no_seat`, because the actor would not
  travel); and, for a Station destination only,
  `footprintX(r) <= 2*PLATFORM_RADIUS+1 && footprintZ(r) <= 2*PLATFORM_RADIUS+1`
  (otherwise disabled with `station_pad_footprint`);
- a READY result from `RocketTargetFlightPlanner.plan` called with the **same TravelTarget kinds
  the survival service admits**: source `BodySurface(A)` or `Station(s_A)`, destination
  `BodySurface(C)` or `Station(s_B)`, where s_X is a synthetic station record with orbitBody X
  (dependency A-DEP-1). A quote that uses `TravelTarget.Orbit` is a contract error
  (`orbit_target_quote`), not a blocked producer. A non-READY quote leaves the row enabled but
  unsatisfied, with blocker `quote:<RocketFlightPlanCode>`.

**Source slot SRC(A, kind):** `cap:surface:<A>` for a surface source; `cap:station_aboard:<A>`
for a station source. The rocket then plans from the station's orbit body (ADR-044 §5;
`RocketFlightService.java:276-278`). The station source is **never** satisfied by
`cap:station_exists:<A>`, because admission needs the rocket physically inside the region.

### P1. Station creation `code:station_create:<B>`

- Row exists iff B is orbitable in the effective catalog **and** B has a surface Level that
  `PlanetarySurfaceResolver.find(catalog, level, false)` maps to B. A body with no surface Level
  (Space itself) and vanilla Nether/End (no catalog surface body) get no row.
- Slots: `cap:surface:<B>`, `item:advancedrocketrycommunity:station_deployment_kit`.
- Output: `cap:station_exists:<B>` **only**.
- Static run preconditions: `StationLimits.MAX_OWNED_STATIONS >= 1` and the Space Level is
  registered. Otherwise the run fails `binding_unfrozen`. Registry `operational()`, reservation
  and platform generation are runtime states; A1 observation AO1 covers them.
- Report-only diagnostic `owner_station_limit`: the number of bodies for which P1 is the
  **only** producer of a needed `cap:station_exists:<B>` exceeds `MAX_OWNED_STATIONS`. It does not
  change availability; P4 can relocate one station.

### P2. Surface route `route:<route id>:<A kind>:<A>->surface:<C>`

- One row per effective route direction whose destination anchor is a surface of C, where C
  `supportsSurfaceArrival` and C is not the Space body.
- Slots: SRC(A, kind), ROCKET(r) with destination `BodySurface(C)`, plus `cap:discovered:<C>`
  when C has `discovery_required`.
- Output: `cap:surface:<C>`.
- A route direction whose destination anchor is `orbit:<C>` produces **no** P2 row. Its only
  use is as the route anchor of P3.

### P3. Station boarding `code:station_board:<A kind>:<A>->station:<B>`

- One row per effective route direction whose destination anchor is `orbit:<B>` with B
  orbitable. The route anchor is the anchor a Station target resolves to
  (`RocketTargetFlightPlanner.java:136-146`).
- Slots: SRC(A, kind), `cap:station_exists:<B>`, ROCKET(r) with destination `Station(s_B)`
  (footprint predicate applies), plus `cap:discovered:<B>` when B has `discovery_required`. The
  last one follows `PlanetaryFlightAdmission.discovered`, which resolves a station target to its
  orbit body (`:19-28`).
- Output: `cap:station_aboard:<B>`.
- Meaning: the actor, seated, launches an owned rocket to its own existing station. VISIT holds
  because the actor is the owner (`RocketFlightService.java:250-257`). The rocket lands on the
  station pad, so the docked-rocket clause of `cap:station_aboard:<B>` also holds.

### P4. Station warp `code:warp:<A>-><C>`

- Slots: `cap:station_aboard:<A>`, `item:advancedrocketrycommunity:warp_core` (placed in the
  region by the owner, BUILD allowed by `StationAccessService.java:30-35`), the
  known/discovered condition for C (`StarSystemKnowledge.bodyKnown`,
  `StationWarpService.java:451`), C orbitable, and `cap:fe:warp_balance` with the
  `WarpCostClass` atomic debit (CONTRACT-02 §4.2/§5.2 item 3, unchanged).
- Outputs: `cap:station_aboard:<C>` and `cap:station_exists:<C>`. The request needs the owner
  in the region looking at the core, so the actor is aboard and stays aboard. Docked rockets
  move with the station (ADR-044 §5).
- Because the only `cap:station_aboard` slot is the warp source, **a kit cannot bootstrap a
  warp** (I1).

### P5. Portals

Unchanged (CONTRACT-02 §3.4 / §5.2 item 4).

## 5. Binding of body-local producers (amends CONTRACT-02 §5.3)

- World features and surface loot stay bound to `cap:surface:<B>`.
- Producers that run in a station's region, and need the actor to place, power or collect from
  them, bind to `cap:station_aboard:<B>`. This includes the black hole generator for a body with
  a singularity profile (`BlackHoleGeneratorBlockEntity.java:263-290`, cited by CONTRACT-02 and
  not re-read here) and station-local environment producers.
- Only a producer whose observable output comes from registry state alone, with no actor in the
  Space Level, may bind to `cap:station_exists:<B>`. No such producer is proposed. A row that
  claims this must cite the code path that runs without a player present.
- "Anywhere in the Space Level" producers use one OR slot over every
  `cap:station_aboard:<B>`. The withdrawn orbit alternative is removed.
- Tau Ceti f/g: reachable only through P4 into their orbit, then P2 from the station source
  (CONTRACT-02 §5.3, now expressed with the new names).

## 6. Monotone semantics and its limits

The graph is a least fixpoint over "the actor can, at some point, obtain/stand at X". Warp
relocates the station, so after P4, `cap:station_exists:<A>` and `cap:station_aboard:<A>` no
longer describe the live world. They stay derived because they were reachable earlier, and
because the actor could warp back if A is still known and orbitable, or create a new station at
A with another kit. Proposed rules:

- L1. The run records a report note `relocated_by_warp` on each `cap:station_*:<A>` that is
  also a P4 source. Availability does not change.
- L2. A producer that needs **simultaneous** presence or existence at two bodies cannot be
  expressed. The validator rejects a row with two different `cap:station_aboard:*` or
  `cap:station_exists:*` slots, error `simultaneous_context_unsupported`. No current row in
  CONTRACT-02 §5.3 needs this.
- L3. One-shot consumption of the kit and of warp energy stays outside reachability. This matches
  how CONTRACT-02 handles other consumed inputs.

## 7. Soundness argument (proposal-level, not a proof of the implementation)

1. **Existence:** P1 is the only creator, and its output is limited to `station_exists`. This
   matches `StationCreationService.create` (`:56-79`): it reserves, generates and commits a
   region and returns a state, with no player transfer.
2. **Presence via rocket:** P3 needs everything that `RocketFlightService.launch` and
   `PlanetaryFlightAdmission.allows` check for this actor and these kinds:
   - FUELED state, reached through blueprint fuel;
   - source kind and physical source: `cap:surface` for a surface source, or `station_aboard`
     with a docked rocket for a station source;
   - destination station exists (`station_exists`) and has an orbitable orbit body;
   - VISIT, through actor ownership;
   - discovery of the orbit body;
   - a READY quote with the admitted kinds;
   - a pad that fits (footprint predicate);
   - the actor travelling (seat predicate).
3. **Raw orbit:** no producer emits or consumes `cap:orbit`, so a READY `Orbit` quote cannot
   reach any product. That matches `RocketFlightService.java:258-259`.
4. **Warp:** P4 needs `station_aboard` at the source, matching the region/core/owner request
   check. Its outputs match logical relocation.
5. **Not covered by this argument:**
   - runtime states: registry not operational, transfer journal blocked, rate limit, landing
     reservations, chunk loading, rocket-in-motion rule;
   - the live existence of the destination server Level;
   - navigation caps `MAX_ACCESSIBLE_DESTINATIONS`/`MAX_QUOTES` (`RocketNavigationService.java:61-69`).

   These are A1 observations (ACCESS-TEST-DESIGN-03 §3), not graph slots. Where a cap could
   hide a target, the risk is stated rather than assumed away (A-OPEN-2).

## 8. Dependencies

New necessary dependencies (Root decides owner and adoption):

| ID | Dependency | Why it is necessary |
| --- | --- | --- |
| A-DEP-1 | A pure way to give `RocketTargetFlightPlanner.plan` a `Function<UUID, Optional<StationState>>` that returns a synthetic station with a chosen `orbitBody`. Either a `StationState` built without Minecraft bootstrap, or a narrow test fixture. It extends G-OPEN-7. | P2/P3 must quote with the admitted Station kind; quoting `Orbit` instead is exactly R1's defect. Whether `StationState` is constructible in plain JUnit was not checked. |
| A-DEP-2 | Blueprint facts exposed to the pure adapter: seat count and footprint bounds of each committed test blueprint, alongside the `RocketStats` binding of G-OPEN-7. | The `no_seat` and `station_pad_footprint` predicates. |
| A-DEP-3 | Observed departure from a station: refuel of a docked rocket in the Space Level and launch from a Station source (AO7). Until observed, station-source rows (SRC kind = station) fail `binding_unfrozen`. | Tau Ceti and every orbit-anchored route depart from a station. Fuel loading in the Space Level is unverified here. |
| A-DEP-4 | The effective RouteCatalog contains each source-anchor → `orbit:<B>` direction the progression needs, for example Earth surface → Earth orbit. | P3 rows come only from effective routes. This read could not find route resources under `src/main/resources/data`; they may be generated or merged at build (CONTRACT-02 cites `build.gradle:146` for mission data). Root's catalog binding (G-OPEN-7) must enumerate them. |

Open questions this proposal does not decide:

- A-OPEN-1 (Root/owner): whether membership-based VISIT (a second player's station) should ever
  count. Proposed: no. It needs another player.
- A-OPEN-2 (Root): whether a launch request whose target the navigation snapshot did not list
  (more than `MAX_QUOTES` targets makes navigation empty, `:69`) is still admitted. If not, the
  graph needs a static check that clean-world surfaces + owned stations stay at or below
  `MAX_QUOTES`. Not determined from the lines read.
- A-OPEN-3 (Root): the warp-core item producer and its placement. Assumed to come from the
  existing recipe graph, not from this leaf.

## 9. Disposition of R1 and scope boundary

R1 (High): **addressed in proposed text, not closed.** Existence (P1 → `station_exists`),
physical access (P3/P4 → `station_aboard`, bound to admitted Station targets, actor ownership,
seat, pad, fuel, discovery) and admitted travel (kinds limited to Surface/Station, `cap:orbit`
withdrawn) are now separate. The negative fixtures (kit without rocket, raw orbit quote, warp
without boarding) and the positive existing-station boarding observation are in
ACCESS-TEST-DESIGN-03. Closure needs:

- a different-agent review;
- Root adoption into a successor contract;
- A-DEP-1..4;
- the A1 observations.

None of these exists yet.

Not settled here:

- R2 (catalog raw/depth binding), R3 (work accounting), R4 (AND/OR hard-lock invariance), R5
  (X05 round);
- steel/arc-furnace bootstrap (G-OPEN-10);
- tag witnesses (T-OPEN-1/2);
- root/portal witnesses;
- energy rows;
- the full contract.

Planned test IDs X01-X03, X09 and X02 of TEST-DESIGN-02 are superseded only for their access
expectations; all other rows are untouched.
