# Committed fixture corrections: hosted regression

Date: 2026-10-06. Source `cfdcd8f546d7005b11c7bb1c8635eb3d1ee03339`,
[run 37343396492](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37343396492),
attempt 1. **FAILED; no release, ledger or Gate acceptance.** This supersedes
RESULT-03's pending replay and current compile-only checkpoint, not its failures.

## Actual commands and results

Hosted non-root execution completes fresh `clean build` in 4m 25s, with
1,861 JUnit testcase elements /342 suites /zero failures, errors or skips.
The eight lamp unit tests are included. Build-side artifact audit passes.
First `runData` writes 788 outputs in 28s; repeated `runData` writes zero of
788 outputs in 24s. Both `git diff --exit-code` and tracked/untracked DataGen
cleanliness checks pass. Hosted Python checks report 17 tests /OK.

Unfiltered `runGameTestServer` completes **483 tests /two required failures**,
then exits 2; Gradle fails in 5m 50s. Three captured native streams agree:

- `atargetregistersoncesavedandaremovedidcomesbackfrozen`:
  Registered before any save.
- `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`:
  No rocket at Tau Ceti f.

The first is a new failure in this cohort; the readiness admission does not
resolve all cold-flight failures. No unique production cause is established.
No failure header names a lamp, readiness or gravity test; this is not full
feature, restart or universal timing proof. Each native stream has 63 ERROR
headers without blanket waiver. Original assertions, deadlines and selection
remain unchanged.

Five bounded gravity snapshots record raw/effective/runtime/active true at
test tick 48 immediately under the `disabled_set` phase label, then all false
at the tick-50 pre-assertion snapshot, SYSTEM_DISABLED and unindexed. Re-enable
records true configuration but an inactive device. Cleanup records removed=true.
Phase labels are not value or unique-cause proof; the historical failure stays open.

## Evidence and verification boundary

Artifact 11359323980: 1,712,370 compressed bytes, SHA-256
`9df43484f6bd79654e51979ba5c5fb9b8022c50401cb473e35cf02cded6ecad6`.
Root's bounded collector exits zero, verifies API source/attempt, archive digest
and CRC, and retains 367 mapped payloads /7,364,568 bytes. Root independently
rehashes all retained payloads and parses all JUnit XML: zero mismatches.
[Receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-cfdcd8-attempt1-20261006-01/RETRIEVAL-01.json).

The [different-agent raw-result audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-cfd-result-review-20261006-e28af4/REVIEW-01.md),
SHA-256 `44adbc4d25005766bef4397c95ce9d967dc4d541a4c1a15bcfe6880e38025810`,
independently reconstructs those totals, all three failure sets and final
481-positive/two-failed status bars, DataGen results and finite diagnostics.
It does not independently replay unretained archive/JAR bytes or authenticated
API retrieval. Conditional JAR upload is skipped because the full job fails;
build-side JAR SHA `796dc9beaa7251f702c2636ef21261b6e9c43e4f05c9329814ce1d446bf3630e`
is a recorded audit result, not independent container/API verification.

No local heavy execution occurs while C is below 10 GB. New scratch uses D in
the project parent. The completed temporary gravity worktree was normally
removed after committed integration; historical policy-refused C targets remain.
Physical machines, full propulsion/station/equipment implementation,
restart/crash, real GPU/multiplayer, R-021 and all G0-G9 remain open.
