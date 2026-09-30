# v1.5 elevator endpoint and command-control authority (C2)

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`. Base: `cd3233e`.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`. This record
is not a Gate approval. Contracts: [ADR-045](../../decisions/ADR-045-SPACE-ELEVATOR-ENDPOINT-CONTRACT.md)
and [ADR-046](../../decisions/ADR-046-V150-STATION-CONTROLS-ARE-COMMANDS.md), both revision 2.

## Scope

**Implemented:**
- **V150-ELEVATOR (ADR-045):**
  - `station/elevator/ElevatorEndpointValidator` is a pure function over a snapshot
    (registry availability, station view, requester authority, catalog captured
    once, Level presence, border). It checks rules 1-5 in order and reports the
    first failure (`ElevatorEndpointCode`).
  - `ElevatorEndpointService` builds the snapshot on the server and writes one
    `ARCE_STATION_ELEVATOR_CHECK` audit line. It reads only: no chunk, no dirty
    SavedData, no persistence.
  - `/arce station admin elevator check <station_id> <body_id> <x> <z>`: its own
    level-2 requirement on the `elevator` literal, and x/z bounded at parse to
    ±30,000,000. It prints one line: `valid`, or the rule number, code and reason.
  - `StationRegistrySavedData.updatesAvailable()` (operational and not
    quarantined) is rule 1, pinned by the quarantine unit test.
- **V150-UI-01:** `NetworkProtocolPinTest` reads every channel in the main sources
  and compares its name, protocol version and registered messages with the
  committed table `src/test/resources/network-protocols.txt`. There are four
  channels and six messages; nothing is added by the station controls.
- **V150-UI-03 (A1):**
  - `StationPermissionMatrixGameTests` asserts all 345 cells of the expected-outcome
    table (23 actions × 15 actors, `StationPermissionMatrix` table revision 1). It
    uses the real commands, the block-break event and the flight service's VISIT
    predicate.
  - Each cell also checks the chunk-holder counts of every Level, and that only
    allowed team cells changed the registry (reset and verified).
  - Report: [PERMISSION-MATRIX.md](PERMISSION-MATRIX.md).
- **Offline removal:** `/arce station remove <station_id> uuid <member_uuid>`. It
  has the same authority as removing an online player.
- **Confirmation order:** expansion and warp confirmations now run the local-actor
  check (position, authority) before the confirmation is taken. An actor who
  cannot act gets `UNAUTHORIZED` or `NOT_IN_STATION`, as ADR-046's table
  requires, not "no confirmation pending". No confirmation can be consumed by an
  actor who could not use it.
- **Caps:** `StationTeamCapsTest` covers 32 members and 32 invitations, and checks
  that inviting the owner, a member or an invitee changes nothing.

**Not done here:**
- S1 permission subset on a native server: planned for C3.
- V2 two-client evidence: open for ACC-02.
- Elevator storage, structure and transport: v1.7 (ADR-045 constraints).

## Results

1. **Elevator:**
   - `ElevatorEndpointValidatorTest` (7 tests) covers:
     - each rule in order, with every later rule also failing;
     - authority before disclosure (no station view on `UNAUTHORIZED`);
     - an unknown station, even for an operator;
     - a missing or non-landable body;
     - a remapped Level and a shared Level;
     - inclusive bounds, including `Integer.MIN_VALUE`;
     - a border asked for the body's own Level;
     - warp invalidation.
   - `StationElevatorGameTests` runs on the live server:
     - one reply line: `valid`, unknown station, not the current orbit;
     - parse bound at 30,000,001;
     - world border shrunk to 2,000 blocks, then restored;
     - a remapped Level, a non-landable body, owner allowed and non-owner refused,
       all on private catalogs;
     - no dirty registry and unchanged loaded-chunk counts in every Level;
     - a non-operator cannot reach the command;
     - after a real checked relocation to the Moon, Earth fails rule 3 and the
       Moon is valid.
   - Run 02 logged 15 `ARCE_STATION_ELEVATOR_CHECK` lines.
