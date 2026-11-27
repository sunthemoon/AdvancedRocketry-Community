# TASK W490: judge round 06's pending visual steps from the native frames

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w490`, branch `codex/v1.8.0-w490` from the shared branch |
| Decision | Root decision R713 (QUEUE row W490), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Method | Exactly the verdicts and the "How to judge" steps of [TASK W482](TASK-W482-PHASE2-VISUAL-JUDGMENT.md) |
| Sources | [W488](records/W488.md), the checklists `records/CAMPAIGN-CHECKLISTS-*.md`, [W482](records/W482.md) and [W485](records/W485.md) for earlier verdicts, and round 06 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``round-06/`, which is sealed and must not change |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``judgment-r06/`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W490.md` |
| Python | `D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe -B` |

## Why

Round 06's first night captured native F2 frames on the real GPU for 62 steps whose visual oracle a machine cannot
judge. The Codex session that captured them left them P_PENDING. You did not capture them, so your reading is
independent of that session. Root decides the step results from your verdicts (R713).

## Which steps

In `round-06/w488/per-step-results.json`, take every step whose result is `P_PENDING`. There should be 62. The
frames are indexed in `w488/sheet-index.json`, with HTML sheets and PNG contact pages in `w488/sheets/`. If your
count differs, stop and report the difference.

For J13 step 1, the oracle is the Moon's sky, not an Earth disc (R710).

## How to judge

Follow steps 1 to 7 of TASK W482's "How to judge". In addition, where W482 or W485 judged the same step from
round 04 or 05, say whether round 06's frames change that verdict, and why.

## Outputs

- `judgment-r06/visual-judgments.json`, with the fields TASK W482 lists, written row by row as you go so that a
  long session can resume from it.
- `judgment-r06/visual-defects.json`.
- A check that every cited frame's SHA-256 matches the file on disk.
- `judgment-r06/SHA256SUMS.txt` over these files, with LF line ends.
- The record:
  - the method;
  - the counts per row and verdict;
  - every VISUAL_FAIL and STILL_PENDING step, with its reason and frames;
  - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Read the sealed rounds only. Never write inside rounds 01 to 06 or the earlier judgment packages.
- Do not start any Minecraft client, server, Gradle task or other JVM. Never close, change or block a program you
  did not start.
- Your verdicts are proposals. Do not mark any step, row or Gate as passed anywhere.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
