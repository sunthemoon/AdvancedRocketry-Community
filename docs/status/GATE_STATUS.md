# GATE_STATUS

```yaml
version: v0.6.0
status: IN_PROGRESS
base_commit: "b349789c9d330da782c8e719f57ff09d8a262e7f"
branch: "codex/v0.6.0-earth-moon-roundtrip"
build: "1.20.1-0.6.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
cross_dimension_adr: "docs/decisions/ADR-006-ROCKET-CROSS-DIMENSION-TRANSFER.md"
gates:
  G0: PASS
  G1: NOT_STARTED
  G2: NOT_STARTED
  G3: NOT_STARTED
  G4: NOT_STARTED
  G5: NOT_STARTED
  G6: NOT_STARTED
  G7: NOT_STARTED
  G8: NOT_STARTED
  G9: NOT_STARTED
overall: IN_PROGRESS
remaining_items:
  - implement and verify the fuel, flight, transfer, passenger, and landing slices
  - complete 20 packaged round trips and the restart/recovery matrix
  - capture visible two-player evidence and bind PR/CI acceptance
g0_approved_by: "sunthemoon"
g0_approved_at: "2026-09-01"
```

The immutable accepted v0.5.0 record is
[`../releases/v0.5.0/GATE-STATUS.md`](../releases/v0.5.0/GATE-STATUS.md). v0.6
adds no imported upstream assets at preparation time, so the repository owner
has allowed G0 to carry forward. All behavior, persistence, security,
performance, client, and release Gates remain unresolved until evidence exists.
