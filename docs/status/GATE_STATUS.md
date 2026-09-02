# GATE_STATUS

```yaml
version: v0.8.0
status: READY_FOR_AUDIT
tested_implementation_commit: "a3b4192d37c524687a0a26bf12d075a8ec6c1e99"
build: "1.20.1-0.8.0-dev"
branch: "codex/v0.8.0-progression-satellites"
base_commit: "6b33eaf0be1a01b7feceefe785d8fba7e9717b02"
merge_commit: ""
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: ""
forge_ci: ""
governance_ci: ""
pull_request_checks: PENDING
accepted_exception: "docs/decisions/ADR-011-V080-VISUAL-EVIDENCE-SEQUENCE.md"
gates:
  G0: PASS
  G1: PASS
  G2: PASS
  G3: PASS
  G4: PASS
  G5: PASS
  G6: PASS
  G7: PASS
  G8: PASS
  G9: PASS
overall: READY_FOR_AUDIT
remaining_items:
  - merge the reviewed pull request after required checks pass
  - reproduce the exact candidate artifact from the merge commit
human_approved_by: "sunthemoon"
human_approved_at: "2026-09-03"
```

Repository owner `sunthemoon` approved G0, G8, and G9 on 2026-09-03. G8 is
bound to the ordered terminal screenshots, final-candidate two-client
screenshots, packaged logs, authority matrix, and ADR-011. The release remains
`READY_FOR_AUDIT`, not `PASSED`, until PR checks, merge identity, and exact
post-merge JAR reproduction are recorded.
