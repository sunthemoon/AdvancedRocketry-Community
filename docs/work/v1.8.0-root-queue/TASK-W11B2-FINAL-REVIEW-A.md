# TASK W11b2-final-review-A: independent read-only review of W11b2's settlement, operator and capacity code

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: you
change nothing in any repository worktree.

This is part A of the final independent review before Root merges W11b2 (D3-A rocket integration, production
admission disabled) into the shared branch. Codex implemented W11b2, so this review comes from a different session and
model (AGENTS.md section 13: persistence and save-refusal slices). Part B, the governed flight path F0 to F8, follows
when continuation 43 ends.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w11b2-rocket-integration` at `3b35a8af`. The W11b2-only change is `git diff fcf3484d..3b35a8af` (the branch already contains the shared branch at `fcf3484d`) |
| Your checkout | `D:/GitHub/arce-v180-w11b2-final-review-a`, a detached worktree of `3b35a8af`. Read it and run tests there; do not commit, switch or reset it |
| Contract | `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN.md` (part 1, sections 6 and 8), `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`): sections 1 to 4, 6 and 7; Root decisions R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` |
| Records | `records/W11B2.md` (continuations 06 to 18, and the decisions they record) and `records/W11B2-F7F8.md` (continuations 36, 38 and 38M) |
| Earlier reviews | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-settle-review/REVIEW.md`, `W11b2-C09-review/REVIEW.md`, `W11b2-C11-rereview/REVIEW.md` and `W11b2-C0C8-review/REVIEW.md` (all under `D:/GitHub/ARCE-Task-Evidence/root-queue/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-final-review-A/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Settlement O1 to O4 and pruning** (`EscrowSettlement*`, `EscrowSettlementPruning`, `EscrowSettlementTombstones`):
   the frozen O1 intent, joint O2 proofs, O3 tombstones and journal removal (O3J), O4 release, exact-group pruning,
   the O-veto, normalization of a neutral survivor, and floor retirement. Each acknowledgement order and forced-stop
   result against design 2 sections 1 to 4.
2. **Receipts and capacity.** The permanent receipt cap of 64 (every choice counts; exhaustion refuses with offline
   repair), R1 to R9's reservation rules (every unsettled component charged at its ceiling, every reserved region at
   its ceiling, one floor retirement's headroom kept, only new work raises the total), and the pinned capacities in
   the records. Look for a write that can raise the reserved total without being new work.
3. **Schemas (M2).** The in-place replacement of the unreleased escrow schemas, the refusal of pre-change development
   data with its raw file preserved, and no migration chain. Production worlds without a ledger must see no new
   refusal.
4. **Operator commands** (continuation 36): `status`, `inspect` and `retry` (bounded output, read-only inspect, one
   accepted acceleration per operation per 200 ticks, no reset of failures or backoff, never clearing REPAIR,
   altering O1, pruning, cancelling UNKNOWN or claiming proof) and F11's console provenance (only an unsuppressed
   player source or the exact server console may settle; functions, command blocks and RCON refused).
5. **Continuation 40's fixes of the C0 to C8 review** (`cd0bccde` to `a092019b`): does each one close its finding in
   `W11b2-C0C8-review/REVIEW.md` without weakening a proof, a hold or a test?
6. **AGENTS.md section 3.3.** List every new refusal, hold or quarantine of saved data in your area with its scope,
   duration, log volume and recovery, so that Root can check R-035 and the ADR-065 note against your list. Name any
   hold that the records do not describe.
7. **Tests.** Do the unit tests, GameTests and native runners in your area prove what the records claim? Look for
   weakened or removed assertions, conservation checks a cut could pass without, and runner oracles that were changed
   (continuation 38 changed `scripts/run_v180_rocket_settlement_completion.py`).
8. **Rerun** in your checkout, with `JAVA_HOME` set to `C:/Program Files/Java/jdk-17.0.7`:
   `gradlew.bat --offline test --tests '*EscrowSettlement*' --tests '*EscrowOperator*' --tests '*EscrowCommand*' --tests '*EscrowCombinedReservation*' --tests '*EscrowReconciliation*' --console=plain`
   and `python -B -m unittest discover -s tests -p 'test_v180_rocket_settlement*.py'`. Do not run GameTests, native
   runs or the whole suite, and take no heavy-JVM lock.

## Output format

- A verdict: MERGE-READY, MERGE-WITH-FOLLOWUP, FIX-FIRST, or BLOCK.
- Findings, each with an ID, a severity (Critical, High, Medium, Low, Info), the file and line at `3b35a8af`, the
  failure scenario and a suggested fix.
- The section 3.3 list from item 6.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.

## Not in this task

Any edit, commit, push or merge in a repository worktree; any other agent; GameTests, native runs and benchmarks; the
governed flight path F0 to F8 and its retry and wait code (part B).
