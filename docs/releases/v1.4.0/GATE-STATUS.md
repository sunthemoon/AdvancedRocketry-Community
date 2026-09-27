# v1.4.0 Gate status

```yaml
version: v1.4.0
status: IN_PROGRESS
build: 1.20.1-1.4.0-dev
branch: codex/v1.4.0-planetary-expansion
development_commit: d33552a5374272b3b0669090ec4466075662ee4c
production_commit: a9143c2b62d5daab412cefedcee198f1fd032588
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

**No Gate is PASS.** Development checks and independent slice reviews are not
candidate certification or human release approval.

| Gate | Scoped development evidence | Remaining acceptance |
|---|---|---|
| G0 | Original planetary code/data provenance; retained MDK adaptation notices; resource identities | Candidate-wide license/asset/package and public identity audit |
| G1 | Local Java 17 clean build and matching artifact bytes | Clean candidate environment/CI, reproducibility, credential/content review |
| G2 | Repeat DataGen, exact generated/packaged resource checks | Candidate-bound clean generation and resource audit |
| G3 | Schema, reload, environment, navigation, discovery/recovery unit and GameTests | Final candidate results and all version requirements, failures/skips retained |
| G4 | Finite packaged planetary travel/restart; actual old-runtime-copy upgrade | Real players, two-client discovery/station/travel and optional-mod matrix |
| G5 | Authentic v1.3 development copy; removed-content restoration; four native two-store process cuts | Representative Beta/stable/candidate whole-world migrations, remaining subsystem/interruption matrix |
| G6 | Bounded input, server admission, ownership and idempotency checks | Candidate-wide security audit plus real packet/multiplayer evidence |
| G7 | Finite 100-body/128-node and hard-bound tests | Reference hardware CPU/memory/tick/network/cache/sky measurements and scheduled load duration |
| G8 | Written manual scenarios only | V1 on at least two real GPU categories, V2 with two real clients, media and operator results |
| G9 | Guides, changelog, requirement/evidence mapping and scoped independent review | Metadata refresh, uninvolved installation, candidate checksums, final review, human approval/publication |

[ADR-018](../../decisions/ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md)
changes full-campaign timing, not correctness obligations. ADR-030 is limited to
v1.4 development and expires before candidate freeze; it does not authorize
v1.5. Inherited v1.0-v1.3 acceptance remains open. See
[known issues](KNOWN-ISSUES.md) and [requirement mapping](REQUIREMENT-MAP.md).
