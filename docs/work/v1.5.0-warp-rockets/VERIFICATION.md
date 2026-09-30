# V150-WARP-04 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `0416455` (WARP-03). Contract:
[ADR-044](../../decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md) §5.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag or human Gate approval. Independent review pending.

## Implemented scope

- **In-motion rule** (`RocketStationMotionRule`, rocket module). It is decided
  from the transfer journal and this session's recovery classification only: no
  entity query and no chunk load. A station region **blocks** a warp when:
  1. the journal is not operational;
  2. any journal record is not yet classified. Classified means live (a launch,
     countdown or descent driven this session) or settled (a source returned by
     recovery, or a landed arrival). This is global, as ADR-044 states;
  3. a record overlapping the region, through either its source or its
     destination snapshot in that Level, is in `DESTINATION_SPAWNED`,
     `PASSENGERS_TRANSFERRED` or `SOURCE_REMOVED`, or is a live `PREPARED`
     record. A `PREPARED` record returned to a stationary source (waiting for
     passengers) does not block;
  4. an overlapping `COMMITTED` record is still descending: game time is below
     its scheduled arrival (the destination flight data's state start) plus
     `DESCENT_TICKS` plus 20.

  Landed reservations do not block.
- **Wiring:**
  - `RocketTransferService.stationRegionInMotion` passes the Space Level region
    and the live/settled sets;
  - `RocketManager.stationRegionInMotion` is public;
  - the mod class installs it as the warp service's `StationRocketAuthority`
    when the rocket services initialize (common setup);
  - there is no new cross-module import (ADR-041 pattern). Before that, the
    port stays fail-closed.
- **Docked rockets move with the station:**
  - `PlanetaryFlightAdmission` no longer compares a station-sourced rocket's
    saved `current_body` with the station's orbit. The checks that remain: the
    current target is the station, the station at the rocket's position has that
    ID and a cataloged orbit body, and the Level and snapshot dimension match;
  - a rocket assembled on a station now records the station's current orbit
    body as `current_body`, instead of the Space body;
  - arrivals already record the planned station orbit.
- **Deletion:** the station deletion rocket guard now fails closed when the
  transfer journal is not operational (a blocked journal hides its records).

## Tests

- `RocketStationMotionRuleTest` (5):
  - a blocked journal, an unclassified record (even far away), and an empty
    journal;
  - live versus settled `PREPARED` records, at the source and the destination;
  - every spawn-to-commit phase blocking, whatever the classification;
  - a committed arrival blocking until landed plus the margin, and not after
    (even while it waits for passengers);
  - inclusive overlap edges, other Levels, and inverted bounds.
- `PlanetaryAdmissionGameTests.dockedRocketDepartsFromTheStationsNewOrbitAfterRelocation`:
  - a rocket docked at a station orbiting Earth, with `current_body` Earth;
  - the station is relocated to the Moon by the checked relocation (the warp
    commit primitive);
  - the rocket still carries Earth, yet the owner's departure to the Moon quotes,
    enters countdown with a persisted journal record, and cancels cleanly;
  - the station keeps its new orbit.

  The removed comparison would have refused this departure.
- `StationWarpGameTests`:
  - `countdownSurvivesOwnerLogoutAndTheRocketRuleIsWired`: the installed port is
    the rocket module's, not `FAIL_CLOSED`. Logging out drops a pending
    confirmation. A running countdown survives the owner's logout, commits
    (orbit Moon, balance debited once), and the online member is told;
  - `stationDeletionFailsClosedWhileTheTransferJournalIsBlocked`: with a blocked
    journal swapped in (not dirty, restored in `finally`), deletion is refused
    for the rocket-authority reason and the station remains.

The real rule is not exercised end-to-end in a GameTest: the shared GameTest
world's journal holds records from other rocket tests, so rule 2 makes its answer
depend on test order. The warp GameTests therefore use a stub port, and the rule
is proven by the unit test above. Native evidence with the real rule is WARP-05.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew compileJava` | Exit 0 |
| `gradlew test --tests "*RocketStationMotionRuleTest"` | Exit 0 |
| `clean build test runData runGameTestServer` run 01 | Exit 0, 3 m 21 s; `:test` executed, 1,013 JUnit / 182 suites, 0 failures; all 248 GameTests passed (245 plus the three new ones); `src/generated` unchanged after runData |

Main JAR `d1b021f7294d3baa37b6fd82e05cb14c80eba0ffa2ef782cf44d5b612a0d8295`; the API JAR is byte-identical to WARP-02/03
(`97d1aaad…`).

## Evidence archive

Source directory: `arce-v150-warp04-*` under the Windows Temp directory (named in
`evidence-archives.json`). `root-checks.zip` holds:
- the Gradle logs;
- the JUnit XML and GameTest logs;
- the source, generated-file and artifact hashes;
- the packaging script.

## Risks

- Rule 2 is global. One journal record that recovery cannot classify, because its
  entity chunks stay unloaded (`RETRY_LATER`), blocks every warp on the server
  until an operator loads the area or runs `/arce rocket recover <transfer_id>`.
  This is fail-closed by design (ADR-044), but it is an availability risk.
  WARP-05 diagnostics must show it.
- An existing station rocket keeps its old `current_body` (Space or the orbit it
  arrived at). Admission ignores it for station sources, so no migration is
  needed.
