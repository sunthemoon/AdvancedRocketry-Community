# Installation — v0.8.0 developer preview

## Compatibility

- Minecraft `1.20.1`
- Forge `47.4.10` baseline
- Java `17`
- Server and every connecting client must use the same candidate JAR
- Candidate SHA-256:
  `0ce6c6bf9eb603f5973f35c19a47b295454a1f8c74ee74a6a99af3c2627a1937`

## Install

Place `advancedrocketry-community-1.20.1-0.8.0-dev.jar` in the `mods`
directory of both the Forge dedicated server and each client. Do not mix the
Windows candidate with a separately built CI JAR merely because both report
the same mod version; compare SHA-256 first.

The v0.8.0 candidate adds a powered Satellite Terminal, bounded data-satellite
components and recipes, logical game-time missions, exactly-once research
claims, persisted celestial discoveries, and operator recovery commands.

## Save boundary

Satellite, mission, research, and discovery state is stored in independently
versioned server `SavedData`. Back up the complete world before installing or
changing builds. Future schemas fail closed and preserve their original
payload. Downgrading a world after it has loaded v0.8.0 state is unsupported.

## Release status

This is an internal, unreleased developer preview. It is not a stable public
release and no release tag is implied by this evidence bundle.
