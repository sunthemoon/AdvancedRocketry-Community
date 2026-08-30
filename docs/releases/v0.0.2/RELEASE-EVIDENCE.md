# RELEASE-EVIDENCE — v0.0.2

## Identity

```yaml
version: v0.0.2
status: IN_PROGRESS
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 7441cd245251040ef2b1629257be978b4796fe0e
tag: NOT_CREATED
release: NOT_CREATED
release_publication: NOT_CREATED
required_classification_if_created: PRE_RELEASE
pull_request: https://github.com/sunthemoon/AdvancedRocketry-Community/pull/3
tested_implementation_forge_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732867
tested_implementation_governance_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732862
documentation_checkpoint: 7441cd245251040ef2b1629257be978b4796fe0e
last_observed_checkpoint_forge_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732867
last_observed_checkpoint_governance_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732862
checkpoint_forge_artifact_id: 9726838947
checkpoint_g0_review_packet_artifact_id: 9726778456
checkpoint_g0_review_packet_commit: 7441cd245251040ef2b1629257be978b4796fe0e
checkpoint_g0_review_packet_manifest_sha256: 395c753fc9d723aa358e27dfaca182bd906fa830bb820ad013a95c95e869f795
checkpoint_final_g0_review_inputs_artifact_id: 9726778602
checkpoint_final_g0_review_inputs_commit: 7441cd245251040ef2b1629257be978b4796fe0e
checkpoint_final_g0_review_inputs_sha256: 9f87d1a82a7d5b8583c6be3cfb2548c9d9018143a9f64407a0232165ec8cd023
minecraft: 1.20.1
forge_baseline: 47.4.10
forge_compat_lane: 47.4.23
java: 17.0.8
gradle: 8.8
built_at: 2026-08-30
built_by: Codex-assisted local development
jar_sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
sources_jar_sha256: 2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293
linux_jar_sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
linux_sources_jar_sha256: 2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293
content_manifest_sha256: a5128fffaca624155a00b8a60bdc6eb3f7c3451b97414cfe9935dbe7408d3cd5
linux_ci_artifact_id: 9726838947
tested_implementation_pull_request_checks: 3/3_PASS
last_observed_checkpoint_pull_request_checks: 3/3_PASS
```

## Gate summary

| Gate | Status | Evidence |
|---|---|---|
| G0 Identity/License/Provenance | IN_PROGRESS | Machine-readable input/target mapping, exact license copies, sources-JAR evidence, JAR packaging, and hash checks exist; the Forge/Gradle subreview, full source/resource inventory-history decision, and rendered README review remain |
| G1 Reproducible Build | PASS | Repeated Windows clean builds and tested-implementation Linux CI produced byte-identical main JARs, sources JARs, and content manifests |
| G2 Data/Assets | PASS | DataGen passed after one retained network retry and left no generated-resource or committed worktree change |
| G3 Automated Behavior | PASS | 3 JUnit, 517 Python, and 1 Forge GameTest pass at exact head `7441cd2` |
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

## Earlier artifact-baseline commands

The following commands belong to the earlier `0fa080f` artifact baseline. The
artifact bytes remain current; the separate acceptance-hardening checkpoint
below records the full validation of `7441cd2`.

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

## Acceptance-hardening checkpoint

The exact committed tree at
`7441cd245251040ef2b1629257be978b4796fe0e` was independently revalidated after
the evidence-contract hardening:

```powershell
$env:JAVA_HOME = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-gamma-snapshot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
python -B -m unittest discover -s tests
.\gradlew.bat clean build --no-daemon --stacktrace
.\gradlew.bat test --rerun-tasks --no-daemon --stacktrace
python scripts/generate_v002_g0_evidence.py verify build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar build/libs/advancedrocketry-community-1.20.1-0.0.2-dev-sources.jar --evidence-dir docs/releases/v0.0.2/evidence/g0-mechanical
python scripts/validate_build_artifact.py build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar --content-manifest build/release-evidence/jar-content-manifest.json
python scripts/validate_release_checksums.py --artifact build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar
python scripts/check_client_imports.py
.\gradlew.bat runData --no-daemon --stacktrace
.\gradlew.bat runGameTestServer --no-daemon --stacktrace
python scripts/run_dedicated_server_smoke.py --evidence-dir build/dedicated-server-smoke/evidence
python -I -S scripts/validate_repository.py --require-approved-identity
python -I -S scripts/validate_v002_final_g0_review.py
python -I -S scripts/validate_v002_g4_applicability.py
python scripts/validate_bootstrap_provenance.py
python -I -S scripts/prepare_v002_final_g0_review_inputs.py generate --commit 7441cd245251040ef2b1629257be978b4796fe0e --output build/v0.0.2-final-g0-review-inputs
python -I -S scripts/prepare_v002_final_g0_review_inputs.py verify --commit 7441cd245251040ef2b1629257be978b4796fe0e --output build/v0.0.2-final-g0-review-inputs
python -I -S scripts/prepare_v002_g0_review_packet.py generate --commit 7441cd245251040ef2b1629257be978b4796fe0e --output build/v0.0.2-g0-review-packet
python -I -S scripts/prepare_v002_g0_review_packet.py verify --commit 7441cd245251040ef2b1629257be978b4796fe0e --packet build/v0.0.2-g0-review-packet
git diff --check
python scripts/check_clean_worktree.py
```

