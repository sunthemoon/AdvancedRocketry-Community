# TASK W501: let baseline launches match their candidate worlds and add a JEI-only baseline

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w501`, branch `codex/v1.8.0-w501` from the shared branch |
| Decision | Root decision R724 (QUEUE rows W500 and W501), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | [W498](records/W498.md), [W499](records/W499.md), [W491](records/W491.md) and [W500](records/W500.md); the harness under `scripts/campaign_client/`, above all `baseline.py`, `baseline_world.py` and `baseline_launch.py`, its README and tests; W491's comparison tool in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w491/` and W500's blockers `authoring-w500/comparison-blockers.json` and `authoring-w500/integration-blockers.json` (all read only) |
| Outputs | Commits on the branch: the harness change, its tests and README; outside Git, the JEI-only profile in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w501/` with an inventory; the record `docs/work/v1.8.0-root-queue/records/W501.md` |
| Time | Deliver by **21:30** local time |

## Why

W500 prepared round 08 and found that W491's frame-time comparisons cannot be paired (R724):
- **Seeds.** A baseline launch always creates a flat world with seed 0. The seven candidate worlds the captures use
  have the seeds -6512649996917166507, -6263571203436868762 and 20261006. W491's comparison requires the same world
  seed, viewpoint and settings on both sides, and that requirement stays.
- **JEI.** Steps A12/2 and I09/1 compare against an empty Forge client with JEI only. The baseline launch kind admits
  only Vanilla 1.20.1 and empty Forge 47.4.10.

## What to do

1. **Matching worlds.**
   - Let a baseline session name the candidate world it is paired with. The new world then takes that world's seed,
     its overworld generator settings, spawn, time, game rules and the viewpoint inputs W491's comparison keys use,
     read from the candidate world's `level.dat` without changing it.
   - Pin the candidate world by its SHA-256 in the session.
   - Refuse a pairing whose overworld generator is not a vanilla generator, and anything you cannot copy exactly.
     Name each refusal.
   - The new world stays fresh, disposable and vanilla-only, with no modded dimension, data pack or block. A session
     that names no candidate world keeps today's flat seed-0 world exactly.
2. **JEI-only baseline.**
   - Add a third baseline profile: Forge 47.4.10 whose mods folder holds exactly the JEI JAR the campaign client uses,
     pinned by SHA-256, and nothing else.
   - Build it from files already on this machine into `authoring-w501/`, as W498 built the empty Forge profile.
     Download nothing, copy no account or launcher login, and change no installed folder.
   - Record each file's SHA-256.
3. **Test it.**
   - Write pure tests of the pairing, the refusals and the JEI profile check, runnable on Linux.
   - Keep all 194 existing harness tests passing unchanged.
   - Update the README.
4. **Record:**
   - what the pairing copies and what it refuses;
   - for each of W500's eight stopped sessions and its comparison block, whether this change unblocks it, and what
     else is missing if not;
   - the JEI profile and its hashes;
   - the changed files and the commands;
   - what a first live launch must confirm;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not change or delete an existing test, or loosen an existing check (AGENTS.md section 5). Do not change W491's
  comparison key or any candidate world.
- Do not start Minecraft, Gradle, PresentMon or any other JVM. A live launch happens later, at night.
- Never write inside rounds 01 to 07, `authoring-w491/`, `authoring-w496/`, `authoring-w498/`, `authoring-w500/` or
  the judgment packages, and do not touch the worktrees `D:/GitHub/arce-v180-w489` and `D:/GitHub/arce-v180-w500`.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
