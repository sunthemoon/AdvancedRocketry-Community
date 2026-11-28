# TASK W507: fix the JEI and Observatory findings of round 07 on the fix branch

Root: Claude. Date: 2026-11-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w507`, branch `codex/v1.8.0-w507` that Root created from `codex/v1.8.0-w504` at `76576ccd4`, which holds W493's and W504's fixes |
| Branch note | This branch predates this task and the records W492 and W505. Read them at their absolute paths under `D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-root-queue/`. In your record, link only to files that exist on this branch, and name this task and those records as code spans |
| Decision | Root decision R732 (QUEUE rows W505 and W507), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | `records/W505.md` (Points for Root and Visual defects), `records/W492.md`, the round-07 frames named below (read only), [W487](records/W487.md) for the form of a fix proposal, and the code under `src/` |
| Outputs | One commit per fix on the branch, each with its tests; the record `docs/work/v1.8.0-root-queue/records/W507.md` |
| Time | Deliver by **19:00** local time. If you run Gradle, hold the heavy-JVM lock as WORKER-RULES says and release it by **21:00**, because tonight's client run takes the machine from 23:30 |

## Why

`src/` stays frozen at C6 for the campaign (R600). Fixes for product defects found in phase 2 are prepared on
unmerged branches, so that the candidate decision after phase 2's first full pass rests on tested changes (R716).
Round 07 ran I04 and J19 on the real GPU for the first time, and W505 found four defects that no fix covers yet.
W487's P05 (`NOT_BOUND` in the elevator tooltip) and P10 (the Chinese research wording) are already on this branch;
do not touch them.

## What to fix

1. **JEI Centrifuge category height.** At 1280 by 720 and GUI scale 3 the Centrifuge recipe box is taller than JEI's
   recipe window: it runs off the bottom of the screen and covers the bookmark buttons. Frame:
   `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-07/i04-r6-s1-w492op1/evidence/screenshots/i04-r6-s1-w492op1-s03-w486-category-centrifuging-13-centrifuging-input-cycle-0.png`.
   Lay the category out so that it fits JEI's window at that size with every input and output visible, and test its
   size.
2. **JEI Precision Assembler category title.** At the same size the title is cut to 'Precision Assembler Con...';
   the other ten titles fit. Frame:
   `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-07/i04-r6-s3-w492op1/evidence/screenshots/i04-r6-s3-w492op1-s03-w486-category-precision-assembling-13-precision-a-56982ed0.png`.
   Give it a title that fits in English and Chinese. A text generated before v1.8 changes only through a new v1.8
   key, as W487 explains.
3. **JEI fluid slots.** The Chemical Reactor's and the Centrifuge's fluid slots draw a 1,000 mB amount as a line two
   or three pixels high, because the slot's capacity is 16,000 mB, while the Electrolyzer and the Rolling Machine
   draw water as a full square. Make every category draw a recipe's fluid by one rule, with a test.
4. **Observatory when not formed.** With 'Multiblock not formed' and a missing cell shown, the 'Start survey' and
   'Cancel survey' buttons are drawn as active, and the energy line reads 0 FE where the formed screen read 10000 FE
   moments before. Frame:
   `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-07/j19-r6-s0-r1-w492op1/evidence/screenshots/j19-r6-s0-r1-w492op1-s01-w486-missing-observatory-east-12-actual-missin-7e5577cd.png`.
   First read C6: what the server sends in the menu's data when the structure is not formed, whether the server
   refuses those two actions then, and whether the stored energy really becomes 0.
   - Draw the two buttons inactive when the server's data says the structure is not formed. The server still
     decides.
   - If the screen shows a value other than the server's, fix the screen.
   - If the server itself drops stored energy when the structure breaks, do not change service logic. Report it
     with source lines for Root.

## Boundaries

- No save schema, network format, permission, service logic or ADR change. If a fix needs one, stop that fix and
  report why.
- English and Chinese stay in step, and the generated language files match their providers.
- Do not touch the fixes already on the branch, other than to keep their tests passing.

## Tests and checks

- A unit test for each fix that fails without it.
- Under the heavy-JVM lock: `gradlew.bat --offline clean build`, then `runData` followed by `git diff --exit-code`,
  then `runGameTestServer`. Report each result, and release the lock by 21:00.
- `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Record

- each fix with its commit, the files it touches and its test;
- what C6 does for the Observatory's data and energy when the structure is not formed, with source lines;
- the frames a later real-GPU round must retake: I04 steps 3 and 4 in both JEI cells, and J19 step 1 in English and
  Chinese;
- the commands and their results.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Do not start a Minecraft client. Never close, change or block a program you did not start.
- Never write inside `D:/GitHub/ARCE-Task-Evidence/final-campaign/`.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
