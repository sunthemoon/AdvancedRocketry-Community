# TASK W11b2, continuation 37: reconciliation rows C3 to C8 and the C0 to C8 forced-stop cuts

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-34](TASK-W11B2-CONTINUE-34.md), [TASK-W11B2-CONTINUE-35](TASK-W11B2-CONTINUE-35.md) and the
documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at Root's merge `b70c6e97` of continuation 33's `c8cc5741`, plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local commits only; Root pushes and merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 5.3; Root decisions R1 to R14 as listed in TASK-W11B2-CONTINUE-34, and R15 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C35/ROOT-DECISION-R15.md` (SHA-256 `8ca99cb36f759f674923e7b5f486b62818c355195c989aa3668c2c0bbfcd7b98`) |
| Input | `records/W11B2.md`, "Continuation 35" (R15 validators, the C0 to C2 driver, and where to resume) |
| Record | Add a "Continuation 37" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C37/` |

Root reviewed continuation 35 and accepts its C0 to C2 row group as the base. Notes on this branch's state:
- Continuation 33's final commits are now merged, including `34ce47b9`, which makes the two-flight fixtures await their
  exact selected landing regions. The N1 failure `ordinaryl2withoutreceiptcompletestwopassengerflights`
  (`TRANSFER_JOURNAL_BLOCKED`) that continuation 35 reported should no longer occur. If it does, report it with its line
  and do not change it.
- `countdownwaitsforownstorearmandpreparedacknowledgement` failed once at `2b6e3c4c` ("Countdown has no exact F2
  acknowledgement") and passed in the runs before and after it. If it fails again, report it with its line; it is
  outside this task.
- Continuation 36 runs in `D:/GitHub/arce-v180-w11b2-f7f8` on design section 6 (status, inspect, retry, console
  provenance, settlement fixtures). It may add small hooks to `EscrowReconciliationScheduler`; Root resolves any merge.

## What to do (commit each row group with its tests)

1. **Rows C3 to C6**, as design section 5.3 says: freeze the complete current frontiers of all affected endpoints (no
   chained apply or admission may change them until L2); own-store one endpoint chunk per tick through its original
   worker and keep the exact own-store future; force the same worker after that future succeeds; read back the recorded
   slot with a stable bounded readback that matches the complete identity, the binding and the canonical contiguous
   frontier, with joint live tokens covering both ends of every proposed retired member and the exact current journal
   lease. Use R15 item 1 for every observation: no historical wrapper or envelope digest comparison. Event acceptance,
   RAM absence or an individual or obsolete frontier proof never counts.
2. **Rows C7 and C8.** C7 captures and replaces the checked ledger generation, removing only the jointly proved
   contiguous retirement prefix and installing the required exact-T floors, and rechecks every token and the journal
   lease at capture and immediately before replacement. R15 item 3 (a): start a retirement only when every later
   L1-acknowledged member on each touched endpoint has already applied. `RocketEscrowAudit.attempt` reports
   `proved=true` only from C7's exact successful acknowledgement for that attempt and its requested members. C8: only the
   exact L2 callback prunes the retired IDs, keeps epochs and resources, unlocks ends with no remaining operation and
   drops old proof tokens; remaining chains need a newly frozen frontier. UNKNOWN keeps the candidate and the locks.
3. **The C0 to C8 forced-stop cuts** with real native rocket and fixture-source stores, two restarts per cut, unchanged
   conservation and log assertions: both sides of L1; both endpoint actions; own-store, force and readback; L2
   replacement before and after its callback; pruning and release; and R15 item 3's cases (the later-member wait, a
   stale marker before binding, a C2 mismatch as sticky REPAIR). Do not insert an autosave to make a cut pass. List the
   cuts that are left, if any.

## Write scope and boundaries

As TASK-W11B2-CONTINUE-35: `RocketEscrowAudit`, `EscrowReconciliationScheduler` (only what C7's acknowledgement and the
existing retry rules need; F10's cursors, caching and backoff work is a later task), the reconciliation and
settlement-pruning classes that the driver needs, the narrow driver entry point in `EscrowCoordinator`,
`EscrowNativeBinding` validators that R15 requires, their tests and GameTests, native-cut fixtures and runners, and this
record. Under R13 item 3, a narrow additive edit to another file is allowed when the frozen design or R1 to R15 already
require it; record each one with its reason. Continuation 33's and continuation 36's files stay out of scope.

Keep the existing budgets: 8 reconciliation visits per tick, one endpoint-chunk serialization per tick, the existing
barrier batch and writer cadence, and the 16 MiB and 16,384-visit preparation budgets. Reconciliation adds no tickets
and loads no chunks.

This session has room for more than one step: do not stop after the first committed step if time remains. Keep the
whole unit suite passing after every step and the unchanged log checker passing, apart from the notes above. A warning
that remains is a finding to report with its line. Stop only for a change to an acknowledgement order, a proof, a
hold's effect or the replay exclusion: commit what you have with your record, and report. Questions sent through the
task conversation do not reach Root; the record and the final answer do. If you run short of time, stop at a committed
step boundary with every test of that step passing, and say exactly where the next continuation starts. One GameTest
run and one set of native cuts per committed step is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C37/`. Continuation 36
shares the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own. If a GameTest run
crosses midnight, check every rolled log part, as continuation 35 did.

## Not in this task

F10 (cursors, caching, fairness, backoff) and the eight-flight benchmark (a later task, after this one); F6 to F9 and
F11 (continuation 36); the CANCELLED path, N1 and F0 to F8 (continuation 33, merged); credited-source reconstruction
(W27f); other propulsion operations; production admission; W11b3; the bay slices; signed per-fluid accounting;
part-1 findings F3, F8 and N2; ADRs, the risk register, status files and the ledger (Root).
