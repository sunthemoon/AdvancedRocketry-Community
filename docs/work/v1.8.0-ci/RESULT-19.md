# Exact power-carrier format regression

Date: 2026-10-07. Source `257e7b3adb54dc693d815434394f311d28e800cc`;
[run 37551957636](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37551957636),
attempt 1 /job 112569285797. Overall **FAILED** at the required GameTest step.
[RESULT-18](RESULT-18.md) remains the previous chunk-metadata source cohort.

## Actual commands and results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds;
  356 XML suites / **1,977 physical testcase elements** /zero testcase or
  container failures/errors/skips. All 13 newly committed
  `ClassicPowerCarrierFormatTest` subjects match their exact Git methods and pass.
- Artifact audit succeeds. `./gradlew runData --no-daemon --stacktrace` succeeds
  twice; both `git diff --exit-code` and tracked/untracked checks pass in the job.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: fails;
  **509 complete /two required failures**:
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`
  (`No rocket at Tau Ceti f`), and
  `destinationspawnwaitsforentityreadinessandrechecksthereservedpad`
  (`Ticketed destination did not become ready`).
- Canonical GameTest stream: **64 ERROR /zero FATAL**. Duplicate main/latest/
  debug/job streams are not summed as separate severities. No error waiver,
  assertion relaxation, longer deadline or test-selection change is made.
- Raw-evidence upload succeeds; the conditional build-JAR upload is skipped.
  **No new independently retained build-JAR bytes are available.**

## Raw association and Root recount

The [fresh terminal observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-power-carrier-ci-20261007-01/OBSERVATION-01.json)
at 2026-10-07T00:38:15Z binds the exact source/run/attempt/job and completed
failure. The separate 00:31:22Z running capture remains unchanged.

Root's bounded artifact and exact-attempt log retrievals exit 0 at tools
`65584f` and `b0e9f1`, using the unchanged pinned helper with a new output root.
Credentials stay in memory and are never sent to signed storage or persisted.
Artifact 11453935259 is 1,758,487 bytes /SHA256
`3b2d24779740eed79ec8f0f91e736e622df3828b2f9660601f585df17b5b64fc`,
matching its API digest. Cohort, member/path/collision, size and native ZIP CRC
checks precede compact raw retention; no container/source/class/world copy is kept.

The [Root raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-power-carrier-ci-20261007-01/AUDIT-01.json),
SHA256 `528558be494b1c45f2345fd3fd168e48031de83c87a137203541cc69b8f42220`,
exits 0 at `e5dafb`: 403 named raw/receipt/observation inputs unchanged, actual
XML children and attributes counted, and exact new method identities checked.
The initial console projection is truncated; retained audit/raw bytes are complete.
Root's separate terminal/severity projection exits 0 at `386f5e`.
The [Root report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-power-carrier-ci-20261007-01/REPORT-01.md)
and raw leaf are sealed: 416 payloads /13,649,692 bytes, manifest SHA256
`4fc1f0fa23f2d7c3501de9931fc831997725732a62ff2b40c541c502250fbcc1`,
417-entry sums SHA256
`53f027f79b98d6037fe8c7cf38bd4c7fd0be1b5575c3a77c719bd07bab7636e7`.
Independent exact-raw review is recorded separately when completed; these Root
observations do not prefill that verdict or qualify release acceptance.

The [canonical GameTest log](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-power-carrier-ci-20261007-01/artifact01/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log)
is 1,562,001 bytes /SHA256
`cc8216df8e1c1b5fba70a79d89c52cbc4f4910dee72f95f38aba1d781dcaa39d`.

## Remaining qualification

Tau samples at lines 2692-2694 remain PREPARED, source Space TRANSIT/not removed
and destination UNASSIGNED. Entities-loaded is NO in both samples; entity-ticking
changes NO to YES. The LIVE manager last records WAIT_ENTITY_READY at 14911,
current 14912. Samples do not prove continuous waiting or a unique native cause.
The two terminal required failures are at lines 5744-5747.

The new helper is data-only: no Item lease/ticket, physical hatch/controller,
save writer, charged placement/drop conservation or native recovery admission.
The separate wait-history candidate is outside this source cohort. Its existence
does not repair or supersede these failed results. R-021, ledger closure and
G0-G9 remain open; v1.8 remains **IN_PROGRESS / IMPLEMENTING**. No full local
Gradle/GameTest/native run is started while C: is below 10 GB.
