# GATE_STATUS

```yaml
version: v0.0.2
status: IN_PROGRESS
tested_implementation_commit: "0fa080fdff3ab025c6b764b02d2d07fa9221c5ae"
base_commit: "86b9db01b1cb4c8b8f673590baf1dc185d1716b3"
branch: "codex/v0.0.2-forge-bootstrap"
build: "1.20.1-0.0.2-dev"
artifact_sha256: "58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944"
sources_artifact_sha256: "2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293"
linux_artifact_sha256: "58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944"
cross_platform_byte_identity: PASS
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/3"
tested_implementation_pull_request_checks: "3/3 PASS"
tested_implementation_forge_workflow_run: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33258532863"
tested_implementation_governance_workflow_run: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33258532838"
documentation_checkpoint: "12375d178cb2c06b09cdf3f196cc6c609607a9ba"
last_observed_checkpoint_pull_request_checks: "3/3 PASS"
last_observed_checkpoint_forge_workflow_run: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33100160962"
last_observed_checkpoint_governance_workflow_run: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33100161000"
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
  - "Packaged-client Mods screen and disposable world evidence"
  - "Three-way JAR hash equality and matching-client join/disconnect/restart/reconnect"
  - "Missing-project-mod behavior and human decisions for proposed G4 N/A cases"
  - "Human release acceptance"
human_approved_by: ""
human_approved_at: ""
```

`PASS` above records evidence-backed automated Gates. Runs 33258532863 and
33258532838 are the tested-implementation CI; runs 33100160962 and 33100161000
are the last observed CI for documentation checkpoint `12375d1`. This does not
mark the version `PASSED`. G0 retains rendered README and human review work, and
no Required Gate is waived. A GitHub Release is not required before acceptance;
if one is created after human acceptance, it must be classified as a
pre-release rather than a stable release.

G0 review is the next ordering dependency: approving its packaged third-party
notice changes JAR bytes. Rebuild, refresh artifact evidence, and obtain CI for
that exact commit before collecting any packaged-client evidence.
