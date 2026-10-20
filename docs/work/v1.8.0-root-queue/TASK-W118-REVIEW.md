# TASK W118-review: independent read-only review of W118 (cargo leaf D-P) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. A Claude session wrote W118 and both Codex slots are
busy, so this is a fresh Claude session with no context from the author (as for W109-review, AGENTS.md section 13).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w118` at `d5831ba1` (base `97866069`; tested source `78b5968a`) in `D:/GitHub/arce-v180-w118`. Read it with `git -C D:/GitHub/arce-v180-w118 diff 97866069..d5831ba1`, `git show` and the file reader; never check out, commit or edit there |
| Task it answers | [TASK-W118](TASK-W118-CARGO-D-P-CONTAINER-MUTATION.md) |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 2.2 to 2.4, 3.2, 3.3 (with R40 and R41), 5.1 and 5.3 and section 6's D-P row; [records/W106.md](records/W106.md) and [records/W112.md](records/W112.md) (C-P, which D-P mirrors), [records/W110.md](records/W110.md) (D-K) |
| Record | `docs/work/v1.8.0-root-queue/records/W118.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W118/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W118-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

**Root decision R44 (2026-10-20), for context.** TASK-W118's "a large chest" meant section 5.3's other
`ChestBlockEntity` subclasses that the adapter captures with up to 54 slots. W118 read it as a vanilla double chest and
refuses its halves with `NO_CARGO_STORAGE`. Each half of a vanilla double chest is its own 27-slot `minecraft:chest` or
`minecraft:trapped_chest` payload, which section 5.3 accepts, so the halves are cargo targets; only other subclasses
refuse. Root will have this changed after your review. Say whether anything else depends on the double-chest refusal
(selection order across the two halves, restoration as a double chest, a test).

## What to check

1. **The mutation** (section 5.3): the frozen item delta applied to exactly one container slot and one buffer slot;
   every other block, the snapshot ID, propulsion and every aggregate bound unchanged; `Items` re-sorted by slot;
   `CustomName` and `Lock` kept; the codec round-trips; recomputation gives byte-identical hashes. Can any input change
   another slot, lose an item, duplicate one, or produce a payload the unchanged `VanillaContainerRocketAdapter` would
   not restore exactly? Is the shared replay helper the fluid path now uses behaviourally identical for fluids?
2. **Selection and partial moves:** the first-nonempty buffer slot and the same-item-with-room then first-empty
   container slot for a load, the mirror for an unload, `n = min(count, room)` with the item's own maximum stack size;
   merging only through D-K's merge identity (same registered item, equal tags, equal canonical digest), never display
   names. Check items with a max stack of 1 and 16, tags that differ only in key order, and a full container.
3. **Eligibility** (R39): structural and native stack checks at admission; every enclosing operation, device, snapshot
   and O1 bound; side-effect free before any sequence, queue, reservation or mutation. Does the new item eligibility
   share the fluid predicate's guarantees, and do `UNRESOLVED_LOOT` and `NO_CARGO_STORAGE` refuse before any work?
4. **The resumable preparation for items:** W112's tickets, budget, lock and one-use permit unchanged; the item
   dispatch cannot bypass a ticket check, the 40-tick lifetime or the session budget.
5. **The device root** `arce_rocket_item_cargo` with a `buffer` field: is it bounded, versioned and refused when unknown,
   so that D-A can adopt it without a migration?
6. **Tests and limits** (AGENTS.md section 5): no existing test, assertion, limit or timeout removed or relaxed; list
   every edited pre-existing file (`CargoTicketTestFixture` and the shared classes) and say why each edit is legitimate.
   Flag any class over 500 lines. Do the A1 tests drive the real adapter and permit?
7. **The merge.** W117 (C-E1, active) rewrites the refusal mapping in `EscrowCargoEligibility` and
   `EscrowCargoPreparedCapture`, which W118 also edits. Read the shared head (`git -C D:/GitHub/AdvancedRocketry-Community
   show HEAD:<path>`) and say what the merge must keep from each side, and how `UNRESOLVED_LOOT` and `NO_CARGO_STORAGE`
   should map once both are in.
8. **The record** against the code, including its GameTest runs: the third full run passed all 1,262 tests, and the
   checker refused only the host-load warning count (13 against 8).

## Rerun

Export the subject with `git -C D:/GitHub/arce-v180-w118 archive d5831ba1` into a new folder under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W118-review/` and, there only, run with JDK 17.0.7
(`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: the five new item test classes, the existing tests of
every touched class (`RocketCargoMutation*`, `RocketCargoSelection*`, `RocketCargoPreparation*`, `EscrowCargoEligibility*`,
`EscrowCargoPreparedCapture*`, `EscrowCargoPublication*`) and C-K's fluid cargo tests. You may add a probe test to check
a finding; keep it in the output folder. Run no GameTest, `runData` or server, check that 6 GiB of memory is free before
each Gradle run, and run one Gradle process at a time. Delete the export's `build` and `.gradle` folders afterwards and
keep only logs.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W118-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
