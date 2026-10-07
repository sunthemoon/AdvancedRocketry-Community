# C16d graph access test design 03

Companion to [ACCESS-PROPOSAL-03](ACCESS-PROPOSAL-03.md) (abbreviated AP). Status: PROPOSAL.
**Every case below is unrun.** No test class, fixture or GameTest exists. A0 cases are pure
synthetic graph cases for the future graph JUnit suite; Root names the class. A1 cases are
real-server GameTest or packaged-server observations. A1 cases also bind runtime facts that the
graph does not model (AP §7 item 5).

The superseded TEST-DESIGN-02 rows are only the access expectations of X01, X02, X03, X09.
All other TEST-DESIGN-02 rows are untouched.

## 1. Shared A0 fixture

Bodies (all in the catalog):

| Body | Surface Level | Orbitable | `discovery_required` | Notes |
| --- | --- | --- | --- | --- |
| `t:earth` | Overworld | yes | no | |
| `t:moon` | own | yes | no | |
| `t:far` | own | yes | yes | other system |
| `t:rock` | own | no | no | |
| `t:space` | none | n/a | n/a | Space body; no surface arrival |

Routes, both directions each:

| Route | Anchors |
| --- | --- |
| `t:earth_orbit` | surface `t:earth` ↔ orbit `t:earth` |
| `t:earth_moon` | surface `t:earth` ↔ surface `t:moon` |
| `t:moon_orbit` | surface `t:moon` ↔ orbit `t:moon` |
| `t:earth_far_orbit` | orbit `t:earth` ↔ orbit `t:far` |
| `t:far_surface_orbit` | surface `t:far` ↔ orbit `t:far` |

Blueprints (synthetic A-DEP-2 facts):

| Blueprint | Seats | Footprint | Other |
| --- | --- | --- | --- |
| `r1` | 1 | 3 x 3 | at most the pad |
| `r_wide` | 1 | (2R+2) x 3, R = `PLATFORM_RADIUS` | |
| `r_noseat` | 0 | 3 x 3 | |

All quotes READY unless a case says otherwise.

Root items at round 0, when a case lists them: `kit`, `warp_core`, `r1.parts`, `r1.fuel`. The
same applies to `r_wide` and `r_noseat` parts and fuel. `cap:surface:t:earth` is round 0.

Synchronous rule as CONTRACT-02 §7.1: a producer is enabled in round k >= 1 from members of
rounds < k. Expected rounds below follow that rule.

**Synthetic seed:** a test-only capability root that the case declares. It is used only to
isolate one producer, and the report must label it `synthetic_seed`.

Observation fields recorded for every capability:

- `id`, `available`, `round`;
- `chosen_producer`;
- `blockers` (sorted ids);
- `notes` (sorted, for example `relocated_by_warp`, `synthetic_seed`).

For every producer row: `row_id`, `enabled`, `disabled_reason` (`no_seat`,
`station_pad_footprint`, `binding_unfrozen` or none). For a rejected run: the error code and the
offending row/slot, before any report.

## 2. A0 cases

