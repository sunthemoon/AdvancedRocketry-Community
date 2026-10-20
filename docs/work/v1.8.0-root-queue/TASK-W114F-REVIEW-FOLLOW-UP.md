# TASK W114F: W114's review follow-up, the per-rocket cargo charge (W114-R1)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W114](TASK-W114-RECEIPT-CHARGE-R40.md),
W114's record (`docs/work/v1.8.0-root-queue/records/W114.md` on your branch) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W114-review/REVIEW.md` (SHA-256 `4c9a7e67…8e1a`), first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w114`, branch `codex/v1.8.0-w114` at `a447eaea`; continue on that branch |
| Record | append a "W114F" section to `docs/work/v1.8.0-root-queue/records/W114.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W114F/` |

**Root decision (R41, 2026-10-20).** The one-marker cargo allowance belongs to the rocket a cargo member names, not to
the whole settlement component. The review showed that `EscrowSettlementIntent.touches` (`:181-184`) joins records by
bare endpoint UUIDs across endpoint kinds, so a structurally valid ledger can put a fuel record on another rocket into a
cargo component; the component-wide discount then charges that fuel rocket one marker instead of five, and the review's
probe admitted a guarded fluid unload whose actual O1 then failed with `Escrow NBT envelope exceeded`. The component
relation itself stays as it is (a larger component only holds more, never less); only the charge changes.

## What to do, each in its own commit

1. **The fix.** Replace `EscrowSettlementCapacity.markerLimit(component)` with a helper that takes the component and one
   logical rocket: one marker when a cargo member of the component names that rocket, otherwise five. Call it for each
   native rocket endpoint in the shared preview and for the candidate's rocket in `requireAdmission`. Keep the cargo
   exclusions, the retained-marker ordering, the shared preview's behaviour for ordinary components and every decode
   bound. Do not add a decode refusal to hide the alias case.
2. **The regression, failing before the fix.** Turn the review's probes
   (`D:/GitHub/ARCE-Task-Evidence/root-queue/W114-review/probes/EscrowReviewProbeTest.java`,
   `crossKindUuidAliasIsAcceptedByTheLedgerAndDiscountsFuel` and `mixedAliasAdmissionCanUnderchargeActualO1`) into
   permanent unit tests: a fuel record with retained markers keeps its five-marker allowance when an alias joins it to a
   cargo member; and whatever a mixed component admits (`requireAdmission`, candidate `preflight` and the real
   `prepareTransfer`) has an actual O1 that encodes with the fuel rocket's markers. Ordinary isolated components keep
   their W114 results.
3. **The record.** Correct the unconditional "fuel retains five" claim, and state the broader diagnostic precedence the
   review noted: for cargo with retained markers, `OTHER_TRANSFER_PENDING` now also masks a `STALE` or `ESCROW_FULL`
   result of `fillRefusal`, and "no side effects" means no sequence, reservation, queue, writer or endpoint change (the
   transient turn hint may be recorded).

No saved format, schema, hash, bound, ceiling, dictionary, proof, budget or timeout changes; no existing assertion is
relaxed. Stop and report if any of these seems needed.

## Verification

The new and changed test classes alone, then the `resourceescrow` package, then the whole unit suite, each on committed
source; one Gradle process at a time, with 6 GiB of free memory checked before each. No GameTest is needed: the hosted
regression runs after the merge.

## Not in this task

The component relation (`touches`), C-E, the ESCROW_FULL and CARGO_TOO_LARGE mapping (W117), ADRs, the risk register,
status files and the ledger (Root).
