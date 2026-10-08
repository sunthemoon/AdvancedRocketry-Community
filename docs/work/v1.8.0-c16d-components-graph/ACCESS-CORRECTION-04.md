# C16d graph access correction 04

Task: CL16D-GRAPH-ACCESS-04 (main checkout `TASK-04.md`). Date: 2026-10-08. Author: delegated
Claude worker (`claude-opus-5-5`, fresh `claude -p` session); not Root, not a reviewer.
Status: PROPOSAL, unreviewed, not adopted. Base: `c9b2c001`. Amends
[ACCESS-PROPOSAL-03](ACCESS-PROPOSAL-03.md) (AP3) and [ACCESS-TEST-DESIGN-03](ACCESS-TEST-DESIGN-03.md)
(AT3) only for REPORT-03 A1-A4. Neither file is edited. Paths are under
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`.

## 1. Source facts

| # | Fact | Source |
| --- | --- | --- |
| S1 | The kit path enforces the owner limit; the operator path does not. | `station/service/StationManager.java:89,98` |
| S2 | At the limit: `OWNER_LIMIT_REACHED`. `MAX_OWNED_STATIONS` = 1. | `StationCreationService.java:48-49`; `station/model/StationLimits.java:17` |
| S3 | Source equal to destination: `INVALID_DESTINATION` (service), `SAME_DESTINATION` (planner). | `rocket/server/RocketFlightService.java:226-228`; `rocket/flight/RocketTargetFlightPlanner.java:45-46` |
| S4 | Station source: the station at the rocket's x/z has the source ID. Station destination: the record exists, orbitable. | `RocketFlightService.java:236-257`; `rocket/server/PlanetaryFlightAdmission.java:44-56,66-69` |
| S5 | The planner calls `routes.plan(sourceAnchor, destinationAnchor)` once; bounded Dijkstra returns multi-leg legs; fuel uses the total distance. | `RocketTargetFlightPlanner.java:49-78`; `travel/route/service/RouteCatalog.java:137-152`; `RoutePlanner.java:41-104` |
| S6 | Admission reads only the source and the final destination. Discovery resolves only the destination. | `PlanetaryFlightAdmission.java:19-72` (full) |
| S7 | `hasFlightComponents` needs engine, seat and guidance > 0. The planner returns `MISSING_FLIGHT_COMPONENTS`, mapped unchanged, before transfer. A non-FUELED rocket fails first with `INVALID_STATE`. | `rocket/stats/RocketStats.java:28-30`; planner `:56-57`; service `:223-224,261-275`; `RocketFlightRequestCode.java:39` |
| S8 | BOARD's only `NO_SEAT_AVAILABLE` return. | service `:413` (grep hit) |
| S9 | Warp relocates one record; docked rockets and passengers move with it. | ADR-044 lines 210-220 |

## 2. Corrected clauses

### C1 (A1): solo-owner concurrency

Replaces AP3 L2 and the Station-source use of P3.

- C1.1 The actor holds at most N = `MAX_OWNED_STATIONS` live stations, all created by P1 (S1, S2).
  N is read statically and is never raised. Synthetic seeds are not live owner records.
- C1.2 P3 exists **only with a surface source**: `travel:surface:<A>->station:<B>`.
  - Station(A) -> Station(B) needs two live records at once (S4).
  - With N = 1, the one record at both ends is refused (S3).
  - So the row is **not generated**. It is outside coverage and fails closed (CONTRACT-02 3.6
    property 1).
  - Any row with Station source and Station destination fails `station_pair_unsupported`.
- C1.3 Validator: a row may have at most **one** slot with a `cap:station_aboard:*` or
  `cap:station_exists:*` member, whatever the bodies. This covers the same body twice and the mixed
  pair. Otherwise it fails `simultaneous_context_unsupported`. One OR slot over all
  `cap:station_aboard:*` (AP3 5, "anywhere in Space") stays legal. Every corrected P1-P4 and
  station-local row passes.
- C1.4 Kept: surface-to-own-station boarding, station-to-surface departure, and one-station warp P4.
  P4 outputs `aboard:C` and `exists:C` for the same record; `relocated_by_warp` goes on A (S9).
- C1.5 Disclosed, not corrected: P1 rows for several bodies each yield `station_exists`. Yet with
  N = 1 only one kit creation succeeds, unless a removal path frees the limit (not read).
  `owner_station_limit` stays report-only. This is A4-OPEN-1.

### C2 (A2): endpoint travel rows

Replaces AP3 4 row generation for P2/P3.

- C2.1 Endpoints:
  - sources are `BodySurface(A)` (surface Level, not Space) and `Station(s_A)` (A orbitable);
  - destinations are `BodySurface(C)` (arrival, not Space) and `Station(s_B)` (B orbitable);
  - `Orbit` stays withdrawn.
- C2.2 Rows:
  - One row per ordered endpoint pair whose anchors `RouteCatalog.plan` connects.
  - No row for C1.2 pairs or equal anchors.
  - The row records `path` (ordered legs `routeId`, `from`, `to`), the distance and blueprint r.
  - IDs are `travel:<src kind>:<A>->surface:<C>` (P2) and `travel:surface:<A>->station:<B>` (P3).
  - CONTRACT-02 7.1 rule 1 adds the producer prefix `travel:`; travel no longer emits `route:`.
- C2.3 Slots:
  - SRC(A, kind) as in AP3 4.
  - ROCKET(r), with **one** endpoint quote from `RocketTargetFlightPlanner.plan` on the admitted
    TravelTargets (A-DEP-1 for Station kinds).
  - `cap:station_exists:<B>` for a station destination.
  - `cap:discovered:<D>` only for destination body D (S6).
  - **No slot names an intermediate anchor**; an intermediate orbit needs no station.
- C2.4 Bounds (proposal):
  - At most 2 endpoints per catalog body. Pairs are counted before any planner call:
    `limit:travel_pairs` = 4,096.
  - Each pair adds 1 + legs `work_units`.
  - An unconnected pair or an absent anchor gives no row.
  - A search-limit or overflow error fails `route_search_failed:<pair>`, told apart without
    parsing messages (A-DEP-5).
- C2.5 Coverage: exactly the planner-connected pairs along the planner's selected path. Other paths
  are not credited, which fails closed. Per-leg rows also misquoted fuel (S5).

### C3 (A3): seatless oracle

`seats(r) >= 1` stays the static `no_seat` predicate. A READY quote already implies it (S7), so it
is a shortcut, not a second oracle. A seatless blueprint cannot fly. A flight without the actor
uses a valid seated blueprint whose actor does not board (AO11b), separate from AX05.

### C4 (A4): dependency reference

A-DEP-3 binds to **AO8**: refuel a docked rocket in Space, then launch from a Station source. AO7
binds only AX04. Until AO8 is observed, station-source rows still fail `binding_unfrozen`.

## 3. Affected planned cases (all unrun)

Renames in all AT3 cases:

- `route:<r>:<k>:<A>->surface:<C>` becomes `travel:<k>:<A>->surface:<C>`;
- `code:station_board:surface:<A>->station:<B>` becomes `travel:surface:<A>->station:<B>`.

| ID | A0 change (AT3 fixture unless stated) |
| --- | --- |
| AX02c | Add: `travel:surface:t:earth->surface:t:far` (3 legs) enabled, blocker `cap:discovered:t:far`. |
| AX05 | Add: the `r_noseat` quote earth surface -> moon surface is `MISSING_FLIGHT_COMPONENTS`. |
| AX07 | Fixture without `t:earth_far_orbit` (warp-only far). Rounds unchanged; surface far 4 via `travel:station:t:far->surface:t:far`. |
| AX08 | `cap:station_aboard:t:moon` round 1 via `travel:surface:t:earth->station:t:moon` (legs `t:earth_moon`, `t:moon_orbit`). `travel:station:t:moon->surface:t:moon` round 2, blocked at round 1 by `cap:station_aboard:t:moon`; seeded existence never satisfies it. |
| AX09 | Replaced (C1.2). Seed `cap:station_exists:t:far`; roots `r1.*`. `travel:surface:t:earth->station:t:far` (legs `t:earth_orbit`, `t:earth_far_orbit`) blocked by `cap:discovered:t:far`. With that seed: `cap:station_aboard:t:far` round 1. |
| AX13 | Add: (b) `aboard:t:earth` + `exists:t:moon`; (c) `aboard:t:earth` + `exists:t:earth` as two slots. Both are rejected. (d) One OR slot over `aboard:*` is accepted. |
| AX16 | Bodies `t:e` (surface, orbitable), `t:x` (orbitable, no surface), `t:y` (surface). Routes `t:e_x` (surface e <-> orbit x), `t:x_y` (orbit x <-> surface y). Roots `r1.*`. Expected: `travel:surface:t:e->surface:t:y` with legs [`t:e_x`, `t:x_y`]; `cap:surface:t:y` round 1; no `cap:station_*:t:x` slot on it. |
| AX17 | AX07 with `t:earth_far_orbit`: `cap:surface:t:far` round 1 via `travel:surface:t:earth->surface:t:far`. Warp-only is a catalog fact. |
| AX18 | Roots `kit`, `r1.*`, `warp_core`; seeds discovery of far and `cap:fe:warp_balance`; N = 1; no `t:earth_far_orbit`. Station capabilities for earth (round 2) and far (round 3) are available; no `travel:station:*->station:*` producer exists; a table row `travel:station:t:earth->station:t:far` fails `station_pair_unsupported`. |
| AX19 | 4,097 admitted pairs fails `limit:travel_pairs` before any planner call. |

Observations that bind source behaviour, independent of a graph implementation:

| ID | Level | Steps | Expected |
| --- | --- | --- | --- |
| PL1 | A0 pure | AX16 catalogs, seated stats, enough fuel: `plan(BodySurface(t:e), BodySurface(t:y))`; `RouteCatalog.plan` on the same anchors | SUCCESS; legs [`t:e_x`, `t:x_y`]; fuel from the summed distance (C2) |
| PL2 | A0 pure | `plan(Station(u), Station(u))` | `SAME_DESTINATION` (C1.2) |
| PL3 | A0 pure | seat 0, engine and guidance > 0, resolvable pair | `MISSING_FLIGHT_COMPONENTS`, fuel 0 (C3) |
| AO11a | A1 | Assemble a seatless blueprint (record the result; stop if refused). BOARD; fuel; LAUNCH to moon surface. | BOARD `NO_SEAT_AVAILABLE`; LAUNCH `MISSING_FLIGHT_COMPONENTS` if FUELED, else `INVALID_STATE` (record which); fuel unchanged; no journal entry |
| AO11b | A1 | Valid seated rocket, FUELED; owner in range does not BOARD; LAUNCH. | Record the code; if admitted, record that the owner's dimension and position are unchanged. The unoccupied-launch path was not read, so no code is asserted. |
| AO12 | A1 | Owner with one kit station at Earth: (a) a second kit on the Moon; (b) docked rocket LAUNCH to its own `Station(id)`; (c) warp to the Moon, then (b) again. | (a) `OWNER_LIMIT_REACHED`, registry unchanged, kit count recorded; (b) `INVALID_DESTINATION`, fuel unchanged; (c) owner record count 1, `orbitBody` Moon, (b) unchanged |

AO8 is unchanged; it is now the A-DEP-3 binding. Other AT3 cases change only by the renames.

## 4. Finding dispositions

| Finding | Disposition |
| --- | --- |
| A1 Medium | Addressed in proposed text: C1, AX09, AX13, AX18, PL2, AO12. Not closed. |
| A2 Medium | Addressed: C2, AX02c, AX07, AX08, AX16, AX17, AX19, PL1. Coverage narrowed and stated. Not closed. |
| A3 Medium | Addressed: C3, AX05, PL3, AO11a/b. Not closed. |
| A4 Low | Addressed: C4. Not closed. |
| REPORT-02 R1 High | Remains OPEN. |
| R2-R5, steel/bootstrap, witnesses, extraction/budgets, energy rows, native observations, inherited process deviations | Untouched; no claim. |

Closure needs a different-agent review, Root adoption, A-DEP-1..5 and these cases run.

## 5. Proof and dependency limits

- The argument is documentary: each slot maps to S1-S9. No model check or code ran.
- C2 holds only while admission ignores intermediate anchors and discovery reads only the
  destination (S6). If either changes, C2.3 needs slots.
- A-DEP-1 (synthetic station lookup) now also serves PL1-PL3. A-DEP-2 is unchanged. A-DEP-3 is AO8.
- A-DEP-4 becomes the anchors and connectivity of the effective catalog.
- New A-DEP-5: an adapter outcome that separates no route from search-limit or overflow errors, plus
  the value of `RouteLimits.MAX_EXPANDED_NODES` (not read).
- Open: A4-OPEN-1 (C1.5); A-OPEN-1..3 unchanged. Runtime states stay A1-only (AP3 7).
