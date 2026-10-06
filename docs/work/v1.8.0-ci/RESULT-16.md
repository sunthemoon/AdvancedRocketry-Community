# Exact diagnostic-source regression

Date: 2026-10-07. Source `ca217afac7fc2034cf4740e18a0fe582af7285d7`;
[run 37525478437](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37525478437),
attempt 1 /job 112481271928. Result: **FAILED** at the required GameTest step.
This supersedes only the dated pending observation for this source.
[RESULT-15](RESULT-15.md) remains the preceding recipe-row success, not this result.

## Actual hosted commands and outcomes

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds;
  fresh `:test`, **353 XML suites /1,946 actual testcase elements /0 failures,
  errors or skips**. The new diagnostic helper contributes 19 subjects.
- `./gradlew runData --no-daemon --stacktrace`: succeeds twice. Both
  `git diff --exit-code` streams are empty and tracked/untracked checks pass.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: fails, exit1;
  **509 tests complete /one required failure**,
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`:
  `No rocket at Tau Ceti f`. Original assertions, deadlines and selection remain.
- Canonical GameTest log: **63 ERROR /0 FATAL**, without duplicate-stream
  addition or a blanket waiver. The new diagnostic line is WARN.
- Hosted artifact audit reports 3,437 entries and JAR SHA
  `9ee280a187342605c68a7b46e7298cc62e18996964243ca85c84348b80785222`.
  Build-JAR upload is skipped. These are hosted manifest/hash observations,
  **not independent build-JAR bytes**.

## Bounded failure observations

Canonical log lines2712-2714 retain the same transfer: PREPARED, source Space
rocket TRANSIT/not removed, destination UNASSIGNED, origin(1,74,0). PRE/POST
loaded YES /entities_loaded NO; entity_ticking changes NO to YES. New service
sample: installed MANAGER, flight_active YES, classification LIVE, current
tick14940, entered/completed tick14939, tracked YES and last WAIT_ENTITY_READY.
Source removal follows these samples during cleanup. Terminal lines5770-5772
name the single Tau failure. These discrete points establish neither continuous
native readiness nor a unique asynchronous-storage cause; no repair is claimed.

## Evidence and independent recount

Root's fresh [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-diagnostics-ca217-raw-audit-20261007-01/AUDIT-01.json),
SHA `a3709c4a647a000acd56fb927b3c4d914f0404d5567c9ee60846b69601da47c8`,
actually exits0 (tool `cf62a0`). It independently rehashes **398 raw/receipt
inputs**, binds source/run/attempt, recounts actual XML children and reads the
canonical failure log; before/after identities match. No Java/network is rerun.

The [collector report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/transfer-diagnostics-ci-collection-20261007-a04c6e/REPORT-01.md),
SHA `fc30e29d15d74ad12510bdeb005a835ebbabc2013de78019ce2e6f5e940d5ba7`,
is **diagnostic-author-side** raw collection, not independent source/cause review.
Exact GET-only regression-artifact and attempt-log retrievals exit0 under the
unchanged 60MB/4096-member/path/digest/CRC bounds. Regression artifact11441659804
has container SHA `17b8f378a42c4f03d0d616ce138e3c4f4f95df63627d3758f3f2d8df53e77ff1`;
attempt logs have downloaded-body SHA
`467f8f0e2ccb1c68b630f9c6c21f17c18e19777410e2ae50de51c2050b7a7d9b`.
Root verifies retained raw identities, not a repeated outer-archive CRC check.

Raw receipts/members stay external under `D:/GitHub/ARCE-Task-Evidence/v1.8.0/`:
`transfer-diagnostics-ca217-artifact-20261007-a04c6e` and
`transfer-diagnostics-ca217-logs-20261007-a04c6e`. The
[canonical log](D:/GitHub/ARCE-Task-Evidence/v1.8.0/transfer-diagnostics-ca217-artifact-20261007-a04c6e/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log)
is 1,573,448 B /SHA
`b9e6ee77d6dafc55bb2b77e6da4ed5abb5260be56c2d0ed7bfdcb6a3fd270ecf`.
Original prefix-selection and file-only sealing errors are preserved; successor
checks neither rerun product/network nor rewrite prior raw evidence. Incidental
process-temp directory remains excluded/untouched, not counted as evidence.

## Open qualification

The historical Planetary/Tau failures and previous success remain bound to
their own sources. Guarded owners, physical hatch/lathe, saving/terminal/native
restart, real clients and content qualification remain incomplete. R-021 is
not accepted or closed. C below10GB prevents local full/native runs; bounded
cached unit cohorts are separately controlled. v1.8 stays IN_PROGRESS /
IMPLEMENTING; **G0-G9 are not satisfied**.
