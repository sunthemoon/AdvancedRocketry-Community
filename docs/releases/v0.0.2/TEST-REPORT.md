# TEST-REPORT — v0.0.2 Forge Bootstrap

```yaml
test_date: 2026-08-27
version: v0.0.2
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 05ef786c3df567517e28d1cb17bb1c74e57a4cc2
branch: codex/v0.0.2-forge-bootstrap
environment: Windows 11 / Microsoft Java 17.0.8 / Gradle 8.8 / Minecraft 1.20.1 / Forge 47.4.10
```

## Automated command results

| Command | Result | Detail |
|---|---|---|
| `gradlew --version` | PASS | Gradle 8.8 on JVM 17.0.8 |
| `gradlew clean build --no-daemon --stacktrace` | PASS | Repeated clean builds passed in 22s and 18s; 3 JUnit tests passed; JAR hashes matched |
| `python -m unittest discover -s tests -v` | PASS | 56/56 Python tests passed |
| `python scripts/validate_build_artifact.py <jar> --content-manifest <path>` | PASS | 34 entries; metadata, exact notices/licenses, paths, placeholders, and credential scans passed |
| `python scripts/validate_release_checksums.py --artifact <jar>` | PASS | 6 entries; all 5 committed evidence files and the external JAR matched |
| `python scripts/check_client_imports.py` | PASS | No common/server client references |
| `python scripts/validate_repository.py --require-approved-identity` | PASS | 13 passed, 0 warnings, 0 failed |
| `gradlew runData --no-daemon --stacktrace` | PASS_AFTER_RETRY | A Mojang asset download failed once; retry passed in 33s |
| `git diff --exit-code -- src/generated/resources` | PASS | DataGen produced no tracked generated-resource change |
| `git diff --exit-code` | PASS | Tested implementation had no tracked diff |
| `python scripts/check_clean_worktree.py` | PASS | No tracked, staged, untracked, or non-ignored files at the committed checkpoint |
| `gradlew runGameTestServer --no-daemon --stacktrace` | PASS | 1/1 required GameTest passed in 58s |
| `python scripts/run_dedicated_server_smoke.py <jar> ...` | PASS_AFTER_RECOVERY | Final JAR passed first start/status/save/stop and same-world restart/status/save/stop |
| GitHub Actions repository governance | PASS | [Run 33083493539](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33083493539) passed for the tested implementation |
| GitHub Actions Forge bootstrap | PASS | [Run 33083493937](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33083493937) passed baseline and advisory jobs |
| Download and hash Linux baseline artifact | PASS | Uploaded JAR and content manifest hashes equal their Windows/committed counterparts |

## Artifact identity

```yaml
artifact: advancedrocketry-community-1.20.1-0.0.2-dev.jar
sha256: 827c07b34745cc5e6f484beb398b718cf87bd50e8d5be4f3c12679adc0973dcd
entry_count: 34
repeated_clean_build_hash_equal: true
server_copy_hash_equal: true
linux_ci_sha256: 827c07b34745cc5e6f484beb398b718cf87bd50e8d5be4f3c12679adc0973dcd
cross_platform_byte_identity_proven: true
```

The committed per-entry manifest is
[`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json).
The six-entry release list is [`checksums.txt`](checksums.txt).
The Linux baseline upload `forge-47.4.10-0c44edceced9cb382f3bcbc71ca4a1d9ff395ba6`
contains the same JAR SHA-256 and the same content-manifest SHA-256
`4b9dc6e9e0f8d52d84a959d475e05238b908bbbf861dde596da849527470b865`.

## Test inventory

| Layer | Count | Result | Notes |
|---|---:|---|---|
| Java unit | 3 | PASS | Approved identity and expanded Forge metadata |
| Python unit | 56 | PASS | Repository, JAR, side, checksum, worktree, status protocol, installer recovery, lifecycle, path, and credential checks |
| DataGen | 1 provider | PASS_AFTER_RETRY | Minimal GameTest structure remained byte-stable |
| Forge GameTest | 1 | PASS | Entrypoint and approved mod ID agree |
| Packaged dedicated server | 2 cycles | PASS | Final JAR first start and same-world restart both exited 0 after identity/status/save/stop checks |
| Packaged client/manual | 0 completed | PENDING | Isolated Mods/world/join/reconnect/mismatch observations remain |

## Failures and recovery

| Attempt | Result | Resolution |
|---|---|---|
| First continuation `runData` | FAIL | Mojang CDN download of `minecraft/sounds/mob/panda/worried/worried4.ogg` failed; the retry downloaded it and passed |
| First final Forge installer invocation | FAIL | The 600-second hard timeout expired while libraries were still downloading; no passing evidence was written |
| Installer recovery implementation | PASS | Timeout output is retained, partial downloads can be retried, and explicit resume refuses any directory containing server runtime state |
| Resumed final packaged-server run | PASS | The validated partial downloads were retained; both server cycles then passed |
| Historical GameTest structure attempt | FAIL | Corrected `size` from `IntArrayTag` to `ListTag<IntTag>` and added an explicit air block |
| Historical Linux clean-tree attempt | FAIL | Stored `gradlew` as executable so CI's setup `chmod` is idempotent |
| Historical Forge status decoder attempt | FAIL | Replaced the empty legacy list check with Forge 47.4.10 optimized `forgeData.d` decoding |
| Historical flat-world attempt | FAIL | Replaced incomplete flat generator properties with a disposable normal world |

## Log review

```yaml
packaged_server_project_error_count: 0
packaged_server_client_class_linkage_count: 0
game_test_required_failures: 0
accepted_runtime_findings:
  - Forge development language-provider JARs report missing mods.toml files
  - Forge userdev union resource URLs report an unexpected schema
  - ForgeGradle reports upstream Gradle deprecations for Gradle 9 compatibility
  - A fresh GameTest/server run creates missing default configuration files
  - The GameTest launcher reports a missing initial server.properties before creating defaults
  - Headless runs report that advanced terminal features are unavailable
```

The accepted findings originate in Minecraft, Forge, ForgeGradle, or the fresh
test environment. No project-source ERROR is accepted.

## Conclusion

```yaml
local_automated_baseline: PASS
current_head_governance_ci: PASS
current_head_forge_ci: PASS
current_head_checks: 3/3 PASS
release_status: IN_PROGRESS
blocking_items:
  - Human Forge/Gradle provenance review
  - Isolated packaged-client Mods page and single-player evidence
  - Matching-client join/disconnect/restart/reconnect evidence
  - Missing-project-mod policy observation and scoped G4 N/A decisions
  - Human release acceptance
```
