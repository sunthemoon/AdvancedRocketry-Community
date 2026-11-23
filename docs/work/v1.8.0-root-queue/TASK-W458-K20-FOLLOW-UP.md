# TASK W458: K20 follow-up, the K02 and K12 rows in checklists A to J

Root: Claude. Date: 2026-11-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w458`, branch `codex/v1.8.0-w458` from the shared branch |
| Decision | Root decision R668 (QUEUE row W458), closing the final audit's M9 ([W459](records/W459.md), R658) |
| Sources | [TASK-W456](TASK-W456-K20-SECOND-PASS.md) and [W456](records/W456.md) (the same rules and style); K02's records [W431](records/W431.md), [W437](records/W437.md), [W447](records/W447.md), [W461](records/W461.md), [W462](records/W462.md), [W463](records/W463.md), [W464](records/W464.md) and [W465](records/W465.md) with their QUEUE rows (Root decisions R611, R631 and R661 to R667); K12's record [W454](records/W454.md) and its QUEUE row (R655); [KNOWN-ISSUES](../../releases/v1.8.0/KNOWN-ISSUES.md) entry KI-V180-DISASSEMBLY-01 |
| Inputs (read only) | K02's and K12's fixtures, recipes and runs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/` and `.../K12/` |
| Outputs | Tracked: the checklists `docs/work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-A.md` to `-J.md` that carry K02 or K12 rows, and the record `docs/work/v1.8.0-root-queue/records/W458.md`. Nothing else |
| Lock | No server runs and no lock. Run the validator only. |
| Not allowed | As TASK-W456: no row, step, expectation, budget or oracle removed or changed; no pass marked; no NOT_COVERED, NOT_REACHABLE_ON_C6, partial or FAILED item turned into anything else; complete sentences only. |

## What to do

1. Replace each "in progress (W447)" and "in progress (W454)" note, and every remaining "needs package K02" or "needs
   package K12" note, with what the package delivered for that row: the fixture path and hash, recipe or run, the
   NOT_COVERED, NOT_REACHABLE_ON_C6 or partial items with their reasons, and Root's acceptance decision. For K02 that
   means at least: the flight matrix of record (W465, 7 PASS and 3 NOT_REACHABLE_ON_C6 under R666's loaded-ends
   contract, with W447's and W463's counted results kept as history), the sealed native cuts (W447 plus W461: a
   passing C6 attempt for all 89 rows, with W447's eight failed attempts kept), W437's twelve legacy rows as W447
   maps them, and what K02 still does not cover (passengers, clients, the three unreachable checkpoints). Each
   sentence must read correctly on its own.
2. Update each checklist's summary of package deliveries in progress, since none remains in progress.
3. **Record** a table of every changed note and run `python -B scripts/validate_repository.py`; its `Summary:` line
   must show `0 failed`.

## Rules

- Commit on the branch plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
