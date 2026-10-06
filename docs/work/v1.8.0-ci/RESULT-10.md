# Solar fixture exact-source full regression

Date: 2026-10-06. Source: `e0c601e4d58cb906d79f744070316c0a2d810c06`.
[Run 37484998908, attempt 1](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37484998908),
job `112342646260`, completed **FAILED**. This supersedes the pending observation
in the [source checkpoint](../v1.8.0-c17c-solar-surface-fixture/SOURCE-INTEGRATION-01.md).
Previous failed cohorts in [RESULT-09](RESULT-09.md) remain historical unchanged.
No workflow, selected subjects, assertions or deadlines changed.

| Actual command or observation | Result |
|---|---|
| `./gradlew clean build --no-build-cache --no-daemon --stacktrace` | Successful; actual `:test` executes; 1,895 testcase occurrences /346 XML suites /0 failures, errors or skips |
| First `./gradlew runData`, tracked/untracked checks | 804 writes; both checks pass |
| Repeat `./gradlew runData`, tracked/untracked checks | Zero writes; both checks pass |
| Unfiltered `./gradlew runGameTestServer` | 493 complete; one required failure; command fails |
| Main GameTest console | 63 ERROR headers /0 FATAL; overlapping stream copies are not added |
| Raw evidence upload | Successful |
| Build JAR upload | Skipped; hosted audit/hash is not independent JAR-byte verification |

The remaining required failure is
`adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`:
`No rocket at Tau Ceti f`. At the retained failure-time observation, the journal
is PREPARED; the source entity is TRANSIT/not removed, the destination UUID is
unassigned, cached chunk presence is true, and both entities-loaded and
entity-ticking predicates are false. Expected game time minus creation time is
270 ticks. This finite snapshot does not establish continuous readiness,
live-transfer membership or a unique cause. The existing readiness gate stays.

The complete required failure set contains no Solar surface failure. This
cohort's success is separate from the earlier failed surface tests and does not
prove the historical occluder's identity, whole Solar functionality, packaged
restart recovery or real-client acceptance. The original 40-tick oracle remains.

Root independently executes `python -B audit01.py`, exit 0, and rehashes all
389 retained members /12,466,073 bytes against both collector receipts. All
346 actual XML suites and 1,895 testcase occurrences are parsed, with each
suite's attribute totals checked. Original build/DataGen/cleanliness and native
GameTest terminal records are read. The sealed [AUDIT-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-solar-e0-raw-audit-20261006-01/AUDIT-01.json)
has SHA-256 `239f9455b3521a8cc89eb7c0a6d57c1f7e475ad8db066f0f2a7eeb9f8f8e45d0`.
The optional helper-module name filter identifies only two modules, and its
optional Solar batch literal selects no batch row; neither is a complete
module-vector or batch-association check. They do not affect the all-XML counts
or actual complete required failure set. No helper/result is rewritten.

The different-agent [collector report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/solar-e0c601-collector-preparation-20261006-c18-504a20/REPORT-01.md),
SHA-256 `c2d28f48dc4a29604c2a5680d027760833d24ebfd1a57e19dd374d2df0a39be4`,
separately binds the one-test `solar_environment` batch to this fixed source's
surface annotation, and the 493-character terminal vector to 492 successes
and one failure. It checks the five raw-fidelity/helper XML suites at
19/13/8/20/15 =75 subjects /0FES. These qualified collector checks are not
attributed to Root's optional filters or treated as another product execution.

The exact [artifact receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/solar-e0c601-artifact-20261006-c18-504a20/RETRIEVAL-01.json)
has SHA-256 `8fe3657a10dd13ef47462bcd264ed071b838f46a32c4d4e1863624378e71133a`;
the [whole-attempt log receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/solar-e0c601-attemptlogs-20261006-c18-504a20/RETRIEVAL-01.json)
has SHA-256 `00b7fcfc10552f75cef2312921bc5dfe7ca20c883d768b83625fd2e9d8e0c5b1`.
The original main console is 1,544,140 bytes, SHA-256
`c974a88aa78219f5f903b91ec00ee31f1fe890b5db0a17f4baf039173ea9ff39`.
This is an independent retained-byte/result audit, not a second product
execution, archive CRC replay, unique-cause finding or Gate approval.

Physical hatch/shared writers, remaining C16-C19 content, dedicated server,
restart/crash recovery, real clients/GPU, R-021 and G0-G9 remain open. Local
full Gradle/GameTest/native execution is not started below the 10 GB capacity
threshold. Content closure remains failed: 186 PLANNED /154 REVIEW units.
