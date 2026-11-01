# TASK W239: bay safe exit, slice S1, the pure candidate and budget model

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w239` (new; branch `codex/v1.8.0-w239` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W239.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W239/` |
| Design | [W230-DESIGN](records/W230-DESIGN.md), Revision 1 (merged at `964c2373d`): sections 3 (position selection: candidate order, safety predicate, hard bounds), 4 (the no-candidate fallback), 9 (tests) and 10 (row W230-S1) |
| Decisions | Q1 = A (R243) and Q2's semantics (R234), in [OWNER-DECISIONS-20261101](records/OWNER-DECISIONS-20261101.md) and QUEUE |

**Root decision R247.** Implement W230-S1 exactly as section 10 describes: a pure, deterministic model of the exit
candidates, the safety port and the shared per-tick budget.

- **Scope.** New `rocket/bay/exit/` classes and matching tests only: 3 or 4 main classes, about 400 to 650 lines in
  all, each below 400. No Forge hook, gameplay entry, event adapter, registration, packet, persisted field or
  language entry.
- **Model.** The finite candidate order and how many candidates are tried. The safety predicate is behind a port that
  reads only loaded state, with no chunk loading. The hard per-exit and per-tick budgets, and how exhausting them
  reports `EXIT_BUDGET_EXHAUSTED`. The deterministic ordering of competing exits, and the losing exit's next
  candidate or fallback (W230-R4). The result types that S2 will consume.
- **Tests.** The section 9 A0 rows for candidates, budgets and hazards, including the R4 contention cases: one
  available box, the last budget unit, reversed request order, and contention from a delayed completion. Each test
  class runs alone; they fail first where meaningful.
- If the design is ambiguous at a point S1 must settle, take the conservative reading and record it. Under the
  owner's delegation Root decides design points, so list them for Root rather than the owner.

## Verification

- Each new test class alone.
- The whole unit suite.
- The repository validator.

An independent review follows before the merge, because S2 builds on this model.

## Not in this task

S2 and S3, any hook or integration change, the cap (W238), bay admission, status files and the ledger.
