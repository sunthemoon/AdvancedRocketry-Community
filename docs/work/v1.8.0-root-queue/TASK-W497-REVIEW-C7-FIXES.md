# TASK W497: review W493's unmerged fixes for P01 to P13 independently

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w497`, branch `codex/v1.8.0-w497` from the shared branch |
| Decision | Root decision R719 (QUEUE rows W493 and W497), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Under review | The 13 commits from `9e8aa491c` to `b244c047d` on `codex/v1.8.0-w493`, against `ada5c3367`. Read them with `git log`, `git show` and `git diff` in this worktree, which shares the repository. W493's record is `docs/work/v1.8.0-root-queue/records/W493.md` in commit `b244c047d` |
| Sources | [W487](records/W487.md) sections 2 to 7 (the proposals, accepted by R709), [TASK W493](TASK-W493-C7-FIXES-P01-P13.md), and the C6 code under `src/` |
| Outputs | The record `docs/work/v1.8.0-root-queue/records/W497.md` only |
| Time | Deliver by **21:00**. If you run Gradle, hold the heavy-JVM lock as WORKER-RULES says and release it by 21:00 |

## Why

`src/` stays frozen at C6 for the campaign (R600). A Codex session prepared the fixes for W487's product defects on an
unmerged branch, so that the candidate decision after phase 2's first full pass can rest on tested changes (R716).
Review them independently before Root relies on them.

## What to check, commit by commit

1. **Matches the proposal.** The change does what W487 proposed for that defect, no more. Note every change outside
   the files W487 names.
2. **Correct.** Name any input, locale, GUI scale or state in which the fix is wrong, with file and line. For P01,
   check the placement of all six hulls against their patterns and say whether the five non-assembler hulls, which
   W493 centred without scaling, overlap or float. W493 asked about scale and got no answer; give your view.
3. **Tests.** Each test that W493 says fails on C6 really depends on the fix: read it against the C6 code. Check that
   no existing assertion was weakened, deleted or given a wider tolerance. W493 changed existing pins in
   `Round7ScreenTextTest`, `ClientReadoutTest`, `WarpBodyLabelTest` and `K4ObjModelTest`; say whether each change
   follows from the fix.
4. **Boundaries.** No save schema, network format, permission, service logic or ADR change. English and Chinese stay
   in step, and the generated language files match their providers.
5. You may run targeted unit tests with `gradlew.bat --offline test --tests '<class>'` under the lock. A full build
   is not needed.

## Outputs

The record lists:
- each commit with a verdict: sound, sound after a named fix, or wrong;
- each finding, with file, line, a concrete case and its severity;
- your view on the five hulls' scale;
- the commands and their results;
- the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not change the code under review or any file outside your record.
- Do not start a Minecraft client. Release the heavy-JVM lock by 21:00, because tonight's client run takes the
  machine from 23:30.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit the record on your branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
