# TASK W420: the operational tech-tree analysis and the archived graph report

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w420`, branch `codex/v1.8.0-w420` from the shared branch |
| Decision | Root decision R571 (QUEUE row W420), following R564 |
| Sources | [MACHINE-CENSUS](../../releases/v1.8.0/MACHINE-CENSUS.md) section 7 (the existing recipe-graph checks, where they ran, and the exact limits of that proof) and findings F13 and F14; the version document's sections 5 (no unreachable node or hard cycle), 6 step 6, 9 and 14 item 4; the existing graph tests and their generators in `src/test` and `src/main`; the research, ground-survey, analysis and mission services |
| Outputs | Unit tests and test-only helpers in `src/test/`; a committed report `docs/releases/v1.8.0/RECIPE-GRAPH-REPORT.md`; the record `docs/work/v1.8.0-root-queue/records/W420.md` |
| Write scope | Those files. No product code (if the analysis needs a product hook, stop and record it), no other test or record. |
| Not allowed | A full GameTest run, `runData` or a native server (compiling and unit tests are allowed). Relabelling item acquisition as device operability. |

## What to build

1. **F13**: extend the existing graph analysis, or add a complementary one, so that it also models the operational
   nodes the census names as unmodelled: ground survey and analysis, the research tree's nodes and prerequisites,
   satellite build and research, missions, and the operating preconditions of the endgame devices (for example a
   device needing a station, a dimension or a powered structure). Each node's prerequisites come from the code (cite
   the class and line). Check that every required node is reachable from a new world and that there is no hard cycle.
   Where a precondition cannot be modelled from the code, list it with the reason.
2. **F14**: make the analysis write a deterministic witness report (nodes, edges, reachable set, every unreachable or
   cyclic node with its path, the unmodelled preconditions), and commit its content as
   `RECIPE-GRAPH-REPORT.md` with the source commit, the command that produced it and the SHA-256 of the raw report.
3. Run the new and existing graph tests and the unit suite for the touched packages, and
   `python -B scripts/validate_repository.py`, which must end with `0 failed`.
4. A required node that is unreachable, or a hard cycle, is a finding: record it with its path and evidence and do not
   change product code.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
