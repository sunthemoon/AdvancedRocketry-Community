# Performance — v0.7.0

The final GameTest run completed 6,000 measured atmosphere authority ticks
after a fixed 90-tick warmup with 16 active vents and no pending scan. Peak
inspection work was 112 against the 1,024-per-level-tick limit; final measured
inspections were zero.

Station work is bounded by fixed limits:

- 4,096 committed stations;
- 64 in-flight reservations;
- 32 members and 32 invitations per station;
- 512×512 logical region on a 1,024-block grid;
- 17×17 (289-block) generated platform touching at most four chunks;
- 400-tick flight ticket lifetime;
- no per-tick full station scan and no permanent station ticket.

The packaged run created ten pairwise non-overlapping stations, persisted them
through restart, executed six flight legs including two exact station arrivals,
and deleted one platform without touching its neighbor. Critical/High findings
were zero. Machine-readable values are in
[`evidence/performance/summary.json`](evidence/performance/summary.json).
