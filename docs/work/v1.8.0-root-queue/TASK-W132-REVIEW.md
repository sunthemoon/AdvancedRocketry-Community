# TASK W132-review: independent read-only review of W132 (cargo leaf D-E1, item admission and reconciliation) before merge

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents or
subagents. Codex wrote W132, so this is a fresh Claude session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w132` at `4bf9ad87` (base `bcb3b81d`; tested source `af6f1584`). Your review worktree `D:/GitHub/arce-v180-w132-review` is a detached checkout of `4bf9ad87`; read the diff with `git diff bcb3b81d..4bf9ad87` there |
| Task it answers | [TASK-W132](TASK-W132-CARGO-D-E1-ITEM-ADMISSION-RECONCILIATION.md) |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 3.2, 3.3, 4 (the stop-point table), 5.1 and 5.3, and section 6's D-E row |
| Models it mirrors | The fluid leaves [records/W117.md](records/W117.md) (C-E1, admission and the queue) and [records/W124.md](records/W124.md) (C-E2, reconciliation, with W124F); the item leaves [records/W110.md](records/W110.md) (D-K), [records/W118.md](records/W118.md) (D-P, with W118F) and [records/W120.md](records/W120.md) (D-A, with W120F) |
| Record | `docs/work/v1.8.0-root-queue/records/W132.md` on the branch (the plan is at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W132/` (`verification-summary-final.json`; 24 native cuts, 48 restarts) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W132-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Exactly once.** For an item load and an item unload, walk D2-C0 to D2-C8 and every restart in section 4's table,
   including partial moves. Can any order of publication, store, crash or restart credit the rocket or the device twice,
   debit twice, duplicate or lose an item stack (count, item, NBT)? The rocket side must classify by the composite
   signature, the device side by its item buffer signature (all four slots), so an AFTER side is never read as BEFORE.
2. **Replay before publication** (W124F's rule): the after snapshot is recomputed through the budgeted replay and compared
   with the frozen digest before either endpoint publishes; a mismatch holds and publishes nothing. Mixed states roll the
   BEFORE side forward; AFTER on both sides goes to the barrier; retirement and C8's stale-marker pruning match C-E2.
3. **The item device adapter and permit:** observe, change, prune and disk decode for the item cargo devices; the permit
   is one-use, server-thread only, and publishes the whole four-slot buffer delta and the marker together; no public
   setter or other path changes the buffer while an operation holds it.
4. **Typed item-NBT readback:** bounded decode; hashing and the composite computed off-thread on an immutable decode and
   compared on the server thread; nothing on the server thread newly unbounded; an item whose NBT does not round-trip
   holds rather than publishes.
5. **R39:** the native stack check runs before `RocketCargoMutation.apply` and before the device buffer changes, at
   capture and at readback; a refusal holds only that operation (REPAIR with `OTHER_STATE`), and other operations
   continue.
6. **Admission (item 1):** the item switches stay false in production; owner-thread preparation tickets, native capture
   and `prepareCargo`; the item kinds share the resource FIFO and the turn tokens, and an unused token is yielded on
   terminal attempts and cancellation (W117F); the item block's 20-tick pacer and turn registration across load, unload
   and rebind do not leak registrations.
7. **W118's carried items (item 2):** one-shot eligibility uses `EscrowCargoRefusals.capacity` and its typed catches, so
   one-shot and staged capture agree; `UNRESOLVED_LOOT` and `NO_CARGO_STORAGE` map to their own device statuses and are
   never retried as another candidate; the selection budget is real (bounded per tick, resumable, no whole-snapshot
   recheck in a loop); `RocketCargoWorkBudget.Exceeded` is caught at admission without side effects.
8. **F1:** named (transfer-guarded) item cargo stays held with nothing lost, pinned by a test; W132 adds no variant of
   the F1 fix (R48's D4 stays separate).
9. **Nothing weaker:** list every edited pre-existing test or fixture file and say whether any assertion, bound, budget
   or timeout became weaker; the fluid tests are unchanged; `EscrowCoordinator` and `RocketTransferService` (796 lines)
   did not grow; new classes stay under 500 lines; no saved format, schema, hash or dictionary change; any new WARN or
   ERROR a GameTest drives has its own exact manifest entry.
10. **The native evidence:** do the 24 cases cover section 4's stop points in both directions, including the exact
    source-only and destination-only stores (W124F's R2)? Does the oracle compare raw totals before recovery and
    conservation after it, and does it compare item identity and NBT, not only counts?
11. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W132's base
    added Root's records and W122 (a test-only fix in `gametest/PlanetaryAdmissionGameTests`, `EntitySections` and
    `EarthReturnPadFixture`). Preview with `git merge-tree` and say what the merge must keep. W125 (the F1 fix, fuel
    side) and W131 (C-E3) are not merged yet; note any file W132 shares with them
    (`git diff bcb3b81d..codex/v1.8.0-w125 --stat`, `git diff bcb3b81d..codex/v1.8.0-w131 --stat`) as a merge risk only.
12. **The record** against the code and the evidence.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W132's
new and changed unit classes, each alone, then the `resourceescrow` and `rocket.cargo` packages; and
`python -B -m unittest discover -s tests -p "test_item_cargo_reconciliation_runner.py"`. You may add a probe test to
check a finding; keep each probe in the output folder. Before each Gradle run, check that at least 6 GiB of memory is
free and wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no heavy-JVM lock.
Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W132-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
