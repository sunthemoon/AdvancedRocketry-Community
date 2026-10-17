# TASK W11b2, continuation 40: the C0 to C8 review's findings F1 to F5 and F8

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-37](TASK-W11B2-CONTINUE-37.md), [TASK-W11B2-CONTINUE-39](TASK-W11B2-CONTINUE-39.md) and the
documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at continuation 39's record `7b1139b8` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local commits only; Root pushes and merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 5.3; R13 and R15 as listed in TASK-W11B2-CONTINUE-37 |
| Input | The independent review `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C0C8-review/REVIEW.md` (SHA-256 `c930e5c106ac824586ded82bbf231917d75795a26089875dab5817a627db0409`), read in full; `records/W11B2.md`, "Continuation 37" and "Continuation 39" |
| Record | Add a "Continuation 40" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C40/` |

The review's verdict is MERGE-READY-AS-PART. Fix its findings in this order, one commit per finding, each with the
tests the review asks for. F6 and F7 are not in this task: F6 is a measurement (continuation 41's benchmark), and Root
writes F7 into R-035 and ADR-065.

Continuation 39 delivered F10's observation caching (`0efca67a`) and F1's retry backoff (`631e4430`), then stopped
because the tool policy refused its stale-lock cleanup; Root has since removed that lock and its GameTest world. Its
pending verification is now part of this task: the final GameTest run below covers the strengthened F1 GameTest, and
the final native rerun covers its 31 C0 to C8 cuts. Its review finding about restart pruning (the review's F1) interacts
with continuation 39's new negative cache in `EscrowReconciliationPruning`; keep that cache's rule that positive
candidates are never cached. Continuation 41 runs in parallel in `D:/GitHub/arce-v180-w11b2-c41` on the governed
flight's F6 and F7 retry continuity, successor waits and the eight-flight benchmark; do not edit the governed dispatch,
terminal or preparation classes.

1. **F1 (Medium).** After a stop between C7's replacement and C8, or when C8 could not prune an unloaded rocket, a
   later valid member must not become sticky OTHER_STATE because the driver captured the rocket before its stale
   leading marker was pruned. Run the existing validated cleanup for each loaded destination rocket of the component
   before the capture, as the driver already does for sources, and treat a leading prefix that
   `EscrowReconciliationPruning.retired` would remove as a non-manual wait rather than OTHER. Keep the pruning rules
   unchanged: no former identity is inferred, and resources and epochs never change. Add the review's A0 test (the
   driver's capture of `[A, B]` against chain `[B]` produces no manual reason) and its native cut (two fills from
   different sources, B applied, stop at A's L2 replacement before C8, sources registered before the first scheduler
   visit, two restarts).
2. **F2 (Low).** Refuse, in `prepareNativeTest`, a new member on a fixture source that still carries a live marker of
   an earlier member, with a unit test. Say in the record that source-side chains are not covered, because no
   production source adapter exists.
3. **F3 (Low).** Remove the probe's direct `EscrowReconciliationPruning.source` calls from
   `EscrowReconciliationProbe.recover`, and make the runner assert what product code actually leaves on the source
   after a restart. If that is a retained marker, assert it and say so in the record.
4. **F4 (Low).** Compare `RocketTransferService.escrowReconciliationBoundary` with `resourceMutationAllowed`. Restore
   the `settled && !live` check and the governed terminal-successor branch unless you can show they cannot apply; record
   the reason either way, and add a test of C2 against a COMMITTED named-T record that is not yet settled in the
   current session.
5. **F5 (Low).** Capacity bounds (more than eight members, the 64-Level limit) return the retryable `BARRIER_BOUNDS`;
   identity and validity conditions keep INVALID_ENDPOINT. Add unit tests.
6. **F8 (Info).** Make the unit sticky-REPAIR test go through the real driver path, add the destination side of the
   native C2-mismatch cut, show the stale-binding refusal natively without the probe pruning first, and rename or
   extend the Python test whose name claims more than it checks.

Rerun the whole C0 to C8 native matrix once at the end, with two restarts per cut, on the committed source.

## Write scope and boundaries

The reconciliation driver, view, validation, pruning and change classes, `RocketEscrowAudit`,
`EscrowCoordinator.prepareNativeTest`, `RocketTransferService.escrowReconciliationBoundary`, the reconciliation probe
and runner, their tests and GameTests, and this record. Under R13 item 3, a narrow additive edit to another file is
allowed when the frozen design or R1 to R15 already require it; record each one with its reason. Continuation 38's
settlement fixtures stay out of scope.

No change to an acknowledgement order, a proof, a hold's effect, the replay exclusion, the log checker or its
expectations, and no relaxed budget, limit, timeout or assertion. Stop only for a change to an acknowledgement order, a
proof, a hold's effect or the replay exclusion: commit what you have with your record, and report. Questions sent
through the task conversation do not reach Root; the record and the final answer do. If you run short of time, stop at
a committed finding boundary with every test passing, and say exactly where the next continuation starts. Keep the
whole unit suite and the unchanged log checker passing; one GameTest run at the end is enough.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C40/`. Other workers share
the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own. If a GameTest run crosses
midnight, check every rolled log part.

## Not in this task

F6 and F7 of the review; the governed flight's retry continuity, successor waits and the benchmark (continuation 41);
F6 and F9 of design section 6 (continuations 38 and 38M);
production admission; W27f reconstruction; other propulsion operations; W11b3; ADRs, the risk register, the R-035 text,
status files and the ledger (Root).
