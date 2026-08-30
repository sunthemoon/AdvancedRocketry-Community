# TEST-REPORT — v0.0.2 Forge Bootstrap

```yaml
test_date: 2026-08-30
version: v0.0.2
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 0fa080fdff3ab025c6b764b02d2d07fa9221c5ae
documentation_checkpoint: d2b571f7dd63cc7d87bc3acf9197e8fd72ab3cfa
branch: codex/v0.0.2-forge-bootstrap
environment: Windows 11 / Microsoft Java 17.0.8 / Gradle 8.8 / local Python 3.13.13 / CI Python 3.12 / Minecraft 1.20.1 / Forge 47.4.10
```

## Automated command results

| Command | Result | Detail |
|---|---|---|
| `gradlew --version` | PASS | Gradle 8.8 on JVM 17.0.8 |
| `gradlew clean build --no-daemon --stacktrace` | PASS | The current Java 17 clean build completed in 12 seconds; main JAR, sources JAR, and content-manifest hashes remain byte-identical to the tested implementation |
| `gradlew test --rerun-tasks --no-daemon --stacktrace` | PASS | 3/3 JUnit tests executed in a 13-second uncached task run, with 0 failures or errors |
| `python -m unittest discover -s tests -v` | PASS | 396/396 Python tests passed in 1797.580 seconds |
| `python -I -S scripts/validate_bootstrap_provenance.py` | PASS_WITH_HUMAN_PENDING | Schema-3 evidence matched 2 pinned components, 11 imported targets, Git object/mode/blob identities, and current content; `--require-approved-review` returned the expected blocking exit 1 |
| `python -I -S scripts/prepare_v002_g0_review_packet.py generate/verify ...` | PASS | Local checkpoint packet bound 33 files to `d2b571f`; governance CI generated and verified the corresponding PR-merge packet before uploading artifact 9721841271 |
| Focused final-G0 review-input generator/verifier tests | PASS | 16/16 tests cover exact-Git reconstruction, deterministic create-once output, isolated CLI execution, history/resource bounds, and link/reparse/hardlink/traversal rejection; the tool records inputs only |
| `python scripts/validate_build_artifact.py <jar> --content-manifest <path>` | PASS | 34 entries; metadata, exact notices/licenses, paths, generated-cache exclusion, placeholders, and credential scans passed |
| `python scripts/generate_v002_g0_evidence.py verify <jar> <sources-jar> ...` | PASS | Committed mechanical G0 evidence matches both current JARs |
| `python scripts/validate_release_checksums.py --artifact <jar>` | PASS | 10 entries; all 9 committed evidence files and the external JAR matched |
| `python scripts/check_client_imports.py` | PASS | No common/server client references |
| `python scripts/validate_repository.py --require-approved-identity` | PASS | 15 passed, 0 warnings, 0 failed |
| `gradlew runData --no-daemon --stacktrace` | PASS | The current 17-second run left the tracked diff, index, status, generated-resource hashes, and non-ignored untracked-content snapshot unchanged; one historical Mojang download retry remains recorded below |
| `gradlew runGameTestServer --no-daemon --stacktrace` | PASS | 1/1 required GameTest passed in the current 22-second run |
| `python scripts/run_dedicated_server_smoke.py <jar> ...` | PASS | Fresh schema-2 session `v002-22fb01477178d45fc51e007e` completed in 76.015 seconds on its first installer attempt; first start/status/save/stop and same-world restart/status/save/stop passed with zero ERROR, project WARN, or client linkage findings |
| GitHub Actions repository governance | PASS | [Run 33258532838](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33258532838) passed at the tested implementation commit |
| GitHub Actions Forge bootstrap | PASS | [Run 33258532863](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33258532863) passed baseline plus advisory jobs and uploaded the Linux artifacts |
| Documentation-checkpoint repository governance | PASS_AFTER_FIX | [Run 33277040675](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33277040675) passed 353/353 tests, packet generation/verification, and 15/15 strict checks at checkpoint `d2b571f` |
| Documentation-checkpoint Forge bootstrap | PASS | [Run 33277040688](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33277040688) passed baseline plus advisory jobs at checkpoint `d2b571f` |
| Tested-implementation clean-worktree checks | PASS | `git diff --exit-code` and `python scripts/check_clean_worktree.py` passed in CI at `0fa080f` |

