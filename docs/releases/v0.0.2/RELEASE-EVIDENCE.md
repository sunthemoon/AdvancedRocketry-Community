# RELEASE-EVIDENCE — v0.0.2

## Identity

```yaml
version: v0.0.2
status: PASSED
build: 1.20.1-0.0.2-dev
tested_implementation_commit: d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b
tag: NOT_CREATED
release: NOT_CREATED
release_publication: NOT_CREATED
required_classification_if_created: PRE_RELEASE
pull_request: https://github.com/sunthemoon/AdvancedRocketry-Community/pull/3
pull_request_status: MERGED
accepted_merge_commit: b8ec149284a14d174f60f09f236ac36c515fd4c5
tested_implementation_forge_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33302877815
tested_implementation_governance_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33302877802
documentation_checkpoint: db9ce96113712dd93e8db05736b3a9ed764e41a8
acceptance_evidence_source_commit: cf476b9601fc482977d1716617c87e4b2cbf704f
last_observed_checkpoint_forge_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33308011345
last_observed_checkpoint_governance_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33308011373
checkpoint_forge_artifact_id: 9729573591
checkpoint_g0_review_packet_artifact_id: 9729539499
checkpoint_g0_review_packet_commit: d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b
checkpoint_g0_review_packet_manifest_sha256: b7df039f085862a0c2aa353549943c537d1183524cf4c3f12a676a0f86647d3b
checkpoint_final_g0_review_inputs_artifact_id: 9729539691
checkpoint_final_g0_review_inputs_commit: d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b
checkpoint_final_g0_review_inputs_sha256: 7b8aef7c8308f0896fc50bd29c7285342de24f93def3c62d6cb87f310e659623
minecraft: 1.20.1
forge_baseline: 47.4.10
forge_compat_lane: 47.4.23
java: 17.0.8
gradle: 8.8
built_at: 2026-08-30
built_by: Codex-assisted local development
jar_sha256: cd5ae579bae1bc21c1f67df2c3e00f196e0ee4a9ead01653c926b88ca37f32ad
sources_jar_sha256: f958f4334e8f95062a6ed15257fb9c5d940759490f3dc335c70e2764f1acacbe
linux_jar_sha256: cd5ae579bae1bc21c1f67df2c3e00f196e0ee4a9ead01653c926b88ca37f32ad
linux_sources_jar_sha256: f958f4334e8f95062a6ed15257fb9c5d940759490f3dc335c70e2764f1acacbe
content_manifest_sha256: 1384c9c47c9b4e40d1ae8d670689bd14101458422c4452728ac2a2abcc6bf80f
linux_ci_artifact_id: 9729573591
tested_implementation_pull_request_checks: 3/3_PASS
last_observed_checkpoint_pull_request_checks: 3/3_PASS
```

## Gate summary

| Gate | Status | Evidence |
|---|---|---|
| G0 Identity/License/Provenance | PASS | Digest-bound bootstrap approval, rebuilt notice-bearing JARs, exact 18-file inventory/history review, and 1440x5000 rendered README visual review are approved without findings |
| G1 Reproducible Build | PASS | Repeated Windows clean builds and tested-implementation Linux CI produced byte-identical main JARs, sources JARs, and content manifests |
| G2 Data/Assets | PASS | DataGen passed after one retained network retry and left no generated-resource or committed worktree change |
| G3 Automated Behavior | PASS | 3 JUnit, 517 Python, and 1 Forge GameTest pass at exact implementation `d6c8464` |
| G4 Dedicated/Sides | PASS | Matching client joined, disconnected, and reconnected after same-world restart; missing-project-mod behavior was observed; ADR-005 accepts the five bootstrap-only N/A cases |
| G5 Persistence/Recovery | NOT_APPLICABLE | No project persistent data in v0.0.2 |
| G6 Security/Authority | NOT_APPLICABLE | No project packets or gameplay authority in v0.0.2 |
| G7 Performance | NOT_APPLICABLE | No gameplay loop, ticking service, or world scan in v0.0.2 |
| G8 Manual Flow | PASS | Strict schema-5 bundle contains privacy-reviewed Mods/world/join/reconnect/missing-mod screenshots, logs, profile inventories, and exact JAR bindings; owner approved 2026-08-30 |
| G9 Docs/Release | PASS | Changelog, installation/save boundary, known issues, evidence, checksums, CI, and explicit owner acceptance are complete; no stable-release claim is made |

No Required Gate is treated as waived. Owner `sunthemoon` accepted five
version-scoped G4 `NOT_APPLICABLE` classifications for bootstrap-only
synchronization, two-player, chunk-unload, configuration-mismatch, and optional-
client-dependency cases. Those decisions do not replace the completed matching-
client checks.
G0 is approved at the exact post-rebuild bindings below. Client evidence must
use the `cd5ae579...` main JAR selected by implementation `d6c8464`; changing
any packaged byte, reviewed source/resource, or `README.md` invalidates the
affected binding rather than silently reusing this approval.

## Earlier artifact-baseline commands

The following commands belong to the superseded `0fa080f` artifact baseline.
They remain historical evidence; the current post-approval bytes and validation
are recorded after the `7441cd2` acceptance-hardening checkpoint below.

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

## Post-rebuild and final-G0 checkpoint

Implementation `d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b` is the selected
post-provenance-approval tree. Two local Java 17 clean builds, the downloaded
Linux Forge artifact 9729573591, and the committed manifests agree on main JAR
SHA-256 `cd5ae579...`, sources JAR SHA-256 `f958f433...`, and content-manifest
SHA-256 `1384c9c4...`. Forge run 33302877815 and governance run 33302877802
provide 3/3 successful pull-request checks; governance ran 517/517 Python tests.

