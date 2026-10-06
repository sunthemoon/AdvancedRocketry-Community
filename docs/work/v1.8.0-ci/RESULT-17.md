# Exact guarded-resource checkpoint regression

Date: 2026-10-07. Source `8d5214061c98fce0cf549a231eae7aaf312b3ba2`;
[run 37534775606](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37534775606),
attempt 1 / job 112512823966. Overall **FAILED** at the required GameTest step.
[RESULT-16](RESULT-16.md) remains the preceding diagnostic-source cohort.

## Actual commands and results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds;
  fresh `:test`, 354 XML suites / **1,954 physical testcase elements** /
  zero failures, errors or skips. The eight new guarded-resource methods and
  39 cases in six unchanged resource suites actually execute and pass.
- `./gradlew runData --no-daemon --stacktrace`: succeeds twice. Both
  `git diff --exit-code` streams are empty; both tracked/untracked checks pass.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: fails, exit 1;
  **509 complete / one required failure**:
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`,
  `No rocket at Tau Ceti f`. No assertion, deadline or selection is changed.
- Main GameTest log has 63 ERROR / zero FATAL lines, without duplicate-stream
  addition or a blanket waiver. Hosted audit reports 3,439 JAR entries and SHA
  `9d3e39999bba55698d0bfde180cfa50a02b211cbef5f9cc1002a081ebc615103`.
  Build-JAR upload is skipped; **no independent JAR bytes are available**.

## Raw association and independent recount

The [collector report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-8d521-ci-collection-20261007/REPORT-01.md),
SHA256 `c0a9102a58fd1f5d415ead87249390a92153c319819f01421d942b3263ff7cd3`,
records one bounded regression-artifact and one attempt-log retrieval, each exit
0. Artifact 11445932693 is 1,753,351 bytes / SHA256
`a4b25171851cb8a7695ad747e5e983d41d4eca556f2b3ab6c3af3e5953e74541`,
matching its API digest. Only the output-root layout is adapted; download,
member, path, digest, CRC and credential bounds remain unchanged.

Root's separate [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-resource-8d521-raw-audit-20261007-01/AUDIT-01.json),
SHA256 `1cf90ed56a2abbbbcae96fbc0a1c00776b37f63559d6898dc2e33b33c47e2639`,
completes at tool `0b1256`, exit 0. It rehashes 397 raw members plus two receipts
before/after without drift, recounts actual XML children and suite attributes,
and binds the eight new case names to the exact Git test blob. Tool `8e30e9`
reads actual suite counts, build success, both empty diffs/clean checks and
failure lines. Initial console projection is truncated; retained audit/raw
bytes are complete. No Java/network or original ZIP/JAR-byte replay occurs.

The new suite XML is 2,165 bytes / SHA256
`9efefa9d47a4bbb2e6d76418239ddcdd2bdb2fa7d4af7d6ebecd13998420ecd0`;
all seven literal XML/path/hash bindings are in
[FOCUSED-FACTS-02.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-8d521-ci-collection-20261007/FOCUSED-FACTS-02.json).
The [canonical GameTest log](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-8d521-ci-collection-20261007/artifact01/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log)
is 1,568,967 bytes / SHA256
`b37ad3ddd77f588060119e50db6fafb851c35ba237db997cd362543c079965a4`.

## Qualification still open

Lines 2692-2694 show PREPARED, source Space TRANSIT/not removed, destination
UNASSIGNED; loaded YES, entities_loaded NO and entity_ticking NO then YES.
The installed manager is LIVE, flight_active YES, entered/completed/last tick
14909 at current tick 14910, with WAIT_ENTITY_READY. Lines 5747-5749 name the
single required Tau failure. Discrete samples establish no unique native cause
or production correction. The collector authored earlier diagnostics; its
causal interpretation is not an independent source verdict.

The three failed local resource cohorts remain failed. Author cleanup removes
only ended own class/temp outputs; raw results remain. Source/unit verification
does not admit physical owners, FULL observations, callbacks, save writers,
restart or real clients. Ledger closure and R-021 remain open. Local full/native
runs remain barred below 10 GB. v1.8 is IN_PROGRESS / IMPLEMENTING;
**all Required Gates are not satisfied**.
