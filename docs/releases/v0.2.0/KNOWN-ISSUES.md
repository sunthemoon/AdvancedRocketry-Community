# KNOWN-ISSUES — v0.2.0

## Release boundary

- This milestone implements only one Electrolyzer vertical slice.
- There is no general power network, multiblock framework, JEI integration,
  dimension, atmosphere, rocket, satellite, or progression system.
- Acceptance is complete, but no tag or public GitHub Release was created.
- Test worlds are disposable; no compatibility promise applies to valued
  worlds through `v0.4.x`.

## Runtime observations

- The vanilla client recipe book logs one `Unknown recipe category` warning
  when the custom Electrolyzer recipe is synchronized. The machine recipe,
  menu, and processing remain functional; this is not repeated per tick.
- Fresh Forge installations require network access.
- Forge language-provider JARs may report missing `mods.toml` metadata.
- Forge union resource URLs may report an unexpected schema.
- A fresh common or server config may be corrected on first launch.
- Vanilla may report absent goat-horn sounds, an unused shader sampler, or
  OpenGL diagnostic messages that are unrelated to the project renderer.

The packaged-client review found zero project `ERROR/FATAL` entries and no
client-class linkage failure. Both packaged-server cycles found zero project
warnings/errors/fatals and zero linkage failures.

## Architecture limitations

- Gas is represented by hydrogen and oxygen canister items; no gas-fluid
  subsystem was introduced.
- Redstone dust can be converted to 2,000 FE so the isolated slice can be
  exercised without adding a premature generator or cable network.
- Two simultaneous menu viewers are covered by deterministic GameTest state
  consistency; the packaged-server evidence separately covers a matching
  client joining, leaving, and reconnecting after restart.

## Revalidation triggers

Repeat the affected checks and human review if the JAR, generated resources,
machine schema, screenshots, filtered logs, server summary, or provenance
record changes.
