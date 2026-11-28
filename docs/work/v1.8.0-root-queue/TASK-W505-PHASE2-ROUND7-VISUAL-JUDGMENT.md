# TASK W505: judge round 07's pending visual steps from the native frames

Root: Claude. Date: 2026-11-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w505`, branch `codex/v1.8.0-w505` from the shared branch |
| Decision | Root decision R731 (QUEUE rows W492 and W505), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Method | Exactly the verdicts and the "How to judge" steps of [TASK W482](TASK-W482-PHASE2-VISUAL-JUDGMENT.md) |
| Sources | [W492](records/W492.md), the checklists `records/CAMPAIGN-CHECKLISTS-*.md`, [W482](records/W482.md), [W485](records/W485.md) and [W490](records/W490.md) for earlier verdicts, and round 07 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``round-07/`, which is sealed and must not change |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``judgment-r07/`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W505.md` |
| Python | `D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe -B` |
| Time | Deliver by **14:00** local time |

## Why

Round 07's night captured native F2 frames on the real GPU for 20 steps whose visual oracle a machine cannot judge.
The Codex session that captured them left them P_PENDING. You did not capture them, so your reading is independent
of that session. Root decides the step results from your verdicts (R731).

## Which steps

In `round-07/w492/per-step-results.json`, take every step whose result is `P_PENDING`. There should be 20: D06 steps
1, 2 and 5 to 8; J19 step 1; A14 steps 1, 3 and 5; J18 step 8; I09 step 3; I04 steps 1 and 3 to 7; H06 steps 5 and
6. The frames are indexed in `w492/sheet-index.json`, with HTML sheets and PNG contact pages in `w492/sheets/`. If
your count differs, stop and report the difference.

## How to judge

Follow steps 1 to 7 of TASK W482's "How to judge". In addition:
- Nine of these steps passed in an earlier round (A14 steps 3 and 5, D06 steps 1, 2 and 5 to 8, H06 step 5), and
  A14 step 1 and J19 step 1 were STILL_PENDING. For each, say whether round 07's frames change that verdict, and
  why.
- Round 07 reran rows after operator corrections, listed in `w492/FINDINGS.md`. Judge only the frames of the attempt
  that `per-step-results.json` cites for each step, not frames of a failed earlier attempt.

## Outputs

- `judgment-r07/visual-judgments.json`, with the fields TASK W482 lists, written row by row as you go so that a
  long session can resume from it.
- `judgment-r07/visual-defects.json`.
- A check that every cited frame's SHA-256 matches the file on disk.
- `judgment-r07/SHA256SUMS.txt` over these files, with LF line ends.
- The record:
  - the method;
  - the counts per row and verdict;
  - every VISUAL_FAIL and STILL_PENDING step, with its reason and frames;
  - every changed earlier verdict;
  - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Read the sealed rounds only. Never write inside rounds 01 to 07, the authoring folders or the earlier judgment
  packages.
- Do not start any Minecraft client, server, Gradle task or other JVM. Never close, change or block a program you
  did not start.
- Your verdicts are proposals. Do not mark any step, row or Gate as passed anywhere.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
