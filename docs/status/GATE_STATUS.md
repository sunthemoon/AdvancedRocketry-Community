# GATE_STATUS

```yaml
version: v1.0.0
status: IN_PROGRESS
build: "1.20.1-1.0.0-dev"
branch: "codex/v1.0.0-stable-core"
base_commit: "34b2e99b48a33f4ba8905b6a69a38efee1649d3f"
tested_implementation_commit: ""
artifact_sha256: ""
provenance_review: IN_PROGRESS
gates:
  G0: IN_PROGRESS
  G1: IN_PROGRESS
  G2: IN_PROGRESS
  G3: IN_PROGRESS
  G4: IN_PROGRESS
  G5: IN_PROGRESS
  G6: IN_PROGRESS
  G7: NOT_STARTED
  G8: IN_PROGRESS
  G9: IN_PROGRESS
overall: IN_PROGRESS
remaining_items:
  - Candidate-bound automated and packaged lifecycle evidence
  - Final-candidate Beta-world upgrade rerun and passenger recovery
  - Candidate-bound hosted CI, retained handshake-timeout review and final-candidate compatibility
  - Four-hour reference workload (not executed; execution deferred by owner)
  - Fresh real-GPU and two-client gameplay acceptance
  - Independent review, installation test and human release approval
human_approved_by: ""
human_approved_at: ""
```

No v1.0 Required Gate is approved by this development status update. The
[accepted v0.9.0 Gate](../releases/v0.9.0/GATE-STATUS.md) is preserved separately;
its version-limited visual exception is not extended. Test results and their
scope are tracked in the [implementation log](../work/v1.0.0-implementation-log.md).
The [development release handoff](../releases/v1.0.0/RELEASE-EVIDENCE.md)
maps existing artifact-specific results and missing candidate acceptance;
G9 work has started, but no candidate identity or approval is assigned.
G8 includes scoped same-artifact real-client reconnect observations with
explicit debugger-controlled readiness; full candidate visual/player-flow
acceptance is still incomplete. See the
[native queue report](../work/v1.0.0-native-reconnect/VERIFICATION.md).

On 2026-09-06 the owner prioritized complete implementation and short defect
regressions over long-load testing, and requested no SSH environment setup.
This changes execution priority, not the Required Gates or release approval.
