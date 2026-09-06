# v1.1.0 Expansion Kernel test design

This document defines tests before production integration. It does not mark
the v1.1 contracts or version as implemented.

## Fixture baseline

- Source commit: `a4ae20190e9f9a36b2a3cbabf1ea44b6746bc7d6`.
- Preserve an untouched copy of a v1.0 world containing Earth/Moon travel, a
  landed Space-station rocket, one active transfer and two stations.
- Every migration run operates on a fresh copy and hashes the source before
  and after.
- Existing v1.0 entity, transfer, station and player NBT are retained even
  when a migration is intentionally rejected.

## A0 contract and route tests

| ID | Scenario | Required observation |
|---|---|---|
| `TT-001` | JSON/NBT round-trip for surface, orbit, station and mission | Equality and canonical bytes/keys |
| `TT-002` | Unknown schema/type, extra field, mixed body/instance identity | Deterministic rejection with bounded diagnostic |
| `TT-003` | Identifier/encoded target at and over limits | Boundary accepted; over-limit rejected |
| `RT-001` | Load baseline Earth/Moon/orbit graph | Three bidirectional definitions produce six deterministic edges |
| `RT-002` | Duplicate route ID or directed edge | Entire candidate rejected; prior graph retained |
| `RT-003` | Missing body, equal endpoint, invalid distance | Entire candidate rejected with stable diagnostic ID |
| `RT-004` | Legal directed cycle | Search terminates and finds deterministic lowest-cost path |
| `RT-005` | Graph exceeds route/node/outgoing/search budgets | Bounded failure; no partial registry or cache entry |
| `RT-006` | Add `test_mars` body and route data only | Catalog/route discovery succeeds with no production Java change |
| `RT-007` | v1.0 fuel parity | Earth/Moon/station quotes equal committed v1.0 fixtures |

## A0/A1 body-context tests

| ID | Scenario | Required observation |
|---|---|---|
| `BC-001` | Earth and Moon unique Levels | Surface contexts resolve to the expected body |
| `BC-002` | Earth-orbit and Moon-orbit stations in shared Space | Positions inside regions return distinct orbit body IDs |
| `BC-003` | Exact minimum/maximum region coordinates | Inclusive boundary resolves to its station |
| `BC-004` | Position one block outside a region | Unresolved; never defaults to Earth |
| `BC-005` | Reservation without committed station | Unresolved and no region authority exposed |
| `BC-006` | Ambiguous Level mapping without region | Unresolved with bounded diagnostic |
| `BC-007` | Unloaded chunk coordinates | Resolver uses indexes only; loaded-chunk set is unchanged |
| `BC-008` | Station deletion/catalog reload | Cache invalidates before the next authoritative action |

## Save and recovery tests

| ID | Scenario | Required observation |
|---|---|---|
| `MG-001` | v1.0 Earth/Moon landed rockets | Body/dimension values become surface targets exactly once |
| `MG-002` | v1.0 rocket landed in a committed station region | Contextual pass writes that station UUID and orbit body |
| `MG-003` | Space rocket outside every region | Launch blocked; legacy fields retained; no Earth fallback |
| `MG-004` | Active transfer in every journal phase | v1.0 authority decision completes before v1.1 resave |
| `MG-005` | Deleted station/body during migration | Recoverable rejection with no entity, inventory or fuel loss |
| `MG-006` | Migrated save loaded and saved again | Second projection and schema are identical |
| `MG-007` | Future target/flight/transfer schema | Refused without overwriting the source fixture |

## Network and authority tests

| ID | Scenario | Required observation |
|---|---|---|
| `NW-001` | C2S surface/orbit/station targets | Server recomputes route, body, destination and fuel |
| `NW-002` | Client sends route cost, coordinate or redundant station body | Frame rejected or field is impossible to encode |
| `NW-003` | Unknown/oversized target and trailing bytes | Decode fails inside the fixed frame bound |
| `NW-004` | Deleted/inaccessible station UUID | Request rejected without chunk activation |
| `NW-005` | v1.0 client against protocol 6 | Explicit version mismatch; no implicit enum interpretation |
| `NW-006` | Two players choose different targets concurrently | Requests, quotes and menu state never cross sessions |

## Packaged and player checks

- `S1`: reload valid routes, then invalid routes; the previous graph remains
  active and the dedicated server remains usable.
- `S1/S2`: migrate copied v1.0 world, restart, interrupt one active transfer,
  recover, and compare rocket/fuel/inventory ledgers.
- `V2`: two real clients visit stations orbiting different bodies and observe
  matching context and route availability.
- `V1/V2`: destination UI displays localized names, never raw internal IDs;
  unknown and unavailable targets have readable reasons.

Long soak and reference-load performance are deferred until the vertical
implementation is complete, per owner priority. Early checks still record
route expansion counts, cache size, resolver lookup count and chunk-ticket
changes so unbounded behavior cannot enter the implementation unnoticed.

## Minimum command set after implementation starts

```text
gradlew.bat clean build --no-daemon
gradlew.bat test --no-daemon
gradlew.bat runData --no-daemon
git diff --exit-code -- src/generated
gradlew.bat runGameTestServer --no-daemon
python scripts/validate_repository.py --require-approved-identity
```
