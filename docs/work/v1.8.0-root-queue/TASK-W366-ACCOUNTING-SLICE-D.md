# TASK W366: per-fluid signed accounting, Slice D (operator settlement, receipts and diagnostics)

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w366`, branch `codex/v1.8.0-w366`, at `2759ff017` (Slice C integrated with the shared head) |
| Decision | Root decision R453 (QUEUE row W366) |
| Design | [W357-DESIGN](records/W357-DESIGN.md), sections 2.2, 2.3, 3 (operator preview and confirm, O1 to O4, ABANDON, settle-floor, pruning), 6 and 7, and section 8 row D. Earlier slices: `records/W361.md`, `records/W362.md`, `records/W364.md`. |
| Record | `docs/work/v1.8.0-root-queue/records/W366.md` |
| Write scope | The observer, proof, recovery, operator, diagnostics and pruning files of design section 8 row D, `EscrowReason`, the account commitment and receipt creation and release, `EscrowItemCargoAdmission` (item 5 below), their unit tests, any language entries the operator output needs through the existing data generator, and the record. No Python oracle, register, ADR, status file, QUEUE, build or workflow change. |

## What to do

1. **Preview and confirm.** Show per-key endpoint deltas, the residual, missing flags and the adjustment owner. A
   non-zero residual is a signed proposed loss or gain; a half-applied transfer is shown as such; an unknown
   contribution prints UNKNOWN. Commit every account projection and any approved discrepancy in the schema-3 intent.
   Confirmation repeats the observations, the actor and digest checks and the existing 200-tick missing-endpoint
   interval.
2. **O1 to O4.**
   - O1 freezes the component's rows with the confirmed intent in one generation.
   - O2 recomputes typed observations against O1; a changed vector invalidates the proof, even when the scalar total
     is unchanged.
   - O3 installs schema-4 receipts with the exact final account digest; no newly inferred loss is allowed there.
   - O4 revalidates before release. Release does not make UNKNOWN known.
   - A mismatch after O1 holds the frozen decision.
   - Log one INFO `ARCE_ESCROW_ACCOUNTING_RELEASED` on release, and one audit INFO per acknowledged O1 decision, with
     at most 64 members and 320 key rows.
3. **ABANDON, settle-floor and pruning.**
   - ABANDON keeps the known side and records the missing side as UNKNOWN, with no rebuild or refund.
   - A bare floor's settlement creates no fluid row.
   - Pruning keeps every vector and permanent row.
4. **Inspect.** Per-fluid values, knowledge and durability, demand-driven and paginated at 16 key rows per response,
   with no native tag body logged.
5. **The item caller (W364's open point).** `EscrowItemCargoAdmission` must give shared cargo preparation the
   combined pending root (`pendingLedger()`), so that an independent item candidate is not refused as stale while a
   row-bearing fluid admission is pending. Root-2 behaviour must not change.
6. **Mixed components and legacy members.** As W357F and W362 fixed them, with a test for each decision path.
7. **Activation.** `EscrowAccountingFormat.ENABLED` stays false, with its test, and no test-only override (R453). Use
   unit tests with explicit root-3 ledgers. Root runs the activated GameTests and native cases on a separate activation
   branch after this slice.

## Rules

- Do not delete tests, loosen assertions, raise timeouts or change a ceiling (AGENTS.md section 5). Root-2 behaviour and
  every existing pin stay unchanged while the constant is off.
- Run the targeted tests, the full unit suite and `runData` (under the heavy-JVM lock) if a language entry changes.
  Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The receipt work is a saved-form change: a separate read-only review follows before it merges.
- The record gives what was done, the commits, the commands and results, and anything not done. Its last line, and
  the last line of your answer, is the path of `records/W366.md`.
