# TASK W486: continue the round-06 authoring until 21:30, without starting the game

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w486`, branch `codex/v1.8.0-w486` from the shared branch |
| Decision | Root decision R706 (QUEUE row W486), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W481](TASK-W481-PHASE2-ROUND6-AUTHORING.md), [TASK W476](TASK-W476-PHASE2-SCENARIOS-CONTINUED.md) and [TASK W474](TASK-W474-PHASE2-FULL-ROW-SCENARIOS.md) applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Start from | [W481](records/W481.md) and its tools in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-06/tools/`. Do not copy them again. Rebind them to this worktree as W481 did |
| Also read | [W482](records/W482.md), its verdicts in `phase2/judgment-r04-r05/visual-judgments.json`, and QUEUE rows W482 and W485 (Root decision R704) |
| Outputs | The same round-06 tools, updated in place, and the record `docs/work/v1.8.0-root-queue/records/W486.md` |
| Time | **Work until 21:30 local time**, then deliver. Tonight's run starts at 23:30 from these files |

## Why this task exists

W481 delivered at 08:31, after 43 minutes, with 11 new mappings and 186 steps still open. Nine rows still have no
runnable input. The window for this work is the whole day.

Deliver before 21:30 only if every open step is either authored or explained as needing one of the fixed external
causes:
- the owner's account;
- V2;
- the second GPU;
- a human listener;
- PresentMon absent;
- a named human-hand input.

An early delivery for any other reason fails this task.

## Root decision R706: C07's Precision batch

Checklist C (line 50) says to confirm a recipe is effective on C6 before using its numbers. C6's
`src/generated/v1.8/resources/data/advancedrocketrycommunity/recipes/precision_control_circuit.json` takes two iron
ingots and two control boards. The two redstone in C07 step 4 come from the older v1.2 recipe. Author C07's
Precision batch with C6's inputs. Keep every other number unchanged: 20 ticks at 40 FE/t (800 FE), giving one
advanced circuit and two redstone torches. Cite this decision in the request.

## What to do, in this order

1. **The nine rows with no runnable input:** E04, J15, A12, J20, E07, E11, I01, J21 and A16. Map their `[H]` steps
   the harness can drive, using W481's shared staging step for every scene away from the spawn area.
2. **Recaptures for the STILL_PENDING visual steps.** These are W482's 23, plus E05 2, A14 1, B02 7 and C06 7 from
   R704. For each one, author a capture that shows what its oracle names. W482's reason says what was missing.
   - **Aim at the body:** put the orbit body, the black hole or the sun in view for J12, J13, B02 and F07.
   - **Wait out the toasts:** wait until the toasts after a join or reconnect are gone before capturing the HUD
     (D05, D04, D08), and keep the chat lines off the message being captured (D03).
   - **Show the inside:** capture the interior or the screen the oracle names for D06 steps 2 to 4.
   - **Load the condition:** load the K13 long-text pack for J16 step 7, and capture the missing-cell diagnostic
     for J19.
   - **Place it in loaded ground:** put I07 step 12's pad grid in loaded ground with W481's staging, then capture
     it.
   - **Select from another node:** click a node that is not already selected for E05 step 2.
   - **Catch a particle:** take a frame while a particle is on screen for A14 step 1.
   - **Show both stations:** show the Moon station's context for B02 step 7.
   - **Add the fullscreen cells:** capture the fullscreen cell that C06, A13 and B06 lack.
3. **The other open steps,** in `run-order.json` order. Prefer the steps that complete a row's `[H]` set.
4. **Tonight's order.** Set the round-06 night order, with each row's estimate against 23:30 to 07:30:
   1. the rows newly runnable from step 1;
   2. the recaptures from step 2;
   3. J18, I06, I09, C07 and I04;
   4. E06 sessions 2 to 5;
   5. H06's newly mapped step;
   6. I08 last, because its 296 estimated minutes would take most of the night.

   Show the order with the runner's dry check without Java.
5. **Keep it runnable.** After each row, run the dry validator and the operator tests.
6. **Record:**
   - the per-row counts against W481's;
   - the recaptures, step by step;
   - the night order and its estimate;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- Do not start any Minecraft client, server, Gradle task or other JVM. No change under `src/` (frozen at C6, R600).
- Do not edit rounds 01 to 05 or `judgment-r04-r05/`. Do not edit `scripts/campaign_client/`: W484 is changing it.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
