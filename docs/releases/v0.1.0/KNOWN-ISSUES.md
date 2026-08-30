# KNOWN-ISSUES — v0.1.0

## Release boundary

- This milestone is an asset/registry developer preview, not a playable
  Advanced Rocketry loop.
- No tag or public GitHub Release is created while blocking CI is pending. Any
  later publication must be marked pre-release.
- Test worlds are disposable; no save compatibility is promised through
  `v0.4.x`.

## Expected content limitations

- The machine casing is intentionally inert. Its interaction tells the player
  that machine behavior begins in v0.2.0.
- The four components are development items. They have generated recipes and
  tags but no processing gameplay.
- OBJ/MTL import is deliberately deferred. This version uses JSON models and
  introduces no custom model loader.
- There is no project packet, persistent service, tick loop, world scan,
  dimension, atmosphere, rocket, satellite, or research system.

## Runtime observations

- Fresh Forge client/server installations require network access. In the
  matching-client test, three initial installer attempts timed out while
  obtaining a mappings artifact. The explicit installer-only recovery reused
  validated partial downloads and then completed both server cycles.
- Forge language-provider JARs may report missing `mods.toml` metadata.
- Forge union resource URLs may report an unexpected schema.
- A fresh common config may be corrected on first launch by `ForgeConfigSpec`.
- Vanilla may report absent goat-horn sound events and one unused shader
  sampler.
- A launcher-supplied non-boolean IPv6 property may make Netty use its default.

These accepted warnings are external to the project logger. Both packaged
language runs and both dedicated-server cycles recorded zero project
`ERROR/FATAL`; project `WARN` and client-class linkage findings are also zero.

## Evidence naming

The dedicated-server harness retains a historical `v002-` session-ID prefix.
The summary independently binds Minecraft 1.20.1, Forge 47.4.10, mod version
`1.20.1-0.1.0-dev`, and the exact v0.1.0 JAR SHA-256; the prefix is diagnostic
only.

## Revalidation triggers

Repeat the affected checks and human review if any of these change:

- an imported or generated resource byte;
- provenance source, commit, path, license, or transform;
- main JAR byte or version metadata;
- client/server JAR equality;
- screenshot or filtered-log evidence;
- the visible content list or localization.
