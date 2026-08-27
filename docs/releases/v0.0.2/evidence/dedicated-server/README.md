# Packaged dedicated-server evidence

This directory records the successful `v0.0.2` packaged-server smoke run from
2026-08-27. The tested artifact was
`advancedrocketry-community-1.20.1-0.0.2-dev.jar` with SHA-256
`827c07b34745cc5e6f484beb398b718cf87bd50e8d5be4f3c12679adc0973dcd`.

The harness installed the pinned Forge 47.4.10 server, verified the optimized
Forge status response contained this exact mod version, created and saved a
world, stopped cleanly, restarted the same world, queried it again, saved it,
and stopped cleanly a second time. Both Java processes exited with code 0, and
the audited logs contained no ERROR or client-class linkage failure.

- [`summary.json`](summary.json) contains hashes, runtime identity, and both
  cycle results.
- [`first-start.txt`](first-start.txt) is the selected first-start lifecycle
  excerpt.
- [`restart.txt`](restart.txt) is the selected same-world restart excerpt.

| File | SHA-256 |
|---|---|
| `summary.json` | `bc81352a980e786257d65ff91947990abc19f8acc1850ccc20c04636eb01a6ff` |
| `first-start.txt` | `bef77379f5d2a8a04aa52bc7068105c353c9f1d2f813bc2904e1e27049bddfd0` |
| `restart.txt` | `d238aac2990929def0b8678b9d8c5cfd45633efca2cc53d67b181d77254f9ed3` |

Full installer and runtime logs remain in the ignored local `build/` session.
They include absolute machine paths and downloaded Minecraft/Forge libraries,
so they are not committed. The run used Java 17.0.8, offline mode, and a
loopback-only bind. This evidence does **not** claim a player joined;
that visible-client case is deferred to a separate test machine.
