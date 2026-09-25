# v1.2.0 Precision Assembler partial verification

```yaml
version: v1.2.0
slice: V120-MCH-03
verified_leaves: [V120-PREC-01, V120-PREC-02, V120-PREC-03]
slice_status: IN_PROGRESS
date: 2026-09-25
branch: codex/v1.2.0-precision-assembler
shared_codec_commit: 2348c088e322ce7881e71e428554ca8ed125e733
implementation_commit: da78f33086d039f8d981b07cc2b99bb1d57385a5
recipe_pattern_artifact_sha256: 10521cb9775db199a733bdc179b7ddd3c658d9b7cf7eba295e3498d2d7cbed57
port_runtime_artifact_sha256: 534bd4793da524ce14ad83bc410094a7f41afa265e87dff012d1e75ab163a109
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

At the `V120-PREC-02` checkpoint, the 73 GameTests validated existing behavior and
packaging of the new pattern, not a working Precision Assembler. The runtime adapter and
its tests were added in the subsequent `V120-PREC-03` checkpoint below.

## V120-PREC-03 port foundation (in progress)

- Defined stable physical Item-input, Item-output and Energy-input port roles. The
  pattern's fixed local cell, rather than placement order, will select each logical
  process channel when the runtime adapter is implemented.
- Added a separate `arce_precision_port` schema-1 resource root with a 64 KiB root
  limit, 16 KiB Item limit, strict fields and type identity, no Item NBT, and a
  20,000 FE Energy limit. Unknown or malformed roots decode as preserved, blocked
  payloads rather than silently becoming empty resources.
- Added one-slot Item and bounded 1,000 FE per-call receive stores. Seven new JUnit cases
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

This foundation checkpoint did not establish resource retention in a world; the runtime
adapter and lifecycle GameTests were added afterward.

## V120-PREC-03 registered port runtime

- The controller and three physical port block types have stable registry IDs and
  dedicated BlockEntity types. The controller uses the bounded pattern catalog,
  footprint index and dirty queue; no capability query scans a structure or loads an
  unready controller chunk. The machine rejects datapack definitions that change its
  frozen 3×3×4 physical port layout.
- The runtime binds five Item inputs, two Item outputs and one Energy input to fixed
  local channels. An unformed controller may select the mirrored transform when its
  observed structure matches it; both mirrored and unmirrored footprints are indexed.
- Port resources remain in their own versioned BlockEntity roots. Automation is gated
  by loaded controller identity, current generation, formed state, controller part set
  and local-cell role. Binding changes, formation changes and reloads invalidate old
  `LazyOptional` views; retaining a handler cannot revive it after rebuild.
- Registered blocks have generated placeholder models, bilingual names, pickaxe/iron
  tags and self-drop loot. Breaking an Item port drops its stored stack, while breaking
  casing only invalidates formation and retains resources.
- Five Forge GameTests cover formation and eight channel assignments, mirrored formation,
  typed capability policy, resource retention through disassembly and NBT reload,
  invalidated old views, future-root preservation, unloaded forged bindings, copied
  out-of-structure bindings and Item drops. Existing JUnit cases cover the NBT bounds.
- The new block IDs and `arce_precision_port` schema 1 have no previous-version alias;
  future ID or payload changes require an explicit migration rather than silent remap.

| Command or check | Result |
|---|---|
| `gradlew test --tests '...machine.precision.*' --no-daemon` | PASS; Precision JUnit suite |
| `gradlew clean build --no-daemon` with JDK 17 | PASS; 118 suites, 577 tests, 0 failures/errors |
| final `gradlew build --no-daemon` after port review | PASS; 118 suites, 577 tests, 0 failures/errors |
| consecutive `gradlew runData --no-daemon` | PASS; 51 total files, written 0 |
| `gradlew runGameTestServer --no-daemon` on the preserved development GameTest world | PASS; 78/78 Required GameTests, including 5 Precision tests |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 869 relative links, 0 failures |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and the 33-input inventory |
| `git diff --check` | PASS |

A separate run against a newly initialized GameTest world completed 77 tests but failed
the earlier `earthMoonRoundTripConservesFuelAndBlockedPadReturnsSource` rocket test with
`Blocked-pad source cleanup failed`, before the isolated Precision batch. This failure
is not waived or attributed to a root cause without evidence. The preserved world and
the fresh-world failure fixture remain under ignored `build/` directories for follow-up;
the overall v1.2 Gate is not marked passed. Four Precision tests passed in that
fresh-world run; the fifth mirror test was added afterward and passed on the preserved
world. No packaged dedicated restart has yet been run for Precision Assembler.

## Remaining slice work

- `V120-PREC-04`: server recipe selection, two-output atomic
  commit, journal recovery and world-level GameTests.
- `V120-PREC-05`: server-authoritative menu, client-only screen, generated resources and
  readable diagnostics.
- `V120-PREC-06`: bounded packaged-server restart and evidence review.

The complete long-load, remote Linux, real-GPU, two-client and all-machine/all-dimension
campaign remains deferred by ADR-018. This phase does not satisfy v1.2.0 G0–G9 or the
Precision Assembler vertical slice.
