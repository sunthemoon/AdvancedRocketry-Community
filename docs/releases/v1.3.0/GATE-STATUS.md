# v1.3.0 Gate status

```yaml
version: v1.3.0
status: IN_PROGRESS
build: 1.20.1-1.3.0-dev
branch: codex/v1.3.0-public-api
development_commit: 884908ebfb936402bcc7a437791f8d9683c27506
candidate_commit: ""
candidate_jar_sha256: ""
required_gates: [G0, G1, G2, G3, G4, G5, G6, G7, G8, G9]
gates:
  G0: IN_PROGRESS
  G1: IN_PROGRESS
  G2: IN_PROGRESS
  G3: IN_PROGRESS
  G4: IN_PROGRESS
  G5: IN_PROGRESS
  G6: IN_PROGRESS
  G7: NOT_STARTED
  G8: NOT_STARTED
  G9: IN_PROGRESS
overall: IN_PROGRESS
independent_candidate_reviewer: ""
human_approved_by: ""
human_approved_at: ""
```

No row is a PASS. Existing scoped evidence and uncompleted release obligations
are distinguished below; task completion does not approve a Gate.

| Gate | Existing development evidence | Still required before approval |
|---|---|---|
| G0 | Existing license/provenance validation; no v1.3 asset import; archive boundary checks | Candidate license/resource audit, packaged notices and public identity review |
| G1 | Local Java 17 build and artifact identities | Clean candidate environment/CI, reproducibility and credential/content inspection |
| G2 | DataGen and resource tests | Candidate-bound clean generation/reference check |
| G3 | Full development unit/GameTest checks; API-only positive/negative consumer; scoped independent reruns | Final candidate results for all version requirements, retained failures/skips and reviewer assessment |
| G4 | Finite packaged startup/restart and per-feature service checks | Real-player connection/two-player flow and optional-client integration matrix on candidate |
| G5 | Explicit Fuel Loader migration; clean restart/removal/reinstall and native readback | Stable/Beta-to-candidate world migration and genuine interrupted-storage/S2 recovery matrix |
| G6 | Ownership/lifecycle/size/phase/replay tests and scoped reviews | Candidate-wide authority assessment, real client packet/multiplayer evidence and inherited recovery gaps |
| G7 | Hard-bound and slow-return tests, not a workload measurement | Reference-node CPU/memory/tick/network/cache measurements and deferred load duration |
| G8 | Written scenarios, no v1.3 real-client acceptance | Candidate V1/V2 hardware, screenshots/video, operators and actual results |
| G9 | API guide, compatibility matrix, changelog and this indexed handoff | Metadata cleanup, uninvolved installation, candidate checksums, independent final audit, human approval and publication verification |

See [test results](TEST-REPORT.md), [known issues](KNOWN-ISSUES.md) and
[release sequence](RELEASE-EVIDENCE.md). All inherited v1.0-v1.2 obligations stay
open. ADR-018 changes full-test scheduling only; ADR-020 expires before v1.3
candidate freeze and does not authorize v1.4 implementation. Neither is a
waiver for a known duplication, corruption or authority defect.
