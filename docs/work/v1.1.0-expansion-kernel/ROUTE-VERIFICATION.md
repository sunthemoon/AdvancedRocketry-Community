# v1.1.0 route kernel verification

Date: 2026-09-06. Build identity: `1.20.1-1.1.0-dev`.

## Implemented boundary

- Exact-shape schema-v1 `RouteAnchor` and `RouteDefinition` codecs.
- Only body-surface and orbit anchors are data-definable; station and mission
  UUIDs cannot enter static route files.
- Immutable graph validation rejects duplicate IDs, duplicate directed edges,
  missing bodies, equal endpoints and values above every ADR-014 graph bound.
- Candidate reload is atomic and retains the last valid graph with a bounded
  diagnostic after rejection.
- Dijkstra search is deterministic, cycle-safe and limited to 1,024 expanded
  nodes; equal costs compare complete route-ID paths lexicographically.
- Successful-plan LRU cache is limited to 256 entries.
- `TravelFuelFormula` preserves the three v1.0 reference quotes and accepts the
  authoritative route-plan distance without trusting client cost input.
- Built-in Earth–Moon and surface/orbit routes are data files. Test Mars and
  its Earth route exist only in test data and require no destination enum.

## Automated evidence

| Check | Result |
|---|---|
| Route/legacy-planner targeted suite | 24/24 passed |
| New route tests | 17/17 passed |
| Full JUnit suite | 431/431 passed |
| Clean build | PASS |
| DataGen | PASS; generated-resource diff empty |
| GameTest | 51/51 passed |
| Runtime route reload | generation 1 accepted, 3 routes and 3 anchors |

Logs are `route-gradle-test.txt`, `clean-build.txt`, `run-data.txt` and
`gametest.txt` in this directory. The GameTest log contains both the accepted
route-catalog marker and Forge's final required-test marker.

## Not yet claimed

The existing rocket menu, C2S packets, flight persistence and transfer journal
still use the v1.0 destination adapter. Route loading and planning are active,
but launch selection will not consume them until BodyContext and migration
contracts are implemented and verified.
