# INSTALLATION — v0.3.0 Celestial Data and Fixed Dimensions

> **Unreleased developer preview.** Use only disposable worlds. This milestone
> validates fixed celestial infrastructure and is not a stable gameplay release.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Loader | Forge 47.4.10 verification baseline |
| Java | 17 |
| Project JAR | `advancedrocketry-community-1.20.1-0.3.0-dev.jar` |
| Optional dependencies | None |

Verified main-JAR SHA-256:

```text
920425eaeb8cf8b6e94f23ed3086ca290ae734315059bbcf8eea100272d8bdfb
```

Client and server must use byte-identical copies. The complete inventory is in
[`checksums.txt`](checksums.txt).

## Client installation

1. Create an isolated Minecraft 1.20.1 profile with Forge 47.4.10 and Java 17.
2. Put the exact project JAR in the profile's `mods/` directory.
3. Use no other content mod when reproducing the acceptance evidence.
4. Connect only to a server using the same JAR.

## Dedicated-server installation

1. Install the official Forge 1.20.1-47.4.10 server with Java 17.
2. Accept the Minecraft EULA for that disposable instance.
3. Put the exact project JAR in `mods/` and verify its SHA-256.
4. Start the server through Forge's generated argument file.

Normal deployments should keep online authentication enabled. The two-client
acceptance harness used offline mode only on a loopback-bound disposable server
to create deterministic isolated player identities.

## Included operator flow

Permission-level 2 operators may use:

```text
/arce celestial validate
/arce celestial list
/arce celestial goto moon
/arce celestial goto space
/arce celestial goto earth
```

Travel is a development command, not rocket gameplay. Destinations are fixed,
bounded, and protected by a small safe platform. Moon and Space are fixed
`ResourceKey<Level>` worlds; data reload never registers arbitrary dimensions.

## Save and compatibility boundary

Celestial discovery and first-visit state use schema 1 in Overworld
`SavedData`. Unsupported future schema is preserved and mutation is rejected.
This is not a compatibility promise for valued worlds; worlds remain disposable
through `v0.4.x`.

No tag or GitHub Release exists for this build. Any later publication must be
marked pre-release and must not be described as stable Advanced Rocketry.
