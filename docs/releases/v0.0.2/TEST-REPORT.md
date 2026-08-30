# TEST-REPORT — v0.0.2 Forge Bootstrap

```yaml
test_date: 2026-08-30
version: v0.0.2
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 7441cd245251040ef2b1629257be978b4796fe0e
documentation_checkpoint: 7441cd245251040ef2b1629257be978b4796fe0e
branch: codex/v0.0.2-forge-bootstrap
environment: Windows 11 / Microsoft Java 17.0.8 / Gradle 8.8 / local Python 3.13.13 / CI Python 3.12 / Minecraft 1.20.1 / Forge 47.4.10
```

## Historical automated command results through `da67cfa`

This table preserves the earlier artifact and review-automation checkpoints.
The current `7441cd2` results are recorded separately below rather than
relabeling the historical 396-test and schema facts.

| Command | Result | Detail |
|---|---|---|
| `gradlew --version` | PASS | Gradle 8.8 on JVM 17.0.8 |
| `gradlew clean build --no-daemon --stacktrace` | PASS | The earlier Java 17 clean build completed in 12 seconds; main JAR, sources JAR, and content-manifest hashes remained byte-identical at that checkpoint |
| `gradlew test --rerun-tasks --no-daemon --stacktrace` | PASS | 3/3 JUnit tests executed in a 13-second uncached task run, with 0 failures or errors |
| `python -m unittest discover -s tests -v` | PASS | 396/396 Python tests passed in 1797.580 seconds |
| `python -I -S scripts/validate_bootstrap_provenance.py` | PASS_WITH_HUMAN_PENDING | Schema-3 evidence matched 2 pinned components, 11 imported targets, Git object/mode/blob identities, and checkpoint content; `--require-approved-review` returned the expected blocking exit 1 |
| `python -I -S scripts/prepare_v002_g0_review_packet.py generate/verify ...` | PASS | Checkpoint `da67cfa` bound 35 exact-Git inputs plus generated instructions (36 payloads, 37 total files); authoritative and content-only verification passed, governance CI uploaded artifact 9724184181, and its manifest SHA-256 is `cdd23d96...` |
| Focused final-G0 review-input generator/verifier tests | PASS | 16/16 tests cover exact-Git reconstruction, deterministic create-once output, isolated CLI execution, history/resource bounds, and link/reparse/hardlink/traversal rejection; the tool records inputs only |
| `python -I -S scripts/prepare_v002_final_g0_review_inputs.py generate/verify ...` | PASS | Exact checkpoint `da67cfa` report covers 18 distributable source/resource/legal files, 11 bootstrap targets, 20 commits, and 38 path changes; governance artifact 9724184322 and the local report are byte-identical with SHA-256 `798aad75...` |
| `python scripts/validate_build_artifact.py <jar> --content-manifest <path>` | PASS | 34 entries; metadata, exact notices/licenses, paths, generated-cache exclusion, placeholders, and credential scans passed |
| `python scripts/generate_v002_g0_evidence.py verify <jar> <sources-jar> ...` | PASS | Committed mechanical G0 evidence matched both checkpoint JARs |
| `python scripts/validate_release_checksums.py --artifact <jar>` | PASS | 10 entries; all 9 committed evidence files and the external JAR matched |
| `python scripts/check_client_imports.py` | PASS | No common/server client references |
| Earlier `python scripts/validate_repository.py --require-approved-identity` contract | PASS | The earlier checkpoint reported 15 passed, 0 warnings, and 0 failed before explicit `PENDING` became a separate result class |
| `gradlew runData --no-daemon --stacktrace` | PASS | The earlier 17-second run left the tracked diff, index, status, generated-resource hashes, and non-ignored untracked-content snapshot unchanged; one historical Mojang download retry remains recorded below |
| `gradlew runGameTestServer --no-daemon --stacktrace` | PASS | 1/1 required GameTest passed in the earlier 22-second run |
| Earlier `python scripts/run_dedicated_server_smoke.py <jar> ...` baseline | PASS | Schema-2 session `v002-22fb01477178d45fc51e007e` completed in 76.015 seconds on its first installer attempt; first start/status/save/stop and same-world restart/status/save/stop passed with zero ERROR, project WARN, or client linkage findings |
| Earlier tested-artifact repository governance | PASS | [Run 33258532838](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33258532838) passed at implementation commit `0fa080f` |
| Earlier tested-artifact Forge bootstrap | PASS | [Run 33258532863](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33258532863) passed baseline plus advisory jobs and uploaded the Linux artifacts for `0fa080f` |
| Earlier documentation-checkpoint repository governance | PASS_AFTER_FIX | [Run 33277040675](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33277040675) passed 353/353 tests, packet generation/verification, and 15/15 strict checks at checkpoint `d2b571f` |
| Earlier documentation-checkpoint Forge bootstrap | PASS | [Run 33277040688](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33277040688) passed baseline plus advisory jobs at checkpoint `d2b571f` |
| Hardened documentation-checkpoint repository governance | PASS | [Run 33285099023](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33285099023) passed 396/396 tests, exact-head packet/report generation and verification, 15/15 strict checks, and uploaded both review-input artifacts at `da67cfa` |
| Hardened documentation-checkpoint Forge bootstrap | PASS | [Run 33285098959](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33285098959) passed baseline plus advisory jobs and packaged-server smoke at `da67cfa` |
| Earlier tested-artifact clean-worktree checks | PASS | `git diff --exit-code` and `python scripts/check_clean_worktree.py` passed in CI at `0fa080f` |