The Python suite passed 517/517. Strict repository validation returned 15 PASS,
3 PENDING, 0 WARN, and 0 FAIL: the pending final-G0 reviews, absent client
bundle, and proposed ADR-005 remain visible acceptance work. The local
final-G0 report is schema 2 `INPUTS_ONLY`, has SHA-256
`9f87d1a82a7d5b8583c6be3cfb2548c9d9018143a9f64407a0232165ec8cd023`,
and covers 18 inventory files, 11 bootstrap targets, 22 commits, and 286 path
changes. The G0 packet manifest SHA-256 is
`395c753fc9d723aa358e27dfaca182bd906fa830bb820ad013a95c95e869f795`.
Neither output is copied into the pending human-review record or treated as G0
approval.

## Artifact and automated tests

- Current tested artifact (pre-provenance-subreview approval):
  `advancedrocketry-community-1.20.1-0.0.2-dev.jar`.
- SHA-256:
  `58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944`.
- Sources JAR SHA-256:
  `2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293`.
- Two same-environment clean builds produced identical bytes.
- Tested-implementation Forge run 33293732867's Linux upload matches the Windows main JAR
  (`58622a5ad...`), sources JAR (`2e18a573...`), and content manifest
  (`a5128fff...`) byte-for-byte.
- The content manifest records 34 sorted entries with per-entry size and hash.
- The artifact contains byte-identical project LICENSE/NOTICE,
  `THIRD-PARTY-NOTICES.md`, and exact Forge/Gradle supplemental license copies.
- The release checksum validator covers all nine committed evidence files and
  verifies the external JAR against the content manifest.
- Pull request #3 reports 3/3 successful checks for exact tested head `7441cd2`
  in Forge run 33293732867 and governance run 33293732862. Governance bound the
  immutable pull-request head to 35 exact-Git input
  payloads plus generated review instructions (36 payloads and 37 total files)
  in packet artifact 9726778456. It also uploaded exact-Git final-G0 input
  report artifact 9726778602, covering 18 distributable source/resource/legal
  files, 11 bootstrap targets, 22 commits, and 286 path changes. The downloaded
  CI artifacts were byte-identical to the locally verified packet and report;
  these mechanical bindings do not approve either G0 review.

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
G4 or G8. The schema-5 collector can bind distinct matching and missing-project-
mod profiles, exact/empty mod inventories, ordered before/after snapshots, and
profile-local raw logs. Acceptance-ready collection also requires an exact-
commit approved final-G0 source/resource review; no such external-machine
bundle exists yet.

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
relevant history against one exact post-rebuild implementation commit. The two
human decisions are stored in the duplicate-key-rejecting JSON record below.
The mechanical validator accepts the current pending state but never supplies
or infers either human outcome.

<!-- v0.0.2-final-g0-review-records:start -->
```json
{
  "schema_version": 1,
  "record_kind": "V0_0_2_FINAL_G0_HUMAN_REVIEW_RECORDS",
  "record_semantics": {
    "mechanical_validation_result": "INPUTS_ONLY",
    "gate_decision": "HUMAN_ONLY",
    "visible_pixel_judgment": "HUMAN_ONLY"
  },
  "final_g0_source_resource_review": {
    "outcome": "APPROVED",
    "selected_implementation_commit": "d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b",
    "selected_tree_oid": "878d0c682d15915ed44804420a21f0220b87bb3d",
    "review_inputs_report": "docs/releases/v0.0.2/evidence/g0-final/d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b/final-g0-review-inputs.json",
    "review_inputs_report_sha256": "7b8aef7c8308f0896fc50bd29c7285342de24f93def3c62d6cb87f310e659623",
    "reviewer": "sunthemoon",
    "reviewed_at": "2026-08-30",
    "findings": []
  },
  "final_g0_readme_visual_review": {
    "outcome": "APPROVED",
    "selected_commit": "d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b",
    "selected_tree_oid": "878d0c682d15915ed44804420a21f0220b87bb3d",
    "screenshot_file": "docs/releases/v0.0.2/evidence/g0-final/d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b/readme-full-window.png",
    "screenshot_sha256": "a89318c28e8ba2a65ab040a91ad6f1e49da2f8484c126d2735ae42e6646a8ebc",
    "reviewer": "sunthemoon",
    "reviewed_at": "2026-08-30",
    "findings": []
  }
}
```
<!-- v0.0.2-final-g0-review-records:end -->

