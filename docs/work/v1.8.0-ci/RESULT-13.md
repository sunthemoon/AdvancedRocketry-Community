# Exact corrected detector regression

Date: 2026-10-07. Source `33a3156e309ca2b8f6a1fcc766501968bc21f138`;
[run 37506268620](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37506268620),
attempt 1, job 112415607085. Result: **FAILED**, at the required Tau GameTest.
This supersedes the dated running observation for this run, not the original
failed source in [RESULT-12](RESULT-12.md).

## Actual results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace` succeeds.
  All 351 XML suites contain **1,918 actual testcase occurrences, 0 failures,
  0 errors, 0 skips**, matching their attributes. All 23 detector-related unit
  cases, including the corrected tooltip fixture, pass.
- First `runData` writes 808 entries; repeat writes 0. Both builds succeed,
  both tracked diff streams are empty, and both worktree checks explicitly
  report clean including untracked files.
- `runGameTestServer` exits 1: **504 GameTests complete; one required failure**,
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`, with
  `No rocket at Tau Ceti f`. The main stream enters the eleven-test
  `seal_detector:1` batch; the terminal required failure list contains Tau only.
  This does not independently certify every detector runtime requirement.
- The primary main stream has 63 ERROR /0 FATAL headers. Overlapping copies
  are not added to this count, and no blanket ERROR waiver is granted.
- Hosted artifact audit reports 3,431 JAR entries and SHA
  `f44206e5acc37e9ceedd7d883f5a6b81de45004a63ea835b80ed43811c3a0a81`.
  JAR upload is skipped after GameTest failure; no independent JAR bytes are
  downloaded or validated. Raw regression upload succeeds.

## Exact evidence and independent audit

[Collector report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/detector-33a-collector-preparation-20261007-c18-84a6e2/REPORT-01.md),
SHA `d39af2a20fc765a59cee70f03d593974e02ad5a6780029f363fe1c72dde7b694`,
records one admitted serial metadata/artifact/log collection: outer and three
children exit 0, followed by audit and sealing exit 0. No network retry occurs.
Artifact 11432272472 is 1,746,054 compressed bytes; API/container SHA
`24980674598111a7f98769b08e39a41decde4c6579b0d5e82e09624ba72bc800`.
[Artifact receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/detector-33a-artifact-20261007-c18-84a6e2/RETRIEVAL-01.json)
SHA `e42c1372b5075f2009da9e36fbdce4dbf8de1d781d438853533d93689e573f09`;
[attempt-log receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/detector-33a-attemptlogs-20261007-c18-84a6e2/RETRIEVAL-01.json)
SHA `74ad4997dc6062cd05bdcd35d55103979392e12eb7f9346505f64038eb4f709c`.
The finite collector checks archive paths, bounds, CRC and artifact API digest
before retaining thin files; discarded archives are not independently replayed.

Root's separate [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-detector-33a-raw-audit-20261007-01/AUDIT-01.json),
SHA `b9066ddc4b222e4d0e32c3c17be579bf9f63f9be14cdc8fd84c86a10b97dc443`,
actually exits 0 (tool `dabf47`): 394 retained files /12,572,608 bytes match
their receipts. It parses all XML actual children and attributes, verifies the
exact tested commit, reads first/repeat DataGen and cleanliness, and checks
the primary GameTest terminal/failure set. This audit does not rerun Java.
The collector independently records 406 named observations without drift.

## Tau observations and remaining scope

The [primary main stream](D:/GitHub/ARCE-Task-Evidence/v1.8.0/detector-33a-artifact-20261007-c18-84a6e2/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log)
is 1,560,029 bytes, SHA
`9cc9e5f9417acbeace1be2330a4e6b02820524d0ed8ba5b7fc0957d0126114d0`.
PRE/POST at lines 2710-2711 correlate transfer
`d087ad44-8cff-458f-b6ab-e765c0a51c86`: PREPARED, source Space rocket
TRANSIT/nonremoved, destination UUID UNASSIGNED, origin `(1,74,0)`, loaded YES
and entities_loaded NO. Entity-ticking changes **NO to YES**, unlike the earlier
Tau cohort. Both samples retain test tick 743 and game time 14896; their nanos
differ by 1,013,685,266. These are two sequential samples, not continuous
readiness, live-service membership, native load-completion or unique-cause proof.

[Independent investigation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/detector-33a-gametest-investigation-20261007-905ed2/REPORT-01.md),
SHA `e631c26bee1c92bf8d9858469cfc1151ce79f747f164ae92e6a9f1d38619fd47`,
checks fixed source and raw observations; no particular production correction
is justified. Readiness checks, cold destination, 270/1400-tick limits and
landing/return assertions remain unchanged.

The narrow tooltip fixture correction is now executed successfully. Whole-item
actor/thread/closed-host/post-query ownership/call-order coverage, installed
custom rules, native/restart, real clients, content closure and R-021 remain
open. v1.8 stays IN_PROGRESS /IMPLEMENTING; G0-G9 are not satisfied.
