# KNOWN-ISSUES — v0.3.0

## Release boundary

- Only Earth, Moon, and Space definitions and two fixed non-Overworld Levels
  are included. Arbitrary planets and runtime dimension registration are not.
- Travel is an operator-only development command. Rockets remain unavailable.
- Vacuum is a profile only; oxygen damage, suits, life support, and sealed-room
  behavior begin in later milestones.
- Moon/Space use placeholder world generation and no custom sky renderer.
- Test worlds are disposable; no valued-world compatibility promise applies
  through `v0.4.x`.

## Runtime observations

- Vanilla emitted one `moved too quickly` warning immediately after the
  fixed Space-to-Earth return. The fixed destination, next-tick gravity, player
  health, and saved state were correct.
- Fresh Forge installation and Forge's version check require network access.
- Forge language-provider JARs may report missing `mods.toml` metadata, and
  union resource URLs may report an unexpected schema. These are loader
  diagnostics, not project linkage failures.
- The two-client evidence server used offline mode on loopback only. This does
  not claim public offline-server safety or public-server compatibility.

No project client-class linkage failure occurred. Packaged client and server
logs are filtered before commit so desktop content, machine paths, and player
IP addresses are not published.

## Reproducibility boundary

Two consecutive clean Windows builds produced byte-identical main and sources
JARs. CI separately audits Linux output and runtime behavior; byte-for-byte
cross-platform equality is not claimed unless the CI artifact hash also matches.

## Revalidation triggers

Repeat the affected automated and human review if the JAR, celestial schema,
fixed dimensions, XML fixture/import output, screenshots, persistence dump,
filtered logs, or provenance record changes.
