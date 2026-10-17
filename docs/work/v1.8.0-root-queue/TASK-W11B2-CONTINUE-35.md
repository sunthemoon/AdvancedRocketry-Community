# TASK W11b2, continuation 35: the ordinary reconciliation driver C0 to C8 under R15

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-34](TASK-W11B2-CONTINUE-34.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at Root's merge of `f2d91796` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), Root decisions R1 to R14 as listed in TASK-W11B2-CONTINUE-34, and **R15** in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C35/ROOT-DECISION-R15.md` (SHA-256 `8ca99cb36f759f674923e7b5f486b62818c355195c989aa3668c2c0bbfcd7b98`) |
| Input | `records/W11B2.md`, "Continuation 34" (R14 delivered; the C0-C8 proof question and its reproducer; the open C0 to C8 work) |
| Record | Add a "Continuation 35" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C35/` |

Root answered continuation 34's question with R15. In short: C0, C3 and C6 validate native-bound endpoints through the
canonical prefix classification and the binding's identity and typed-signature fields, never the complete section or
envelope digests. C2 compares the current section to `before_wrapper` and the computed E' to `after_wrapper`. A
retirement starts only when every later L1-acknowledged member on the touched endpoints has already applied, and C0
never binds over a stale marker.

Two notes on this branch's state:
- Root's merge `b8c1bd60` also brought in continuation 33's first three commits (`e80f39a5`, `6e3fab13`, `f3239a4c`),
  and Root has since merged continuation 33's fix `f2d91796`. The N1 tests in `RocketSettlementIdleGameTests` belong
  to continuation 33, which is still running in `D:/GitHub/arce-v180-w11b2-f7f8`. If they still fail on this branch,
  report them with their lines and do not change them.
- `moreThan32ProcessorsShareOneFairBudgetAcrossTwoLevels` (`ResearchEnvironmentGameTests`) failed once in continuation
  34's run ("Fairness processor claim refused") and passed in every earlier run. If it fails again, report it with its
  line; it is outside this task.

## What to do (commit each step with its tests)

1. **R15's tests and validators first.** Add R15 item 5's A0 cases with the validators they need: survivor validation
   after pruning, the retirement wait while a later member is BEFORE, no binding over a stale marker, and C2 mismatch
   as sticky REPAIR. Keep the reproducer's existing assertions.
2. **The driver, design section 5.3, rows C0 to C8**, exactly as TASK-W11B2-CONTINUE-34 step 2 says (FLUID_PROPULSION_FILL
   only, with real native rockets and the gated fixture source; `attempt` reports `proved=true` only from C7's exact
   acknowledgement; a missing credited source is REPAIR; no tickets). Commit in row groups: C0 to C2, then C3 to C6,
   then C7 and C8.
3. **The C0 to C8 forced-stop cuts** as TASK-W11B2-CONTINUE-34 step 3 says, including R15 item 3's cases. List the
   cuts that are left.

## Write scope and boundaries

As TASK-W11B2-CONTINUE-34, without the R14 files: `RocketEscrowAudit`, `EscrowReconciliationScheduler`, the
reconciliation and settlement-pruning classes that the driver needs, a narrow driver entry point in `EscrowCoordinator`,
`EscrowNativeBinding` validators that R15 requires, their tests and GameTests, native-cut fixtures and runners, and this
record. Continuation 33's files stay out of scope. Under R13 item 3, a narrow additive edit to another file is allowed
when the frozen design or R1 to R15 already require it; record each one with its reason.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step and the unchanged log checker passing, apart from the two notes above. A
warning that remains is a finding to report with its line. Stop only for a change to an acknowledgement order, a proof,
a hold's effect or the replay exclusion: commit what you have with your record, and report. Questions sent through the
task conversation do not reach Root; the record and the final answer do. If you run short of time, stop at a committed
step boundary with every test of that step passing, and say exactly where the next continuation starts. One GameTest
run and one set of native cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C35/`. Continuation 33
shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own.

## Not in this task

As TASK-W11B2-CONTINUE-34: the CANCELLED path, N1 and F0 to F8 (continuation 33); credited-source reconstruction
(W27f); other propulsion operations; production admission; F6 to F11 and the benchmark; W11b3; the bay slices; signed
per-fluid accounting; part-1 findings F3, F8 and N2; ADRs, the risk register, status files and the ledger (Root).
