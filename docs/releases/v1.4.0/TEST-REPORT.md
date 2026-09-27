# v1.4 development checks and historical evidence

Development checkpoint `d33552a5374272b3b0669090ec4466075662ee4c`;
production checkpoint `a9143c2b62d5daab412cefedcee198f1fd032588`.
This documentation-only handoff changes no runtime, resource, build, test or
save/network contract. All release Gates remain open.

## Handoff command execution

Windows local environment, Oracle Java 17.0.7, Forge 47.4.10. Python uses
`D:/python/pyenv/pyenv-win/shims/python.bat`, `PYTHONUTF8=1`, `-B`.

```text
gradlew.bat clean build test runData runGameTestServer --offline --no-daemon --console=plain
python -B -m unittest tests.test_v140_discovery_cut_smoke tests.test_v140_migration_smoke tests.test_v140_celestial_schema_smoke tests.test_v140_planetary_worlds_smoke -v
python -B scripts/validate_repository.py --require-approved-identity
python -B scripts/validate_v1plus_planning.py
python -B scripts/validate_bootstrap_provenance.py
git diff --check
git diff --exit-code -- src/generated
git diff --exit-code
```

| Check | Actual result |
|---|---|
| Required Gradle command | Exit 0, 2m34s; 17 tasks executed, 9 cached, 1 up-to-date |
| JUnit | 922 results / 167 suites, zero failures/errors/skips, **FROM-CACHE**, not a fresh Java execution |
| GameTests | **236 required cases actually execute and pass** in disposable `build/gametest` after clean |
| DataGen | Zero writes and no generated diff |
| Artifacts | All three current JARs byte-identical to MIG-01/02/03; 27 exported API classes and unchanged v1.3 API JAR |
| Resources | All 34 current generated files match their prior inventory and exactly one packaged entry |
| Historical integrity | 12 outer manifests, 155 listed files, 198,939,157 bytes verified; original reports/metadata pinned |
| Python | 57 existing harness tests executed and passed (1.574s); no new production tests |
| Governance | Repository 45 passed, zero pending/warnings/failures (1869 links); planning 11 plans/33 inputs; bootstrap provenance passes; all exit 0 |
| Git | Generated diff, working/staged whitespace and staged-worktree `git diff --exit-code` exit 0; evidence metadata is rechecked after assembly |

The packaged host is 2,624,223 bytes, SHA-256
`576eaaf8a5e23df1b049fd5fa5029f1cd6d6ffcfd0cf50bd5980fc280c31d475`.
[Artifact identities](development-artifacts.json) distinguish the reused native
consumer from this build. No consumer, native server, client, SSH or load
campaign is rerun here. Raw command log, XML, diagnostics and collection helper
are in [handoff-checks.zip](handoff-checks.zip), with
[member identities](handoff-checks-files.json) and
[machine summary](handoff-verification.json).

## Diagnostics and collection corrections

The successful GameTest run retains **2560 ms / 51 ticks** of startup lag. It
also retains the disposable missing `server.properties` diagnostic, deliberate
Precision migration save/readback failure injection and the marked discovery
launch-save failure (`ARCE_DISCOVERY_EXPECTED_SAVE_FAILURE`). These are not
zero-warning/zero-ERROR logs. The terminal/Forge configuration and Gradle
deprecation diagnostics remain intact. A passing run does not approve timing.

The initial handoff helper wrongly expected the 33-file SKY batch to remain the
current count; DISC adds a Data Satellite resource, making 34. Its next attempt
assumed all old artifact metadata used an object envelope; some valid older
records are arrays. Both original scripts and failing logs are retained, followed
by corrected schema-aware collection and strict byte comparisons. No production
test assertion, timeout or performance budget was changed.

## Original slice records (not cumulative current-artifact counts)

