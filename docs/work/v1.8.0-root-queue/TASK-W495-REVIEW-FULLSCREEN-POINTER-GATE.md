# TASK W495: review W494's fullscreen pointer-gate change independently

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w495`, branch `codex/v1.8.0-w495` from the shared branch |
| Decision | Root decision R716 (QUEUE rows W494 and W495), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Under review | Commit `77dbcf42b` on `codex/v1.8.0-w494`, against its parent `ada5c3367`; the worker's record is commit `03f4681b2`. Read them with `git show` and `git diff` in this worktree, which shares the repository |
| Sources | [TASK W494](TASK-W494-FULLSCREEN-POINTER-GATE.md), the harness under `scripts/campaign_client/` and its tests |
| Outputs | The record `docs/work/v1.8.0-root-queue/records/W495.md` only |

## Why

The harness refused a window covering the desktop, because GLFW's pointer clip is then the same with the pointer held
or shown. A Codex session added a second signal, Windows' cursor visibility, for that case only. The gate decides
whether input is safe to send to the game, so a wrong reading could send keys into the wrong screen. Review it
independently before Root merges it.

## What to check

1. **Fail closed.** Check that the change refuses in every case where the clip and the visibility could mislead,
   including:
   - a desktop of several monitors;
   - a borderless window that covers one monitor;
   - a window larger than the desktop;
   - `CURSOR_SUPPRESSED`;
   - a cursor hidden by another program;
   - a change between the reads.
2. **No other case changes.** Every input that returned or raised before must return or raise the same now, except the
   case that used to be refused.
3. **Tests.**
   - Run `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q` on the commit under review.
   - Check that the new tests are Linux-safe: no Windows-only call at import or run time without a mock.
4. **README.** Check that the README matches the code.

## Outputs

The record lists:
- each finding, with its file and line, a concrete input that goes wrong, and its severity;
- the commands and their results;
- a verdict: merge, merge after named fixes, or reject.

## Rules

- Do not change the code under review or any file outside your record.
- Do not start Minecraft, Gradle or any other JVM.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on your branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
