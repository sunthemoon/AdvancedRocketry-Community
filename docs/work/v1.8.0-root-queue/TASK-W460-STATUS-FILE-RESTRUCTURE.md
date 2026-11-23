# TASK W460: draft a current-state-only status file and move its history to the implementation log

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w460`, branch `codex/v1.8.0-w460` from the shared branch |
| Decision | Root decision R659 (QUEUE row W460), following the final audit [W459](records/W459.md) finding M7 |
| Sources | [docs/status/CURRENT_VERSION.md](../../status/CURRENT_VERSION.md) (about 615 lines); the implementation log `docs/work/v1.8.0-implementation-log.md`; [AGENTS.md](../../../AGENTS.md) section 10 (status files hold the current state only, one current figure per metric, history in the implementation log; open items from evidence packets stay in the status file until evidence closes them); [W459](records/W459.md); [QUEUE](QUEUE.md) for what is closed and what is open |
| Outputs | Tracked, on the branch: a proposed replacement `docs/work/v1.8.0-root-queue/records/CURRENT-VERSION-PROPOSAL.md` (the full new text of the status file), a proposed history section `docs/work/v1.8.0-root-queue/records/IMPLEMENTATION-LOG-HISTORY-PROPOSAL.md` (the text to append to the implementation log), and the record `docs/work/v1.8.0-root-queue/records/W460.md`. Root applies both after review; you do not edit the status file or the implementation log |
| Lock | No server runs and no lock. Run the validator only. |
| Not allowed | Editing `docs/status/`, the implementation log, the QUEUE or any other existing file. Dropping any item that is still open. Changing any figure, verdict or decision; marking anything passed. |

## What to do

1. Keep both YAML blocks, with their current values.
2. **Current state.** One section with the current figures only: candidate C6 and its evidence, the packages, the
   owner's packet, and what is left before release. One figure per metric.
3. **Open items.** Every item in the current file that is still open (each intermittent GameTest that "stays open
   until ...", V1 and V2, the reference node, the risks awaiting the owner, the owner's decisions, the ledger's five
   PLANNED rows, and so on) stays, each in one or two sentences with its link, its evidence and the condition that
   closes it. Check each against the QUEUE and the cited records; list in your record every item you judged closed
   and why, and keep any you cannot decide.
4. **History.** Everything else (C2 to C5 campaign figures, superseded decisions and sentences, closed intermittent
   tests, earlier merge narratives) goes, unchanged in substance, into the history proposal under a heading
   "Status-file history moved 2026-11-23".
5. **Record** a table mapping every paragraph of the current file to "kept (current)", "kept (open)" or "moved", and
   run `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Complete sentences; define code names at first use (AGENTS.md section 10).
- Commit on the branch plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
