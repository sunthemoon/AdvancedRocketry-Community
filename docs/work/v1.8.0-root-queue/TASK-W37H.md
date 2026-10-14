# TASK W37H: fix the W37F review findings (a newer point wins, the End exit, fresher decisions)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w37-sleep` (at `f8f75ba3` plus this task) |
| Branch | `codex/v1.8.0-w37-sleep` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W37F-review/REVIEW.md`, SHA-256 `bc7d5b2bfa66f4bb6014fb90e7446f94a071419cdf1906c9bee951ffe4fed028`. Verdict BLOCK: F1 High; F2 Medium; F3 to F8 Low |
| Record | `docs/work/v1.8.0-root-queue/records/W37H.md` (new) |

Read the whole review, `records/W37F.md` (with its design section), `records/W37G.md` and
[TASK-W37F](TASK-W37F-REVIEW-FIXES.md). This slice handles a player's saved respawn point, which is
player data.
- Write a short design section at the top of your record **before** the code.
- Root will have the result reviewed independently.
- Give each finding its own commit, with its tests.

`f8f75ba3` already contains Root's fix for the two W38 player-fall failures (`a39ecbec` on shared).
The full native suite should now pass on this branch.

## Root's decisions

1. **F1: a newer point always wins over a pending backup.**
   - **Where a restore may write.** A restore, from any path (decision, respawn event, login, living
     tick, server tick), writes only when the native tuple is exactly the cleared tuple that
     W37F's own clear writes: Overworld, null position, angle 0.0F, not forced.
   - **Superseded by a different tuple.** Any other native tuple supersedes the backup. Remove the
     backup without writing the fields. Put any retained opaque root back under its key.
   - **Superseded by a foreign setter.** Observe non-cancelled `PlayerSetSpawnEvent`s at
     `EventPriority.LOWEST` for a player whose UUID has a pending backup, regardless of
     `connection.player`. W37F's own writes post no event, so every observed event is foreign.
     - Record "foreign setter since the clear" in transient, lifecycle-owned state.
     - At the next decision or restore point, if the tuple is still the cleared tuple and a
       foreign setter was seen, treat it as another party's explicit clear: remove the backup.
     - If no foreign setter was seen, the cleared tuple is W37F's own clear, and the backup still
       governs.
     - Say in the design section what happens to that transient flag across logout. At login, the
       tuple check alone decides.
   - **The pending backup is not re-decided against a newer point.** A dead tick with a pending
     backup and a superseded tuple removes the backup. It judges the new tuple as a fresh saved
     point.
   - **Native tests:**
     - `/spawnpoint` through the real command dispatcher on a dead player with a pending backup,
       before and after corpse removal;
     - a respawn-event setter that runs before ours, and one that runs after ours;
     - a foreign null clear while a backup is pending;
     - the existing coexistence and interruption tests still pass.
2. **F2: judge the End exit like a death respawn.**
   - Decide for a connected player with `wonGame` set, on the same boundary as a dead player
     (point 3).
   - Set aside a refused point, and restore it afterwards, by the same rules.
   - Replace the GameTest that pins the exclusion with one that proves the real End exit
     (`changeDimension` from the End, then the respawn packet) is judged:
     - refused: the player lands at the Overworld spawn with the point restored;
     - accepted: the player lands at the saved point.
   - Update `docs/CELESTIAL-DATA-GUIDE.md` to match.
3. **F3 and F4: decide at the end of the server tick.**
   - Move the per-tick decision to one `TickEvent.ServerTickEvent` END listener at
     `EventPriority.LOWEST`. It iterates the PlayerList's players that are dead, or have `wonGame`
     set, and are not FakePlayers. This also removes the dependence on `doTick`'s spectator guard.
   - Keep the `DISCARDED` final check.
   - Correct the record's freshness argument. What still runs between the decision and the read:
     the inter-tick task queue (packets, commands, `/reload` completion) and the config watcher.
4. **F5: diagnostics.**
   - One WARN per player per server session for each of:
     - an unencodable native tuple, where the refusal is skipped;
     - a foreign non-compound `PlayerPersisted` parent, where the refusal is skipped;
     - an unreadable or future backup root.
   - Each WARN gives the UUID, the case and the recovery pointer.
   - Add each to `scripts/gametest_expected_log.json` if a test triggers it.
   - Behaviour stays fail-open as W37F designed. Say so in the record.
5. **F6: stable tokens.** Persist explicit, pinned outcome tokens, not `name()`. A known schema 1
   backup with an unknown outcome token decodes with a generic message and keeps the point. Nothing
   has shipped yet, so the codec may change freely before merge. Unit-test both.
6. **F7: no per-tick rewrite.** Do not re-encode or rewrite the backup when the stored tuple and
   outcome are unchanged. State the per-dead-player probe cost in the record. No global cap: the
   cost is bounded by the number of dead players.
7. **F8: no change.** Root accepts `loadedVent`'s completed-chunk read for every atmosphere consumer,
   and records it in the ADR note. Add one GameTest showing that a vent in a visible but incomplete
   chunk reads as no provider without loading it, if the existing `IncompleteFullChunk` fixture
   allows it.
8. **Risk text.** Rewrite the proposed risk text in your record using the review's section 5:
   - the six points, with F1 now fixed;
   - concrete offline recovery steps.

   Root puts it in the register and in ADR-066.

## Commands

- The whole unit suite once.
- `runData` under the lock, only if the language strings change.
- `runGameTestServer` under the lock, then the log checker. It must pass with no required failure.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- W37b (`natural=true`, D1 outcome 3).
- Status files, the ledger, ADRs and the risk register (Root).
