# TASK W462: classify K02's five failed flight checkpoints on C6

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w462`, branch `codex/v1.8.0-w462` from the shared branch |
| Decision | Root decision R662 (QUEUE row W462), following [W447](records/W447.md) |
| Sources | [W447](records/W447.md), section "Flight results"; [W437](records/W437.md); the runners [run_v100_flight_forced_stop.py](../../../scripts/run_v100_flight_forced_stop.py) and `scripts/run_v060_flight_server_smoke.py`; C6's code under `src/main/` (read only), in particular `RocketTransferService`, `RocketOrdinaryLaunch`, `RocketTransferSavedData`, the release-test pause (`ARCE_RELEASE_TEST_FLIGHT_PAUSED`), the retirement wait (`ARCE_TRANSFER_RETIREMENT_WAIT`), the disassembly refusal `REGION_BUSY` and `SealedDisassemblyCoordinator` |
| Inputs (read only) | W447's flight evidence `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R631-W447/flight/` (the case folders under `cases/`, `observations.json` and `summary.json`); K02's sealed sources under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/` (fresh copies only) |
| Outputs | Tracked: the record `docs/work/v1.8.0-root-queue/records/W462.md`, and a runner change with tests only if step 2 needs one. Outside Git: diagnostic runs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/R662-W462/` |
| Lock | Server runs take the heavy-JVM lock with the WORKER-RULES loop, one JVM per hold, at least 90 seconds between holds, no hold over two hours; detached launchers start with no console window. Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to K02's sources or W447's evidence. Changing a runner's default behaviour or loosening any existing check. Raising the 30-second pause budget or the 45-second disassembly wait for a counted result. Counting a diagnostic observation as a pass: the five cases stay FAIL. |

## What to do

1. **Missing pauses** (DESTINATION_SPAWNED, PASSENGERS_TRANSFERRED and SOURCE_REMOVED). From C6's code, find where
   the release-test checkpoint pause is checked and whether C6's journalled transfer reaches that check at these
   three checkpoints. Classify each case as NOT_REACHABLE_ON_C6 (the phases run inside one step that the check
   cannot split, with file and line citations, as W437 did for the legacy disassembly rows), HARNESS (the runner
   asks for the wrong name or waits for the wrong line) or PRODUCT (the check should fire and does not).
2. **Cleanup after recovery** (DESCENT: `REGION_BUSY`; LANDED: pending, then
   `ARCE_TRANSFER_RETIREMENT_WAIT site=BEFORE_RELEASE reason=SLOT_UNLOADED`). From the logs and the code, find what
   holds the region and which slot is unloaded, and whether the disassembly would finish once that slot loads or
   after a later event. Then run DESCENT and LANDED once each on fresh copies with the unchanged checks. After the
   counted 45-second wait fails as before, keep the server running and observe for up to ten minutes; if the code
   points to an unloaded chunk, load it with a vanilla `forceload` command and observe again. Keeping the server
   running may need an opt-in runner flag, default off, with Linux-safe tests. These observations are diagnostic
   only.
3. **Player impact.** For each of the five cases give its class. For PRODUCT, say whether a player can meet it
   (for example a server stop during descent or right after landing, then a restart and a disassembly attempt),
   and propose one paragraph of known-issue text for players and a v1.9 follow-up, since `src/main` stays frozen
   at C6. For HARNESS, describe the runner fix; a rerun closes nothing unless Root queues it.
4. **Record** the classes with their evidence, the commands and hashes, and anything left open, and run
   `python -B scripts/validate_repository.py`; its `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
