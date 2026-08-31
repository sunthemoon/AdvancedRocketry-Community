# INSTALLATION — v0.5.0 Transactional Rocket Assembly

> **Unreleased developer preview.** Use only disposable worlds. Long-term save
> compatibility is not promised for this build.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Loader | Forge 47.4.10 verification baseline |
| Java | 17 |
| Project JAR | `advancedrocketry-community-1.20.1-0.5.0-dev.jar` |
| Optional dependencies | None |

Verified Windows candidate SHA-256:

```text
45782780eeec54f1710cee4425f96b4d0152d29590559f519130ca9f227f0ba0
```

Install the same JAR in the client and dedicated-server `mods/` directories.
Do not mix v0.5 snapshots across the connection. The server is authoritative
for scanning, statistics, assembly, disassembly, persistence, and recovery.

## Milestone content

- Rocket Assembler, motor, seat, fuel tank, and guidance computer blocks.
- Loaded-chunk-only structure validation with fixed block, volume, NBT, and
  observation limits.
- Transactional conversion to a same-dimension `RocketEntity`, including
  rollback and exact vanilla chest/barrel inventory restoration.
- Bounded tracking-player visual synchronization and cached block rendering.
- Operator diagnostics under `/arce rocket`.

This version does not launch, consume fuel, move between dimensions, or travel
to the Moon. Those behaviors begin in v0.6.0.
