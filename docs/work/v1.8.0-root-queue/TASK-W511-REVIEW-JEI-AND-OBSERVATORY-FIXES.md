# TASK W511: review W507's JEI and Observatory fixes independently

Root: Claude. Date: 2026-11-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w511`, branch `codex/v1.8.0-w511` from the shared branch |
| Decision | Root decision R737 (QUEUE rows W507 and W511), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Under review | The five fix commits on `codex/v1.8.0-w507`, against `76576ccd4`: `720c1fa4c` (Centrifuge height), `90a896c6c` (recipe fluids), `af4f5179a` (unformed Observatory buttons), `1d6bdbd9c` (Precision Assembler title) and `60109476e` (Cancel follows the server, R736). W507's record is `docs/work/v1.8.0-root-queue/records/W507.md` in commit `f2ca7daf1`. Read them with `git log`, `git show` and `git diff` in this worktree, which shares the repository |
| Sources | [TASK W507](TASK-W507-JEI-AND-OBSERVATORY-FIXES.md), QUEUE rows W505 (R732) and W507 (R736), [W505](records/W505.md) and the round-07 frames it cites (read only), [W487](records/W487.md) for the language rule, and the C6 code under `src/` |
| Outputs | The record `docs/work/v1.8.0-root-queue/records/W511.md` only |
| Time | Deliver by **16:00** |

## Why

`src/` stays frozen at C6 for the campaign (R600). A Codex session prepared these fixes on an unmerged branch, so that
the candidate decision after phase 2's first full pass rests on tested changes (R716). Review them independently
before Root relies on them.

## What to check, commit by commit

1. **Matches the task.** The change does what TASK W507 and R736 ask, no more. Note every change outside the files
   W507's record names.
2. **Correct.** Name any input, locale, GUI scale or state in which the fix is wrong, with file and line:
   - the Centrifuge category at 1280 by 720 and GUI scales 2, 3 and 4, with the most inputs and outputs a recipe can
     have, and every other classic category, whose size must not change;
   - the fluid rule for every category and every fluid amount, including amounts above 16,000 mB and tooltips;
   - the Observatory's Start and Cancel for owner and non-owner, processor and Observatory, formed and unformed, with
     and without an active job, against what C6's server accepts;
   - the Precision Assembler title in English and Chinese, and that no text generated before v1.8 changed in place.
3. **Tests.** Each new test fails without its fix: read it against the code before the fix. No existing assertion
   was weakened, deleted or given a wider tolerance.
4. **Boundaries.** No save schema, network format, permission, service logic or ADR change. English and Chinese stay
   in step, and the generated language files match their providers.
5. You may run targeted unit tests with `gradlew.bat --offline test --tests '<class>'`, holding the heavy-JVM lock
   as WORKER-RULES says and releasing it by 21:00. A full build is not needed; hosted CI runs it on the branch.

## Outputs

The record lists:
- each commit with a verdict: sound, sound after a named fix, or wrong;
- each finding, with file, line, a concrete case and its severity;
- the commands and their results;
- the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not change the code under review or any file outside your record.
- Do not start a Minecraft client. Release the heavy-JVM lock by 21:00, because tonight's client run takes the
  machine from 23:30.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Never close, change or block a program you did not start.
- Commit the record on your branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
