# TASK W472: phase 2 round 2, rerun W471's failed rows and continue the V1 rows on C6

Root: Claude. Date: 2026-11-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w472`, branch `codex/v1.8.0-w472` from the shared branch |
| Decision | Root decisions R687 (W471 triage) and R688 (this round), QUEUE rows W471 and W472, under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)); R686's launch-mode decision (TASK [W471](TASK-W471-PHASE2-V1-ROUND1.md)) still applies |
| Sources | [W471](records/W471.md) and its evidence root `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-01/` (read only: `ROW-ORDER.md`, `row-plan.json`, `row-plan-correction.json`, `step-coverage.json`, `row-results.json`, `FINDINGS.md`, `tools/`); [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) sections 5 and 6; checklists [A](records/CAMPAIGN-CHECKLISTS-A.md) to [J](records/CAMPAIGN-CHECKLISTS-J.md); `scripts/run_campaign_client_session.py` and [scripts/campaign_client/README.md](../../../scripts/campaign_client/README.md) |
| Inputs (read only) | C6 `c8fa16e16be87554f26c402546733df6555d15e6` and its JAR `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar` (SHA-256 `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`; check it before each server start); the sealed fixtures the checklists name (use fresh copies, never the sources) |
| Outputs | Tracked: the record `docs/work/v1.8.0-root-queue/records/W472.md`, and a harness change with Linux-safe tests only if a row cannot run without one (outside `src/`, opt-in, existing behaviour unchanged). Outside Git: a new evidence root `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-02/` with each row's requests, results, screenshots, client and server logs, contact sheets and a `SHA256SUMS.txt` |
| Window | Client windows only until 08:00 (R604). At 07:45 finish the request in progress, stop every client and server cleanly, release the lock and write the record with the rows not yet run listed as `NOT_RUN`. |
| Lock | Each packaged server takes the heavy-JVM lock, one server per hold, at least 90 seconds between holds, no hold over two hours. Check that C: and D: each have at least 10 GiB free before each hold. Detached processes start with no console window (`pythonw` and `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/hidden_run.py`). |
| Not allowed | Any change under `src/` (frozen at C6, R600). Any owner account, launcher profile, account file or credential. Enabling any production admission switch. Changing a test, timeout, budget, expectation or the harness's 65,536-character output-line bound. Editing any file under `round-01/`. Recording a row as PASS from anything but its own run on C6. |

## Root's triage of W471 (R687)

- W471's two FAIL rows are operator and staging failures, not product defects. Both failed attempts stay in
  round 01 as they are; the rows rerun here on fresh copies.
  - **E04 (F-W471-02):** the operator borrowed K16's safe-return point `(4.5,150,4.5)` for a K07 world whose
    surface is near y=-63, so the server kicked the airborne player for floating, and the operator went on
    after the failed suit setup.
  - **A12-2048 (F-W471-03):** operation 3 asked for the whole rocket entity's NBT in one console reply, which
    is longer than the harness's bounded output line. The bound is correct and stays.
- **The missing lock (F-W471-04):** Root removed the stale lock at 00:15 on 2026-11-25 after checking that its
  owner, pid 8112, had exited and no Java process was running. The record is
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W471/stale-lock-20261125-0015.txt`. W471's queue then started A12-multi
  without noticing the earlier failure; that is the operator queue's defect, fixed below.
- A12-multi's observations stay disclosed in round 01 and are not used for A12.

## What to do

1. **Operator tools.** Copy W471's external operator tools into `round-02/tools/` and fix them there:
   - The queue stops (fails closed) when a session's summary status is not `COMPLETE`, when it has any cleanup
     error or `machine_lock_retained`, or when a request fails. It does not start another row after that. Show
     this with a dry check that feeds it a failed summary without starting Java.
   - The A12 readback queries individual small NBT fields or counts, each well under the output bound, never a
     whole entity or block entity. Check every readback reply on the 64-block rocket before the 2048-block one.
   - E04 stages the player on the K07 world's own safe ground, which is read from the fixture rather than taken
     from another fixture, and stops the row after a failed suit setup.
   - Screenshot labels stay lowercase and unique.

   If a row truly needs a tracked harness change, make it opt-in with Linux-safe tests, and leave existing
   behaviour unchanged.
2. **Rerun.** On fresh copies, rerun E04 at its four sites and A12 at 64, 512 and 2048 blocks.
3. **Continue.**
   - Run the rest in W471's row order with J15 added: the open `[H]` steps of A13, C06 and J14, then C04, J20,
     A14, E01, E05, E06 (this GPU only; the second GPU stays open), E07, E11, J12, J13, J18, B02, F07, G06, H06,
     J16, A02, I01, I06 to I09, J19, J21, B06, D03, D04, D05, D06 and D08.
   - Then, as time allows, the matrix smokes A16, C07 and I04: GUI scales 2 and 4, fullscreen, Chinese, Forge
     47.4.23 and JEI 15.56.0.205.
   - J15's listening steps are NOT_COVERED with the reason "needs a human listener". Its `[H]` steps still run.
   - J01, J06 and J23 to J26 stay NOT_COVERED under R686.
4. **Results.** Give each row PASS, FAIL, NOT_COVERED or NOT_RUN with its evidence path. A row whose `[H]` steps all
   pass but which has `[P]` judgment steps is `H_COMPLETE_P_PENDING`, waiting for Root's review, never PASS.
   - A FAIL names the exact step, the expected and actual result, the screenshot and the log line.
   - Keep every failed attempt.
   - A harness or staging failure is named, fixed outside the JAR, and the row is rerun on a fresh copy. A
     product defect is a finding for Root and is not rerun.
   - Collect every client-log line that mentions a missing model, texture or translation.
5. **Contact sheets.** One sheet per row, grouped by row, listing each `[P]` step beside its native screenshots
   for Root's review.
6. **Frame time.** PresentMon is not installed. Mark A12's and I09's frame-time measurements NOT_COVERED and run
   their other steps.
7. **Cleanup.** After each completed session, remove its server and client copies once their logs are in the
   evidence. Do not touch round 01.
8. **Record.**
   - Write the row list, the per-row table, the findings, the commands, the client and server identities and
     the evidence hashes.
   - Run `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- A product defect is a finding for Root, never a fix in this task.
- Commit the record, plus any harness change, on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
