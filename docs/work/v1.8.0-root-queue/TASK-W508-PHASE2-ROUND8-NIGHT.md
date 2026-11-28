# TASK W508: phase 2 round 8, the night of 2026-11-28, from W506's package

Root: Claude. Date: 2026-11-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w500`, on the new branch `codex/v1.8.0-w508` that Root created there from W506's commit `e82381bb2`. Round 08's tools are bound to this path; do not rebind them |
| Branch note | This branch predates this task. Read it at its absolute path `D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-root-queue/TASK-W508-PHASE2-ROUND8-NIGHT.md`. In your record, link only to files that exist on this branch, and name this task as a code span |
| Decision | Root decisions R724, R731 and R733 (QUEUE rows W500, W492, W506 and W508), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W488](TASK-W488-PHASE2-ROUND6-NIGHT1.md) applies, except where this task changes it |
| Sources | [W506](records/W506.md), [W500](records/W500.md) and round 08 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/authoring-w500/round-08/`, identified by `w506/delivery-inputs.json`; read `w506/OPERATOR-RUNBOOK.md`, `w506/ATTRIBUTION.md` and W500's `OPERATOR-RUNBOOK.md` before starting |
| Outputs | Outside Git: the night's evidence in `round-08/`, with your notes in `round-08/w508/`, sealed at the end with `round-08/SHA256SUMS.txt`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W508.md` |
| Window | Rows start from 23:30 on 2026-11-28 and not after 07:30 on 2026-11-29. The input deadline is 07:43; stop everything by 07:45 |

## What is different from W492

- **The package is W506's round 08.** It runs in place. Root checked every file against W506's inventory and reran
  the checks below from this worktree at about 09:00. Change nothing before the first row.
- **The order (R724, R731).** A new block of about 173 minutes comes first: the baseline smoke, D03, the six
  fullscreen cells of B06, C06 and A13, and the five hulls. Round 07's carry-over follows: E06's Space and profile
  sessions, D06, C06's non-fullscreen sessions, I06, C07, E05 from session 3 and I08 last. W506 estimates 978.3
  minutes against about 480. Rows not reached by 07:30 stay NOT_RUN for a later night. Do not reorder, skip or
  shorten rows.
- **The baseline smoke.** Its result is for Root's review in the morning. No frame-time capture runs tonight, so a
  failed or partial smoke does not stop the later rows: record it, then continue from D03 as W506's runbook says.
  Never create `baseline-qualification.json`; only Root writes it.
- **Fullscreen.** The first native fullscreen cell is the live confirmation that R717 asks for. If a cell meets the
  pointer gate's refusal of a window covering the desktop, record it as the blocked instrument F-W488-25, leave
  that cell NOT_RUN and continue. Do not work around the gate.
- **Operator corrections are capped (R713).** One fresh whole-row rerun per distinct operator fault, each with its
  own test, and at most two operator corrections per logical row tonight. The queue segments `C06-new` and
  `C06-carry` are one logical row and share that cap. After a third operator fault, leave the row NOT_RUN with that
  reason and continue.
- **W506's corrections are not yet verified live.** Read `w506/ATTRIBUTION.md` before you decide whether a failure
  is an operator fault or a product finding. A failure of an authored correction is not by itself a new operator
  fault.

## What to do

1. **Before Java:**
   - Run `python -B <round-08>/r733/verify_inputs.py`. It must report PASS. If it fails, stop and report: do not
     run the night on inputs Root has not reviewed.
   - Check the JAR hash and the fixture seals with the runbook's preflight, free space above 10 GB on C and D, that
     no Java runs and that `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` is absent.
   - From this worktree, run:
     - the dry validator: exit 2, with structural PASS and 129 H and 29 P mapped;
     - the 308 operator tests;
     - the three dry checks, each exiting 0;
     - the harness tests `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q` (194 tests).

     Root got exactly these results. A different result is a stop-and-report before Java.
2. **Run the night** with the runbook's entry command and a new tag. Its own guards wait for 23:30.
3. **When the runner stops before 07:30,** apply TASK W488 step 3 with R713's cap above.
4. **Score each step on its own** (R690). Label captured visual-oracle steps P_PENDING, keep partial inputs NOT_RUN
   as `PARTIAL_INPUT_ONLY`, and do not judge frames yourself.
5. **Collect and record** everything TASK W488 step 5 lists, with each row's minutes against W506's estimate.
6. **After 07:45,** clean up as the runbook says and seal round 08 with `SHA256SUMS.txt`, covering W500's and
   W506's inputs under `round-08/`, Root's `r733/` files, your `w508/` notes and the night's evidence.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- No change under `src/` (frozen at C6, R600) or `scripts/campaign_client/`. No owner account, launcher profile or
  credential. No production admission switch. Never close, change or block a program you did not start.
- Do not edit rounds 01 to 07, `authoring-w491/`, `authoring-w496/`, `authoring-w498/`, `authoring-w501/`,
  `authoring-w503/`, the judgment packages, or anything under `authoring-w500/` outside `round-08/`.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
