# Packaged dedicated-server evidence

This directory records the successful `v0.0.2` packaged-server smoke run from
2026-08-28 (Asia/Shanghai; completed at 2026-08-28T01:10:03+08:00). The tested artifact was
`advancedrocketry-community-1.20.1-0.0.2-dev.jar` with SHA-256
`58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944`.

The harness installed the pinned Forge 47.4.10 server, verified the optimized
Forge status response contained this exact mod version, created and saved a
world, stopped cleanly, restarted the same world, queried it again, saved it,
and stopped cleanly a second time. Both Java processes exited with code 0. The
full-log audit found zero ERROR lines, zero project-logger WARN lines, and zero
client-class linkage failures in both cycles. It retained counts of 23 and 10
non-project WARN lines rather than treating them as project findings. Schema 2
also binds cycle IDs, timestamps, exit codes, complete-log hashes, the loopback
port, and a stable world marker with pre/post-restart `level.dat` snapshots.

- [`summary.json`](summary.json) contains hashes, runtime identity, and both
  cycle results.
- [`first-start.txt`](first-start.txt) is the selected first-start lifecycle
  excerpt.
- [`restart.txt`](restart.txt) is the selected same-world restart excerpt.

| File | SHA-256 |
|---|---|
| `summary.json` | `61cbf5d45926eb6c8f73ba484fc08bdf9c51ab07041489cc8701f7e57b9e5319` |
| `first-start.txt` | `775fabcd222281ac4a9baf7302b86dc31ff80eff82ec8ee7d75ddad8ec584ec6` |
| `restart.txt` | `0970e9724c9f9fcc4b972654a9313d75abfa66cd5af382d5164d13a9f0ab2053` |

Full installer and runtime logs remain in the ignored local `build/` session.
They include absolute machine paths and downloaded Minecraft/Forge libraries,
so they are not committed. The run used Java 17.0.8, offline mode, and a
loopback-only bind. `manual_player_cycles` is `false`; this evidence does
**not** claim a player joined. The matching-client schema-2 run remains deferred
to a separate test machine.