2. **Matrix:** 345 of 345 cells pass (336 executed, 9 not applicable). The only
   registry changes are the allowed team cells, each reset and verified.
3. **Operator create/delete:** loads 4 full chunks in the new cell's region and
   nothing outside Space. The Space holder count rises by 676 on a
   never-generated cell because of vanilla's light ticket (see the report). The
   console's create reused the cell, and its delta is `none`.

## Mutation checks

Evidence: `mutations/` in `root-checks.zip`.

| Mutation | Outcome |
|---|---|
| V1: authority checked after the orbit rule | killed (`ElevatorEndpointValidatorTest`) |
| V2: a shared Level accepted | killed |
| V3: the border ignored | killed |
| G2: expansion confirm takes the confirmation before the authority check | killed: 8 matrix cells (`NO_CONFIRMATION` instead of `UNAUTHORIZED`/`NOT_IN_STATION`) |
| G3: the same for warp confirm | killed: 8 cells |
| G4: `uuid` removal branch removed | killed: all 15 `REMOVE_UUID` cells |
| G1: the `elevator` literal's own requirement removed | **survived**: the shared `admin` node already requires level 2 today, so the leaf check is defence in depth against a registration-order change. Recorded, not hidden |

G1 to G4 ran as one combined GameTest run, in which each mutation targets
different cells: 31 cells failed.

## Commands actually executed

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `./gradlew test` (validator, protocol pin, checked update) | exit 1: `ElevatorEndpointValidatorTest` read `CelestialIds` in a static initializer before the Minecraft bootstrap. That broke registry initialization for the next test class in the same JVM. The constants became instance fields | `focused-unit-01.log` |
| 2 | the same | exit 0 (7 + 1 + 12 tests) | `focused-unit-02.log` |
| 3 | `./gradlew runGameTestServer` | exit 1: the non-operator elevator check got Brigadier's "Incorrect argument" (the `admin` node's requirement) rather than "Unknown"; the test now asserts that the command does not parse for that source. One matrix cell: create/delete Space holders +720 (see below) | `gametest-01.log`, `gametest-01/` |
| 4 | the same | exit 1: create/delete Space holders +676 after the stabilization wait. Also, the first audit capture attached its appender to the mod's logger, which created a new logger configuration and cut mod log lines off from `latest.log` in runs 3 and 4. The capture now attaches to the existing configuration | `gametest-02.log`, `gametest-02/` |
| 5 | the same | exit 1: +676 remained with a warmed cell, because loading a chunk adds a light ticket. The cell is now measured by full chunks in its region, and Space holders are recorded | `gametest-03.log`, `gametest-03/` |
| 6 | the same | exit 0: all 256 GameTests; matrix 345/345 | `gametest-04.log`, `gametest-04/` |
| 7 | `python c2_mutate.py` | 3 JUnit mutations killed; combined GameTest run exit 1 with 31 cells failing | `mutations/` |
| 8 | `./gradlew clean build test runData runGameTestServer` (full run 01) | exit 0: 1,040 JUnit, 256 GameTests; generated diff exit 0 | `root-gradle-01.log`, `run-01/` |
| 9 | `./gradlew test --tests *StationTeamCapsTest` | exit 0 (2 tests) | `focused-unit-03.log` |
| 10 | `./gradlew clean build test runData runGameTestServer` (full run 02, after the caps test) | exit 0: 1,042 JUnit tests, 0 failures, 0 errors, 0 skipped; all 256 required GameTests passed; matrix 345/345; `git diff --exit-code -- src/generated` exit 0 | `root-gradle-02.log`, `run-02/`, `junit-summary.json`, `artifacts.json`, `generated-diff.log` |
| 11 | repository validators, `unittest` and `git diff --check` (before staging) | see `validation.log` | `validation.log` |

## Evidence archive

`root-checks.zip` holds:
- the Gradle and GameTest logs of every run, including the failed ones;
- JUnit XML;
- hashes;
- the mutation logs;
- the scripts, including the report generator and the packaging script.

`PERMISSION-MATRIX.md` is generated from run 02's log by
`scripts/c2_matrix_report.py`.
