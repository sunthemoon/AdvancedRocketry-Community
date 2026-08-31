# GATE_STATUS

```yaml
version: v0.4.0
status: PASSED
tested_implementation_commit: "f880870aa4db0a46758dcc8615dfa2c16b2e3b59"
base_commit: "93755bc2e89a788d5e1a9bad11fe535e4743333d"
branch: "codex/v0.4.0-atmosphere-life-support"
build: "1.20.1-0.4.0-dev"
upstream_commit: "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
provenance_review: APPROVED
pull_request: "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/8"
forge_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33421129833"
governance_ci: "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33421129566"
pull_request_checks: 3/3_PASS
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
overall: PASSED
remaining_items: []
human_approved_by: "sunthemoon"
human_approved_at: "2026-09-01"
```

The exact candidate JAR is 466,433 bytes with SHA-256
`05279656dfae21f682ca45a000517628dfcf706ebc4cce9ce2fe16e0723e96f1`.
Two clean rebuilds reproduced those bytes. Two packaged clients, same-world
restart/rejoin, the five-minute 16-Vent server scenario, and all local technical
checks passed. Owner `sunthemoon` approved G0, G8, and G9 on 2026-09-01.

PR technical checkpoint `d3a13642c3e21d6b9868b1a9e746935db17ceba9`
passed all three checks, including the full 596-test governance inventory and
the five-minute packaged atmosphere server lane. All ten Required Gates are
`PASS`. The accepted v0.3.0 snapshot remains archived at
[`../releases/v0.3.0/GATE-STATUS.md`](../releases/v0.3.0/GATE-STATUS.md).
