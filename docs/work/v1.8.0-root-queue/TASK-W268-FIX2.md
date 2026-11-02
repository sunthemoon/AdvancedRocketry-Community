# TASK W268-fix2: the recheck's corrections to the REVIEW closure proposal (W268-R11 to R15)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w268` (existing; branch `codex/v1.8.0-w268` at `e82206a6a`) |
| Task it answers | [TASK-W268-FIX](TASK-W268-FIX.md) and the recheck `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-recheck/REVIEW.md` (SHA-256 `76403fcd2cd6d40be91d7e554ad44937933dd99d22f56bb2fe5adef797a9ad0b`, APPROVE-WITH-CHANGES); read all of it and its helper evidence |
| Record | Append a "W268-fix2" section to `docs/work/v1.8.0-root-queue/records/W268.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-fix2/` |

Make every change through `build_proposal.py` (and the scripts it drives), so the committed outputs still reproduce
byte for byte on `0cae612d2`:

1. **W268-R11 (Medium).** Adoption step 5: `test_review_needs_a_cleared_finding_to_import` needs all five related files
   in the beacon finding's `releases` (`controlpanel`, `cuttingmachine`, `cuttingmachine_active`, `fuelingmachine` and
   `machineprecisionlaseretcher`), not only `controlpanel`. Derive the list from the derivation data rather than typing
   it, and add a test that applies the adoption steps in a scratch copy with revision 7 simulated as accepted and
   expects the whole ledger test module to pass.
2. **W268-R12 (Low).** The adoption guard also refuses findings that still carry the draft wording ("not an owner
   decision") or the preparation date as the review date; add the failing cases to `test_proposal.py`.
3. **W268-R13 (Low).** Compute "898", "247 -> 162", "16 PLANNED" and "80 patterns" from the base instead of typing them.
4. **W268-R14 (Low).** G1's question and its group text say which of its old faces belonged to blocks with no modern
   version (rejected, deferred, or redesigned as screens or commands), and that a future block of that kind would need
   NEW art.
5. **W268-R15 (Info).** The wording and punctuation notes.

## Verification

`build_proposal.py` on `0cae612d2` reproduces every committed output byte for byte; the proposal's tests, including the
new ones; the scratch verification with all import verifies; the repository validator on the branch.

Commit on the branch; do not push. Root reviews and pushes.

## Not in this task

Any change to a proposed disposition or count, applying a draft, accepting the ADR revision, filling in the owner's
name, changing the validator, Java or resources.
