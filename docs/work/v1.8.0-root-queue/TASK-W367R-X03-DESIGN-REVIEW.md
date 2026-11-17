# TASK W367R: independent read-only review of the X03 repair design

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new, read-only session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w367r`, detached at the shared head |
| Decision | Root decision R472 (QUEUE row W367R) |
| Under review | [W367-DESIGN](records/W367-DESIGN.md) and [W367](records/W367.md), written by a Codex session under [TASK-W367](TASK-W367-X03-REPAIR-DESIGN.md), with Root's answers in QUEUE row W367 (R463) |
| Deliverable | `D:/GitHub/ARCE-Task-Evidence/root-queue/W367-review/REVIEW.md` (outside Git) |
| Write scope | The review file only. Do not change, stage or commit anything in any worktree. |

## Why

The design proposes an offline tool that edits a copy of a world's saved data to resolve the protected FLOOR_CONFLICT
after `FF_REBUILD_SOURCE`. AGENTS.md section 13 requires a separate read-only review before such a change is
implemented, preferably by a different engine from the author.

## What to check

1. **The diagnosis.** It is true, store by store, against the code and against W142's recorded world (the ledger, the
   transfer journal, the rocket entities and the tank and device contents). The authoritative state is the right one.
2. **The choice of an offline tool.** The existing audited settlement really cannot resolve the case. The tool's scope
   is exactly the retired, stationary R74/R76 shape. Every competing authority is refused.
3. **The procedure.**
   - The whole-world backup and its hashes come first, and only a copy is edited.
   - Two saves and restarts follow, then the conservation check, then the whole-world replacement.
   - No single store is restored and no resource is rebuilt or refunded.
   - The refusal conditions are complete.
4. **Both ledger forms.** Root 2 is handled. Root 3, now merged and about to be activated, is handled too: the external
   repair account is honest (UNKNOWN unless a typed witness supports confirmed values) and nothing is resurrected.
5. **The native case and its oracle.**
   - The oracle checks conservation, no duplicate, the conflict cleared, ordinary use afterwards, and that every file
     not named is unchanged.
   - The POI-tamper control is a real negative control.
   - The file-difference boundary is right, given that Minecraft rewrites some files on every save.
6. **Slices, estimate and the operator text.** They are plausible and complete.

## Output

`REVIEW.md` begins with a verdict: APPROVE, APPROVE-WITH-CHANGES or REJECT. Then give numbered findings, each with a
severity (High, Medium, Low or Information), the design section, the code location you checked, what is wrong and the
change you recommend. Give its SHA-256 in your answer. The last line of your answer is the path of `REVIEW.md`.

## Rules

- Read-only. You may run `git` reads and searches, and targeted unit tests from a copy in your own Temp folder under
  `D:/GitHub/ARCE-Task-Evidence/Temp/`. Do not take the heavy-JVM lock and run no GameTest or native server.
- English, full sentences.
