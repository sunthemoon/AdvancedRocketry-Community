# v1.7.0 Gate status

```yaml
version: v1.7.0
status: IN_PROGRESS
build: 1.20.1-1.7.0-dev
branch: codex/v1.7.0-endgame-systems
development_parent_commit: 33cb72b2f1a0cbabde6e7937305410a8349939f7
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
| G0 | No upstream code or assets imported (the legacy audit is read-only); the endgame casing model reuses project textures and references vanilla ones; the provenance validators pass | Candidate-wide licence, asset, package and identity audit |
| G1 | Local Java 17 clean build; tested JAR bytes recorded | Clean candidate environment or CI; reproducibility (the sources JAR is not byte-reproducible) |
| G2 | DataGen (`src/generated/v1.7`) with no generated-file change during the runs | Candidate-bound clean generation and resource audit |
| G3 | 1,376 JUnit tests and 337 GameTests: the ledger reference vectors and every named cut, laser crash cuts, the endgame protection chain, destruction bounds, and rule mutations caught | Final candidate results for every plan item |
| G4 | Native, on a packaged dedicated server: the v1.6 upgrade; twelve ledger crash cuts and a record pruned while its source was unloaded, over three railgun pairs; an elevator cargo cut; three ride forced stops; a final restart; and the reference load with 44 connected test players | S2 with real players (deferred by the maintainer on 2026-10-02); protection-mod matrix with real mods |
| G5 | Native v1.6 to v1.7 upgrade with no endgame file before the first endgame change; [recovery matrix](RECOVERY-MATRIX.md) | Candidate-bound whole-world migrations |
| G6 | Server-authoritative intents (button IDs only); no new client-to-server message; intent limits; barrier spacing; no chunk tickets except the bounded ride ticket; bounded records, packets and audit lines; destruction bounds; three independent implementation reviews (C11, C12, C13) with no open Critical or High finding | Candidate-wide security audit with real multiplayer evidence |
| G7 | The ADR-054 §7 endgame reference load on the development host ([PERFORMANCE](PERFORMANCE.md)): idle 0.054 ms, loaded mean 0.553 ms (0.879 ms with flushes), P99 2.04 ms, every per-system share within budget, and every flush of the run within 60 ms (at most 45.8 ms) | The flush budget is not reliably met on this host: earlier loaded runs had single flushes of 116–562 ms (`FileChannel.force` stalls). ADR-054 §7 requires the writer-thread follow-up ADR if reference hardware also exceeds 60 ms. The reference load's railgun rate (about 2 escrows a second against 16) is a recorded deviation. Reference-hardware measurements (docs/17 §4) |
| G8 | None: V0 not run on this Windows host | V1 on at least two real GPU categories and V2 with two real clients; endgame effect and screen checks (deferred by the maintainer on 2026-10-02) |
| G9 | ADR-053 to ADR-059, the [operator guide](../../ENDGAME-OPERATOR-GUIDE.md), the requirement map, known issues and this handoff | Uninvolved installation, candidate checksums, final audit, human approval and publication |

ADR-053 is limited to v1.7 development; its acceptance obligations stay open until a
candidate. See [RELEASE-EVIDENCE](RELEASE-EVIDENCE.md), [KNOWN-ISSUES](KNOWN-ISSUES.md),
[REQUIREMENT-MAP](REQUIREMENT-MAP.md) and [PERFORMANCE](PERFORMANCE.md).
