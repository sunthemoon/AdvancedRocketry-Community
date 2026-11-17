# TASK W367: design note for X03, the operator's whole-world repair after REBUILD_SOURCE

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w367`, branch `codex/v1.8.0-w367`, at the shared head |
| Decision | Root decision R460 (QUEUE row W367) |
| Plan | [X01-X03-ISOLATION-PLAN](records/X01-X03-ISOLATION-PLAN.md), section 5 |
| Deliverables | `docs/work/v1.8.0-root-queue/records/W367-DESIGN.md` and `records/W367.md` |
| Write scope | Those two files only. No code, test, script or Root-owned file. |

## Why

Row X03 of [W326-MATRIX](records/W326-MATRIX.md) is NOT_COVERED. `FF_REBUILD_SOURCE` ends in a protected FLOOR_CONFLICT
(W142 R76: the conserved result 5,000 mB in the historical tank against 4,000 mB in the latest rocket and 1,000 mB in
the unloader). R-035's recovery is an offline whole-world repair. It keeps every file and hash, repairs a copy, does
two saves and restarts, and then replaces the whole world. No run has carried that repair out. The repair edits saved
data, so a design note and a separate read-only review come before any code (AGENTS.md section 13).

## Read first

- [W142](records/W142.md) (R73, R74 and R76: the REBUILD_SOURCE fixture and its attribution), the C-S5 rows of
  [W326-MATRIX](records/W326-MATRIX.md), and sections 7 and 8.6 of [C3-SWITCH-REPORT](records/C3-SWITCH-REPORT.md).
- R-035's recovery text in `docs/11-RISK-REGISTER.md`, and its new accounting paragraph.
- [W357-DESIGN](records/W357-DESIGN.md), section 6's REBUILD_SOURCE row: the repair must also hold for the coming
  ledger root 3 with fluid accounts.
- The code of the existing audited settlement (`EscrowOperatorDiagnostics` and the settlement intent, recovery and
  floor classes in `resourceescrow/`), the transfer journal, `EscrowCargoRecoveryCuts` and `scripts/run_v180_cargo_s4_s5_cuts.py`.

## What the note must answer

1. **What exactly is wrong** in the world after `FF_REBUILD_SOURCE`, store by store: the ledger floor and record, the
   transfer journal, the rocket entities, the tank and device contents. Say which state is authoritative.
2. **The repair path.** Choose between:
   - the existing audited settlement in a normal or maintenance start, if it can resolve FLOOR_CONFLICT without a
     code change;
   - a small offline tool with strict checks.

   Give the reasons for your choice. Prefer the existing settlement if it suffices. A new operator command is a public
   interface: if you propose one, give its permission, its checks and its bounded logs.
3. **The procedure.** Each step, with the checks before and after:
   - the whole-world backup and its hashes;
   - working on a copy;
   - the edits or commands;
   - two saves and restarts;
   - the conservation check;
   - replacing the world.

   Name every refusal condition. The procedure must never restore a single store, rebuild a resource or refund one.
4. **Both ledger forms.** Root 2 as today, and root 3 with fluid accounts after activation, including what the repair
   records in the account (a signed, operator-confirmed discrepancy or UNKNOWN, never an invented zero).
5. **The native case.** A new family runs `FF_REBUILD_SOURCE`, applies the procedure to a copy and starts the candidate
   twice. The oracle:
   - conservation;
   - no rocket or tank duplicated;
   - FLOOR_CONFLICT cleared;
   - an ordinary unload and launch work afterwards;
   - every file the procedure did not name unchanged.

   Add a negative control with one check skipped.
6. **Slices and estimate**, with the files each one touches.
7. **Operator text.** A short draft of the operator guidance, for the owner's approval before it is published.

Mark each open question for Root as a numbered question with your recommendation.

## Rules

- Design only. Commit the two files on your branch with messages in the style of AGENTS.md section 6. Do not push.
- English, full sentences, UTF-8 without BOM, LF line endings. Links only to tracked files.
- The last line of your answer is the path of `records/W367.md`.