## Acceptance-hardening checkpoint — `7441cd2`

These results were executed against the clean committed tree at
`7441cd245251040ef2b1629257be978b4796fe0e`. They verify the evidence and Gate
contracts but do not supply a provenance decision, a client observation, or
human release acceptance.

| Command or check | Result | Detail |
|---|---|---|
| `python -B -m unittest discover -s tests` | PASS | 517/517 tests passed in 1832.229 seconds; wrapper elapsed time was 1832.449 seconds |
| Final `gradlew clean build --no-daemon --stacktrace` on explicit Java 17 | PASS_AFTER_RECOVERY | After the verified-empty directory was removed, Microsoft OpenJDK 17.0.8 completed the full clean build in 11.719 seconds and both JAR hashes matched; the earlier 12.065-second compilation and first sources audit are retained below |
| `gradlew test --rerun-tasks --no-daemon --stacktrace` | PASS | 3/3 JUnit tests executed with 0 failures, errors, or skips in 13.187 seconds |
| JAR, G0 mechanical evidence, checksum, and side audits | PASS | Main JAR has 34 entries and SHA-256 `58622a5a...`; sources JAR SHA-256 is `2e18a573...`; generated and committed content manifests are byte-identical |
| `gradlew runData --no-daemon --stacktrace` | PASS | Completed in 17.155 seconds; tracked diff and complete non-ignored status were empty before and after |
| `gradlew runGameTestServer --no-daemon --stacktrace` | PASS | 1/1 required GameTest passed in 23.395 seconds |
| `python scripts/run_dedicated_server_smoke.py ...` | PASS | Session `v002-c86a0d5af17a6047bfbbf8c3` passed first start/status/save/stop and same-world restart/status/save/stop in 132.829 seconds |
| Strict repository validation | PASS_WITH_PENDING_GATES | 15 PASS, 3 PENDING, 0 WARN, 0 FAIL; final G0, the client bundle, and ADR-005 remain explicitly pending |
| Bootstrap provenance validation | PASS_WITH_HUMAN_PENDING | 2 components, 11 imported targets, and 2 local assets validated mechanically; human provenance review remains pending |
| Final-G0 and G4 validators | PASS_WITH_HUMAN_PENDING | Both pending records are structurally valid; neither validator records or infers a human decision |
| Commit-bound final-G0 report | INPUTS_ONLY | Schema 2 report for `7441cd2` covers 18 inventory files, 11 bootstrap targets, 22 commits, and 286 path changes; SHA-256 `9f87d1a82a7d5b8583c6be3cfb2548c9d9018143a9f64407a0232165ec8cd023`; bootstrap approval prerequisite remains pending |
| Commit-bound G0 packet | PASS_WITH_HUMAN_PENDING | 35 bound files plus generated instructions produced 36 payloads and 37 total files including the manifest; manifest SHA-256 `395c753fc9d723aa358e27dfaca182bd906fa830bb820ad013a95c95e869f795` |
| Acceptance-hardening repository governance | PASS_WITH_PENDING_GATES | [Run 33293732862](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732862) passed 517/517 tests and strict validation at exact head `7441cd2`; the validator correctly reported 15 PASS and 3 PENDING rather than treating unfinished acceptance as success |
| Acceptance-hardening Forge bootstrap | PASS | [Run 33293732867](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732867) passed baseline plus advisory jobs, 3/3 JUnit, 1/1 GameTest, and packaged-server smoke at exact head `7441cd2` |
| Exact-head CI artifact comparison | PASS | Forge artifact 9726838947 and governance artifacts 9726778456/9726778602 are bound to `7441cd2`; both JARs, the content manifest, packet manifest, and final-G0 report match local hashes |

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
linux_ci_artifact_id: 9726838947
checkpoint_linux_ci_artifact_id: 9726838947
checkpoint_g0_review_packet_artifact_id: 9726778456
checkpoint_g0_review_packet_commit: 7441cd245251040ef2b1629257be978b4796fe0e
checkpoint_g0_review_packet_manifest_sha256: 395c753fc9d723aa358e27dfaca182bd906fa830bb820ad013a95c95e869f795
checkpoint_final_g0_review_inputs_artifact_id: 9726778602
checkpoint_final_g0_review_inputs_commit: 7441cd245251040ef2b1629257be978b4796fe0e
checkpoint_final_g0_review_inputs_sha256: 9f87d1a82a7d5b8583c6be3cfb2548c9d9018143a9f64407a0232165ec8cd023
cross_platform_byte_identity_proven: true
```

The committed per-entry manifest is
[`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json),
with SHA-256
`a5128fffaca624155a00b8a60bdc6eb3f7c3451b97414cfe9935dbe7408d3cd5`.
The ten-entry release list is [`checksums.txt`](checksums.txt). The earlier
artifacts downloaded from Forge run 33258532863 match the Windows main JAR,
sources JAR, and committed content manifest byte-for-byte.
The same three hashes were independently confirmed again in exact-head Forge
artifact 9726838947 from run 33293732867.

