# v1.0.0 development stabilization verification

Date: 2026-09-05. Branch: `codex/v1.0.0-stable-core`.
Base commit: `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`.
This is development evidence, not an independent review, approved Gate or release.

## Artifact and environment

- Build: `1.20.1-1.0.0-dev`, 1,225,820 bytes, 758 entries.
- SHA-256: `5b5d942a1c807a62ec030bc2150f246a7de6d5f78cd2ed3698db3445a53d2096`.
- [Content manifest](artifact-manifest.json), [source inventory](source-inventory.json).
  The tree is uncommitted; the base commit alone does not identify these bytes.
- Windows 11, Java 17.0.7, Gradle 8.8, Forge 47.4.10, JEI absent.
- Python: `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe`.
  `PYTHONUTF8=1`, process-local `TEMP`/`TMP` use
  `C:\Users\Administrator\AppData\Local\Temp`.
- Gradle uses process-local `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7` and
  its `bin` on PATH. No global Python or Java configuration was changed.

## Actual commands and outcomes

Commands ran from the repository root. Python commands below use the full
interpreter above. Windows `gradlew.bat` runs the required Wrapper tasks.

| Command | Exit/result | Evidence |
|---|---|---|
| `gradlew.bat test --tests '*PopulatedWorldDataPreservationTest'` | 0; 18 constructed-state cases | [Log](populated-state-tests.txt) |
| `gradlew.bat clean build` first run | 1; metadata test still expected Beta | [Failure](clean-build-dev.txt) |
| `gradlew.bat clean build` after exact metadata/checkpoint updates | 0; 35 s, 304 tests, zero failures/errors/skips | [Log](clean-build-dev-retry.txt), [JUnit XML](junit/) |
| `gradlew.bat test` | 0; 15 s, up-to-date | [Log](test-dev.txt) |
| `gradlew.bat runData` | 0; 33 s, no generated changes | [Log](run-data-dev.txt) |
| `git diff --exit-code -- src/generated` | 0 | [Git results](git-results.txt) |
| `git diff --exit-code` | 1; intentional uncommitted source/docs changes, not a clean release tree | [Git results](git-results.txt) |
| `gradlew.bat runGameTestServer` | 0; 2 m 33 s, all 44 required tests pass | [Log](gametest-dev.txt) |
| `python -m unittest discover -s tests -p 'test_run_v*.py' -v` final run | 0; 62 tests, including 14 new harness tests | [Log](server-harness-regression-tests-final.txt) |
| `python -m unittest discover -s tests -p test_v1plus_planning.py -v` | 0; 14 tests | [Log](planning-tests.txt) |
| `python scripts/validate_build_artifact.py build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar --expected-version 1.20.1-1.0.0-dev --content-manifest <temporary>/artifact-manifest.json` | 0; 758-entry metadata/notices/credential audit | [Log](artifact-audit-retry.txt) |
| Same artifact validator with the incorrect `--write-manifest` option | 2; invocation corrected without changing the validator | [Failure](artifact-audit.txt) |
| `python scripts/check_client_imports.py` | 0; no forbidden common/client reference | [Log](client-imports.txt) |
| `python scripts/check_celestial_identity.py` | 0; namespaced identity, no DOM implementation | [Log](celestial-identity.txt) |
| `python scripts/validate_v090_resources.py --output <temporary>/resources.json` | 0; inherited resource contract, 175 files / 231 bilingual keys | [Log](resources.txt), [JSON](resources.json) |
| `python scripts/validate_v1plus_planning.py --package-root AdvancedRocketry-Community-v1plus-Development-Docs` | 0; 11 plans, 33 original input hashes/sizes | [Log](planning-validator.txt) |
| `python scripts/validate_repository.py --require-approved-identity` initial and final runs | 0; 45 checks, no pending/warnings/failures; final tracked-link count 338 | [Initial](governance-dev.txt), [Final](governance-final.txt) |
| `git diff --check`; historical release diff | 0; historical accepted releases unchanged | [Git results](git-results.txt) |

The full historical Python suite is not rerun here. Its earlier 706-test result
belongs to the separate planning-integration baseline, not this revised tree.

## Stabilization files changed

- Development metadata/support: `gradle.properties`, `THIRD-PARTY-NOTICES.md`,
  `README.md`, `CHANGELOG.md`.
