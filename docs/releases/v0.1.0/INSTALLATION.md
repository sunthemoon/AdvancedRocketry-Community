# INSTALLATION — v0.1.0 Asset and Registry Baseline

> **Unreleased developer preview.** This build contains a small inert content
> slice for asset/registration validation. It is not a stable gameplay release
> and must not be used with an irreplaceable world.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Loader | Forge 47.4.10 verification baseline |
| Java | 17 |
| Project JAR | `advancedrocketry-community-1.20.1-0.1.0-dev.jar` |
| Optional dependencies | None |

The accepted local artifact SHA-256 is:

```text
07f5c108233ba14dad518a64f4141caa70f2338166b139b31415d6f284b8e6ea
```

Verify the final CI artifact against [`checksums.txt`](checksums.txt) before
using it. Do not mix client and server copies from different builds.

## Content boundary

This preview adds:

- one inert machine casing block/item;
- silicon wafer, basic circuit, advanced circuit, and data storage unit items;
- one dedicated creative tab and one UI sound;
- generated models, blockstates, loot, recipes, tags, sounds, and English/
  Chinese language data.

It does not add machine processing, inventories, power, fluids, dimensions,
atmosphere, rockets, satellites, progression, or project networking. Those
systems remain assigned to later milestones.

## Client installation

1. Create a new isolated Minecraft 1.20.1 profile.
2. Install Forge 47.4.10 and select Java 17.
3. Put the exact project JAR in that profile's `mods/` directory.
4. Install no other content mod when reproducing v0.1.0 acceptance.
5. Confirm the copied JAR SHA-256 matches the value above.
6. Launch through the packaged Forge profile, not ForgeGradle `runClient`.
7. Use a new disposable world.

## Dedicated-server installation

1. Install the official Forge 1.20.1-47.4.10 server with Java 17.
2. Read and accept the Minecraft EULA for that test instance.
3. Copy the same JAR into the server's `mods/` directory.
4. Verify its SHA-256 equals the client/source artifact.
5. Start the server with Forge's generated argument file.

For local acceptance, bind the disposable server to loopback. A normal server
may use its intended interface only after the operator applies the usual
network and authentication controls.

## Save compatibility

Worlds remain disposable through the pre-v0.5 development milestones. This
version introduces no project `SavedData` schema and makes no compatibility
promise for valued worlds. Removing the mod from a test world removes access to
its registered content and may leave missing registry references; discard the
test world instead of treating it as an upgrade path.

## Distribution status

The milestone passed acceptance, but no public tag or GitHub Release was
created. If this developer preview is later published, it must be marked as a
pre-release and must link the exact source commit and checksums. It must never
be described as stable Advanced Rocketry gameplay.
