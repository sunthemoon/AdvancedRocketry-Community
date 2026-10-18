# TASK W92-review: independent read-only review of P1-F8 (W85 Design M)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. Both Codex slots are busy, so this is a fresh Claude
session with no context from the implementer.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w92-p1-f8`, commits `2a28c604` to `df25825a` (code) and `3ad6e56a` (record `records/W92.md`), on top of the shared branch at `e460bdc1` |
| Task it answers | [TASK-W92-P1-F8](TASK-W92-P1-F8.md) |
| Design (frozen) | [records/W85-DESIGN.md](records/W85-DESIGN.md) revision 3, Design M (section 4), its crash points (section 5), closure (section 6) and tests (section 7) |
| Checkout | `D:/GitHub/arce-v180-w92-review`, detached at `3ad6e56a`, made for you; do not commit in it |
| Evidence to check | `D:/GitHub/ARCE-Task-Evidence/root-queue/W92/` (the passing native run in `native-all2/`, the GameTest run in `gametest-iter1c/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W92-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Design M exactly:** today's order (T's checked removal before any restored block), the shared no-change
   pre-checks, the pending entry P (at most 64) and its reservation and slot in `prepareLaunch`, the one-way restoration
   latch, the identical write-back of T only before restoration starts or after a certified clean rollback, T and its
   locks kept until that write is acknowledged, zero-fuel requests and the `DISASSEMBLY_PENDING` status.
2. **Crash safety:** for each crash point in W85 section 5, what a restart finds, and whether the native cuts K1, K2,
   K4, K5, K9 and K10 show it (including the recorded loss in K2 and the residual in K5). Check the harness change in
   `df25825a` (each restart now waits for the rebuilt rocket to land) does not hide a failure.
3. **The governed record:** the record says a governed terminal successor with a schema-3 record would otherwise be
   removed on disassembly, and Design M's "schema 2, no native transfer" check prevents it (G9). Confirm.
4. **No saved-format change**, as the record claims; the journal change is only `validateWriteBack` beside
   `validateNewRecord` and test helpers.
5. **The open choices** in the record's section 4 (`REGION_BUSY` for a landed record, a pending answer counting as
   command success, notifying a connected fake player, the escrow reconciliation boundary refusing while P exists).
6. **Log expectations:** the four new exact expectations and the pinned ERROR total moving from 98 to 102 must match
   only lines the new tests drive.
7. **Evidence:** the GameTest and unit runs used a working tree that differed from `035f89b9` by two removed blank
   lines; say whether that matters. Rerun the transfer and disassembly unit tests
   (`gradlew.bat --offline test --tests '*rocket.server*' --tests '*rocket.flight*'`) only when
   `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` does not exist; take no lock and run no GameTest or server.

## Output format

- A verdict: MERGE, MERGE-WITH-FOLLOWUP, or FIX-FIRST.
- Findings, each with an ID (W92-R1, ...), a severity, the file and line, the problem, and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
