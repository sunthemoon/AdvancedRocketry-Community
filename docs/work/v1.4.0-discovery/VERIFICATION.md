# V140-DISC — shared planetary discovery

Date: 2026-09-27. Scoped development criteria verified; version IN_PROGRESS,
not a release candidate or Gate approval.
Base: `e810fb390dd30dbaa4506a59bffee00b1c78083b`, branch
`codex/v1.4.0-planetary-expansion`. Java 17.0.7 / Forge 47.4.10.
Root is the sole tracked writer. Independent review uses read-only source and
fresh Temp output; Gradle/server execution is serialized. The separate untracked
documentation bundle is excluded from reads, edits and staging.

## Implemented behavior

[ADR-037](../../decisions/ADR-037-PLANETARY-DISCOVERY-AND-RESEARCH.md) records the
recommended policy under standing maintainer authorization. Schema-2 bodies can
opt into `discovery_required`; absent fields, legacy definitions and previous
Java constructors retain unrestricted arrival. Built-in Mars/Venus/gas giant
opt in. Existing personal satellite research and world-shared celestial records
provide the progression; no second currency or persistent schema is introduced.

The server's quote/launch planner checks physical admission, identical source,
destination discovery, then ordinary route/components/fuel. Station destinations
resolve their committed orbit body and still require membership. Discovery does
not gate departure, rewrite prepared journals, create a gas surface, load chunks
or transfer research. The flight channel increments from 7 to 8 for explicit
status 12; packet layout/limits, celestial channel 2 and API 1.7 are unchanged.

The current generated data satellite retains its old three targets, 200-tick
duration, 120 yield and 100 captured discovery fee, and adds the three planets.
Historical generated content remains unmodified. Exact-path exclusions select
one current definition in main and sources artifacts. All added code/data/text
is original repository work; no upstream code, official art or audio is copied.

## Coverage and retained corrections

- Six new JUnit cases cover strict JSON/NBT flags, legacy/default behavior,
  lossy export rejection, retained visits/removal, full/future stores, shared
  progress with private captured research fees, persistence/repeated claims and
  exact generated mission data. One added packet case round-trips status 12.
  Existing protocol assertions now require exactly 8, not a wider range.
- One added 280-tick GameTest uses two network-free actors and two owned rockets
  to check locked player/operator surface/station intents, unchanged fuel/journal/
  stations/progress/chunk counts, reload/stale quotes, undiscovered-source
  evacuation quotes, private claim permission/balances, periodic open-menu
  refresh and saturated-store pending retries. This is not two-client evidence.
- The original planetary/navigation tests use batch-owned temporary discovery
  preconditions and restore their original SavedData objects, including after a
  failed batch. Their travel, pad, fuel and permission assertions/timeouts remain.
- The native harness adds an explicit `--research-unlocks` mode; its old mode is
  retained. Python mutant tests check research totals, stable identities,
  definition IDs, record schemas and the captured duration.

Retained failures, not relabeled as successes:

1. `clean-build-01`: a new GameTest treated the void intent adapter as returning
   a result; corrected to check the actual unchanged flight state. The operator
   service result and logged player refusal remain independently inspected.
2. `clean-build-02`: 898 tests, one failure from an old protocol-7 structural
   assertion; updated to exact protocol 8 under ADR-037, keeping save assertions.
3. `clean-all-03`: all 898 JUnit passed; 3 of 235 GameTests lacked their new
   discovery precondition (physical admission and changed-pad batches). Added
   paired fixtures, without changing existing scenario assertions or budgets.
   The full failed world and logs are preserved in Temp for evidence archival.
4. Independent review identified partial new-test setup cleanup and native
   definition/duration oracle omissions. Owned-resource cleanup and exact native
   checks/mutants address them. The refresh test now establishes a locked cache
   immediately before claim and observes its subsequent periodic refresh.
5. `native-runner.log` / `native-failed.zip`: the first native harness sent a
   namespaced target through the existing bounded `word()` command argument,
   which rejects `:`. It timed out before starting a mission. The runner now
   sends the bare built-in body word accepted by that parser; a Python regression
   binds the command shape. No production parser, timeout or assertion changed.
   Attempt 02 uses a fresh disposable world and the same frozen artifacts.

