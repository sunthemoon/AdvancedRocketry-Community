# TASK W134-review: independent read-only review of W134 (cargo leaf D-E2, item settlement and the end-to-end cases) before merge

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents or
subagents. Codex wrote W134, so this is a fresh Claude session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w134` at `3c04ae01` (base `eeb1fb80`; tested source `3258d606`). Your review worktree `D:/GitHub/arce-v180-w134-review` is a detached checkout of `3c04ae01`; read the diff with `git diff eeb1fb80..3c04ae01` there |
| Task it answers | [TASK-W134](TASK-W134-CARGO-D-E2-ITEM-SETTLEMENT.md) |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 3.3 and 4, section 6's D-E row and the cut mapping's "Neutral survivor normalization and veto", "O2 survivor/absence, O3, O3J, O4 and pruning" and "Latest-cargo disassembly" rows |
| Models it mirrors | The fluid settlement leaf [records/W131.md](records/W131.md) (C-E3, with W131F) and the item leaf [records/W132.md](records/W132.md) (D-E1, with W132F) |
| Record | `docs/work/v1.8.0-root-queue/records/W134.md` on the branch (the plan is at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W134/evidence-3258d606/VERIFICATION.md` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W134-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **O1 for the item device:** the four buffer slots observed as typed native NBT through D-E1's endpoint adapter; the
   rocket's observation stays `FUEL_VECTOR_V1` with the invariant-envelope digest; nothing falls into the fixture
   branch of `EscrowSettlementObserver` or `EscrowSettlementDisk`.
2. **Normalization and veto** with item cargo: old versus normalized wrapper and the listed mismatches; a vetoed
   survivor is never normalized, proved or released.
3. **O2** from one bounded native decode, comparing item stacks with their NBT tag types; both unload survivor proofs
   (rocket source and device destination); ABANDON's exact surviving and absent endpoints; R39's native check before O2
   acts, holding only that operation. Can an item whose NBT does not round-trip, or a stack over its native maximum, be
   proved or published?
4. **O3, O3J and O4** unchanged in order; **pruning** for the item device (`9be73cb5`) never removes a marker an
   unresolved or earlier group still needs, and the gated fixture fallback keeps W131F's identity check.
5. **Exactly once.** For a load and an unload, can any order of proof, write, crash or restart credit or debit twice,
   or duplicate or lose a stack (count, item, NBT)? Does the latest-cargo disassembly on P1-F8 materialize exactly the
   latest cargo once, with the checked legacy COMMITTED record case?
6. **The end-to-end cases** of the D-E row: competing item devices, a partial move and a restart, cancellation, two
   successive cargo-mutated flights with a mutation by the successor, the refusal comparisons; receipts and inspector
   output pinned. Named (transfer-guarded) item cargo stays held (R48's D4), pinned by a test, with no variant of the F1
   fix.
7. **The governed launch after O4 READY.** W133 is in W134's base: no launch step may return `INVALID_STATE` for an
   escrow-filled assembled rocket. `3258d606` releases the settlement observer before the governed launch; is that a
   fixture change only, or does production need the same release?
8. **Nothing weaker.** List every edited pre-existing production, test or fixture file (`EscrowCargoLaunch`,
   `EscrowSettlementProofs`, `EscrowSettlementPruning`, `EscrowSettlementRecovery`, `EscrowCargoGameTestWorld`,
   `ItemCargoReconciliationFixtures`) and say whether any assertion, bound, budget or timeout became weaker; the fluid
   and fuel settlement tests are unchanged and their receipt bytes and inspector output stay pinned;
   `EscrowCoordinator` and `RocketTransferService` did not grow; new classes stay under 500 lines; each new WARN or
   ERROR a GameTest drives has its own exact manifest entry.
9. **The full-GameTest failure.** W134's complete run timed out three existing tests
   (`ordinaryL2WithoutReceiptCompletesTwoPassengerFlights` at 1,200 ticks,
   `theCapAndThePerPlayerLimitRefuseColdDestinationsAndEveryWarmupEnds` and
   `aWarmupKeepsItsPadThroughReservationChurnAndHandsOverAfterAPlayerRelaunch` at 2,400 ticks); all three passed in a
   fresh filtered run, four agent sessions were present, and the same warm-up class timed out once before on hosted
   run 39464852370 (QUEUE). Root pushed the branch, so hosted CI runs a full regression on it. From the code and the
   logs in the evidence: could anything W134 adds (new batches, fixtures, tickets, chunk loading, leftover entities or
   ledger state) slow or block those tests in a complete run? Name a cause if you find one; do not change timeouts.
10. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W134's
    base merged W130 (the retirement before the first fill) and Root's records. Preview with `git merge-tree`, list every
    conflict and say what the merge must keep.
11. **The record** against the code and the evidence.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W134's
five new unit classes, each alone, then the `resourceescrow` and `rocket.cargo` packages. You may add a probe test to
check a finding; keep each probe in the output folder. Before each Gradle run, check that at least 6 GiB of memory is
free and wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no heavy-JVM lock.
Delete the worktree's `build`, `.gradle` and `logs` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W134-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
