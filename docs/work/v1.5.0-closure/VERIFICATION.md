# v1.5 closure (C5): runtime bridge, final independent review and fixes

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`. Base: `d45534e`.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`. This record is not
a Gate approval. The development handoff is [RELEASE-EVIDENCE](../../releases/v1.5.0/RELEASE-EVIDENCE.md).

## Runtime bridge

`StationRuntime` (the station deployment item's bridge) was cleared at every server stop
but installed only at mod construction. A second singleplayer world in the same game
session therefore reported the station service as unavailable. The rolling-machine,
precision-assembler and satellite bridges already reinstall at each start;
`StationManager.onServerAboutToStart` now does the same. Test:
`StationRuntimeLifecycleTest`.

## Final independent review

Two read-only reviewers reviewed the committed trees at `d45534e`. Each reran the full
build (1,051 JUnit and 257 GameTests, matching the recorded evidence), verified the
evidence archives and ran single-change mutations.
- **Area A** (authority and persistence): `cd3233e`, `b3d4cf3`, `50a0f42`, `a96569b`.
  Report: `independent-review-final-a.zip`.
- **Area B** (sky, MIG-01a, ORBIT-02/04, STATION-03/04): report
  `independent-review-final-b.zip`.

**Verdicts:** every area and commit is "accept with changes". There are no Critical or
High findings: A has 2 Medium, 6 Low and 5 Info; B has 3 Medium, 5 Low and 5 Info.

### Area B

| ID | Severity | Handling |
|---|---|---|
| B1 | Low | `StationSkyContextRuntime` holds the registered service. GameTest `theRegisteredServiceFollowsARealRespawnLevelRoundTripAndLogout` drives it only through its registered listeners: the tick pass, a real `PlayerList.respawn`, a real Level round trip and a real logout |
| B2 | Low | ADR-047 rev. 3 states the main-thread invariant. It is noted at the registration, and `NetworkProtocolPinTest` requires `consumerMainThread` for every message |
| B3 | Low | Quarantine case (`StationCheckedUpdateTest.aQuarantinedRegistryStillResolvesTheSkyContext`); stop-clearing assertion; V1 items and F8 dispositions in ADR-047 rev. 3. Correction: the orbit-sky packet's "Review handling" row for F7/F8 should read "partly" |
| B4, B5 | Info | Recorded |
| B6 | Medium | Native `native_missing_orbit_restart_check.py`. The v1.4 Mars station stays on its missing body through the upgrade and an ordinary save: the record is unchanged except for its schema version. A restart follows with Mars still hidden (reported again, `environment` unavailable). Mars is then restored while the station still orbits it (available again, record unchanged). The log's ADR-042 statement now cites this run |
| B7 | Info | Recorded |
| B8 | Medium | The same native run: the other station's owner sets gravity to 40% through the probe. `gravity_milli=400` is on disk after the save and read back after the restart and the restore (inspect and `environment`) |
| B9 | Medium | `StationWriteBudget`: server-wide spacing of checked station writes, `floor(records × N / 100)` ticks, with N from the config value `stations.checkedWriteTicksPer100Stations` (default 3). Expansion and gravity answer `REGISTRY_BUSY`, before a confirmation is taken; a due warp stays due. ADR-041 rev. 2 and ADR-044 §4 note. Tests: `StationWriteBudgetTest`, `CommonConfigTest`, GameTest `checkedWritesShareOneServerWideSpacing` |
| B10 | Low | Implementation log corrected. Correction: the orbit-environment packet's "ADR-041 is PROPOSED" is stale; ADR-041 is accepted (now rev. 2) |
| B11 | Info | Recorded in KNOWN-ISSUES |
| B12 | Low | `NOT_LOCAL_PLAYER` text and ADR-041 rev. 2 now say "`/execute` run by anyone else"; a player's own `/execute as @s` is accepted |
| B13 | Info | Recorded |

### Area A

| ID | Severity | Handling |
|---|---|---|
| A1 | Medium | The R7 change evaluated the "loaded" check for every unclassified record, and that check adds a ticket and loads chunks. Recovery now handles one record per call, chosen round-robin by a cursor (`RocketTransferRecoveryService.step`). A stuck record advances the cursor and no longer starves the rest, and at most one record's ends are loaded per call, as before R7. Tests: `RocketTransferRecoverySelectionTest` (counts the checks). Native: the C3 harness reran on the final JAR |
| A2 | Medium | `CommonConfigWiringTest` loads an in-memory config (no file, no watcher) and shows `warpEnabled`, both costs and the write spacing reaching the services. The repricing case moved to `StationWarpAbortGameTests.aRepricedCountdownAborts` on a private service. No GameTest writes the shared config file any more; run 01 logged no config-file change |
| A3 | Low | The matrix requires every allowed team cell to change the registry, with its own effect: inviting adds the candidate, removing by name or UUID removes that member only, accept and decline act on the invitee |
| A4 | Low | The matrix also compares every Level's force-loaded chunk set per cell. Loads through other ticket types remain a residual (holder counts only see synchronous loads) |
| A5 | Low | The elevator GameTest captures exactly one `ARCE_STATION_ELEVATOR_CHECK` line per command, with its result, and checks the `elevator` node's own requirement directly. The comment is corrected |
| A6 | Low | Recorded in KNOWN-ISSUES: no capacity tests yet for CELESTIAL and ROCKET_TRANSACTIONS, and block-entity item NBT can raise the journal's heap ratio (7.79 with survival chests, 53.9 with a creative item tag) |
| A7 | Low | The rejections GameTest runs the operator diagnostics and asserts no fold and no dirty registry |
| A8 | Low | `StationWarpGameTests` split: the abort and wait cases moved to `StationWarpAbortGameTests`. It is now 630 lines |
| A9 | Info | `NetworkProtocolPinTest` fails when a `messageBuilder` registration cannot be read (for example, a non-literal index) |
| A10 | Info | Recorded: "345/345" includes 9 not-applicable and 2 parse-only cells. VISIT uses the flight service's predicate; the packet path is covered by 3 other GameTests |
| A11 | Info | The recovery matrix is corrected in the handoff copy ([RECOVERY-MATRIX](../../releases/v1.5.0/RECOVERY-MATRIX.md)) |
| A12, A13 | Info | Recorded in KNOWN-ISSUES |

Reviewer A also reported that one of its mutation lanes left a Gradle daemon running in
its own export directory, and that its permission check refused to stop it. That is
outside this repository, and it is left for the maintainer.

These fixes were checked by the mutations below and by full and native reruns. They
were not given a further independent review; the candidate-bound audit is ACC-03.
Reviewer B kept ORBIT-04 in progress: the plan's native multi-station load and reload
items are not run, and they move to ACC-02 with reference hardware.

## Mutation checks of these fixes

Evidence: `mutations/` in `root-checks.zip`. Every mutation was killed:
- **JUnit, each alone:**
  - A1: scanning every record;
  - A2: the kill switch ignored;
  - B9: the budget never spacing.
- **GameTest, one combined run in which each mutation targets a different test:**
  - A3: `remove … uuid` removing the wrong member;
  - A5: the elevator audit line removed;
  - A7: the diagnostics folding;
  - B9: gravity ignoring the budget.

## Commands actually executed

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `./gradlew test --tests *StationRuntimeLifecycleTest` | exit 0 | `focused-unit-01.log` |
| 2 | focused tests after the B9 and B1-B3 changes | exit 1 (missing import), then exit 0 | `focused-unit-02.log`, `focused-unit-03.log` |
| 3 | `./gradlew runGameTestServer` | exit 0: 259 GameTests | `gametest-01.log`, `gametest-01/` |
| 4 | `./gradlew assemble publishMavenJavaPublicationToLocalProjectRepositoryRepository`, then `./gradlew -p compat-test-mod clean build -ParceVersion=1.20.1-1.5.0-dev` | exit 0 | `fixture-build-01.log` |
| 5 | `python native_missing_orbit_restart_check.py` | attempt 1 failed: a 33-character probe label, where the probe allows 32; attempt 2 passed on the pre-review-A JAR | `attempt-01-failed/`, `attempt-02-pass-superseded-jar/` |
| 6 | focused tests after the review-A changes | exit 0 | `focused-unit-04.log` |
| 7 | `./gradlew runGameTestServer` | exit 0: 260 GameTests, matrix 345/345 | `gametest-02.log`, `gametest-02/` |
| 8 | `python c5_mutate.py` | 3 JUnit mutations killed; combined GameTest run exit 1 with the 4 targeted tests failing | `mutations/` |
| 9 | `./gradlew clean build test runData runGameTestServer` (full run 01) | exit 0: 1,058 JUnit tests, 0 failures, 0 errors, 0 skipped; all 260 required GameTests passed; matrix 345/345; `git diff --exit-code -- src/generated` exit 0; no config-file rewrite logged | `root-gradle-01.log`, `run-01/`, `junit-summary.json`, `artifacts.json`, `generated-diff.log` |
| 10 | `python native_missing_orbit_restart_check.py`, attempt 3, on the final JAR `26a25d02…` | exit 0 | `attempt-03/native/` |
| 11 | `python native_mig_c3_check.py` rerun on the final JAR | exit 0 (in-flight recovery through the cursor, concurrent warps, kills, S1) | `c3-rerun/native/` |
| 12 | repository validators, `unittest`, `check_client_imports.py` and `git diff --check` (before staging) | see `validation.log` | `validation.log` |

## Evidence archive

`root-checks.zip` holds every run, attempt, mutation and script (server copies
excluded). One tested file changed after full run 01: `DOCUMENT-INDEX.md` gained the
handoff entry and ADR-041's revision. No Gradle test reads it; the planning validator,
which does, ran afterwards. `post-run-changes.json` records both hashes, and the
packer checks them. The two final reviews are archived with:
- their reports, mutation tables, logs and scripts;
- excluding their exported trees, re-extracted evidence copies and decompiled
  Minecraft/Forge sources.

Both reviews are listed in `evidence-archives.json`.
