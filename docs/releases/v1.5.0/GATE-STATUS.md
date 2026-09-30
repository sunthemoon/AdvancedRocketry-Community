# v1.5.0 Gate status

```yaml
version: v1.5.0
status: IN_PROGRESS
build: 1.20.1-1.5.0-dev
branch: codex/v1.5.0-orbital-station-warp
development_parent_commit: d45534ebc55a4005df511935ed34adf62a8a7b2e
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
  G7: IN_PROGRESS
  G8: NOT_STARTED
  G9: IN_PROGRESS
overall: IN_PROGRESS
independent_candidate_reviewer: ""
human_approved_by: ""
human_approved_at: ""
```

**No Gate is PASS.** Development checks and independent slice reviews are not
candidate certification or human release approval.

| Gate | Development evidence | Remaining acceptance |
|---|---|---|
| G0 | No new upstream code or assets; the provenance validators pass | Candidate-wide licence, asset, package and identity audit |
| G1 | Local Java 17 clean build; tested JAR bytes recorded | Clean candidate environment or CI, reproducibility (the sources JAR is not byte-reproducible) |
| G2 | DataGen with no generated-file change | Candidate-bound clean generation and resource audit |
| G3 | 1,058 JUnit tests and 260 GameTests, including the 345-cell permission matrix; mutation checks of the review fixes | Final candidate results for every plan item |
| G4 | Native v1.4-world upgrade, warp, kill and restart runs on a packaged server; S1 permission subset | S2 and two real clients; optional-mod matrix |
| G5 | Native root-2/3 to root-4 upgrades with backups; rocket identities; missing orbit body kept through save and restart; [recovery matrix](RECOVERY-MATRIX.md) | Candidate-bound whole-world migrations; an audited abandon path for stuck transfer records |
| G6 | Server-authoritative commands; no new packet; permission matrix; no chunk loads from requests; bounded storage and write spacing | Candidate-wide security audit with real multiplayer evidence |
| G7 | 4,096-station checked-write measurement; write spacing; fold-window test | Reference-hardware CPU, memory, tick and network measurements; native multi-station load and reload |
| G8 | None: V0 not run on this Windows host | V1 on at least two real GPU categories and V2 with two real clients |
| G9 | CHANGELOG, ADR-039 to ADR-047, requirement map, known issues and this handoff | Uninvolved installation, candidate checksums, final audit, human approval and publication |

ADR-039 is limited to v1.5 development; its inherited acceptance obligations stay
open until a candidate. See [RELEASE-EVIDENCE](RELEASE-EVIDENCE.md),
[KNOWN-ISSUES](KNOWN-ISSUES.md) and [REQUIREMENT-MAP](REQUIREMENT-MAP.md).
