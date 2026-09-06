# v1.1.0 BodyContext verification

Date: 2026-09-06

## Implemented scope

- Immutable `WorldLocation` and typed `BodyContext` values.
- Ordered tri-state resolver chain with a fixed 16-adapter limit.
- Ambiguous shared-Level mappings fail closed.
- Committed station regions resolve through the existing indexed lookup.
- Space gaps, reservations, deleted stations and unknown body IDs do not fall
  back to Earth or the generic Space body.
- New station creation rejects an undefined orbit body before reservation,
  persistence or platform generation.

The resolver accepts only a Level key and immutable coordinates. It calls no
world or chunk API. `StationRegistryModel.findAt` computes one grid cell and
performs map lookups, so query cost does not scan the station list.

## Automated evidence

```text
gradlew test --no-daemon
BUILD SUCCESSFUL
85 suites, 440 tests, 0 failures, 0 errors, 0 skipped

gradlew runGameTestServer --no-daemon
52 tests are now running
All 52 required tests passed :)
BUILD SUCCESSFUL
```

Unit coverage includes immutable-position copying, unloaded-world-independent
Level fallback, shared Space station differentiation, inclusive boundaries,
unassigned coordinates, reservation exclusion, deletion, unknown body IDs,
authoritative miss precedence and resolver-count bounds.

The `station_context` GameTest uses real `StationRegistrySavedData`, commits an
Earth-orbit station and a Moon-orbit station in the same Space Level, resolves
both landing positions, rejects an undefined creation target and verifies a
far unassigned position remains unresolved.

## Evidence files

- `body-context-gradle-test.txt`
- `gametest.txt`

## Deferred by version plan

Target-aware rocket planning, network frames, menus and v1.0 save migration are
separate remaining v1.1.0 slices. Long-duration load and soak testing remains
deferred until implementation is complete.
