# V130-ATM-01: compiled state-based atmosphere boundaries

## Scope and decisions

Bounded development slice under [ADR-024](../../decisions/ADR-024-STATE-BASED-ATMOSPHERE-BOUNDARIES.md),
not a release or complete v1.3 acceptance. Baseline `f4a580f`, contract `e62346d`,
implementation `3ba450c`, native restart runner `1e1bd46`.

- API 1.2 adds four `api.atmosphere` types. The classifier contains exactly ten
  supported classes, byte-identical to the normal host; fixture compilation has
  only the classifier and platform dependencies, not host implementation classes.
- Owner-bound loading registration is thread-confined, non-reentrant, finite and
  atomic. Every claimed BlockState is compiled before world services are installed;
  runtime scans retain only immutable results, not provider callbacks.
- Legacy tag/door priority and loaded-only guards remain intact. Neighbor changes
  synchronously revoke cached and in-flight authority, including a changed block
  already replaced with air. Completed results cannot escape invalidation behind
  a dirty backlog. Server-wide reload invalidates caches and pending scans.
- No new persistent payload, schema, packet or upstream asset. Callback timing
  checks occur after return; this is not a sandbox or a way to interrupt hung mods.

## Actual commands and outcomes

All local Gradle commands used Java 17.0.7 and `--no-daemon`. Python used the
installed `D:/python/pyenv/pyenv-win/shims/python.bat`, `-B` and `PYTHONUTF8=1`.

| Command | Actual result |
|---|---|
| `gradlew.bat test --tests '*AtmosphereBoundaryRegistryTest' --tests '*Api*Test'` | Initial run failed: test-worker heap exhaustion in a 4,096-value test property. After fixing the fixture representation, exit 0 in 27s; [output](targeted-tests.txt) |
| `gradlew.bat clean build runData runGameTestServer publishMavenJavaPublicationToLocalProjectRepositoryRepository` | Build and all 688 JUnit / 133 suites passed; DataGen wrote 0. GameTest failed two new heightmap-sensitive fixtures; publication was not reached. [Output](initial-host-validation.txt) |
| `gradlew.bat build runGameTestServer publishMavenJavaPublicationToLocalProjectRepositoryRepository` | Build/JUnit passed; one independent-room fixture failed after changing its chunk tickets. [Output](ticket-only-validation.txt) |
| `gradlew.bat build publishMavenJavaPublicationToLocalProjectRepositoryRepository` | Exit 0, 18s; final source/fixture compilation and local Maven publication. [Output](publication.txt) |
| `gradlew.bat -p compat-test-mod clean build` | Exit 0, 23s; ten tasks executed, classifier/classpath/internal-import boundaries passed; JUnit is NO-SOURCE. [Output](consumer-validation.txt), [reports](consumer-reports/) |
| `gradlew.bat runGameTestServer` | Exit 0, 2m33s; **all 150 Required GameTests passed**. [Output](gametest-final.txt) |
| `python -B -m unittest discover -s tests -p 'test_v130_*smoke.py' -v` | Exit 0; **49 tests**, no failures. [Output](python-tests.txt) |
| `python -B scripts/run_v130_atmosphere_boundary_smoke.py <fresh-server> --host-jar <host> --fixture-jar <fixture> --evidence-dir <new-output> --java <java17> --accept-eula` | Exit 0; **four clean packaged JVMs**, each 22 observed ticks, zero log errors/fatals/client linkage failures. [Summary](runtime-summary.json), [driver output](packaged-server.txt) |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 passed, zero pending/warnings/failures before this evidence-document integration. [Output](repository-validation.txt) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0; 11 plans and 33-input inventory valid. [Output](planning-validation.txt) |
| `git diff --check`; `git diff --exit-code -- src/generated`; `git diff --exit-code` after implementation/runner commits | Exit 0; generated resources unchanged; only the user's unrelated documentation bundle was untracked before evidence integration |
| Final staged-evidence Markdown/content checks and planning validator | Exit 0; relative links, binary/source allowlists, case-collision check and the 11-plan inventory passed. [Links](final-links.txt), [contents](final-contents.txt), [planning](final-planning.txt) |

All 688 JUnit results, with zero failures/errors/skips, are retained in
[junit-full.zip](junit-full.zip). Initial failed fixtures and native logs are in
[failed-fixture-evidence.zip](failed-fixture-evidence.zip); they were not removed
or reclassified as passes.
Readable text/JSON copies normalize trailing whitespace and line endings for Git;
[build-evidence.zip](build-evidence.zip) preserves the original command outputs,
consumer reports and source-review diff. Runtime and independent-rerun archives
likewise retain original bytes. The initial staged whitespace check exposed these
tool-emitted spaces; no assertion or raw evidence was removed to suppress it.

