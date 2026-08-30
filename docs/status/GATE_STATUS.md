# GATE_STATUS

```yaml
version: v0.0.2
status: IN_PROGRESS
tested_implementation_commit: "7441cd245251040ef2b1629257be978b4796fe0e"
base_commit: "86b9db01b1cb4c8b8f673590baf1dc185d1716b3"
branch: "codex/v0.0.2-forge-bootstrap"
build: "1.20.1-0.0.2-dev"
artifact_sha256: "58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944"
sources_artifact_sha256: "2e18a57345583d1541ef169c0364929711e579b03e7dffde97bff878de834293"
linux_artifact_sha256: "58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944"
cross_platform_byte_identity: PASS
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/3"
tested_implementation_pull_request_checks: "3/3 PASS"
tested_implementation_forge_workflow_run: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732867"
tested_implementation_governance_workflow_run: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732862"
documentation_checkpoint: "7441cd245251040ef2b1629257be978b4796fe0e"
last_observed_checkpoint_pull_request_checks: "3/3 PASS"
last_observed_checkpoint_forge_workflow_run: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732867"
last_observed_checkpoint_governance_workflow_run: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33293732862"
checkpoint_forge_artifact_id: 9726838947
checkpoint_g0_review_packet_artifact_id: 9726778456
checkpoint_g0_review_packet_manifest_sha256: "395c753fc9d723aa358e27dfaca182bd906fa830bb820ad013a95c95e869f795"
checkpoint_final_g0_review_inputs_artifact_id: 9726778602
checkpoint_final_g0_review_inputs_sha256: "9f87d1a82a7d5b8583c6be3cfb2548c9d9018143a9f64407a0232165ec8cd023"
latest_strict_validation: "15 PASS / 3 PENDING / 0 WARN / 0 FAIL"
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
  - "Human Forge/Gradle provenance/license subreview before the final rebuild"
  - "Final G0 full source/resource inventory-history review and rendered README visual review"
  - "Packaged-client Mods screen and disposable world evidence"
  - "Three-way JAR hash equality and matching-client join/disconnect/restart/reconnect"
  - "Missing-project-mod behavior and human decisions for proposed G4 N/A cases"
  - "Human release acceptance"
human_approved_by: ""
human_approved_at: ""
```

`PASS` above records evidence-backed automated Gates. Runs 33293732867 and
33293732862 are the exact-head CI for hardened checkpoint `7441cd2` and all
three pull-request checks passed. The governance run uploaded exact-head packet
and final-G0 input-report artifacts,
but neither mechanical artifact contains a human decision. This does not mark
the version `PASSED`. G0 retains rendered README and human review work, and no
Required Gate is waived. A GitHub Release is not required before acceptance; if
one is created after human acceptance, it must be classified as a pre-release
rather than a stable release.

Strict validation reports three explicit pending states: both final-G0 human
records are pending, no canonical packaged-client evidence bundle exists, and
ADR-005 remains `PROPOSED`. The validator exits successfully because the
repository state is internally valid, not because those acceptance items pass.

The G0 provenance/license subreview is the next ordering dependency: approving
its packaged third-party notice changes JAR bytes. Rebuild, refresh artifact
evidence, and obtain CI for that exact commit before the full source/resource
review. That review must be `APPROVED` before collecting packaged-client and
rendered README evidence. Neither subreview alone is final G0 `PASS`; G0 remains
`IN_PROGRESS` until the rendered README visual review also passes. Governance CI now generates a bounded,
exact-Git final-G0 source/resource review-input artifact for each commit; that
mechanical input neither supplies nor predicts the human outcome.
