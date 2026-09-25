# V120-MIG-03A — Precision saved-world fixture

```yaml
status: verified_fixture_only
date: 2026-09-25
source_slice: V120-PREC-06
source_tested_commit: ea654f678d6a0d94ab3508c3743f99fc52cfa3c8
source_region_sha256: 66ba5141c0568e7abcd156f1b19c6a9b6de25d63af14acbb8308ddf2b242954e
source_artifact_sha256: e339deda6ade5bccd034cbf6d6953e7ca8da8c547ce4af99a8e8befc6878cd55
```

## Fixture and provenance

The four compressed chunk NBT files in
[`fixtures/precision-final/`](fixtures/precision-final/) were extracted from the
final-restart region of the short packaged-server Precision test. The local source
region was checked against the SHA-256 in the archived
[`summary.json`](../v1.2.0-precision-assembler/packaged-restart/precision/summary.json)
before extraction. The manifest records the source summary, region, artifact,
tested commit, machine UUID and individual chunk hashes. Verification of the
committed fixture does not require the ignored local region file.

The chunks cover `(8,8)`, `(8,9)`, `(9,8)` and `(9,9)`. They contain one
Precision Assembler controller and eight ports, with no other BlockEntities in
those four chunks. The checker validates schema-1 roots, controller/part binding,
machine identity, generation, process revision, last-applied transaction, item
stacks and energy against the archived final-restart summary. The final state is
`IDLE`, revision `5`, energy `800`, one advanced circuit and two redstone torches.
This is server-generated test-world data, not an upstream world or imported art.

## Verification

| Check | Result |
|---|---|
| `python scripts/v120_precision_world_fixture.py --verify` | PASS; four chunks, one controller, eight ports; source region SHA matched |
| `python -m unittest tests.test_v120_precision_world_fixture -v` | PASS; 6 tests, including altered chunk/resource/summary rejection |
| `python -m py_compile scripts/v120_precision_world_fixture.py` | PASS |
| `./gradlew clean build --offline --no-daemon` | PASS; Java 17.0.7; `test` used Gradle cache |
| `./gradlew test --offline --no-daemon --rerun-tasks` | PASS; 587 JUnit tests, 0 failures/errors/skips across 120 suites |
| `./gradlew runData --offline --no-daemon` | PASS; `written: 0` |
| `./gradlew runGameTestServer --offline --no-daemon` | PASS; all 90 required GameTests |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 0 warnings/failures |
| `python scripts/validate_v120_machine_resources.py` | PASS; 9 machine blocks and resources |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 version plans and G0-G9 structure |
| `git diff --check`; `git diff --exit-code -- src/generated` | PASS; no whitespace error or generated-resource drift |

## Limits and remaining work

This fixture proves that archived, saved final-state NBT matches its report. It
does not rerun the packaged server on the current commit, prove durability across
arbitrary cross-chunk crash ordering, or establish the three-machine migration
result. `V120-MIG-03B` still needs Rolling Machine and old Electrolyzer world
fixtures plus the aggregate migration report. `V120-PREC-04` still needs the
cross-chunk journal/port durability design and recovery evidence. The full
machine/dimension and long-duration acceptance matrix remains scheduled by
[ADR-018](../../decisions/ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md),
and no G0-G9 Gate is claimed here.