## Root executed checks

Python uses `D:/python/pyenv/pyenv-win/shims/python.bat -B` and `PYTHONUTF8=1`.
Complete logs are retained under the root evidence directory in the index below.

| Command | Observed result |
|---|---|
| `gradlew.bat runData --console=plain` | Initial generation succeeds; 6 writes, 34 output resources |
| `gradlew.bat build runData runGameTestServer --console=plain` (`all-reused-04`) | Exit 0, 2m40s; 898 JUnit and all 235 required GameTests; 0 DataGen writes |
| `python -B -m unittest tests.test_v140_planetary_worlds_smoke tests.test_v140_celestial_schema_smoke` | Exit 0, 25 tests; corrected native oracle included |
| `python -B scripts/validate_v1plus_planning.py` | Planning-only validation of 11 plans; no Gate approval |
| `gradlew.bat clean build runData --console=plain` (`clean-final-05`) | Exit 0, 46s; 898 tests/164 suites restored from prior passing cache; 0 writes among 34 generated resources |
| Same Python focused command (`python-focused-03`) | Exit 0, 26 tests; includes corrected command-token regression |
| `python -B scripts/validate_bootstrap_provenance.py --require-approved-review` | Accepted historical provenance remains digest-bound; 2 components, 11 imported targets, 2 local assets |
| `python -B scripts/manage_v030_generated_manifest.py verify` | Unchanged 7-file historical DataGen inventory |
| `python -B scripts/run_v140_planetary_worlds_smoke.py <fresh-server> --baseline-jar <pre-planet> --host-jar <frozen-main> --evidence-dir <native-02> --java <jdk17> --accept-eula --research-unlocks` | Exit 0, 3 packaged local JVMs; research/upgrade/travel/restart; own ports 51037 and 57877 closed afterward |

## Independent review and reruns

The reviewer read the actual contract and diff, retained initial findings and
verified the concrete corrections. An exclusive execution period then forced
57 focused JUnit cases in 10 suites (zero failures/errors/skips, all 16 tasks
executed), followed by DataGen and all 235 required GameTests in a fresh world.
The world directory was absent beforehand; the reviewer did not delete it.
DataGen wrote zero of 34 resources. The 26 Python harness tests also passed.

Commands: `gradlew.bat test --tests '*PlanetaryDiscoveryTest' --tests
'*CelestialDefinitionCodecTest' --tests '*CelestialSchemaV2Test' --tests
'*CelestialSavedDataTest' --tests '*SatelliteMissionRegistryTest' --tests
'*SatelliteMissionSavedDataTest' --tests '*RocketTargetFlightPlannerTest'
--tests '*RocketNavigationTest' --tests '*RocketFlightPlanPacketTest' --tests
'*RocketMaintenanceStructureTest' --rerun-tasks --offline --no-daemon
--no-build-cache --console=plain`; then `gradlew.bat runData runGameTestServer
--offline --no-daemon --no-build-cache --console=plain`, and the focused Python
command above. Full exact argv/exit/duration receipts are retained in the review
archive, not inferred from Gradle's task list.

All 1344 tracked/new runtime/build/Python/generated inputs, generated diff and
three frozen JAR hashes are unchanged across review. Independent ZIP inspection
finds 34 exact/unique generated JSON resources and 27 API classes byte-identical
to the main artifact. All 149 raw native manifest entries and persisted mission,
account, discovery, cargo, station and binding evidence were read back.

The independent fresh GameTest startup reports one 2771 ms / 55-tick lag warning.
It is preserved alongside the intentional migration fault-injection logs; no
exception is removed from evidence. The tests do not establish MSPT/load budgets.

## Packaged restart and frozen artifacts

The real pre-planet development artifact initializes a new native world before
the current JAR upgrades it. An undiscovered Mars request is refused without
changing the 2000 mB rocket. Three research missions for Mars, Venus and gas giant
then complete and claim; two owner accounts retain 40 and 20 net research, with
240/200 and 120/100 earned/spent totals respectively. Repeated claims before and
after restart return ALREADY_CLAIMED without changing progress, missions,
satellites or accounts. Clock advancement is not mistaken for an account change.

