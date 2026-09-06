# v1.1.1 Maintenance Verification

## Scope

Behavior-preserving decomposition of `RocketManager` and the rocket-flight
Forge GameTest collection. No player feature, save schema, network protocol,
registry ID, route data, balance or hard limit changed.

## Structural result

| Component | Before | After |
|---|---:|---:|
| `RocketManager` | 902 lines | 249 lines |
| `RocketFlightGameTests` | 1000 lines | 396 lines |

Extracted production services range from 79 to 405 lines. Split flight-test
classes and the shared fixture range from 187 to 396 lines. The structural
test and independent source audit enforce the recorded limits.

The baseline/current comparison reports:

- manager public declarations: 31 expected, 31 actual, exact match;
- flight GameTest registrations: 5 expected, 5 actual, exact match;
- flight data schema: 2;
- flight plan schema: 3;
- transfer record schema: 2;
- rocket-flight network protocol: 6.

## Automated results

| Check | Result |
|---|---|
| Java 17 clean build with rerun tasks | PASS |
| JUnit | PASS — 453 tests, 0 failed/error/skipped |
| Forge GameTest | PASS — all 52 required tests |
| DataGen | PASS — no generated-resource diff |
| Repository validator | PASS — 45/45 |
| `git diff --check` | PASS |
| Common/server client import audit | PASS — 0 findings |

The Forge GameTest startup emits Minecraft's normal missing initial
`server.properties` message, then creates defaults, starts the server and
passes all tests. No project-source error blocked execution.

## Artifact

```text
path: build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar
size: 1418823 bytes
sha256: fa27bd2714d337b0a5a50d62c26e3ecdedb83f1410b8a5135effcc35bddb945b
```

## Evidence files

- `clean-build-final.txt`
- `unit-tests.txt`
- `unit-test-summary.txt`
- `unit-test-summary-final.txt`
- `run-data.txt`
- `generated-diff.txt`
- `gametest.txt`
- `repository-validation.txt`
- `structural-audit.txt`
- `artifact.txt`
- `final-static.txt`

## Gate boundary

Implementation and short automated verification are complete. Dedicated
packaging/restart, long-duration performance, real-GPU, two-client and human
release acceptance were not executed in this pass. Overall status therefore
remains `IN_PROGRESS`; this report is not a release approval.