The final-G0 source review is bound to normal PR #3 merge commit
`b8ec149284a14d174f60f09f236ac36c515fd4c5`. Its tree is byte-identical to the
accepted PR head; the exact-Git report has SHA-256 `835dc8c9...`, inventories 18
distributable source/resource/legal files, and covers 11 bootstrap targets, 34
commits, and 487 path changes. Its one exact-blob lineage entry is the intentional
copy of the already approved README screenshot into canonical client evidence,
not a source/resource import. Reviewer `sunthemoon` retained approval on
2026-08-30 with no findings. The complete 1440x5000 rendered README visual review
was originally approved at `d6c8464`; the README blob and screenshot are byte-
identical at `b8ec149`, where the canonical copy records the normal-merge
topology without changing the reviewed pixels. These human decisions, not the
input hashes alone, support G0 `PASS`.

## Artifact and automated tests

- Current tested post-provenance-approval artifact:
  `advancedrocketry-community-1.20.1-0.0.2-dev.jar`.
- SHA-256:
  `cd5ae579bae1bc21c1f67df2c3e00f196e0ee4a9ead01653c926b88ca37f32ad`.
- Sources JAR SHA-256:
  `f958f4334e8f95062a6ed15257fb9c5d940759490f3dc335c70e2764f1acacbe`.
- Two same-environment clean builds produced identical bytes.
- Tested-implementation Forge run 33302877815's Linux upload matches the
  Windows main JAR (`cd5ae579...`), sources JAR (`f958f433...`), and content
  manifest (`1384c9c4...`) byte-for-byte.
- The content manifest records 34 sorted entries with per-entry size and hash.
- The artifact contains byte-identical project LICENSE/NOTICE,
  `THIRD-PARTY-NOTICES.md`, and exact Forge/Gradle supplemental license copies.
- The release checksum validator covers all 36 committed evidence files and
  verifies the external JAR against the content manifest.
- Pull request #3 reports 3/3 successful checks for exact tested head `d6c8464`
  in Forge run 33302877815 and governance run 33302877802. Governance bound the
  immutable pull-request head to 35 exact-Git input
  payloads plus generated review instructions (36 payloads and 37 total files)
  in packet artifact 9729539499. It also uploaded exact-Git final-G0 input
  report artifact 9729539691, covering 18 distributable source/resource/legal
  files, 11 bootstrap targets, 27 commits, and 319 path changes. The downloaded
  CI artifacts were byte-identical to the locally verified packet and report;
  the separately recorded human review supplies the decision.

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
player_join: PASS
matching_client_reconnect: PASS
source_server_client_jar_hash_equal: true
```

Selected lifecycle evidence is under
[`evidence/dedicated-server/`](evidence/dedicated-server/). The retained local
session is ignored and contains full installer/runtime logs plus its disposable
world. That committed directory is the historical pre-approval headless
baseline. Exact implementation run 33302877815 independently repeated the same
two-cycle smoke with the current `cd5ae579...` JAR, Java 17.0.20.1, zero
project errors/warnings, and zero client-class linkage findings. The packaged-
client procedure creates a fresh isolated player session.

## Manual tests

See [`MANUAL-TEST.md`](MANUAL-TEST.md) and the canonical
[`evidence/client/`](evidence/client/) bundle. Schema-5 record SHA-256
`8d5cab6e...` is `READY_FOR_HUMAN_GATE_REVIEW` and binds source commit
`cf476b9`, exact/empty profile inventories, three-way `cd5ae579...` JAR equality,
Mods/world screenshots, two matching-client cycles, and the missing-project-mod
attempt. The latter displayed a red incompatibility marker but accepted the
connection; the evidence preserves both facts. Owner `sunthemoon` approved G8
and G9 on 2026-08-30. ForgeGradle `runClient` remains diagnostic-only.

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
mechanical_review_state: THIRD_PARTY_APPROVED
declared_new_upstream_ar_code_or_assets: 0
full_source_resource_review: APPROVED
new_original_assets: 1 bootstrap logo
generated_assets: 1 GameTest structure
current_rendered_readme_screenshot: true
review_status: THIRD_PARTY_APPROVED
```

The digest-bound Forge/Gradle subreview and both final G0 decisions are complete.
The duplicate-key-rejecting JSON record below preserves the exact commit/tree,
report/screenshot hashes, reviewer, date, and findings. Mechanical validation
checks those bindings but did not create or infer the human outcomes.

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
    "selected_implementation_commit": "b8ec149284a14d174f60f09f236ac36c515fd4c5",
    "selected_tree_oid": "29822b546ea43ddd8c19f612008223e06d4de7d5",
    "review_inputs_report": "docs/releases/v0.0.2/evidence/g0-final/b8ec149284a14d174f60f09f236ac36c515fd4c5/final-g0-review-inputs.json",
    "review_inputs_report_sha256": "835dc8c9efe2e5413f5ca170218b447497e8d59ac5432c34ac5b5ef7c7e63002",
    "reviewer": "sunthemoon",
    "reviewed_at": "2026-08-30",
    "findings": []
  },
  "final_g0_readme_visual_review": {
    "outcome": "APPROVED",
    "selected_commit": "b8ec149284a14d174f60f09f236ac36c515fd4c5",
    "selected_tree_oid": "29822b546ea43ddd8c19f612008223e06d4de7d5",
    "screenshot_file": "docs/releases/v0.0.2/evidence/g0-final/b8ec149284a14d174f60f09f236ac36c515fd4c5/readme-full-window.png",
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
recommended_status: PASSED
blocking_reasons: []
human_approved_by: "sunthemoon"
human_approved_at: "2026-08-30"
```

No GitHub Release is required for the completed Gate review. If a release is
created, it must be classified as a pre-release and must not be presented as
stable.