| ID | Fixture | Expected (exact fields) |
| --- | --- | --- |
| AX01 (R1 negative: kit without rocket; replaces X01 access) | roots `kit`; no blueprint parts or fuel; no `warp_core`. A producer `t:local` bound to `cap:station_aboard:t:earth`. | `cap:station_exists:t:earth` available, round 1, via `code:station_create:t:earth`. `cap:station_aboard:t:earth` unavailable; blockers include `r1.parts`, `r1.fuel`. `t:local` output unavailable. `cap:station_aboard:*` and `cap:surface:t:far` unavailable. |
| AX02a (R1 negative: raw orbit output) | A row whose output or slot names `cap:orbit:t:moon`. | Run rejected `unsupported_capability:orbit`, naming that row. No report. |
| AX02b (raw orbit quote) | An adapter row configured to quote `TravelTarget.Orbit(t:earth)`. | Run rejected `orbit_target_quote`, naming the row. No report. |
| AX02c (orbit route without a station) | roots `r1.*`; no `kit`. | No P2 row for the `surface:t:earth -> orbit:t:earth` direction. Row `code:station_board:surface:t:earth->station:t:earth` enabled with blocker `cap:station_exists:t:earth`. `cap:station_aboard:t:earth` unavailable. `cap:surface:t:moon` available, round 1 (via `t:earth_moon`). |
| AX03 (R1 positive: existing-station boarding) | roots `kit`, `r1.*`; `t:local` as AX01. | `cap:station_exists:t:earth` round 1. `cap:station_aboard:t:earth` round 2 via `code:station_board:surface:t:earth->station:t:earth`. `t:local` output round 3. |
| AX04 (pad footprint) | roots `kit`, `r_wide.*`. | Every P3 row for `r_wide` disabled `station_pad_footprint`. `cap:station_aboard:t:earth` unavailable. `cap:surface:t:moon` available round 1 (P2 has no pad predicate). |
| AX05 (seat) | roots `kit`, `r_noseat.*`. | Every P2 and P3 row for `r_noseat` disabled `no_seat`. `cap:surface:t:moon` and `cap:station_aboard:t:earth` unavailable. `cap:station_exists:t:earth` round 1. |
| AX06 (R1 negative: warp bootstrap) | roots `kit`, `warp_core`; seeds `cap:discovered:t:far`, `cap:fe:warp_balance`; no rocket roots. | `code:warp:t:earth->t:far` blocked by `cap:station_aboard:t:earth`. `cap:station_aboard:t:far` and `cap:station_exists:t:far` unavailable. |
| AX07 (warp positive; replaces X02 access) | AX03 plus `warp_core`; seeds `cap:discovered:t:far`, `cap:fe:warp_balance`. Station-source binding declared frozen (synthetic A-DEP-3). | `cap:station_aboard:t:earth` round 2. `cap:station_aboard:t:far` and `cap:station_exists:t:far` round 3 via `code:warp:t:earth->t:far`. `cap:surface:t:far` round 4 via `route:t:far_surface_orbit:station:t:far->surface:t:far`. `cap:station_*:t:earth` carry `relocated_by_warp`. |
| AX07b | AX07 with the station-source binding unfrozen. | Run rejected `binding_unfrozen` (A-DEP-3), naming the first station-source row. |
| AX08 (existence is not departure) | seed `cap:station_exists:t:moon` (synthetic); roots `r1.*`; no `kit`; station-source binding frozen. | `cap:surface:t:moon` round 1, via `t:earth_moon`. `cap:station_aboard:t:moon` round 2, via `code:station_board:surface:t:moon->station:t:moon`. Row `route:t:moon_orbit:station:t:moon->surface:t:moon` is first satisfied in round 3; at rounds 1-2 its blocker is `cap:station_aboard:t:moon`. The seeded existence alone never satisfies it. |
| AX09 (discovery of station body) | seeds `cap:station_exists:t:far`, `cap:station_aboard:t:earth`; roots `r1.*`; no discovery; station-source binding frozen. | `code:station_board:station:t:earth->station:t:far` (route `t:earth_far_orbit`) blocked by `cap:discovered:t:far`. Adding seed `cap:discovered:t:far` makes `cap:station_aboard:t:far` available round 1. |
| AX10 (anywhere in Space; replaces X09) | producer `t:any` with one OR slot over every `cap:station_aboard:<B>`; (a) AX01 roots; (b) AX03 roots. | (a) unavailable, although `cap:station_exists:t:earth` is available. (b) available round 3. |
| AX11 (creation rows) | full fixture. | P1 rows exist exactly for `t:earth`, `t:moon`, `t:far`. None for `t:rock` (not orbitable), `t:space` (no surface Level), Nether or End. |
| AX12 (owner limit) | (a) `MAX_OWNED_STATIONS` = 1 fixture; P1 is the only producer of needed `station_exists` for `t:earth` and `t:moon`. (b) `MAX_OWNED_STATIONS` = 0. | (a) report note `owner_station_limit`; availability identical to the same case with limit 2. (b) run rejected `binding_unfrozen`. |
| AX13 (simultaneous context) | a row with slots `cap:station_aboard:t:earth` and `cap:station_aboard:t:moon`. | Run rejected `simultaneous_context_unsupported`. |
| AX14 (retired name) | a row referencing `cap:station:t:earth`. | Run rejected `retired_capability:station`. |
| AX15 (quote failure keeps the row blocked) | AX03 with the P3 quote returning `INSUFFICIENT_CAPACITY`. | P3 row enabled but unsatisfied, blocker `quote:INSUFFICIENT_CAPACITY`. `cap:station_aboard:t:earth` unavailable. |

