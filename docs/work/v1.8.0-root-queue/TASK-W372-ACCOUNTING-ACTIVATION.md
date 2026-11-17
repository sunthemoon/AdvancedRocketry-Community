# TASK W372: switch the per-fluid accounting format on (activation branch)

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w372`, branch `codex/v1.8.0-w372`, at the shared head (Slices A to D and E part 1 merged, `83907330c`) |
| Decision | Root decision R466 (QUEUE row W372) |
| Design | [W357-DESIGN](records/W357-DESIGN.md), section 8 (activation); slice records W361, W362, W364, W365 and W366; the register's R-035 accounting paragraph |
| Record | `docs/work/v1.8.0-root-queue/records/W372.md` |
| Write scope | `EscrowAccountingFormat` and its pin test, the expectations of existing tests that change only because ledgers are now written in root 3, the GameTest log manifest's exact bounded entries for the new accounting log lines if GameTests now emit them, and the record. No production behaviour change other than the constant, no oracle relaxation, no register, ADR, status file or QUEUE change. |

## Why

The accounting code is merged behind `EscrowAccountingFormat.ENABLED = false`. Root turns it on in a separate,
reviewed change (W357-DESIGN section 8; R453). This task makes that change and proves the whole suite with the format
on, before candidate C4.

## What to do

1. **The constant.** Set `ENABLED` to `true`. Change its pin test to pin `true`, citing R466. With the constant on:
   - the writer emits root 3;
   - a root-2 ledger migrates at pre-start;
   - every new fluid fill and cargo transfer gets its account.
2. **Format-dependent expectations only.** Run the full unit suite and the full GameTest server with the log checker.
   For each failure, decide which kind it is.
   - **A value that changes only because the format grew:** a schema number, a byte size, a count already accepted
     (R443 and R445: 55, 53, 54, the 7,113-byte benchmark blob, the cargo blob edges, the 512-node case, the
     mixed-component blob), a receipt now KNOWN with a zero residual, or a new bounded log line. Update it under R441's
     rule, and record each change with its old value, new value and reason.
   - **Anything else:** a behaviour change, an unexpected hold, a lost or duplicated resource, a refusal that should
     not happen, or a timing failure. Stop and report it as a finding. Do not change that test.
3. **The log manifest.** Add exact bounded entries for `ARCE_ESCROW_ACCOUNTING_HELD`, `_RELEASED` and the audit line
   only where GameTests now emit them. Never widen a generic allowance.
4. **Evidence.** Run the full unit suite, `runData` with a clean generated diff, and the full GameTest server with the
   log checker, under the heavy-JVM lock. Then run, natively and on a locally built JAR, one fresh fluid fill and one
   fluid cargo case from the existing families with both restarts. They show that a root-3 ledger is written, read
   back and retired. The full native accounting cases are the next task.

## Rules

- Do not delete tests, loosen assertions, raise timeouts or change a ceiling (AGENTS.md section 5). Every re-pin is a
  format value with its record.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Heavy runs go under the lock, and server copies are deleted after
  each run.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by another engine follows before the merge.
- The record gives the constant change, a table of every re-pin, the findings, the commands and results, and the
  native smoke results. Its last line, and the last line of your answer, is the path of `records/W372.md`.
