# TASK W482: judge the 93 pending visual steps of rounds 04 and 05 from the native frames

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w482`, branch `codex/v1.8.0-w482` from the shared branch |
| Decision | Root decision R703 (QUEUE row W482), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | [W479](records/W479.md), [W480](records/W480.md), the checklists `records/CAMPAIGN-CHECKLISTS-*.md`, and rounds 04 and 05 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/`, which are sealed and must not change |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/judgment-r04-r05/`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W482.md` |
| Python | `D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe -B` |

## Why

Rounds 04 and 05 captured native F2 screenshots on the real GPU for 93 steps whose visual oracle a machine cannot
judge. The scorer left them P_PENDING. You did not capture them, so your reading is independent of the sessions
that did. Root decides the final step results from your verdicts (R703).

## Which steps

Take each row's latest round:
- round 04 for J13, B02, J16, D05, D06, C04 and A14;
- round 05 for every other row.

In that round's `per-step-results.json`, take every step whose `result` is `P_PENDING`. There should be 93 steps,
with 1,139 frame references and 905 distinct frames. If your count differs, stop and report the difference.

## How to judge

1. For each step, read its `original` text: the action, then the expected result after the `|`. That expected
   result is the oracle. Read the row's checklist line in `records/CAMPAIGN-CHECKLISTS-*.md` if the text is short.
2. Look at the step's frames:
   - use the round's `sheets/<ROW>*.png` pages for an overview;
   - open the native frame itself whenever the verdict depends on text, numbers, small icons or models;
   - confirm every failure on the native frame;
   - never judge from a file name, a caption or a log line alone.
3. Give one verdict per step:
   - **VISUAL_PASS:** the frames show what the oracle expects.
   - **VISUAL_FAIL:** a frame contradicts the oracle.
   - **STILL_PENDING:** the frames do not show enough to decide, for example the wrong screen, a covered element or a
     missing frame.
4. **Reason.** Write one or two sentences that name what the frame shows against the oracle. Cite each frame you
   relied on by its path and SHA-256.
5. **Where a step also has a machine check** in `results.jsonl`, say whether the readback agrees with what you see,
   but do not rescore it.
6. **Where a step asks for sound as well,** the sound half stays NOT_COVERED. Judge only the visual half and say so.
7. **On every frame you open, also note visual defects:**
   - the magenta and black missing texture;
   - raw translation keys such as `item.advancedrocketrycommunity.` or `gui.`;
   - clipped or overlapping text;
   - wrong or missing models.

   Record each with its frame, even where the step's own verdict does not depend on it.

## Outputs

- `judgment-r04-r05/visual-judgments.json`, with one entry per step:
  - the round, row, step, original text and verdict;
  - the reason;
  - the cited frames, each with its path and SHA-256;
  - any visual defects.

  Write it row by row as you go, so a long session can resume from it.
- `judgment-r04-r05/visual-defects.json`, listing every visual defect noted under "How to judge" step 7.
- A check that every cited frame's SHA-256 matches the file on disk.
- `judgment-r04-r05/SHA256SUMS.txt` over these files.
- The record: the method, the counts per row and verdict, and every VISUAL_FAIL and STILL_PENDING step with its
  reason and frames. Add the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must
  show `0 failed`.

## Rules

- Read the sealed rounds only. Never write inside rounds 01 to 05, and never rescore their files.
- Do not start any Minecraft client, server, Gradle task or other JVM. Never close, change or block a program you
  did not start.
- Your verdicts are proposals. Do not mark any step, row or Gate as passed anywhere.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
