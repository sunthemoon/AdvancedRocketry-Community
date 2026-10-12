# TASK W04G: force field follow-ups N1 to N3 from the W04F re-review

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w04g-force-field` (created by Root from the shared branch after W04 is merged) |
| Branch | `codex/v1.8.0-w04g-force-field` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W04F-review/REVIEW.md`, SHA-256 `49319b9caa76a894835008101aac6bd7ab60ff08e8e89567826a2499605d3735`. Verdict MERGE-WITH-FOLLOWUP. |
| Record | `docs/work/v1.8.0-root-queue/records/W04G.md` |

Read the review's N1 to N6 first.

## Scope

Fix each item in its own commit, together with its tests.

1. **N1 (Medium): projectors near the build-height limits never decide their power.**
   - In `FieldCellView`, a position outside the dimension's build height reads as
     `VOID_AIR` with no block entity and no chunk access, exactly as
     `Level.getBlockState` does.
   - Add native tests at `maxBuildHeight - 1`, `maxBuildHeight - 2`, `minBuildHeight` and
     `minBuildHeight + 1`, in a 256-high mod dimension and in the Overworld.
2. **N2 (Low): third-party callbacks can crash the tick.**
   - Treat any `RuntimeException` from the stock power query as "no decision" for this
     visit.
   - Log one bounded diagnostic per projector, never one per tick.
   - Never let a third party's swallowed `Unavailable` turn into a decision. Detect it
     after the query, for example with a flag set when a refused read happens.
3. **N3 (Low): a stale deferred-drop flag can mint a second projector.** Clear the flag at
   the start of `onDestroyedByPlayer`, as `onBlockExploded` already does. Bind the flag to
   the arming tick and position. Test the review's sequence: `dropResources` without
   `setBlock`, then a player break gives exactly one item.
4. N4 to N6 are optional. Fix them only if each is a few lines and its test is clear;
   otherwise list them in your record as still open.

## Tests and commands

- The tests above.
- The whole unit suite once. Each new class must also pass when run alone.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- Other force-field behaviour.
- Status files and the ledger.
