# TEST-REPORT — v0.0.2 Forge Bootstrap

```yaml
test_date: 2026-08-27
version: v0.0.2
build: 1.20.1-0.0.2-dev
commit: 41374d828e9200dc3efc8d2435e8857adb11335b
branch: codex/v0.0.2-forge-bootstrap
environment: Windows 11 / Java 17.0.6 / Gradle 8.8 / Minecraft 1.20.1 / Forge 47.4.10
```

## Automated command results

| Command | Result | Detail |
|---|---|---|
| `gradlew --version` | PASS | Gradle 8.8 on JVM 17.0.6 |
| `gradlew clean test --no-daemon` | PASS | 3 JUnit tests passed |
| `gradlew runData --no-daemon` | PASS | Provider completed; repeated output hash was stable |
| `gradlew runGameTestServer --no-daemon` | PASS | 1 required GameTest passed on Forge 47.4.10 |
| `python -m unittest discover -s tests -v` | PASS | 20 Python tests passed |
| `python scripts/check_client_imports.py` | PASS | No common/server client references |
| `gradlew clean build --no-daemon` | PASS | Two clean builds passed in 34s and 27s; binary JAR hashes matched |
| `ORG_GRADLE_PROJECT_forge_version=47.4.23; gradlew clean build --no-daemon` | PASS | Clean compatibility build passed in 1m 14s with 3 JUnit tests |
| `python scripts/validate_build_artifact.py <jar>` | PASS | 32 entries; metadata, notices, paths, placeholders, and credential name/content scans passed |
| `python scripts/run_dedicated_server_smoke.py --java <JDK17>` | PASS | Pinned installer; first start/status/save/stop and same-world restart/status/save/stop all passed |
| `git diff --exit-code` after DataGen | PASS | The current implementation tree remained clean; no tracked or unignored files were created |
| GitHub Actions repository governance | PASS | [Run 32980410415](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/32980410415) passed |
| GitHub Actions Forge bootstrap | PASS | [Run 32980410416](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/32980410416) passed baseline and advisory jobs |

## Test inventory

| Layer | Count | Result | Notes |
|---|---:|---|---|
| Java unit | 3 | PASS | Approved identity and expanded Forge metadata |
| Python unit | 30 | PASS | Repository, JAR, side, identity, status protocol, Forge optimized payload, lifecycle log, path, and credential checks |
| DataGen | 1 provider | PASS | Minimal GameTest structure generated |
| Forge GameTest | 1 | PASS | Entrypoint and approved mod ID agree |
| Packaged dedicated server | 2 cycles | PASS | Final JAR first start and same-world restart both exited 0 after status/save/stop checks |
| Client/manual | 0 completed | DEFERRED | Visible Mods/world and matching-player checks move to an external test machine |

## Failures and retries

| Attempt | Result | Resolution |
|---|---|---|
| First `runData` | FAIL | One Mojang CDN sound asset failed download/validation; retry succeeded and later runs remained green |
| First `runGameTestServer` | FAIL | Corrected generated structure `size` from `IntArrayTag` to `ListTag<IntTag>` and added an explicit air block |
| Final `runGameTestServer` | PASS | `All 1 required tests passed :)`; server shut down normally |
| First Linux baseline CI | FAIL | Build, JAR audit, side scan, and DataGen passed; clean-tree check detected the workflow's `chmod` mode change. Stored `gradlew` as executable and reran CI |
| Corrected Linux baseline CI | PASS | Clean-tree check, GameTest, and artifact upload passed after the wrapper-mode correction |
| First packaged-server harness attempt | FAIL | Server started, but Forge 47.4.10 keeps legacy `mods` empty; implemented the official optimized `forgeData.d` decoder |
| Initial disposable flat-world configuration | FAIL | Incomplete flat generator properties emitted `No key layers`; final harness uses a normal disposable world |
| Intermediate installer session | FAIL | One `libraries.minecraft.net` read timed out; the harness now records and explicitly retries installer attempts |
| Final packaged-server run | PASS | Installer attempt 1 succeeded; first start and restart both returned status protocol 763 with the exact mod marker and exited 0 |

## Log review

```yaml
project_error_count_after_fix: 0
project_warn_count_after_fix: 0
accepted_runtime_warnings:
  - Forge development language-provider JARs report missing mods.toml files
  - Forge userdev union resource URLs report an unexpected schema
  - ForgeGradle reports upstream Gradle deprecations for Gradle 9 compatibility
  - A fresh packaged server corrects missing default Forge/common configuration keys
  - The headless packaged server reports that advanced terminal features are unavailable
```

## Conclusion

```yaml
automated_gate: PASS
blocking_issues:
  - Matching-client join/reconnect remains deferred to the external test machine
  - Client Mods-screen and world-start evidence remains deferred to the external test machine
```
