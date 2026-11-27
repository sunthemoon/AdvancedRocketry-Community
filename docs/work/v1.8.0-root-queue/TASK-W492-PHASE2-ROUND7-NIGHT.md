# TASK W492: phase 2 round 7, the night of 2026-11-27, from W489's package

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w489`, on the new branch `codex/v1.8.0-w492` that Root created there from the shared branch. Round 07's tools are bound to this path; do not rebind them |
| Decision | Root decisions R713 and R715 (QUEUE rows W488, W489 and W492), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W488](TASK-W488-PHASE2-ROUND6-NIGHT1.md) applies, except where this task changes it |
| Sources | [W489](records/W489.md) and round 07 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-07/`, identified by `w489/delivery-inputs.json`; read `w489/OPERATOR-RUNBOOK.md`, `w489/NIGHT-PLAN.md` and `w489/ATTRIBUTION.md` before starting |
| Outputs | Outside Git: the night's evidence in `round-07/`, with your notes in `round-07/w492/`, sealed at the end with `round-07/SHA256SUMS.txt`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W492.md` |
| Window | Rows start from 23:30 on 2026-11-27 and not after 07:30 on 2026-11-28. The input deadline is 07:43; stop everything by 07:45 |

## What is different from W488

- **The package is W489's round 07.** It runs in place. Root checked every file against W489's inventory and reran
  the checks below from this worktree at about 08:45. Change nothing before the first row.
- **Operator corrections are capped (R713).** One fresh whole-row rerun per distinct operator fault, each with its
  own test, and at most two operator corrections per row tonight. After a third operator fault, leave the row
  NOT_RUN with that reason and continue.
- **Blocked instrument.** The fullscreen cells of B06, C06 and A13 are already left out (F-W488-25). If another cell,
  such as one of E05's, meets the pointer gate's refusal of a window covering the desktop, record it as the same
  blocked instrument, leave that cell NOT_RUN and continue. Do not work around the gate.
- **The queue is longer than the night.** W489 estimates 1,201.2 minutes against about 480. Rows not reached by
  07:30 stay NOT_RUN for a later night. Do not reorder, skip or shorten rows.

## What to do

1. **Before Java:**
   - Run `python -B <round-07>/r715/verify_inputs.py`. It must report PASS. If it fails, stop and report: do not run
     the night on inputs Root has not reviewed.
   - Check the JAR hash and the fixture seals with the runbook's preflight, free space above 10 GB on C and D, that
     no Java runs and that `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` is absent.
   - From this worktree, run:
     - the dry validator: exit 2, with structural PASS and 129 H and 29 P mapped;
     - the 263 operator tests;
     - the three dry checks, each exiting 0;
     - the harness tests `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q` (134 tests).

     Root got exactly these results. A different result is a stop-and-report before Java.
2. **Run the night** with the runbook's entry command and a new tag. Its own guards wait for 23:30.
3. **When the runner stops before 07:30,** apply TASK W488 step 3 with R713's cap above.
4. **Score each step on its own** (R690). Label captured visual-oracle steps P_PENDING, keep partial inputs NOT_RUN
   as `PARTIAL_INPUT_ONLY`, and do not judge frames yourself.
5. **Collect and record** everything TASK W488 step 5 lists.
6. **After 07:45,** clean up as the runbook says and seal round 07 with `SHA256SUMS.txt`, covering W489's inputs,
   Root's `r715/` files, your `w492/` notes and the night's evidence.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- No change under `src/` (frozen at C6, R600) or `scripts/campaign_client/`. No owner account, launcher profile or
  credential. No production admission switch. Never close, change or block a program you did not start.
- Do not edit rounds 01 to 06, `authoring-w491/` or the judgment packages.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
