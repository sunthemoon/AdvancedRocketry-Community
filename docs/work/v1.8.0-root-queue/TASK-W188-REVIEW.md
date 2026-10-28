# TASK W188-review: independent read-only review of the JEI tag-recipe fix and the v1.8 matrix

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w188` at `b4e9b0432`, cut from the shared head `ec3f373e4`, with W181 merged in (`fee873cd5`). Review W181's commits `25f0b6dd8` and `b72f03975` and W188's `520b5cc35..b4e9b0432`. Review worktree `D:/GitHub/arce-v180-w188-review`, a detached checkout of `b4e9b0432` |
| Tasks it answers | [TASK-W181](TASK-W181-JEI-MATRIX.md) and [TASK-W188](TASK-W188-JEI-TAG-RECIPES-ON-SERVERS.md) |
| Records | `records/W181.md` and `records/W188.md` on the branch (W188's design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W181/` and `.../W188/` |
| Hosted run | the run on `b4e9b0432` (Root pushed the branch at 01:54); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W188-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The cause.** The record says a joining client gets the server's recipes before its tags, JEI 15.56 restarts on
   the recipe update without waiting for tags, and the classic recipes expand tags on the client at lookup. Check this
   against the code and the matrix logs.
2. **The fix (`aaf76ba43`).** The JEI plugin holds every recipe that names a tag until this server's tags arrive, then
   adds them. Joining or leaving a server resets the hold.
   - Is any recipe now held forever, for example when the server sends no tag update, or sends it before JEI starts?
   - Does anything else on the client, such as a mod screen or the recipe book, read the same tag-dependent data
     before the tags arrive?
   - Is the code client-only, apart from the two read-only checks in common code? Run `check_client_imports.py`.
   - Does the reset hold on a reconnect to a different server, and in single-player?
3. **Unchanged:** recipe sync, its bounds, the serializers and the server's lookup.
4. **The runner (W181 and `e03aa8e6d`).** It expects every classic category and checks the last count per category
   after the connection. One existing Python test now passes a repeated correct final count, as R148 allows. Confirm
   that a wrong final count, a missing category and a malformed line still fail.
5. **The markers (W181).** One bounded INFO line per category, client-only.
6. **The matrix evidence.** All four cells and both restart cycles, on a JAR built from committed source. Check the
   JAR's hash against the build record.
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes and the
compat and recipe packages; the runner's Python tests; `python -B scripts/check_client_imports.py`. Check 6 GiB free
memory first. No GameTest, matrix run, runData, full build or server. Delete the worktree's `build`, `.gradle` and
`logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W188-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
