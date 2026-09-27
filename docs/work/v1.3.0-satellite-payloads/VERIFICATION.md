# V130-SAT — Declarative satellite payload missions

## Scope and identity

Baseline `f29a4f88f80fdd59784c65842c0e7733df5f006c`, branch
`codex/v1.3.0-public-api`. Contract/planning: `387cf5c`; production/API/consumer
fixtures/tests: `1216d58`; native runner/Python tests: `24cd0d2`. The independent Rolling fixture correction is
`ff539a2`. Root is the sole tracked writer; independent reviews/reruns are
read-only with Temp-only outputs. The user's untracked documentation bundle is
outside the read/write scope. No upstream code or art is imported; existing
license/provenance notices remain intact.

[ADR-029](../../decisions/ADR-029-SATELLITE-PAYLOAD-MISSIONS.md) adds three
declarative API types, bringing API 1.7 to 27 exported classes. Registration is
loading-thread/owner/window bound and frozen atomically. It grants no arbitrary
mission/reward callbacks. Registered defaults merge with explicit datapack
overrides; invalid reloads retain the previous valid satellite catalog. Existing
missions retain their captured duration, research yield and discovery cost.

Actual terminal assembly, launch and claim reuse production authority and
transactions. A shared bounded target dictionary fits Forge's menu framing limit;
catalog generation changes expire old menus. A menu also requires the exact live
BlockEntity, and a cached removed inventory cannot extract again. Missing native
items are checked before ItemStack decoding; bounded quarantined roots survive
eligible terminal drops and exact placement. Over-budget roots refuse ordinary
survival removal. These are not guarantees for wrong-tool/no-drop/explosion or
destructive operator actions, nor for native serialization of arbitrary corrupt
data. Save schemas remain unchanged. The pre-existing NBT bound walker is shared
with Fuel Loader without changing its budgets.

The terminal remains 657 lines after extracting inventory and target transport
helpers; the 549-line manager still owns satellite service operations. Neither
crosses the 800-line ADR threshold. No client-only dependency is introduced in
common code, and no third-party code sandbox is promised.

## Executed commands

Windows, Java 17.0.7, Forge 47.4.10, Python 3.13.15. Raw output is archived in
`build-evidence.zip`. Commands below exited 0 except the explicitly retained
initial GameTest and native-runner failures.

| Command/check | Actual result |
|---|---|
| `gradlew.bat test --tests '*Satellite*' --tests '*Mission*' --console=plain` | Baseline passed, 18 s |
| `gradlew.bat compileJava compileSatelliteApiJava --console=plain` | Passed, 20 s |
| `gradlew.bat test --tests '*Satellite*' --tests '*Api*Test' --tests '*Fuel*Test' compileAdapterTestJava --console=plain` | Passed, 32 s |
| `gradlew.bat clean build runData publishMavenJavaPublicationToLocalProjectRepositoryRepository --console=plain` | 769 JUnit / 143 suites, zero failures/errors/skips; DataGen written 0; 58 s |
| `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache --console=plain` | Final classifier-only consumer passed, 23 s, all ten tasks executed |
| `gradlew.bat runGameTestServer --console=plain` | Final 199 Required passed, 121 s |
| `python -B -m unittest discover -s tests -p 'test_v130*.py' -v` | Final 77 passed |
| `python -B scripts/run_v130_satellite_payload_smoke.py <fresh-server> --host-jar <immutable-host> --fixture-jar <immutable-consumer> --evidence-dir <fresh-evidence> --java <java17> --accept-eula` | Corrected run: three exits 0, 91.32 s total |
| `python -B scripts/validate_repository.py --require-approved-identity` | 45 checks passed |
| `python -B scripts/validate_v1plus_planning.py` | 11 plans and 33-input inventory passed |
| Markdown relative-link validation | 1,412 links checked, no missing targets |
| `git diff --exit-code -- src/generated` | Passed, generated resources unchanged |

The full JUnit XML was copied before independent targeted reruns and is preserved
in `junit-full.zip`, with counts in `junit-summary.json`. New coverage comprises
thirteen JUnit methods (registration, menu framing, catalog, persistence and API
compile boundaries), eleven GameTests and six Python runner tests. Existing
assertions remain. Active v1.3 runner receipts require API 1.7; historical evidence
is not rewritten. Compiler/platform deprecation warnings remain in the logs.

## Failures found and corrected

- The first GameTest run had 197 tests and two failures. The new research observer
  reattached listeners, missing unchanged DataSlots; retaining one listener fixes
  observation without weakening exact reward/replay assertions. The existing
  Rolling future-process test also failed formation. Its fixture, like ten sibling
  Rolling tests, wrote outside the one-block `empty` template. All eleven now use
  the existing 16-cube `rocket_test`, with assertions/timeouts unchanged. This
  establishes an actual reservation defect, not proof of a specific neighboring
  collision. Both first output and the subsequent 199-test pass are retained.
- Source review found an inherited stale-menu replacement duplication risk and
  a new boundary discrepancy where the inventory `Slot` field consumed the native
  item's 4 KiB allowance. Exact-live-BlockEntity checks, removed-handler rejection
  and distinct envelope accounting have actual GameTest regressions. Contract
  review also prompted shared target encoding to stay within Forge's 32,600-byte
  opening payload limit and explicit quarantine/native-callback boundaries.
