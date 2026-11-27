# TASK W503: author the three frame-time workloads W500 had to stop, without starting the game

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w503`, branch `codex/v1.8.0-w503` from the shared branch |
| Decision | Root decision R726 (QUEUE row W503), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W491](TASK-W491-PRESENTMON-AND-HULL-AUTHORING.md) and the tasks it names applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | [W491](records/W491.md) with `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w491/` and its `OPERATOR-RUNBOOK.md`; [W500](records/W500.md) with `authoring-w500/integration-blockers.json` and `authoring-w500/blocked-video-sessions.json`; the original step text of A12, I09 and their checklists under `records/`; the sealed round 06 for scenes and fixtures (copy, do not edit); the harness under `scripts/campaign_client/`; C6's source under `src/` (read only) |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w503/`, in W491's formats, with an inventory `delivery-inputs.json`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W503.md` |
| Time | Deliver by **21:30** local time |

## Why

W500 integrated W491's frame-time captures into round 08's package, and Root moved them to round 09 (R724). Six of
W500's eight stopped sessions need authoring, not a harness change (W501 handles the JEI-only baseline):
- **Flights (A12/4).** Three size-matched flight captures, at 64, 512 and 2,048 blocks, need ordinary launch and
  passenger actions by the session's own player. A rocket placed and owned by console commands, as K04 does, does
  not supply them.
- **All models (I09/2).** The combined workload, the centrifuge with every formed machine model in view at once, is
  missing. K14's partial core scene does not supply it.
- **JEI clips (I09/6 and I09/7).** W491 put every per-input clip in one fixed-window session, but the harness allows
  at most four video spans per session (`native_video.Spans`), and that limit stays.

## What to do

1. **Flights.** For each size, author a scene in which the session's player builds or is given a rocket of that
   size, boards it as a passenger and launches it through the rocket's own screen, with the actions and checks the
   harness already supports. Read C6's source to show that the player owns the launch. Keep W491's capture span
   around the flight.
2. **All models.** Author one scene that places the centrifuge and every formed machine model C6 renders, within one
   view, from the existing fixtures, and keep W491's capture span. List each model and the fixture it comes from.
3. **JEI clips.** Split each of I09/6 and I09/7 into sessions of at most four clips each, keeping every input, its
   clip and its fixed window. First check the original step text: if it requires all inputs in one session, stop
   that part and quote the text.
4. **Check.** Run W491's dry validator over the new rows and the operator tests, and write the inventory. Each new
   session must validate against the unchanged harness limits.
5. **Record:**
   - each scene and its source citations;
   - the session split;
   - for each of W500's six stopped sessions, whether it is now authored and what remains;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts or limits (AGENTS.md section 5). Tracked tests must be
  Linux-safe.
- Do not start any Minecraft client, server, Gradle task, PresentMon or other JVM. Make no change under `src/`
  (frozen at C6, R600) or `scripts/campaign_client/`.
- Never write inside rounds 01 to 07, `authoring-w491/`, `authoring-w496/`, `authoring-w498/`, `authoring-w500/`,
  `authoring-w501/` or the judgment packages, and do not touch the worktrees `D:/GitHub/arce-v180-w489` and
  `D:/GitHub/arce-v180-w500`.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