Native NBT binds exact mission/satellite UUIDs, owners, schema 1, definition ID,
captured 200-tick duration, 120/100 yield/fee and claimed state. Both root stores
remain schema 2. The three discovered IDs persist, including the gas giant,
which still has no Level or surface arrival. Existing world bindings grow 3→6
without remapping; stations grow 1→3 and remain identical after restart.
Earth→Mars→Venus→Earth conserves one rocket and its cargo, with fuel
2000→1552→1019→504. This command-driven empty-server exercise does not prove
terminal interaction, real-player flight or arbitrary interruption safety.
All three processes exit 0 with no ERROR/FATAL or client-linkage failure. The
restart log does contain one `Can't keep up` warning: 2050 ms / 41 ticks behind.
It is retained, not rerun away; this startup/restart observation does not pass
the reference-hardware MSPT or sustained-load budgets.

| JAR | Bytes | SHA256 |
|---|---:|---|
| main | 2,608,569 | `086029e364e2c54ce1b398db9542eabf0c02caf6420b4e7ac3a657ab7bd1d7e9` |
| API | 48,469 | `50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf` |
| sources | 1,186,739 | `94c274d732713d24cf470b7fd93d2574278a1d96eb09dbd0d7ca3a479caef3b1` |

API bytes match the previous SKY slice. No v1.4 release candidate, push or tag
is created by these local development artifacts.

## Evidence index

- [Source identity](source-identity.json), [artifact identities](development-artifacts.json),
  [generated-resource/package audit](generated-resources.json) and
  [checksums](SHA256SUMS.txt) bind the observed bytes.
- [Root result summary](root-checks.json), [root logs/XML](root-checks.zip) and
  [member inventory](root-checks-files.json) retain passing and failing commands.
- [Failed GameTest world](failed-world.zip) and [inventory](failed-world-files.json)
  preserve the original three missing-precondition failures.
- [Failed native attempt](native-failed.zip) and [inventory](native-failed-files.json)
  retain its original world and command-parser failure.
- [Successful native restart](native-restart.zip), [summary](native-summary.json)
  and [inventory](native-restart-files.json) preserve raw progress/account/mission,
  world, entity, journal, configuration, command and log observations.
- [Independent contract/source/final review](independent-review.zip) and
  [inventory](independent-review-files.json) retain all review phases, forced
  XML, raw manifests and the separately written native readback. Final report
  SHA256: `cba4a54690b7c64fe37cab33bc226becb45a6834f7610c87f39cb67b2206e541`.
- Root raw output: `C:/Users/Administrator/AppData/Local/Temp/arce-v140-discovery-1b0774f342554387ab7d491c64e7af64/`.
- Contract review: `C:/Users/Administrator/AppData/Local/Temp/arce-v140-disc-contract-review-9f6ee1e5fdeb4973a9de00c92d65b887/`.
- Source/correction review: `C:/Users/Administrator/AppData/Local/Temp/arce-v140-disc-source-review-125f78863a4c4f22ac9684c5b11b1ba3/`.
- Final reruns/native audit: `C:/Users/Administrator/AppData/Local/Temp/arce-v140-disc-final-review-941e61441246407d947d23d37d04c0ef/`.
- [Manual cases](MANUAL-CASES.md) remain NOT_RUN.

## Limits and remaining work

Normal restart/repeat claims are not a cross-file power-loss guarantee. Existing
mission and celestial SavedData writes are separate authorities. At the retained
128-ID limit, a claim can remain pending with its once-applied research award/fee;
records are not silently evicted. Full interruption/recovery and removed-content
scenarios remain V140-MIG. Real client UI/audio/GPU, two clients, remote reference
hardware and sustained load are deferred under ADR-018. All v1.4 Required G0–G9
are **not satisfied**; version status stays IN_PROGRESS and no tag/candidate or
release approval is created.
