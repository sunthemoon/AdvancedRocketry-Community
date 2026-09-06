# v1.1.0 typed target integration verification

Date: 2026-09-06. Build identity: `1.20.1-1.1.0-dev`.

## Runtime integration

- Production rocket launch and cancel requests carry `TravelTarget` rather
  than an enum ordinal.
- The flight channel is protocol 6. Both C2S intent and S2C plan/quote frames
  use bounded target codecs; quote lists are limited to 160 distinct targets.
- The server resolves body definitions, station UUIDs, orbit ownership, route
  distance, gravity and required fuel. Clients provide none of those authority
  values.
- Flight plans and transfer checksums retain the exact typed target.
- The flight console receives data-defined body quotes and per-station UUID
  quotes. Earth/Moon buttons remain compatibility shortcuts; additional body
  destinations are enumerated from the server quote list.
- Unknown bodies, missions, missing stations, unauthorized stations, source
  dimension spoofing, duplicate targets, oversized/truncated frames and
  trailing bytes fail closed.
- The operator station-creation command accepts a namespaced body ID and then
  delegates existence checks to the active server celestial catalog; it no
  longer contains an Earth/Moon-only parser branch.

## Data-only extension proof

The test-only `test_mars` definition and Earth-to-Mars route live under
`src/test/resources/data/`. `RocketTargetFlightPlannerTest` decodes both JSON
files, builds the catalogs and successfully creates a typed Earth-to-Mars plan.
No `test_mars` identifier or branch exists in production Java.

## Final automated evidence

| Check | Result | Log |
|---|---|---|
| Contract oracle | 11/11 passed | `contract-tests-final.txt` |
| Baseline audit | `PASS_V110_CONTRACT_BASELINE` | `audit-final.txt` |
| JUnit | 449/449 passed | `unit-tests-final.txt` |
| Clean build | PASS | `clean-build-final.txt` |
| DataGen | PASS | `run-data-final.txt` |
| Forge GameTest | 52/52 required tests passed | `gametest-final.txt` |
| Repository policy | 45 passed, 0 pending/warnings/failed | `repository-check-final.txt` |

The development JAR is
`advancedrocketry-community-1.20.1-1.1.0-dev.jar`, 1,400,590 bytes, SHA-256
`2b641ffef92866468ca1d32718a5eb0c06afe89f20840e81a0a7b5a34de60985`.
It contains all four built-in route JSON files, the typed target/planner classes
and the v1.1 localization overlay. It is not a frozen release candidate.

## Remaining acceptance

No real-GPU UI result, two-client multiplayer result, historical-world
dedicated restart or long-duration load result is claimed. The owner accepted
shipping/continuation risk for those deferred checks; Required Gates remain
`IN_PROGRESS` until their evidence exists.
