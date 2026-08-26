# TEST-REPORT — v0.0.2 Forge Bootstrap

```yaml
test_date: 2026-08-26
version: v0.0.2
build: 1.20.1-0.0.2-dev
commit: 1b5f28094619081bc901bc1c7472cd19b129505a
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
| `git diff --exit-code` after DataGen | PASS | Commit `1b5f280` remained clean; no unignored files were created |

## Test inventory

| Layer | Count | Result | Notes |
|---|---:|---|---|
| Java unit | 3 | PASS | Approved identity and expanded Forge metadata |
| Python unit | 20 | PASS | Repository, JAR, side, identity, link, evidence, binary, path, and credential checks |
| DataGen | 1 provider | PASS | Minimal GameTest structure generated |
| Forge GameTest | 1 | PASS | Entrypoint and approved mod ID agree |
| Packaged dedicated server | 0 | NOT_RUN | Required before G4 can pass |
| Client/manual | 0 | NOT_RUN | Required before G8 can pass |

## Failures and retries

| Attempt | Result | Resolution |
|---|---|---|
| First `runData` | FAIL | One Mojang CDN sound asset failed download/validation; retry succeeded and later runs remained green |
| First `runGameTestServer` | FAIL | Corrected generated structure `size` from `IntArrayTag` to `ListTag<IntTag>` and added an explicit air block |
| Final `runGameTestServer` | PASS | `All 1 required tests passed :)`; server shut down normally |
| First Linux baseline CI | FAIL | Build, JAR audit, side scan, and DataGen passed; clean-tree check detected the workflow's `chmod` mode change. Stored `gradlew` as executable and reran CI |

## Log review

```yaml
project_error_count_after_fix: 0
project_warn_count_after_fix: 0
accepted_runtime_warnings:
  - Forge development language-provider JARs report missing mods.toml files
  - Forge userdev union resource URLs report an unexpected schema
  - ForgeGradle reports upstream Gradle deprecations for Gradle 9 compatibility
```

## Conclusion

```yaml
automated_gate: IN_PROGRESS
blocking_issues:
  - Remote CI has not run yet
  - Packaged dedicated and manual client tests remain
```
