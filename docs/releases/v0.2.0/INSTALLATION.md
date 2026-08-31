# INSTALLATION — v0.2.0 Electrolyzer Vertical Slice

> **Unreleased developer preview.** Use only disposable worlds. This milestone
> validates one machine architecture and is not a stable gameplay release.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Loader | Forge 47.4.10 verification baseline |
| Java | 17 |
| Project JAR | `advancedrocketry-community-1.20.1-0.2.0-dev.jar` |
| Optional dependencies | None |

Accepted main-JAR SHA-256:

```text
a8356cbeafdaffbd1192628c414c6996c402a757f6211c857d87e8ead52a2598
```

Verify the artifact with [`checksums.txt`](checksums.txt). Client and server
must use byte-identical copies of this JAR.

## Client installation

1. Create an isolated Minecraft 1.20.1 profile with Forge 47.4.10 and Java 17.
2. Put the exact project JAR in the profile's `mods/` directory.
3. Install no other content mod when reproducing the acceptance evidence.
4. Launch the packaged Forge profile and use a new disposable world.

## Dedicated-server installation

1. Install the official Forge 1.20.1-47.4.10 server with Java 17.
2. Accept the Minecraft EULA for that disposable instance.
3. Put the same accepted JAR in `mods/` and verify its SHA-256.
4. Start the server through Forge's generated argument file.

## Included gameplay slice

The single-block Electrolyzer accepts two empty canisters, 1,000 mB water, and
2,000 FE. After 100 processing ticks it atomically produces one hydrogen and
one oxygen canister. Its menu displays energy, water, progress, and a bounded
status reason. Redstone power pauses processing without consuming material.

## Save boundary

The BlockEntity uses schema version 1 and preserves unsupported future-schema
data without running or silently overwriting it. This protects downgrade
diagnostics but is not a general world-upgrade promise. Test worlds remain
disposable through the pre-v0.5 milestones.

## Distribution status

The milestone passed acceptance, but no tag or GitHub Release was created. Any
later publication must be marked pre-release and bind the exact source commit
and checksums. It must not be described as stable Advanced Rocketry gameplay.
