# TASK W476: continue the full-row phase 2 scenarios until 23:00, without starting the game

Root: Claude. Date: 2026-11-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w476`, branch `codex/v1.8.0-w476` from the shared branch |
| Decision | Root decision R695 (QUEUE row W476), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W474](TASK-W474-PHASE2-FULL-ROW-SCENARIOS.md) applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Start from | W474's record (`docs/work/v1.8.0-root-queue/records/W474.md` on branch `codex/v1.8.0-w474`, commit `6479b839f`; read it with `git show`) and its tools in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-03/tools/`, including `step-map.json`, the dry validator and the 30 operator tests |
| Outputs | The same round-03 tools, updated in place; the record `docs/work/v1.8.0-root-queue/records/W476.md`; a tracked harness change only as TASK W474 allows |
| Time | Stop authoring at **23:00 local time** and deliver: tonight's run starts at 23:30 from these files, so leave them consistent, with the dry validator and tests passing for every session marked ready |

## What to do

1. **Continue authoring** the 35 rows that still have open steps, in `run-order.json` order. W474 listed what each
   row lacks. Map every `[H]` step that the harness can drive. Keep W474's fixed NOT_COVERED reasons and add new
   ones only for the same causes: the owner's account, V2, the second GPU, a human listener, PresentMon absent, or
   a named human-hand input.
2. **Split mixed steps.** Where one `[P]` step asks for both a sound judgment and a visual judgment (for example
   A14 steps 4 and 6: particles, models and sound), keep the step's single result NOT_COVERED for its sound half.
   Still plan and capture the native screenshots of its visual half, and mark them for Root's review, so the visual
   evidence exists.
3. **Change the night runner so that a row that is not ready is skipped, not a stop.** Tonight it must:
   1. run every row whose `[H]` steps are all mapped, depth first in `run-order.json` order;
   2. then run the mapped steps of the partly mapped rows, scoring each step on its own, with the unmapped steps
      left NOT_RUN;
   3. stop for good only on what the fail-closed queue already stops on (a failed summary, a cleanup error, a
      retained lock or a failed request), or at 07:45.

   Show this with a dry check without Java.
4. **Keep it runnable.** After each row, run the dry validator and the operator tests. A row you could not finish
   by 23:00 stays partly mapped, with its open steps NOT_RUN and the reason.
5. **Record:**
   - the per-row counts: `[H]` mapped, `[P]` named and fixed NOT_COVERED by reason, against W474's;
   - the night-runner change and its dry check;
   - the rows still open, with what they lack;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- Do not start any Minecraft client, server, Gradle task or other JVM.
- Commit the record, plus any harness change, on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
