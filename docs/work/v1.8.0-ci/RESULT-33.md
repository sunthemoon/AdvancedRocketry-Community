# Airlock timing checkpoint: terminal retained raw results

Date: 2026-10-07. Exact source
`22f7d1cae0735a8b8c072165e7a3cf8089e3719c`, run 37605098496 /attempt 1
/job 112738525112. Terminal: **FAILED**. This succeeds the dated
[running observation](RESULT-32.md) without rewriting it.

Root's [terminal metadata](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-timing-ci-regression-20261007-01/MONITOR-07.json)
has SHA-256 `ecf12536bd58c8be752a5087993713d2bceadd39ff8120f6d1cf1181bcf69c37`,
observed at **2026-10-07T10:16:53.933496Z**. The monitor exits normally
(`a7d870`, exit 0); raw upload succeeds, external binary upload is skipped.
Different-agent raw audit is **pending**, not inferred from Root's audit.

## Actual commands and retained cohort

Root reads/rehashes the existing bounded retrieval helper (`62d837`, exit 0),
then retrieves artifact **11475311263** (`9cae2b`, exit 0), matching API/actual
archive SHA-256
`084dfd01f7a4f9cf754615ffa8f4ba17e999c7ea6bd404438bffc4f7b26fb66a`,
1,820,441 bytes. The
[retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-timing-ci-raw-20261007-01/RETRIEVAL-01.json)
has SHA-256 `8173bee2ce9aefb399a83a900dcac8d61be4086c8af6fa2ff19c6cdcb6616f03`.
Of 893 logical members, 397 raw logs/XML/metadata totaling 7,701,456 bytes are
retained; HTML/source/binary copies are not retained. Retrieval remains bounded
to 60 MB/4,096 members with path/CRC/digest checks and in-memory credentials.

Fresh Root `python -B audit01.py` (`71cf45`, exit 0) derives
[RAW-AUDIT-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-timing-ci-regression-20261007-01/RAW-AUDIT-01.json),
SHA-256 `1c33c432638e1b6c388704d5287f5d3450cd11d593a8fddcc4ce882bbedc90c8`.
All 397 inputs/receipt are checked before and after with zero drift. Selected
source/raw/metadata reads are `eb906a`, `47fd53`, `bf1d00`, `7e799b`, `169be2`
(exit 0). Source-gated limits and actual method sets remain in the audit.

## Observations

- Actual clean no-cache build passes: **372 XML /2,104 testcase children**,
  zero failure/error/skip or declared-counter mismatch. Exact sets match all
  3 config +7 effects and 4 airlock-block +5 provider +5 lifecycle unit methods.
- Artifact/client-boundary audits and both actual `runData` executions pass.
  Both tracked diff streams are empty; both tracked/untracked clean checks pass.
- The canonical stream completes **137 batches /525 GameTests**, final
  **523 '+' /two 'X'**, with two required failures: Tau Ceti f travel at line
  2,721 (`No rocket at Tau Ceti f`) and fuel-loader drop/place at line 5,393
  (`Native drop duplicated visible inventory or omitted loader`). The latter
  fails the one-ItemEntity assertion before carried-root/actual replacement
  checks; actual queried count/identities are not logged. This is not proof of
  a production duplication incident or a unique fixture cause.
- All seven airlock tests are in the actual batch at line 2,818 and absent from
  the complete failure list. The installed test's committed path only succeeds
  after all six mirror/phase cases, supply/revoke/no-republication/recovery and
  ordinary cleanup. This is source-gated aggregate execution evidence, not
  individual native PASS XML, failure/timeout cleanup or controlled crash proof.
  The prior initial-supply and Earth-Mars-Venus failures are not present here;
  old cohorts remain immutable, and absence does not establish a unique cause.
- Canonical **64 ERROR /zero FATAL** remains unwaived: EventBus 31, ChunkMap 5,
  project 10, NetworkRegistry 16, Reporter 2. Occurrences are not distinct defects.
- Hosted Linux samples have UID 1001 and at least 89,767,129,088 bytes free.
  Manifest identity is JAR SHA-256
  `59648936a1746f37ac09595db8b5e83ee7bbe7c9e0bc4da7b9a3eeeb0df0eccd`,
  3,526 entries. Applicable airlock/sky/config classes are present. Root does not
  download or independently parse the binary JAR.

Tau PRE/POST lines 2,714-2,715 bracket lookup, not an untouched-world comparison:
source remains TRANSIT, journal PREPARED, destination unassigned, entities-loaded
NO; entity-ticking changes NO to YES. The POST holder line is 2,717. These are
not a completed transfer, actual pending-load progression or causal proof.

No assertion, deadline, budget, production contract, save protection, risk,
ledger or Gate changes. Airlock native aggregate evidence has progressed;
complete regression, packaged/player/stop-restart, V1/V2 and G0-G9 remain open.
Local C is below 10 GB; no local Java/JVM/Gradle/native/client runs. New logs and
scripts are D-parent-local. The owned ended source worktree is already removed
normally, exact source and branch retained.
