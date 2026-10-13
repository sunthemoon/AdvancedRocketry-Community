# TASK W26: beacon follow-ups F4, F6 and F7 from the W18 review

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w26-beacon-followups` |
| Branch | `codex/v1.8.0-w26-beacon-followups` (local only; Root merges) |
| Kind | Parallel bug fix |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W18-review/REVIEW.md`, SHA-256 `7a65b5f1d81f738ea9b1ff101f6d3eea64d0e7f6ea31f3e3034dee904c6e63ff`: F4, F6, F7 |
| Record | `docs/work/v1.8.0-root-queue/records/W26.md` |

Read F4, F6 and F7, and [records/W18F.md](records/W18F.md). Fix each finding in its own
commit, together with its tests.

## Root's decisions

1. **F4.** Defer the load-time reconcile until the first structure validation completes, and
   keep the last record while the structure is PENDING.
   - A chunk load must no longer write the record inactive and then active again.
   - Test with a GameTest across unload and reload: the record stays active, the index is not
     rewritten twice, and a finder snapshot taken in that window still lists the beacon.
2. **F6: the missing tests.** Add them, as unit tests or GameTests:
   - `/setblock` placement, which gives no owner, `NO_OWNER` and no record;
   - fake-player placement and control;
   - the copy rule (`DUPLICATE`) and the takeover rule;
   - an unload keeping the record, which then shows as last known;
   - a missing body;
   - the retained block-entity root through the native `load` and `saveAdditional`;
   - an unavailable index at the beacon and finder level;
   - the 8-per-tick budget and the 200-tick recheck;
   - the event wiring: logout, dimension change, respawn, and the datapack reload resend.

   F1, operator-only NBT, is already covered by W18F and W23.
3. **F7.**
   - Place the finder strip so that it never overlaps the vanilla boss bar overlay. Read
     the actual 1.20.1 `BossHealthOverlay` layout from the mapped jar with `javap`, and cite
     it.
   - Either move the strip below the bars while they are shown, or into a HUD position the
     W16 panel solver already reserves. Record which.
   - Add a pure layout test of the arithmetic.
   - V1 visual evidence is out of scope here. Say so in the record.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- Other beacon behaviour.
- Status files and the ledger.
