# TASK W224-review: independent read-only review of the satellite root startup refusal

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w224` at `02d35b264` (tested source `5e33ce834`), cut from `aebf5782d`; 5 commits, 15 source files. Review worktree `D:/GitHub/arce-v180-w224-review`, a detached checkout of `02d35b264` |
| Task it answers | [TASK-W224](TASK-W224-SATELLITE-ROOT-OPTION-A.md), Root decision R214, owner decision D15 ("A：拒绝启动并给出具体诊断（推荐）") |
| Record | `docs/work/v1.8.0-root-queue/records/W224.md` (the design note at its top); the R-030 and ADR-050 drafts in `D:/GitHub/ARCE-Task-Evidence/root-queue/W224/` |
| Design | `records/W173-DESIGN.md`, section 3.A |
| Hosted run | the run on `02d35b264` (Root pushed the branch at 17:29); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W224-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Exactly the decided behaviour:** a root that decodes but whose canonical re-encoding exceeds the unchanged root or
   section bounds refuses the world's start, with one bounded diagnostic naming the record kind, ID and bound; a root
   at the bound and a normal root load exactly as before; a migration that produces such a root is caught; the
   original file is never rewritten, moved or truncated.
2. **No new false refusal:** no valid, savable root is refused (fresh, reloaded, migrated, at the bound, after W216's
   exact resident sizing), and the check uses the same canonical encoder and bounds as the writer.
3. **The changes to shared persistence code** (`BetaWorldMigrationEvents`, `ManagedSavedDataPayloadValidator`,
   `SavedDataSchemaMigrator`, the codecs, `SatelliteNbtSize`): no other registry or SavedData type changes behaviour;
   `5e33ce834`'s counting without retained buffers is exact.
4. **Cost and logs:** where the check runs, its cost at the bound (startup only?), and that the diagnostic stays
   bounded for a root with many bad records.
5. **Tests** failed first and would catch a wrong refusal or a missed one; the eight native scenarios are what the
   record says (refusal with the file unchanged, the repaired world starting, a normal world starting and restarting).
6. **The R-030 and ADR-050 drafts** match the code, keep every remaining limitation (lifecycle growth after load,
   unknown-key loss), and follow AGENTS.md section 3.3 (scope, duration, log volume, recovery).
7. **Nothing weakened:** bounds, budgets, formats and assertions; **merge conflicts** against the shared head and
   W226 (`codex/v1.8.0-w226`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed test classes and the
`satellite.persistence`, `satellite.mission` and `persistence.migration` packages. Targeted unit runs need no heavy-JVM
lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or server. Delete the worktree's
`build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W224-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
