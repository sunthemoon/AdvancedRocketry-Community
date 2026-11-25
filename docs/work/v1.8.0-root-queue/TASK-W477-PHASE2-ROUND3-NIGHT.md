# TASK W477: phase 2 round 3, run the authored full-row scenarios on C6 tonight

Root: Claude. Date: 2026-11-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w477`, branch `codex/v1.8.0-w477` from the shared branch |
| Decision | Root decision R696 (QUEUE row W477), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)); R686's launch-mode and NOT_COVERED rules and R690's per-step scoring apply |
| Sources | [W474](records/W474.md) and [W476](records/W476.md); the round-03 tools in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-03/tools/` with `step-map.json` and `run-order.json`; [TASK W472](TASK-W472-PHASE2-V1-ROUND2.md) for the identity, lock and cleanup rules, which still apply; checklists [A](records/CAMPAIGN-CHECKLISTS-A.md) to [J](records/CAMPAIGN-CHECKLISTS-J.md) |
| Inputs (read only) | C6 `c8fa16e16be87554f26c402546733df6555d15e6` and its JAR `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar` (SHA-256 `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`; check it before each server start); the sealed fixtures, used only as fresh copies |
| Outputs | Outside Git: round-03's evidence beside its tools, under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-03/`, with each session's requests, results, native screenshots, client and server logs, a per-step result file, contact sheets for Root and a `SHA256SUMS.txt`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W477.md`, and a harness change with Linux-safe tests only if a step cannot run without one (outside `src/`, opt-in, existing behaviour unchanged) |
| Window | Start no client before 23:30. At 07:45 finish the request in progress, stop every client and server cleanly, release the lock and write the record. Client windows close by 08:00 (R604). |
| Not allowed | Any change under `src/` (frozen at C6, R600). Any owner account, launcher profile, account file or credential. Enabling any production admission switch. Changing a test, timeout, budget, expectation, check or the harness's output-line bound to make a step pass. Editing round 01 or round 02. |

## What to do

1. **Before 23:30**, without starting Java:
   - check the JAR hash, the fixture seals, free space (at least 10 GiB on C: and D:) and that no Java runs;
   - run the dry validator and the operator tests on the tools exactly as W476 left them;
   - copy the tools' hash inventory into the evidence.
2. **From 23:30**, run the night runner. It runs rows depth first in `run-order.json` order: first the rows whose
   `[H]` steps are all mapped, then the mapped steps of the partly mapped rows. One server per heavy-JVM lock hold,
   at least 90 seconds between holds, and detached processes with no console window.
   W476 estimates 773 minutes for the night subsets against a 495-minute window, so the order decides what is
   reached. Keep the runner's 07:30 last-start guard and 07:43 input deadline, and leave every step the cutoff
   does not reach NOT_RUN.
3. **Score each step on its own** (R690):
   - **PASS** when its own oracle is met by its own evidence from this run on C6.
   - **FAIL** when it is not; name the expected and actual result, the native screenshot and the log line.
   - **P_PENDING** for a `[P]` step, or an `[H]` step with a visual oracle, whose native screenshots are captured for
     Root's judgment.
   - **NOT_COVERED** with W474's fixed reasons.
   - **NOT_RUN** for steps not reached.

   A row is PASS only when all its steps are PASS. It is H_COMPLETE_P_PENDING when all its `[H]` steps are PASS and
   only `[P]` steps remain.
4. **First live runs.** These scenarios have never met a live server.
   - When a failure is in the operator rather than the product (a wrong coordinate, label, slot type, timing step or
     staging), keep the failed attempt, name it, fix the scenario, and rerun the row on a fresh copy.
   - A product defect is a finding for Root. Do not rerun it to get a pass.
   - Do not change a check's expected value to match what the game did unless the checklist's own oracle shows the
     check was written wrong; record each such correction with the checklist line.
5. **Stops.** The fail-closed queue stops on a failed summary, a cleanup error, a retained lock or a failed request.
   Find the cause:
   - if it is in the operator, fix it and continue with the next row on a fresh copy;
   - if a lock is left by a process that has exited and no Java runs, record it and remove it;
   - otherwise stop and record why.
6. **Collect** every client-log line that mentions a missing model, texture or translation. Make one contact sheet per
   row that puts each P_PENDING step beside its native screenshots.
7. **Record:**
   - the per-row and per-step results, with counts by result and evidence paths;
   - every failed attempt and its fix;
   - the findings;
   - the client and server identities;
   - the evidence hashes;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- A product defect is a finding for Root, never a fix in this task.
- Commit the record, plus any harness change, on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
