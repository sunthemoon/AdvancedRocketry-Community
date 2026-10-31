# TASK W226-review: independent read-only review of the gas charge pad's finite canister exchange

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w226` at `5018a9678`, cut from `f2261aefe`; 4 commits, 34 files (domain, adapters, registration, generated resources, two new textures, tests, record). Review worktree `D:/GitHub/arce-v180-w226-review`, a detached checkout of `5018a9678` |
| Task it answers | [TASK-W226](TASK-W226-PAD-O1-CANISTER.md), Root decision R217, owner decision PAD-D4 ("O1：单人房主用有限罐交换（推荐）") |
| Record | `docs/work/v1.8.0-root-queue/records/W226.md` (the design at its top); the R-027 and ADR-066 drafts in `D:/GitHub/ARCE-Task-Evidence/root-queue/W226/W226-drafts.md`; the evidence index `W226/artifact-index.json` |
| Design | `records/W174-DESIGN.md`, section 9 (O1) and W174-R13 |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W226-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The decided behaviour.**
   - The pad fills one empty canister with exactly 1,000 mB, and the bank and the output are committed together in
     one pad root.
   - The player-local refill puts exactly 1,000 units into the matching tank or an EMPTY one and returns one empty
     canister. It refuses, with nothing debited, when there is no room or no return slot.
   - There is no automatic charging, and no protocol for dedicated or LAN players (a later task).
2. **Conservation.** Trace every path that moves gas or canisters and confirm that each one conserves both:
   - the pad's slots through menus, hoppers and other automation;
   - creative mode, a full inventory, stacking and merging of the returned canister;
   - death, logout or dimension change during use, chunk unload, and breaking the pad (its contents leave exactly
     once).
   Say which paths you traced in the code and which the tests cover.
3. **Persistence and protection.**
   - The schema-1 `arce_gas_charge_pad` root: bounds, unknown or corrupt data kept unchanged with the pad refusing
     work, and the `GuardedChunkSaves` path.
   - The `DeviceProtection` change, with no change for other devices.
   - The existing roots of suits, tanks and canisters keep their format.
4. **Server authority.** No client-trusted decision. Any new packet or menu action checks the player, distance,
   state and loaded chunks.
5. **Assets and data.**
   - How the two new pad textures were made, and whether they are recorded as the project requires for NEW art.
   - The recipe, loot table, language entries and models, and that the generated resources match `runData` output.
6. **Tests.** They failed first, and they would catch a gas or canister count that is off by one in either
   direction. The GameTests and the dedicated and integrated restart checks are what the record says.
7. **The drafts.**
   - They match the code and follow AGENTS.md section 3.3 (scope, duration, log volume, recovery). Draft 2 adds
     English text to a Chinese risk cell: say whether that is acceptable.
   - The risk register changed after the drafts' base (W224's R-030 mitigation, merged at `b0b28bd2f`), so the
     audit's base pin will fail. Check that each anchor still occurs exactly once in the shared head `f30a7a9a2`.
8. **Nothing weakened.** No bound, budget, format or assertion is loosened. Check for merge conflicts against
   `f30a7a9a2` and against W225's branch `codex/v1.8.0-w225`.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:

- the new and changed test classes;
- the `atmosphere`, `equipment` and `fluid` test packages.

Targeted unit runs need no heavy-JVM lock. Check that 6 GiB of memory is free first. Do not run a GameTest, native
case, runData, full build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W226-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
