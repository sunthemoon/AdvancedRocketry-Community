# TASK W393: the safe exit's saved rider is grounded on its pad, with either grounded vertical motion

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w393`, branch `codex/v1.8.0-w393` from the shared branch |
| Decision | Root decision R524 (QUEUE row W393) |
| Source | [W373](records/W373.md), the `f058c8bcf` row of "Native-proven corrections, tests and commits" |
| Record | `docs/work/v1.8.0-root-queue/records/W393.md` |
| Write scope | `scripts/run_v180_bay_exit_cuts.py` (the function `check_saved_rider` only), `tests/test_run_v180_bay_exit_cuts.py`, the record. Nothing else. |

## Why

The exit family's flushed cases check the rider's saved state. W262 required a negative vertical motion for every
flushed case, reading it as "falling under vanilla gravity". For the safe exit `X8_SAFE_MENU` that reading was wrong:
in C2, C3 and the 2026-11-09 integrated run the saved rider stood on its pad at exactly (8.5, 2.0, 8.5) with
`motion_y` -0.0784000015258789, the value vanilla leaves on a grounded entity after one physics tick. In W373's
attempts 5 and 7 the rider stood at the same coordinates with `motion_y` 0.0, because the save came before that
tick. W373's fix (`f058c8bcf`) now requires exactly 0.0, which would fail a run whose save comes one tick later.

Root's decision: for a SAFE flushed case, require the rider to be grounded on its checked pad, and accept exactly
these two vertical motions: 0.0 and -0.0784000015258789. Keep everything else in W373's check: the exact pad
coordinates, `saved["y"]` equal to the cut's y, not riding, and at the cut `mounted=false`, `slot=SAFE`,
`offer=none`, `history=closed`. Falling cases keep `motion_y < 0` unchanged.

## What to do

1. Change only the SAFE branch of `check_saved_rider` as above. Name the grounded value as a constant with a
   comment that says where it comes from.
2. Tests: the SAFE check accepts both exact values at the pad; it refuses -0.0784 at another x, y or z, a falling
   value such as -0.1552 at the pad, a positive value, a rider still riding, and each wrong cut field. Keep every
   existing test unchanged.
3. Replay offline: load the saved X8 rider and cut state from these results files and run them through the new
   check; all four must pass:
   - `D:/GitHub/ARCE-Task-Evidence/root-queue/C2/campaign/exit-attempt1/native/results.json`
   - `D:/GitHub/ARCE-Task-Evidence/root-queue/C3/campaign/exit-attempt1/native/results.json`
   - `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/bay/exit-native4/native/results.json`
   - `D:/GitHub/ARCE-Task-Evidence/root-queue/W373/attempt7/exit/exit-attempt7/native/results.json`
   Read them only; do not change them. No native run, no Gradle, no GameTest.
4. Run `python -B -m unittest` on the exit runner's test module (with `PYTHONPATH` set to the worktree's `tests` and
   `scripts` folders) and `python -B scripts/validate_repository.py` on your record (it must end with `0 failed`).
5. Write `records/W393.md`: the change, the tests, the replay results and the commands. The last line is its path.

## Rules

- Do not delete tests, loosen any other assertion or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one or two commits in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
