# Living-gravity original cohort: strict config inventory failure

Date: 2026-10-07. Exact source **`1b6071d14fb5801f4fcf18c7808a5938f7a96c25`**;
[run 37578961754](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37578961754),
attempt 1 /job 112654063999. Automatic regression **FAILED**. A later fixture
correction does not change this original result.

## Actual commands and results

`./gradlew clean build --no-build-cache --no-daemon --stacktrace` reaches
`compileJava`, `compileTestJava` and test execution, then fails `:test`.
Raw build lines 364-375 identify the sole failing subject; line 547 reports
`BUILD FAILED in 4m 41s`. This is an executed-test failure, not a compiler failure.

Actual recount: **369 XML suites /2,083 testcase records /2,082 passed /1 failure
/zero errors or skips**. Suite attributes agree. Direct suite-level failure/error
elements are zero; this is not an assertion about unexported engine state.
`CommonConfigTest.obsoleteUnconsumedLifecycleToggleIsNotExposed()` expects 72
config values but observes 73 at fixed source line 44. The accepted living-gravity
switch is the single additional value. The independent audit classifies this
strict inventory maintenance/verification blocker as Medium M1.

All nine controller cases (two retained plus seven new) and all three new
`ClassicGravityConfigTest` cases match fixed declarations and pass. These are
JUnit results, not native movement, entity NBT, actual dispatch or restart proof.
Artifact audit, DataGen and all Forge GameTests are **SKIPPED**; no GameTest count
or success is claimed. Raw-evidence upload succeeds; build-artifact upload skips.

## Exact raw evidence and independent audit

Root's [terminal capture](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-root-integration-20261007/TERMINAL-METADATA-01.json)
at 06:05:15.641403Z records completed failure. The separate
[public metadata monitor](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-ci-metadata-independent-20261007/REPORT-01.md)
observes the same cohort/terminal and skipped steps, without inferring raw counts.

The pinned bounded retrieval (`b8dd4d`, exit 0) retains only compact raw facts:
[receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/living-gravity-ci-regression-20261007-01/RETRIEVAL-01.json),
artifact 11463329752, 1,191,472 archive bytes, SHA-256
`aa9568416c274c410906c5d6cf071d21180303b2c9fccc61c2cb9a0c1ecedd25`.
API digest agrees. Credentials and signed redirects remain in memory; no ZIP,
source/class tree, server copy or JAR is retained.

Root `b36842` and `6acd58` exit 0. Its
[raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-root-integration-20261007/FAILED-AUDIT-01.json),
SHA-256 `e24195cd1fad1346f442f57a0a835b6f3f9ba01c8a2ad3b1897d7d85b2c2f6dd`,
rehashes 378 retained files before/after and recounts actual children, suite
attributes, selected fixed subjects and literal build lines with zero drift.
The [different-agent retained-stream audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/living-gravity-ci-independent-audit-20261007-01/REPORT-01.md),
SHA-256 `e21faab15b63d9b9e426ac79702ed97144f0ad6890e85d949eaebc6eb9cbb153`,
independently agrees; its three output checksums verify. Neither raw audit
re-downloads the archive or executes Java/native code.

## Separate successor and retained obligations

Root changes only the strict inventory fixture/comment/import: retains the
obsolete-toggle ban and exact cardinality, adds an identity check for the actual
registered new key, and accounts for exactly 73 values. All 15 methods, other
assertions/helpers and budgets are preserved by an exact inverse check.
[Independent correction review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/living-gravity-config-cardinality-review-20261007-01/REVIEW-01.md)
finds no scoped issue; the revised test is not executed by that review.
Correction **`8b3fdca990be3880ff04aae5a2354657d499b60f`** is committed and normally
pushed (`54562e`, exit 0), not a waiver, original-result rewrite or runtime pass.
Its separate dated running capture is in the
[source integration record](../v1.8.0-c18a-living-gravity/SOURCE-INTEGRATION-01.md).

[RESULT-22](RESULT-22.md) retains the preceding successful source's own counts.
Neither those numbers nor these failed-source numbers are rebound to the
successor. No local JVM/Gradle/native starts below the 10 GB C: threshold. New
temporary data stays under the D-local project parent. Full successor regression,
native movement/NBT, packaged/restart, leaf B's independent fall Medium,
C16 remaining machines, C17-C19, R-021 and G0-G9 remain open. v1.8 stays
**IN_PROGRESS /IMPLEMENTING**.
