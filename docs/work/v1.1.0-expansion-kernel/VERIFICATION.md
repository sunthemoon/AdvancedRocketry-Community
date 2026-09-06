# v1.1.0 contract and TravelTarget codec verification

Date: 2026-09-06. Branch: `codex/v1.1.0-expansion-kernel`. Baseline commit:
`a4ae20190e9f9a36b2a3cbabf1ea44b6746bc7d6`.

## Scope

This work freezes the v1.1 target, route and body-context contract and
implements only the pure `TravelTarget` model plus bounded JSON/NBT codecs.
It does not change flight, station, network, GUI, data-pack or runtime callers.
The development build identity is advanced to `1.1.0-dev`; its metadata test
keeps the generated `mods.toml` assertion exact.

ADR-015 records the owner's bounded decision to continue implementation from
the committed v1.0 development baseline while v1.0 remains `IN_PROGRESS`.
That decision neither represents v1.0 as released nor waives a v1.1 Gate.

## Contract result

- ADR-014 is accepted and freezes four schema-v1 target kinds: body surface,
  orbit, station and mission.
- Body identities are bounded `ResourceLocation` values; runtime station and
  mission identities are canonical RFC 4122 UUID strings.
- Body targets contain no instance identity. Instance targets contain no
  client-supplied body, Level, region or coordinate.
- Unknown schemas/types, missing/additional/mixed fields, malformed UUIDs and
  identifiers over 128 characters fail closed.
- Canonical maximum-size JSON remains within 256 bytes. The NBT boundary
  measures the complete uncompressed compound and rejects values over 256
  bytes before decoding.
- All four variants round-trip through both `JsonOps` and `NbtOps`.

## Preserved baseline findings

- Exactly 23 baseline Java files contain 201 `RocketDestination` references.
- Flight NBT already persists bounded body/dimension identities and an
  optional destination station UUID.
- Station schema 1 already persists arbitrary `orbit_body` identifiers.
- `StationRegistryModel.findAt(x,z)` can support position-aware Space context
  without loading chunks.
- `CelestialCatalog` currently maps a Level to one body with `putIfAbsent`;
  multi-body ambiguity remains for the later BodyContext slice.
- The v1.0 rocket protocol is 5; flight and transfer record schemas are 1;
  transfer/station root schemas are 2; station state schema is 1.

## Commands and results

| Command | Result |
|---|---|
| `gradlew test --tests '*TravelTargetCodecTest' --no-daemon` | PASS; 8 target codec tests |
| `gradlew test --no-daemon` | PASS; 414 tests, 0 failures/errors |
| `gradlew clean build --no-daemon` | PASS |
| `gradlew runData --no-daemon` | PASS; generated resources unchanged |
| `gradlew runGameTestServer --no-daemon` | PASS; all 51 required tests passed |
| `python docs/work/v1.1.0-expansion-kernel/audit.py` | `PASS_V110_CONTRACT_BASELINE`; 23 files / 201 references, schemas and samples verified |
| `python -m unittest discover -s docs/work/v1.1.0-expansion-kernel -p test_contract.py -v` | 11 passed |
| `python scripts/validate_v1plus_planning.py` | PASS |
| `python -m unittest tests.test_v1plus_planning` | 14 passed |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 passed, 0 pending/warnings/failures |
| `git diff --check` plus changed-file whitespace scan | PASS |

The resulting development artifact is
`advancedrocketry-community-1.20.1-1.1.0-dev.jar`, 1,296,655 bytes, SHA-256
`e50e6c3beec0396aa7741f4c209f6c259d0c775d5beef9c2c3d04db273d8aeb1`.
It is not a frozen candidate. `git diff --exit-code -- src/generated` returned
0 after DataGen. The full `git diff --exit-code` returned 1 as expected because
this reviewed slice remains uncommitted.

The first clean build after changing the development identity found one stale,
exact `ModMetadataTest` expectation for `1.0.0-dev`. The assertion was updated
to the new exact version and description rather than relaxed; the subsequent
clean build and all 414 tests passed. The failed attempt is retained in
`metadata-version-transition.txt`.

No dedicated server, remote Linux, client, migration or long-duration
performance result is claimed. The GameTest run is a regression check for the
unchanged runtime callers, not evidence that the new target model is integrated.
The broader v1.1 Required Gates remain open.

## Next executable leaf

`V110-ROUTE-LOAD` can implement immutable, bounded route definitions and an
atomic candidate registry against the accepted target/anchor contract. Runtime
flight integration remains deferred until route planning and BodyContext are
independently verified.
