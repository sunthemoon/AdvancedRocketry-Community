# v1.2.0 Electrolyzer compatibility migration verification

```yaml
version: v1.2.0
slice: V120-MCH-02
status: VERIFIED
date: 2026-09-25
branch: codex/v1.2.0-electrolyzer
implementation_commit: 1bb4ca1b6bc601e31f67ceb5709ac7a129cdce1e
harness_commit: f8c8a3cb17846af11ddc75f4bfaf5e4db11015d9
artifact_sha256: 832eaa3d61e42661adf6280d01302526a25fe21e66f0d315da6e20ab9b7932c7
```

## Verified implementation

- The block, item, BlockEntity and menu remain registered as
  `advancedrocketrycommunity:electrolyzer`; the recipe type and serializer remain
  `advancedrocketrycommunity:electrolyzing`. Existing player-visible batch behavior is
  unchanged: two empty canisters, 1000 mB water and 2000 FE over 100 ticks produce one
  hydrogen and one oxygen canister.
- `ElectrolyzerRecipe` now supplies the shared schema-1 `ProcessDefinition`, bounded
  resolved ingredient alternatives and a canonical SHA-256 semantic signature. Invalid
  stable outputs and definitions larger than 64 KiB fail closed.
- The Electrolyzer delegates process execution, resource snapshots and Forge capability
  exposure to narrow v1.2 adapters. It does not introduce a shared BlockEntity base class
  or move Minecraft lifecycle ownership into the pure process domain.
- The accepted `arce_machine` schema-1 decoder remains available. A legacy active process
  is converted to `arce_process` once without recipe lookup, energy consumption, output
  production or mutation of the compatibility root; loading the converted state again is
  idempotent.
- Current `arce_process` and `arce_process_journal` roots use the shared persistence codec.
  Future, malformed, semantically invalid and oversized legacy/process/journal data is
  preserved and blocks execution rather than being silently rewritten or discarded.
- Recovery requires matching machine ID, completed progress, recipe signature and
  transaction plan. Orphan or mismatched journals enter a fail-closed state; external
  Item/Fluid/Energy access is disabled while recovery is required or persisted data is
  unsupported.
- Item, Fluid and Energy side policies retain the accepted Electrolyzer behavior. Cached
  capability views become permanently inert after invalidation, including when a new
  capability epoch is later created.
- Menu handlers return copies and route insertion, extraction and slot replacement through
  authoritative storage rules. Shift-click into an occupied input cannot lose items, and
  an active process input cannot be extracted or copied through alternate menu paths.
- The 50-cycle material ledger returns the exact expected canister, water and FE balance.
  Existing Electrolyzer GameTests remain in `BootstrapGameTests` unchanged; v1.2-specific
  compatibility checks live in `ElectrolyzerKernelGameTests`.
- A packaged-server fixture atomically seeds the accepted legacy BlockEntity NBT, pauses at
  progress 40, restarts the same world with identical resources, then completes exactly one
  hydrogen/oxygen batch with no retained input, water or FE.

## Verification commands

| Command or check | Result |
|---|---|
| Electrolyzer JUnit suites plus `ProcessForgePortAdapterTest` | PASS; 9 suites, 27 tests, 0 failures/errors/skips |
| `gradlew clean build --no-daemon` with JDK 17 | PASS; 114 suites, 561 tests, 0 failures/errors/skips |
| two consecutive `gradlew runData --no-daemon` runs | PASS; 35 total files, written 0 on both runs |
| final `gradlew runGameTestServer --no-daemon` | PASS; all 73 Required GameTests |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 860 links, 0 failures |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and the 33-input inventory |
| `python -m py_compile scripts/run_v020_machine_server_smoke.py` | PASS |
| common/server client-import scan | PASS; no `net.minecraft.client` import |
| class-size review | PASS; largest changed production class is 656 lines and remains below the 800-line ADR threshold |
| intended-file sensitive-pattern scan | PASS; 0 matches |
| unchanged accepted GameTest check | PASS; `BootstrapGameTests.java` has no diff |
| packaged `run_dedicated_server_smoke.py` baseline | PASS; first start and same-world restart both exited cleanly on loopback |
| packaged `run_v020_machine_server_smoke.py` legacy fixture | PASS; progress 40 and all resources survived restart, followed by exactly one completed batch |
| `git diff --check` | PASS |

One intermediate GameTest run failed two Electrolyzer cases because the strict decoder
classified Forge's canonical empty fluid tag as malformed. The decoder now accepts both an
empty compound and `{FluidName: "minecraft:empty", Amount: 0}`; the final 73/73 run passed.

The first packaged legacy-fixture attempt used `setblock` followed by `data merge`, which
combined a newly initialized process root with legacy state and was correctly rejected as a
mismatch. The harness now places the disposable block and complete legacy BlockEntity NBT in
one atomic `setblock`; Python compilation and the final packaged restart scenario passed.

The GameTest and packaged-server commands above are bounded slice checks under ADR-018. They
are not long-load, full remote-Linux, real-GPU, two-client or final all-machine/all-dimension
acceptance.

## Packaged restart evidence

- Tested production tree: `1bb4ca1b6bc601e31f67ceb5709ac7a129cdce1e`.
- Harness-only atomic fixture correction:
  `f8c8a3cb17846af11ddc75f4bfaf5e4db11015d9`.
- Packaged artifact SHA-256:
  `832eaa3d61e42661adf6280d01302526a25fe21e66f0d315da6e20ab9b7932c7`.
- [Baseline first-start/restart summary](packaged-restart/baseline/summary.json)
- [Legacy-machine restart summary](packaged-restart/machine/summary.json)
- [Filtered machine lifecycle log](packaged-restart/machine/filtered-lifecycle.txt)
- [Evidence SHA-256 manifest](packaged-restart/SHA256SUMS)

## Deferred acceptance outside this slice

- Long-duration load, the complete remote-Linux matrix, real-GPU visual checks, two-client
  multiplayer checks and the all-machine/all-dimension integration campaign remain deferred
  by ADR-018 until the original machine and dimension scope has been completed or explicitly
  dispositioned.
- Development still requires bounded JUnit, build, DataGen, GameTest, migration,
  resource-conservation, security and restart checks for each slice; ADR-018 does not waive
  failures in those checks.
- The packaged restart proves accepted legacy-schema migration and one bounded same-world
  completion path. It does not prove long-run stability or every transaction-journal crash
  boundary.

## Gate statement

This evidence verifies `V120-ELEC-001..004`, `V120-MIG-001..002` and the functional
`V120-MCH-02` Electrolyzer compatibility slice. It does not satisfy the v1.2.0 release Gates,
the broader `V120-MIG` campaign or the full acceptance campaign deferred by ADR-018.
