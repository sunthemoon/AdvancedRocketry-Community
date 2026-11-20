# TASK W413: the per-machine census and the recipe-graph check on candidate C5

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w413`, branch `codex/v1.8.0-w413` from the shared branch |
| Decision | Root decision R561 (QUEUE row W413) |
| Sources | [version document](../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md) sections 5, 6 (step 4: every machine gets recipes, JEI, saving, GameTests and visuals; step 6: tech-tree reachability and recipe-graph analysis), 9 and 14 (item 4); [PORTING_MATRIX](../../PORTING_MATRIX.md); the source at the shared head (`src/main`, `src/generated`, `src/test`); C5's hosted run 44681973284 (the GameTest classes it ran are the tracked ones at `9172113ad`); W402's inventory `D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/fixtures/K13/` (screens, classification); the release drafts [REQUIREMENT-MAP](../../releases/v1.8.0/REQUIREMENT-MAP.md) and [TEST-REPORT](../../releases/v1.8.0/TEST-REPORT.md) |
| Output | `docs/releases/v1.8.0/MACHINE-CENSUS.md` and a short record `docs/work/v1.8.0-root-queue/records/W413.md` |
| Write scope | Those two files. Read only otherwise. |
| Not allowed | Marking a requirement or Gate as passed. Any JVM, Gradle, server or client. |

## What to do

1. **List every machine** C5 registers: each machine block, multiblock controller and classic controller, with its
   registry ID, the framework it uses (the v1.2 machine framework or the exception ADR that allows otherwise,
   version section 5) and its matrix row.
2. **For each machine**, one row with: its recipes (data files and count, or why it has none); its JEI category (or
   why none); how it saves (the block entity's saved fields and schema version, with the class and line); its
   GameTests, split into happy path, failure path and restart or reload (test class and method names; "none" where
   none exists); its visual assets (model, textures, and W402's class: high-risk, sampled or family member); its
   screen; and its player documentation. Version section 9 asks for at least a happy, a failure and a restart test
   per machine: mark each machine that lacks one of the three.
3. **Recipe graph and tech tree (version 6.6, 9 and 14.4).** Find the existing reachability analysis (a GameTest, a
   unit test or a script that checks the recipe graph and the research tree for unreachable nodes and hard cycles)
   and say what it checks and where it ran (C5's hosted run, by class name). If none exists or it does not cover the
   whole graph, say exactly what is missing.
4. **Findings.** Number each gap (a missing test kind, a machine without JEI or recipes, a framework exception
   without an ADR, an unreachable recipe), with its evidence and a severity under docs/17 section 3, and what would
   close it. Do not fix anything.
5. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`. Link only to tracked files; use a code
   span for paths outside Git.

## Rules

- Commit on the branch in one commit in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
