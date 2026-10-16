# TASK W11b2, continuation 36: design section 6 (F6, F7, F8, F9, F11) and the operator commands

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-10](TASK-W11B2-CONTINUE-10.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-f7f8` (at `c8cc5741` plus this task) |
| Branch | `codex/v1.8.0-w11b2-f7f8` (local only; Root merges it into `codex/v1.8.0-w11b2-rocket-integration`) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 6, and Root decisions R1 to R13 as listed in TASK-W11B2-CONTINUE-33 |
| Input | `records/W11B2-F7F8.md`, "Continuation 33" (CANCELLED through F7 and F8, N1's complete flights, the F0 to F8 audit) |
| Record | Add a "Continuation 36" section to `records/W11B2-F7F8.md`. Keep the existing text. Do not edit `records/W11B2.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C36/` |

Root reviewed continuation 33 and removed its draft directory and GameTest world. Its final run passed all 1,155
GameTests, but the unchanged log checker refused 7 `server-overloaded` warnings against a maximum of 5. They cluster in
the run's first four minutes, while another worker's unit suite was running; other branches' runs had 2. Root is
checking this on hosted CI and owns that finding; do not change the checker or its expectations.

Continuation 35 (Codex, worktree `D:/GitHub/arce-v180-w11b2-rocket-integration`) runs in parallel on the ordinary
reconciliation driver C0 to C8 under R15.

In this task, F6 to F11 are design section 6's findings. Design 2 numbers them after the settlement review
(`W11b2-settle-review/REVIEW.md`), not the part-1 review. They are not the flight-order steps F6 to F8 either.

## What to do (commit each step with its tests)

1. **F7 and F8: status, inspect and retry.** Do what section 6's two tables say:
   - `status` reports held, released and pruning counts and bounded reason summaries, and counts every member of
     accept-current, abandon and floor settlement (F8). The 64 permanent receipt cap stays; at exhaustion every choice
     refuses and needs whole-world offline repair.
   - Add permission-2 `/arce escrow inspect <operation>` and `/arce escrow retry <operation>` exactly as the command
     table defines them: read-only and bounded output for `inspect`; at most one accepted acceleration per operation per
     200 ticks for `retry`, through the same fair, loaded-only queue and budgets, without resetting failure counts or
     backoff, and never clearing REPAIR, altering O1, pruning, cancelling UNKNOWN or claiming proof.
   - Fix the preview and refusal wording for F8. The R-035 wording itself belongs to Root.
2. **F11: console provenance.** Keep player UUID actors and typed console actors. Require the exact non-entity
   server-console source and an unsuppressed-output stack, as section 6 says. If the current command API cannot
   establish that provenance, use a dedicated console entry and refuse the generic non-player route. Test that
   permission-4 tick and load functions, suppressed stacks, command blocks and RCON are refused for both preview and
   confirm, while permission-2 `inspect` and `retry` stay bounded.
3. **F6 and F9: the settlement fixtures.**
   - F6: assert the actual hold reason on each restart. Add separately checkpointed source states with a reinstalled
     veto to prove save refusal across restarts. Keep the old non-floor results and do not relabel them.
   - F9: cut floor and all other O1 choices on the final source, with named-transfer fill guards and truly empty entity
     slots. Update the old intent runner to follow eventual O2 to O4 without removing its pre-O1 and retained-intent
     assertions.
   - Native cuts with two restarts each, pinned to the committed source and its jar.

## Write scope and boundaries

- In scope: `EscrowCommands`, `EscrowSettlementOperator` and the settlement status, preview and intent classes these steps
  need, the settlement probes and runners for F6 and F9, their tests, GameTests and Python oracles, and this record.
  Under R13 item 3, a narrow additive edit to another file is allowed when the frozen design or R1 to R13 already
  require it; record each one with its reason.
- Shared with continuation 35: `retry` needs to advance the next eligible attempt of a waiting operation. If that needs a
  hook in `EscrowReconciliationScheduler` or the settlement-pruning classes, add only a small additive method and
  record it; Root resolves the merge.
- Out of scope, owned by continuation 35: `RocketEscrowAudit`, the C0 to C8 driver, `EscrowNativeBinding` and its
  validators.
- Not in this task: F10 (cached intents, independent cursors, fairness and backoff) and the eight-flight benchmark,
  which follow after Root merges continuation 35.
- Use a new GameTest batch only when a fixture needs isolation.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step. Run the unchanged log checker after each GameTest run; report a remaining
warning with its line, and report the overload count separately. Stop only for a change to an acknowledgement order, a
proof, a hold's effect or the replay exclusion: commit what you have with your record, and report. Questions sent
through the task conversation do not reach Root; the record and the final answer do. If you run short of time, stop at
a committed step boundary with every test of that step passing, and say exactly where the next continuation starts.
One GameTest run and one set of native cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C36/`. Continuation 35
shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own.

## Not in this task

- C0 to C8 (continuation 35), F10 and the benchmark (later), production admission, W11b3, the bay slices, W27f
  reconstruction, signed per-fluid accounting.
- The part-1 review's findings F3 (W86), F8 (W85) and N2, which follow the W11b2 merge.
- ADRs, the risk register, the R-035 text, status files and the ledger (Root).
