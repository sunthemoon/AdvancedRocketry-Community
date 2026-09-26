# External registration fixture handoff

## Completed scope

Added the isolated `arce_adapter_test` Forge mod under `src/adapterTest`:

- `AdapterTestMod`: deferred block/BlockEntity registration and one listener on
  its own MOD bus using only the public rocket adapter API.
- `FixtureContainerBlock` / `FixtureContainerBlockEntity`: original, non-chest,
  non-barrel two-slot inventory, schema 1 persistence and validated restoration.
- `FixtureInventoryAdapter`: captures inventory-only NBT; restoration touches
  only the target container. Adapter ID `arce_adapter_test:cargo_inventory`,
  payload version 1, exact type `arce_adapter_test:cargo_container`.
- Dedicated-only metadata and an additive host `rocket_movable` block tag.
- `AdapterRegistrationGameTests`: two required tests using the existing host
  `rocket_test` template, with an explicit `templateNamespace` annotation.

No host production, build, generated-resource, canonical status or ADR files
were changed. No upstream files/assets were imported.

## Tests implemented, not yet executed

1. `externalAdapterReceivesOneClosedRegistrationEvent`: observes exactly one
   real mod-bus registration event; a retained event must reject a new adapter
   after startup. The attempted type is an otherwise unused vanilla furnace,
   not the already claimed fixture type, so a duplicate-type error cannot stand
   in for the closed-window check. Expected failure is `IllegalStateException`.
2. `externalAdapterProductionAssemblyPreservesInventory`: places a motor, seat,
   guidance computer and fixture container above a real assembler. It queues
   `arce rocket assemble` using a per-test Forge fake owner's command source,
   waits for the actual host tick to assemble the rocket, and inspects entity NBT
   for exactly four blocks, owner UUID, adapter ID, payload version and exact
   inventory/item metadata. Normal owner shift-interaction then exercises the
   installed Runtime's disassembly. It checks all blocks restored, a fresh
   inventory BlockEntity, unchanged contents and schema, removal of the rocket,
   and absence of dropped item duplicates.

No test creates or replaces a host manager, runtime service or adapter registry.
No host internal Java type is imported. Host-specific observations use registry
IDs, command syntax and persisted NBT; those are black-box test observations,
not a newly advertised public API.

## Central integration requirements

The integrator owns the `adapterTest` source set and its isolated API/platform
compile classpath. Load the source set as mod `arce_adapter_test` in
`gameTestServer` only; exclude it from main/API JARs and ordinary client/server
runs. The annotations use the host template namespace, so the existing enabled
`advancedrocketrycommunity` GameTest namespace includes these tests. Including
`arce_adapter_test` as well is harmless but not needed for these two templates.

The current host command is sufficient: no new command, release-test system
property, client asset or production test hook is required. Assembly uses the
operator command service; disassembly uses normal entity interaction and the
installed Runtime. Their agreement also exercises manager installation wiring.

## Actual checks and results

- Read-only source/Forge 47.4.10 cached-source inspection confirmed command
  ownership, entity interaction, NBT field names, fake-player behavior and the
  Forge `templateNamespace` annotation behavior.
- Python static validation: 5 Java files; all host imports are public
  `api.rocket` imports; no direct host manager/runtime/registry invocation;
  ASCII Java sources; TOML, JSON and mcmeta parse; exact additive tag; two tests.
  Final check exit 0. Output is retained in `static-checks.txt`.
- The first ad-hoc static regex matched `RocketRuntime.` in a comment and
  reported an assertion failure. The checker was corrected to ignore comments;
  no test assertion or implementation was weakened. The rerun passed.
- `git diff --check` and staged `git diff --cached --check`: exit 0.
- No Gradle, compiler, GameTest, Java server or client was launched by this
  delegated task. Build/runtime behavior remains unverified until integration.

## Remaining scope and risks

This is the finite V130-ROCKET-02 consumer fixture, not V130-ROCKET-03 completion.
It does not establish external-mod uninstall/reinstall behavior, actual process
restart, crash recovery, cross-dimension transport, real-client behavior or
Required Gate approval. Entity and BlockEntity NBT inspection is serialization
evidence only. The fixed 40-tick round-trip test must be executed unchanged and
any failure investigated rather than hidden by extending its timeout.

No version Gate is marked passed. Next action: integrate the API/production
registration changes and the source-set wiring, then compile the isolated
consumer and run the two tests with the existing required GameTests.
