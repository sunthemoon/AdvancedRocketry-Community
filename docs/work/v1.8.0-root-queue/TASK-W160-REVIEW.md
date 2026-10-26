# TASK W160-review: independent read-only review of W160 (the W80 command and view)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w160` at `5e3498171`, 17 commits on `d33b75350`. Read `git diff d33b75350..5e3498171`. Review worktree `D:/GitHub/arce-v180-w160-review`, a detached checkout of `5e3498171` |
| Task it answers | [TASK-W160](TASK-W160-W80-REJOIN-COMMAND.md) |
| Design | [records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3; sections 3, 6 and 7 and Appendix A, with the R106 and R108 corrections in section 4), [records/W158.md](records/W158.md), [records/W159.md](records/W159.md), the W80 note in ADR-065 and the W80 part of R-035 |
| Record | `docs/work/v1.8.0-root-queue/records/W160.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W160/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W160-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Permission.** The leaf's `requires` needs level 3, and the handler calls `source.hasPermission(3)` itself before
   any read of the transfer or the sidecar. Tick, load and scheduled functions and command blocks (including minecart
   command blocks and nested `execute`) are refused at execution with no sidecar change. Operators, functions an
   operator runs, the console and RCON are accepted. No origin-provenance mechanism, reflection, stack inspection or
   command rewriting.
2. **The command.** Every section-3 refusal changes nothing. A repeated submission reports the release status instead
   of adding a batch. The command uses W158's admission, the same path as the gated internal entry point, replies once
   W80 has admitted or refused, and loads no chunk outside the server-recorded slots. Governed records stay refused.
3. **The view.** `/arce rocket rejoins [player]` writes nothing and loads nothing, shows at most eight entries per
   response and caps UUID lists at 16.
4. **The production fix `b79074899`** (later H1 riders after a completed seat revocation). Check it against the
   design: it must never admit a second release for a seat or rider that already has one, and it must not change what
   the fence and B0 protect.
5. **Nothing weakened.** Several commits change tests and manifests (`a7d535352`, `b492fd27e`, `6e79fea89`,
   `8aeda72a6`, `82e58242d`, `26e933840`). No existing assertion may be loosened, and new log lines need exact entries
   in the right manifest (the `Failed to load function` ERROR only in the level-2 server run's manifest). No saved
   format, journal root, sidecar schema, bound, budget, timeout or protocol change. Player text is in both language
   files. New tests fail on the old code. W159-R4 is closed as TASK-W160 item 5 describes.
6. **Evidence.** The record's Appendix A mapping and the 19 counted native starts: no row is claimed from evidence
   that does not cover it.
7. **The full-suite failure.** W160's complete GameTest failed once in the unchanged
   `RocketRejoinReadbackGameTests.realReplacementFailureEventCannotProveOldFile` ("Prompt save retried before the
   global 100-tick cadence", `complete-regression/latest.log:4423`); it passed alone. Say whether anything in W160 can
   cause it, and what in the test or the production code makes it depend on the run.
8. **Merge.** List the conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`),
   which now has W152 (sealed staging). W152H (branch `codex/v1.8.0-w152h`, in progress) adds the cross-guard
   between release planning and sealed staging; note any overlap with it.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- W160's new unit classes alone, then the `rocket.rejoin` package and the W158 release classes;
- `tests/test_run_w158_release_cuts.py`, `tests/test_run_w159_rejoin_cuts.py`, `tests/test_w160*.py` and
  `tests/test_check_gametest_log.py`.

**Take the heavy-JVM lock for every Gradle run in this review**, with the atomic pattern and the 90-second hand-off;
W152H shares it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`,
`.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W160-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
