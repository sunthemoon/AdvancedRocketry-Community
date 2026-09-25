# v1.2.0 Precision Assembler partial verification

```yaml
version: v1.2.0
slice: V120-MCH-03
verified_leaves: [V120-PREC-01, V120-PREC-02, V120-PREC-03, V120-PREC-05, V120-PREC-06]
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
menu_datagen_commit: 5f5815b053b42faf1d9685e448b567fe3d3c7513
menu_runtime_commit: 617a9efc357ad46347afdb23f36b40ac8109738c
menu_dev_jar_sha256: 63941ac68b24ee93314fbd79ff178fee61f112857ec1ded12b81851101b4ee73
restart_fixture_commit: ea654f678d6a0d94ab3508c3743f99fc52cfa3c8
restart_harness_commit: 46d10356d6bd23b0079dadc13595401e19888d3b
restart_dev_jar_sha256: e339deda6ade5bccd034cbf6d6953e7ca8da8c547ce4af99a8e8befc6878cd55
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

At this earlier checkpoint, a packaged-server stop/restart had not yet been verified;
the later `V120-PREC-06` checkpoint below supplies a saved-state restart. The build JAR
is a development artifact, not a v1.2.0 release candidate. `setChanged()` alone still
does not prove that journal and port chunk writes are durably ordered at arbitrary
crash points. This keeps `V120-PREC-04` in progress and is not waived by ADR-018.
The pending human checks are listed in [short manual checks](MANUAL-TEST.md).

## V120-PREC-05 menu, screen and generated resources (verified implementation)

- The controller opens a server-authoritative menu only for a non-spectator within eight
  blocks of the loaded, exact controller BlockEntity. The server menu captures its
  instance UUID and generation; distant, removed or rebuilt controllers invalidate
  clicks and quick transfers. No client packet supplies formation, progress or results.
- Five numbered input slots and two output slots resolve the current physical ports.
  The eighth port appears as a server-synchronized 20,000 FE gauge. Menu Item views
  preserve the port's process lock, generation epoch and resource-revision accounting;
  the output slot cannot accept manual insertion. The 22 read-only data fields include
  process/failure states, 32-bit progress and the first local/world structure diagnostic.
- The client-only screen uses a code-drawn panel and bilingual labels. Four distinct
  community crafting recipes and their advancements now make the controller and its
  three physical port types craftable. Existing community block models, loot and tags
  remain DataGen-owned; no upstream file or unreviewed visual asset was imported.
- Two JUnit cases cover stable menu IDs and signed-short transport. Three new Forge
  GameTests cover five-slot routing plus Energy, distance and generation rejection,
  structural diagnostics, process input locking, two-output transfer and a partial
  inventory merge without duplication. `validate_v120_machine_resources.py` checks
  nine v1.2 machine blocks' models, recipe results, advancements, loot, tags and
  bilingual keys, including local model/texture references.

| Command or check | Result |
|---|---|
| `gradlew test --tests '...machine.precision.*' --offline --no-daemon` | PASS; targeted Precision JUnit |
| `gradlew clean build --offline --no-daemon` on the final source tree | PASS; 119 JUnit suites, 581 tests, 0 failures/errors |
| `gradlew test --offline --no-daemon` | PASS; up-to-date after the clean build |
| consecutive `gradlew runData --offline --no-daemon` | PASS; 61 generated files, second invocation written 0 |
| `gradlew runGameTestServer --offline --no-daemon` on the preserved development world | PASS; 89/89 Required GameTests, including 3 new menu tests |
| `python scripts/validate_v120_machine_resources.py` | PASS; 9 machine block resource sets and bilingual keys |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 873 relative links, 0 failures |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and the 33-input inventory |
| `git diff --check` | PASS |

The complete GameTest console log remains at ignored local
`build/prec05-gametest-console.txt` (SHA-256
`d3deac4df980fe90f4f48fbc9de3bb27af6dbb4ce9a86fe713ff0f11ab6ceeeb`).
The final development JAR SHA-256 is
`63941ac68b24ee93314fbd79ff178fee61f112857ec1ded12b81851101b4ee73`;
its `1.1.1-dev` name is inherited from the unfrozen development baseline and does
not make it a v1.2 candidate. A client-only slot-number drawing change followed the
GameTest run and was included in the final clean build; no server behavior changed.

Real-GPU visual confirmation and two-client interaction remain unexecuted under
ADR-018. At this menu checkpoint, `V120-PREC-04/06` packaged persistence and durable
cross-chunk transaction ordering were open. The later saved-state restart below
does not close the transaction-ordering gap or prove v1.2.0 G0–G9 complete.

## V120-PREC-06 bounded packaged-server restart (verified)

- The opt-in release fixture constructs the community 3×3×4 structure at
  `(143, 80, 143)`. Its physical ports cross the X=143/144 chunk boundary. The
  test hook is disabled unless `advancedrocketrycommunity.releaseTestHooks=true`
  and requires command permission level 2.
- The packaged Forge 47.4.10 server first passed a clean-start/same-world-restart
  baseline with JDK 17. The fixture then ran to progress 1, paused by redstone,
  completed `save-all flush`, and was killed without a graceful `stop` command.
  On the same world's restart, formation, generation, all seven Item slots,
  progress 1/20, 1560 FE, revision 4, and journal/marker state were identical.
- Resuming produced one advanced circuit and two redstone torches. The inputs
  became empty, Energy became 800 FE, revision became 5, and a single transaction
  UUID appeared. The output and UUID remained unchanged after an idle wait and a
  final clean restart. This proves the saved snapshot and one completion in this
  bounded fixture; it does **not** prove durable ordering when a crash occurs
  between journal and separate port chunk writes.

| Command or check | Result |
|---|---|
| `gradlew clean build --offline --no-daemon` with JDK 17 | PASS; 119 suites, 581 tests, 0 failures/errors/skips |
| `gradlew runData --offline --no-daemon` | PASS; 61 files, written 0 |
| `gradlew runGameTestServer --offline --no-daemon` on preserved development world | PASS; 89/89 Required GameTests |
| `python scripts/run_dedicated_server_smoke.py ... --offline-mode` | PASS; first start and same-world restart, no project ERROR |
| `python scripts/run_v120_precision_restart_smoke.py ...` | PASS; saved forced stop, recovery, completion and final restart |
| `python scripts/validate_v120_machine_resources.py` | PASS; 9 machine block resource sets |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 873 links |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and 33-input inventory |
| `python -m py_compile scripts/run_v120_precision_restart_smoke.py` | PASS |

The development JAR SHA-256 is
`e339deda6ade5bccd034cbf6d6953e7ca8da8c547ce4af99a8e8befc6878cd55`.
The [baseline summary](packaged-restart/baseline/summary.json),
[fixture summary](packaged-restart/precision/summary.json),
[filtered lifecycle](packaged-restart/precision/filtered-lifecycle.log), and
[SHA-256 manifest](packaged-restart/SHA256SUMS) bind the short verification to the
artifact and the same-world identity. Full logs stay in the ignored local disposable
server session; the fixture summary records their hashes. The preserved GameTest
console is ignored at `build/prec06-gametest-console.txt` (SHA-256
`1985ce390b21ccdd3f2be6854462ab1a2699d2c5197ddd7680d2f528d11d6e57`).

The complete long-load, remote Linux, real-GPU, two-client and all-machine/dimension
matrix is deferred by ADR-018 until all original machines and dimensions are
implemented. This does not make the Precision slice or v1.2.0 release-ready.

## Remaining slice work

- `V120-PREC-04`: resolve the durable cross-chunk transaction boundary before
  claiming exact-once recovery at arbitrary crash points.

The complete long-load, remote Linux, real-GPU, two-client and all-machine/all-dimension
campaign remains deferred by ADR-018. This phase does not satisfy v1.2.0 G0–G9 or the
Precision Assembler vertical slice.
