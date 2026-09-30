# V150-WARP-05 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `1a5763a` (review fixes). Contract:
[ADR-044](../../decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md) §3, §4, §6 and §8.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag, real-client result or human Gate approval.
Independent review pending.

## Implemented scope

- **Operator diagnostics:**
  - `/arce station admin warp [station_id]` (permission level 2, read-only,
    bounded) prints:
    - one summary line: settings, registry operational/quarantined, pending
      stations and energy, confirmations, countdowns;
    - one line from the rocket port;
    - one line per countdown (at most 64);
    - for a station: its orbit, balance, pending credit, cooldown readiness,
      whether rockets are in motion, and whether a countdown is running.
  - The rocket module's line reports the transfer journal: blocked, or its
    records, live, settled and **unclassified** counts. This makes ADR-044 §5
    rule 2's availability risk visible.
  - `/arce station admin inspect` now ends with `warp_energy=`.
- **Native tooling:** the opt-in adapter-fixture probe
  (`-Darce_adapter_test.stationSmoke=true`) can now:
  - run the warp commands as its connected mock players;
  - aim a player at a block;
  - push Forge Energy into a block through the public capability, simulated and
    then real, as a producer mod would.

  It still uses no host internals. The fixture README documents it.

## Tests

- `StationWarpGameTests.tenChargingStationsDirtyTheRegistryOnlyOncePerFoldWindow`
  (ADR-044 §8, plan §13.3):
  - ten stations are charged every tick for 400 ticks;
  - the registry is dirtied only by folds (1 to 3 times, never per tick);
  - folded plus pending energy equals exactly what the cores accepted;
  - the operator diagnostics show the summary, the transfer-journal line and the
    station line.
- `StationCheckedUpdateScaleTest.warpCommitAndFullBalanceSaveStayWithinTheSpikeBudget`
  (§13.2–13.4): a checked warp commit and an ordinary save, with every station
  holding a balance, at 10, 100 and 4,096 stations. Both stay within the 500 ms
  spike budget. Measurements are in `ARCE_WARP05_SCALE` lines in the JUnit XML.

## Native evidence

`native_station_warp_check.py` copies the world left by the WARP-02 native run:
root 4, the witness station orbiting the Moon, with an owner, a member, an
invitee and a 768 region. It first verifies the world's managed files against
that run's recorded end state. Three finite dedicated-server processes then ran
with the WARP-05 host and the rebuilt fixture.

1. **warp**:
   - no migration;
   - the console places a warp core beside the pad;
   - a charge of 300,000 FE is simulated and accepted as 200,000 (the per-tick
     allowance), and the core reports 0 stored and no extraction;
   - ten more charges; status shows 2,200,000 FE;
   - the member's request is refused (owner or operator only), and so is
     `execute as <owner>` (not local);
   - the owner, aiming at the core, gets the in-system quote (2,000,000 FE);
     the request alone changes nothing on disk;
   - after confirmation, the real 10-second countdown commits
     (`WARP_COMMITTED`) through the production rocket rule;
   - the station file read before `save-all` equals the source payload with only
     the orbit (Moon to Earth) and the balance (200,000) changed;
   - `admin inspect` and `admin warp` agree, with `transfer_journal=operational
     … unclassified=0` and `rockets_in_motion=false`;
   - one more charge right before the stop: the file after `save-all` still held
     200,000, and after `stop` it held 400,000, so the **stop fold** persisted it.
2. **restart**:
   - Earth orbit and 400,000 FE persisted, and the core block is still present;
   - a request is refused for insufficient energy, with the numbers;
   - after charging to 2,200,000, a countdown is confirmed and the server is
     stopped in an orderly way at once. No commit line appears.
3. **restart-2**:
   - still Earth orbit with 2,200,000 FE: the interrupted countdown neither
     warped nor charged;
   - `admin warp` shows no countdown or pending credit;
   - the registry is unchanged, every other authority is unchanged (visit
     records only), no migration backup was created, and the source world is
     unchanged.

Every phase had 0 ERROR, 0 FATAL and 0 project WARN. The first phase's 21 WARN
lines are:
- the fresh server's config defaults;
- offline mode;
- loader notices;
- Forge assigning new registry IDs to `warp_core` in a world saved before the
  block existed (expected when adding content).

Attempt 01 failed on a harness arithmetic error: in phase 2 it expected
2,400,000 instead of 400,000 + 9 × 200,000 = 2,200,000. Its first phase had
passed. It is retained in `attempt-01-failed/`; attempt 02 passed.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew compileJava compileTestJava` | Exit 0 |
| `clean build test runData runGameTestServer` run 01 | Exit 1: the fold GameTest failed ("Cores stopped accepting: 20000"). Its ten fixture chunks were loaded only during setup and unloaded after about two ticks, which removed the cores' block entities. The fixture now holds a region ticket for the test (test setup only) |
| same, run 02 | Exit 0, 3 m 14 s; `:test` executed, 1,021 JUnit / 184 suites, 0 failures; all 249 GameTests passed; `src/generated` unchanged |
| `gradlew publishMavenJavaPublicationToLocalProjectRepositoryRepository` | Exit 0 |
| `gradlew -p compat-test-mod clean build -ParceVersion=1.20.1-1.5.0-dev` | Exit 0; fixture `e3cddd87ea54bbc75380b9dccfd3d3cead21fc815cbe59d2d7e5301f994ff2d1` |
| `python native_station_warp_check.py` attempt 01 | Exit 1 (harness arithmetic, above) |
| same, attempt 02 | Exit 0; PASS as above |

Main JAR `a99f78c0d63ceccbcfd821bb4f4aea09db5b52dd960ee9b3a91c45a28b95bce2` (installed by the native run); the API JAR is byte-identical
(`97d1aaad…`).

## Evidence archive

`root-checks.zip` holds:
- both Gradle runs, the JUnit XML and GameTest logs;
- the publish and fixture build logs;
- the native harness, both attempts' outputs (per-phase stdout, logs, receipts,
  commands, probe reports, decoded state, and the checked write read before
  save), and the summary;
- hashes and the packaging script.

The disposable `station-server/` copies are excluded.

## Not covered; risks

- There is no real networked client, no V1/V2 visual check and no multiplayer
  UI; the per-station sky is ORBIT-03.
- The native stop is orderly; power loss during the checked write is covered
  only by the WARP-02 fault-injection tests.
- No native docked rocket crossed a warp, because the fixture world has no
  rocket. The admission change is GameTested (WARP-04), and a native rocket case
  remains with MIG-01, as ADR-042 assigns.
- Catalog reload or target removal during a countdown is not exercised natively.
