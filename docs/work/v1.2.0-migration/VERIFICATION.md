# v1.2.0 machine migration: controller and part codec verification

```yaml
slice: V120-MIG-02
status: verified
date: 2026-09-25
environment: Windows 11, Java 17.0.7, Forge 47.4.10, Gradle 8.8
source_state: codex/v1.2.0-precision-assembler worktree before scoped commit
```

## Scope

The shared `arce_multiblock` and `arce_part_binding` schema-1 decoders now reject
unexpected top-level and position fields, non-compound controller part lists,
noncanonical UUIDs and oversized roots without discarding the original NBT.
Unsupported future schemas remain preserved and blocked. Encoded schema-1 field
names and root names did not change.

The previous controller decoder used `getList("parts", TAG_COMPOUND)`. Minecraft
returns an empty list when the stored list has another element type, which could
make malformed nonempty part data appear to be an empty legal state. Unknown
fields in both roots and position entries could also be dropped on re-encode.
The new tests exercise both the codec and a Rolling Machine BlockEntity load/save.

The controller bound is 1,310,720 NBT-accounted bytes (1.25 MiB). A legal state
with all 4,095 part positions measured 1,172,078 bytes and round-tripped; a
1 MiB candidate bound was rejected by that regression test and was corrected
before verification. The binding root is limited to 65,536 bytes.

## Verification

| Check | Observed result |
|---|---|
| Targeted `MultiblockNbtCodecTest` | 7/7 passed after bound calibration |
| `./gradlew clean build --offline --no-daemon` | PASS; 587 JUnit tests, 0 failures, 0 skipped, 120 suites |
| `./gradlew runData --offline --no-daemon`, twice | PASS; `written: 0` on each run |
| `./gradlew runGameTestServer --offline --no-daemon` | PASS; all 90 required tests, including malformed current-schema BlockEntity preservation |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and G0-G9 structure |
| `python scripts/validate_v120_machine_resources.py` | PASS; 9 machine blocks and related generated resources |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 0 failed/warnings |
| `git diff --check`; `git diff --exit-code -- src/generated` | PASS; no whitespace errors or generated-resource drift |

`build/test-results/test/` and `build/gametest/logs/latest.log` contain local
machine-readable results; they are ignored build outputs. Existing GameTest world
and packaged-server cache were moved outside `build/` before `clean` and restored
before GameTest, avoiding deletion by Gradle `clean`. GameTest then used its
restored world as usual. No long-duration, remote Linux, real-GPU or two-client
campaign was run under ADR-018.

## Remaining work and limits

- `V120-MIG-03` still needs the three-machine representative world migration
  report and fixture inventory. This codec slice does not prove save migration
  for every original machine or dimension.
- `V120-PREC-04` still lacks a proof of cross-chunk journal/port durability at
  arbitrary crash points. A clean build and GameTest cannot discharge that risk.
- The v1.2.0 G0-G9 release Gates and the prerequisite v1.1.0 Gate remain open.
