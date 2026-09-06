# v1.0.0 current-artifact CI integration

Local execution dates: 2026-09-05 to 2026-09-06 (Asia/Shanghai).
Branch: `codex/v1.0.0-stable-core`.
Base: `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`; uncommitted development tree.
This is development verification, not a GitHub Actions run, frozen candidate,
independent audit or human release approval. v1.0 and its Required Gates remain
**IN_PROGRESS**.

## Scope and decisions

The Forge workflow still selected `1.20.1-0.9.0-beta.1` while Gradle built
`1.20.1-1.0.0-dev`. All three jobs now derive exact version, main JAR and sources
JAR names from the checked-out `gradle.properties`. The helper accepts bounded
literal properties, rejects ambiguous keys/unsafe identity values, records the
input SHA-256 and writes only three validated `ARCE_*` variables. No glob picks
an arbitrary JAR, and the existing packaged-artifact audit still verifies its
actual contents and expected version.

Identity resolution follows `clean build`, so cleaning cannot delete the newly
created identity manifest; every artifact consumer follows resolution. The
workflow uses the documented [GitHub environment-file protocol](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands#setting-an-environment-variable)
for subsequent steps. Uploads include the exact artifact, metadata and test
evidence; the advisory latest lane now retains its JAR audit and reports too.

The immutable review-commit checkout, baseline/core blocking policy, latest
advisory policy, action versions, Forge pins and timeouts are unchanged. All
historical asset inventories, release validators and regression harnesses remain.
The old harness directory names identify regression datasets, not the version
of the JAR under test; their explicit version argument selects the new build.
Accepted Beta evidence is labelled as historical, not as v1.0 candidate approval.

No mod code, resources, persistence schema, protocol, dependencies or gameplay
features change in this CI slice. Prior PCL login is not repeated and no launcher
account is read. No project commit, push, tag or publication is performed.

## Verification checkpoint

- New metadata/workflow module: 20 tests. Together with all 128 existing
  repository-validator tests: **148 pass** using a full-form Windows temporary
  directory. This includes the 33 existing workflow mutation tests.
- Before wiring changes, the new consumer test fails in each of the three jobs.
  An intermediate negative-test helper incorrectly split YAML job boundaries;
  its failure log is retained and the helper is corrected, without weakening
  production checks or assertions.
- The first full repository-module run under inherited `ADMINI~1` temporary
  paths has two failures and three errors in existing Markdown fixtures. Their
  unresolved fixture roots differ from canonicalized returned paths. The same
  128 tests plus the 20 new tests pass with full-form `TEMP`/`TMP`; no test or
  production Markdown behavior is changed to conceal that platform limitation.
- Independent static tool `actionlint` 1.7.12 returns 0 for both active workflow
  files. The official release ZIP is SHA-256 verified before execution:
  `6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9`.
  This is a tool result, not an independent human review or runner execution.
- Strict repository validation: **45 pass**, no pending/warnings/failures.
- Standalone baseline `clean build`: exit 0, 27 seconds; 362 Java results in 70
  suites are cache-restored. The JAR remains 1,238,476 bytes, 766 entries, SHA-256
  `cf077ef750149f6b957ac4e8dc8a7da8e5cb91eafbe80703859157aac34a49b8`.
- The extended bootstrap partition completes separately: **95 pass**, exit 0,
  2664.700 seconds. Its source/test files are unchanged by this CI work and its
  [full result](../v1.0.0-compatibility/verification/checks/bootstrap-full-tests.txt)
  is archived with the original compatibility slice.
- The remaining Python partition completes with exit 0: **569 run, 565 pass,
  four skips**, 1091.210 seconds. Together with the disjoint 95-case bootstrap
  and 148-case repository/CI partitions, all **812 discovered tests** are
  covered: 808 pass and four existing skips for absent local v0.2-v0.5 JARs.
  This is a partitioned development run, not a single-process frozen-candidate
  acceptance run. The inventory records every included and excluded test ID.
  The initial discovery attempt used a top-level directory unsupported by this
  namespace-style test layout; explicit module loading enumerates the same
  `test_*.py` files and rejects unexpected nested tests or import errors.
  Expected installer-timeout and overlapping-directory rejection messages in
  unit-test stdout are negative test cases, not failed live server runs.
- The baseline local sequence completes in 35 minutes 16 seconds: 36 entries,
  one Windows-inapplicable `chmod`, 33 successful commands and the two retained
  clean-worktree failures. Its overall exit is 1, not a passing CI job.
  Actual commands pass artifact/resource/history checks, DataGen, all 44 required
  GameTests, dedicated start/restart, schema-1 migration/restart, a durable flight
  kill/recovery, Electrolyzer and celestial regressions, five-minute atmosphere
  and restart coverage, rocket assembly/recovery, and 20 Earth/Moon round trips
  (40 legs) with all eight restart cases. All packaged reports identify the same
  `cf077ef7...` JAR. The then-declared upload outputs were archived before the
  next clean build; the missing full-log issue below limits that first archive.
- The first core and latest sequences also complete with exit 0: current-JAR
  audit, dedicated restart, station regression, 100-mission satellite stress,
  and the latest-lane build/audit/44 GameTests all execute successfully. All
  three builds reproduce the unchanged development JAR.

## Full-log upload defect found during final audit

The first strict archive audit fails: historical server harnesses store full
console files in their `session` directories but write only summaries/filtered
receipts into `evidence`. The workflow uploaded only `evidence`. Thus the first
local archives contain successful command receipts and exact full-log hashes,
but not the referenced full server logs. The later clean builds have removed
those session files; they are **not recoverable from these archives**. No other
run's logs are substituted, and that failed audit is retained.

The workflow now uploads explicit `session/*-full.txt` and native `session/logs/`
entries for its three baseline sessions and the core session. `if: always()`
also retains diagnostic logs when a runtime step fails. These are bounded,
known log locations, not a wildcard mod JAR or a whole-world/account upload.
Historical harnesses and summary schemas remain unchanged.

The local driver now handles these declared log globs and checks every summary's
full-log filename/SHA-256 against the archived files before permitting another
clean build. Missing references stop the sequential run and preserve the runtime.
All 148 repository/CI tests pass again after extending upload-omission mutations;
`actionlint` again returns 0. Source inventory and executed scripts for the first
attempt are retained separately from the corrected attempt.

All three corrected sequences complete as `attempt-2`. Baseline runs from
15:38:03 to 16:11:29 UTC, core from 16:11:29 to 16:20:30, and latest from
16:20:30 to 16:22:16 on 2026-09-05. Their inventories contain 283, 214 and 186
files respectively; each reports `archive_complete=true` and no missing logs.
Baseline retains its two dirty-worktree failures and returns 1; core/latest
return 0. All three reproduce the same development JAR. Baseline/latest each
pass 44 GameTests; all three retain 362 Java results in 70 suites.

The completed strict audit passes: 726 source inputs are unchanged, the 812
Python IDs form disjoint partitions, every command/upload hash matches, and all
38 packaged process full-log references are present with matching SHA-256.
There are 37 normal exits and one deliberate durable-checkpoint kill, not 38
clean exits. See [the final audit](verification/checks/final-audit.json).
The final default-Forge restoration `clean build` also returns 0 in 19 seconds.
The local CI-contract task is verified; no Required Gate is self-approved.

## Local execution boundaries

The local driver reads the actual workflow commands and substitutes only the
recorded build/review environment. Windows uses `gradlew.bat` and an explicit
Python executable; POSIX `chmod` is not applicable. The official Forge installer
cache is seeded from the previously hash-verified download. Installer/server
commands themselves still perform their normal integrity and runtime checks.
Checkout/setup/upload actions are recorded as **not executed** locally.

Both clean-worktree commands correctly fail on the uncommitted development tree.
The driver retains those failures and continues other commands independently;
this is deliberately not presented as a passing CI job. Generated resources and
historical release files are checked separately for actual drift. Runtime logs,
command arguments and upload inventories identify the tested development JAR;
the base HEAD does not pretend to contain the uncommitted changes.

## Remaining acceptance

Bind future runner evidence to a reviewed candidate, investigate the retained
connection timeout, and complete fresh GPU/two-client gameplay acceptance,
independent review, uninvolved installation and human release approval. The core
job ID remains `satellite-acceptance`, but its display name/upload prefix changed;
hosted required-check bindings have not been inspected and need verification on
the actual PR. No remote Actions result is claimed.

On 2026-09-06 the owner deferred long-load testing and SSH setup in favor of
complete implementation and short defect regressions. The four-hour reference
workload remains not executed, not passed or waived. Do not rerun these completed
long sequences merely to accumulate more development evidence. Earlier
compatibility/recovery/upgrade results remain distinct, and no v1.1+ feature is
introduced here.
