# GATE_STATUS

```yaml
version: v0.5.0
status: READY_FOR_AUDIT
tested_implementation_commit: "eae8d9224c708924930b781d7332eb69b6a4bf8d"
base_commit: "9c270e29673b97fc78dc73024c58d623b5869c48"
branch: "codex/v0.5.0-rocket-snapshot"
build: "1.20.1-0.5.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/9"
forge_ci: ""
governance_ci: ""
pull_request_checks: PENDING
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
  - pass pull-request checks and merge the accepted candidate
  - reproduce the accepted JAR after merge
human_approved_by: "sunthemoon"
human_approved_at: "2026-09-01"
```

The accepted v0.4.0 record remains immutable at
[`../releases/v0.4.0/GATE-STATUS.md`](../releases/v0.4.0/GATE-STATUS.md). v0.5
uses the repository-approved 2,048-block / 32,768-volume / 1 MiB snapshot
limits and rejects every BlockEntity without an explicit bounded adapter. The
local evidence bundle and owner review are complete; `PASSED` remains reserved
for the post-CI, post-merge accepted snapshot.
