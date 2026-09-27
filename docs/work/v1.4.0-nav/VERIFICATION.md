# V140-NAV — console star map and authoritative navigation

Date: 2026-09-27. Development slice, not a release candidate or Gate approval.
Base: `bd8b9a827f3cf961ee7d658f54ac2b6547716fcc`, branch
`codex/v1.4.0-planetary-expansion`, Java 17.0.7 / Forge 47.4.10.
Root is the sole tracked writer; independent review writes only separate Temp
reports and receives an exclusive Gradle rerun period. The user documentation
bundle is excluded from reads, edits and staging.

## Implemented scope and compatibility

[ADR-035](../../decisions/ADR-035-ROCKET-STAR-MAP-AND-NAVIGATION-SYNC.md)
records the recommended design under standing maintainer authorization, not a
new numbered manual vote or release approval. The same flight container now
offers a code-drawn body/parent diagram, bounded pan/zoom, node selection,
previous/next focus, environment metadata and accessible orbit-station choices.
Selection returns to the console; only its explicit Launch button requests a
flight. No new route, permission, discovery unlock or surface for gas bodies is
created. The compact console, boarding/leaving and active-plan cancellation remain.

Server quote batches capture one paired catalog generation and reuse the actual
launch physical/route/fuel planner. Explicit status codes distinguish current
target, route/components/thrust/fuel/capacity/state/access problems. Stations use
the existing VISIT policy, expose stable IDs/names/orbit bodies, and refresh at
20-tick intervals. Changed immutable flight data refreshes immediately. Selected
stations follow IDs rather than list positions and are cleared after revocation;
cancellation still uses the synchronized active plan.

Flight protocol changes **6 to 7**, with exact client/server matching. Frame
bounds are **19..31,419 bytes**, at most **160 quotes / 32 stations**, strict flags,
status IDs, canonical counts/IDs and bounded names. Typed station UUID validation
is retained. API **1.7**, existing save schemas and celestial display channel/schema
**2** do not change. The opening payload's entity ID remains available even if
the corresponding client entity has not been resolved yet.

Menu recovery reuses the existing full celestial packet on an additional S2C
registration and vanilla container-button intent 0. Actual menu/viewer, thread,
same-Level live loaded rocket and distance are checked. Changed-only quote sends
do not suppress pending full-catalog retries. The per-player 100-tick full-send
budget persists across menu reopenings, caps tracked players at 128, prunes and
clears on logout/shutdown; encoding caches only one generation. Client launch
requires coherent valid catalog/quote generations, retries missing data and
rejects older catalog generations. Cancellation is not gated on this cache.

The original schematic rebuilds layout only when display data changes, caches
parent lookup, clips draw/hit regions, clamps viewport values and bounds layout
to 128 nodes. No upstream artwork or implementation is imported. Only the two
current-version language resources change; older generated roots stay untouched.
The existing 550-line flight service was inspected: navigation computation and
full-snapshot budget/cache are extracted, while it retains launch orchestration
and the shared planner. No new class approaches the 800-line ADR threshold.

## Automated coverage

Fourteen added JUnit cases cover explicit status IDs/precedence, quote validity,
generation coherence/stale retention, DTO bounds, a maximum-size Unicode frame,
malformed/truncated/duplicate inputs, stable station selection, active cancellation,
100/128-node deterministic layout, cycle/missing-parent rejection, finite viewport
limits, container input-dispatch order and full-sync retry/cross-menu/lifecycle
budgets. Existing protocol assertions now require 7 without changing save-schema
assertions.

Three new GameTests exercise actual services/menus: owner/nonowner statuses and
private station filtering; captured generations and closed arrivals; synchronous
no-chunk-load/no-resource-write quotes; stale launch refusal; immediate changed-fuel
refresh and periodic station revocation; invalid viewer/button/distance/closed
menu; reopen cooldown, latest catalog recovery and logout reset. An existing
source-station access fixture also asserts the UNAUTHORIZED navigation reason.

These tests use network-free FakePlayers. Candidate changes use private
`applyCandidate`, not native `/reload`; full-catalog recovery invokes the service
after 101 ticks, not actual packet delivery through `broadcastChanges`. The pending
retry path combines source inspection with pure schedule/limiter tests. Synchronous
chunk counts and journal/station/fuel equality are scoped observations, not an
unload campaign or whole-world item-conservation proof. Cleanup is scoped to
owned fixtures; unexpected successful negative-case launches are not claimed to
leave a pristine failure world.

## Retained failures and review corrections

- Initial compile attempts exposed a package-private station-name validator,
  the mapped Font trim return type, Netty's fluent return type, and fixture
  catalog/logout method signatures. Call sites now use the existing public
  summary validation, plain text trim, separate buffer calls, captured generation
  and actual logout event. Compiler logs are retained.
- The first full JUnit run had **1 failure of 866**: the maximum-frame fixture
  constructed non-RFC station UUIDs. It now uses deterministic RFC-4122 IDs;
  production typed-target validation was not relaxed. The original fixture and
  failed XML are retained; the navigation DTO also rejects invalid station IDs.
