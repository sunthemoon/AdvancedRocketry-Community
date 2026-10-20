# TASK W118F: W118's review follow-ups (W118-R1, R3, R4, R5) under Root decisions R44 and R45

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W118](TASK-W118-CARGO-D-P-CONTAINER-MUTATION.md),
W118's record (`docs/work/v1.8.0-root-queue/records/W118.md` on your branch) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W118-review/REVIEW.md` (SHA-256 `666c3d71…3104`) with its probe in that folder,
first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w118`, branch `codex/v1.8.0-w118` at `d5831ba1`; continue on that branch |
| Record | append a "W118F" section to `docs/work/v1.8.0-root-queue/records/W118.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W118F/` |

## Root decisions

- **R44** (recorded in QUEUE): each half of a vanilla double chest is a 27-slot `minecraft:chest` or
  `minecraft:trapped_chest` payload and a cargo target; only other `ChestBlockEntity` subclasses refuse with
  `NO_CARGO_STORAGE`.
- **R45 (2026-10-20):**
  1. W118-R1 is fixed before merge: item preparation gets its own first-step reservation, sized so the largest legal
     container payload fits the unchanged 16 MiB per-tick budget; no budget, bound or lifetime changes.
  2. W118-R4: an unload skips a container slot whose stack fails the stack checks (it stays where it is, as section
     5.3 says of a stack that fails) and offers the next nonempty slot, so one oversized stack does not block a chest.
  3. W118-R5: a load first looks across all containers, in canonical block order, for a slot with the same item and tag
     and room, and only then for the first empty slot, as the fluid selection's two passes do.
  4. W118-R2 (the refusal mapping with W117) is Root's at the merge. W118-R6 and R7 go to D-A and D-E.

## What to do, each in its own commit with a test that fails before the change

1. **R1:** the reservation fix, with a test that prepares a chest at the largest legal payload (and one just above the
   old 16 KiB point) through the ticket to READY, and a test that an over-budget step reports a status rather than
   throwing out of the ticket.
2. **R3 under R44:** drop the `type` check in `RocketItemCargoMutation.storage()`; convert the four assertions that
   depend on the refusal into assertions about the accepted halves (do not delete them); add a test that a double chest
   whose halves were mutated restores through the unchanged adapter, both halves exact.
3. **R4** and **R5** as decided, each with its test; keep every existing assertion that does not depend on the old order,
   and list any that does, with the decision that changes it.
4. Update the record.

No saved format, schema, hash, bound, budget, lifetime or timeout changes; no existing assertion is relaxed. Stop and
report if any of these seems needed.

## Verification

Each changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker on
committed source, under the heavy-JVM lock, releasing it and deleting `build/gametest/world` in the `finally` of the
same detached script that took it (WORKER-RULES). If the checker refuses only the host-load warning count again, record
it with the count and the number of workers running; Root reruns the GameTest when the machine is quieter. Check that
6 GiB of memory is free before each Gradle run.

## Not in this task

The refusal mapping (W117 and Root's merge), D-A, D-E, ADRs, the risk register, status files and the ledger (Root).
