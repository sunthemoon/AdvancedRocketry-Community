# Exact seal detector regression failure

Date: 2026-10-07. Source `9cd5890bffe7e544a6b365bf47612dc13293401e`;
[run 37501874947](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37501874947),
attempt 1, job 112400654061. Result: **FAILED**, at `:test`, not compilation.
This supersedes the earlier dated running observation for this exact run.

## Actual results

The unchanged hosted `./gradlew clean build --no-build-cache --no-daemon --stacktrace`
compiles main and test source, then executes **1,918 actual testcase occurrences
in 351 XML suites: 1 failure, 0 errors, 0 skips**. XML attributes agree with actual
children and the Gradle summary. The build exits 1; all 20 actionable tasks execute.

Only `SealDetectorItemTest.tooltipIsFixedAndDoesNotInspectHeldData()` fails.
The XML records `IllegalArgumentException: No delegate exists for value air`,
through `ForgeRegistry.getDelegateOrThrow` and the `ItemStack` constructor at
test line 42. It fails before the tooltip and its assertions execute. The other
three item tests pass. This trace establishes a plain-JUnit fixture defect,
not a detector gameplay defect or compiler error.

Artifact audit, DataGen/repeat/clean-worktree checks, GameTest and JAR upload are
**SKIPPED**. No counts, clean result or JAR identity from an earlier source are
rebound here. Raw regression upload succeeds.

## Exact raw evidence

Artifact 11429734142 has 1,133,131 compressed bytes and verified API/container
SHA `316c7072ab4de44380f365b5421493e3ec797234653b06f3ba9786e076a01c37`.
[Artifact receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/detector-9cd-artifact-20261007-c18-d28e64/RETRIEVAL-01.json)
SHA `4db6e49802c33c6670df4292c1f0c7b492a203b69805066d992baed0cb2819b1`;
[attempt-log receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/detector-9cd-attemptlogs-20261007-c18-d28e64/RETRIEVAL-01.json)
SHA `1b4517f685ec5ccddf0bee22037781bb93f42ca2a457658c6dd948bb6d7d5b10`.
The admitted serial metadata/artifact/log collector exits 0; collection success
does not mean build success. Its unchanged archive helper performs all-member
path/size/CRC checks before retention. Archives are not retained or replayed.

Root's independent [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-detector-9cd-raw-audit-20261007-01/AUDIT-01.json),
SHA `9030b3100c161009281cfb7eb635c37c9581e5cabf9d4e3cca986e296a87e325`,
actually exits 0: all 375 retained files /1,336,536 bytes match their receipts.
It parses every XML, verifies actual cases against suite attributes and binds
the raw tested-commit file and primary build stream. It does not rerun the tests.
Build stream: 56,592 bytes, SHA
`85c03984cf0e00ba4b093ec1629cda8f27595189bab8a3362c9fc17ad0f08564`;
failure XML: 11,053 bytes, SHA
`575d3ad19770c824a5d0b16640fc03aa5c122a05d9a6b4cfa6c65cc12897e6fc`.

## Reviewed correction and remaining verification

The separately [reviewed test-only correction](../v1.8.0-c18a-seal-detector/SOURCE-UNIT-FIX-01.md)
is normally published at `33a3156e309ca2b8f6a1fcc766501968bc21f138`.
It changes the tooltip's stack fixture to initialized vanilla PAPER; all original
assertions and four test methods remain, with no production or registry change.
That correction has not yet earned a passing execution result in this record.

Its [new run 37506268620](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37506268620),
attempt 1 /job 112415607085, is in clean build at the
[dated metadata observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-detector-fixed-hosted-observation-20261007-01/OBSERVATION-01.json)
(`2026-10-06T17:48:12.118914+00:00`, SHA
`c4ddafe192889064165f8b4ec49a9bb1f8f0f79d6277cb01efeaa1e98cf718b5`).
No future result is inferred. Earlier Tau failure remains historical in
[RESULT-11](RESULT-11.md). Native/restart, installed custom rules, real clients,
content closure, R-021 and G0–G9 remain open.
