# Fuel-loader count diagnostic: terminal retained raw results

Date: 2026-10-07. Exact source
`10eb561a5112ee6226e2c52fef1c1cc358c5ef3f`,
[run 37608847289](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37608847289),
attempt 1 /job 112750895284. Terminal: **FAILED**. The dated
[running observation](RESULT-35.md) is retained unchanged.

Root's [terminal metadata](D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-drop-diagnostic-ci-20261007-01/MONITOR-12.json)
at **2026-10-07T10:51:42.032785Z** has SHA-256
`41210baf43a8e17bae94b05d65f9327c3618b538597fb652f19ad38403f01d9a`.
The bounded monitor completes normally (`f5a87f`, exit 0); raw evidence uploads,
the success-only binary upload is skipped. Different-agent raw audit is pending.

## Actual commands and retained inputs

Root reads/rehashes the existing bounded retrieval helper (`c16b20`, exit 0),
then `python -B retrieve01.py artifact 11478345039 --run 37608847289 --attempt 1
--sha 10eb561a5112ee6226e2c52fef1c1cc358c5ef3f --out <fresh D evidence leaf>`
succeeds (`7deb04`, exit 0). API and actual archive match: 1,823,425 bytes,
SHA-256 `1b3ee5fdd71f0c8cc2defe6d96d68c627674a26e326ced39beb4b11bff4b3c5d`.
The [retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-drop-diagnostic-ci-raw-20261007-01/RETRIEVAL-01.json)
has SHA-256 `a9d206cbee57d29ae4154272c0a4c9a1cafedf2659ec3aad3a42ee582827e304`.
397 raw XML/log/metadata members totaling 7,700,271 bytes are retained, not
HTML/source/runtime copies. The helper enforces 60 MB/4,096 member limits,
path/CRC/digest checks and in-memory credentials.

Fresh Root `python -B audit01.py` (`e2b55d`, exit 0) produces
[RAW-AUDIT-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-drop-diagnostic-ci-20261007-01/RAW-AUDIT-01.json),
SHA-256 `000dc5434cf0cd8286b23784e8f4f0519079a38880c6e6f7d36835106c7250b4`.
All retained inputs/receipt are checked before/after with zero drift. Actual
selected source/log/host/workflow reads are `86b515`, `9f1e4d`, `6ee814` (exit 0).
No previous cohort supplies these counts or results.

## Observations

- Hosted `./gradlew clean build --no-build-cache --no-daemon --stacktrace`
  passes: **372 XML /2,104 testcase children**, zero failure/error/skip,
  XML declared counters matching child counts. Selected five unit suites match
  their exact source method sets. Artifact and client-boundary audits pass.
- Both `./gradlew runData --no-daemon --stacktrace` executions pass; both
  `git diff --exit-code` streams are empty and tracked/untracked checks pass.
- Unfiltered `./gradlew runGameTestServer --no-daemon --stacktrace` fails:
  **137 batches /525 tests complete /two required failures**. The complete
  failure list names Tau travel (`No rocket at Tau Ceti f`, line 2,721) and
  installed-airlock supply/revocation (line 2,820). The latter fails during
  `upperOnly=false phase=1` initial supply with OPEN, oxygen 1,000, energy 40,000,
  scan NONE, indexed false. Later cases and cleanup are not thereby qualified.
- No loader count assertion/failure line is present. The six unchanged loader
  subjects are not in the complete failure list; this source-correlated aggregate
  result does not exercise the new failure-message branch, record a drop count,
  prove a unique cause or repair the preceding loader failure. Individual native
  PASS XML, abnormal-stop/restart and real-client proof remain absent.
- Canonical **64 ERROR /zero FATAL** remains unwaived: EventBus 31, ChunkMap 5,
  project 10, NetworkRegistry 16, Reporter 2. Mirrored logs are not added together.
- Hosted Linux UID 1001 /minimum sampled free space 89,686,466,560 bytes.
  Manifest JAR SHA-256
  `d6743fd312381e161bbadd4ee5354f831b394ed86bebdacbd900223191963d2e`,
  3,526 entries; applicable diagnostic/airlock/sky/config classes are present.
  Root does not download or independently parse the inner binary JAR.

Tau PRE/POST observations retain TRANSIT/PREPARED, destination unassigned and
entities-loaded NO; neither establishes actual load-stage progress or a cause.
Earlier cohorts and their passing/failing airlock/loader observations remain
immutable. No assertion, deadline, budget, production policy, risk or ledger
delivery is changed. The diagnostic source/check/replay slice is published, not
a completed regression repair. All v1.8 G0-G9, packaged recovery and V1/V2 remain
open. Local C is below 10 GB: no local Java/JVM/Gradle/native/client. New scratch
is D-parent-local; the clean ended owned source worktree is already retired.
