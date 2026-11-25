# TASK W481: phase 2 round 6, shared staging and the open steps, by day without the game

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w481`, branch `codex/v1.8.0-w481` from the shared branch |
| Decision | Root decision R702 (QUEUE row W481), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W476](TASK-W476-PHASE2-SCENARIOS-CONTINUED.md) and [TASK W474](TASK-W474-PHASE2-FULL-ROW-SCENARIOS.md) applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | [W480](records/W480.md) and [W479](records/W479.md), and rounds 04 and 05 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/`, which are sealed and must not change |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-06/`, with its own `tools/` copy. Tracked: the record `docs/work/v1.8.0-root-queue/records/W481.md` |
| Time | Stop at **22:30 local time** and deliver. Tonight's run starts at 23:30 from these files, so leave them consistent, with the dry validator and the tests passing for every session marked ready |

## Why

Rounds 04 and 05 completed 23 row queues. Most failed first attempts had one cause: a scenario set blocks or placed
its observer before the target area had loaded, and the server kicked the observer for floating. W480 corrected
G06, H06, A02, I07, I08, D03, A13 and E01 one by one. 197 steps are still not authored, and 14 rows did not run
in rounds 04 and 05: E04, J15, A12, J20, E07, E11, J18, I01, I06, I09, J21, A16, C07 and I04.

## What to do

1. **Copy and check.** Copy `round-05/tools/` to `round-06/tools/`, check every copied file against round 05's
   `SHA256SUMS.txt`, and bind the copy to this worktree as W480 did. This carries W480's corrections (D08's READY
   precondition, E05's probe initialization, I08's top-down anchor approach, D04's approach and the second 07:30
   check after the fairness wait).
2. **One shared staging step.** Fold W480's repeated staging correction into one preparation that every scenario
   uses before its original setup when it sets blocks or places an observer away from the spawn area:
   - a spectator or supported observer;
   - bounded travel to the target;
   - a bounded wait until the target chunk is loaded;
   - finite support outside the tested structure.

   Do not change any original step, oracle or `require` operation, and do not raise any timeout. Add external
   operator tests for the helper. Show with W480's `assert_original_oracles()` that all 394 original step checks
   and all 1,686 original `require` operations still match.
3. **Author the open steps** under TASK W476's steps 1 and 2:
   - first the `[H]` steps of those 14 rows;
   - then the open steps of rows that have run.

   Prefer the steps that complete a row's `[H]` set. Keep the fixed NOT_COVERED reasons, and add new ones only for
   the same causes: the owner's account, V2, the second GPU, a human listener, PresentMon absent, or a named
   human-hand input.
4. **Timeout signature.** Write a read-only log scan for every KI-V180-CONNECT-01 occurrence in rounds 01 to 05. For
   each one, record:
   - whether the server logged sending the Forge login messages (`Sending ticking packet info`);
   - whether the client logged receiving the first one (`Recieved login wrapper packet event ... index 0`);
   - the client's connect time against its resource-reload lines;
   - the server's `lost connection` time.

   Compare with every successful join in the same rounds. Root read three of W480's occurrences: in each, the
   server sent all 23 messages and the client received none until its 30-second read timeout. Confirm or refute
   this for all occurrences. Do not change any timeout or join gate.
5. **Night plan for round 06.**
   - Order the night: those 14 rows, then I08 and E06's sessions 2 to 5, then the newly mapped steps of rows that
     have run.
   - Give each row an estimated duration against the 23:30 to 07:30 window.
   - Keep W480's rules for connection timeouts, clip probes, reruns on fresh copies and the 07:30 and 07:43
     limits.
   - Show the order with the runner's dry check without Java.
6. **Keep it runnable.** After each row, run the dry validator and the operator tests. A row you could not finish
   by 22:30 stays partly mapped, with its open steps NOT_RUN and the reason.
7. **Record:**
   - the per-row counts against W480's;
   - the staging helper and its tests;
   - the timeout-signature table;
   - the night plan;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- Do not start any Minecraft client, server, Gradle task or other JVM. No change under `src/` (frozen at C6, R600).
- Do not edit rounds 01 to 05.
- Commit the record, plus any harness change TASK W474 allows, on the branch in the style of AGENTS.md section 6.
  Do not push.
- The last line of your answer is the path of the record.
