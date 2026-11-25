# TASK W479: phase 2 round 4, rerun W477's night on C6 once the pointer can be confined

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w479`, branch `codex/v1.8.0-w479` from the shared branch |
| Decision | Root decision R699 (QUEUE row W479), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W477](TASK-W477-PHASE2-ROUND3-NIGHT.md) applies, except where this task changes it |
| Sources | [W477](records/W477.md), [W476](records/W476.md), and round 03 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-03/`, which W477 sealed with `SHA256SUMS.txt` and which must not change |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-04/`, with its own `tools/` copy and evidence. Tracked: the record `docs/work/v1.8.0-root-queue/records/W479.md` |
| Window | Root launches you only after the clip check below has passed three times. Start rows at once, keep the 07:30 last-start guard and the 07:43 input deadline, and stop everything by 07:45 |

## Why W477 stopped, and what Root found

W477's three J13 attempts failed the startup gate `require_screen` with `open: false` (F-W477-01). The game was in
the world, but the clip rectangle stayed at the whole desktop. The harness reads the clip that Minecraft's GLFW sets
while it holds the pointer, so the gate saw an open screen.

Root's reading of the cause:
- A probe at 23:50 set a cursor clip and found it cleared about 80 ms later, every time.
- The Synergy client on this PC (`synergy-core`, connected to the owner's other computer) was connected all evening,
  and the owner's cursor crossed onto this screen at 23:29:37 to 23:30:45 and 23:33:39 to 23:33:54.
- Round 02's sessions met the same gate in 26 sessions at about 01:00 on 2026-11-25, after Synergy's log reported
  the server offline at 00:11.

The pointer gate is right and stays unchanged. Synergy is the owner's program: never close, change or block it, or
any other program you did not start.

## What to do

1. **Before Java:** run W477's preflight checks:
   - copy `round-03/tools/` to `round-04/tools/` and check every copied file against round 03's `SHA256SUMS.txt`;
   - bind the copy to this worktree, as W477 did;
   - check the JAR hash, the fixture seals, free space and that no Java runs;
   - run the dry validator, the runner's dry check, the operator tests and the harness tests.
2. **Before each row's first server start**, run
   `python -B D:/GitHub/ARCE-Task-Evidence/root-queue/tools/clip_holds.py`. It exits 0 when a cursor clip holds for
   one second.
   - If it exits 1, do not start the row. Probe again every two minutes, and record each wait with its times.
   - A wait is not a failure and spends no attempt.
   - If the clip never holds again before 07:30, leave the remaining rows NOT_RUN and record why.
3. **Run the night order** of W476's runner, H-ready rows first and then the mapped steps of the partly mapped rows.
   Use fresh labels and fresh copies, and score each step on its own (R690).
4. **If the startup gate still fails while `clip_holds.py` passes**, the cause is something else:
   - keep the attempt, with the clip diagnostics W477 added;
   - try the next row once on a fresh copy;
   - if that also fails at startup, stop and record both attempts.
5. **For any other failure**, follow TASK W477's steps 4 and 5: an operator fault is fixed and rerun on a fresh
   copy, and a product defect is a finding for Root that is not rerun.
6. **Collect and record** everything TASK W477 lists, plus every clip wait. Seal round 04 with its own
   `SHA256SUMS.txt`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- No change under `src/` (frozen at C6, R600). No owner account, launcher profile or credential. No production
  admission switch.
- Do not edit round 01, round 02 or round 03.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
