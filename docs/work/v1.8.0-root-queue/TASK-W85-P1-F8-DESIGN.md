# TASK W85: design note for part-1 finding F8 (a durable "disassembly under way" record)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. This is design work only: write no
production or test code. Do not start subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w85-p1-f8-design` (from the shared branch) |
| Branch | `codex/v1.8.0-w85-p1-f8-design` (local only; Root merges) |
| Finding | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-review/REVIEW.md` (SHA-256 `8c1da51138a655581c8a3256841615fd556c627eaed46edcf421b4fcc67de499`), F8: the landed reservation is released before disassembly runs |
| Analysis so far | [records/W77.md](records/W77.md), "F8: retained claim through disassembly (blocked before code)": the current order, why moving the call alone is not crash-safe, and the required tests |
| Contract | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) revision 2: section 1's P1-F8 row and the P1-F8-D leaf in section 5. W11b2's design 2 (`records/W11B2-DESIGN-2.md` on branch `codex/v1.8.0-w11b2-rocket-integration`): read it and W11b2's code only through Git at a commit you name; do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration`, where continuation 22 is working |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W85-DESIGN.md` (two to four pages) and a short record `records/W85.md` |

## Why

W11b3 may not turn on admission until F8 is closed (ADR-065, "Open before W11b3 or any admission"). W77 showed that
a crash-safe fix needs a durable record that disassembly is under way, so that transfer recovery cannot recreate a
rocket over blocks that disassembly has already restored, or over blocks a player has since used. That is a saved-data
change, so AGENTS.md section 13 requires a design note and a read-only review before code.

## What the note must settle, citing code

1. **The current order** in `RocketDisassemblyService`, `RocketDisassemblyTransaction`, `RocketTransactionSavedData`
   and the transfer journal, and each crash point with what a restart finds today.
2. **The durable record:** where it lives (a transfer-journal state, a checked disassembly record, or another option),
   its schema version and bounds, and how it is written and acknowledged. Compare at least two options and recommend
   one. If it touches the transfer journal, say how it fits journal root 3, W44's outbox and W11b2's governed record 3,
   without changing any of their acknowledgement orders.
3. **The new order:** the claim is kept through obstruction and rollback; recovery of the old transfer is fenced before
   restored resources become available; checked removal and release happen once; cleanup is idempotent.
4. **Each crash point** in the new order and what a restart finds, as a table.
5. **Governed flights:** whether W11b2's governed (record-3) rockets reach this path, and if so how the two fit.
6. **Player-visible behaviour:** the first-attempt REGION_BUSY answer the review noted, and what a player sees while a
   disassembly is pending.
7. **Tests:** A0 and GameTests (obstructed and rolled-back disassembly keep the claim; a successful one releases it
   once) and native restart cuts on both sides of restoration, removal acknowledgement and release, including a failed
   removal writer and two restarts each, with JVM-start estimates.
8. **Risk and ADR text** for Root, and **open questions**, each with a recommended answer.

## Commands

- No Gradle, GameTest or JVM run. `git show`, `git grep` and reading files are enough.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git
  tracks (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code; F3 and N2; W11b3's other leaves.
- ADRs, the risk register, status files and the ledger (Root).
