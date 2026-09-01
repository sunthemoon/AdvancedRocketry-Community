# Installation — v0.7.0 developer preview

## Compatibility

- Minecraft `1.20.1`
- Forge `47.4.10` baseline
- Java `17`
- Server and every connecting client must use the same candidate JAR
- Candidate SHA-256:
  `4c049a4e0c2a74f78d383af7bc56ad31d746f8b7f8872cbc7258c58981d9c068`

## Install

Place `advancedrocketry-community-1.20.1-0.7.0-dev.jar` in the `mods`
directory of both the Forge dedicated server and each client. Do not mix the
Windows candidate with a separately built CI JAR merely because both report
the same mod version; compare SHA-256 first.

The v0.7.0 candidate adds a Station Deployment Kit, one shared Space Level,
fixed-grid station regions, invitations and membership, protected station
building, approved-pad rocket destinations, and operator recovery commands.

## Save boundary

Station state is stored as independently versioned server `SavedData`. Back up
the complete world before installing, changing builds, transferring ownership,
or deleting a station. Future station schemas fail closed. Downgrading a world
after it has loaded v0.7.0 station data is unsupported.

## Release status

This is an internal, unreleased developer preview. It is not a stable public
release and no release tag is implied by this evidence bundle.
