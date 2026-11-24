# TASK W471: phase 2 round 1, single-player and loopback V1 rows on C6 with a real client

Root: Claude. Date: 2026-11-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w471`, branch `codex/v1.8.0-w471` from the shared branch |
| Decision | Root decision R686 (QUEUE row W471), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) section 5, phase 2 (its row list, environment and steps) and section 6 (evidence layout); the checklists [A](records/CAMPAIGN-CHECKLISTS-A.md), [B](records/CAMPAIGN-CHECKLISTS-B.md), [C](records/CAMPAIGN-CHECKLISTS-C.md), [D](records/CAMPAIGN-CHECKLISTS-D.md), [E](records/CAMPAIGN-CHECKLISTS-E.md), [F](records/CAMPAIGN-CHECKLISTS-F.md), [G](records/CAMPAIGN-CHECKLISTS-G.md), [H](records/CAMPAIGN-CHECKLISTS-H.md), [I](records/CAMPAIGN-CHECKLISTS-I.md) and [J](records/CAMPAIGN-CHECKLISTS-J.md) (each row's steps, fixture and package); `scripts/run_campaign_client_session.py` and [scripts/campaign_client/README.md](../../../scripts/campaign_client/README.md); [W427](records/W427.md) and [W430](records/W430.md) (the core-route world, its batches and the dry run) |
| Inputs (read only) | C6 `c8fa16e16be87554f26c402546733df6555d15e6` and its JAR `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar` (SHA-256 `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`; check it before each server start); the sealed fixtures the checklists name (use copies, never the sources) |
| Outputs | Tracked: the record `docs/work/v1.8.0-root-queue/records/W471.md`, and a harness or batch change with tests only if a row cannot run without one (outside `src/`, opt-in, existing behaviour unchanged). Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-01/` with each row's requests, results, screenshots, client and server logs, contact sheets and a `SHA256SUMS.txt` |
| Window | Client windows use this PC's desktop, so start them only between 23:30 and 08:00 (R604). At 07:45 finish the row in progress, stop every client and server cleanly, release the lock and write the record with the rows not yet run listed as `NOT_RUN`. |
| Lock | Each packaged server takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds between holds, no hold over two hours. Check that C: and D: each have at least 10 GiB free before each hold. Detached processes start with no console window (`pythonw` and `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/hidden_run.py`). |
| Not allowed | Any change under `src/` (frozen at C6, R600). Any owner account, launcher profile, account file or credential. Enabling any production admission switch. Changing a test, timeout, budget or expectation. Recording a row as PASS from anything but its own run on C6. |

## Root's decision for this round (R686)

Phase 2's V1 rows run in the harness's **launch mode with an isolated offline client** on this PC's real GPU, as
the plan's phase 2 environment says ("against the packaged server on loopback, as the harness does"). Checklist
A's run convention that isolated offline loopback is allowed only in phase 3 does not apply to phase 2's V1 rows:
V1 checks rendering on the real GPU, which the client's identity does not change. A row, or a step, whose expected
result depends on online authentication, premium skins, the whitelist or the owner's UUID is **NOT_COVERED** in
this round with the reason "needs the owner's account (attach mode)"; it runs later in attach mode. The gated
bay, cargo and fuel world (plan step P5) needs the owner's UUIDs, so its rows (the J rows on that world, including
J23 to J26) are NOT_COVERED here with that reason.

## What to do

1. **Row list.** Take phase 2's V1 rows from the plan (A12 to A14, A16, B06, C06, C07, D03, D05, E04 to E07, E11,
   F07, G06, H06, I06 to I09, J01, J06, J12 to J16, J19 to J21, J23 to J26, and the V1 half of the rows marked
   "DS, V1"). For each, read its checklist steps and mark each step `[H]` (the harness drives it) or `[P]` (a human
   hand or judgment). Write the list and the order before you start.
2. **Order.** Forge 47.4.10 without JEI first, GUI scale 3, windowed, English, in the plan's batch order: materials
   and machines, life support, rockets of three sizes, skies of every body, stations and warp, satellites and
   missions, endgame devices, the v1.8 machines and equipment, and the API fixture rows. Then, as time allows,
   the matrix smokes: GUI scales 2 and 4, fullscreen, Chinese, Forge 47.4.23, and JEI 15.56.0.205.
3. **Run.** For each row run every `[H]` step on a fresh copy of the row's fixture with the tracked harness,
   screenshotting each observation the checklist names, and record the client's OS, CPU, GPU, driver, resolution,
   GUI scale and JVM once per client start. A `[P]` judgment step gets its screenshots and is marked for Root's
   review; a `[P]` hand step that the harness cannot perform is NOT_COVERED with the reason.
4. **Results.** Give each row PASS, FAIL, NOT_COVERED or NOT_RUN with its evidence path. A FAIL names the exact step,
   the expected and actual result, the screenshot and the log line. Do not retry a failed step and keep only the
   retry; a harness failure is kept, named, fixed outside the JAR and the row rerun, as the plan's failure table
   says. Collect every client-log line that mentions a missing model, texture or translation.
5. **Contact sheets.** One sheet per row, grouped by row, for Root's review.
6. **Frame time.** A12 and I09's frame-time capture needs the plan's tool (PresentMon); if it is not installed, mark
   those measurements NOT_COVERED and still run the rows' other steps.
7. **Record** the row list, the per-row table, the findings, the commands, the client and server identities and the
   evidence hashes, and run `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- A product defect is a finding for Root, never a fix in this task.
- Commit on the branch plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
