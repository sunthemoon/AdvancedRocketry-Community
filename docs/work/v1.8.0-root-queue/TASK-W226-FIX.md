# TASK W226-fix: the review's NEW-art record and pad adapter conservation tests

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w226` (W226's; continue on its branch) |
| Branch | `codex/v1.8.0-w226`, now at `5018a9678` (pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W226.md`: append "R225 review corrections" |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W226-review/REVIEW.md` (SHA-256 `20119336…dd7014e9`): APPROVE-WITH-CHANGES, findings W226-R1 and W226-R2, both Medium |

**Root decision R225.** Fix both findings on W226's branch before Root integrates it.

1. **W226-R1, the NEW-art record.**
   - Add a repository-local provenance declaration (batch record) for `gas_charge_pad_top.png` and
     `gas_charge_pad_side.png`, in the format that existing generated NEW-art records use (ADR-061 section 4.3; find
     the closest precedent, such as the C16c machine-art records).
   - It names: the provider `V180GasChargePadData` and the encoder; the authoring commit; MIT attribution; both
     output paths with byte counts and SHA-256; and the outstanding originality and visual review (D7 and the final
     campaign).
   - Do not backdate anything, and do not mark the art as reviewed.
   - If a validator or ledger requires an entry that only Root may write, put the exact text in
     `D:/GitHub/ARCE-Task-Evidence/root-queue/W226/W226-fix-drafts.md` instead.
2. **W226-R2, conservation tests.** Add bounded GameTests for both slots and both gases, with exact counts and root
   checks:
   - stacked insertion, simulated and executed, with exact remainders;
   - extraction, simulated and executed, with the exact item and count, and repeated extraction returning nothing;
   - player insertion and withdrawal, in survival and creative;
   - breaking a pad with nonempty slots: each slot's canister appears exactly once and nothing is emitted a second
     time, while the residual bank gas is lost as W174 section 4.8 specifies;
   - stale or unloaded capability refusal.

   Where a test finds a real defect, fix it in the smallest way and show the test failing first. Otherwise state that
   the tests passed on first run and that each one would detect a count off by one in either direction. Prove that
   with a temporary negative control that you revert before committing.

## Verification

- The new tests three times, in their batch, under the heavy-JVM lock.
- The `atmosphere`, `equipment` and `fluid` unit packages.
- The generated-resource check if datagen changes.
- The repository validator.

## Not in this task

Behaviour changes beyond a found defect, the R-027 and ADR-066 drafts (Root applies them), status files and the
ledger.
