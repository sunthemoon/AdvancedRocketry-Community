# Packaged dedicated-server evidence

This directory records the successful `v0.0.2` packaged-server smoke run from
2026-08-29 (Asia/Shanghai; completed at 2026-08-29T22:45:52+08:00). The tested artifact was
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
port, and a stable world marker with pre/post-restart `level.dat` snapshots. The
world identity additionally binds the harness-owned canonical startup-properties
snapshot used to select the loopback bind, port, and `world` level name.

- [`summary.json`](summary.json) contains hashes, runtime identity, and both
  cycle results.
- [`first-start.txt`](first-start.txt) is the selected first-start lifecycle
  excerpt.
- [`restart.txt`](restart.txt) is the selected same-world restart excerpt.

| File | SHA-256 |
|---|---|
| `summary.json` | `37be93f86779121854223980207ff4228a534d47dacecae837d70e2c11b17cfa` |
| `first-start.txt` | `79ad47b208404d555cfe80653ce4ddf440ce641849b41b96dcb7b99d561852a5` |
| `restart.txt` | `cc23c817def71110aa69d096306ea32f0a4754b6193f7e6d93044a4ccc199144` |

Full installer and runtime logs remain in the ignored local `build/` session.
They include absolute machine paths and downloaded Minecraft/Forge libraries,
so they are not committed. The run used Java 17.0.8, offline mode, a
loopback-only bind, and two Forge-installer attempts: the first timed out while
retaining downloads, and the retry completed. `manual_player_cycles` is
`false`; this evidence does
**not** claim a player joined. The matching-client manual-player schema-3 run
remains deferred to a separate test machine.
