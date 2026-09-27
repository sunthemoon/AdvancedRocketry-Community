# V140-MIG-01 — ordered discovery persistence

Date: 2026-09-27. Base `cbff561a8e949c25e93d51365a260e0fd061be4d` on
`codex/v1.4.0-planetary-expansion`. Scope is the research/discovery recovery
implementation, not complete v1.4 migration or release acceptance.

## Implementation

[ADR-038](../../decisions/ADR-038-ORDERED-PLANETARY-DISCOVERY-RECOVERY.md)
preserves both existing SavedData names, wrappers, root/record schemas and
research values. Only celestial and satellite data adopt bounded, forced,
read-back-checked atomic replacement. Ordinary DataStorage saves use the same
writer and retain dirty state on failure; explicit barriers report failure.

The coordinator acknowledges the paid mission receipt before publishing a
durable celestial candidate, then acknowledges mission completion. Unchanged
pending/claimed receipts and existing dirty visits still cross the barriers.
Absent targets and full/future progress wait without repeated disk writes.
Historical paid CLAIMED receipts can repair missing discovery without resetting
their phase or disturbing a newer active mission. A deduplicated lifecycle
queue handles newly pending work as well as startup recovery, at most eight
receipts per tick within the existing 8192-record bound.

Root readback additionally identified the launch-retry acknowledgment case:
after a failed save, the registry already contains the identity, so the next
launch is IDEMPOTENT. Mutation paths now also acknowledge dirty state, rather
than treating an unchanged result as proof that storage succeeded. Independent
review noted that player cancellation with no current mission still returns
MISSION_NOT_FOUND before this retry point. That failure is not successful save
acknowledgment; dirty state remains for ordinary autosave. Cancellation-by-identity
is not made idempotent, and the mission file and terminal chunk are not atomic.

## Verification scope

Twenty-four added unit cases cover real Temp file bytes around six cuts before/
after the three coordinator barriers, three same-process acknowledgment
failures, ordinary save failure, unsupported atomic move, staging reuse,
identity/path and expanded-size refusal, dirty visits, historical receipts,
newer missions, unavailable data, exact research totals and replay bounds.
Cut injection reloads actually written files; it is not a native process kill.

One added GameTest drives the production END-tick adapter and FakePlayer claim
against batch-owned SavedData. It checks startup repair, runtime removed/restored
catalog recovery without another claim, launch persistence failure/retry, disk
readback, publication notifications and ordinary DataStorage dispatch. Its
local manager is not installed into the global bridge; explicit catalogs and
deadline readiness do not prove real resource uninstallation, ServerStartedEvent
dispatch or real-client behavior. The paired fixture restores both original
stores even after a failed batch.

The original DISC implementation's source/codec/menu assertions and timeouts
are unchanged. No upstream code/assets, public API, protocol, generated resource
or persistent schema changes are included.

## Executed root checks

Java: `C:/Program Files/Java/jdk-17.0.7`. Python:
`D:/python/pyenv/pyenv-win/shims/python.bat -B`, with `PYTHONUTF8=1`.
All commands below exited 0; logs/XML distinguish earlier and final inputs.

| Command | Observed result |
|---|---|
| `gradlew.bat test --tests '*AtomicSavedDataTest' --tests '*DiscoveryClaimRecoveryTest' --tests '*DiscoveryReplayQueueTest' --console=plain` | Initial 23 added unit cases pass; 28s |
| `gradlew.bat clean build runData runGameTestServer --console=plain` (`clean-all-02`) | 921 JUnit / 167 suites, all 236 required GameTests; 3m11s; DataGen 0 writes |
| `gradlew.bat clean build runData --console=plain` (`clean-build-final-03`) | 922 JUnit / 167 suites; 67s; DataGen 0 writes; before dirty-launch correction |
| Same clean/build/DataGen command (`clean-build-final-04`) | Final 922 JUnit / 167 suites actually executed, no failures/errors/skips; 74s; DataGen 0 writes among 34 resources |
| `python -B -m unittest tests.test_v140_planetary_worlds_smoke tests.test_v140_celestial_schema_smoke` | 26 existing harness checks pass |
| `python -B scripts/validate_bootstrap_provenance.py --require-approved-review` | Historical accepted provenance remains digest-bound: 2 components, 11 imported targets, 2 local assets |
| `python -B scripts/manage_v030_generated_manifest.py verify` | Existing 7-file historical DataGen inventory unchanged |
| `python -B scripts/validate_v1plus_planning.py` | 11 plans and 33-input inventory pass; planning-only validation |
| `git diff --exit-code -- src/generated`; `git diff --check` | No generated-resource changes or whitespace errors |

