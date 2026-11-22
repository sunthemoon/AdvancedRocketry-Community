# TASK W451: passive observation of X01's lost-ledger late-loss states

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w451`, branch `codex/v1.8.0-w451` from the shared branch |
| Decision | Root decision R637 (QUEUE row W451), following [W448](records/W448.md) sections E7 and E8 |
| Sources | [W448](records/W448.md) (E7: X01-05/07, X01-06/07, X01-07/01 and X01-09/07; E8: X01-11/12; the passive two-start observation it describes), [W368-DESIGN](records/W368-DESIGN.md), the harness `scripts/run_v180_crash_replay.py` and `scripts/v180_crash_replay.py` with W448's triage options, the cargo runners `scripts/run_v180_cargo_cuts.py`, `scripts/run_v180_item_cuts.py` and `scripts/run_v180_cargo_s4_s5_cuts.py`; C6's code under `src/` (read only) |
| Inputs | X01 S3 and W448's triage under `D:/GitHub/ARCE-Task-Evidence/x01-crash-replay/c8fa16e16be87554f26c402546733df6555d15e6/` and `...-triage/` (read only); the C6 JAR, template and pins exactly as W448 records them |
| Outputs | Tracked: an opt-in harness change with tests, and the record `docs/work/v1.8.0-root-queue/records/W451.md`. Outside Git: a new follow-up below the triage directory (W448's `--triage-run followup-<name>`) |
| Lock | Native runs take the heavy-JVM lock as the harness already does (per case, at least 90 seconds between holds); detached launchers start with no console window (WORKER-RULES). Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to earlier evidence. Turning any existing failed state into a pass, or changing an oracle, outcome, marker, count or timeout. Changing a default of the harness or runners. |

## What to do

1. Rebuild the five states named above from their sealed traces and kill images exactly as S3 did. Add an opt-in
   observation mode that starts each copy twice without the mutating and asserting release recovery probe, with
   `save-all flush` and an orderly stop each time, and keeps the raw ledger, the endpoint payloads (fluid and item
   amounts per endpoint, the rocket's tanks and inventory), identities, markers and locks before and after each save.
2. Compare, per state, the typed totals before the kill and after each start, and separately the authority and history
   (sequence, accounts, receipts, settlement tombstones). Report each state as: quantities conserved with authority or
   history lost, quantity lost or created (with the amounts), or undecidable (with what is missing).
3. These observations add evidence; they do not change the S3 or triage verdicts. **Record** the tables, commands and
   hashes, and run `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
