# Exact chunk-metadata checkpoint regression

Date: 2026-10-07. Source `3e2f6f1ba96508305b78cdb27d95ebbeabad6a38`;
[run 37541636327](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37541636327),
attempt 1 / job 112535778550. Overall **FAILED** at the required GameTest step.
[RESULT-17](RESULT-17.md) remains the preceding resource-source cohort.

## Actual commands and results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds;
  fresh `:test`, 355 XML suites / **1,964 physical testcase elements** /
  zero testcase or container failures/errors and zero skips. The ten new
  `ClassicChunkRecordsTest` subjects match the exact Git test blob and all pass.
- `./gradlew runData --no-daemon --stacktrace`: succeeds twice. Both
  `git diff --exit-code` and tracked/untracked checks pass in the hosted job.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: fails;
  **509 complete / one required failure**:
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`,
  `No rocket at Tau Ceti f`. Assertions, deadlines and selection are unchanged.
- Canonical GameTest log: **64 ERROR / zero FATAL** lines, with no duplicate
  stream addition or blanket waiver. The build-JAR upload step is skipped;
  **no new independent build-JAR bytes are available**.

## Raw association and Root recount

Root's [fresh observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-chunk-records-ci-20261007-01/OBSERVATION-01.json)
at 2026-10-06T23:00:03Z uses bounded public GET requests (tool `736d12`, exit 0).
It binds the exact source/run/attempt/job and completed failure. Earlier
22:37 metadata remains immutable in its separate original evidence directory.

One bounded regression-artifact retrieval (`1dd6e4`) and one attempt-log
retrieval (`b01725`) each exit 0. The retained receipts enforce cohort, digest,
path, member, expanded-size and CRC checks using the unchanged retrieval helper;
only its output root is rebound in memory. API credentials stay in memory and
are not sent to signed storage or written into evidence. Artifact 11448832370
is 1,757,354 bytes / SHA256
`a1f8779d6d043b06441f5f9523cfa06570e8517f6187446eae270b9d61bc7cb4`,
matching its API digest. Only compact raw log/XML/JSON/text members are retained.

Root's [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-chunk-records-ci-20261007-01/AUDIT-01.json),
SHA256 `0b1d900a7cdd21147f5c7d194dad5e479dce021a64fb0acfb85644f0a34e3264`,
exits 0 at tool `257f67`: 402 named inputs rehash without drift, actual XML
children and suite attributes are checked, and the new subjects match the exact
committed test methods. Its initial console projection is truncated; retained
audit and raw bytes are complete. Tools `04911d`, `80f98e` and `55febb` inspect
the actual terminal summary, readiness samples and canonical severity counts.
This Root recount is not an original-JAR replay.
The [Root report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-chunk-records-ci-20261007-01/REPORT-01.md)
and raw leaf are sealed with 413 payloads /13,668,448 bytes; manifest SHA256
`6ded3331279e3184608dd7838dd7ad24f00958998e7d4813b64f69f1bd6dc424`,
414-entry sums SHA256
`4232d206249eab1844ababa155ff051153ee9ad6dfef0f8ebde8ad8c336b2d6a`.

The separate [independent raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/chunk-records-ci-independent-audit-20261007/REVIEW-01.md)
is sealed at SHA256
`95e72d39c364c02210e2d07372b25a2e75da9e268fbe4149309f3f6eef39c7fa`.
Its independent recount confirms 398 retained raw files, 355 XML /1,964 actual
cases /0FES, the exact ten new subjects, and the same 509-complete/one-required
failure. Its 406 raw/metadata/policy pins plus the separate Root recount pin
remain unchanged; no identity, parsing or count discrepancy is established.
Its 16-payload /421,143-byte evidence has manifest SHA256
`cdb4f45202a7c3aa6e10b6ed122ba365a5cdd0f2fc5026e44023daa69bfa9f03`
and 17-entry sums SHA256
`591484ec650047ec927005f6f0e7315711b03de0f35d39f3a55be3f5be37969b`.
Overlapping main/latest/debug/job streams are not added as independent error
totals. No new Java/network/native execution or JAR-byte verification occurs.

The [canonical GameTest log](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-chunk-records-ci-20261007-01/artifact01/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log)
is 1,567,018 bytes / SHA256
`0e297cfa9287030bc8476e74c04f7293ee049cdc532b20c1639db38a4fc84acb`.

## Qualification still open

Lines 2692-2694 show PREPARED, source Space TRANSIT/not removed, destination
UNASSIGNED; loaded YES, entities_loaded NO and entity_ticking NO in both samples.
The installed manager is LIVE, flight_active YES, entered/completed/last tick
14963 at current tick 14964, with WAIT_ENTITY_READY. Lines 5766-5768 name the
single required Tau failure. Discrete samples do not prove a unique native cause
or a production correction; the existing pad priming is not passive observation.

The new reader remains data-only: no installed owner, FULL observer, capability,
save writer, final-save/restart or real-client qualification. The 39-file owner
candidate and its 41 declared tests are outside this committed source cohort.
Ledger closure, R-021 and all Required Gates remain open. Local full/native runs
remain barred below 10 GB; hosted execution does not waive those obligations.
v1.8 remains **IN_PROGRESS / IMPLEMENTING**.
