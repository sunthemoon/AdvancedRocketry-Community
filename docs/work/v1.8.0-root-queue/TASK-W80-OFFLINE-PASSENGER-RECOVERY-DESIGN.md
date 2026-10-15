# TASK W80: design note for releasing a rocket held by offline passengers

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. This is design work only: write
no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w80-offline-passenger-design` (from the shared branch) |
| Branch | `codex/v1.8.0-w80-offline-passenger-design` (local only; Root merges) |
| Evidence of the hold | [records/W63.md](records/W63.md), the F2_TWO case: after three offline restarts, each of two recorded passengers reconnected while the other was absent; the rocket stayed locked with its PREPARED transfer record kept, unchanged, through six JVM starts |
| Related | [records/W02F.md](records/W02F.md) (the review that named the limitation); ADR-065 and `docs/11-RISK-REGISTER.md` R-035 (offline passengers can hold a handoff indefinitely); [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) revision 2, section 6 (the proposed R-035 text) |
| W11b2 state | Branch `codex/v1.8.0-w11b2-rocket-integration` (continuation 18 is working there). Read it only through Git at a commit you name. Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W80-DESIGN.md` (two to four pages) and a short record `records/W80.md` |

## Why

A transfer record that names several passengers waits until every one of them is online at the same time.
If they never are, the rocket and its resources stay locked for ever, and there is no operator path out. The
fix touches transfer recovery and saved data, so AGENTS.md section 13 requires a design note and a read-only
review before code.

## What the note must settle, citing code

1. **Where the hold comes from:** the exact condition in the shared branch's recovery code (and in W11b2's
   governed flights at the commit you read) that waits for all passengers, and why it exists (no passenger
   may be left behind or duplicated).
2. **The operator action:** a command (for example under `/arce rocket recover`) that an operator runs to
   release such a rocket. What it does to the rocket, the resources, the transfer record and each absent
   passenger's saved position and riding state, so that no player is lost, duplicated or left inside a
   removed rocket when they next log in. What it refuses, and what it logs.
3. **Crash safety:** the order of writes and each crash point, with what a restart finds there. Reuse the
   checked journal writer; add no new acknowledgement order to W11b2's governed flights without saying so.
4. **Whether a time-based release is wanted.** Recommend one answer, with its risk (AGENTS.md section 3.3:
   any protection that drops or isolates player data needs a risk entry with scope, duration, log volume and
   recovery).
5. **Tests:** A0, GameTests, and native restart cuts in the style of W63's runner, with JVM-start estimates.
6. **Risk and ADR text** for Root, and **open questions**, each with a recommended answer.

## Commands

- No Gradle, GameTest or JVM run. `git show`, `git grep` and reading files are enough.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files
  Git tracks (stage your record before the check); write other paths as code spans.

## Not in this task

- Any production or test code.
- F3, F8 and N2 (they have their own design leaves), W11b3, and the satellite bay.
- ADRs, the risk register, status files and the ledger (Root).
