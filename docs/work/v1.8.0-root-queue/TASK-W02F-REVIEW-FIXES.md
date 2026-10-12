# TASK W02F: fix the W02 independent review findings

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w02-propulsion` (at the W02 candidate `2260e575`, which merges the shared branch and Root's config-count and log-expectation fixes) |
| Branch | `codex/v1.8.0-propulsion` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W02-review/REVIEW.md`, SHA-256 `4b9127e665e4dcc7074aaf37b2896faafbbfee62c49a003f8d5fc19105401d7a`. Verdict BLOCK: findings F1 to F4. |
| Record | `docs/work/v1.8.0-root-queue/records/W02F.md` |

## Why

W02 added typed propulsion and a hand-held fuel exchange (filling and unloading with
containers). The independent review traced two Critical duplication paths, where the
new exchange meets the existing transfer recovery, and two Medium problems. Read the
whole review first: it gives exact scenarios and line references.

## Root's decisions

Fix each finding in its own commit, together with its tests.

1. **F1** (recovery rebuilds a drained, landed rocket).
   - Recovery must recognize the existing destination entity by its logical rocket
     identity, whatever its current state (LANDED, FUELED or ASSEMBLED, empty or not).
     It must never rebuild an older, resource-bearing arrival beside it.
   - Landed-reservation release and disassembly must retire the retained record in the
     same way.
   - Test: drain a landed rocket over several containers, do a clean restart, then
     disassemble and restart again. Count the entities, both banks and the player's
     containers at every step.
2. **F2** (an exchange during pending source recovery is replayed).
   - Block every fill or unload, and every other resource mutation, while this logical
     rocket has an unsettled transfer journal entry or a pending recovery, such as
     WAITING_FOR_PASSENGERS.
   - Give the player a clear refusal message.
   - Offline-passenger reconnect still works.
   - Test: recover a source with an offline passenger, refuse fill and unload, then
     restart repeatedly. Conservation holds across the player's containers and both
     banks.
3. **F4** (a catalog change strands stored Fluid).
   - Validate fuel entering the rocket against the current catalog and allowlists.
   - Allow a lossless drain of Fluid already stored, whatever the current catalog says.
     It needs only a registered Fluid that a container can hold.
   - Test: replace the catalog, then unload an old bank larger than one container.
4. **F3** (a whole-unit container cannot refill a partial balance). Do not fix it here.
   The C17a loader task will add mB-precise refuelling. Add one sentence to your record
   stating the current limitation. Do not round away any remainder.

## Tests and commands

- New JUnit and GameTests as listed above.
- The whole unit suite once (`test`, no filter).
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.
- If the known vanilla IOWorker CME, or the atmosphere boundary startup budget failure,
  appears, retry once and record it. Do not chase either.

## Not in this task

- Loader variants, the monitor and the linker.
- Partial refuelling (F3).
- Other content.
- Status files and the ledger.
