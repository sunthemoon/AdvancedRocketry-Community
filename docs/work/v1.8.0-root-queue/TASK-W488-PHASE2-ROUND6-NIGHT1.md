# TASK W488: phase 2 round 6, the first night of W486's queue on C6

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w488`, branch `codex/v1.8.0-w488` from the shared branch, which contains W484's harness change |
| Decision | Root decision R710 (QUEUE row W488), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W480](TASK-W480-PHASE2-ROUND5-NIGHT.md), [TASK W479](TASK-W479-PHASE2-ROUND4-NIGHT.md) and [TASK W477](TASK-W477-PHASE2-ROUND3-NIGHT.md) applies, except where this task changes it |
| Sources | [W486](records/W486.md) and its round-06 package under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-06/`, identified by `w486/delivery-inputs.json`; read `w486/OPERATOR-RUNBOOK.md` and `w486/NIGHT-PLAN.md` before starting |
| Outputs | Outside Git: the night's evidence in `round-06/` beside W486's inputs, with your own notes in `round-06/w488/`, sealed at the end with `round-06/SHA256SUMS.txt`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W488.md` |
| Window | The night entry's own gate: rows start from 23:30 and not after 07:30; the input deadline is 07:43; stop everything by 07:45. No Java before 23:30 |

## What is different from W480

- **No copy.** Round 06 runs in place from W486's package. That package is not sealed; its delivery inventory
  identifies the bytes Root reviewed.
- **The harness binding moves to this worktree** (R710), so the tools use W484's harness, in which a rejected command
  fails a `require` attempt and never satisfies a wait.
- **The queue is longer than the night.** W486 estimates 1,763.8 minutes (2,267.9 with slow guards) against 480.
  Round 06 runs over about four nights in R706's order, each continuing from where the last stopped. Do not reorder,
  skip or shorten rows to fit more in. Rows not reached by 07:30 stay NOT_RUN with that reason.

## What to do

1. **Before Java.** You may start these checks before 23:30.
   - Run `python -B <round-06>/w486/delivery_inventory.py --verify`. It must report PASS with 11,746 files and
     62,903,154 bytes. If it does not, stop and report: do not run the night on inputs Root has not reviewed.
   - Rebind exactly six files by replacing `D:/GitHub/arce-v180-w486` with `D:/GitHub/arce-v180-w488`:
     `tools/author.py`, `tools/dry_validate.py`, `tools/history_census.py`, `tools/stopped_mission.py`,
     `tools/w471.py` and `w486/check.py`. Change nothing else in them. Leave `tools/round02/w471.py`, a historical
     copy bound to W472, as it is.
   - Keep `w486/delivery-inputs.json` unchanged. Write `w488/revised-inputs.json` with every delivered file whose
     bytes now differ, with its old and new SHA-256 and the reason. Only the six rebound files and files that the
     runbook's own checks write may differ.
   - Check the JAR hash and the fixture seals with `w486/preflight.py`, free space above 10 GB on C and D, that no
     Java runs and that `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` is absent.
   - From this worktree, run the runbook's read-only checks: the dry validator (exit 2 with structural PASS, 129 H
     and 29 P mapped), the 235 operator tests, the three dry checks (each exit 0) and the harness tests
     `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q` (134 tests).
   - Root ran the same rebinding on a scratch copy bound to the shared head at 21:30 to 21:40 and got exactly
     those results. A different result is a stop-and-report before Java.
2. **Run the night** from this worktree at 23:30:
   `python -B <round-06>/tools/night_round06.py --execute-night --tag initial`. It checks the cursor clip before each
   row, keeps R706's order, runs only E06's sessions 2 to 5 and runs I08 last.
3. **When the runner stops before 07:30,** read why, then continue with `--from-row` and a new tag. Use `--only-row`
   with a new tag for a rerun. Never reuse a tag, label or request file.
   - **A first-connection timeout (KI-V180-CONNECT-01):** keep the attempt, record it with its log lines and native
     frame, and rerun that row once on a fresh copy. If the rerun also times out, leave the row NOT_RUN with that
     reason and continue. Stop the night only after three timeouts in a row, counted across rows.
   - **An operator fault:** fix it in the round-06 tools only, with a test, record the change and its hashes, and
     rerun the row once on a fresh copy.
   - **A product defect:** record it as a finding for Root with its evidence. Do not rerun it; continue with the next
     row.
   - **A cleanup error or a retained lock:** resolve it before the next row. Never clear a lock or stop a process that
     you did not start.
4. **Score each step on its own** (R690). Label captured visual-oracle steps P_PENDING, keep partial inputs NOT_RUN
   as `PARTIAL_INPUT_ONLY`, and do not judge frames yourself. J13 step 1 needs the Moon's sky in view, not an Earth
   disc (R710).
5. **Collect and record** everything TASK W477 lists, and also:
   - each timeout and clip wait;
   - the rows reached and not reached;
   - the time the last row started;
   - each row's actual minutes against W486's estimate, so that Root can plan the next nights.
6. **After 07:45:**
   - clean up as the runbook says, keeping the exports that later D08 and E01 checks still need;
   - seal round 06 with `SHA256SUMS.txt`, covering W486's inputs, your `w488/` notes and the night's evidence.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- No change under `src/` (frozen at C6, R600) or `scripts/campaign_client/`. No owner account, launcher profile or
  credential. No production admission switch. Never close, change or block a program you did not start.
- Do not edit rounds 01 to 05, `judgment-r04-r05/` or `judgment-r04-r05-coverage/`.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
