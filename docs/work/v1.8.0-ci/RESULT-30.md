# Sky-switch checkpoint: failed full regression with Root raw audit

Date: 2026-10-07. Source `cbbb78e1fdc8dd88128162d43d88125c7aee9b60`.
[Run 37600300782](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37600300782),
attempt 1 /job 112722759189. Terminal: **FAILED**.
Root raw audit is complete; different-agent raw audit is pending. This is not
full feature qualification, real-client observation or a Gate decision.

## Exact source, terminal metadata and raw retention

The dated [running observation](RESULT-29.md) stays historical. Root monitor
ends normally (`180662`, exit 0), with terminal observation at
**2026-10-07T09:31:47.326403Z** in
[MONITOR-07.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sky-switches-ci-regression-20261007-01/MONITOR-07.json),
SHA-256 `7cca9d55c02e05ff9fe88b7c6e1f7e7d006228327ae01cf92f660c4ca6260723`.
Build, artifact audit and twice DataGen/clean steps succeed; GameTests fail.
Failed-run raw upload succeeds; the separate external JAR upload is skipped.

Root fully reads and rehashes the existing bounded retrieval helper (`70d877`),
then retrieves only artifact **11472816899** into a fresh D leaf (`28ed47`, exit
0). The 1,817,659-byte in-memory archive matches the API digest and actual
SHA-256 `db1d6214cef8cf7d9fa7a7f5277e1f2bc9b442180448fea98fa88bfda63c3ac9`.
All 893 member names, duplicate/ancestor/symlink constraints, 60 MB expanded
bound and CRC are checked. Only 397 raw log/XML/JSON/text members totaling
7,635,459 bytes are retained; no source/class/archive/runtime copy is saved.
Credentials and signed storage URLs remain in memory, not reports.

[RETRIEVAL-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sky-switches-ci-raw-20261007-01/RETRIEVAL-01.json)
is 340,274 bytes, SHA-256
`4115bd4d20eca53bb38b497a18c12c9663e39d534f99f166b83b58f5a7d26659`.
Root's new [RAW-AUDIT-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sky-switches-ci-regression-20261007-01/RAW-AUDIT-01.json),
SHA-256 `57aef7577ab46d9dbb3137595537d7bb8bb15e5d8a3c15b2585963c4cc2cb5e8`,
derives the following from actual retained input (`ee769f`, exit 0), with all
397 before/after checks and receipt unchanged. No runtime is replayed.

## Actual command results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: success.
  **372 JUnit XML suites /2,104 actual testcase children**, zero failures,
  errors or skips, including zero direct/descendant failure/error elements.
  The seven PlanetaryEffectsTest and three ClientConfigTest method names match
  their exact committed declarations, all passing. Four airlock block, five
  provider and five runtime-lifecycle unit methods also match and pass.
- Artifact and client-boundary audit: success. Hosted manifest identity is
  `1.20.1-1.8.0-dev`, Minecraft 1.20.1; JAR SHA-256
  `a4ab5a0ac901b45bc8cabe2f2b55ae2950aa342c4cda53a0e5f2caf61bd46a0d`,
  **3,525 entries**. Root checks the sixteen paths named for airlock against
  exact Git bytes and seven selected sky/config/airlock production class
  members. This is retained manifest verification, not a downloaded JAR or
  independent inner-binary parse, and not a complete per-asset/client Gate.
- Two actual `./gradlew runData --no-daemon --stacktrace`: success. Both
  subsequent `git diff --exit-code` logs are zero bytes; both tracked and
  untracked clean-tree checks pass. No generation diff is ignored.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: failure.
  **525 completed GameTests /three required failures**, listed below. No
  per-method native pass XML is retained.

## Required native failures and open scope

Canonical log `_temp/arce-v180-regression/gametest.log` is 1,568,315 bytes,
SHA-256 `53f839e7e77845df0f7a6fdf83044e9d2cd963ff4763f02302ab0014f0cda5d4`.
Its terminal summary starts at line 5727. Required failures are:

1. `earthmarsvenusearthkeepsonerocketandexactfueldebits`: line 1958,
   "Planetary flight did not land at the requested body".
2. `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`: line
   2668, "No rocket at Tau Ceti f". The preceding observations retain PREPARED,
   source TRANSIT still present, loaded/entity-ticking destination with entities
   not loaded and 110 service waits. This is not a unique native-cause proof.
3. `bothhalfcallbacksrevokeinstalledcachedinflightandcompletedairbeforetick`:
   line 2766, first `upperOnly=false`, `phase=0` supply setup fails. Status OPEN,
   oxygen 1,000 and energy 40,000 remain; scan NONE, no indexed chamber,
   tracked 1/active 0/pending 0/dirty 0/inspections 376. Later callback/recovery
   assertions remain unqualified. The airlock batch at line 2765 contains seven
   subjects; absence of other named failures is not individual pass XML.

The canonical stream has **65 ERROR /zero FATAL**, unwaived. These are log
occurrences, not a distinct defect count. Prior failures/results remain in
RESULT-28 and RESULT-26 without rebinding. Earth-Mars-Venus recurrence keeps
its readiness issue open; no relation to sky switches is established here.
The separately authored fixture lifecycle experiment is not part of this source.

## Remaining verification and ownership

Different-agent raw derivation is still pending. Real-client sky/fog/cloud hook
dispatch, all four switch states, V1/V2, resource reload, packaged restart and
complete v1.8 G0-G9 remain open. No assertion, timeout or resource budget is
changed, no R-021 disposition or physical hatch activation occurs. C is below
10 GB: all new scratch/logs/scripts are in D's project-parent evidence directory;
no local Java/JVM/Gradle/native/client execution is run.
