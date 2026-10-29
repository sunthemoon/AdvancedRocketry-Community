# TASK W194: the satellite registry writer's missing pending file

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w194` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w194` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W194.md` (new; the design note at its top if code changes) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W194/` |
| The failure | W164's complete GameTest under R156: its record's R156 section on `codex/v1.8.0-w164` (`77ccb436d`), and the retained logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W164/r156-full/` (checker log SHA-256 `7d1e8d6f…364e`) |
| Background | [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) and [records/W129-DESIGN.md](records/W129-DESIGN.md) |

**Root decision R162.** One complete GameTest logged, in batch `endgame_menu_member`,
`ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=FAILED sequence=14331 epoch=99 durable=99`, with a stack through
`SatelliteRegistryCapture.write` and `CheckedSavedDataFile.replace` ending in `NoSuchFileException` for
`advancedrocketrycommunity_satellite_missions.dat.arce-pending`. No recent shared-head hosted run has shown it. This is
a persistence slice.

## What to do

1. **Find the cause.** Who creates, moves and deletes the `.arce-pending` file, on which threads, and what can remove
   or move it between its write and its replace: a second writer of the same file, a synchronous save, the checked
   writer's own retry, or a test that resets satellite storage. Reproduce it in a unit test if you can.
2. **Say what the failure costs.** After `outcome=FAILED`, is the registry state retried and saved later, or can a
   mission or satellite be lost or duplicated? Check against ADR-074's acknowledgement rules.
3. **Fix the cause** if it is in production code: a design note first, then a regression that fails first. If it is in
   a test fixture, fix the fixture. Do not add a manifest entry to hide the line, and change no timeout, bound or
   assertion.

## Verification

- the new tests alone, failing first; the whole unit suite;
- the satellite and endgame batches three times under the heavy-JVM lock.

Root's hosted run on the pushed branch stands in for a complete local GameTest. An independent read-only review follows
a production fix.

## Not in this task

Other writers, ADRs, status files and the ledger.
