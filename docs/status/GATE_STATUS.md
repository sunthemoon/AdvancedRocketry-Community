# GATE_STATUS

```yaml
version: v0.0.2
status: IN_PROGRESS
tested_implementation_commit: "7567dbb60332526789ee3b2824c582ff1909203e"
base_commit: "86b9db01b1cb4c8b8f673590baf1dc185d1716b3"
branch: "codex/v0.0.2-forge-bootstrap"
build: "1.20.1-0.0.2-dev"
artifact_sha256: "58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944"
sources_artifact_sha256: "2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293"
linux_artifact_sha256: ""
cross_platform_byte_identity: PENDING_CURRENT_HEAD_CI
pull_request_checks: "PENDING_CURRENT_HEAD_CI"
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/3"
forge_workflow_run: ""
governance_workflow_run: ""
release_publication: NOT_CREATED
required_classification_if_created: PRE_RELEASE
gates:
  G0: IN_PROGRESS
  G1: PASS
  G2: PASS
  G3: PASS
  G4: IN_PROGRESS
  G5: NOT_APPLICABLE
  G6: NOT_APPLICABLE
  G7: NOT_APPLICABLE
  G8: NOT_STARTED
  G9: IN_PROGRESS
overall: IN_PROGRESS
remaining_items:
  - "Current rendered README screenshot and human Forge/Gradle provenance/license review"
  - "Current-head CI and Linux artifact/content-manifest comparison"
  - "Packaged-client Mods screen and disposable world evidence"
  - "Three-way JAR hash equality and matching-client join/disconnect/restart/reconnect"
  - "Missing-project-mod behavior and human decisions for proposed G4 N/A cases"
  - "Human release acceptance"
human_approved_by: ""
human_approved_at: ""
```

`PASS` above records only locally evidence-backed automated Gates. It does not
mark the version `PASSED`; current-head CI is still pending. G0 retains rendered
README and human review work, and no Required Gate is waived. A GitHub Release
is not required before acceptance; if one is created after human acceptance, it
must be classified as a pre-release rather than a stable release.