## Artifact identity

```yaml
artifact: advancedrocketry-community-1.20.1-0.0.2-dev.jar
sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
sources_sha256: 2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293
entry_count: 34
repeated_clean_build_hash_equal: true
server_copy_hash_equal: true
linux_ci_sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
linux_ci_sources_sha256: 2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293
linux_ci_content_manifest_sha256: a5128fffaca624155a00b8a60bdc6eb3f7c3451b97414cfe9935dbe7408d3cd5
linux_ci_artifact_id: 9716650737
checkpoint_linux_ci_artifact_id: 9721907600
checkpoint_g0_review_packet_artifact_id: 9721841271
checkpoint_g0_review_packet_commit: 08e8c4813f4cbc4913ff8fb9c78162bdf6dbc5b4
cross_platform_byte_identity_proven: true
```

The committed per-entry manifest is
[`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json),
with SHA-256
`a5128fffaca624155a00b8a60bdc6eb3f7c3451b97414cfe9935dbe7408d3cd5`.
The ten-entry release list is [`checksums.txt`](checksums.txt). The artifacts
downloaded from Forge run 33258532863 match the Windows main JAR, sources JAR,
and committed content manifest byte-for-byte.
The same three hashes were independently confirmed in checkpoint Forge artifact
9721907600 from run 33277040688.

## Test inventory

| Layer | Count | Result | Notes |
|---|---:|---|---|
| Java unit | 3 | PASS | Approved identity and expanded Forge metadata |
| Python unit | 396 | PASS | Repository/workflow contracts, schema-3 provenance approval binding, commit-bound G0 subreview packet, final-G0 exact-Git review inputs, schema-4 client-profile evidence, bounded JAR/G0, side, checksum, worktree, status protocol, installer recovery, lifecycle, path, privacy, and credential checks |
| DataGen | 1 provider | PASS | Minimal GameTest structure remained byte-stable and the current run left the worktree unchanged |
| Forge GameTest | 1 | PASS | Entrypoint and approved mod ID agree |
| Packaged dedicated server | 2 cycles | PASS | Current JAR schema-2 first start and same-world restart both exited 0 after artifact/status/save/stop, world, and canonical startup-properties identity checks |
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
| Current clean-build log placement | FAIL | The verification wrapper opened its log below `build/`, so Gradle could not delete that file during `clean`; moving only the wrapper log to the system temporary directory allowed the unchanged clean build to pass |
| First commit-bound packet governance CI | FAIL | Run 33276573450 exposed a fixture that attempted a non-empty commit after the tool bytes were already in `HEAD`; commit `d2b571f` made the fixture tip explicitly `--allow-empty`, 30/30 focused tests passed locally, and run 33277040675 passed the full 353-test suite |

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
tested_implementation_governance_ci: PASS
tested_implementation_forge_ci: PASS
tested_implementation_checks: 3/3_PASS
documentation_checkpoint: d2b571f7dd63cc7d87bc3acf9197e8fd72ab3cfa
last_observed_checkpoint_governance_ci: PASS
last_observed_checkpoint_forge_ci: PASS
last_observed_checkpoint_checks: 3/3_PASS
release_publication: NOT_CREATED
required_classification_if_created: PRE_RELEASE
release_status: IN_PROGRESS
blocking_items:
  - Human Forge/Gradle provenance/license subreview before the final rebuild
  - Exact-commit final G0 source/resource inventory-history review and archived input report
  - Post-rebuild rendered README screenshot and human G0 visual review
  - Isolated packaged-client Mods page and single-player evidence
  - Three-way JAR equality and matching-client join/disconnect/restart/reconnect evidence
  - Missing-project-mod observation and scoped G4 applicability decisions
  - Human release acceptance
```

No GitHub Release is required to finish the Gate review. If a release is created
after explicit human acceptance, it must be marked as a pre-release, never as a
stable release.
