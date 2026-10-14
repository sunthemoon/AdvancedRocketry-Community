# TASK W37J: reconcile a pending respawn backup at the save boundary and at the clone

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w37-sleep` (at `a7b2829b` plus this task) |
| Branch | `codex/v1.8.0-w37-sleep` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W37HI-review/REVIEW.md`, SHA-256 `eaeb57cf9bbccd47ebaa9e173a10df9cb9a47eb4ea1b7d23e1cec571460a4a73`. Verdict BLOCK: F1 High, F2 Medium, F3 Low |
| Record | `docs/work/v1.8.0-root-queue/records/W37J.md` (new) |

Read the whole review and these files:
- `records/W37F.md`, `W37H.md` and `W37I.md`, with their design sections;
- [TASK-W37H](TASK-W37H.md) and [TASK-W37I](TASK-W37I.md).

The rules decided so far still hold:
- a restore writes only into the exact cleared tuple;
- only a changed tuple supersedes a backup, and a foreign null clear does not;
- the decision runs at `ServerTickEvent` END at LOWEST, with the `DISCARDED` final check;
- the End exit is judged.

Write a short design section at the top of your record **before** the code. Give each finding its
own commit, with its tests.

## Root's decisions

1. **F1: reconcile at the real save boundary.**
   - Add a listener for Forge's `PlayerEvent.SaveToFile`. Verify with `javap` that
     `PlayerDataStorage.save` posts it before writing, for the disconnect path
     (`PlayerList.remove`) and for ordinary saves.
   - When the player being saved holds the cleared tuple while a backup is pending, and the
     PlayerList's current player for that UUID is a different object (the unadopted replacement)
     holding a non-cleared tuple:
     - copy that latest tuple into the saved player;
     - remove the backup;
     - put any retained opaque root back under its key.
   - Keep the restriction: never overwrite a saved player's tuple other than the cleared one.
   - Also make W37I's END-tick copy follow later changes. While the connection is unadopted, the copy
     may update the old player again, but only if the old player holds a tuple that this service
     itself copied earlier. Track that in transient lifecycle state, and say what happens to it at
     an abnormal stop.
   - **Native tests**, each checking the saved player file and its readback after reconnect:
     - a setter immediately followed by logout;
     - a foreign setter that runs before the throwing listener aborts dispatch;
     - two newer setters before logout.
2. **F2: judge supersession from the original player at the clone.**
   - In `PlayerEvent.Clone` (and the respawn path), compare the pending backup against the original
     player's tuple, `event.getOriginal()`.
   - If the original holds a non-cleared tuple, the backup was superseded before the native read:
     remove it, and never restore it into the replacement.
   - A freshly constructed replacement's default tuple is never evidence that a backup governs.
   - **Native tests:**
     - after corpse removal, `/spawnpoint` then respawn with another listener cancelling the native
       setter;
     - the same with no arrival found at the new point.

     In both, the old backup must not come back.
3. **F3: correct the risk text in your record.**
   - The 1 KiB, nine-node bound covers the core, not a retained opaque root.
   - An unreadable or future root does not mean an empty native point.
   - Fix recovery step 7.
   - Limit "an abnormal stop changes nothing" to what it actually covers.

If either correction cannot be made without a Mixin, stop and say why in the record.

## Commands

- The whole unit suite once.
- `runData` under the lock, only if the language strings change.
- `runGameTestServer` under the lock, then the log checker. It must pass with no required failure.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- W37b, status files, the ledger, ADRs and the risk register (Root).

## Amendment (Root, 2026-10-14, after the `SaveToFile` blocker in `records/W37J.md`)

`SaveToFile` fires after the `.dat` replacement, so decision 1's hook is replaced.

1. **The boundary is `PlayerEvent.PlayerLoggedOutEvent`.**
   - `PlayerList.remove` posts it before it saves. The round-3 review cites the offsets as 1 and 18;
     verify them with `javap` and record them.
   - On that event, reconcile the departing player object when all of these hold:
     - it is the unadopted old player;
     - a backup is pending;
     - the PlayerList's current player for the UUID is a different object holding a non-cleared
       tuple.
   - The reconciliation copies the replacement's latest tuple into the departing player, then removes
     the backup and puts back any retained opaque root.
   - Keep the restriction: copy only when the departing player holds the exact cleared tuple, or the
     tuple this service itself copied into it earlier in this session (transient, lifecycle-owned).
2. **The END-tick copy follows later changes** under the same restriction. This covers two newer
   setters before logout.
3. **Prove that the other saves are already correct.** With `javap`, check which player object each
   save path writes during the unadopted window:
   - periodic `PlayerList.saveAll`;
   - server stop through `PlayerList.removeAll`.

   They should write the PlayerList's replacement, which holds the latest tuple. If one writes the
   old player instead, stop and report.
4. **F2 (the `Clone` original) and the native tests are unchanged.** The logout tests check the
   saved file and its readback after reconnect.