## Test inventory

| Layer | Count | Result | Notes |
|---|---:|---|---|
| Java unit | 3 | PASS | Approved identity and expanded Forge metadata |
| Python unit | 517 | PASS | Repository/workflow contracts, schema-3 provenance approval binding, commit-bound G0 packet, schema-2 final-G0 exact-Git inputs, schema-5 client bundle and schema-4 player summary, G4 applicability, bounded JAR/G0, side, checksum, worktree, status protocol, installer recovery, lifecycle, path, privacy, and credential checks |
| DataGen | 1 provider | PASS | Minimal GameTest structure remained byte-stable and the current run left the worktree unchanged |
| Forge GameTest | 1 | PASS | Entrypoint and approved mod ID agree |
| Packaged dedicated server | 2 cycles | PASS | Current JAR schema-2 first start and same-world restart both exited 0 after artifact/status/save/stop, world, and canonical startup-properties identity checks |
| Packaged client/manual | 0 completed | PENDING | Isolated Mods/world/join/reconnect/mismatch observations remain |

## Failures and recovery

| Attempt | Result | Resolution |
|---|---|---|
| Accidental Java 8 clean build | FAIL | Re-ran with the pinned Microsoft Java 17.0.8 runtime; the current artifact built successfully |
| Acceptance-hardening ambient-runtime clean build | FAIL | The shell selected IBM Java 8 and ForgeGradle stopped before compilation; setting `JAVA_HOME` explicitly to Microsoft Java 17.0.8 made the unchanged clean build pass |
| Acceptance-hardening first sources-JAR audit | FAIL | A verified-empty, untracked `src/main/java/example/` directory added an unwanted archive entry; removing only that empty directory and rerunning `sourcesJar` restored the expected `2e18a573...` hash |
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
historical_packaged_server_session_id: v002-22fb01477178d45fc51e007e
historical_packaged_server_first_cycle:
  error_count: 0
  warning_count: 23
  project_error_count: 0
  project_warning_count: 0
  client_class_linkage_count: 0
historical_packaged_server_restart_cycle:
  error_count: 0
  warning_count: 10
  project_error_count: 0
  project_warning_count: 0
  client_class_linkage_count: 0
acceptance_hardening_packaged_server_session_id: v002-c86a0d5af17a6047bfbbf8c3
acceptance_hardening_packaged_server_first_cycle:
  error_count: 0
  warning_count: 19
  project_error_count: 0
  project_warning_count: 0
  client_class_linkage_count: 0
acceptance_hardening_packaged_server_restart_cycle:
  error_count: 0
  warning_count: 6
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
documentation_checkpoint: 7441cd245251040ef2b1629257be978b4796fe0e
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
