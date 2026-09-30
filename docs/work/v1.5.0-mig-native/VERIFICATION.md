# v1.5 native rocket identity, concurrent warp and S1 (MIG-01b, MIG-02, UI-03 S1)

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`. Base: `b3d4cf3`.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`. This record
is not a Gate approval.

## Scope

- **MIG-01b (ADR-042 item 2):** native preservation of rocket identities that touch a
  station, through a real upgrade from v1.4. Together with MIG-01a
  ([missing orbit body](../v1.5.0-mig-missing-orbit/VERIFICATION.md)), both ADR-042 cases
  now have native evidence.
- **MIG-02:** two stations warp at once to different targets; a killed countdown; a
  commit killed right after its line.
- **UI-03 S1:** the ADR-046 subset natively. See [PERMISSION-S1.md](PERMISSION-S1.md).
- **MIG-01 recovery matrix:** [RECOVERY-MATRIX.md](RECOVERY-MATRIX.md).
- **WARP review R7 (first half):** transfer recovery takes the first unclassified
  record whose ends are loaded (`RocketTransferRecoveryService.nextRecoverable`), so a
  record that cannot be loaded no longer starves the others.
- **Test hygiene:** two warp GameTests switched the shared `stations.warpEnabled` config
  value. That rewrites the config file, and Forge's file watcher can re-apply a stale
  value on another thread. In full run 01 this left warp disabled for two later tests.
  Both tests now use private warp services with their own settings and keep their
  assertions. The rejections test also asserts that the server's settings come from the
  default config.
- **Fixture probe:** the allow-list covers team commands between probe players (including
  removal by UUID), `accept`/`decline`, `environment`/`list` and one admin command.
  Up to 256 replies are kept per player.

## Native run

`native_mig_c3_check.py` (attempt 10, exit 0) uses a copy of the retained v1.4 world
(`arce-v140-mig-worlds…/server-final/world`). It contains two v1.4 stations and no
rocket transfers. The source world is verified unchanged at the end. Artifacts:
- v1.4 host `576eaaf8…` and its fixture `e58de48f…`, both as recorded by that world's
  run;
- v1.5 host `a12db9de…`, the JAR of full run 02;
- rebuilt fixture `db6e890b…`.

| Phase | Host | End | Result |
|---|---|---|---|
| `legacy` | v1.4 with release-test hooks | killed after a durable save (exit 1, intended) | The v1.4 host created stations A and C (Earth orbit). It assembled two rockets on Earth and flew them in: R1 docked at A. R2 departed from C to Earth, frozen at `DESTINATION_SPAWNED`. The journal holds R1's COMMITTED record and R2's in-flight record |
| `upgrade` | v1.5 | stop (exit 0) | `[ARCE-BETA-1002] … migrated=1, backup=…`. R2 was recovered forward once both ends were loaded (`REMOVE_SOURCE_KEEP_DESTINATION`): exactly one rocket, same logical identity, fuel 670, landed at Earth. R1 was unchanged. S1: 23 player cells and the console, all PASS. The owner warped A (with R1 docked) to the Moon through the real countdown and the production rocket rule; R1 was unchanged |
| `concurrent` | v1.5 | killed after a durable save (exit 1, intended) | R1 was unchanged after the restart. A (Moon → Earth) and C (Earth → Moon) were confirmed together and both committed in the same second, each charged 2,000,000 FE once. A new request was refused by the 100-tick cooldown, then accepted. A third countdown was started and the process was killed |
| `interrupted` | v1.5 | killed right after a commit line, with no save (exit 1, intended) | The killed countdown left nothing: `countdowns=0 pending_energy=0`, and A kept its orbit and its 2,000,000 FE. C's warp committed and the process was killed at once. On disk: C orbits Earth, and the debit is applied |
| `final` | v1.5 | stop (exit 0) | The registry is unchanged since the kill. R1 is unchanged (identity, snapshot, fuel 670, position). `transfer_journal=operational records=2 live=0 settled=2 unclassified=0` |

Retained failed attempts (`attempt-01` to `attempt-08`) and what each taught:
1. The pad chunk was unloaded, so R1 was not found.
2. `FlightHarness.configure_rocket`'s `kill @e[type=rocket]` selects rockets in **every**
   dimension and killed the docked R1. The harness now assembles without it.
3. Unidentified registry mappings: the world was last run with the v1.4 fixture.
4. Legacy phase only (debug stop).
5. The migration line is `ARCE-BETA-1002`.
6. R2 was still descending above its landing origin.
7. Landed reservations stay in the journal as COMMITTED records.
8. The post-commit cooldown refused the next request, which is correct.

Attempt 9 passed with the JAR from before the GameTest fix. It is kept, and superseded by
attempt 10 on the final JAR.

## Commands actually executed

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `./gradlew build publishMavenJavaPublicationToLocalProjectRepositoryRepository`, then `./gradlew -p compat-test-mod clean build -ParceVersion=1.20.1-1.5.0-dev` | exit 0 | `fixture-build-01.log` |
| 2 | `./gradlew test --tests *RocketTransferRecoverySelectionTest --tests *RocketStationMotionRuleTest` | exit 0 (2 + 7 tests) | `focused-unit-01.log` |
| 3 | `./gradlew assemble` | exit 0 | `assemble-01.log` |
| 4 | `python native_mig_c3_check.py`, attempts 1-8 | failed; each retained with its cause (above) | `attempt-0N-*/` |
| 5 | the same, attempt 9 | exit 0 on the pre-fix JAR `3544396b…` | `attempt-09-pass-superseded-jar/` |
| 6 | `./gradlew clean build test runData runGameTestServer` (full run 01) | exit 1: 1,044 JUnit passed; 2 GameTests failed because warp stayed disabled (config file watcher race, above) | `root-gradle-01.log`, `run-01/` |
| 7 | the same (full run 02, after the test fix) | exit 0: 1,044 JUnit tests, 0 failures, 0 errors, 0 skipped; all 256 required GameTests passed; matrix 345/345; generated diff exit 0 | `root-gradle-02.log`, `run-02/`, `junit-summary.json`, `artifacts.json`, `generated-diff.log` |
| 8 | `python native_mig_c3_check.py`, attempt 10 (final JAR) | exit 0 | `attempt-10/native/` |
| 9 | repository validators, `unittest` and `git diff --check` (before staging) | see `validation.log` | `validation.log` |

## Evidence archive

`root-checks.zip` holds every run, attempt, script and log. `station-server/` world
copies and libraries are excluded; each phase's managed data files are kept under
`native/<phase>/state/`.