The final source-review input procedure generates one exact-Git report binding
the complete v0.0.2 distributable source/resource/legal-file scope, its
history/change inventory from the v0.0.1 base
(`86b9db01b1cb4c8b8f673590baf1dc185d1716b3`), the approved bootstrap coverage,
and the complete main/sources JAR manifests. Replace `<selected-commit>` with a
lowercase full commit ID; do not use a moving ref for the human review:

```text
python -I -S -c "from pathlib import Path; Path('build').mkdir(exist_ok=True)"
python -I -S scripts/prepare_v002_final_g0_review_inputs.py generate --commit <selected-commit> --output build/v0.0.2-final-g0-review-inputs
python -I -S scripts/prepare_v002_final_g0_review_inputs.py verify --commit <selected-commit> --output build/v0.0.2-final-g0-review-inputs
```

The output directory is create-once. Blocking governance CI independently
generates and uploads the same report as
`v0.0.2-final-g0-review-inputs-<selected-commit>`. Pull-request runs bind that
artifact to the immutable PR head SHA, not the synthetic merge SHA; push runs
bind it to the pushed SHA. Compare the selected commit, tree, and downloaded and
local report SHA-256 values before inspecting every listed Git blob and relevant
history entry. The report retains `INPUTS_ONLY` semantics and cannot make the
human determination.

For a source review with outcome `APPROVED` or `CHANGES_REQUIRED`, preserve the
reconstructed report bytes at
`docs/releases/v0.0.2/evidence/g0-final/<source-selected-commit>/final-g0-review-inputs.json`.
After the human reviewer enters that decision and its bindings above, stage only
the source report and this release-evidence record before worktree validation:

```text
git add -- docs/releases/v0.0.2/evidence/g0-final/<source-selected-commit>/final-g0-review-inputs.json
git add -- docs/releases/v0.0.2/RELEASE-EVIDENCE.md
python -I -S scripts/validate_v002_final_g0_review.py
git commit
python -I -S scripts/validate_v002_final_g0_review.py --record-commit <record-commit>
```

For a README visual review with either non-pending outcome, capture the complete
rendered README window at
`docs/releases/v0.0.2/evidence/g0-final/<readme-selected-commit>/readme-full-window.png`.
The README capture commit may differ from the source-review commit, but its
canonical directory must use its own selected full commit ID. After entering
the README decision and bindings, stage only that screenshot and this record:

```text
git add -- docs/releases/v0.0.2/evidence/g0-final/<readme-selected-commit>/readme-full-window.png
git add -- docs/releases/v0.0.2/RELEASE-EVIDENCE.md
python -I -S scripts/validate_v002_final_g0_review.py
git commit
python -I -S scripts/validate_v002_final_g0_review.py --record-commit <record-commit>
```

Replace every placeholder with its lowercase full commit ID. If both reviews
are recorded in one commit, stage both evidence files and this record before a
single validation and commit sequence. In every case, staging lets the validator
prove that the worktree and stage-0 index bytes agree.

These are the only valid record states:

- `PENDING_HUMAN_REVIEW`: every commit/tree/report-or-screenshot/hash/reviewer/
  date field is `null`, and `findings` is empty.
- `APPROVED`: the applicable commit and tree fields are lowercase full SHA-1
  values, each evidence path is the canonical commit-named path above, each
  SHA-256 is lowercase, the reviewer is nonempty, `reviewed_at` is a real
  `YYYY-MM-DD` date, and `findings` is empty. The source reviewer has inspected
  the exact reconstructed report and determined that the reviewed distributable
  scope contains no unreviewed or unlicensed source/resource. The README
  reviewer has inspected the visible full-window pixels for required identity
  and non-affiliation text and for private information.
- `CHANGES_REQUIRED`: the same commit/tree/evidence/reviewer/date bindings are
  required and `findings` contains at least one nonempty factual correction.
  Preserve that record and evidence; correct the implementation or capture, then
  create and review a new commit-named input rather than relabeling old bytes.

The exact-record validator reconstructs the source report from the selected Git
objects and accepts only report schema 2 with its strict inventory/JAR/history
scope and `APPROVED_PREREQUISITE_OBSERVED` bootstrap prerequisite. It separately
requires the bootstrap provenance record to be `THIRD_PARTY_APPROVED` at that
selected implementation, proves selected-to-record ancestry, and rejects
intervening repository changes except the explicit version-scoped client,
review, checksum, decision, release/test/status, changelog, and handoff outputs
needed to complete the remaining Gates. Code, build, workflow, provenance,
packaged-manifest, validator, schema, test, README, and unrelated evidence
changes remain invalidating. It also validates the canonical
tracked screenshot hash and PNG structure and rejects intervening README
changes. It deliberately does not inspect visible pixels or compute a Gate
decision. G0 may be recommended for `PASS` only after both records are human
`APPROVED`; later invalidating changes require a new review.

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
