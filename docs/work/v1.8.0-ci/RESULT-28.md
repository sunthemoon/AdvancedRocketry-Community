# Airlock successor: units/DataGen pass, two required native failures

Date: 2026-10-07. Exact source:
`01521d6cf56bfac0d88d05ac74389f2c36dc4887`.
[Run 37595775604](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37595775604),
attempt 1 /job 112707924073. Automatic regression: FAILED.
[RESULT-27](RESULT-27.md) remains the original dated running observation.

## Executed commands and raw outcomes

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace` succeeds.
  Actual retained XML: **372 suites /2,097 testcase children**, zero failures,
  errors/skips or direct suite failures/errors. All fourteen airlock block,
  provider and runtime-lifecycle cases match the fixed-source declarations.
- Artifact/client-boundary audits succeed. `./gradlew runData --no-daemon
  --stacktrace` runs twice successfully; both tracked diff logs are empty and
  both tracked/untracked clean-worktree checks pass.
- `./gradlew runGameTestServer --no-daemon --stacktrace` fails. Canonical lines
  5771-5775 report **525 complete /two required failures**: existing
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns` and
  `bothhalfcallbacksrevokeinstalledcachedinflightandcompletedairbeforetick`.
  The seven-test airlock batch executes at line 2812. Finite loot sampling and
  the Earth-Mars-Venus subject are not named failures in this cohort; there is
  no individually named native pass XML. One nonfailure does not establish a
  unique historical PRNG cause or close intermittent rocket readiness.
- Canonical console has **64 ERROR /zero FATAL**, unwaived. Failed-run raw
  upload succeeds; external built-JAR upload skips.

Supply failure at line 2813 retains the new scalar observation:
`upperOnly=false phase=0 status=OPEN oxygen=1000 energy=40000 scan=NONE
indexed=false tracked=1 active=0 pending=0 dirty=0 inspections=376`.
The assertion fails before the revocation phases. This establishes available
resources and the observed OPEN status, not which native world observation
caused it, a supply repair, or cached/in-flight/completed revocation success.
Tau still reports `No rocket at Tau Ceti f` at line 2716.

## Retention and Root audit

[Terminal metadata](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-fixture-ci-regression-20261007-01/MONITOR-12.json)
captured at **2026-10-07T08:56:09.793714Z**, SHA-256
`d5791b3240b34639e157c4687886547180e103b8974332fbd4f22747a9353230`,
binds source/run/attempt/job and all final step statuses. Root's monitor ends
normally (`cb666f`, exit 0); selected raw lines/metadata are reread at `ea642f`.

Bounded retrieval `dc20c6`, exit 0, retains
[RETRIEVAL-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-fixture-ci-raw-20261007-01/RETRIEVAL-01.json),
SHA-256 `2f0a201b6d6b55e2cd2b0943d9e6b69987b2b7a7604fdb13d21c53c493bd7408`.
Artifact 11470953083 has 1,821,221 archive bytes with matching API/actual digest
`36ac9817cf3f868533e43750c70cb3d6afb6a7d3caba3e9d6353dc2eb5603a39`.
397 compact raw files /7,673,426 bytes are retained, not a server/JAR/archive
or exported source/class tree. Existing noninteractive credentials and signed
redirects remain in memory. Retention is not itself verification.

Root's [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-fixture-ci-regression-20261007-01/RAW-AUDIT-01.json),
SHA-256 `a0a59f0b7e1ad304e7c639b8bc6846f8f6782de9cc77da307e13b6f252defd98`,
executes at `4108e1`, exit 0. It independently parses actual XML children and
attributes, matches three committed test method sets, reads generation/clean
logs and the canonical terminal/batch/failure observations, and checks hosted
manifest resource membership. All retained inputs are rehashed before/after
with zero drift. The separate
[different-agent raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-fixture-ci-independent-raw-audit-20261007-01/REPORT-01.md),
SHA-256 `30c12ff63f35f32c371d0827fa27d0757b8a281f023c881a232d2b9f180c6f25`,
independently derives agreeing XML, 137 batches/terminal, failure scalars,
severity/loggers, twice generation and 21 fixed-Git/manifest resource hashes.
Its [thin results](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-fixture-ci-independent-raw-audit-20261007-01/RESULTS-01.json)
have SHA-256 `a2a65c6f4202ca68bfd737c5b8208a771a14a45c8616a37d749eb813312338ef`.
All retained inputs agree before/after, with zero drift. Root reads/hashes the
complete report (`dd5cb9`, exit 0). The review's guessed path, injected Path
signature and GBK decoding command failures/corrections remain disclosed;
they are not product failures or rewritten native outcomes.

Hosted manifest records JAR SHA-256
`a42b722eb2706d9a5923512e20a92bdcd258acc5bcaf858c72fec9a2f153f6cf`
and 3,525 entries, including all 21 unchanged airlock resources and its three
primary classes. This is hosted manifest/identity evidence, not independent
inner-JAR parsing, downloadable release qualification or client rendering.

## Open work and boundaries

The preceding four-failure [RESULT-26](RESULT-26.md) is unchanged. Sampling
input revision preserves its oracle; supply revision remains diagnostic-only.
No production repair is inferred from this run. Native world-state evidence is
needed before any supply correction, without relaxing assertions/deadlines or
budgets. Cold Tau readiness, unwaived errors, packaged restart, V1/V2, full
content qualification and G0-G9 remain open. Local JVM/native work stays
prohibited by C free space below 10 GB; all new scratch is in the D project
parent. No risk acceptance, ledger closure or whole-version completion occurs.
