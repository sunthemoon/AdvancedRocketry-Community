# TEST-REPORT — v0.5.0

## Automated verification

| Check | Result |
|---|---|
| Two clean builds | PASS; byte-identical main JAR |
| Java unit tests | PASS; 156 passed, 0 failed, 0 skipped |
| Python repository tests | PASS; 609 run, 606 passed, 3 historical-artifact skips |
| JAR audit | PASS; 497 entries, 0 findings |
| Common/server client-import scan | PASS; 0 findings |
| DataGen | PASS; 39 v0.5 resources, clean worktree |
| Forge GameTests | PASS; 34 passed, 0 failed |
| Repository strict validation | PASS; 0 warnings, 0 failures |

The Java suite covers snapshot bounds and round trips, stable hashes,
server-side statistics, loaded-only scanning, BlockEntity default-deny,
transaction rollback at every injected phase, region contention, replay
rejection, 100-cycle material conservation, bounded visual reassembly, and
maximum-size performance.

## Packaged server and persistence

- Generic packaged server: first start, status query, save, clean stop, and
  same-world restart passed with no project error or client-class linkage.
- Rocket workflow: exact four-block assembly, entity UUID/snapshot/NBT
  persistence, and restart passed.
- Recovery workflow: a staged pre-commit journal restored all four blocks,
  17 diamonds, and 64 iron ingots, then cleared the journal.
- Visible connection: the matching Forge user-development client joined the
  packaged server after restart, rendered the entity, disconnected, and left a
  clean server save/stop record.

## Fixed limits and performance

The 2,048-block maximum scan completed in 41 service ticks with 10,242
observations, below the 12,289 hard limit and the 256-per-tick budget. The
32,888-byte visual projection round-tripped in two chunks under the fixed
32,768-byte packet limit and 524,288-byte aggregate cap.

The first clean local and PR GameTest runs observed the inherited closed-door
test reading the fresh Moon heightmap in its block-placement tick. The test now
waits one normal server tick; production logic, assertions, and timeout remain
unchanged. A clean corrected run completed all 34 required tests. Exact
structured results are under [`evidence/`](evidence/). Known scope and runtime
observations are retained in [`KNOWN-ISSUES.md`](KNOWN-ISSUES.md).
