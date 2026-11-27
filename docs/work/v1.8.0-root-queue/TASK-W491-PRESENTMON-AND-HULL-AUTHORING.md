# TASK W491: author the PresentMon captures and the five hull scenes, without starting the game

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w491`, branch `codex/v1.8.0-w491` from the shared branch |
| Decision | Root decisions R711 and R713 (QUEUE row W491), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W486](TASK-W486-PHASE2-ROUND6-AUTHORING-CONTINUED.md) and the tasks it names applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | [W488](records/W488.md), [W487](records/W487.md) (finding F-W487-01), the checklists `records/CAMPAIGN-CHECKLISTS-*.md`, round 06's tools under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``round-06/tools/` (sealed: copy, do not edit), and PresentMon 2.6.0 at `D:/Tools/PresentMon/PresentMon-2.6.0-x64.exe` |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w491/`, row files, requests, scripts and tests in round 06's formats, which Root will add to a later round. Tracked: the record `docs/work/v1.8.0-root-queue/records/W491.md` |
| Time | Deliver by **21:00** local time |

## Why

- **PresentMon.** Twelve of the 84 NOT_COVERED steps were NOT_COVERED only because PresentMon was absent. Since
  R711 it is installed. It is the console build, and it needs an elevated process. Root's worker sessions run
  elevated.
- **Hulls.** F-W487-01 found that all six formed hulls mismatch their redesigned patterns. Phase 2 has frames only of
  the precision assembler's hull (C04, I07). The candidate decision also needs frames of the other five formed
  hulls:
  - the railgun;
  - the orbital laser drill;
  - the space elevator anchor;
  - the black hole generator;
  - the rolling machine.

## What to do, in this order

1. **The twelve PresentMon steps.**
   - Find them in `round-06/w488/per-step-results.json` and the checklists.
   - For each one, author a capture that:
     - starts PresentMon for the client process only, with a fixed duration;
     - writes its CSV into the session's evidence;
     - stops it in the same session;
     - reads the CSV with `scripts/campaign_client/frametimes.py` (W399) against the step's budget in
       `docs/17-V1PLUS-QUALITY-BUDGETS.md`.
   - Start it hidden, with no console window, and with no owner account.
   - Test the command lines and the CSV reading with a sample CSV that you write by hand. Do not run PresentMon.
2. **The five hulls.** For each, author a scene that:
   - builds the formed structure in loaded ground with W481's staging;
   - captures F2 frames from its four sides and from above.

   Cite each structure's C6 source definition and the redesigned pattern from W487 that the frames will be
   compared with.
3. **Estimate** each new row's minutes as W486 did.
4. **Check:** run the dry validator on the new rows and the operator tests. Write
   `authoring-w491/delivery-inputs.json` with every file, its size and SHA-256.
5. **Record:**
   - each step's capture;
   - the scenes;
   - the estimates;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- Do not start any Minecraft client, server, Gradle task, PresentMon or other JVM. Make no change under `src/`
  (frozen at C6, R600) or `scripts/campaign_client/`.
- Never write inside rounds 01 to 07 or the judgment packages. Round 07 belongs to W489 today.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
