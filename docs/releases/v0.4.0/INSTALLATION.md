# INSTALLATION — v0.4.0 Vacuum and Life Support

> **Unreleased developer preview.** Use only disposable worlds. World
> compatibility is not promised through `v0.4.x`.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Loader | Forge 47.4.10 verification baseline |
| Java | 17 |
| Project JAR | `advancedrocketry-community-1.20.1-0.4.0-dev.jar` |
| Optional dependencies | None |

Verified JAR SHA-256:

```text
05279656dfae21f682ca45a000517628dfcf706ebc4cce9ce2fe16e0723e96f1
```

Client and server must use byte-identical copies.

## Client and server installation

1. Create an isolated Minecraft 1.20.1 Forge 47.4.10 profile using Java 17.
2. Put the exact project JAR in each client's `mods/` directory.
3. Install the same Forge version on the dedicated server and put the same JAR
   in its `mods/` directory.
4. Keep normal online authentication enabled for ordinary use.

The acceptance server used loopback-only offline mode solely to test two
isolated deterministic identities on one machine. That setting is not a
deployment recommendation.

## Milestone content

- Space suit helmet, chestplate, leggings, and boots.
- Oxygen canister loading into the chestplate.
- Oxygen Vent with finite oxygen and energy.
- `VACUUM`, `SUIT INCOMPLETE`, `SUIT OXYGEN`, `OXYGEN EMPTY`, `SCANNING`, and
  `ROOM SEALED` HUD states.
- `/arce atmosphere status` and permission-level-2
  `/arce atmosphere rescan` diagnostics.

Right-clicking the Vent without a refill item reports its finite operating
state, including open room, oversized room, unloaded boundary, or missing
power. The Vent never loads arbitrary chunks to complete a scan.

No public release or tag is produced by this milestone acceptance record.
