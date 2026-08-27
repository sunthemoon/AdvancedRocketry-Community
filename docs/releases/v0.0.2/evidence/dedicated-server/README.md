# Packaged dedicated-server evidence

This directory records the successful `v0.0.2` packaged-server smoke run from
2026-08-27. The tested artifact was
`advancedrocketry-community-1.20.1-0.0.2-dev.jar` with SHA-256
`b10db9785c3f80e35b6bba53d11c518907f12d39fdee263ca3630a4ba57d50e9`.

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
| `summary.json` | `62667ac689c32210d74a2503a35d0c5cf2d418fb00a1f45db5168634ed951f0b` |
| `first-start.txt` | `42c01a71520e50780333445df2720e689cfc604900ce8391e0aca4e8fd66770f` |
| `restart.txt` | `cf085d14a82274355802544d0220be32dcd874038be36e7445ff559d96cf0bc8` |

Full installer and runtime logs remain in the ignored local `build/` session.
They include absolute machine paths and downloaded Minecraft/Forge libraries,
so they are not committed. This evidence does **not** claim a player joined;
that visible-client case is deferred to a separate test machine.
