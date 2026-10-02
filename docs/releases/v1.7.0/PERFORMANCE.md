# v1.7.0 performance (development host)

Development evidence for the ADR-054 §7 budgets, measured on a packaged dedicated server.
It is **not** a Gate approval: the numbers are provisional, and the reference-hardware run
is `[H]`.

## Host and method

- Host: AMD Ryzen 5 5500 (6 cores, 12 threads), 31.9 GiB RAM, Windows 11 Pro, Java
  17.0.7, server heap 2 GiB; view and simulation distance 2.
- World: a copy of the retained v1.6 native world.
- Build: the C13 closure jar (see [development-artifacts](development-artifacts.json)).
- Harness: `native_v170_refload.py` in the [C13 closure](../../work/v1.7.0-c13-closure/VERIFICATION.md).

**The endgame reference load** (ADR-054 §7) is built through release-test hooks
(`refload build`):

- 16 logical and 4 physical laser drills, on stations orbiting Earth; the physical ones
  dig 256-layer shafts, so they drill through every window;
- 32 railguns in 16 pairs, one owner each, with a launch requested from each source every
  20 ticks;
- 16 black-hole generators burning at Cygnus X-1;
- 64 active gravity fields with 20 test players inside them;
- 8 elevator pairs, with a ride every 15 seconds (4 rides a minute);
- the root topped up with synthetic mass: 1,024 endpoints, 1,024 tombstones and 192
  transfers with four-stack payloads, to which the live railgun records add about 40.

Test players are connected through an embedded channel and ticked as connected players
(in creative mode, so vacuum does not hurt them); owners start their devices through the
device menus. The run is alone on the host.

**Measurement.** The endgame timing window (`/arce endgame timing`) holds the last 1,200
ticks, per place:

- each device type's block-entity ticks;
- the endpoint index;
- the ledger passes;
- the rides;
- the field lookups;
- the root flushes.

The total leaves flush ticks out of the P99 (review R2-L3). The idle budget is judged on
the window read after 11 minutes of the built load with nothing driven, once the
once-per-tick code is compiled as on a long-running server; the first window after the
build (cold) is reported too. The loaded windows are three consecutive 1,200-tick windows,
70 s apart, after every device is running. The flush budget is judged on every flush of
the run, the ramp included (review C13-F2). A JFR profile was recorded in the same run.

## Results

C13 closure run (jar `4326d3d…`, commit `33cb72b`), alone on the host. The steady state
was reached 10 s after the driver started: 20 drills running, 64 fields active, 16
generators burning, 8 pairs bound, 44 players. In the three loaded windows the root held
1,024 endpoints, 1,024 tombstones and 231–238 records (1.01–1.03 MiB accounted), and the
physical drills were still drilling (`OK=20`). Whole-server MSPT stayed at 7.2–8.6 ms.

| Budget (ADR-054 §7) | Limit | Measured | Result |
|---|---|---|---|
| All endgame work, systems loaded and idle (mean) | ≤ 0.1 ms | 0.054 ms after 11 minutes (cold, the first 1,200 ticks: 0.104 ms) | Within |
| All endgame work at the reference load (mean, worst window) | ≤ 2.0 ms | 0.553 ms (windows 0.553, 0.509, 0.517); 0.879 ms with the flush time included | Within |
| P99 over the ticks without a flush (worst window) | ≤ 8 ms | 2.04 ms | Within |
| Laser drills (mean) | ≤ 0.8 ms | 0.090 ms | Within |
| Ledger passes, railgun and elevator cargo (mean) | ≤ 0.5 ms | 0.192 ms | Within |
| Black-hole generators (mean) | ≤ 0.2 ms | 0.037 ms | Within |
| Field lookups (mean) | ≤ 0.3 ms | 0.033 ms | Within |
| Rides (mean) | ≤ 0.2 ms | 0.022 ms | Within |
| One coalesced or barrier flush at the reference root | ≤ 60 ms | 55 flushes in the run, at most 45.8 ms, none over 60 ms | Within in this run; not reliably on this host (below) |
| One flush at the accounted maximum (unloaded benchmark) | ≤ 250 ms | 84.2 ms mean, 95.6 ms max | Within |
| Tickets | None persistent; ride tickets ≤ 64 | One ride ticket per pending ride, released at commit, cancel and stop | Within |

