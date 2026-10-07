# Living-gravity successor: unit/DataGen pass, Tau roundtrip fails

Date: 2026-10-07. Exact source **`8b3fdca990be3880ff04aae5a2354657d499b60f`**;
[run 37580341390](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37580341390),
attempt 1 /job 112658310596. Automatic regression **FAILED**. This is a separate
cohort from the original config-inventory failure in [RESULT-23](RESULT-23.md).

## Actual commands and results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds.
  Actual raw recount finds **369 XML suites /2,083 testcase records /zero
  failures, errors or skips**, with suite attributes agreeing and zero direct
  suite failure/error elements. All nine controller, three config and fifteen
  maintained CommonConfig cases match fixed declarations and pass. The strict
  cardinality correction is now exercised; the preceding failed result remains
  failed, rather than being rewritten.
- Hosted artifact/common-client audits succeed. `./gradlew runData --no-daemon
  --stacktrace` succeeds twice. Both `git diff --exit-code` logs are empty and
  both tracked/untracked clean-worktree checks pass.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: fails. Canonical lines
  5773-5775 report **518 tests complete /one required failure**, solely
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`.
  Line 2716 reports `No rocket at Tau Ceti f` at 06:20:42; the nine-test
  `living_gravity:1` batch starts at line 4385 /06:21:42. No new living-gravity
  subject is in the terminal failure list. Aggregate/batch evidence is not
  per-case success XML or a proof of a unique Tau cause or repair.
- Canonical stream: **63 ERROR /zero FATAL**, unwaived. Mirrors are not summed.
  Raw-evidence upload succeeds; external build-artifact upload skips.

## Evidence and audit boundary

The [terminal capture](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-root-integration-20261007/SUCCESSOR-OBSERVATION-03.json)
at **06:23:59.433389Z**, SHA-256
`1e58a73a80a9fbe2b772af4c0f462cd8228853f9901a87a1b087d6dfff1bbb24`,
binds completed failure and step outcomes to the exact source/run/attempt/job.
The two previous dated running captures remain unchanged.

Bounded retrieval `ad18d7` exits 0:
[receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/living-gravity-fixture-ci-regression-20261007-01/RETRIEVAL-01.json),
artifact 11464533038, 1,808,038 archive bytes, SHA-256
`7d69ba98c844e4ecad354bc8ca7706516f83d06440e02cd006d67a1059666dc3`.
API digest agrees. Only XML/log/JSON/text is retained, not archives, source/class
trees, a server copy or JAR. Credentials/signed redirects are not persisted.

Root's [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-root-integration-20261007/SUCCESSOR-AUDIT-01.json),
SHA-256 `c0c2e185f6e189204b2ed2e777d22e896a41fa2dc211299c0e7400d5f7e55efa`,
executes at `f098c3`, exit 0: 394 retained files rehashed before/after, actual
XML children/attributes, fixed selected declarations, generation/clean outputs,
canonical batch/terminal and error counts agree with zero drift.
Earlier `3d323b` prints excessive general exception contexts; `21704b` rereads
the relevant bounded contexts, rather than relying on truncated output.
The [different-agent retained-stream audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/living-gravity-fixture-ci-independent-audit-20261007-01/REPORT-01.md),
SHA-256 `2ce5021e2f660e4c17cea4efbe2462609370e8cf3883ed80074767e66909a12f`,
independently agrees on counts, fixed subjects, generation, batch/terminal and
canonical errors with zero input drift. All five output checksums verify. It
also qualifies batch execution versus absent individual native pass records.
No independent runtime replay or unretained-archive/JAR rehash is claimed here.

Hosted JAR checksum/audit/manifest records SHA-256
`f20fdc16c9e449d32be526604e03e6fdfde1ea7e48744fbb6259f8a3cec354e8`
and 3,502 entries. This is hosted identity evidence, not a downloaded binary or
independent inner-JAR parse. The failed-cohort artifact-upload skip is preserved.

## Remaining acceptance

The previously observed Tau failures and their fixed budgets remain; this new
failure is not waived, hidden, retried into a repair claim or attributed to a
single cause. Passing unit/DataGen and the executed new native batch do not
deliver the parent gravity/fall task, actual dimension transfer, natural
scheduler qualification, packaged/client or dedicated restart acceptance.
No local JVM/Gradle/native starts below the C: 10 GB threshold. New evidence is
thin and D-local. Leaf B's fall Medium, physical/save/recovery scope, C16 remaining
machines, C17-C19, R-021, real GPU/multiplayer, performance and G0-G9 stay open;
v1.8 remains **IN_PROGRESS /IMPLEMENTING**.