Proof obligations a later implementation test should add (not designed here):

- monotonicity: adding roots never removes a capability;
- invariant I1: every derivation path to `cap:station_aboard:*` contains a P3 row, checked by
  walking `chosen_producer` over every A0 fixture.

## 3. A1 observations (real server, unrun)

Actor: one non-operator survival player. These need Root-granted GameTest/server runs. Record:

- code version;
- the exact `RocketFlightRequestCode` / `StationCreationCode` / `StationManagementCode`;
- before/after values of each named field;
- raw logs.

| ID | Steps | Expected | Binds |
| --- | --- | --- | --- |
| AO1 | Player on the Overworld uses the deployment kit. | Code success. Registry +1 committed station: owner = player, `orbitBody` = Earth. Player dimension and block position unchanged (not in the Space Level). Kit count -1. | P1 output is existence only. |
| AO2 | Same, but the player stands in the Space Level, then the Nether. | `INVALID_SOURCE` both times. Kit unchanged. No registry change. | AX11 |
| AO3 | X10 retained: kit on the Moon surface. | New station `orbitBody` = Moon. | P1 body choice |
| AO4 | FUELED owned rocket on the Overworld; launch intent with destination `TravelTarget.Orbit(earth)` through the real C2S path. | `INVALID_DESTINATION` (`RocketFlightService.java:258-259`). Fuel unchanged. No transfer journal entry. If the wire codec cannot encode Orbit, record the decode rejection instead and say so. | AX02 |
| AO5 | Owner seated in an `r1`-like rocket on the Overworld; own Earth-orbit station exists; launch `Station(id)`. | Success. After arrival: player in the Space Level; `findAt(player x, z)` = that station; rocket region on the station pad; fuel debit = quoted fuel. | AX03 (P3) |
| AO6 | As AO5 to another owner's station, not a member. | `UNAUTHORIZED`. Station absent from the navigation snapshot. | ownership context, A-OPEN-1 |
| AO7 | As AO5 with a rocket wider than the pad. | `LANDING_PAD_UNAVAILABLE`. Fuel unchanged. | AX04 |
| AO8 | After AO5, refuel the docked rocket in the Space Level; launch `BodySurface(earth)`. | Fuel loading succeeds (record the method). Launch admitted with a Station source. Player arrives on the Overworld. | A-DEP-3 / AX07 |
| AO9 | Warp request (a) owner outside the region, (b) owner in the region not looking at the core, (c) owner looking at the core with balance and a known target, then confirm and wait out the countdown. | (a) locate failure code recorded. (b) `NO_WARP_CORE`. (c) `WARP_ISSUED`; afterwards station `orbitBody` = target and the player is still inside the region. | P4 |
| AO10 | Navigation snapshot for AO5's rocket. | Entries: surface-arrival bodies plus owned/visitable stations; zero Orbit entries. Record entry count vs `MAX_QUOTES`. | AX10, A-OPEN-2 |
| AO11 | Seatless rocket: `BOARD`, then the owner launches from outside. | `NO_SEAT_AVAILABLE`. The rocket travels and the player stays at the source. | AX05 |

## 4. Not covered

- Throughput, energy witness rows, discovery arithmetic: TEST-DESIGN-02 §2/§4, unchanged.
- R2-R5 cases.
- Multiplayer membership and dedicated-server restart.
- Recovery of a warp interrupted mid-transfer.
- V1/V2 visuals.

None of these is claimed.
