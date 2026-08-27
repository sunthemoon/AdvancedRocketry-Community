# TEST-REPORT — v0.0.2 Forge Bootstrap

```yaml
test_date: 2026-08-28
version: v0.0.2
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 7567dbb60332526789ee3b2824c582ff1909203e
branch: codex/v0.0.2-forge-bootstrap
environment: Windows 11 / Microsoft Java 17.0.8 / Gradle 8.8 / Minecraft 1.20.1 / Forge 47.4.10
```

## Automated command results

| Command | Result | Detail |
|---|---|---|
| `gradlew --version` | PASS | Gradle 8.8 on JVM 17.0.8 |
| `gradlew clean build --no-daemon --stacktrace` | PASS | Repeated clean builds produced the same main and sources JARs; 3 JUnit tests passed |
| `python -m unittest discover -s tests -v` | PASS | 154/154 Python tests passed |
| `python scripts/validate_bootstrap_provenance.py` | PASS | 2 pinned components and all 11 imported targets matched the machine-readable manifest |
| `python scripts/validate_build_artifact.py <jar> --content-manifest <path>` | PASS | 34 entries; metadata, exact notices/licenses, paths, generated-cache exclusion, placeholders, and credential scans passed |
| `python scripts/generate_v002_g0_evidence.py verify <jar> <sources-jar> ...` | PASS | Committed mechanical G0 evidence matches both current JARs |
| `python scripts/validate_release_checksums.py --artifact <jar>` | PASS | 10 entries; all 9 committed evidence files and the external JAR matched |
| `python scripts/check_client_imports.py` | PASS | No common/server client references |
| `python scripts/validate_repository.py --require-approved-identity` | PASS | 15 passed, 0 warnings, 0 failed |
| `gradlew runData --no-daemon --stacktrace` | PASS_AFTER_RETRY | A historical Mojang asset download failed once; the retained retry passed and produced no tracked generated-resource change |
| `gradlew runGameTestServer --no-daemon --stacktrace` | PASS | 1/1 required GameTest passed |
| `python scripts/run_dedicated_server_smoke.py <jar> ...` | PASS_AFTER_RECOVERY | Current 34-entry JAR passed schema-2 first start/status/save/stop and same-world restart/status/save/stop with bound log/world evidence |
| GitHub Actions repository governance | PENDING | The evidence-integration commit has not been pushed yet |
| GitHub Actions Forge bootstrap | PENDING | Current Linux JAR and content-manifest hashes are not yet available |
| Post-commit clean-worktree checks | PENDING | Run after the evidence-integration commit is created |

## Artifact identity

```yaml
artifact: advancedrocketry-community-1.20.1-0.0.2-dev.jar
sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
sources_sha256: 2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293
entry_count: 34
repeated_clean_build_hash_equal: true
server_copy_hash_equal: true
linux_ci_sha256: ""
cross_platform_byte_identity_proven: PENDING_CURRENT_HEAD_CI
```

The committed per-entry manifest is
[`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json),
with SHA-256
`a5128fffaca624155a00b8a60bdc6eb3f7c3451b97414cfe9935dbe7408d3cd5`.
The ten-entry release list is [`checksums.txt`](checksums.txt). No Linux hash is
recorded for this artifact until the evidence-integration workflow completes and
its uploaded files are downloaded and compared.

## Test inventory

| Layer | Count | Result | Notes |
|---|---:|---|---|
| Java unit | 3 | PASS | Approved identity and expanded Forge metadata |
| Python unit | 154 | PASS | Repository, provenance approval binding, blocking workflow structure, bounded JAR/G0, side, manual-evidence audit binding/readiness, checksum, worktree, status protocol, installer recovery, lifecycle, path, privacy, and credential checks |
| DataGen | 1 provider | PASS_AFTER_RETRY | Minimal GameTest structure remained byte-stable |
| Forge GameTest | 1 | PASS | Entrypoint and approved mod ID agree |
| Packaged dedicated server | 2 cycles | PASS | Current JAR schema-2 first start and same-world restart both exited 0 after identity/status/save/stop checks |
| Packaged client/manual | 0 completed | PENDING | Isolated Mods/world/join/reconnect/mismatch observations remain |

## Failures and recovery

| Attempt | Result | Resolution |
|---|---|---|
| Accidental Java 8 clean build | FAIL | Re-ran with the pinned Microsoft Java 17.0.8 runtime; the current artifact built successfully |
| First continuation `runData` | FAIL | Mojang CDN download of `minecraft/sounds/mob/panda/worried/worried4.ogg` failed; the retry downloaded it and passed |
| First final Forge installer invocation | FAIL | The 600-second hard timeout expired while libraries were still downloading; no passing evidence was written |
| Installer recovery implementation | PASS | Timeout output is retained, partial downloads can be retried, and explicit resume refuses any directory containing server runtime state |
| Resumed packaged-server run | PASS | The validated partial downloads were retained; both server cycles then passed |
| Generated `.cache` resource discovery | FAIL | Excluded `.cache/**` from resources and made the artifact validator reject generator metadata; the rebuilt JAR has 34 entries |
| Historical GameTest structure attempt | FAIL | Corrected `size` from `IntArrayTag` to `ListTag<IntTag>` and added an explicit air block |
| Historical Linux clean-tree attempt | FAIL | Stored `gradlew` as executable so CI's setup `chmod` is idempotent |
| Historical Forge status decoder attempt | FAIL | Replaced the empty legacy list check with Forge 47.4.10 optimized `forgeData.d` decoding |
| Historical flat-world attempt | FAIL | Replaced incomplete flat generator properties with a disposable normal world |

## Log review

```yaml
packaged_server_first_cycle:
  error_count: 0
  warning_count: 23
  project_error_count: 0
  project_warning_count: 0
  client_class_linkage_count: 0
packaged_server_restart_cycle:
  error_count: 0
  warning_count: 10
  project_error_count: 0
  project_warning_count: 0
  client_class_linkage_count: 0
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
test environment. No project-source ERROR or WARN is accepted. The broad warning
counts are retained separately from project-source findings.

## Conclusion

```yaml
local_automated_baseline: PASS
current_head_governance_ci: PENDING
current_head_forge_ci: PENDING
current_head_checks: PENDING
release_publication: NOT_CREATED
required_classification_if_created: PRE_RELEASE
release_status: IN_PROGRESS
blocking_items:
  - Current rendered README screenshot and human Forge/Gradle provenance review
  - Current-head CI and Linux artifact/content-manifest comparison
  - Isolated packaged-client Mods page and single-player evidence
  - Three-way JAR equality and matching-client join/disconnect/restart/reconnect evidence
  - Missing-project-mod observation and scoped G4 applicability decisions
  - Human release acceptance
```

No GitHub Release is required to finish the Gate review. If a release is created
after explicit human acceptance, it must be marked as a pre-release, never as a
stable release.
