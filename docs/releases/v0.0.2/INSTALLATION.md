# INSTALLATION — v0.0.2 Forge Bootstrap

> **Accepted, unreleased developer preview:** `v0.0.2` is `PASSED`. There is no
> public release, release tag, or stable download. These instructions describe
> the accepted verification artifact and remain unsuitable for valued worlds.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Mod loader | Forge 47.4.10 verification baseline |
| Java | 17, for client, build, and dedicated-server execution |
| Evidence tooling | Python 3.12, for repository validators and the manual-evidence helper |
| Project JAR | `advancedrocketry-community-1.20.1-0.0.2-dev.jar` |
| Companion source artifact | `advancedrocketry-community-1.20.1-0.0.2-dev-sources.jar` |
| Optional dependencies | None |

Forge 47.4.23 is an advisory CI compatibility lane, not the packaged-client
acceptance baseline. Python is source/evidence tooling and is not a Minecraft
runtime dependency. Do not substitute another Minecraft, Forge, Java, or mod
version when producing `v0.0.2` release evidence.

## What this build contains

This milestone proves the Forge project bootstrap. It contains no playable
blocks, items, machines, planets, dimensions, rockets, recipes, project
networking, or progression. Installing it does not add a playable Advanced
Rocketry loop.

Use a new disposable world and a separate test profile. The project promises
no world compatibility for `v0.0.x` through `v0.4.x`; do not open a valued
world with this developer preview.

## Acceptance prerequisite: approved artifact selection

G0 is complete for implementation
`d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b`. The bootstrap provenance record
is digest-bound `THIRD_PARTY_APPROVED`; both rebuilt JARs and artifact evidence
reproduce in blocking CI; the exact source/resource inventory-history review
and rendered README review are `APPROVED` in immutable record commit
`3d8274082008ebcdd59d5c118dd9583790ccf175`.

Packaged-client acceptance must use main JAR SHA-256
`cd5ae579bae1bc21c1f67df2c3e00f196e0ee4a9ead01653c926b88ca37f32ad`
and companion sources JAR SHA-256
`f958f4334e8f95062a6ed15257fb9c5d940759490f3dc335c70e2764f1acacbe`.
Verify the exact physical source, server, and client copies; do not substitute a
pre-approval artifact. If a later change touches packaged bytes, reviewed
source/resources, provenance, or `README.md`, stop and repeat the affected
review instead of relabeling this evidence.

The exact verification and invalidation procedure remains in the
[`test-machine handoff`](../../work/v0.0.2-test-machine-handoff.md).

## Obtain the test artifact

Build from that post-provenance-approval source revision with Java 17:

```text
./gradlew clean build
```

The distributable artifact is:

```text
build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar
```

The corresponding source artifact is:

```text
build/libs/advancedrocketry-community-1.20.1-0.0.2-dev-sources.jar
```

For any v0.0.2 distribution, offer both files from the same download location
and link the exact repository source revision that produced them. The sources
JAR carries the source form of both adapted MDK resources packaged by the main
JAR; the repository revision carries the adapted build/bootstrap targets. Do
not distribute the main JAR alone under the reviewed provenance treatment.

Record its SHA-256 before copying it. A manual client/server test is valid only
when the source artifact, the client copy, and the server copy have identical
SHA-256 values. Do not rebuild or replace the JAR between those checks.

## Client installation

1. Install the official Forge 1.20.1-47.4.10 client.
2. Create a new launcher installation with an isolated game directory used
   only for this test.
3. Select Java 17 for that installation.
4. Create `<isolated-game-directory>/mods/` and copy the built project JAR into
   it. Install no other mods.
5. Confirm the copied JAR hash equals the recorded source-artifact hash.
6. Launch through the packaged Forge profile, not ForgeGradle `runClient`.

ForgeGradle `runClient` remains useful for development diagnostics, but it
loads development outputs rather than the distributable JAR and therefore is
not acceptable packaged-client release evidence.

## Dedicated-server installation

1. Install the official Forge 1.20.1-47.4.10 server into a new disposable
   directory.
2. Accept the Minecraft EULA only after reading it and only for that test
   instance.
3. Create `<server-directory>/mods/` and copy the same built project JAR into
   it.
4. Confirm the server copy hash equals both the source and client hashes.
5. Configure the server to listen only on loopback for local acceptance.
6. Start it with Java 17 using Forge's generated argument file:

```text
Windows cmd.exe: java @libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt nogui
Linux shell:     java @libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt nogui
```

For the repository's retained, disposable server workflow and the full
join/restart evidence procedure, follow
[`../../work/v0.0.2-test-machine-handoff.md`](../../work/v0.0.2-test-machine-handoff.md).

## Distribution status

Do not present this artifact as stable or use it for a persistent server. G0,
G4, G8, and G9 passed with explicit owner approval on 2026-08-30. A GitHub
Release is not required for Gate acceptance; if one is created afterward, it
must be marked as a pre-release rather than stable.
