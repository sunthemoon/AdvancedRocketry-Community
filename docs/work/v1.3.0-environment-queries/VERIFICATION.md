# V130-ENV — Read-only environment and body-context queries

## Scope, identity and design

Baseline `758c7a11e430168e0f70221baace561dd41906bb`, branch
`codex/v1.3.0-public-api`. Contract/planning: `be1453e`; production/API/consumer
fixtures/tests/documentation: `5b62291`; native runner/Python checks: `65bbd41`.
Root is the only tracked writer. Independent reviews/reruns are read-only with
Temp-only outputs. The user-supplied untracked documentation bundle is untouched.

[ADR-028](../../decisions/ADR-028-READ-ONLY-ENVIRONMENT-QUERIES.md) adds API 1.6,
preserving the nineteen previous exports and adding four environment types and
one nested enum. The normal mod and classifier contain identical exported bytes.
A FORGE-bus ready event supplies an owning-server-thread handle. Stopping expires
that handle and releases server-bearing references; a subsequent handle never
reactivates an old one. Values are detached immutable records.

Actual queries use one immutable celestial catalog and the committed station
registry's indexed lookup. Surface values come from definitions; a Space region
reports its own persisted gravity/vacuum and orbit-body/station identity. Missing,
uncommitted, ambiguous or unavailable context returns empty, not an Earth fallback.
Station atmosphere fields absent from its schema remain unspecified. No chunk
access, per-position cache, dirty-state write, save schema or packet is added.

These are configured base values, **not** entity gravity, room oxygen or permission.
The Level-based gravity controller is unchanged. The source is new project work;
no upstream code/art is imported. The existing approved Forge MDK build header and
license notices are retained.

## Commands actually executed

Windows, Java 17.0.7, Forge 47.4.10, Python 3.13.15. Full raw logs are in
`build-evidence.zip`; the independent consumer's real classpath/artifact reports
are included. All commands below exited 0 unless explicitly described otherwise.

| Command/check | Actual result |
|---|---|
| `gradlew.bat test --tests '*BodyContextResolverTest' --tests '*CelestialCatalogTest' --tests '*StationRegistrySavedDataTest' --console=plain` | Baseline passed, 18 s |
| `gradlew.bat test --tests '*Environment*Test' --tests '*Api*Test' --tests '*BodyContextResolverTest' --console=plain` | Initial focused checks passed, 37 s |
| `gradlew.bat clean build runData publishMavenJavaPublicationToLocalProjectRepositoryRepository --console=plain` | 756 JUnit / 141 suites, zero failures/errors/skips; DataGen written 0; 52 s |
| `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache --console=plain` | Independent API-classifier consumer passed, 10 tasks executed, 21 s |
| `gradlew.bat runGameTestServer --console=plain` | 188 Required tests passed, 141 s |
| `python -B -m unittest discover -s tests -p 'test_v130*.py' -v` | 71 tests passed |
| `python -B scripts/run_v130_environment_smoke.py <fresh-server> --host-jar <immutable-host> --fixture-jar <immutable-consumer> --evidence-dir <fresh-evidence> --java <java17> --accept-eula` | Two clean processes, 52.42 s total |
| `python -B scripts/validate_v1plus_planning.py` | 11 plans and 33-input inventory passed |
| `python -B scripts/validate_repository.py --require-approved-identity` | 45 checks passed |
| Markdown relative-link validation | 1,404 links checked, zero failures |
| `git diff --check`; `git diff --exit-code` after implementation commits | Passed; user bundle remains untracked |

The full JUnit XML was copied before independent targeted reruns; see
`junit-full.zip` and `junit-summary.json`. Added coverage consists of fifteen
environment unit methods, two classifier compile-boundary checks, three API-only
GameTests and six Python runner checks. Existing tests are retained. Active
v1.3 runner receipts now require API 1.6; historical evidence is unchanged.

