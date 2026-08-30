# RELEASE-EVIDENCE — v0.0.2

## Identity

```yaml
version: v0.0.2
status: IN_PROGRESS
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 0fa080fdff3ab025c6b764b02d2d07fa9221c5ae
tag: NOT_CREATED
release: NOT_CREATED
release_publication: NOT_CREATED
required_classification_if_created: PRE_RELEASE
pull_request: https://github.com/sunthemoon/AdvancedRocketry-Community/pull/3
tested_implementation_forge_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33258532863
tested_implementation_governance_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33258532838
documentation_checkpoint: d2b571f7dd63cc7d87bc3acf9197e8fd72ab3cfa
last_observed_checkpoint_forge_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33277040688
last_observed_checkpoint_governance_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33277040675
checkpoint_forge_artifact_id: 9721907600
checkpoint_g0_review_packet_artifact_id: 9721841271
checkpoint_g0_review_packet_commit: 08e8c4813f4cbc4913ff8fb9c78162bdf6dbc5b4
minecraft: 1.20.1
forge_baseline: 47.4.10
forge_compat_lane: 47.4.23
java: 17.0.8
gradle: 8.8
built_at: 2026-08-29
built_by: Codex-assisted local development
jar_sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
sources_jar_sha256: 2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293
linux_jar_sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
linux_sources_jar_sha256: 2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293
content_manifest_sha256: a5128fffaca624155a00b8a60bdc6eb3f7c3451b97414cfe9935dbe7408d3cd5
linux_ci_artifact_id: 9716650737
tested_implementation_pull_request_checks: 3/3_PASS
last_observed_checkpoint_pull_request_checks: 3/3_PASS
```

## Gate summary

| Gate | Status | Evidence |
|---|---|---|
| G0 Identity/License/Provenance | IN_PROGRESS | Machine-readable input/target mapping, exact license copies, sources-JAR evidence, JAR packaging, and hash checks exist; the Forge/Gradle subreview, full source/resource inventory-history decision, and rendered README review remain |
| G1 Reproducible Build | PASS | Repeated Windows clean builds and tested-implementation Linux CI produced byte-identical main JARs, sources JARs, and content manifests |
| G2 Data/Assets | PASS | DataGen passed after one retained network retry and left no generated-resource or committed worktree change |
| G3 Automated Behavior | PASS | 3 JUnit, 396 Python, and 1 Forge GameTest pass |
| G4 Dedicated/Sides | IN_PROGRESS | Packaged first-start/save/stop/restart passes; packaged player join/reconnect and mismatch observation remain |
| G5 Persistence/Recovery | NOT_APPLICABLE | No project persistent data in v0.0.2 |
| G6 Security/Authority | NOT_APPLICABLE | No project packets or gameplay authority in v0.0.2 |
| G7 Performance | NOT_APPLICABLE | No gameplay loop, ticking service, or world scan in v0.0.2 |
| G8 Manual Flow | NOT_STARTED | Packaged-client Mods page, world entry, and player-flow evidence remain |
| G9 Docs/Release | IN_PROGRESS | Changelog, installation/save boundary, known issues, mechanical evidence, checksums, tested-implementation CI, and a successful last-observed documentation-checkpoint CI exist; client evidence integration and human acceptance remain |

No Required Gate is treated as waived. Five proposed G4 `NOT_APPLICABLE`
rationales cover bootstrap-only synchronization, two-player, chunk-unload,
configuration-mismatch, and optional-client-dependency cases. Every decision
requires explicit human review and does not replace the matching-client checks.
The G0 provenance/license subreview must be approved and its packaged notice
changes rebuilt before those client checks begin; otherwise the client evidence
would bind a superseded JAR. This subreview does not pass G0: the later full
source/resource inventory-history decision and rendered README visual review
remain part of final G0 acceptance.

## Commands actually run for the tested implementation

```powershell
$env:JAVA_HOME = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-gamma-snapshot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat clean build --no-daemon --stacktrace
.\gradlew.bat clean build --no-daemon --stacktrace
python -m unittest discover -s tests -v
python scripts/validate_bootstrap_provenance.py
python scripts/validate_build_artifact.py build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar --content-manifest build/release-evidence/jar-content-manifest.json
python scripts/generate_v002_g0_evidence.py verify build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar build/libs/advancedrocketry-community-1.20.1-0.0.2-dev-sources.jar --evidence-dir docs/releases/v0.0.2/evidence/g0-mechanical
python scripts/validate_release_checksums.py --artifact build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar
python scripts/check_client_imports.py
python scripts/validate_repository.py --require-approved-identity
.\gradlew.bat runData --no-daemon --stacktrace
git diff --exit-code -- src/generated/resources
.\gradlew.bat runGameTestServer --no-daemon --stacktrace
$session = Join-Path (Resolve-Path .).Path 'build\dedicated-server-smoke\v2-session'
$evidence = Join-Path (Resolve-Path .).Path 'build\dedicated-server-smoke\v2-evidence'
python scripts/run_dedicated_server_smoke.py build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar --java "$env:JAVA_HOME\bin\java.exe" --offline-mode --session-dir $session --evidence-dir $evidence
git diff --exit-code
python scripts/check_clean_worktree.py
```

