# TASK W377R: independent read-only review of the X03 offline repair tool

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new, read-only session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w377r`, detached at W377's commit `979746385` |
| Decision | Root decision R477 (QUEUE row W377R) |
| Under review | `git diff bc5cdc928 979746385`: `scripts/repair_v180_rebuild_source.py`, its tests and `records/W377.md`, written by another Codex session under [TASK-W377](TASK-W377-X03-OFFLINE-TOOL.md) |
| Design | [W367-DESIGN](records/W367-DESIGN.md) revision 2, sections 1 to 4; Root's answers in QUEUE rows W367, W367F and W376 (R474) |
| Deliverable | `D:/GitHub/ARCE-Task-Evidence/root-queue/W377-review/REVIEW.md` (outside Git) |
| Write scope | The review file only. Do not change, stage or commit anything in any worktree. |

## Why

The tool writes saved data on a copy of a world. AGENTS.md section 13 requires a separate read-only review before such
a writer merges. You are a different session from the author and work from the code alone.

## What to check

1. **Scope.** The tool edits exactly the one record of the retired, stationary REBUILD_SOURCE shape. It changes nothing
   it does not name, and it never restores a single store, rebuilds a resource or refunds one.
2. **The rule.**
   - It is stricter than production, as section 2.1 says.
   - The candidate's own `ARCE_TRANSFER_FENCED` line is required as bound evidence.
   - The geometric FF_KEEP_SOURCE refusal is applied.
   - Every competing authority is refused.
   - It refuses unless every root is current.
3. **The writer.**
   - It works only on a copy and takes a lock.
   - Its output is reproducible gzip with canonical NBT and empty-list encoding.
   - A failure has no effect, and interrupted output leaves nothing behind.
   - `census` is read-only.
4. **Inputs.** Malformed, oversized or future-schema NBT is refused with a bounded message, and paths cannot escape the
   copy.
5. **Tests.** Every rule and refusal has a test, including an interrupted write and a no-effect failure. The tests do
   not depend on host state.

You may run the tool's tests and the helper suites from a copy in your own Temp folder under
`D:/GitHub/ARCE-Task-Evidence/Temp/`. Do not take the heavy-JVM lock and run no GameTest or native server.

## Output

`REVIEW.md` begins with a verdict: APPROVE, APPROVE-WITH-CHANGES or REJECT. Then give numbered findings, each with a
severity (High, Medium, Low or Information), the file and line, what is wrong and the change you recommend. Then give
the commands you ran with their results. Give its SHA-256 in your answer. The last line of your answer is the path of
`REVIEW.md`.
