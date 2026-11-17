# TASK W370: X01 and X02 simulation, slice S2 (the replay builder and the X01 runner)

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w370`, branch `codex/v1.8.0-w370`, at the shared head |
| Decision | Root decision R463 (QUEUE row W370) |
| Design | [W368-DESIGN](records/W368-DESIGN.md), sections 1 to 3, 5, 7.1 row S2 and 7.3; Root's answers in QUEUE row W368 (R461) |
| Record | `docs/work/v1.8.0-root-queue/records/W370.md` |
| Write scope | New `scripts/v180_crash_replay.py`, `scripts/run_v180_crash_replay.py`, `tests/test_v180_crash_replay.py`, small additive changes to the runners the design names (B, F, S, I and R) so they can run only their restart check on a given world, their tests, and the record. No Java, no change to any existing oracle or case selection. |

## What to do

1. **The replay builder.** Implement `v180_crash_replay.py` as a pure function of a recorded trace (`ops.jsonl`,
   `blobs/`, `pre/`) and a kill image:
   - the in-flight states of section 2.3: S-OLD, S-EMPTY, S-PREFIX, S-TORN, S-STAGED and S-NEW;
   - the late-loss states of section 2.4, with at most seven per case;
   - `states.json`, written before any server starts, with each state's derivation and the SHA-256 of every seam
     file.

   Enforce the bounds of section 2.5: at most 16 states per case, or `OVER_BOUND`.
2. **The X01 runner.** Implement `run_v180_crash_replay.py`. For each case of section 3, run the existing runner with
   the trace property, kill at the cut as today, then build, start twice, check and delete each copy in turn on D:.
   Use the existing oracles unchanged. The additive runner changes only allow "run the restart check on this world".
3. **Tests.** Unit tests on synthetic traces for every state kind, the bounds, `OVER_BOUND`, a damaged trace refused,
   and the state table's crash-point mapping. Add tests for the runner changes. Every existing runner test keeps
   passing unchanged.
4. **No native run yet.** Slice S1 (W369, the Java recorder) is in progress. Write the code against the trace format of
   section 2.2. Root runs the X01 campaign on the candidate after S1 merges.

## Rules

- Do not change an existing oracle's expectation or case selection, and do not delete tests or loosen assertions
  (AGENTS.md section 5).
- Run the Python tests you touched and the full suites of the runners you changed.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives what was done, the commits, the commands and results, and anything not done. Its last line, and the
  last line of your answer, is the path of `records/W370.md`.
