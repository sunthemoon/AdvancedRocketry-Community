# V130-COMPAT — Independent provider faults and compatibility inventory

## Scope and identity

Baseline `c9b79e57a011b258ea09b713947ac2ed04e95d5e`, branch
`codex/v1.3.0-public-api`; fixture implementation `0e90a68`.
Root owns tracked changes; the independent reviewer
uses read-only source access and Temp-only evidence. The user-supplied untracked
documentation bundle remains outside the read/write scope.

This slice completes the existing API-only fixture's missing failure cases and
the reader-facing [compatibility inventory](../../API-COMPATIBILITY.md). API 1.7
still exports exactly 27 classes across seven functional families plus version
metadata. No production source, public signature, world schema, network format,
Gradle configuration or generated asset changes. No upstream import or new
third-party dependency; existing license/provenance notices remain intact.

## Implemented behavior

Eleven new fixture GameTests use the actual installed public registration,
production assembly command and owner disassembly interaction, not an internal
mock registry:

- Five source-scan cases cover throwing/slow `canMove`, throwing/slow capture,
  and an oversized capture. Refusal preserves the exact source instance,
  blocks, named cargo and native inventory; removing the fault permits retry.
- Four restoration cases throw, return false, return slowly or restore different
  contents **after writing the host-created target**. Checked cleanup leaves no
  partial blocks, BlockEntities or item drops, preserves the complete serialized
  rocket authority, and permits a healthy retry with exact original cargo.
- The synchronous restore scope is cleared even when its caller throws.
- Actual MOD-bus registration rejects both a duplicate owned adapter ID with an
  otherwise valid different type and a new owned ID claiming the existing type.
  The original registration remains usable. These are two attempts from one
  fixture mod, not independently authored conflicting mods.

Fault controls are per source BlockEntity or a `finally`-cleared thread-local
probe containing only test fields, never saved world state. Each slow case targets
a finite 20 ms delay; scheduler overshoot is retained and the production 5 ms
returned-time budget is unchanged. This tests
rejection **after return**, not callback interruption or same-JVM isolation.
The oversized assertion distinguishes payload validation from incidental timing
failure; the mismatch assertion observes post-restore capture. Receipt logging
runs outside the timed callback. No journal/crash guarantee is inferred from
these synchronous authority and cleanup assertions.

The inventory explains minimum API versions, MOD/FORGE bus and thread/side
lifetimes, removal/failure behavior, supported-use limits and deprecation policy.
The consumer README now lists all 27 exports and links the existing bounded
flight, recovery, atmosphere, suit, component, fuel, environment and satellite
scenarios. Historical per-feature evidence keeps its own artifact scope.

## Actual commands and results

Windows, Java 17.0.7, Forge 47.4.10, Python 3.13.15. Root raw logs and consumer
reports are in `build-evidence.zip`; the independent raw attempts remain separate.

| Command/check | Observed result |
|---|---|
| `gradlew.bat test --tests '*RocketAdapter*Test' --tests '*RocketBlockEntityAdapter*Test' --console=plain` | Baseline passed, 16 s; `:test` executed |
| `gradlew.bat compileAdapterTestJava --console=plain` | Passed, 12 s |
| `gradlew.bat runGameTestServer --console=plain` | Initial 209 Required passed, 101 s; before the additional registration test |
| `gradlew.bat clean build runData publishMavenJavaPublicationToLocalProjectRepositoryRepository runGameTestServer --console=plain` | Passed, 140 s; 210 Required GameTests executed; DataGen written 0 |
| `gradlew.bat build --console=plain` | Passed after the first review corrections, 17 s |
| `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache --console=plain` | Passed, 19 s; all ten tasks executed |
| `python -B -m unittest discover -s tests -p 'test_v130*.py' -v` | 77 passed |
| `python -B scripts/run_v130_environment_smoke.py <fresh-server> --host-jar <immutable-host> --fixture-jar <immutable-consumer> --evidence-dir <fresh-evidence> --java <java17> --accept-eula` | Two clean exits 0, 50.79 s total |
| `python -B scripts/validate_repository.py --require-approved-identity` | 45 checks passed |
| `python -B scripts/validate_v1plus_planning.py` | 11 plans and 33-input inventory passed |
| Markdown relative-link check | 1,443 targets checked; none missing; user bundle excluded |
| `git diff --exit-code -- src/main src/generated build.gradle compat-test-mod/build.gradle` | Passed; production, generated data and build configuration unchanged |
| `git diff --check` | Passed |

The clean build restored the unchanged full JUnit result **from Gradle cache**:
769 tests / 143 suites, zero failures/errors/skips. `junit-full.zip` preserves
the XML before targeted reruns. It is not represented as 769 newly executed
tests in this slice. Baseline focused tests and the actual GameTest processes
did execute. No JUnit/Python cases or production assertions were removed.

