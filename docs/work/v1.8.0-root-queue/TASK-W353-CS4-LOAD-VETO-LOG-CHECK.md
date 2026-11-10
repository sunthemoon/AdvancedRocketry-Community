# TASK W353: the cargo settlement runner's check of the load veto's injected ERROR

Root: Claude. Date: 2026-11-11. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w353`, branch `codex/v1.8.0-w353`, created by Root from the shared head |
| Decision | Root decision R425 (QUEUE row W353) |
| Evidence to read | `D:/GitHub/ARCE-Task-Evidence/root-queue/C2/campaign/cargo-C-S4-LOAD-attempt1/` (outside Git): `runner.log`, `native/results.json`, and the files `native/FS_LOAD_VETO_DESTINATION_SAVED-*` |
| Record | `docs/work/v1.8.0-root-queue/records/W353.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W353/` |

## Why

Candidate C2's campaign family `cargo-C-S4-LOAD` passed its first 18 cases and then failed
`FS_LOAD_VETO_DESTINATION_SAVED` with "start 0: Native run logged ERROR or FATAL". `FS_LOAD_BIND_VETO` did not run,
and the driver then skipped `cargo-C-S5`, which uses the same runner.

The ERROR is the fault the case injects on purpose. For the load direction, W330's fixture
(`EscrowCargoSettlementCuts.held`, the VETO branch) calls `GuardedChunkSaves.recordObservedDenial` with the reason
`Release-test load settlement source veto`, and the denied chunk save is logged by the event bus as
`[Server thread/ERROR] [ne.mi.ev.EventBus/EVENTBUS]: Exception caught during firing event: Release-test load settlement source veto`.
The unload direction injects its veto with an entity whose save returns false, which logs no ERROR, so
`scripts/run_v180_cargo_s4_s5_cuts.py` (function `observations`) refuses every ERROR line. W330 added the load
cases but did not run them natively (its record lists only `FS_LOAD_O3_AFTER` and `IS_LOAD_O3_AFTER` from these
families). The S6 chain runner already handles the same kind of injected ERROR exactly: see `check_veto_error` and
`_VETO_REASON` in `scripts/loader_s6_chain.py`.

## What to do

1. **Accept exactly the injected ERROR, nothing else.** In the cargo settlement runner, accept the load veto's
   ERROR only for the load veto cases, only at the start where the fixture installs the veto, only with the exact
   reason `Release-test load settlement source veto`, and only in the event-bus and throwable form the denial
   produces (follow `check_veto_error`: the header, the listener list and the stack frames that belong to it). Any
   other ERROR or FATAL line, the same text in another case or at another start, or a different count, still fails.
   The unload cases keep refusing every ERROR. Do not change the Java fixture, any oracle's expected state or any
   timeout.
2. **Check `FS_LOAD_BIND_VETO`** and every other case W330 added to the cargo runners (the fluid and item load
   settlement cuts and the owner-cap cases) for an injected fault that logs ERROR. Read the Java fixture for each,
   list them, and handle each one that logs ERROR the same exact way.
3. **Unit tests**, in the style of `tests/test_loader_s6_chain.py`: the exact injected ERROR passes for its case and
   start; a changed reason, an extra ERROR, the text in an unload case or at a restart, and a FATAL each fail.

## Verification

- The changed runner's unit tests and the cargo runner tests:
  `python -B -m unittest discover -s tests -p "test_*cargo*"`.
- **Replay from C2's evidence, no JVM:** run the runner's checks for `FS_LOAD_VETO_DESTINATION_SAVED` over the three
  logs and archives C2 retained, and report every check's result. If the runner has no replay entry point, add a
  small one in your write scope or write a scratch script outside the repository that imports the runner's
  functions; do not copy the checks.
- **Native:** the whole `C-S4-LOAD` family (all 20 cases, forced stop and both restarts) with
  `scripts/run_v180_cargo_integrated.py <template server> --family C-S4-LOAD`, using the C2 JAR
  `D:/GitHub/ARCE-Task-Evidence/root-queue/C2/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`
  (SHA-256 `1eea72ac442ea181806940c1288698abd7840fbfa0bce2b17f415550bff8ca57`). The runner checks the worktree's
  production sources against the candidate commit, and your branch is not at `ade1538c3`, so run it from a
  detached scratch worktree at `ade1538c3` with your runner files copied in, or build a JAR from your own commit;
  say which you did. The template server and the other arguments are in the C2 family's `invocation.json`. All 20
  cases must pass.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- **The heavy-JVM lock.** Root's C2 campaign driver is running and takes the lock once per family, with a cooldown
  between families; it waits when the lock is held. W352 also takes it for its own native run. Take the lock the
  normal way for each heavy step (backslash lock path, as WORKER-RULES says) and release it after each step. Never
  remove, rename or edit a lock you do not own. No full GameTest or full build while the campaign runs.
- Check C: and D: for 10 GB free before each heavy step. Delete server copies and scratch worktrees after the run;
  keep logs, results and manifests.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Java code, including the veto fixture; the unload cases' checks; the C2 worktree and its evidence; W352's fixture
fix; the status file, QUEUE and the risk register.
