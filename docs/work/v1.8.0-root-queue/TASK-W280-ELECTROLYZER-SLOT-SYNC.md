# TASK W280: the electrolyzer screen's slots must follow the server inventory

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w280`, branch `codex/v1.8.0-w280`, created by Root from the shared head |
| Decision | Root decision R311 (QUEUE row W280), from finding 2 of W278's scripted single-player round 1 |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md` section 4, finding 2; screenshots `e00` to `e08` under `evidence/r01-solo/screenshots/`; the server's NBT receipts in `evidence/r01-solo/results.jsonl` |
| Record | `docs/work/v1.8.0-root-queue/records/W280.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W280/` |
| Code | `machine/electrolyzer/` (`ElectrolyzerMenu`, `ElectrolyzerMenuItemHandler`, `ElectrolyzerMenuData`, `ElectrolyzerBlockEntity`, `ElectrolyzerStorage`) and `client/ElectrolyzerScreen` |

## What a real client showed

On a dedicated server with one real client (the hosted JAR of commit `6f3bd461b`): a bucket of water poured in, two
empty canisters in the input slot and two redstone in the charge slot. When the process finished, the output slots
looked empty, yet clicking them took the hydrogen and oxygen canisters. The charge slot kept showing two redstone,
also after the screen was closed and reopened, while the block entity's NBT said `inventory: {Items: []}` and
`energy: 2000`: the redstone had been consumed. Clicking the shown redstone gave nothing and the player's inventory
did not change, so no item was created; the client shows a stale copy.

## What to do

1. Reproduce it in a test that fails on the shared head: the menu's client-side slot contents (what the container
   synchronisation sends, as a `ServerPlayer` with the menu open would receive it) must equal the server inventory
   after the machine consumes a charge item and after it produces outputs, including when the screen is reopened.
   Prefer a GameTest with a real menu and mock server player, or a unit test of the synchronisation path, whichever
   reproduces the stale state the client showed.
2. Find why the menu does not see the machine's own inventory changes (for example, a menu item handler that
   copies rather than views the storage, a missing `setChanged` or broadcast, or slots bound to a snapshot) and fix
   it at that cause. Server authority stays as it is: clicks act on the server inventory, and no item may be
   created or lost; keep or add a conservation check for that.
3. Check the other v1.8 machine menus built the same way (search for the pattern you fix) and list in the record
   which share the cause; fix only the electrolyzer here, unless the fix is in shared code.

## Verification

The new test failing before and passing after; the electrolyzer GameTests (`ElectrolyzerKernelGameTests`,
`ElectrolyzerAutomationRun` and any menu tests); `./gradlew build` with all unit tests; the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B scripts/validate_repository.py
--require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The other round-1 findings (W281, W282), screen layout and textures, recipes, energy values, persistence format,
the ledger, the status file and QUEUE.
