# GATE_STATUS

```yaml
version: v0.1.0
status: IN_PROGRESS
tested_implementation_commit: "ccae3a79242a1901daed0cadf0c15bb058f89c0b"
base_commit: "8877ea2cbb84a45615fe653e15a8bd0214814d3e"
branch: "codex/v0.1.0-asset-registry-baseline"
build: "1.20.1-0.1.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
gates:
  G0: PASS
  G1: IN_PROGRESS
  G2: PASS
  G3: PASS
  G4: PASS
  G5: NOT_APPLICABLE
  G6: NOT_APPLICABLE
  G7: NOT_APPLICABLE
  G8: PASS
  G9: PASS
overall: IN_PROGRESS
remaining_items: [blocking_ci, pr_merge]
human_approved_by: "sunthemoon"
human_approved_at: "2026-08-30"
```

The exact upstream audit, ten-target provenance review, generated resources,
packaged client, matching-client dedicated-server restart, release evidence,
and owner review are complete. G1 remains open until the exact pull-request
head passes the blocking GitHub Actions checks; the version therefore remains
`IN_PROGRESS` and is not represented as accepted from local evidence alone.

The accepted v0.0.2 snapshot is archived at
[`../releases/v0.0.2/GATE-STATUS.md`](../releases/v0.0.2/GATE-STATUS.md).
