# v1.0.0 migration evidence

**Final-candidate migration acceptance is incomplete.**

The [representative Beta-world report](../../work/v1.0.0-beta-world-upgrade/VERIFICATION.md)
records an unchanged accepted Beta JAR (`fbddf669...`) producing a 59-file
world, retained as an 8,403,030-byte ZIP with SHA-256
`9022b2fdf4537168bc33f1db06f96c8d5ce150c221177cd4e0890b037f9bed1c`.
Two independent upgrade-copy sequences and their restarts use development
JAR `cf077ef7...`, not the later logout-cleanup artifact.

The fixture includes running and energy-paused electrolyzers, a supplied
sealed Moon room, fueled Earth and landed Moon rockets with inventories,
station ownership/membership, missions and research. The report distinguishes
legitimate processing/clock changes from identity and resource conservation.
Full artifacts and report hashes are in [evidence-index.json](evidence-index.json).

## Final-candidate procedure

1. Hash the immutable original archive and every original world file.
2. Copy the stopped world; never migrate or repair the original fixture.
3. Install the hash-checked candidate, start/save/stop, then reopen twice.
4. Compare machine inventory/fluid/progress, atmosphere supply, rocket
   identities/snapshots/fuel/positions/seats, station rights, mission claims,
   research and schemas using the existing field-level projections.
5. Verify backups and original hashes; retain failures and reject unsupported
   future/downgrade formats without overwriting recoverable source data.

No direct 1.12.2 import or downgrade support is promised. Development testing
must use backed-up world copies. This historical migration result does not
approve the current passenger changes or a future release candidate.
