# TASK W506: prepare round 08 for tonight and attribute round 07's open failures, without starting the game

Root: Claude. Date: 2026-11-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w500`, on the new branch `codex/v1.8.0-w506` that Root created there from W500's commit `ded729b2a`, so that the tracked harness stays the one W500 verified. Round 08's tools are bound to this path; do not rebind them |
| Branch note | This branch predates this task and W492's record. Read them at their absolute paths under `D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-root-queue/`. In your record, link only to files that exist on this branch, and name this task and W492's record as code spans |
| Decision | Root decision R731 (QUEUE rows W492 and W506), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in TASK W489 applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | `records/W492.md` and the sealed round 07 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-07/`, above all `w492/FINDINGS.md`, `w492/revised-inputs.json`, `w492/per-step-results.json`, `w492/row-timings.json` and `w492/attempt-results.json`; [W500](records/W500.md) and its package under `phase2/authoring-w500/`, with `OPERATOR-RUNBOOK.md`; QUEUE rows W500 (R723, R724) and W492 (R731); C6's source under `src/` (read only) |
| Outputs | Outside Git: round 08 in place under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/authoring-w500/round-08/`, with your notes in `round-08/w506/`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W506.md` |
| Time | Deliver by **18:00** local time. Tonight's run starts at 23:30 from your files |

## Why

Round 07 ran on the night of 2026-11-27 and is sealed. W500 prepared round 08's new inputs before that night, and
R724 decided that round 08 runs a short new block, then round 07's carry-over. Round 07's operator corrections live
only in round 07's copies, and four failures have no known cause. This task carries R731's decisions into round 08.

## What to do, in this order

1. **Verify first.** Before you change anything:
   - check every file of `authoring-w500/` against W500's inventory `delivery-inputs.json` (SHA-256 `3b205a25...`,
     in QUEUE row W500) and list any difference;
   - check `round-07/SHA256SUMS.txt` (SHA-256 `26613b8533369de1e752d10ec86dbfa0d125a5a1282a19358b63b544ab8528ca`)
     and every round-07 member you copy from.

   Save both results. Never write inside rounds 01 to 07.
2. **Trim the new block (R724).** Round 08's new block is the baseline smoke, D03, the six fullscreen cells of B06,
   C06 and A13 and the five hulls, about 173 minutes. Leave the A12, E11, I09, E06 and H06 collector rows out of
   round 08's order; they move to the frame-time rounds 09 and 10. Keep their files.
3. **Port round 07's corrections.** For each reviewed input that W492 changed for an operator correction
   (`w492/revised-inputs.json`), check that round 08's copy still has the old hash, then apply the same change and
   its regression test. Merge by hand where W500 changed the same file. The corrections are:
   - A13's terminal reopened after the F11 round trip (F-W492-02);
   - C06's refusal extractor limited to the aggregate summary (F-W492-03);
   - I06's gallery staging order (F-W492-07);
   - the nested JEI exits of C07 and I04 (F-W492-10, F-W492-12);
   - H06's preparation of closed distant revisits (F-W492-14);
   - J19's approach to rotated controllers (F-W492-15);
   - I08's loader and monitoring-station placement (F-W492-20, F-W492-21).

   Apply each one to every round-08 session with the same sequence, including the fullscreen cells of A13 and C06.
   Do not port round 07's fresh labels or aliases; round 08 allocates its own. D06's eye anchor depends on step 4.
4. **Attribute the open failures.** For each, read C6's source (read only) and round 07's evidence, and write the
   attribution in `w506/ATTRIBUTION.md` with source lines and evidence paths. An operator fault gets a fix in round
   08's inputs, with a test. A product finding leaves the request unchanged and the row out of round 08.
   - **C07 (F-W492-11).** Find why `data get block 19 150 20 arce_rolling_port.fluid.Amount` found no element.
     Name what puts 1000 mB of water in that port in the scene, the NBT layout C6 writes for it, and whether an
     empty tank omits `Amount`.
   - **D06 session 4 (F-W492-16, F-W492-18).** Find why the console did not open with both supports in place: the
     aim, the reach, the block at the target and what C6 requires before the console opens. Say whether the eye
     anchor is right. If it is not, restore the original commands and their exact assertions.
   - **E06's Space session (F-W492-13).** Name the C6 class and condition that returned the creative observer from
     Space to the Overworld, and what checklist E06 asks of the Space view. If a place C6 allows, such as a
     station, gives that view, author it with a test. Do not switch to spectator or bypass the safety service.
   - **E05 session 3 (F-W492-19).** Find C6's countdown length and when a cancel is still accepted, and compare them
     with round 07's timings: the COUNTDOWN receipt on its second attempt and the cancel about 3.34 seconds after
     the launch click. Operator timing gets a re-authored sequence that cancels within the countdown, without
     shortening any wait.
   - **I08 (F-W492-22).** Apply H06's preparation of closed distant revisits to I08, with a test. Check every other
     carry-over row for the same pattern.

   These fixes are authored inputs. Tonight's cap of two operator corrections per row (R713) starts fresh.
5. **Carry-over (R731).** Fill `round-08/carry-over.json` in this order, each entry with its sessions, its estimate
   and its restart parent. Where round 07 removed a parent export, the row reruns whole.
   1. E06's Space session, if step 4 authors a valid view, and its profile session;
   2. D06, whole row, only if step 4 finds and fixes an operator cause;
   3. C06's non-fullscreen sessions, whole row;
   4. I06, whole row;
   5. C07, whole row, only if step 4 fixes the water baseline;
   6. E05 from session 3;
   7. I08, whole row, last.

   A row left out says why.
6. **Order.** Show the night's order with each row's estimate against 23:30 to 07:30 with the runner's dry check,
   without Java. Rows not reached by 07:30 stay NOT_RUN; do not reorder, skip or shorten rows to fit.
7. **Keep it runnable.** From this worktree, run and record:
   - the dry validator;
   - the operator tests;
   - the three dry checks;
   - the harness tests `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q`.
8. **Inventory.** Write `w506/delivery-inputs.json` with every file of `authoring-w500/`, its size and SHA-256, the
   checks of step 1 and every changed file against W500's inventory.
9. **Record:**
   - the attributions;
   - every ported correction and changed file;
   - the order with its estimate;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Do not start any Minecraft client, server, Gradle task, PresentMon or other JVM. Make no change under `src/`
  (frozen at C6, R600) or `scripts/campaign_client/`.
- Never write inside rounds 01 to 07, `authoring-w491/`, `authoring-w496/`, `authoring-w498/`, `authoring-w501/`,
  `authoring-w503/` or the judgment packages.
- Never close, change or block a program you did not start.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
