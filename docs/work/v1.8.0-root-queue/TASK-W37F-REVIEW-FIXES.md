# TASK W37F: fix the W37 review findings (respawn boundary, durable restore, sleep loading)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w37-sleep` (at `37908cb4` plus this task) |
| Branch | `codex/v1.8.0-w37-sleep` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W37-review/REVIEW.md`, SHA-256 `cfd61b7483ca6bc35e7f2bf2db5a9b851789ebb775efdfc7a5994ef4b665cc2d`. Verdict BLOCK: R1, R2 and R3 High; R4 and R5 Medium |
| Record | `docs/work/v1.8.0-root-queue/records/W37F.md` |

Read the whole review and `records/W37.md`. This slice handles a player's saved respawn point,
which is player data.
- Write a one-page design section at the top of your record **before** the code.
- Root will have the result reviewed independently.
- Give each finding its own commit, with its tests.

## First: merge the shared branch

Merge `codex/v1.8.0-classic-content` (`f58f4c9f` or later) into this branch before any change.
- It brings W33, W34, W36 and W38.
- Expected conflicts: `CommonConfigTest`'s count (shared is 95; with W37's two keys it becomes 97),
  `scripts/gametest_expected_log.json`, the v1.8 language files (regenerate them with `runData` under
  the lock) and `AdvancedRocketryCommunity.java`.
- Keep every assertion. Commit the merge on its own.

## Root's decisions

1. **A new respawn boundary, with no Mixin (R1).**
   - **Decide while the player is dead.** On each tick of a connected, dead `ServerPlayer`
     (`TickEvent.PlayerTickEvent`, END phase, logical server), evaluate the respawn decision for an
     off-world saved point.
     - Refused: set the point aside (point 2).
     - Accepted: put back a point you set aside earlier.
   - **Why this is fresh enough.** Player ticks run in the connection tick, after the Level ticks.
     The respawn packet is handled as a server task between ticks. So the last dead-tick
     decision is current when `PlayerList.respawn` reads the point.
   - **Verify that ordering with `javap`** and cite it:
     - that `ServerPlayer.doTick` keeps calling `Player.tick` after the corpse is removed (`KILLED`);
     - how `handleClientCommand` reaches `respawn`.

     If the ordering does not hold, stop and report.
   - **Keep the existing `DISCARDED` leave-event check** as the final check. It covers an
     immediate respawn before the corpse is removed, including `doImmediateRespawn`.
   - **Cover the End exit and a mod-called `respawn(player, true)`.** Either judge them the same
     way, or state exactly why each is excluded.
2. **Set aside and restore without a veto, durably (R2 and R3).**
   - **Clearing and restoring.** Use direct field writes through the existing access transformer
     (`src/main/resources/META-INF/accesstransformer.cfg`; you may add the `ServerPlayer` respawn
     fields there). Do not use `setRespawnPosition`. Another listener then cannot veto the
     refusal, and cannot veto the restore of the player's own record.
   - **The backup.**
     - Keep the exact original tuple in the player's persisted Forge data
       (`Player.PERSISTED_NBT_TAG`), which Forge copies across respawn.
     - Give it a schema version and bounds.
     - Store it before the point is cleared.
   - **Restoring.**
     - Restore on the respawn event, and retry at login and on later ticks while a backup is
       pending.
     - Verify the whole tuple, then delete the backup.
     - Never discard a backup at tick end or at server stop.
   - **Unreadable backups.** An undecodable or future backup is kept verbatim. Say what happens
     to it.
   - **Risk register.** In your record, propose the risk text for this backup and restore: its
     scope, how long it lasts, its logs, and how to recover. Root edits the register.
3. **Remove the bed-setter cancel from W37 (R4).**
   - With the current `natural=false` types, vanilla never reaches that setter, and an
     operation-scoped boundary cannot be proven here.
   - Remove the token and the artificial after-return test.
   - D1 outcome 3, which keeps the existing point on an off-world bed, moves to W37b. In W37b the
     `natural=true` change makes the native setter reachable, and W37b needs a real operation
     boundary.
   - Say this in your record. Keep the sleep refusal itself.
4. **Completed chunks only (R5).**
   - Sleep admission reads head and foot cells only after a completed-chunk check (`getChunkNow`,
     or the same check the respawn probe uses).
   - Audit the `loadedVent` helper the review names, and fix it if it has the same gap.
   - Make the supplied-room and PENDING test go through the production registration and a real
     `startSleepInBed`, if the fixtures allow it.

## Tests

- **Native GameTests** with real connected players:
  - death, then more than 20 dead ticks, then respawn;
  - a delayed respawn after `/kill`;
  - an immediate respawn;
  - the End exit (judged or excluded, as you decide);
  - another listener cancelling every `PlayerSetSpawnEvent`: the refusal still holds and the point
    is restored;
  - another respawn listener throwing before ours: the backup survives and is restored later;
  - a backup pending across a save and reload (log out and back in);
  - an unloaded refused destination, with no chunk loaded.
- **Unit tests** for the backup codec: bounds, schema, undecodable data kept.
- **Regressions:** the existing W37 tests pass, except the removed artificial setter test.

## Commands

- The whole unit suite.
- `runData` under the lock, only if the language strings change.
- `runGameTestServer` under the lock, then the log checker.

## Not in this task

- The `natural=true` type change and D1 outcome 3 (W37b).
- Other C18a rules.
- Status files, the ledger, ADRs and the risk register (Root).
