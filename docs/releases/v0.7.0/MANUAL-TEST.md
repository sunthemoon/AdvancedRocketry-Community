# Manual and dedicated acceptance — v0.7.0

## Bound candidate

- Build: `1.20.1-0.7.0-dev`
- Implementation commit: `e1c2db8ca3e67ae7f92fbbbbd5b6c23a25f7412f`
- JAR SHA-256:
  `4c049a4e0c2a74f78d383af7bc56ad31d746f8b7f8872cbc7258c58981d9c068`
- Reviewer: `sunthemoon`
- Review date: `2026-09-01`

## Executed observations

| Flow | Expected | Actual evidence | Result |
|---|---|---|---|
| Packaged server first start and same-world restart | clean status/save/stop, same world | `evidence/dedicated-server/summary.json` | PASS |
| Ten station allocations | unique 512×512 regions on 1,024 grid | `evidence/station-server/station-map.json` | PASS |
| Ownership administration | transfer preserves one authority owner | packaged lifecycle log | PASS |
| Earth ↔ station | approved station pad and exact return | station flight ledger | PASS |
| Moon ↔ station | approved station pad and exact return | station flight ledger | PASS |
| Restart and deletion | exact IDs/owners/cells/regions; neighbor survives | restart/deletion record | PASS |
| Two simultaneous players | distinct owners, same shared state marker | multiplayer client/server logs | PASS |
| Client shutdown | both clients disconnect and log `Stopping!` | multiplayer summary | PASS |

The owner directly confirmed G8 and G9 for this internal milestone. No
screenshot or video was captured or claimed. ADR-009 records the evidence
substitution, residual visual risk, and recollection conditions. The packaged
script creates the two multiplayer stations on behalf of the connected player
UUIDs; it does not claim recorded mouse/keyboard footage of the deployment
item or expansion flow.

## High-risk review

- Critical/High station authority or region-destruction findings: `0`
- Unauthorized visit/build/destination actions: rejected by the centralized
  server policy and covered by the authority matrix
- Ticket lifetime: bounded to `400` ticks
- Permanent chunk ticket: none introduced

## Accepted limitations

See [`KNOWN-ISSUES.md`](KNOWN-ISSUES.md) and
[`ADR-009`](../../decisions/ADR-009-V070-VISUAL-EVIDENCE-ATTESTATION.md).
