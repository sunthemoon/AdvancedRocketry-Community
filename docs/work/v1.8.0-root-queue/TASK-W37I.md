# TASK W37I: supersede a pending backup only by a changed tuple

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w37-sleep` (at `e8880994` plus this task) |
| Branch | `codex/v1.8.0-w37-sleep` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W37I.md` (new) |

Read [TASK-W37H](TASK-W37H.md) and `records/W37H.md`, especially the residual F1 cases 1(a) to 1(c)
and its note on the narrower rule.

## Root's decision (replaces W37H decision 1's foreign-setter rule)

W37H counts every observed foreign `PlayerSetSpawnEvent` as superseding a pending backup. That
loses the player's point when another mod's `PlayerEvent.Clone` listener copies the cleared tuple
onto the replacement player. Losing the player's own point is worse than overriding another mod's
explicit reset of an already-refused point, so:

- **When a backup is superseded.** It is superseded only when the native tuple differs from W37F's
  exact cleared tuple: Overworld, null position, angle 0.0F, not forced. That check runs at every
  decision and restore point.
- **The event observation goes.** Remove the transient "foreign setter seen" state. A setter that
  writes the cleared tuple, or one that another listener cancels, therefore does not count. Nothing
  depends on transient state across an abnormal stop.
- **The trade-off.** If another mod clears the point of a player whose point W37 has already
  refused, the restore puts the original point back. Say this in the record and in the risk text.
- **Tests.**
  - A `PlayerEvent.Clone` listener that copies the old player's (cleared) tuple onto the
    replacement through `setRespawnPosition`: the point is restored.
  - A foreign setter with a new point while a backup is pending (dead tick, respawn event before
    ours, after ours, `/spawnpoint`): the new point wins. These exist; keep them passing.
  - The W37H test of a foreign null clear changes its expectation: the backup governs and the point
    is restored. State the change of expectation in the record. This is Root's decision, not a
    weakened assertion.
- **Also check one W37H residual.** In W37F's interrupted-listener window, a newer point set on the
  replacement is lost and the superseded backup is removed. With the tuple-only rule, find whether
  that loss still happens.
  - If it does, fix it: a restore must never overwrite a tuple other than the cleared one.
  - Or explain precisely why it cannot be fixed without a Mixin.
- **Risk text.** Update the record's proposed risk text to match.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. It must pass with no required failure.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- W37b, status files, the ledger, ADRs and the risk register (Root).
