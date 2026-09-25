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
process_safety_commit: a64b411ef94c1b51fb2f8a66b420caca7e0ee17b
process_recipes_commit: 9ff02da1cf3565c6d56ddb2055d092a9332d1b21
process_runtime_commit: 8af00d4bd439cc263b7b7a18d3796baf346007e4
process_checkpoint_artifact_sha256: edac60ea023d35a56597fb07d01d91e87cf83102e64db6d253cca2c9d9cc64a6
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

## V120-PREC-04 server process checkpoint (in progress)

- The controller now owns independent schema-1 `arce_process` and
  `arce_process_journal` roots, while Item and Energy remain in their physical port
  BlockEntities. Unsupported or malformed process roots are preserved and block
  automation and processing. No client packet selects a recipe or submits a result.
- The loaded-only port set resolves five fixed input cells, two output cells and one
  Energy cell for the current controller generation. The server process queue handles
  at most 256 controllers per tick. Item automation is locked while progress is active;
  Energy can be replenished during processing. Capability access never loads a chunk.
- Two generated community recipes exercise 2-input/2-output and 5-input/1-output
  processing. Recipe selection remains order-independent and rejects ambiguity.
  A combined maximum of 55 ingredient alternatives reserves space for current foreign
  stacks and both output identities inside the shared 64-entry journal snapshot.
- The final resource replacement simulates all inputs and both outputs before mutation,
  checks the controller resource revision, validates the exact live port set, and rolls
  back all seven Item stores on an in-memory mutation failure. The shared journal now
  retains a mismatched persisted marker instead of clearing an unverified transaction.
- Eight new process GameTests cover both recipe shapes, two-output exact-once behavior,
  running Energy replenishment, full output and missing Energy pauses, active-progress
  NBT round-trip, PREPARED/APPLYING journal replay, stale revision rejection, recipe
  signature mismatch and future process/journal schema preservation. A JUnit case
  covers the 55-alternative snapshot bound; another covers marker/resource divergence.

| Command or check | Result |
|---|---|
| `gradlew test --tests '...machine.precision.*' --tests '...machine.process.ProcessTransactionExecutorTest' --no-daemon` | PASS; targeted JUnit |
| `gradlew clean build --no-daemon` with JDK 17 | PASS; 118 suites, 579 tests, 0 failures/errors |
| `gradlew test --no-daemon` | PASS; task up-to-date after the clean build |
| consecutive `gradlew runData --no-daemon` | PASS; 53 total files, written 0 |
| `gradlew runGameTestServer --no-daemon` on the preserved development world | PASS; 86/86 Required GameTests, including 13 Precision tests |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 871 relative links, 0 failures |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and the 33-input inventory |
| `git diff --check` | PASS |

The build JAR above is a development artifact, not a v1.2.0 release candidate. A real
packaged-server stop/restart against one persisted Precision Assembler, especially with
ports spanning chunks, has not been verified. `setChanged()` alone does not prove that
journal and port chunk writes are durably ordered across a forced stop. This remains a
recovery risk and keeps `V120-PREC-04` in progress; it is not waived by ADR-018.
The pending human checks are listed in [short manual checks](MANUAL-TEST.md).

## Remaining slice work

- `V120-PREC-04`: complete short packaged-server same-world stop/restart and resolve
  the durable cross-chunk transaction boundary before claiming exact-once recovery.
- `V120-PREC-05`: server-authoritative menu, client-only screen, generated resources and
  readable diagnostics.
- `V120-PREC-06`: bounded packaged-server restart and evidence review.

The complete long-load, remote Linux, real-GPU, two-client and all-machine/all-dimension
campaign remains deferred by ADR-018. This phase does not satisfy v1.2.0 G0–G9 or the
Precision Assembler vertical slice.
