# Exact empty-removal admission regression

Date: 2026-10-07. Source `7d7b474eb74a73d6cfb3ffa7271bc22f2f94b678`;
[run 37568695086](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37568695086),
attempt 1 /job 112622219034. Automatic regression **PASSED**; physical hatch
delivery and version acceptance remain incomplete.

## Commands and actual results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds.
  Actual raw recount finds **368 XML suites /2,073 testcase children**, zero
  testcase failures, errors or skips and zero container failure/error elements.
  Suite attributes agree with actual children. Six new
  `ClassicEmptyHatchRemovalAdmissionTest` and seven maintained
  `ClassicLoadJoinOwnershipTest` subjects match the fixed source and pass.
  They test declarations/data boundaries, not physical block interaction.
- Artifact and common/client-boundary audits succeed.
  `./gradlew runData --no-daemon --stacktrace` succeeds twice;
  `git diff --exit-code` after each generation and both tracked/untracked clean
  checks pass on the hosted checkout.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: succeeds. Canonical
  raw lines 5788-5789 report **509 complete /all 509 required passed**.
  The stream retains **62 ERROR /zero FATAL**, unwaived; duplicate copies are
  not summed. This is not a demonstrated repair of earlier Tau failures.
- Raw-evidence and single external build-artifact uploads succeed.

## Raw association and limits

The [terminal API capture](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-removal-ci-terminal-20261007-01/observation-02/OBSERVATION-01.json)
at `2026-10-07T04:10:12.328997Z` binds the exact source/run/attempt/job to
completed success; the run updated at 04:03:32Z. The first dated running capture
and [source publication record](../v1.8.0-c16a-hatches/EMPTY-REMOVAL-GUARD-INTEGRATION-01.md)
remain historical evidence rather than being rewritten as a terminal test run.

Root's three bounded retrieval commands (`d89ebc`, `3b7302`, `b00d02`) exit 0.
Their receipts retain raw XML/log/JSON/text with original member mappings and
digests, not archives, source trees, classes, credentials or runtime copies:

- [Regression receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-removal-ci-regression-20261007-01/RETRIEVAL-01.json):
  artifact 11460177209, 1,800,637 bytes, archive SHA-256
  `dcb6ca1c07381294ac9bdbeeda70e1495ba0e935a2b5c3052b319587f4c5a037`.
- [Build receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-removal-ci-build-20261007-01/RETRIEVAL-01.json):
  artifact 11459914818, 5,141,198 bytes, archive SHA-256
  `4b95ca586dfba0b746596d58c258af826ea92a8122c7531ac22cba21a6a73233`.
- [Hosted command logs](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-removal-ci-logs-20261007-01/RETRIEVAL-01.json)
  bind command execution and original log member names to the same cohort.

The [Root audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-removal-ci-root-audit-20261007-01/AUDIT-01.json)
executes at `58a5ca`, exit 0, with 420 unchanged input pins. Its SHA-256 is
`ba728c91885c648a892f61fd73bb2da9de9e37bbbbdaa2bfc368d1441cacf082`;
the [compact report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-removal-ci-root-audit-20261007-01/REPORT-01.md)
states the observed counts and verification boundary. The subsequent
[different-agent raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-removal-ci-independent-audit-20261007-01/REVIEW-01.md),
SHA-256 `176f5f3e864f56f42b794014760ba1e400239676d031ffbd9c3e2404051f704d`,
independently agrees on the retained cohort, actual counts, fixed source subjects,
commands and native terminals. It rehashes 413 raw files plus receipts/metadata;
its original mirror timestamp-extraction limitation and narrow correction remain
separate. It does not rehash the absent JAR or qualify physical/save activation.

The helper hashes the sole JAR's whole bytes in memory: 5,893,279 bytes,
SHA-256 `4688e05b381f10f694cede3cfabb94ea500c2b5b5b415728b69558d7e86f6401`.
This agrees with the hosted checksum, artifact audit and 3,500-entry manifest.
No local JAR is retained; the Root audit does not independently rehash a retained
binary or reparse inner entries. Archive digests match API metadata.

## Remaining scope

[RESULT-21](RESULT-21.md) belongs to the preceding source; its numbers are not
rebound here. Earlier failed cohorts and unwaived errors remain. No local Java,
Gradle or native execution starts while C: has less than 10 GB free. New
temporary scripts and raw evidence use the project parent on D:; unrelated
processes and user-owned files remain untouched.

Physical hatch registration, placement/cancellation/removal, generated/disk
Proto observation, pre-serialization protection, resource/drop conservation,
final save and dedicated restart remain open. Native outer-hook feasibility
does not constitute an accepted architecture exception or implementation.
R-021, real-client/GPU/multiplayer, performance, ledger closure and G0-G9 remain
open. v1.8 stays **IN_PROGRESS /IMPLEMENTING**.