The first combined run predates the newer-active-mission unit case, ordinary-save
assertion and launch-save-failure GameTest additions. Its full console is retained;
its passing world was not archived before the later clean. The final source is
covered by the last clean build and the independent complete GameTest rerun.
The initial GameTest startup has a 2074 ms / 41-tick lag warning. Expected
Precision migration fault-injection errors remain in the raw output.

## Independent review and reruns

Contract review identified the original cross-file write ordering and runtime
replay enrollment gaps. It clarified that unchanged pending/claimed receipts
still require acknowledgment, existing dirty progress must be saved, and old
CLAIMED receipts must not be demoted. Independent source review found no remaining
production blocker within that contract and requested the newer-current-mission
and ordinary DataStorage checks now included. Root's later launch-retry fix was
part of the final independently inspected input, not retroactively attributed
to the earlier review.

The final reviewer forced 71 JUnit cases in 10 suites (zero failures/errors/skips,
16/16 tasks executed, 54.004s), then DataGen and all 236 required GameTests
(153.632s). The GameTest world was absent before launch; no reviewer deletion or
clean was used. DataGen wrote zero files. All 1359 captured source/build/Python/
generated inputs and the generated diff remained unchanged, as did all three
frozen artifact hashes. Complete argv, exit, duration and before/after identities
are retained in the independent archive.

Commands: `gradlew.bat test --tests '*AtomicSavedDataTest' --tests
'*DiscoveryClaimRecoveryTest' --tests '*DiscoveryReplayQueueTest' --tests
'*SatelliteMissionSavedDataTest' --tests '*SatelliteMissionRegistryTest' --tests
'*CelestialSavedDataTest' --tests '*PlanetaryDiscoveryTest' --tests
'*WorldDataMigrationServiceTest' --tests '*PopulatedWorldDataPreservationTest'
--tests '*ManagedSavedDataLoaderMigrationTest' --rerun-tasks --offline --no-daemon
--no-build-cache --console=plain`; then `gradlew.bat runData runGameTestServer
--offline --no-daemon --no-build-cache --console=plain`.

The final GameTest log retains the first-world missing `server.properties`
message, intentional migration failures and the marked satellite launch-save
failure. A 2018 ms / 40-tick startup warning is also preserved. These are not
zero-error logs or reference-hardware performance measurements.

## Packaged restart

Executed command:

```text
python -B scripts/run_v140_planetary_worlds_smoke.py <fresh-server>
  --baseline-jar <pre-planet-map01-jar> --host-jar <frozen-final-main>
  --evidence-dir <native> --java <jdk17>/bin/java.exe
  --accept-eula --research-unlocks
```

Exit 0 in 125.222s. All three owned packaged JVMs (baseline, upgrade, restart)
exit 0; the allocated port 62525 has no listener after completion. The actual
baseline is the pre-planet **v1.4 development** MAP-01 JAR with SHA256
`9fc582f1b83599526627b065d72b9995fbb7a69e04065a135dce7aa8697b1acf`,
not a v1.3 artifact. The fresh baseline world and complete pre-upgrade backup
remain in the raw evidence. All phases use normal save/stop.

Before discovery, Mars arrival is refused without changing the 2000 mB rocket.
Three captured 200-tick missions unlock Mars, Venus and the gas giant. Native
mission/satellite schema-1 records bind the exact UUIDs, owners, definition,
120 research yield and 100 discovery fee. The two owner accounts retain balances
40 and 20, with lifetime earned/spent 240/200 and 120/100. Repeated claims before
and after restart return ALREADY_CLAIMED without changing the recorded missions,
satellites, accounts or progress. Both root stores remain schema 2.

