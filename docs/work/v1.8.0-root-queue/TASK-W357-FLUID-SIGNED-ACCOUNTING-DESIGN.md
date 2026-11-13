# TASK W357: design note for per-fluid signed accounting

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w357`, branch `codex/v1.8.0-w357`, at the shared head |
| Decision | Root decision R434 ([OWNER-DECISIONS-20261113](records/OWNER-DECISIONS-20261113.md); QUEUE row W357) |
| Deliverables | `docs/work/v1.8.0-root-queue/records/W357-DESIGN.md` (the design note) and `docs/work/v1.8.0-root-queue/records/W357.md` (a short record) |
| Write scope | Those two files only. No production code, test, script, register, ADR, status file or QUEUE change. |

## Why

The owner keeps all five switches off and asked to implement the fluid accounting first (2026-11-13). The risk
register's R-035 says that per-fluid signed accounting ("按流体有符号记账") is needed before admission and is not
implemented. [W11B2](records/W11B2.md) (around the paragraph that starts "Complete signed per-fluid/net-loss accounting
remains missing") explains the gap. Receipts keep the payload liability, hashes and current quantities. Their
`loss_known` field concerns missing endpoint contents, not a complete signed transfer balance, and must not be read as
proof of zero net loss. The change touches a saved form, so a design note and a separate read-only review come before
any code (AGENTS.md section 13).

## Read first

- The risk register `docs/11-RISK-REGISTER.md`, R-035: the fuel paragraph ("按流体有符号记账未实现") and the cargo
  paragraph ("准入前另需：按流体有符号记账").
- [W11B2](records/W11B2.md), [W11B2-DESIGN](records/W11B2-DESIGN.md), and item 4 (review F5, `audited_loss` and
  `loss_known`) of [TASK-W11B2-DESIGN-2](TASK-W11B2-DESIGN-2.md).
- [W11CD-DESIGN](records/W11CD-DESIGN.md) for the fluid cargo kinds, and sections 6.3, 6.4 and 7.3 of
  [C3-SWITCH-REPORT](records/C3-SWITCH-REPORT.md).
- The code under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/`: the ledger and its
  codec and schema, receipts, floors, `EscrowSignatureKind` (`FLUID_TANK_V1`), `EscrowLoaderEndpoint`,
  `EscrowFluidCargoEndpoint`, `EscrowReconciliationCheckpoint` and the settlement code.

## What the design note must answer

1. **Definition.** What exactly is recorded, for each fluid, at each step: preparation, apply, settlement O1 to O4,
   ABANDON, REPAIR, operator settlement and pruning. Give the sign convention and the balance identity that is checked,
   for example source change plus destination change plus audited loss equals zero for each fluid. Say when the
   balance is known and when it is unknown.
2. **Consumers.** The fuel loader (`FLUID_TANK_V1`) and both fluid cargo directions. Say whether the item cargo kinds
   need a count balance of their own, and why or why not.
3. **Saved form.** The new fields or records, the schema versions of the ledger and the receipts, and the migration of
   existing ledgers. Check the claim that production worlds have no ledger while admission is off (NO_FILE), and say
   which worlds the migration therefore concerns. Say what each older JAR does with the new form, and whether R-044's
   downgrade behaviour changes.
4. **Bounds.** The maximum number of fluids per operation, the size in bytes against the receipt and record ceilings
   (W113 and W114), and any per-Level budget. Every variable-length field must have a limit.
5. **Failure cases.** What the accounting says when a write is UNKNOWN, when an endpoint is missing, on ABANDON, on
   REPAIR, on REBUILD_SOURCE (the X03 case, `FF_REBUILD_SOURCE`) and on cut-E2.
6. **Checks.** Where settlement and readback check the balance, and what a mismatch does: hold, REPAIR, refusal and
   log lines, with log volume bounds. No exception may be used as an ordinary save control.
7. **Tests.** Unit tests, GameTests, and the native cuts to add or to run again. Name every existing family or oracle
   whose expectations change, and why.
8. **Slices and estimate.** The implementation in reviewable slices, the files each one touches, and an estimate per
   slice.
9. **Risks.** Text Root can use for the register: scope, duration, log volume and recovery for any new hold or
   refusal.

Keep the note to what the implementation needs. Mark each open question that needs Root's decision as a numbered
question, with your recommendation.

## Rules

- Design only. Do not change `src/`, `tests/`, `scripts/` or any Root-owned file.
- Commit the two files on your branch, with a message in the style of AGENTS.md section 6. Do not push.
- English, full sentences, UTF-8 without BOM, LF line endings. Links only to tracked files.
- The last line of your answer is the path of `records/W357.md`.
