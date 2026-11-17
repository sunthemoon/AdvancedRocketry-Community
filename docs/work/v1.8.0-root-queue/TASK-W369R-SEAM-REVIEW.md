# TASK W369R: independent read-only review of simulation slice S1 (the file-operations seam)

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new, read-only session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w369r`, detached at W369's commit `1748f6f1f` |
| Decision | Root decision R472 (QUEUE row W369R) |
| Under review | `git diff 44dda90db 1748f6f1f`, written by a Claude session under [TASK-W369](TASK-W369-STORAGE-SEAM-S1.md), with its record `records/W369.md` |
| Design | [W368-DESIGN](records/W368-DESIGN.md), sections 0, 2 and 6 |
| Deliverable | `D:/GitHub/ARCE-Task-Evidence/root-queue/W369-review/REVIEW.md` (outside Git) |
| Write scope | The review file only. Do not change, stage or commit anything in any worktree. |

## Why

S1 reroutes every checked write of the mod's managed stores (`CheckedSavedDataFile.replace`, `atomicMove` and the
directory flush) through a new seam. Any change of order, exception or bytes there would touch every world's
durability. The author was a Claude session; you are a different engine.

## What to check

1. **`DIRECT` is the old code, call for call.** Compare the order of operations, every exception type and message,
   the bytes written, the readback, the move options, the directory flush (including the Windows return) and the
   cleanup with the code at `44dda90db`. Look for any new catch, retry, buffer or ordering change.
2. **Gating.** The field holds `DIRECT` unless every gate holds: the release-test hooks, a dedicated server, the open
   release-test gate and the trace property. Check that it is restored at server stop; read `restore()`. Check that no
   path installs a recorder in production, or leaves one installed after a stop.
3. **The recorder.**
   - It never changes a write's outcome.
   - Its bounds and its refusal marker work.
   - Each line is written unbuffered.
   - The trace stays outside the world.
   - Concurrent writers get a correct global order.
4. **Thread safety.** The seam field is published safely to the writer threads.
5. **The relaunch inventory change.** The two classes are inert at initialization.
6. **Tests.** The tests pin `DIRECT`'s behaviour, and no existing test was changed (AGENTS.md section 5).

You may run the targeted tests and the full unit suite from a copy in your own Temp folder under
`D:/GitHub/ARCE-Task-Evidence/Temp/`, with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7` and `--offline`. Do not take the
heavy-JVM lock and run no GameTest or native server.

## Output

`REVIEW.md` begins with a verdict: APPROVE, APPROVE-WITH-CHANGES or REJECT. Then give numbered findings, each with a
severity (High, Medium, Low or Information), the file and line, what is wrong and the change you recommend. Then give
the commands you ran with their results. Give its SHA-256 in your answer. The last line of your answer is the path of
`REVIEW.md`.