No test/build failure occurred in this slice. New test source emits five existing-
platform deprecation warnings for ResourceLocation construction; they are retained
in the logs, not treated as release or runtime failures.
The independent archive-audit helper initially assumed an English javac message;
the consumer intentionally uses `-XDrawDiagnostics`. Its corrected check requires
the actual `compiler.err.doesnt.exist` code, internal package and exit 1. The initial
helper assertion/output is retained. Windows extended-path reads were used for a
long cache metadata path; no product source or test assertion was relaxed.

## Native dedicated observations

The server is a fresh disposable local world with the normal host and separately
compiled fixture JAR. Only the 104 prepared library files are reused, byte-verified
against the previous installation. No old world/config, remote host or players are
used. The observation command is opt-in and permission-level 2.

1. The actual API event supplies a usable handle. Far unloaded Earth/Moon queries
   return configured values; Space gaps and an absent dimension return empty.
2. Production `arce station admin create` creates two stations, orbiting Earth
   and Moon. API queries report distinct saved UUIDs and body IDs in the same
   Space Level, each with gravity 0 and vacuum true, with no invented atmosphere.
3. A real datapack reload changes Moon surface gravity from 0.165 to 0.4. The
   next API query reads 0.4 while the Moon-orbit station still reads its saved 0.
4. Clean save/stop invalidates the handle. The second process receives a new
   handle, reads the same two station UUIDs and unchanged native station data,
   and sees Moon gravity 0.4 from the saved enabled datapack. It also expires on stop.

All fourteen probes report unloaded before/after. This is immediate loaded-state
evidence; source review establishes the absence of chunk/ticket requests. It is
not a general proof about every mod or asynchronous ticket source.

Raw gzip station SavedData, level metadata, datapack files, command receipts,
configuration, stdout/debug/latest logs and the original 31-file checksum manifest
are preserved inside `native-runtime.zip`. The decoded station root remains schema
2 and each station schema 1, without pending reservations. `runtime-summary.json`
records the exact JAR identities, commands, statuses and probes.

There is one retained first-process warning: **2,231 ms / 44 ticks behind** during
the bounded create/reload sequence. Both processes have zero ERROR/FATAL/client
linkage failures; this is not a performance/soak PASS. No rerun hides the warning.

## Independent review and evidence boundaries

Independent contract and source reviews found no material blocker. Two contract
wording comments were incorporated: the ID bound applies to both IDs, and rejected
reload retention is scoped to the celestial candidate, not the entire datapack.

The independent reviewer reran 46 JUnit / 5 suites with `--rerun-tasks --offline
--no-daemon --no-build-cache` (46.604 s) and six Python environment checks, all
passing. 1,211 scoped source hashes and eight artifact locations remained unchanged.
Detailed commands, XML, source identity, raw native and classifier/consumer review
are archived in `independent-review.zip`; earlier reviews are in `review-history.zip`.
Independent artifact/native readback verifies all 31 manifest files, exact 24-class
API/runtime identity, 114 consumer dependency JARs, 21 fixture sources, 104 copied
libraries, unchanged raw station roots, fourteen probes and ready/reload/stop receipts.

No full integrated-server restart campaign, client/GPU or two-client validation,
crash/power-loss, remote test or load campaign was run. Accepted native reload is
tested; rejected celestial candidates, ambiguous/missing/blocked contexts,
create/delete visibility and dirty-state preservation are focused Java evidence,
not additional native rejection/uninstall scenarios. API 1.6 promises only the
configured view, not station-specific entity physics or local life support.

## Completion and remaining work

The environment implementation and bounded tests are delivered. The next v1.3
implementation item is `V130-SAT`, followed by remaining compatibility/documentation
and acceptance work. All original mechanical/dimensional integration remains
scheduled under ADR-018. **Not all v1.3 Required Gates G0–G9 are satisfied**;
version status remains `IN_PROGRESS`. No candidate, release, tag or human Gate
approval is assigned. Rollback removes the additive API/consumer use together;
this slice requires no world conversion.

`SHA256SUMS` covers the archived evidence files except itself. Artifact hashes
and sizes are in `artifact-identities.json`.
