# GATE_STATUS

```yaml
version: v0.5.0
status: IN_PROGRESS
tested_implementation_commit: ""
base_commit: "9c270e29673b97fc78dc73024c58d623b5869c48"
branch: "codex/v0.5.0-rocket-snapshot"
build: "1.20.1-0.5.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: IN_PROGRESS
pull_request: ""
forge_ci: ""
governance_ci: ""
pull_request_checks: NOT_STARTED
gates:
  G0: IN_PROGRESS
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
  - bounded rocket snapshot and server-recomputed statistics
  - loaded-only structure scan and BlockEntity allowlist
  - transactional assembly/disassembly with rollback and region locking
  - persistent thin RocketEntity and bounded visual synchronization
  - automated, dedicated-server, performance, multiplayer, and manual evidence
human_approved_by: ""
human_approved_at: ""
```

The accepted v0.4.0 record remains immutable at
[`../releases/v0.4.0/GATE-STATUS.md`](../releases/v0.4.0/GATE-STATUS.md). v0.5
uses the repository-approved 2,048-block / 32,768-volume / 1 MiB snapshot
limits and rejects every BlockEntity without an explicit bounded adapter.