## Review findings and retained failure

Initial source review identified two weak distinctions: generic scan rejection
did not alone prove the payload-size branch, and restoration rejection did not
alone prove readback. The fixture now checks the bounded diagnostic and scoped
capture count. No timeout, production budget or expected resource count changed.

The first independent 210-test run then exited 1: the new oversized diagnostic
assertion omitted the production formatter's final `}`. Actual saved diagnostic
already identified `RocketSnapshotException`; no production payload acceptance
failed. The assertion now requires the exact `detail=<fixture type>:
RocketSnapshotException}` suffix. The failing log, source snapshot and review
remain archived rather than relabeled as a pass.

The corrected independent `runGameTestServer --offline --no-daemon
--no-build-cache --console=plain` executed all 210 Required tests successfully
in 101.079 s, reusing its disposable GameTest world without a clean. All nine
fault probes were observed; mismatch reached one readback and earlier restoration
failures reached none. No timeout or assertion was relaxed.
Its separate 2,060 ms / 41 tick warning is retained; this is not a performance run.

Independent `test --tests '*ExternalRocketBlockEntityAdapterBudgetTest'
--rerun-tasks --offline --no-daemon --no-build-cache --console=plain` executed
all sixteen Gradle tasks and passed five budget tests in 38.540 s. The separate
consumer's `verifyConsumerBoundary --offline --no-daemon --no-build-cache
--console=plain` passed in 21.305 s. Standalone `test` is not the acceptance
mechanism: classpath, archive and negative-import checks perform the boundary
verification. Raw commands/results, XML and the retained failing attempt are
in `independent-review.zip`.

The independent artifact audit confirms 27 API class entries with matching
runtime bytes, 114 real dependency JARs, all 25 fixture source/resource hashes,
the expected negative-import diagnostic and the exact final fixture delta.
Its first optional ForgeGradle metadata read encountered Windows MAX_PATH;
the retained helper correction uses an extended-length path. This was an audit
helper failure, not a consumer build failure or missing dependency. Initial
output/helper and corrected results are all retained. No scoped finding remains.
Two supplemental helper attempts also mistook the mapping ZIP's digest for a
TSRG file digest; those failures and the corrected actual-ZIP check are retained.
The review's 59-file manifest accompanies its report. The nested fixture-record
class delta was independently limited to debug line-number tables.

## Packaged observations

The native check used immutable host/consumer copies in a new disposable,
loopback-only, no-player server. Only 104 library files were reused and compared
byte-for-byte; no existing world, configuration, remote server or credentials
were used. Its original 31-file manifest and captured bytes are retained in
`native-runtime.zip`.

Both processes logged exactly one two-conflict rejection receipt and completed
normal provider registration. Actual environment readiness/expiry, a live Moon
datapack reload, two native station identities and same-world restart passed.
All fourteen probes were unloaded before and after the query. These are configured
values, not entity gravity simulation. No fault probe was activated in this
packaged server; the deliberate callback faults were exercised by GameTests.

`artifact-identities.json` identifies the exact host/API/sources and consumer
used for that run (consumer SHA-256 `4207b9b3c686b7230cceaef946062f3dce8318acf587bea2263d0a990509f735`).
After the diagnostic assertion correction, the independently rebuilt consumer is
`759f214daae269964ab78ed771a2b3cf799a480061df1144f6075fd911881ec8`.
`final-fixture-identity.json` records the complete ZIP-entry comparison: only
`AdapterFaultGameTests.class` and its nested `Fixture` record differ. Normal
runtime provider classes/resources are identical. The native test was not
repeated for this GameTest-only change, and is not labeled a native execution
of the final fixture hash. Final GameTest and consumer compilation evidence
cover the corrected source.

The first process retains one **2,553 ms / 51 tick** `Can't keep up` warning.
There were no ERROR/FATAL or client-linkage failures in the audited stdout.
This is finite startup/restart evidence, not performance or stability acceptance.

## Acceptance boundaries

No real clients/GPU, two-player session, forced crash/power-loss, arbitrary
third-party modpack, remote or long-duration workload was run. Full original
machine/dimension acceptance remains scheduled under ADR-018. Platform 47.4.23
is a configured compatibility lane, not a result established here.

Required G0-G9 remain incomplete and v1.3 remains `IN_PROGRESS`; no candidate,
release, tag or human Gate approval is assigned. `V130-ACC` is the remaining
v1.3 acceptance work, without starting the deferred full campaign. Production
host/API/source JAR bytes match the preceding SAT slice. Fixture-only changes
can be reverted without a world/schema migration.

The artifact identities, original native manifest, raw logs, review history and
independent verification are archived alongside this report. Outer metadata uses
canonical LF; archives preserve original captured bytes. `SHA256SUMS` covers all
evidence files except itself, including this report.
