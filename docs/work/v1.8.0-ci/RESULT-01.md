# Hosted development regression: retained failure

Record date: 2026-10-05. Tested source:
`516317a583d1626d114dc5e1d4a3670cb79d0b5a`. Result: FAILED; no Gate acceptance.

## Actual execution

[Run 37322207759](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37322207759),
attempt 1, is completed/failure. Checkout, preflight, tooling, clean build,
artifact audit and DataGen succeeded; unfiltered GameTest failed. The always-run
raw upload succeeded; the conditional JAR upload was skipped.

| Command or observation | Actual result |
| --- | --- |
| `./gradlew clean build --no-build-cache --no-daemon --stacktrace` | BUILD SUCCESSFUL; fresh `:test`, 20 executed tasks |
| JUnit XML | 341 suites, 1,853 testcase elements; zero failures/errors/skips |
| Private NC1 and arithmetic suites | 23 tests each, included in that JUnit total |
| Host-tool Python tests | 16 tests, zero failures/errors/skips |
| `./gradlew runData --no-daemon --stacktrace` | BUILD SUCCESSFUL |
| `git diff --exit-code` / clean-worktree check | Empty tracked diff / clean tracked and untracked tree |
| `./gradlew runGameTestServer --no-daemon --stacktrace` | 477 completed; one required failure; BUILD FAILED in 5m 11s |
| Build artifact / common-client audit | Recorded PASS; 3,355 JAR entries; no client imports |

The required failing test is
`adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`:
`No rocket at Tau Ceti f`. Its destination-spawn marker precedes failure;
the log does not establish destination entity-section readiness or a unique
production cause. It is not waived as an intentional error fixture. Original
landing/ownership assertions and the 1,400/270-tick budgets remain unchanged.
Other ERROR headers are not blanket-waived. A later correction needs its own
source review and actual committed, unfiltered replay.

The hosted Linux UID is 1001. Four preflights sample checkout/build, temporary/
evidence and Gradle-cache roles against 10,000,000,000 bytes; all pass. The three
heavy-step samples range from 89,774,424,064 to 91,306,651,648 free bytes.
Python and Java temporary directories are recorded under `/home/runner/work/_temp`.
This is not reference-hardware performance or a local C-space waiver.

## Compact raw evidence

Artifact `11351390994`, named
`v180-regression-516317a583d1626d114dc5e1d4a3670cb79d0b5a-1`, is 1,704,055
compressed bytes. Its API digest and actual in-memory archive digest agree:
`aba072b0bcf4a756e5c9050fc8488bb7a4f586ae1bbb7282ef854eaca47659d3`.
The bounded collector checks cohort, digest, CRC, member limits and canonical
paths before retention. No token or signed redirect URL is written into evidence.

[Complete fresh retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-516317-attempt1-20261005-02/RETRIEVAL-01.json)
records original member names, content hashes/bytes and explicit flat
`retained_path` mappings. Consumers must use those mappings. Root independently
checks all retained bytes/hashes with zero mismatches and parses all JUnit XML.
The [different-agent raw-result audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-result-review-20261005-30a8e2/REVIEW-01.md),
SHA-256 `3daffd81231ed617e40bbef5a02ccb3b8ffef12f45901c283049a00c83c35b87`,
independently verifies 363 retained files /7,322,979 bytes and derives the same
XML and failed GameTest results. It does not recompute the unretained ZIP or
actual JAR container and does not run Java. Its limits are not Root waivers.
Excluded HTML/class/archive content is not reproduced as another source/build tree.
The complete GameTest console is 1,510,357 bytes, SHA-256
`8ee0b925490ea79d6b475db767c085d51edae92a0e068d356ac68a6b330083c1`.

[Public run/job observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-ci-root-20261005-01/RUN-OBSERVATION-06.json)
records step outcomes. The build-side audit reports main JAR SHA-256
`64ab06d4b3a5bb103baed2d54a19b7735213a2c7791589023ba2d45d5f5c0c73`.
No actual JAR bytes were retrieved because its upload was skipped. Thus this is
a hash-bound recorded build-side audit, not an independent JAR-byte/API comparison.
DataGen ran once in this cohort; no repeat-run result is invented.

## Original failures and corrections retained

Run 37318904441 at `33b690f001f5ecca88589d44582369a8d73f1785` failed workflow
validation with no jobs, Java tests or artifacts. The independently reviewed
context and failure-upload repair is published at the tested commit above.
Original tooling/independent-control failures remain in their separate leaves.

The first authenticated retrieval exited 1 after retaining only some files:
[partial receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-516317-attempt1-20261005-01/RETRIEVAL-01.json)
records FAILED/FileNotFoundError. Its XML is not used for full counts. Deep paths
were a working hypothesis, not a demonstrated OS cause. A separately reviewed
flat-storage-only correction preserves validation/authentication rules and
exclusive creation. The fresh second retrieval exits 0. Neither original
receipt nor either reviewed helper generation is overwritten.

## Scope remaining open

This establishes successful build/unit/DataGen steps plus an actual failed
integration regression, not full private-helper integration or runtime delivery.
Physical hatches/machines, typed propulsion callers/writers, first-event holds,
native restart/crash recovery, real clients/GPU/multiplayer, progression and
R-021 remain open. The ledger stays 186 PLANNED /154 REVIEW; v1.8 remains
IN_PROGRESS /IMPLEMENTING with G0-G9 open. Source27's earlier successful
cohort remains historical, not relabelled or rebound to this newer failed run.

Local C remains below 10 GB. No local full build/GameTest/native server ran.
New collector/review scratch stays under the project-parent D evidence root;
no runtime copy or retained ZIP was created by this retrieval. Old policy-refused
or other-owner cleanup targets are untouched.
