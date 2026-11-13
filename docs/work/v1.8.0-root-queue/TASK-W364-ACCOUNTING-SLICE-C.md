# TASK W364: per-fluid signed accounting, Slice C (native capture and normal completion)

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w364`, branch `codex/v1.8.0-w364`, at `d957d7dce` (Slices A and B with the shared head) |
| Decision | Root decision R448 (QUEUE row W364) |
| Design | [W357-DESIGN](records/W357-DESIGN.md), sections 2, 3 (preparation to normal L2 and C8, and REPAIR), 6 and 7, and section 8 row C. Slice A and B records: `records/W361.md`, `records/W362.md`. |
| Record | `docs/work/v1.8.0-root-queue/records/W364.md` |
| Write scope | The files of design section 8 row C (`EscrowLoaderAdmission`, `EscrowCargoPreparedCapture` and its eligibility, `EscrowLoaderEndpoint`, `EscrowFluidCargoEndpoint`, `EscrowReconciliationChange` and its action, `EscrowReconciliationCheckpoint`, `EscrowSettlementDisk`, `EscrowCargoReadback`, normal retirement and `EscrowFloors`), the coordinator code that returns admission outcomes, their unit tests and GameTests, and the record. No operator settlement, diagnostics or receipt creation (Slice D), no Python oracle (Slice E), no register, ADR, status file or QUEUE change. |

## What to do

1. **Capture.** At preparation, derive the typed baseline from the same immutable native captures as the binding, verify
   the plan for every key, and do every size and reservation check before any reservation. A refusal changes no
   sequence, reservation, queue, writer or endpoint. Fuel observes the loader tank and both banks; cargo observes the
   tank its cursor names.
2. **L1, C2 to C6, L2 and C8.**
   - L1 commits the operation and its baseline row atomically.
   - C2 recomputes the typed delta beside the native signatures.
   - C3 to C6 freeze the frontier and the numeric projection together, from the same decoded root for live and
     stable readback. The projection is prefix-normalized for chains, and only a zero residual completes.
   - Normal L2 deletes the operation and its row atomically after rechecking the account.
   - C8 changes only markers.
3. **Mismatch and failure.** These follow design section 6:
   - a numeric mismatch holds the component in manual `ACCOUNTING_MISMATCH` REPAIR with no resource effect;
   - an UNKNOWN write keeps the exact candidate;
   - cut-E2 keeps its barrier;
   - an oversize candidate is refused before mutation.
   Add the bounded WARN `ARCE_ESCROW_ACCOUNTING_HELD`: one per held operation, at most 64 held at once, with an ID
   forgotten when its operation leaves the ledger.
4. **Legacy records.**
   - Old live fluid records held as UNKNOWN_BASELINE get no C2 apply and no L2 retirement.
   - Admission into a component that holds a legacy fluid record gets a distinct typed outcome for the coordinator and
     the menu, not the internal `StaleCapture` bridge, with no-mutation assertions (W362R finding 8).
   - Operation copies made at native binding and capture carry the baseline (W362R finding 10).
5. **Activation.** `EscrowAccountingFormat.ENABLED` stays false, with its test. If GameTests need the activated
   format, propose a test-only override in the record before you implement it. It must be off by default, readable
   only under the release-test property, and never readable in production, and existing GameTests keep root 2. Root
   decides on it. Until then, test with explicit root-3 ledgers in unit tests.
6. **Tests.** The design's section 7.1 and 7.2 items for this slice:
   - real fuel and both cargo directions;
   - all four partial applications;
   - chains;
   - same total, different fluid, refused at both live and stable readback, while a healthy component continues;
   - a numeric mismatch held;
   - E2 and UNKNOWN;
   - no-effect refusals;
   - the legacy hold;
   - no permanent history for a normal transfer.

## Rules

- Do not delete tests, loosen assertions, raise timeouts or change a ceiling (AGENTS.md section 5). Root-2 behaviour and
  every existing pin stay unchanged while the constant is off.
- Run the targeted tests, the full unit suite and, if you add GameTests, the full GameTest server with the unchanged
  log checker under the heavy-JVM lock. Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives what was done, the commits, the commands and results, any proposal for Root, and anything not done.
  Its last line, and the last line of your answer, is the path of `records/W364.md`.
