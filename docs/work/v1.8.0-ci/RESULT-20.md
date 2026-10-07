# Exact readiness-wait history regression

Date: 2026-10-07. Source `046fc477ab80a9bc10d4170092bed6e0ad4e34ef`;
[run 37554222038](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37554222038),
attempt 1 /job 112576587243. Overall **FAILED** at the required GameTest step.
[RESULT-19](RESULT-19.md) remains the preceding format-source result, not this cohort.

## Actual commands and results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds;
  356 XML suites / **1,988 physical testcase elements** /zero testcase or
  container failures/errors/skips. All 30 `TransferFailureDiagnosticsTest`
  subjects match the fixed Git declarations and pass: nineteen unchanged and
  eleven new, not forty-one subjects. This hosted build includes the real Service.
- Artifact audit succeeds. `./gradlew runData --no-daemon --stacktrace` succeeds
  twice; both `git diff --exit-code` and tracked/untracked checks pass in the job.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: fails;
  **509 complete /one required failure**:
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`
  (`No rocket at Tau Ceti f`). The destination-readiness/reserved-pad fixture
  does not terminally fail in this cohort; that is not a demonstrated repair.
- Canonical GameTest stream: **63 ERROR /zero FATAL**. Duplicate streams are
  not summed; errors are not waived. Assertions, predicates, deadlines and
  selected subjects are unchanged.
- Raw-evidence upload succeeds; conditional build-JAR upload is skipped.
  **No new independently retained build-JAR bytes are available.**

## Raw association and recount

The [fresh terminal observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-transfer-wait-ci-terminal-20261007-01/OBSERVATION-01.json)
at 2026-10-07T01:04:33Z binds exact source/run/attempt/job and completed failure.
It supersedes the current pending status, without changing the separate earlier
00:52:58Z running capture or the dated source checkpoint's original observation.

Root's bounded artifact/log retrieval `016245` and resumed `6d2595` exit 0.
The unchanged pinned helper keeps credentials in memory at api.github.com,
never persisted or sent to signed storage. Artifact 11454530500 is 1,763,017 bytes
/SHA256 `5bee4d38805a31125ba6b2a7bb55486ff36a85baee45e82e71cf553e40724b7f`,
matching its API digest. Cohort/member/path/collision/size/ZIP CRC checks precede
compact raw retention; no container/source/class/world copy remains.

The [Root raw recount](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-transfer-wait-ci-terminal-20261007-01/AUDIT-01.json),
SHA256 `21072b9114faab2b92ec9f912dd4f3ede27e7cf695c1ce03c243ff85a73528aa`,
and terminal projection exit 0 at `4d2904`. All 403 named inputs rehash unchanged;
actual XML children/attributes and exact method identities are checked.
The [Root report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-transfer-wait-ci-terminal-20261007-01/REPORT-01.md)
has SHA256 `4b37b19f8f5fce70728816688be08b1ca4de5fe8d70a8f3f1175cb5b3b364e94`.
Its leaf is sealed: 419 payloads /13,750,012 bytes, manifest SHA256
`c3ca9ffbfd9fc9c018dc25f55955305d3db26873eaf54b33d9938e7bd0919b63`,
420-entry sums SHA256
`5c16965dcdc1157f7b5d022e096a112b3920fa85e4816cec5552c9045bf55281`.
Independent raw/factual review is separate; no verdict or Gate is prefilled here.

## Observations and remaining work

The [canonical GameTest log](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-transfer-wait-ci-terminal-20261007-01/artifact01/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log)
is 1,565,665 bytes /SHA256
`32684188894e2c6e8b144c557b2e08142213aa51d0f69c8c5ac3ef143bc7587d`.
Both Tau samples, lines 2712-2713, retain test tick 743 /game time 14951,
PREPARED, source Space TRANSIT/not removed, destination UNASSIGNED and loaded
YES /entities-loaded NO /entity-ticking NO. Line 2714 records LIVE/tracked manager,
last service/target tick 14950 and WAIT_ENTITY_READY, first wait tick 14841,
last 14950, attempts 110. These are actual qualified calls, not proof of continuous
native readiness, I/O completion, distinct-tick guarantees or a unique cause.
Failure is at line 2718; terminal results are at lines 5767-5769.

The observation-only source does not repair flight or readiness. Physical
machines, resource writers, conservation, dedicated recovery, clients,
performance, R-021, ledger closure and G0-G9 remain unfinished. No full local
build/GameTest/native starts below the 10 GB space threshold. v1.8 remains
**IN_PROGRESS / IMPLEMENTING**.