- The first native attempt saved and exited cleanly, then the runner rejected
  the mission snapshot. Raw NBT showed `discovery_required=1`: a fresh no-player
  world had not discovered Earth. The script incorrectly expected zero discovery
  charge. Its exact corrected oracle requires first yield 137, spend 11, net 126;
  the later task yields 211, cumulative earned 348, spent 11 and balance 337.
  Native game state was not modified to satisfy the script. The GameTest now
  captures the authoritative discovery DataSlot before launch, eliminating
  dependence on other test batches while asserting the exact captured net reward.
  All 199 GameTests and the independent consumer were rebuilt afterwards.

`native-initial.zip` retains the failed attempt's original raw files and checksum
manifest. A separate supplemental decode explains the failure; it is not a
three-process PASS. Host/API/sources bytes stayed unchanged; the final consumer
JAR differs only in the corrected GameTest and nested observer classes. Initial and final identities
are retained. No game timeout, performance budget or production reward was changed.

## Native dedicated observations

The corrected run used a new disposable loopback, no-player world and immutable
host/consumer copies. Only 104 prepared library files were reused, byte-verified
against their source. No old world/configuration or remote host was used. The
network-free fixture actor submitted production menu intents; the existing
explicitly enabled operator claim hook was used only while the consumer was absent.

1. Registered amethyst payload, chassis, panel and blank chip manufacture one
   package/control identity for 1,000 FE. Actual launch consumes the package,
   leaves one queued shard and 9,000 FE, and captures a 400-tick, 137-yield task.
   A real datapack reload installs a 20-tick, 211-yield override while the old
   mission remains active with its original snapshot. The process saves/exits 0.
2. The copied consumer JAR and generated override definition are removed. The
   restarted host completes the persisted task without its registration, claims
   137 less the captured 11 discovery cost, and rejects replay without changing
   the 126 balance. The task/chip/terminal identity remains intact; exit 0.
3. Matching consumer reinstall and the override permit another launch using the
   same identity. The new 20-tick task grants 211 without a second discovery charge;
   replay is rejected. Both missions remain claimed, balance 337, lifetime earned
   348, lifetime spent 11, and the same inventory/energy survive save; exit 0.

Raw gzip mission SavedData, native terminal Anvil chunk, level metadata, datapack,
configuration, status, commands and stdout/debug/latest logs are in
`native-runtime.zip`, together with the original 48-file checksum manifest.
`runtime-summary.json` records the three processes and exact artifact identities.
The first failed attempt's 17-file manifest remains separate in `native-initial.zip`.
Two Forge ERROR lines during actual uninstall concern the known missing fixture
block mappings; they are explicitly classified, not suppressed. No other ERROR,
FATAL, client linkage or `Can't keep up` warning was found. This observation is
not a performance or stability Gate.

## Independent verification

The reviewer reran eight focused suites with `--rerun-tasks --offline --no-daemon
--no-build-cache`: 56 JUnit, zero failures/errors/skips, 48.385 s. All sixteen
Gradle tasks executed. Six final Python satellite runner tests also passed.
Detailed commands, reports, source identities, JUnit XML and raw audit results
are retained in `independent-review.zip`; prior contract and source review,
including resolved findings, are in `review-history.zip`.

Independent artifact checks establish the exact 27-class classifier/runtime
identity, internal-import rejection for the expected javac diagnostic, 114 real
consumer dependency JARs and all 23 fixture source hashes. Raw native readback
confirms the 48-file manifest, identities, captured task fields, exact balances,
unchanged terminal resources and three clean exits. The reviewer's first terrain
decode helper used a celestial-only 4,096-tag cap; its retained correction uses
a bounded 65,536-tag Anvil read with byte limits. This is an audit helper change,
not a production budget or acceptance assertion change. A separate library hash
helper also replaced its capture-file 32 MiB ceiling with streaming SHA-256 for
the 47,791,053-byte bundled server JAR; its initial output is retained. All 104
source/destination library hashes match. No production or focused-JUnit source
changed during independent reruns; later fixture/runner changes are separately
identified and verified in the final report. No unresolved scoped finding remains.
The final report corrects one diagnostic label: terminal replay returns result 9
(`MISSION_NOT_FOUND`) after its binding is cleared, while explicit mission-ID
replay returns `ALREADY_CLAIMED` (12). The earlier report is preserved; no test or
raw evidence changed for this wording correction.

## Acceptance boundaries

This slice supplies a declarative payload and research mission extension, not
general satellite behavior or future-version gameplay. Real clients/GPU,
two-client multiplayer, forced crash/power-loss, remote and long-duration load
testing were not run. Full original-machine/dimension integration remains
scheduled under ADR-018. Missing *registration* and unknown-item quarantine have
unit/GameTest coverage; the native uninstall uses a vanilla amethyst payload,
not an arbitrary third-party missing item.

The next v1.3 implementation item is `V130-COMPAT`, supported-use documentation,
compatibility inventory and remaining bounded fixture coverage. **Not all v1.3
Required Gates G0–G9 are satisfied**; status stays `IN_PROGRESS`. No candidate,
release, tag or human Gate approval is assigned. Removing this additive API
requires removing dependent consumers together; save schemas need no conversion,
but bounded menu framing requires matching client/server builds.

Artifact hashes and sizes are in `artifact-identities.json`. `SHA256SUMS` covers
the evidence files except itself; archived native manifests cover the original
captured bytes. Final whitespace/clean-diff checks and staged-byte manifest
verification accompany the documentation commit. The user bundle stays untracked.
The first staged-byte check caught CRLF-to-LF normalization in derived outer JSON;
those metadata copies now use canonical LF before hashing. Original captured
bytes inside archives remain unchanged; the initial packaging failure is recorded
in `build-evidence.zip`.
