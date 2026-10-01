# v1.6.0 Gate status

```yaml
version: v1.6.0
status: IN_PROGRESS
build: 1.20.1-1.6.0-dev
branch: codex/v1.6.0-satellite-resource-missions
development_parent_commit: eb5b80e1aaa6d427e6e3f879f468a814c1bb336f
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
| G0 | No upstream code or assets imported; the balance data is v1.6 data under ADR-052 §8; the provenance validators pass | Candidate-wide licence, asset, package and identity audit |
| G1 | Local Java 17 clean build; tested JAR bytes recorded | Clean candidate environment or CI; reproducibility (the sources JAR is not byte-reproducible) |
| G2 | DataGen (`src/generated/v1.6`) with no generated-file change during the runs | Candidate-bound clean generation and resource audit |
| G3 | 1,157 JUnit tests and 290 GameTests. These include the reconciliation table, the 64 rebind orderings, every crash cut and more than 256 claims at one terminal | Final candidate results for every plan item |
| G4 | Native upgrade, 1,000-mission restart and backlog, and crash cuts on a packaged dedicated server | S2 with real players; optional-mod matrix |
| G5 | Native root-2 to root-3 upgrade with a byte-identical backup; terminal root 1 to 2; [recovery matrix](RECOVERY-MATRIX.md) | Candidate-bound whole-world migrations |
| G6 | Server-authoritative intents (button IDs only); server seeds; intent limits; no chunk tickets; bounded records, packets and audit lines; an independent review with no Critical finding | Candidate-wide security audit with real multiplayer evidence |
| G7 | 500- and 1,000-mission and worst-case flush timings; tick P95/P99 through a 1,000-mission backlog on the development host, within the docs/17 budgets | Reference-hardware CPU, memory and tick measurements. The ADR-050 §2 flush-cost gate is open: a worst-case-root flush took up to 631 ms on the development host; if reference hardware exceeds the budgets, the writer-thread ADR is required |
| G8 | None: V0 not run on this Windows host | V1 on at least two real GPU categories and V2 with two real clients; a mission gameplay video |
| G9 | ADR-048 to ADR-052, the requirement map, known issues and this handoff | Uninvolved installation, candidate checksums, final audit, human approval and publication |

ADR-048 is limited to v1.6 development; its inherited acceptance obligations stay open
until a candidate. See [RELEASE-EVIDENCE](RELEASE-EVIDENCE.md),
[KNOWN-ISSUES](KNOWN-ISSUES.md) and [REQUIREMENT-MAP](REQUIREMENT-MAP.md).