The first unit fixture used a single 4,096-value property, whose Minecraft neighbor
table is quadratic. Twelve binary properties retain the same **4,096 states** and
budget assertions without that pathological allocation. The two initial world
fixtures now start after the normal cross-dimension heightmap update, matching
existing atmosphere tests. Independent review identified persistent forced-chunk
ownership/cleanup in the external fixture; it now uses its own expiring region
ticket. The subsequent run exposed an empty dimension's paused BlockEntity ticks;
resetting its empty-time counter enables only the existing 120-tick test window.
No product limit, assertion or test timeout was relaxed.

## Coverage and native observations

New JUnit coverage: 13 registry tests, two classifier consumer tests and one
completed-scan invalidation test. API-version assertions now include minor 1.2.
Five added GameTests cover rule priority/loaded guards, dirty-backlog cancellation,
reload revocation, one closed MOD-bus registration event, and a real independent
state-boundary room using the production vent ticker/service.

The independent fixture keeps full collision in both `open` states, so a permeable
open room demonstrates the new rule rather than ordinary collision fallback. The
first packaged process checks closed/open/reclosed/replaced walls and **two actual
`/reload` operations**: a sealing tag overrides the open rule, then removing its
entry restores permeability. Event counts are checked both at startup and after
clean shutdown. This is stronger than the host GameTest's direct event-handler call.

All four processes use the same world, same normal host/fixture JARs and normal
Forge 47.4.10 loading. The fixture remains installed in every process; skipping
its provider is **not** mod uninstallation. Native operator commands prepare
the single room and resources, not player canister-loading gameplay.

| Process | Seconds | Boundary state / vent | Saved energy / oxygen / phase |
|---|---:|---|---|
| setup + two reloads | 40.74 | open / inactive after tag removal | 38,600 / 2,997 / 0 |
| unchanged restart | 20.95 | open / inactive | 38,600 / 2,997 / 0 |
| provider skipped | 20.57 | open / active via legacy full-cube fallback | 37,920 / 2,996 / 14 |
| provider restored | 25.88 | open / inactive | 37,920 / 2,996 / 0 |

The final phase reset is existing inactive-vent behavior, not lost stored oxygen.
These observations prove bounded positive consumption when the fallback supplies
the room, and preservation of stored resources across inactive restarts; they do
not claim exact per-tick accounting throughout asynchronous command handling.

[Raw runtime evidence](runtime-evidence.zip) includes commands, stdout/native logs,
status replies, configuration, launch arguments, actual Anvil/level.dat copies,
per-process results and SHA256SUMS. [Decoded state observations](runtime-disk-states.json)
remain separate from those raw files. The 104 copied Forge library files were
verified against the local seed, recorded in [preparation](libraries-preparation.json).
Input JARs are retained locally in the immutable artifact directory recorded by
[artifact identities](artifact-identities.json), not checked into the repository.

## Independent review

Contract and source review occurred in a separate read-only delegated session;
root was the only tracked-source writer. [Contract review](independent-review/contract/REVIEW.md)
requested synchronous scan revocation, reentrancy rejection, exclusion of dynamic
tag queries and explicit physical-side loading. [Source review](independent-review/source/REVIEW.md)
identified the fixture ticket issue above; it was corrected before final validation.

The reviewer independently reran the four applicable JUnit classes with
`--rerun-tasks --offline --no-daemon --no-build-cache`: **37 tests passed**, all
14 Gradle tasks executed. The five new Python tests passed separately. Source
bytes and all four artifact hashes were unchanged, and direct archive inspection
confirmed the ten API classes and their runtime byte identity.

Independent native readback also verified all 55 runtime-manifest files, parsed
four raw chunk/level.dat captures and checked 150 room/shell positions per phase.
It confirmed the table above, both ordered real-reload transitions, one complete
registration/skip event per process, unchanged world seed, no restart setup/top-up,
zero raw error/fatal/linkage findings, and the closed local port after shutdown.
The actual reviewer commands, XML, source/artifact snapshots and raw-NBT helper
are retained in [independent-rerun.zip](independent-rerun.zip); see the
[final review](independent-review/final/REVIEW.md).

## Remaining scope and risks

`V130-ATM-02` equipment/oxygen providers and the remaining v1.3 component,
environment, satellite and compatibility work are not implemented by this slice.
Arbitrary world/BlockEntity-aware callbacks are outside this API. Integrations
must notify ordinary neighbors when their state/block changes; silent mutations
are unsupported. Same-JVM mod purity and non-returning callbacks cannot be enforced
as a sandbox by this API.

There is no new remote, real-GPU, two-real-client, crash/power-loss or long-duration
acceptance. Full original-machine/dimension testing stays deferred under ADR-018.
No version label, release, inherited Gate approval or acceptance-cursor change was
made. **The complete v1.3 Required G0-G9 Gates are not satisfied.** Continue with
`V130-ATM-02` under the existing [implementation plan](../v1.3.0-implementation-log.md).
