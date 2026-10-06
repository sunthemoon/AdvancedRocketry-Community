# Exact recipe-row regression

Date: 2026-10-07. Source `60f1564528de782fa15269884fd1355d3c0b9ff1`;
[run 37519061729](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37519061729),
attempt 1 /job 112459397257. Result: **SUCCESS**, with independent raw and
available JAR-byte verification. This supersedes this source's dated pending
observation. [RESULT-14](RESULT-14.md) remains the preceding failed cohort.
No result is rebound to the later diagnostic commit.

## Actual commands and results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds;
  **352 XML suites /1,927 actual testcase occurrences /0 failures, errors or
  skips**. Actual children match suite attributes; display names are not deduped.
- `./gradlew runData --no-daemon --stacktrace`: succeeds twice, writing **808
  then 0** entries. Both `git diff --exit-code` streams are empty; both
  tracked/untracked worktree checks report clean.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: succeeds; **509 tests
  complete /all 509 required pass**, no terminal required failure titles. The
  five `classic_save_admission:1` subjects execute. Assertions, deadlines,
  budgets and test selection are unchanged.
- Primary GameTest log: **62 ERROR /0 FATAL headers**, without double-counting
  copies or granting a blanket waiver.
- Actual built JAR: **5,710,095 B**, SHA256
  `cebbe1df6140654057740a31f07573338c37d5219e493b6f56907d53c0689204`.
  All **3,435** ZIP members' paths/sizes/SHA match the hosted content manifest;
  native ZIP CRC validation separately succeeds. No extraction or packaged-server
  execution occurs.

## Evidence

Root's fresh [raw/JAR audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-row-60f-raw-audit-20261007-01/AUDIT-01.json),
SHA `305739a8fa508fc3e15adc16c1876db30267ba49af3717103c1512f5d111116f`,
actually exits 0 (tool `c08e62`). **398 retained files /18,322,358 B** are
rehashed before/after without mismatch; exact source/run/attempt, every XML,
build/DataGen/cleanliness and full GameTest terminal are checked independently.
No Java is rerun by that audit.

The separate [collector report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/row-60f-ci-collection-20261007-c18-31ac09/REPORT-01.md),
SHA `1a65f9b7d4a2eb74612b22ecf16065a4d6e4fa72537201d182810c490e7026ed`,
records four actual retrieval/metadata children and outer exit 0, complete
captures and an independent raw audit with matching derived results. An earlier
collector checker used a wrong XML prefix and printed zero suites; that
preserved observation is not treated as a JUnit pass. The fresh corrected audit
rejects empty discovery and parses the actual member prefix, without rerunning
network or product execution.

GET-only receipts/raw remain in external D leaves
`row-60f-ci-artifact-20261007-c18-31ac09`,
`row-60f-ci-logs-20261007-c18-31ac09` and
`row-60f-ci-build-20261007-c18-31ac09` under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/`. Regression artifact **11440755366** has
container SHA `b4b0509f192d5e5dc87c7fd2a693964896b6a287176f250d62f85ff30e45b3b8`;
build artifact **11440230650** has container SHA
`7247cacec126faa721741690709d206c36051c918692d8bbc4f9c4d66cb1e505`.
Root checks retained identities, not an independent replay of the downloaded
outer archives' CRC. The actual JAR stays external, not duplicated into Git.

[Primary GameTest log](D:/GitHub/ARCE-Task-Evidence/v1.8.0/row-60f-ci-artifact-20261007-c18-31ac09/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log),
SHA `a84d54dea2cce65495b52a7ee5051c76ec459ca7fde149d77edbd4a38a6d9bde`,
contains the pass/build-success terminal. API metadata is not substituted for
raw results.

## Remaining qualification

Previous Planetary/Tau failures remain historical failures. One successful
cohort does not prove a unique cause or remove intermittent readiness risk;
these row records do not change production flight logic. The diagnostic source
has separate attribution. Full guarded frame/owner/native saving and terminal
lifecycle, physical hatch/lathe, dedicated/restart/client/content qualification
and R-021 remain open. C below 10 GB prevents local full/native runs. v1.8
remains IN_PROGRESS /IMPLEMENTING; **G0-G9 are not satisfied**.