A historical DataGen command failed on one Mojang asset download and a
historical server command failed on the 600-second Forge installer timeout.
Neither was reported as a pass; their successful retries and recovery are
recorded in [`TEST-REPORT.md`](TEST-REPORT.md).

## Artifact and automated tests

- Current tested artifact (pre-provenance-subreview approval):
  `advancedrocketry-community-1.20.1-0.0.2-dev.jar`.
- SHA-256:
  `58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944`.
- Sources JAR SHA-256:
  `2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293`.
- Two same-environment clean builds produced identical bytes.
- Tested-implementation Forge run 33258532863's Linux upload matches the Windows main JAR
  (`58622a5ad...`), sources JAR (`2e18a573...`), and content manifest
  (`a5128fff...`) byte-for-byte.
- The content manifest records 34 sorted entries with per-entry size and hash.
- The artifact contains byte-identical project LICENSE/NOTICE,
  `THIRD-PARTY-NOTICES.md`, and exact Forge/Gradle supplemental license copies.
- The release checksum validator covers all nine committed evidence files and
  verifies the external JAR against the content manifest.
- Pull request #3 reports 3/3 successful checks for the tested implementation.
  Documentation checkpoint `d2b571f` also had 3/3 successful last-observed
  checks in Forge run 33277040688 and governance run 33277040675. The latter
  uploaded the 33-file PR-merge review packet as artifact 9721841271; its
  diagnostic binding does not approve G0.