Other places in the same windows (means): railgun block entities up to 0.103 ms, gravity
field controllers up to 0.080 ms, elevator endpoints 0.007 ms, the endpoint index up to
0.009 ms.

**Railgun throughput.** The driver requested 3,536 launches and 497 escrowed, about 2.1 a
second against ADR-054 §7's 16. Each railgun holds at most four outbox entries, each
waiting for its chunk's save and 40 ticks before it registers, so the persistence gates
allow about 3 escrows a second whatever the root holds (a shakeout with 64 synthetic
transfers measured 3.2 a second, refused with `OUTBOX_FULL`). The ledger share is
therefore measured at the highest rate the gates allow at the reference root; the
deviation is recorded for the maintainer (C13 review R2-N2).

**Field lookups.** The lookup runs in each player's tick. The server ticks only the
connections it accepted, so the first C13 runs, whose test players never ticked, measured
it as 0 (review C13-F3). Since `162a1d0` the hooks tick every test player's connection as
the server ticks a real one.

## Flush budget

- **Unloaded, at the reference root:** 33.6 ms mean, 41.9 ms max over 10 runs (a 23.8 KB
  file, 1.07 MiB accounted).
- **Unloaded, at the accounted maximum:** 84.2 ms mean, 95.6 ms max over 10 runs (a
  109 KB file, 2.57 MiB accounted).
- **Under the load, this run:** 55 coalesced and barrier flushes, at most 45.8 ms.
- **Under the load, earlier runs on this host:** single flushes of 116 to 562 ms. The
  first C13 closure run had one of 350 ms (327 ms in `FileChannel.force`) during the
  ramp, which its harness did not read (review C13-F2). Two shakeouts that shared the
  host with other servers had 202 and 207 ms.

A typical flush is mostly CPU on the server thread: at the reference root, of 31.8 ms,
the read-back decode that ADR-054 §10 mandates takes 14.3 ms, compression 7.0 and the
force 3.2 (review C13-F5). The outliers are durable-write stalls in `FileChannel.force`.

The budget held in this run, but not reliably on this host. ADR-054 §7 requires a
follow-up ADR that moves the write to a writer thread with separate snapshot and durable
epochs when a flush exceeds its limit, as ADR-050 §2 requires for the satellite registry
(whose v1.6 flush-cost gate is open for the same reason). That ADR should move encoding,
compression, the read-back and the force off the server thread. Whether reference
hardware also exceeds 60 ms, and that ADR, are the maintainer's decisions. **G7 stays
open.**

## What C13 changed

The first reference load met every loaded budget, but measured 0.44 ms per tick of
endgame work with the systems loaded and idle. Each cause found in the profile is fixed in
its own commit:

| Commit | Cause | Change |
|---|---|---|
| `5fbc412` | The ledger scanned every record per loaded endpoint each tick | Indexes by endpoint, source, arrival tick and acknowledged payload |
| `21285b2` | Structure controllers recomputed their pattern box each tick | Recomputed only when its inputs change |
| `a1437a3` | Idle generators and terminals re-derived their station every tick | Every 20 ticks while idle |
| `b4cdbca` | The ID order built two strings per comparison in every root map lookup | Compares the unsigned halves (the same order) |
| `b895778` | Pattern lookups regex-checked their ID every structure tick | Cached per catalog |
| `f0aa6ff` | Validation regex-checked every cell's ID and tags | Pattern blocks cached per state and role |
| `8687d9d` | The ledger visited every idle loaded endpoint each tick | Skips them, with a 20-tick sweep and wake-ups |

## Not measured here

- The docs/17 §4 base reference load (players, chunks, other mods) under the endgame
  load, real clients, and memory beyond the root's accounting: `[H]` on reference
  hardware.
- Client effects (laser, railgun, generator, tether, gravity). Their caps are in the
  code: at most 8 concurrent railgun effects per client, 4 field particles and 8
  target particles per device per tick. Their frame cost needs V1 on real GPUs.
