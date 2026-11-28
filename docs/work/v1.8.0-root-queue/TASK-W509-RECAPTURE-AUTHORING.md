# TASK W509: author the recaptures that round 07's judgment asks for, without starting the game

Root: Claude. Date: 2026-11-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w509`, branch `codex/v1.8.0-w509` from the shared branch |
| Decision | Root decision R733 (QUEUE row W509), following R732, under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in TASK W489 applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | [W505](records/W505.md) (Points for Root) and its packet `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``judgment-r07/`; [W492](records/W492.md) and the sealed round 07 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``round-07/`; [W506](records/W506.md) and round 08's tools under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w500/round-08/`, above all `w506/ATTRIBUTION.md` on E06; the checklists `records/CAMPAIGN-CHECKLISTS-*.md`; C6's source under `src/` (read only) |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w509/`, in round 08's formats, with an inventory `delivery-inputs.json`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W509.md` |
| Time | Deliver by **17:00** local time |

## Why

Round 07's frames left four steps open for want of a frame or a readback, not for a product defect (R732). Root adds
the recaptures to a later round, after round 08, which runs tonight from the package under `authoring-w500/`.

## What to author

Start from round 08's current inputs for each row, copied into `authoring-w509/` after checking them against
W506's inventory (`round-08/w506/delivery-inputs.json`, SHA-256 `bc4fd897...`). Keep every original action,
oracle, wait and timeout, and add only what each item needs, with a test.

1. **A14 step 1, Earth particles.** Round 07's frames over Earth show no particle, because the camera looks at the
   cabin and the lower end of the rocket is under the hotbar and the chat. Add frames of the same flight's lift-off
   on Earth, taken from beside or below the rocket so that its lower end and exhaust are in view, with the HUD's
   ASCENT state readable. Keep the existing frames.
2. **I04 steps 5 and 7, JEI after `/reload`.** After the reload, JEI's recipe view was closed, so no frame shows a
   category. In each JEI-present cell, add a recipe lookup and a usage lookup after the reload that show the final
   categories, so that a reader can see they are correct and not duplicated.
3. **J19 step 1, server values.** The oracle asks that status, progress, energy, job, owner and selection match the
   server's `ContainerData`. Add a server-side readback of those values beside each frame of the formed and the
   unformed controller, from C6's own block entity or menu data, read only. Name the source of each value.
4. **J18 step 8, Space instruments.** C6 returned the observer from Space to the Overworld during both Space requests,
   as in E06. Move J18's Space scenes into a committed station region in the way W506 did for E06, keeping creative
   observation and every instrument, border and clock action, with a native station-environment receipt.

## Check and record

- Run round 08's dry validator, operator tests and three dry checks against your copies where they apply, and the
  harness tests `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q`.
- Write `authoring-w509/delivery-inputs.json` with every file, its size and SHA-256, and the check of the copied
  round-08 inputs.
- The record lists each item with its sessions and estimate, the changed files against round 08, the commands, and
  the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- Do not start any Minecraft client, server, Gradle task, PresentMon or other JVM. Make no change under `src/`
  (frozen at C6, R600) or `scripts/campaign_client/`.
- Never write inside rounds 01 to 07, `authoring-w500/` (round 08 runs tonight from it), the other authoring
  folders or the judgment packages, and do not touch the worktree `D:/GitHub/arce-v180-w500`.
- Never close, change or block a program you did not start.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
