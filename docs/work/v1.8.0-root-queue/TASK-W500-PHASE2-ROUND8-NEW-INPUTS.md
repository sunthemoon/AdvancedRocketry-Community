# TASK W500: prepare round 08's new inputs while round 07 runs, without starting the game

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w500`, branch `codex/v1.8.0-w500` from the shared branch. Round 08 will later run from this worktree |
| Decision | Root decision R723 (QUEUE row W500), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W489](TASK-W489-PHASE2-ROUND7-PREPARATION.md) and the tasks it names applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | Round 07's prepared inputs in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``round-07/` with W489's inventory `round-07/w489/delivery-inputs.json` (read only); [W491](records/W491.md) with `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w491/` and its `OPERATOR-RUNBOOK.md`; [W496](records/W496.md) with `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w496/`; [W498](records/W498.md) with `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w498/`; [W494](records/W494.md); QUEUE rows W491 to W499 (R715 to R722); the harness under `scripts/campaign_client/` and its README |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w500/`, with an inventory `delivery-inputs.json`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W500.md` |
| Time | Deliver by **21:30** local time. Copy round 07's files first: round 07 runs from 23:30 tonight |

## Why

Round 07 runs tonight. Round 08 runs the next night and adds inputs that are now ready:
- W491's frame-time captures and five-hull scenes;
- W496's D03 fixture and the empty Vanilla 1.20.1 profile;
- W498's baseline launch kind, now merged into the harness (R722), and its empty Forge 47.4.10 profile;
- the fullscreen cells of B06, C06 and A13, left out of round 07 because the pointer gate was blocked
  (F-W488-25), which W494's merged gate now handles.

Root decision R723 prepares these now, so that once round 07 is sealed only its carry-over rows remain to add.

## What to do, in this order

1. **Copy, then verify.** Copy round 07's `tools/`, `step-map.json` and every file the tools read, including the
   sibling paths they resolve, into `authoring-w500/round-08/`. Before you change anything, check every copied file
   against `round-07/w489/delivery-inputs.json` and save the result. Round 07's dry-check, dry-validation and
   input-preflight outputs may differ; name each one that does. Never write inside round 07.
2. **Rebind and set the window.** Bind the tools to this worktree. Set the window to 2026-11-28 23:30 to
   2026-11-29 07:30, with the 07:43 input deadline and the 07:45 stop. Every time guard must agree:
   - `night_round06.py`;
   - `w471.py`, both `start_permitted` and the inline loop in `run`;
   - `run_rows.py` and `queue.py`.

   Keep the test that checks every guard against the same window, updated for this window.
3. **Integrate the new inputs.**
   - **Baseline launches.** Bind the empty Vanilla 1.20.1 profile from `authoring-w496/` and the empty Forge
     47.4.10 profile from `authoring-w498/` to the harness's baseline launch kind, by the hashes those packages
     record. Candidate sessions keep their current form.
   - **First baseline launch.** Make the first new row a short smoke launch of each baseline that confirms the
     list in W498's record of what a first live launch must confirm. If that row fails, the rows that need a
     baseline must not start.
   - **Frame-time captures.** Add W491's supplemental rows from `authoring-w491/additions/` as its runbook
     describes, with PresentMon 2.6.0 at `D:/Tools/PresentMon`. Each collector span starts and stops around its
     row's actions and has a time limit.
   - **D03.** Replace D03's scene for steps 1 and 2 with W496's, with its data pack, and keep step 5's healthy
     variant without the pack. Steps 3 and 4 stay NOT_COVERED.
   - **Fullscreen cells.** Add the fullscreen cells of B06, C06 and A13 with W494's gate, as round 06 authored
     them.
   - **Five hulls.** Add W491's five-hull scenes.
4. **Leave room for the carry-over.** Round 07's unreached rows and the rows its review sends back are added after
   it is sealed. Make the tools read them from one input file, `carry-over.json`, empty for now, and test that a
   row placed there runs in the order Root sets.
5. **Order and estimate.** Set the new rows' order with each row's estimate, using round 06's and round 07's
   timings, and show it with the runner's dry check without Java. Say how much of the window the new rows leave
   for the carry-over.
6. **Keep it runnable.** From this worktree, run and record:
   - the dry validator;
   - the operator tests;
   - the three dry checks;
   - the harness tests `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q`.
7. **Inventory.** Write `authoring-w500/delivery-inputs.json` with every file, its size and SHA-256, and the copy
   check from step 1.
8. **Record:**
   - every changed file against round 07;
   - the new rows with their bindings;
   - the order with its estimate;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Do not start any Minecraft client, server, Gradle task, PresentMon or other JVM. Make no change under `src/`
  (frozen at C6, R600) or `scripts/campaign_client/`. If the harness lacks something round 08 needs, stop that part
  and say what is missing.
- Never write inside rounds 01 to 07, `authoring-w491/`, `authoring-w496/`, `authoring-w498/` or the judgment
  packages, and do not touch the worktree `D:/GitHub/arce-v180-w489`, which runs tonight.
- Do not change the owner's launcher, accounts or installed game folders.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