| Record | Scoped observations | Attribution limit |
|---|---|---|
| [Preparation](../../work/v1.4.0-preparation/VERIFICATION.md) | Contract examples and baseline checks | Unchanged v1.3 runtime; not planetary feature evidence |
| [DATA-01](../../work/v1.4.0-data01/VERIFICATION.md) | Schema/capability and display-2 checks, finite startup/restart | Earlier artifact; no new surfaces yet |
| [DATA-02](../../work/v1.4.0-data02/VERIFICATION.md) | 100 bodies/101 routes; actual six rejected reload candidates, repair/restart | Finite model input, not reference-load performance or all-listener transaction |
| [MAP-01](../../work/v1.4.0-map01/VERIFICATION.md) | Binding ledger restart and refused remap | Refused pre-Level startup includes errors/fatal shutdown diagnostic; not a clean cycle |
| [MAP-02](../../work/v1.4.0-map02/VERIFICATION.md) | Actual Mars/Venus travel, bounded landing, new-body stations/restart | Earlier pre-planet v1.4 fixture, not authentic v1.3 upgrade |
| [ENV](../../work/v1.4.0-env/VERIFICATION.md) | Exposure/protection model and GameTests, finite packaged travel | No real-player protection/oxygen survival or native new-rule reload |
| [NAV](../../work/v1.4.0-nav/VERIFICATION.md) | Server quotes, generations, bounded 100/128-node layout | No actual client menu/packet or readable UI proof |
| [SKY](../../work/v1.4.0-sky/VERIFICATION.md) | 891 JUnit/234 GameTest final results and 33-file batch | No GPU/audio; original fixture failures and warnings retained |
| [DISC](../../work/v1.4.0-discovery/VERIFICATION.md) | 898 JUnit/235 GameTest final results, discovery/research restart, 34-file batch | Not two real clients or native two-store cuts on that earlier artifact |
| [MIG-01](../../work/v1.4.0-mig-claims/VERIFICATION.md) | Ordered durability/replay, 922 JUnit, independent 71 focused tests/236 GameTests, three clean native processes | Current production artifact; normal restart is S1, not forced cuts |
| [MIG-02](../../work/v1.4.0-mig-worlds/VERIFICATION.md) | Actual v1.3-copy upgrade/removal/restoration, four clean processes; 269 final native evidence files | Current artifact; 34-file original unchanged, not every subsystem or old stable world |
| [MIG-03](../../work/v1.4.0-mig-cuts/VERIFICATION.md) | Four finite native S2 cuts, 237 final and 103 failed-attempt files independently audited; 57 Python checks | Current artifact; two authority stores only, no disk/power-loss claim |

Each original report retains source/artifact identity, cache attribution,
commands, failed attempts and independent review. Counts are not added together
as unique current tests. The handoff index verifies **outer file hashes**, not
a fresh recursive interpretation of every native ZIP/NBT record. Detailed
native readback remains supplied by the named slice audits.

## Independent handoff review and outstanding acceptance

The independent reviewer checks the actual documentation diff and independently
reruns short artifact/metadata/manifest/link/planning checks. Its report and
limitations are recorded in [independent-review.json](independent-review.json).
That scoped review is not a candidate release audit.

The substantive review finds no unresolved issue after correcting two P3
descriptions: rejected sky reload retains its old map, and interrupted ledger
`.json.pending` blocks startup rather than following research-scratch retry.
Its independent checks cover three JARs, exact 27 API exports/main equality,
34 resources, archived root XML/logs, index records, planning/provenance and
1870 local file targets. The parser differs from root's 1869-link count; neither
number is a release metric. Its initial 179-member archive snapshot predates
the final governance/review assembly. The original in-progress checksum mismatch
and corrected successful audit are preserved in
[independent-review.zip](independent-review.zip); final assembly is checked
separately rather than retroactively enlarging that review's snapshot.

The final delta review finds no unresolved issue: it checks the refreshed
184-member root archive, the then-60-member independent archive, 20 checksums,
all 28 staged/working file identities and 1872 local file targets. It preserves
its initial collector-delta assumption failure and the corrected strict check;
178 prior raw members are unchanged, the reviewed collector changes and five
completed check logs are added. Its immutable report is appended afterwards,
with root checksum/member readback; the review does not claim to certify its
own future archive. ACC-01 is verified only as a development handoff.

Candidate CI/reproducibility, Forge 47.4.23 and modpack lanes, representative
whole-world migration, remaining S2, real players, V1/V2/two GPU categories,
reference-load, gameplay video and human release acceptance remain open.
See [GATE-STATUS](GATE-STATUS.md), [MANUAL-TEST](MANUAL-TEST.md) and
[PERFORMANCE](PERFORMANCE.md). No historical Gate is retroactively changed.
