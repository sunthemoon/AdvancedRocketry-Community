# TASK W11b2-final-review-B: independent read-only review of W11b2's governed flight path F0 to F8

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: you
change nothing in any repository worktree.

This is part B of the final independent review before Root merges W11b2 (D3-A rocket integration, production
admission disabled) into the shared branch. Part A (settlement, operator and capacity) returned MERGE-WITH-FOLLOWUP;
its fixes are in progress. Codex implemented the governed flight path, so this review comes from a different session
and model (AGENTS.md section 13).

| Item | Value |
|---|---|
| Subject | Review tree `12f5a5b2`: the W11b2 branch at `3b35a8af` with the C41 branch's `a28004df` (continuations 41 to 43) merged, no conflicts. The W11b2-only change is `git diff fcf3484d..12f5a5b2` |
| Your checkout | `D:/GitHub/arce-v180-w11b2-final-review-b`, a detached worktree at `12f5a5b2`. Read it and run tests there; do not commit, switch or reset it |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`): sections 1, 5.1, 5.2, 5.4 and 5.5 and section 6's F10; Root decisions R10 to R16 (`D:/GitHub/ARCE-Task-Evidence/root-queue/`: `W11b2-C27/ROOT-DECISION-R10.md`, `W11b2-C29/ROOT-DECISION-R11.md`, `W11b2-C31B/ROOT-DECISION-R12.md`, `W11b2-C31B/ROOT-DECISION-R13.md`, `W11b2-C34/ROOT-DECISION-R14.md`, `W11b2-C35/ROOT-DECISION-R15.md`, `W11b2-C43/ROOT-DECISION-R16.md`) and the earlier proof-scope decisions recorded in continuations 09 and 12 |
| Records | `records/W11B2.md` (continuations 10 to 34 and 39), `records/W11B2-F7F8.md` (31B and 33) and `records/W11B2-C41.md` (continuations 41 to 43) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-final-review-B/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Design section 5.4, rows F0 to F8.** For each row: the action, its acknowledgement, the one-use permits, and the
   forced-stop result. In particular: F1's checkpoint of the exact E before ARMED and the replay exclusion of a
   client T; F2's schema-3 PREPARED record replacing only an exact F7-terminal predecessor; F4's fixed destination
   UUID; F5's phases; F6's joint proofs; F7's HANDED_OFF and CANCELLED with the exact successor-lock release (R12) and
   R13's accepted interval; F8's cleanup and ARMED pruning. A cancelled exact-source return must name cancellation and
   never refund from an old snapshot.
2. **Holds and logging.** R10, R11 and R14's silent waits versus real repair fences; R16's test-owned player gate
   (identity-based ownership, RCON refused, fail closed); F6's live observer under R13's standard; the 100, 400 and
   1,200-tick backoff keyed by the logical rocket (continuations 39, 41 to 43); `ARCE_ESCROW_SUCCESSOR_HOLD` once per
   transition; a healthy flight logging no hold or fence warning.
3. **Interlocks and the legacy path.** Governed records never enter the legacy live loop; the ARMED and READY-head
   interlocks; the bay-first ordering; flight tickets only after a checked ACTIVE classification; no chunk loading by
   reconciliation or held repair.
4. **F10 and the wait routes.** Generation-bound caches never bypass a final check; negative-only caching; bounded
   cursors; no per-tick full decode or rehash. Name any per-tick full capture that remains on a healthy path.
5. **AGENTS.md section 3.3.** List every new refusal, hold or quarantine of saved data in your area with its scope,
   duration, log volume and recovery, as part A did, so that Root can check R-035 and the ADR-065 note against it.
6. **Tests.** Do the unit tests, GameTests and native runners (`scripts/run_v180_escrow_*.py`) prove what the records
   claim? Look for weakened or removed assertions, runner oracles that changed, and cuts that pass because a probe does
   the product's work.
7. **Rerun** in your checkout, with `JAVA_HOME` set to `C:/Program Files/Java/jdk-17.0.7`:
   `gradlew.bat --offline test --tests '*EscrowGoverned*' --tests '*EscrowFlight*' --tests '*EscrowSuccessor*' --tests '*EscrowDispatch*' --tests '*EscrowTerminal*' --tests '*RocketGoverned*' --tests '*EscrowTestEndpoints*' --tests '*ReleaseTestPlayers*' --console=plain`
   and `python -B -m unittest discover -s tests -p 'test_v180_escrow*.py'`. Do not run GameTests, native runs or the
   whole suite, and take no heavy-JVM lock.

## Output format

- A verdict: MERGE-READY, MERGE-WITH-FOLLOWUP, FIX-FIRST, or BLOCK.
- Findings, each with an ID, a severity (Critical, High, Medium, Low, Info), the file and line at `12f5a5b2`, the
  failure scenario and a suggested fix.
- The section 3.3 list from item 5.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.

## Not in this task

Any edit, commit, push or merge in a repository worktree; any other agent; GameTests, native runs and benchmarks;
settlement, receipts, capacity and the operator commands (part A).