- Independent source review found P2 click dispatch: the pinned container screen
  consumes even blank clicks. The map now receives bounded viewport clicks first,
  falling through for toolbar/console handling. A finite dispatch-order regression
  covers an always-consuming container. Original evidence and correction remain.
- A later clean-world run failed **1 of 232 GameTests**, inherited sunlight roof
  coverage: fixed two-tick timing did not observe asynchronous sky-light updates.
  The fixture now polls the loaded column within the original **40-tick timeout**,
  stops waiting at tick 30, and additionally checks the actual opaque block and
  sky state. Night/far-unloaded assertions and cleanup remain. No environment
  runtime rule, timeout or production performance budget changes. The failed
  clean-run log and original fixture remain archived rather than relabeled PASS.

## Root commands and observed results

With `JAVA_HOME=C:/Program Files/Java/jdk-17.0.7`:

```text
gradlew.bat test --tests '*RocketNavigationTest' --tests '*RocketFlightPlanPacketTest' --tests '*RocketFlightSelectionTest' --tests '*CelestialSnapshotTest' --console=plain
gradlew.bat test --tests '*RocketNavigationTest' --tests '*RocketCatalogRefreshLimiterTest' --tests '*StarMapLayoutTest' --tests '*RocketFlightPlanPacketTest' --tests '*RocketFlightSelectionTest' --tests '*CelestialSnapshotTest' --offline --console=plain
gradlew.bat test runData runGameTestServer --offline --no-daemon --console=plain
gradlew.bat build runData runGameTestServer --offline --no-daemon --console=plain
gradlew.bat clean build runData runGameTestServer --offline --no-daemon --console=plain
```

The first four attempts ended during compilation, exit **1**. The first clean
run then failed the UUID fixture in **866 JUnit / 155 suites**, exit **1**.
The subsequent build/DataGen/GameTest command exited **0** in **3m 1s** with
**866 JUnit** and **232 required GameTests**. DataGen wrote two language resources,
so packaging was rebuilt rather than freezing those earlier JARs. The next clean
run passed build/JUnit/DataGen but failed the inherited sunlight GameTest, exit
**1**, **3m 11s**. The failed combined command is not reported as a pass.

After the scoped fixture correction, the final **clean/build/DataGen/GameTest**
command exited **0 in 3m 22s**: **866 JUnit / 155 suites**, zero failures/errors/
skips, **232 required GameTests passed**. JUnit executed rather than being
restored from test cache; some compile tasks reused their unchanged cache.
DataGen wrote **0 of 27 resources**. The corrected sunlight fixture observed its
specific column at tick **2**, skylight **14**, on this run; that observation does
not mean the entire lighting queue completed. Its inherited night check does not
separately wait for restored open-column lighting and is not an isolated nocturnal
lighting oracle.

```text
python -B -m unittest tests.test_v140_celestial_schema_smoke tests.test_v140_planetary_worlds_smoke -v
python -B scripts/validate_v1plus_planning.py
```

Using `D:/python/pyenv/pyenv-win/shims/python.bat` with `PYTHONUTF8=1`, the unchanged
native-harness checks passed **24 tests in 0.371s**, exit **0**. Planning validation
exited **0** for eleven version plans; this is not a runtime or release Gate.

## Packaged server and restart

```text
python -B scripts/run_v140_planetary_worlds_smoke.py <fresh-server-native>
  --baseline-jar <MAP01-artifacts>/advancedrocketry-community-1.20.1-1.4.0-dev.jar
  --host-jar <NAV-artifacts>/advancedrocketry-community-1.20.1-1.4.0-dev.jar
  --evidence-dir <native> --java <Java17>/bin/java.exe --accept-eula
```

The unchanged runner and three native JVMs exited **0**. A fresh preceding
MAP-01 world is created, upgraded to the frozen NAV artifact, flown Earth/Mars/
Venus/Earth and restarted. Native logical rocket/cargo/fuel journal, station and
binding identities survive; three fuel debits are **448 / 533 / 515**, ending at
**504 / 2000**. The native logs contain **25 / 10 / 11 warnings**, zero ERROR/FATAL,
zero project warnings and zero client-linkage failures. Original observations and
logs are retained, not filtered into a warning-free claim.
The extra restart warning reports a **2189 ms / 43 tick** startup delay while
short local checks ran concurrently; this smoke supplies no performance acceptance.

This is a local offline operator-driven short regression, not a player login,
navigation GUI, actual menu packet/recovery session, new native reload case, S2
crash-ordering test or whole-world/block-palette immutability proof. No remote
host or user world is used. The preceding baseline JAR SHA256 is
`9fc582f1b83599526627b065d72b9995fbb7a69e04065a135dce7aa8697b1acf`.

Frozen NAV artifacts:

| Artifact | Bytes | SHA256 |
|---|---:|---|
| Main | 2546008 | `66d365ccfe8434b3e2f461b4a3f3b9663e71bd6c1571a58df44c2217089293b8` |
| API | 48469 | `50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf` |
| Sources | 1158573 | `f0ba9b84775822a095ff421d57ddcd87a95a383062a55b141b7121b13f9bb1dc` |

