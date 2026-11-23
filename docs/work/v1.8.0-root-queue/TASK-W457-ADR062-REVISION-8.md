# TASK W457: draft ADR-062 revision 8 for the five units whose production admission stays off

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w457`, branch `codex/v1.8.0-w457` from the shared branch |
| Decision | Root decision R653 (QUEUE row W457) |
| Sources | [ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) (sections 1, 3, 6, 7 and revisions 6 and 7); the content ledger `docs/work/v1.8.0-content-ledger.csv` and its validator [validate_v180_content_ledger.py](../../../scripts/validate_v180_content_ledger.py) with its tests; [W412](records/W412.md) finding MP-01 (the five PLANNED rows the closure check fails on) and QUEUE row W412 (R562: for any switch the owner keeps off, Root prepares the ADR-062 revision that defers that unit's production admission); [OWNER-FINAL-PACKET-DRAFT](records/OWNER-FINAL-PACKET-DRAFT.md) items P4 to P8 (Root recommends keeping all five off); [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md) sections 3 to 7, first subsection each (what is live with the switch off); [OWNER-DECISIONS-20261113](records/OWNER-DECISIONS-20261113.md) |
| Outputs | Tracked, on the branch only: the validator change with tests, a draft record `docs/work/v1.8.0-root-queue/records/ADR-062-REVISION-8-DRAFT.md` holding the exact revision text and the five exact ledger rows, and the record `docs/work/v1.8.0-root-queue/records/W457.md`. Root merges it only after the owner answers P4 to P8 |
| Lock | No server runs and no lock. Run the changed tests and the validator (R614). |
| Not allowed | Editing ADR-062 itself, the ledger, the asset plan, the status files or the QUEUE (Root applies the revision after the owner's answer). Changing any production code or switch. Loosening any other validator rule. |

## The problem

`validate_v180_content_ledger.py --closure` fails on five PLANNED rows: `block_variant:loader/1` (satellite bay, C17c)
and `block_variant:loader/2` to `/5` (item and fluid unloaders and loaders, C17a). Their code ships in v1.8 with
production admission off; the owner keeps the switches off for now (2026-11-13), and Root recommends "no" on P4 to P8.
The validator only allows `DEFERRED` with plan `post-2.0`, which is wrong for a unit whose code is present and whose
admission may be enabled in v1.9.

## What to do

1. **Validator.** Allow `DEFERRED` with plan `v1.9.0` only for a unit whose decision column names ADR-062 and whose
   notes say its production admission is off in v1.8 (choose an exact, testable marker), keeping every other rule,
   including the player-impact ADR requirement. Add tests for the accepted row, a `v1.9.0` row without the marker, and
   an unchanged `post-2.0` row. Linux-safe tests only.
2. **Draft revision text.** Write ADR-062 revision 8 as it would be appended: the five units, why they are deferred to
   v1.9 (production admission off; the owner's packet answers), what a player sees with the switch off (from each C6
   report section's first subsection), the player impact, the condition for enabling one later (the next-operation
   test R641 names, the reference and real-client rows, a new candidate), and the change-control note. Mark it
   PROPOSED; it becomes ACCEPTED only with the owner's answer.
3. **Draft ledger rows.** The five exact replacement rows, and a check in your record that the validator with your
   change passes `--closure` on a scratch copy of the ledger with those rows (do not commit the ledger change).
4. **Record** the commands and results, and run `python -B scripts/validate_repository.py`, which must end with
   `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
