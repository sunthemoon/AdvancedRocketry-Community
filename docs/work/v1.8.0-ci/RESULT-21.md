# Exact inactive save / Tau development regression

Date: 2026-10-07. Source `7ab1b0879527f4d8d3e88f09f9360015175b911a`;
[run 37560119538](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37560119538),
attempt 1 /job 112595248301. Overall automatic regression **PASSED**.
[RESULT-20](RESULT-20.md) remains the preceding failed cohort; one successful
run does not prove deterministic Tau readiness or close its earlier failures.

## Actual commands and results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds;
  367 XML suites / **2,067 actual testcase children**, zero testcase failure,
  error or skipped, zero container failure/error, matching suite attributes.
  Eleven changed suites /79 cases match exact fixed Git declarations: ten
  machine suites /70 cases and one Tau text suite /nine cases. Unlike the
  preceding production-only local compile, these are executed hosted tests.
- Artifact and common/client-boundary audits succeed.
  `./gradlew runData --no-daemon --stacktrace` succeeds twice; both
  `git diff --exit-code` and tracked/untracked clean checks pass in the job.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: succeeds;
  canonical lines 5797-5798 report **509 complete /all 509 required passed**.
  There are no Tau missing/holder/service-failure samples in this stream.
  This does not exercise the new failure-only holder branch or prove a fix.
- Canonical GameTest stream: **62 ERROR /zero FATAL**. Duplicate streams are
  not summed; errors remain unwaived. Assertions, deadlines, ticket behavior
  and selected native fixtures are unchanged.
- Both raw-evidence and single external build-JAR uploads succeed.

## Exact raw association

The separate [terminal public capture](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-classic-save-tau-ci-observation-20261007-02/OBSERVATION-01.json)
at 2026-10-07T02:17:33Z reports completed success, updated 02:14:48Z. It
supersedes live pending status without rewriting the earlier dated 02:06 capture
or the [source-integration record](../v1.8.0-c16a-hatches/GUARDED-SAVE-SOURCE-INTEGRATION-01.md).
The five preceding factual records were separately reviewed and normally
published at `44df7fe9538d4683ad6bc4978ecd609d3b1e6bff`; their review covers
the dated running capture, not this terminal result.

Root's bounded metadata/retrieval `151c03`, `e811e0` /completion `d07061`
exit 0. The unchanged helper keeps API credentials in memory, never persisted
or sent to signed storage. Cohort/member/path/collision/size/digest/ZIP CRC
checks precede compact raw retention; no archive, HTML, source, class or
world copy is retained. Regression artifact 11456866726 is 1,796,717 bytes,
SHA256 `a03124097eaa66ba8be7d19a79783e5088bb1d76d02a03f338a0f1ab5a7dcb77`.

[Root raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-classic-save-tau-ci-terminal-20261007-01/AUDIT-01.json),
SHA256 `1290ede00dab57e46d30f482f4d3bc3d08ca777bb0878e579448238296017479`,
exits 0 at `5de70c`: 417 input pins remain unchanged, actual XML children,
container events, attributes, changed source subjects and native terminals agree.
The [Root report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-classic-save-tau-ci-terminal-20261007-01/REPORT-01.md)
has SHA256 `7bb138e95e1383f96824d0bfc7424b0f995a5ef649f64cda77783d7a0c0c8803`.
Its seal `010558` covers 442 payloads /13,714,825 bytes; manifest SHA256
`cb353af12868d5b16138385620d40c1ff810cf7ba3fa3f7f17138ce63103b79f`,
443-entry sums SHA256
`00c4db7b460ff8fa7de4c638defebb22172521e54458cb33c4f195bd66659b70`.
Different-agent raw review is separate; its conclusions are not prefilled here.

The subsequent [independent terminal raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-save-tau-ci-terminal-independent-audit-20261007/REVIEW-01.md),
SHA256 `db5eaf5f9903d3162323e8290aaad1effa5f7f50f0856e6414d84ecfe111cbe3`,
finds no factual discrepancy in the exact retained result/report/seal. Its actual
recount exits 0, with 39 finite controls and 453 unchanged final pins; these
controls are not Java tests. It independently checks raw counts, changed source
subjects, native terminals, command/DataGen facts and retained JAR digest
association, not a fresh binary rehash or inner-entry parse. All read holds are
explicitly released; one successful cohort, unwaived errors and all Gate limits remain.

## External build artifact

Build artifact 11456502168 is 5,133,381 compressed bytes, SHA256
`fc1d74b786ad06d10e148fb6083b294820665d6e3da72d9ece6f06cd2527f4a3`.
The actual downloaded archive contains exactly
`advancedrocketry-community-1.20.1-1.8.0-dev.jar`, 5,884,169 bytes, SHA256
`93bbcb1894ecd6d59c371a2e0c40ed214c93bdcf05ff862aedba50b4af62ca11`.
The helper hashes the whole JAR bytes in memory; that identity agrees with
the hosted checksum, build identity and 3,496-entry hosted manifest. This is
a whole-byte digest/provenance association, **not independent inner-JAR entry
reparsing**; no local JAR is retained. Both outer digests match API metadata.

## Remaining work

The [canonical raw stream](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-classic-save-tau-ci-terminal-20261007-01/artifact01/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log)
is 1,559,818 bytes, SHA256
`91bdc4a004f0d5af404e4acbffeeed4377b916a8ff91187de4690a1ccb7210da`.
New machine source remains unregistered/inactive: passing these tests is not
physical hatch placement, inventory conservation or native save/restart proof.
Metadata-transition witnesses, resource writers, final save ordering, dedicated
recovery, real clients, performance, continuous readiness/unique Tau cause,
R-021 acceptance, ledger closure and G0-G9 remain open. No full local
build/GameTest/native starts while C: is below 10 GB; unrelated Java processes
are untouched. v1.8 remains **IN_PROGRESS / IMPLEMENTING**.