The API JAR remains byte-identical to the ENV baseline. Main and sources include
the navigation slice and reviewed test-fixture correction, not a release approval.

## Independent reruns

The separate reviewer independently executed, with the exclusive Gradle slot:

```text
gradlew.bat test --tests '*RocketNavigationTest' --tests '*RocketFlightPlanPacketTest' --tests '*RocketFlightQuotesTest' --tests '*RocketFlightSelectionTest' --tests '*CelestialSnapshotTest' --tests '*StarMapLayoutTest' --tests '*RocketCatalogRefreshLimiterTest' --tests '*RocketMaintenanceStructureTest' --tests '*RocketTargetFlightPlannerTest' --rerun-tasks --offline --no-daemon --no-build-cache --console=plain
gradlew.bat runData runGameTestServer --offline --no-daemon --no-build-cache --console=plain
```

Forced focused JUnit passed **47 tests / 9 suites**, zero failures/errors/skips,
exit **0**, **49.99 s**; sixteen tasks executed rather than relying on cached test
results. DataGen plus reused-world GameTests exited **0 in 136.35 s**, wrote **0**
resources and passed **232 required tests**, including all three new navigation
batches. The sunlight fixture observed tick **1**, skylight **14**. A **2164 ms /
43 tick** integration lag warning is retained; this is not performance acceptance.
The reviewer compared **1180 source/generated/build inputs** and all three JARs:
unchanged before/after, with JARs matching the frozen root artifacts.

Contract, initial source, incremental GameTest/DTO/rendering and lighting reviews
are retained separately, including original findings rather than a replacement
clean verdict. Source review plus forced tests is not a rendered-client observation.
Final report SHA256:
`cd4c072e66b9628143784caaa472bbc12cc277aa99cd1c0c567b339daa5ab4a1`.

Independent readback verified **147 native manifest members** and separately
decoded captured raw entity Anvil, station and transfer data. Cargo remains
**17 diamonds / 64 iron ingots** in each saved phase, three committed debits and
final **504 fuel** agree, station counts are **1/3/3**, binding counts **3/6/6**,
and the preexisting identities remain. The final audit used separate assertions
over existing bounded low-level parsers. It does not independently recompute all
snapshot/transfer checksums, decode whole level.dat, inspect every block palette
or certify whole-world item immutability.

The reviewer's first native helper incorrectly expected the post-baseline world
marker inside the earlier baseline capture. That helper failure remains retained;
the corrected helper binds the actual baseline level.dat to its subsequent marker
receipt, without altering raw evidence or weakening runtime assertions. This
tooling correction is not a native server failure or a fabricated passing rerun.
The initial root archive helper also had a local ZIP-member-name shadowing bug;
it was corrected before final per-member/staged-byte validation. Runtime logs,
test results and artifact bytes were not edited to fix archive assembly.

## Evidence index and repository checks

- [Root results](root-checks.json), [raw root bundle](root-checks.zip) and
  [member hashes](root-checks-files.json): original compile/JUnit/GameTest failures,
  passing commands, XML, fixtures and logs.
- [Independent bundle](independent-review.zip) and
  [member hashes](independent-review-files.json): all five original review stages,
  forced reruns, unchanged-input/artifact comparisons and raw native audit.
- [Native summary](native-summary.json), [raw restart bundle](native-restart.zip)
  and [member hashes](native-restart-files.json): launch receipts, saved-state
  captures, raw logs and baseline identity.
- [Artifacts](development-artifacts.json) and [27 generated resources](generated-resources.json):
  exact packaged-byte checks, not only source JSON validation.
- [Changed staged inputs](source-identity.json), [local links](links.json) and
  [complete evidence hashes](SHA256SUMS.txt): baseline, Git blobs, normalized staged
  bytes and raw executed-worktree identities. ZIP CRC and every member digest are
  checked; no evidence file hashes itself.

`git diff --check`, `git diff --cached --check` and the staged-worktree checkpoint
`git diff --exit-code` exited **0**. The excluded untracked user bundle remains;
an empty tracked diff does not claim that bundle was removed. No original ENV
evidence or historical Gate record was rewritten.

## Acceptance boundaries

The implemented navigation scope is distinct from V1/V2 acceptance. No real GPU
client, real multiplayer packet session, S2 forced recovery, long-load campaign,
new discovery policy or full historical-world migration is claimed. Explicit
client cases are in [MANUAL-CASES](MANUAL-CASES.md). Follow
[the player/data guide](../../CELESTIAL-DATA-GUIDE.md) for usage rather than these
implementation notes.

v1.4 remains **IN_PROGRESS**, all-version G0-G9 remain open, and the historical
v1.0 acceptance cursor is unchanged. No tag, push, publication or manual approval
is created. Remaining current-version work is sky presentation, discovery/research
and migration/acceptance; the full campaign still waits for original mechanical
and dimensional implementation under ADR-018. Rollback requires matched client
and server builds; this slice adds no persistent navigation data to migrate.
