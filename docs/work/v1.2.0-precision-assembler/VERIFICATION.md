# v1.2.0 Precision Assembler partial verification

```yaml
version: v1.2.0
slice: V120-MCH-03
verified_leaves: [V120-PREC-01, V120-PREC-02]
slice_status: IN_PROGRESS
date: 2026-09-25
branch: codex/v1.2.0-precision-assembler
shared_codec_commit: 2348c088e322ce7881e71e428554ca8ed125e733
implementation_commit: da78f33086d039f8d981b07cc2b99bb1d57385a5
artifact_sha256: 10521cb9775db199a733bdc179b7ddd3c658d9b7cf7eba295e3498d2d7cbed57
```

## Verified behavior

- The frozen `advancedrocketrycommunity:precision_assembling` recipe type and serializer
  are registered. Its schema-1 JSON requires exact fields, 2–5 fixed-slot Item inputs,
  1–2 Item outputs, bounded vanilla Item/Tag ingredients, an encoded definition no larger
  than 64 KiB, checked process timing and energy, and valid stack sizes.
- Each input and output maps to a distinct stable process channel. Extra occupied input
  slots do not accidentally match a shorter recipe; two- and five-input definitions are
  selected independently of candidate iteration order. Ambiguous or over-1024 recipe
  sets are rejected instead of choosing the first match.
- Recipe signatures are SHA-256 of semantic values. Ingredient alternative ordering does
  not change the signature, and bounded network round-trip retains the same process
  definition. Inputs carrying NBT are rejected because the process snapshot identifies
  resources by Item ID only.
- The vanilla Item/Tag ingredient decoder is now shared with Rolling Machine; Rolling's
  existing recipe tests remain green.
- The community-authored 3×3×4 pattern contains one controller, five interchangeable
  physical Item input ports, two interchangeable physical Item output ports, one energy
  input port and 27 casing cells. It permits four horizontal rotations and local-X mirror.
- Fixed local cells assign `item_input_0..4`, `item_output_0..1` and `energy_input`.
  Eight transform combinations retain those assignments regardless of world coordinates
  or physical placement order. Existing bounded validation distinguishes wrong/missing
  cells from unloaded cells without loading a chunk.

## Commands and observations

| Command or check | Result |
|---|---|
| `gradlew test --tests '...machine.precision.*' --tests '...machine.rolling.RollingMachineRecipeTest' --rerun-tasks --no-daemon` | PASS; 9 Precision tests and 4 Rolling tests |
| `gradlew clean build --no-daemon` with JDK 17 | PASS; 116 suites, 570 tests, 0 failures/errors/skips |
| `gradlew runData --no-daemon` | PASS; 35 total files, written 0 |
| `gradlew runGameTestServer --no-daemon` | PASS; all 73 existing Required GameTests |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 867 relative links, 0 failures |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and the 33-input inventory |
| `git diff --check` | PASS |

The current GameTest run validates existing behavior and packaging of the new pattern,
not a working Precision Assembler. No Precision BlockEntity, port capability, process
transaction, menu or world-level machine GameTest exists yet.

## V120-PREC-03 port foundation (in progress)

- Defined stable physical Item-input, Item-output and Energy-input port roles. The
  pattern's fixed local cell, rather than placement order, will select each logical
  process channel when the runtime adapter is implemented.
- Added a separate `arce_precision_port` schema-1 resource root with a 64 KiB root
  limit, 16 KiB Item limit, strict fields and type identity, no Item NBT, and a
  20,000 FE Energy limit. Unknown or malformed roots decode as preserved, blocked
  payloads rather than silently becoming empty resources.
- Added one-slot Item and bounded 1,000 FE/t receive stores. Seven new JUnit cases
  cover typed round-trip, defensive copy, future/malformed/oversized roots, mixed
  resources, invalid automation input and Energy accounting.

| Command or check | Result |
|---|---|
| `gradlew test --tests '...machine.precision.PrecisionAssemblerPort*Test' --no-daemon` | PASS; 7 new JUnit tests |
| `gradlew clean build --no-daemon` with JDK 17 | PASS; 118 suites, 577 tests, 0 failures/errors |
| `gradlew runData --no-daemon` | PASS; 35 total files, written 0 |
| `gradlew runGameTestServer --no-daemon` | PASS; 73 existing Required GameTests; none exercises these unregistered ports |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 868 relative links, 0 failures |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and the 33-input inventory |
| `git diff --check` | PASS |

The port BlockEntity, registry entries, generation-aware binding, capability lifecycle,
drop behavior and lifecycle GameTests are still required before `V120-PREC-03` can be
marked verified. These foundation tests do not establish resource retention in a world.

## Remaining slice work

- `V120-PREC-03`: connect the tested persistence and stores to registered Item/Energy
  port BlockEntities with loaded-generation binding, invalidated capability views,
  exact world resource retention and lifecycle GameTests.
- `V120-PREC-04`: controller formation, server recipe selection, two-output atomic
  commit, journal recovery and world-level GameTests.
- `V120-PREC-05`: server-authoritative menu, client-only screen, generated resources and
  readable diagnostics.
- `V120-PREC-06`: bounded packaged-server restart and evidence review.

The complete long-load, remote Linux, real-GPU, two-client and all-machine/all-dimension
campaign remains deferred by ADR-018. This phase does not satisfy v1.2.0 G0–G9 or the
Precision Assembler vertical slice.
