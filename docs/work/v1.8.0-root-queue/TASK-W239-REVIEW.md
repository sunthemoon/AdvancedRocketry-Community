# TASK W239-review: independent read-only review of the bay exit model (S1)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w239` at `295589374`, cut from `fec38d5b6`: four commits adding `rocket/bay/exit/` (`BayExitBudget`, `BayExitCandidates`, `BayExitSafety`, `BayExitSelector`) and four test classes. Review worktree `D:/GitHub/arce-v180-w239-review`, a detached checkout of `295589374` |
| Task it answers | [TASK-W239](TASK-W239-BAY-EXIT-S1.md), Root decision R247 |
| Design | [W230-DESIGN](records/W230-DESIGN.md) Revision 1: sections 3, 4, 9 and 10 (W230-S1), and the closed review findings W230-R1 to W230-R4 |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W239-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Fidelity to the design.**
   - The candidate order and count, the safety predicate, the hard per-exit and per-tick budgets, and how exhausting
     them is reported all match sections 3 and 4.
   - Contention between exits is deterministic as W230-R4 requires: one box, the last budget unit, reversed request
     order, and a delayed completion. The losing exit gets its exact next candidate or fallback.
2. **Purity.**
   - No Forge hook, registration, packet, persisted field or language entry.
   - The safety port reads loaded state only, and nothing in the model can load a chunk.
   - No static mutable state, and the budget cannot leak across ticks or exits.
3. **Bounds.** Every loop and collection is bounded by the design's limits, with no unbounded allocation per call.
4. **Tests.** They pin the A0 rows of section 9 and would catch the following:
   - a wrong candidate order;
   - a budget off by one;
   - a hazardous or unloaded position accepted;
   - nondeterministic arbitration.
   They failed first where meaningful.
5. **What S2 consumes.** The result types are enough for S2, and S1 decides nothing that the design leaves to S2.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`, the four new test classes and the
`rocket` test package. Delete the worktree's `build`, `.gradle` and `logs` afterwards if you are allowed to;
otherwise list them for Root.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W239-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
