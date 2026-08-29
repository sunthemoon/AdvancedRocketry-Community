# INSTALLATION — v0.0.2 Forge Bootstrap

> **Unreleased developer preview:** `v0.0.2` is `IN_PROGRESS`. There is no
> approved public release, release tag, or stable download. These instructions
> exist for source-build verification and manual acceptance only.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Mod loader | Forge 47.4.10 verification baseline |
| Java | 17, for client, build, and dedicated-server execution |
| Evidence tooling | Python 3.12, for repository validators and the manual-evidence helper |
| Project JAR | `advancedrocketry-community-1.20.1-0.0.2-dev.jar` |
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

## Acceptance prerequisite: finish the provenance subreview first

Do not select or launch a packaged-client acceptance artifact while the G0
Forge/Gradle provenance/license subreview is pending. The repository owner or
assigned license reviewer must resolve that subreview, commit its approval
metadata and packaged notice changes, then rebuild the JARs, refresh artifact
evidence/checksums, and obtain successful blocking CI for that exact commit.
The approval transition changes packaged bytes, so a pre-approval JAR cannot be
reused as client evidence.

The reviewer uses the commit-bound packet procedure in
[`../../work/v0.0.2-test-machine-handoff.md`](../../work/v0.0.2-test-machine-handoff.md).
Do not continue until
`python -I -S scripts/validate_bootstrap_provenance.py --require-approved-review`
passes for the reviewed checkout; the default validator intentionally permits a
mechanically valid but human-pending state.

This first phase is not final G0 `PASS`. The rendered README screenshot is
captured from the later clean post-rebuild checkout and receives a separate
human visual review together with the packaged-client evidence. G0 stays
`IN_PROGRESS` until both phases are complete.

## Obtain the test artifact

Build from that post-provenance-approval source revision with Java 17:

```text
./gradlew clean build
```

The distributable artifact is:

```text
build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar
```

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

Do not present this artifact as stable, upload it as a public release, or use it
for a persistent server. Acceptance remains blocked until the remaining G0,
G4, G8, and G9 evidence is reviewed and a human explicitly marks the version
`PASSED`. A GitHub Release is not itself required for Gate acceptance; if one is
created afterward, it must be marked as a pre-release rather than stable.