One six-block rocket travels Earth→Mars→Venus→Earth, retaining 17 diamonds and
64 iron ingots. Fuel is 2000→1552→1019→504. Binding records grow 3→6 without
remapping and stations 1→3; both survive the final restart. The discovered gas
giant still has no surface Level. This is operator-command, empty-server
regression, not terminal interaction or connected-player/network evidence.

No native phase reports ERROR/FATAL, project warnings or client-linkage failures.
The runner counts 25/10/10 bracketed warnings; independent raw readback counts
26/11/11 when each process's initial unbracketed Log4j terminal-feature warning
is included. Raw diagnostics are retained. No startup `Can't keep up` warning
appears in these three native logs. This does not measure the reference-hardware
load budget or prove arbitrary interruption safety.

The separate independent raw audit verifies all 150 native manifest members,
the three artifact identities, persisted account/mission/progress equality,
station/binding counts, fuel and cargo, and one saved rocket per phase using
retained Anvil entity data. Its first helper run failed an incorrect assumption
about the cumulative report-array slice. The original helper/log are retained;
the corrected audit compares the recorded array boundaries and raw flight
receipts, without changing the runtime, evidence or rerunning the server.
This audit does not fully decode `level.dat`; it checks its identity/hash.

## Artifacts and evidence

| JAR | Bytes | SHA256 |
|---|---:|---|
| main | 2,624,223 | `576eaaf8a5e23df1b049fd5fa5029f1cd6d6ffcfd0cf50bd5980fc280c31d475` |
| API | 48,469 | `50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf` |
| sources | 1,193,936 | `afc83ef63e3054657831bfb6e170cbc4a6cbffe3029738ec3a6bb562474dc8db` |

API 1.7 is byte-identical to the previous DISC artifact. Flight protocol remains
8. Artifacts are development builds, not a release candidate or version tag.

- [Source identity](source-identity.json), [artifact identities](development-artifacts.json),
  [generated resources](generated-resources.json) and [checksums](SHA256SUMS.txt).
- [Root summaries](root-checks.json), [raw logs and XML](root-checks.zip) and
  [archive inventory](root-checks-files.json).
- [Contract, source and final independent review](independent-review.zip) and
  [archive inventory](independent-review-files.json). Execution report SHA256:
  `0670633b95ba6d1265b5c4eafec452d315af6e3dbe6149b69fb454e46f3fb6b3`.
  Native supplement SHA256:
  `832fc06bad100f00573920be897d14b5d053da9a5e42b25df275d6b8c6ea93af`.
- [Native restart evidence](native-restart.zip), [summary](native-summary.json)
  and [archive inventory](native-restart-files.json).
- [Changed Markdown file-target check](links.json); URL/anchor checks excluded.
- Root raw output:
  `C:/Users/Administrator/AppData/Local/Temp/arce-v140-mig-claims-7ff01118440047f59bae982f7891efc6/`.
- Independent contract:
  `C:/Users/Administrator/AppData/Local/Temp/arce-v140-mig-contract-review-c80a027a3fa143b98ad74e534deead67/`.
- Independent source:
  `C:/Users/Administrator/AppData/Local/Temp/arce-v140-mig-source-review-99f5dae5ba2140748ef51945b7fb17d7/`.
- Independent final checks:
  `C:/Users/Administrator/AppData/Local/Temp/arce-v140-mig-final-review-148b917d52a943339b2114c85f94e0e0/`.

## Limits and remaining work

Actual v1.3-world upgrade, coherent removed-pack startup/restoration and packaged
forced interruption remain MIG-02/03. Arbitrary machine power loss, real GPU,
two-client, remote reference hardware and sustained load are not certified.
Eight receipts per tick is a count bound, not a measured full-registry disk/MSPT
budget. Historical lost or corrupted award/fee data cannot be inferred from a
discovery alone. Use complete matched backups for recovery or downgrade.
The existing [real-client discovery cases](../v1.4.0-discovery/MANUAL-CASES.md)
remain NOT_RUN; this slice changes no client UI or wire schema.
All v1.4 Required Gates remain unsatisfied; version status is IN_PROGRESS.
