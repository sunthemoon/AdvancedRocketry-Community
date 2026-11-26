# TASK W485: check the display-cell coverage behind W482's visual passes

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w485`, branch `codex/v1.8.0-w485` from the shared branch |
| Decision | Root decision R704 (QUEUE row W485), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | [W482](records/W482.md), its outputs in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/judgment-r04-r05/`, the checklists `records/CAMPAIGN-CHECKLISTS-*.md`, and rounds 04 and 05, which are sealed and must not change |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/judgment-r04-r05-coverage/`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W485.md` |
| Python | `D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe -B` |

## Why

W482 proposed 66 VISUAL_PASS verdicts. For rows that repeat one screen across display cells (language, GUI scale,
windowed or fullscreen), it opened a sample of the frames. Its record lists the frames it saw neither natively nor
on a sheet page, and notes that A13, C06 and B06 have no fullscreen cell although their checklist rows list one.
Root decision R704 accepts a pass for a step that covers several cells only when every listed cell has a frame and
every distinct frame has been looked at. You did not take part in W482, so your check is independent of it.

## What to do

1. **List the cells.** For each of W482's VISUAL_PASS steps, read the step's text and its checklist row, and list
   the display cells it requires. If neither names cells, the step needs none. Steps E05 2, A14 1, B02 7 and C06 7
   are left out: Root has already set them to STILL_PENDING.
2. **Check the captures.** For each required cell, find whether the step has a frame in that cell. Use the
   frames' identity and debug-overlay lines, and the session's start identity in the round's `identity-index.json`.
   A required cell with no frame makes the step **STILL_PENDING**, with the missing cells named.
3. **Look at the rest.** For every step that still passes the cell check, look at each distinct frame of the step
   that W482 did not open natively and did not see on a sheet page. W482's `visual-judgments.json` lists what it
   opened.
   - Use the round's `sheets/<ROW>*.png` pages, and open the native frame whenever text, numbers or small details
     decide the outcome.
   - Pixel-identical regions may be grouped by a pixel hash of the region, as W482 did; say which.
   - A frame that contradicts the oracle makes the step **VISUAL_FAIL**, confirmed on the native frame.
   - Otherwise the step **stays VISUAL_PASS**.
4. **Note visual defects** on every frame you open, as W482 did: missing textures, raw keys, clipped or overlapping
   text, wrong models.

## Outputs

- `judgment-r04-r05-coverage/coverage.json`, with one entry per step:
  - the cells required and the cells captured;
  - the frames you looked at, each with its path and SHA-256, and how you looked at it;
  - your verdict and the reason.
- `judgment-r04-r05-coverage/visual-defects.json`.
- `judgment-r04-r05-coverage/SHA256SUMS.txt`.
- The record, with:
  - the counts;
  - every changed verdict with its reason and frames;
  - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Read the sealed rounds and W482's outputs only. Never write inside rounds 01 to 06 or `judgment-r04-r05/`.
- Do not start any Minecraft client, server, Gradle task or other JVM. Never close, change or block a program you
  did not start.
- Your verdicts are proposals. Do not mark any step, row or Gate as passed anywhere.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
