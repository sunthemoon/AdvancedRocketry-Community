# TASK W157-rereview: re-review of the W157F fixes

Root: Claude. Date: 2026-10-25. This continues your W157 review session (`W157-review/REVIEW.md`, SHA-256
`bd9845f8…876b`, verdict BLOCK on W157-R1). Same rules: read-only, no commit, write only to your output folder, no other
agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w157` at `f83c18e4`. Read `git diff a7be23e4..f83c18e4`. Your review worktree `D:/GitHub/arce-v180-w157-review` is now a detached checkout of `f83c18e4` |
| Task | [TASK-W157F](TASK-W157F-STRICT-NATIVE-CHECK.md) and Root's follow-up on the satellite failure (the W157F section of `records/W157.md`) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W157F/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W157-review/REREVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W157-R1 closed.** Unused starts get the strict scanner; refused starts get an exact list of expected lines; an
   unrelated ERROR, FATAL or project WARN fails a case. The 15 runner tests cover a clean log, the expected refusal, a
   duplicate or missing refusal, changed bytes, a premature ready marker and injected failures. Rerun your two probes
   against the new runner.
2. **The `ResearchRuntime` fix** (`ab02b74a`): a stop after a refused or failed start is clean without catching or
   hiding an exception, and normal start and stop are unchanged.
3. **The vanilla early-stop line:** it is listed exactly and with its reason, and cannot match a project exception.
4. **The satellite failure:** whether the record's diagnosis (no interaction with W157's changes; 3 isolated batch runs
   and a full run pass) is sound, or whether you see a link.
5. **Nothing weakened**, and the merge with the shared head and W152 as before.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the changed unit classes alone and the
runner's Python tests. No GameTest, server or heavy-JVM lock. Delete the worktree's `build`, `.gradle` and `logs`
afterwards.

## Output format

One verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings only where something remains, with an ID (W157-R2, ...);
the commands you ran and their results; the SHA-256 of REREVIEW.md on the last line.
