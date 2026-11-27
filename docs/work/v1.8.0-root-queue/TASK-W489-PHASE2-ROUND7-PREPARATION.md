# TASK W489: prepare round 07 and attribute round 06's open failures, without starting the game

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w489`, branch `codex/v1.8.0-w489` from the shared branch |
| Decision | Root decision R713 (QUEUE rows W488 and W489), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W486](TASK-W486-PHASE2-ROUND6-AUTHORING-CONTINUED.md) and the tasks it names applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | [W488](records/W488.md) and the sealed round 06 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``round-06/`, above all `w488/FINDINGS.md`, `w488/per-step-results.json`, `w488/revised-inputs.json` and `w488/row-timings.json` |
| Outputs | Outside Git: `phase2/round-07/`, with your notes in `round-07/w489/`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W489.md` |
| Time | Deliver by **21:30** local time. Tonight's run starts at 23:30 from your files |

## Why

Round 06 was sealed after its first night. Its unreached rows, and the rows whose failures R713 sends back, run
tonight in a new round 07. R713 decided how to treat each open failure; this task carries those decisions into
round 07's inputs.

## What to do, in this order

1. **Copy, then verify.** Copy round 06's `tools/`, `step-map.json` and every file the tools read, including the
   sibling paths they resolve, into `round-07/`. Before you change anything, check every copied file against
   `round-06/SHA256SUMS.txt` and save the result. Never write inside round 06.
2. **Rebind and set the window.** Bind the tools to this worktree, as W486 and R712 did. Set the window to
   2026-11-27 23:30 to 2026-11-28 07:30, with the 07:43 input deadline and the 07:45 stop. Every time guard must
   agree:
   - `night_round06.py`;
   - `w471.py`, both `start_permitted` and the inline loop in `run`;
   - `run_rows.py` and `queue.py`.

   Add one test that checks every guard against the same window.
3. **Attribute the open failures (R713).** For each failure, read C6's source under `src/` (read only) and the
   round-06 evidence. Write the attribution in `w489/ATTRIBUTION.md`, with its source lines and evidence paths.
   - **D06 session 3 (F-W488-18).** Find whether a block supported the player at (268.5, 101, 261.5) and at
     (268.5, 101, 257.5) in D06's restart world, or only the rocket entity did.
     - Unsupported means an operator fault: author a supported position for the console capture, with a test.
     - Supported only by the rocket means a product finding: leave the request unchanged.
   - **J15 (F-W488-07).** List every condition C6's oxygen vent needs before it lights: the energy side, the oxygen
     supply, the seal, the scan and the timing. Check whether J15's scene met each one.
     - A missing condition is an operator fault: fix the scene, with a test.
     - If every condition was met, it is a product finding.
   - **J20 session 8 (F-W488-11).** This is the documented limit KI-V180-USER-01. Re-author the slow-fall control
     so that each drop ends within the Moon's air allowance as C6 computes it (`GravityFlightBudget`). Show the
     numbers in a test.
   - **E05 (F-W488-19).** Replace the retained-plan readback after cancellation with R713's criterion: the cancel is
     accepted for the active plan's destination, and the rocket returns to FUELED with its fuel unchanged. Record
     the plan, the transfer and the selection after cancellation as observations, without asserting them. Keep
     every other check.
4. **Set tonight's order.** Set round 07's order with each row's estimate against 23:30 to 07:30, and show it with
   the runner's dry check without Java:
   1. D08 session 2, from round 06's retained export `restart-inputs/d08-r6-s1-op1/world`, copied into round 07;
   2. A14, C06, A13, J18, I06, I09, C07, I04, E06 sessions 2 to 5 and H06, in R706's order;
   3. J19, whose two round-06 attempts both timed out;
   4. the failures that step 3 attributes to an operator fault, then E05 and J20 session 8;
   5. I08 last.

   Leave out the fullscreen cells of B06, C06 and A13. Their instrument is blocked (F-W488-25, R713).
5. **Keep it runnable.** From this worktree, run and record:
   - the dry validator;
   - the operator tests;
   - the three dry checks;
   - the harness tests `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q`.
6. **Inventory.** Write `w489/delivery-inputs.json` with every file of round 07, its size and SHA-256, and the
   copy check from step 1.
7. **Record:**
   - the attributions;
   - every changed file against round 06;
   - the order with its estimate;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). R713's E05 change, which replaces
  an authoring error with the checklist's own criterion, is the only changed expectation.
- Do not start any Minecraft client, server, Gradle task or other JVM. Make no change under `src/` (frozen at C6,
  R600) or `scripts/campaign_client/`.
- Never write inside rounds 01 to 06 or the judgment packages.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
