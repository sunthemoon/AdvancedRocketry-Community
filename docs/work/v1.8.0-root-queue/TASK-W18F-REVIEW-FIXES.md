# TASK W18F: fix the W18 review findings F1, F2, F3 and F5

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w18-beacon` (W18 plus the shared branch, `8b8a3a32`) |
| Branch | `codex/v1.8.0-w18-beacon` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W18-review/REVIEW.md`, SHA-256 `7a65b5f1d81f738ea9b1ff101f6d3eea64d0e7f6ea31f3e3034dee904c6e63ff`. Verdict MERGE-WITH-FOLLOWUP |
| Record | `docs/work/v1.8.0-root-queue/records/W18F.md` |

Read the whole review first. Fix each finding in its own commit, together with its tests.

## Root's decisions

1. **F1: operator-only NBT.** Override `onlyOpCanSetNbt()` to return `true` on
   `PlanetaryBeaconBlockEntity`. Add:
   - an assertion like `ForceFieldGameTests` line 308;
   - a GameTest that places a beacon item carrying a `BlockEntityTag` without operator
     rights, and checks that the beacon is fresh and has no owner.
2. **F2: phantom records are removed.**
   - Use the review's bounded rule: a record is removed when its own position is loaded and
     `BeaconWorld.stale` proves it stale.
   - Run the check in one bounded place. Root's choice is the finder snapshot: at most the
     viewer's 32 records, loaded chunks only, with no chunk loading or ticketing.
   - Add a GameTest for the crash-before-autosave case: a saved record, the beacon gone and
     the chunk loaded. The record must be removed, and the owner's slot freed.
3. **F3:** a refused update must not leave a disabled or unformed beacon recorded as active.
   Fix it, and test it.
4. **F5: documentation.** Correct the record, the ADR-065 W18 note and R-028 in the four
   places the review lists, and wherever they claim a removal path that does not exist.
   Keep R-028's ID; Root renumbers at merge if needed.

The review's F4, F6, F7 and F8 are follow-ups; they are not part of this task. Root lists
them in the queue.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- Other beacon or finder behaviour.
- Status files and the ledger.
