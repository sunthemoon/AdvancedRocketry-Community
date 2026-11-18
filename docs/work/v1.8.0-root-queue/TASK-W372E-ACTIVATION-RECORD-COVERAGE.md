# TASK W372E: complete the activation record's per-test coverage

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w372`, branch `codex/v1.8.0-w372`, at Root's integration merge `c5714243e` (the activation at `dbe073d5a` with the shared head merged in) |
| Decision | Root decision R503 (QUEUE row W372E) |
| Review | W372R's review: `D:/GitHub/ARCE-Task-Evidence/root-queue/W372-review/REVIEW.md` (outside Git), findings 1 and 2, the section "Missing per-test rows", and the table of all 220 Java diff hunks. The reviewer's helper files `hunks.json` and `source.diff` are in the same folder if present. |
| Record | `docs/work/v1.8.0-root-queue/records/W372.md`: append a section "W372E coverage addendum (R503)". Do not rewrite or reorder any earlier section or row. |
| Write scope | The record only. No code, test, script, resource or other document. |

## Why

The record claims a classification row for every changed test, but W372R found changed tests without a row, and some
numerical re-pins without their old value. The code is accepted; the audit trail is not complete.

## What to do

1. **Coverage table.** One row for every test that `git diff e99e4a35c dbe073d5a` changes and that no earlier table
   row names: the test, the change, the R468 class (1, 2 or 3), and the reason. Use the reviewer's list as the
   starting point, and check the diff yourself so that the table is complete.
   - For a helper or fixture change, name the tests that consume it.
   - Give the five fluid settlement GameTests their rows: the KNOWN or UNKNOWN_ENDPOINT accounting page, four
     inspector lines instead of two, and the exact signed accounting diagnostics.
2. **The `ordinaryL2` rewrite.** Explain in its own paragraph why `RocketSettlementIdleGameTests.ordinaryL2` and its
   siblings now drive the real C3 to C8 progression, and why the old `records.noneMatch` assertion and the marker
   checks became conditions that must hold before the test succeeds, under the unchanged 1,200-tick timeout. Say what
   a failure looks like now.
3. **Class-1 sub-rows (finding 2).** For every count or byte value that changed, give the old value, the new value
   and the reason, and separate a change caused by the root-3 charge (16,384 bytes per operation instead of 14,336)
   from a change caused by R469's padding. Include at least `EscrowReservationTransitionTest` (old 62, 58, 60 and 56;
   new 55, 51, 53 and 50) and `EscrowItemCapacityTest` (old 62, new 61). Recompute each value and show the arithmetic.
4. **Stop rule.** If you find a changed assertion that weakens a test, or a change that fits no class, stop and report
   it; do not write a row that hides it.

## Rules

- Change no code. Run `python -B scripts/validate_repository.py --require-approved-identity` before you commit; it must
  report zero failures.
- Commit once on the branch with a message in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of `records/W372.md`.
