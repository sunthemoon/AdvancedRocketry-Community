# GATE_STATUS — v0.8.0 release candidate

```yaml
version: v0.8.0
status: READY_FOR_AUDIT
tested_implementation_commit: "a3b4192d37c524687a0a26bf12d075a8ec6c1e99"
reviewed_head_commit: ""
base_commit: "6b33eaf0be1a01b7feceefe785d8fba7e9717b02"
merge_commit: ""
branch: "codex/v0.8.0-progression-satellites"
build: "1.20.1-0.8.0-dev"
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

Repository owner `sunthemoon` approved the zero-copy provenance inventory and
the G0, G8, and G9 decisions on 2026-09-03. G8 is bound to the ordered terminal
screenshots, final-candidate two-client screenshots, packaged lifecycle logs,
authority matrix, and ADR-011. The terminal sequence is explicitly labeled as
pre-candidate and is not represented as continuous video.

All technical and owner-review Gates are complete. The milestone remains
`READY_FOR_AUDIT`, rather than `PASSED`, until required pull-request checks,
merge identity, and exact post-merge JAR reproduction are recorded.