- Runtime observation: `RocketFlightReleaseCheckpoint.java`, `RocketManager.java`.
- Java tests: `ModMetadataTest.java`, `PopulatedManagedDataFixture.java`,
  `PopulatedWorldDataPreservationTest.java`, `RocketFlightReleaseCheckpointTest.java`.
- Python: `scripts/run_v100_flight_forced_stop.py`, its matching test module,
  and the in-progress fixture adjustment in `tests/test_v1plus_planning.py`.
- Tracking: current-version/Gate status, v1.0 version plan, PORTING_MATRIX,
  core/gap audits, implementation log, stabilization provenance/index and this
  evidence directory. Earlier planning-package integration changes are retained
  separately and are not attributed to this implementation slice.

The 608-file source inventory and packaged logs were hash-checked; the evidence
inventory is [checksums.txt](checksums.txt). A separate local link check covers
the new untracked work documents in addition to the governance validator's
tracked-file scan.

## Packaged server and forced-stop matrix

All servers bind to `127.0.0.1:25610`, with `online-mode=true`. No authenticated
player is required by or claimed for these headless checks. Each run uses a
new disposable directory below the canonical temporary task directory. Original
worlds and previous failed evidence are not overwritten.

The exact invocations, including local directories, are in
[packaged commands](packaged-commands.txt). The
[baseline summary](dedicated-baseline/summary.json) records the downloaded
checksum-verified Forge installer, fresh first start, save/stop, same-world
restart, runtime and JAR identity.

The [flight matrix](flight-matrix/summary.json) ran ten cases across 20 Java
processes. Each staging process reached its reported checkpoint, executed
`save-all flush`, then was killed through its owned `Popen` handle without a
graceful stop command. All ten forced exits were 1; each recovery process
subsequently saved and stopped with exit 0.

| Checkpoint | Recovered state | Fuel |
|---|---|---:|
| ASSEMBLED | ASSEMBLED | 0 |
| FUELED | FUELED | 1,000 |
| COUNTDOWN | FUELED | 1,000 |
| ASCENT | FUELED | 1,000 |
| TRANSIT_PREPARED | FUELED | 1,000 |
| DESTINATION_SPAWNED | LANDED | 628 |
| PASSENGERS_TRANSFERRED | LANDED | 628 |
| SOURCE_REMOVED | LANDED | 628 |
| DESCENT | LANDED | 628 |
| LANDED | LANDED | 628 |

Reports preserve logical/snapshot/entity identity, capacity, origin, block count
and the empty passenger manifest. Transfer recovery receipts must match exact
phase, source/destination counts and action. The harness counts loaded rocket
entities once across dimensions, requires one authority, then verifies normal
disassembly, five expected blocks and the diamond/iron chest contents.

The complete matrix used the hash-verified
[archived harness revision](executed-harness-r2.py.txt). A final defensive check
rejects a server that already exited before the kill call; unit tests cover
clean/crashed prior exits, and [a further SOURCE_REMOVED run](flight-guard/summary.json)
verifies that final harness against the same JAR. That one-case run is explicitly
not labeled a complete matrix.

The [initial matrix failure](flight-matrix-first-failure/summary.json) is
retained. Its selector enumerated an already cross-dimension `@e` once per
dimension and counted the same rocket twice. The corrected script enumerates
once; it neither relaxes `matches 1` nor extends the timeout. Product behavior
was not changed to accommodate the failure.

## Remaining acceptance and limitations

- Nonempty constructed NBT does not establish an actual accepted-Beta world
  upgrade. Capture an immutable representative world and test two restarts.
- The matrix covers unmanned flight and all five transfer journal phases,
  not intermediate assembly/disassembly mutations or player reconnects.
- Explicit pre-kill flush does not simulate power loss during an in-flight
  disk write. Full stage/recovery work remains under `V100-DATA-03`.
- Latest Forge/JEI, four-hour active-player reference load, fresh real-GPU and
  two-client gameplay evidence still need the final candidate.
- Candidate freeze, independent review, uninvolved installation test, human
  approval, reproducible publication and all Required Gates remain incomplete.
- No commit, push, tag or release was created. `v1.0.0` remains `IN_PROGRESS`.

The owner-reported prior PCL login is recorded in the
[gap audit](../v1.0.0-stabilization-gap-audit.md); authentication setup is not
repeated and no launcher credentials were read.
