# RELEASE-EVIDENCE — v0.0.2

## Identity

```yaml
version: v0.0.2
status: IN_PROGRESS
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 05ef786c3df567517e28d1cb17bb1c74e57a4cc2
tag: NOT_CREATED
release: NOT_CREATED
pull_request: https://github.com/sunthemoon/AdvancedRocketry-Community/pull/3
forge_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33083493937
governance_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33083493539
minecraft: 1.20.1
forge_baseline: 47.4.10
forge_compat_lane: 47.4.23
java: 17.0.8
gradle: 8.8
built_at: 2026-08-27
built_by: Codex-assisted local development
jar_sha256: 827c07b34745cc5e6f484beb398b718cf87bd50e8d5be4f3c12679adc0973dcd
```

## Gate summary

| Gate | Status | Evidence |
|---|---|---|
| G0 Identity/License/Provenance | READY_FOR_HUMAN_REVIEW | Exact MDK/Wrapper source and target mapping, exact license copies, JAR packaging, and automated hash checks exist; human scope/sufficiency review remains |
| G1 Reproducible Build | PASS | Two Windows clean builds and the Linux baseline upload produced the same 34-entry JAR |
| G2 Data/Assets | PASS | DataGen passed after one retained network retry and left no generated-resource or committed worktree change |
| G3 Automated Behavior | PASS | 3 JUnit, 56 Python, and 1 Forge GameTest pass |
| G4 Dedicated/Sides | IN_PROGRESS | Packaged first-start/save/stop/restart passes; packaged player join/reconnect and mismatch observation remain |
| G5 Persistence/Recovery | NOT_APPLICABLE | No project persistent data in v0.0.2 |
| G6 Security/Authority | NOT_APPLICABLE | No project packets or gameplay authority in v0.0.2 |
| G7 Performance | NOT_APPLICABLE | No gameplay loop, ticking service, or world scan in v0.0.2 |
| G8 Manual Flow | NOT_STARTED | Packaged-client Mods page, world entry, and player-flow evidence remain |
| G9 Docs/Release | READY_FOR_HUMAN_REVIEW | Changelog, installation/save boundary, known issues, evidence, checksums, and 3/3 checks exist; human acceptance remains |

No Required Gate is treated as waived. Proposed G4 `NOT_APPLICABLE` rationales
for two-player project state and optional client dependencies require explicit
human decisions and do not replace the matching-client checks.

## Commands actually run for the tested implementation

```powershell
$env:JAVA_HOME = 'C:\Users\admin\AppData\Roaming\.minecraft\runtime\java-runtime-gamma-snapshot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat clean build --no-daemon --stacktrace
.\gradlew.bat clean build --no-daemon --stacktrace
python -m unittest discover -s tests -v
python scripts/validate_build_artifact.py build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar --content-manifest build/release-evidence/jar-content-manifest.json
python scripts/validate_release_checksums.py --artifact build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar
python scripts/check_client_imports.py
python scripts/validate_repository.py --require-approved-identity
.\gradlew.bat runData --no-daemon --stacktrace
git diff --exit-code -- src/generated/resources
.\gradlew.bat runGameTestServer --no-daemon --stacktrace
$session = Join-Path (Resolve-Path .).Path 'build\dedicated-server-smoke\final-session'
$evidence = Join-Path (Resolve-Path .).Path 'build\dedicated-server-smoke\final-evidence'
python scripts/run_dedicated_server_smoke.py build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar --java "$env:JAVA_HOME\bin\java.exe" --offline-mode --session-dir $session --evidence-dir $evidence
python scripts/run_dedicated_server_smoke.py build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar --java "$env:JAVA_HOME\bin\java.exe" --offline-mode --session-dir $session --resume-install-session --evidence-dir $evidence --install-timeout 1800
git diff --exit-code
python scripts/check_clean_worktree.py
```

The first DataGen command failed on one Mojang asset download and the first
server command failed on the 600-second Forge installer timeout. Neither was
reported as a pass; their successful retries and recovery are recorded in
[`TEST-REPORT.md`](TEST-REPORT.md).

## Artifact and automated tests

- Final artifact:
  `advancedrocketry-community-1.20.1-0.0.2-dev.jar`.
- SHA-256:
  `827c07b34745cc5e6f484beb398b718cf87bd50e8d5be4f3c12679adc0973dcd`.
- Two same-environment clean builds produced identical bytes.
- The Linux baseline upload produced the same JAR SHA-256 as Windows, proving
  cross-platform byte identity for the tested implementation.
- The content manifest records 34 sorted entries with per-entry size and hash.
- The artifact contains byte-identical project LICENSE/NOTICE,
  `THIRD-PARTY-NOTICES.md`, and exact Forge/Gradle supplemental license copies.
- The release checksum validator covers all five committed evidence files and
  verifies the external JAR against the content manifest.
- Pull request #3 has 3/3 successful checks: governance, Forge 47.4.10
  baseline, and Forge 47.4.23 advisory compatibility.

See [`TEST-REPORT.md`](TEST-REPORT.md),
[`checksums.txt`](checksums.txt), and
[`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json).

## Dedicated server

```yaml
java: 17.0.8
forge: 47.4.10
offline_mode: true
loopback_only: true
source_and_server_jar_hash_equal: true
packaged_server_startup: PASS
packaged_status_minecraft: 1.20.1
packaged_status_protocol: 763
packaged_mod_marker: 1.20.1-0.0.2-dev
packaged_save_and_stop: PASS
same_world_restart: PASS
client_class_linkage_findings: 0
player_join: PENDING
```

Selected lifecycle evidence is under
[`evidence/dedicated-server/`](evidence/dedicated-server/). The retained local
session is ignored and contains full installer/runtime logs plus the world for
the packaged-client continuation.

## Manual tests

See [`MANUAL-TEST.md`](MANUAL-TEST.md). No packaged-client/manual PASS is
claimed. ForgeGradle `runClient` output is diagnostic-only and cannot satisfy
G4 or G8.

## Provenance

```yaml
official_forge_mdk: 1.20.1-47.4.10
official_gradle_wrapper_source: v8.1.1
forge_license_copy_verified: true
gradle_license_copy_verified: true
third_party_notices_packaged: true
new_upstream_ar_code_or_assets: 0
new_original_assets: 1 bootstrap logo
generated_assets: 1 GameTest structure
review_status: PENDING_HUMAN_REVIEW
```

The mechanical record is
[`../../provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md`](../../provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md).
It deliberately does not claim final legal/provenance approval.

## Save, network, security, and performance

There is no project save schema, packet, gameplay transaction, recurring world
work, or player-visible content in this bootstrap milestone. Disposable-world
compatibility and installation constraints are documented in
[`INSTALLATION.md`](INSTALLATION.md).

## Known issues

See [`KNOWN-ISSUES.md`](KNOWN-ISSUES.md).

## Final recommendation

```yaml
recommended_status: IN_PROGRESS
blocking_reasons:
  - G0 human provenance/license scope review is incomplete
  - G4 matching-client join, disconnect, restart, and reconnect evidence is absent
  - G4 missing-project-mod behavior and proposed N/A decisions are unreviewed
  - G8 packaged-client Mods screen and world-start evidence is absent
  - G9 human release acceptance is incomplete
human_approved_by: ""
human_approved_at: ""
```