See [`TEST-REPORT.md`](TEST-REPORT.md),
[`checksums.txt`](checksums.txt), and
[`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json).

## Dedicated server

```yaml
evidence_schema_version: 2
harness_session_id: v002-643266d9b1762b0a3e505a45
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
same_world_identity_marker: PASS
canonical_startup_properties_identity: PASS
client_class_linkage_findings: 0
project_error_findings: 0
project_warning_findings: 0
player_join: PENDING
```

Selected lifecycle evidence is under
[`evidence/dedicated-server/`](evidence/dedicated-server/). The retained local
session is ignored and contains full installer/runtime logs plus its disposable
world. It is a headless baseline only; the post-provenance-approval packaged-
client procedure creates a fresh isolated player session.

## Manual tests

See [`MANUAL-TEST.md`](MANUAL-TEST.md). No packaged-client/manual PASS is
claimed. ForgeGradle `runClient` output is diagnostic-only and cannot satisfy
G4 or G8. The schema-4 collector can bind distinct matching and missing-project-
mod profiles, exact/empty mod inventories, ordered before/after snapshots, and
profile-local raw logs, but no such external-machine bundle exists yet.

## Provenance

```yaml
official_forge_mdk: 1.20.1-47.4.10
official_gradle_wrapper_source: v8.1.1
forge_license_copy_verified: true
gradle_license_copy_verified: true
third_party_notices_packaged: true
machine_readable_manifest_verified: true
mechanical_g0_evidence_verified: true
provenance_schema_version: 3
mechanical_review_state: EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING
declared_new_upstream_ar_code_or_assets: 0
full_source_resource_review: PENDING_HUMAN_REVIEW
new_original_assets: 1 bootstrap logo
generated_assets: 1 GameTest structure
current_rendered_readme_screenshot: false
review_status: EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING
```

The Forge/Gradle packet is only the first G0 subreview. Final G0 acceptance must
also review the complete distributable source/resource inventory and its
relevant history against one exact post-rebuild implementation commit. Record
that independent result here rather than inferring originality from the packet
or from hash equality:

```yaml
final_g0_source_resource_review:
  outcome: PENDING_HUMAN_REVIEW
  selected_implementation_commit: null
  selected_tree_oid: null
  review_inputs_report: null
  review_inputs_report_sha256: null
  reviewer: null
  reviewed_at: null
  findings: []
final_g0_readme_visual_review:
  outcome: PENDING_HUMAN_REVIEW
  selected_commit: null
  selected_tree_oid: null
  screenshot_file: null
  screenshot_sha256: null
  reviewer: null
  reviewed_at: null
  findings: []
```

The final input procedure generates one exact-Git report binding the complete
v0.0.2 distributable source/resource/legal-file scope, its history/change
inventory from the
v0.0.1 base (`86b9db01b1cb4c8b8f673590baf1dc185d1716b3`), the approved bootstrap
coverage, and the complete main/sources JAR manifests:

```text
python -I -S -c "from pathlib import Path; Path('build').mkdir(exist_ok=True)"
python -I -S scripts/prepare_v002_final_g0_review_inputs.py generate --commit HEAD --output build/v0.0.2-final-g0-review-inputs
python -I -S scripts/prepare_v002_final_g0_review_inputs.py verify --commit HEAD --output build/v0.0.2-final-g0-review-inputs
```

The output directory is create-once. Blocking governance CI independently
generates and uploads the same report as
`v0.0.2-final-g0-review-inputs-<selected-commit>`. Pull-request runs bind that
artifact to the immutable PR head SHA, not the synthetic merge SHA; push runs
bind it to the pushed SHA. Before review, compare both the selected commit and
the downloaded/local report SHA-256 values. The report organizes
review inputs; it does not make the human determination, and hashes do not
replace inspection of every listed Git blob and relevant history entry.

When recording `APPROVED` or `CHANGES_REQUIRED`, preserve the verified bytes at
`docs/releases/v0.0.2/evidence/g0-final/<selected-commit>/final-g0-review-inputs.json`
and put that tracked repository-relative path plus its SHA-256 in the record.
The report path remains `null` while the review is pending. A later replacement
review uses a new commit-named directory; an earlier changes-required report is
not overwritten or relabeled.

These are the only valid record states:

- `PENDING_HUMAN_REVIEW`: all commit/tree/report-or-screenshot/reviewer/date
  fields are `null`, and `findings` is empty.
- `APPROVED`: the commit and tree are lowercase 40-hex values. The source review
  report is a tracked regular file at the exact commit-named path above; the
  README screenshot path is repository-relative. Each SHA-256 is lowercase
  64-hex, the reviewer is nonempty, `reviewed_at` is `YYYY-MM-DD`, and the
  referenced input has been verified for that exact commit. The source/resource reviewer has
  determined that no unreviewed upstream, community-fork, Minecraft/Mojang, or
  otherwise unlicensed code/resource remains within that distributable scope.
  The README reviewer has inspected
  the full rendered window and found the required identity/non-affiliation text
  visible with no private pixels.
- `CHANGES_REQUIRED`: the same binding, tracked-report, reviewer, and date fields
  are present and `findings` contains at least one factual correction. G0
  remains open; preserve this report and decision, then regenerate and review a
  new exact-commit input after correction.

The selected tree must equal `git rev-parse <selected-commit>^{tree}`. G0 may be
recommended for `PASS` only when the bootstrap provenance record is approved
and both final-G0 records above are `APPROVED`. A later documentation-only
evidence commit may cite the reviewed implementation, but any code, resource,
provenance scope, README, screenshot, or packaged-byte change invalidates its
affected record and requires a new review.

The human-readable record is
[`../../provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md`](../../provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md),
the machine-readable input manifest is
[`../../provenance/v0.0.2-bootstrap-inputs.json`](../../provenance/v0.0.2-bootstrap-inputs.json),
and generated mechanical evidence is under
[`evidence/g0-mechanical/`](evidence/g0-mechanical/). They deliberately do not
claim final legal/provenance approval, establish full-repository originality,
or replace either final-G0 review above.
`scripts/prepare_v002_g0_review_packet.py` can copy the exact committed inputs
into a deterministic ignored review packet and verify them against Git objects.
The current pending packet labels its content digest
`PENDING_CONTENT_DIAGNOSTIC_ONLY`. A packet from an already approved commit
instead observes its previously recorded valid approval binding; the tool never
writes reviewer answers or approval state in either case.
`scripts/prepare_v002_final_g0_review_inputs.py` independently inventories the
final source/resource/legal scope and relevant history from exact Git objects.
It likewise records only bounded mechanical inputs and never writes a reviewer
answer or Gate status.

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
  - G0 human provenance/license subreview is incomplete before the final rebuild
  - G0 full source/resource inventory-history review is absent
  - G0 post-rebuild rendered README screenshot and human visual review are absent
  - G4 matching-client join, disconnect, restart, and reconnect evidence is absent
  - G4 missing-project-mod behavior and proposed N/A decisions are unreviewed
  - G8 packaged-client Mods screen and world-start evidence is absent
  - G9 human release acceptance is incomplete
human_approved_by: ""
human_approved_at: ""
```

No GitHub Release is required before the Gate review can finish. If a release is
created after explicit human acceptance, it must be classified as a pre-release
and must not be presented as stable.
