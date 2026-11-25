# TASK W474: author full-row phase 2 scenarios in daytime, without starting the game

Root: Claude. Date: 2026-11-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w474`, branch `codex/v1.8.0-w474` from the shared branch |
| Decision | Root decision R690 (QUEUE row W474), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)); R686's launch-mode and NOT_COVERED rules still apply |
| Sources | [W471](records/W471.md) and W472's record (`docs/work/v1.8.0-root-queue/records/W472.md` on branch `codex/v1.8.0-w472`); round 02's evidence root `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-02/` (read only, above all `tools/`, `row-plan.json`, `step-coverage.json`, `findings.json` and each session's `requests/` and `results.jsonl`); [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) sections 5 and 6; checklists [A](records/CAMPAIGN-CHECKLISTS-A.md) to [J](records/CAMPAIGN-CHECKLISTS-J.md); `scripts/run_campaign_client_session.py` and [scripts/campaign_client/README.md](../../../scripts/campaign_client/README.md); C6 `c8fa16e16be87554f26c402546733df6555d15e6` source (read only) |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-03/tools/` and `round-03/step-map.json`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W474.md`, and a harness change with Linux-safe tests only if a step cannot be driven without one (outside `src/`, opt-in, existing behaviour unchanged) |
| Time | Deliver by 21:00 local time on 2026-11-25 with what is ready, so Root can review before tonight's run at 23:30. List rows not finished. |
| Not allowed | Starting any Minecraft client, server or other JVM (R604: client windows only 23:30 to 08:00; this task is daytime). Any change under `src/`. Any owner account or credential. Editing round 01 or round 02. Changing a test, timeout, budget, expectation or the harness's output-line bound. |

## Why this task exists (R690)

Rounds 01 and 02 ran 43 real-GPU sessions on C6 with clean final attempts, but no step was scored PASS. Each
session touched a few scenes of many rows instead of every step of one row, and each step was judged against
the whole row rather than its own oracle. From now on:

- **Per-step scoring.** Each checklist step is judged on its own action and oracle, with its own evidence.
  - A step is PASS when its oracle is met by evidence from its own run on C6, and FAIL when it is not.
  - Otherwise a step is NOT_COVERED with a fixed reason, NOT_RUN, or P_PENDING (a `[P]` step with its screenshots
    ready for Root).
  - A row is PASS only when every step is PASS. A row whose `[H]` steps are all PASS, with only `[P]` steps left,
    is H_COMPLETE_P_PENDING.
- **Depth first.** Tonight's run finishes one row's every `[H]` step before it starts the next row.
- **Author by day, run by night.** Scenarios are written and checked now without the game, so tonight's window is
  spent running them.

## What to do

1. **Copy the tools.** Copy round 02's operator tools to `round-03/tools/`, keeping its fixes: the fail-closed
   queue, the field-level readbacks, the K07 ground staging, label normalization and typed slots.
2. **Map every step.** For each of the 45 rows in round 02's `row-plan.json`, write one entry per original step in
   `round-03/step-map.json`.
   - **`[H]` steps** give the scenario request IDs that perform the action, and the check that decides the oracle.
     The check is either a machine check on command replies, small NBT fields, block states, counts or log lines,
     or a named native screenshot when the oracle is visual.
   - **`[P]` steps** name the screenshots or recordings Root will judge.
   - **Steps that cannot run in launch mode on this PC** get a fixed NOT_COVERED reason, decided now and not
     during the run:
     - the owner's account (attach mode, R686);
     - two real clients (V2);
     - the second GPU;
     - a human listener;
     - PresentMon absent;
     - a human hand the harness cannot drive (say which input).
   - Do not mark a step NOT_COVERED just because it is long or awkward. Drive it if the harness can.
3. **Write the scenarios.** One scenario per row, or per row and fixture when a row needs several worlds. Each
   covers every mappable `[H]` step of that row in checklist order on a fresh fixture copy, including restarts the
   steps ask for. Reuse round 02's working scenes where they already perform a step's exact action.
4. **Check without the game.**
   - A dry validator confirms that every step of every row is mapped, every request is well formed, every label is
     valid and unique, and every reply stays well under the output bound.
   - Unit-test each oracle check against round 02's recorded replies where a matching reply exists, plus synthetic
     failing replies.
   - Report which scenarios have never met a live server, so tonight's run expects first-run operator fixes.
5. **Run order for tonight.** Write `round-03/tools/run-order.json`.
   - Rows in the plan's batch order, starting with J13 and the rows round 02 never reached (J18, B02, F07, G06, H06,
     J16, A02, I01, I06 to I09, J19, J21, B06, D03, D04, D05, D06, D08), then the rows round 02 only touched (A13,
     C06, C04, E04, J14, A12, J20, A14, E01, E05, E06, E07, E11, J12), then J15's `[H]` steps and the matrix smokes
     A16, C07 and I04.
   - Give each row's expected session count and minutes.
6. **Record** the per-row mapping counts (`[H]` mapped, `[P]` named, fixed NOT_COVERED by reason), the dry-validator
   and unit-test results, the rows not finished, and the commands. Run `python -B scripts/validate_repository.py`;
   its `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- A product defect noticed while reading evidence is a finding for Root, never a fix in this task.
- Commit the record, plus any harness change, on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
