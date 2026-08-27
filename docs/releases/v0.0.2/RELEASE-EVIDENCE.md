# RELEASE-EVIDENCE — v0.0.2

## Identity

```yaml
version: v0.0.2
build: 1.20.1-0.0.2-dev
commit: 41374d828e9200dc3efc8d2435e8857adb11335b
tag: ""
pull_request: https://github.com/sunthemoon/AdvancedRocketry-Community/pull/3
workflow_run: PENDING_FOR_CURRENT_HEAD
governance_workflow_run: PENDING_FOR_CURRENT_HEAD
minecraft: 1.20.1
forge_baseline: 47.4.10
forge_compat_lane: 47.4.23
java: 17.0.6
gradle: 8.8
built_at: 2026-08-27
built_by: Codex-assisted local development
jar_sha256: b10db9785c3f80e35b6bba53d11c518907f12d39fdee263ca3630a4ba57d50e9
```

## Gate summary

| Gate | Status | Evidence |
|---|---|---|
| G0 License/Provenance | PASS | Approved identity; official MDK hashes; packaged-license audit script; no upstream AR code/assets imported |
| G1 Build | PASS | Reproducible local baseline plus remote baseline and Forge 47.4.23 jobs pass on Java 17 |
| G2 Data/Assets | PASS | DataGen is deterministic and commit `1b5f280` remained clean after regeneration |
| G3 Automated Behavior | PASS | 3 JUnit tests, 30 Python tests, and 1 Forge GameTest pass |
| G4 Dedicated/Sides | IN_PROGRESS | Packaged first-start/save/stop/restart passes; matching player join/reconnect remains |
| G5 Persistence/Recovery | NOT_APPLICABLE | No persistent project data in v0.0.2 |
| G6 Security/Authority | NOT_APPLICABLE | No networking or gameplay authority in v0.0.2 |
| G7 Performance | NOT_APPLICABLE | No gameplay loop or world scanning in v0.0.2 |
| G8 Manual Flow | NOT_STARTED | Client Mods screen and world-start evidence deferred to external test machine |
| G9 Docs/Release | IN_PROGRESS | Status and evidence exist; CI, changelog/release acceptance, and checksums remain |

## Commands actually run

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17.0.6'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
./gradlew.bat --version
./gradlew.bat clean test --no-daemon
./gradlew.bat clean build --no-daemon
./gradlew.bat runData --no-daemon
./gradlew.bat runGameTestServer --no-daemon
python -m unittest discover -s tests -v
python scripts/check_client_imports.py
python scripts/validate_build_artifact.py build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar
python scripts/run_dedicated_server_smoke.py --java C:/Progra~1/Java/jdk-17.0.6/bin/java.exe
$env:ORG_GRADLE_PROJECT_forge_version = '47.4.23'
./gradlew.bat clean build --no-daemon
```

## Automated tests

See [TEST-REPORT.md](TEST-REPORT.md). The final Forge 47.4.10 GameTest execution reported one required test passed and a normal server shutdown.

## Dedicated server

```yaml
gametest_physical_server_startup: PASS
common_mod_initialization: PASS
client_class_linkage_errors: 0
packaged_server_startup: PASS
packaged_status_protocol: 763
packaged_mod_marker: 1.20.1-0.0.2-dev
packaged_save_and_stop: PASS
restart: PASS
player_join: DEFERRED_TO_TEST_MACHINE
```

## Manual tests

See [MANUAL-TEST.md](MANUAL-TEST.md). No visible client/manual PASS is claimed.
The packaged lifecycle evidence is archived under
[`evidence/dedicated-server/`](evidence/dedicated-server/).

## Provenance

```yaml
official_forge_mdk: 1.20.1-47.4.10
new_upstream_ar_code_or_assets: 0
new_original_assets: 1 bootstrap logo
generated_assets: 1 GameTest structure
unresolved: []
```

## Save, network, security, and performance

Not applicable to this bootstrap milestone. It contains no project save schema, packets, gameplay transactions, or recurring world work.

## Known issues

See [KNOWN-ISSUES.md](KNOWN-ISSUES.md).

## Final recommendation

```yaml
recommended_status: IN_PROGRESS
blocking_reasons:
  - Matching-client join, disconnect, restart, and reconnect evidence is absent
  - Client Mods screen and world-start evidence is absent
reviewed_by: ""
reviewed_at: ""
```
