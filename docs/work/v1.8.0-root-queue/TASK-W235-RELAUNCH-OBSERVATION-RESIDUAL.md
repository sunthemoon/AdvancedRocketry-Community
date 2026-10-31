# TASK W235: the two full-observation first-flight checkpoints over 100 ms

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w235` (new; branch `codex/v1.8.0-w235` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W235.md`; logs and raw samples under `D:/GitHub/ARCE-Task-Evidence/root-queue/W235/` |
| Sources | [TASK-W215](TASK-W215-RELAUNCH-FIRST-USE.md) and its record and review (`EscrowRelaunchClasses`, merged); [TASK-W212](TASK-W212-W90-RELAUNCH-P99.md) and its harness; [TASK-W217](TASK-W217-W215-FOLLOW-UPS.md) and the R-035 text it added; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) section 4 |

**Root decision R239.** After W215, the first governed relaunch's first-flight checkpoint P99 meets 100 ms in all nine
timing-only cases and in seven of nine full-observation cases. The two that miss are the new strip at 102.789 ms and
the generated contained case at 103.517 ms. ROOT-HANDOFF-20261031 lists this as open: either a further measured
remedy or an owner disposition.

1. **Attribution.** On W212's harness, measure the two failing cases and their timing-only twins on the current
   shared head, three runs each, with raw samples. Say how much of the gap is the full observation's own overhead,
   by comparing identical work with and without it, and how much is product cost. Name the product part, if any,
   with a profile.
2. **Remedy.** If a product cost remains, propose and implement the smallest change that brings both cases under
   100 ms at P99 without moving work into another tick budget. Keep state, authority, ordering and saves unchanged,
   and add a test that fails first. If it touches persistence or the relaunch path, write a design note first; an
   independent review follows.
3. **No remedy.** If the gap is observation overhead, or if no safe remedy exists, write the exact evidence and an
   R-035 draft for the owner's disposition, in `D:/GitHub/ARCE-Task-Evidence/root-queue/W235/W235-drafts.md` with an
   audit. Do not change the budget or the measurement.

## Verification

- The harness runs with their raw samples.
- The whole unit suite.
- The relaunch GameTests three times under the heavy-JVM lock.
- The repository validator.

## Not in this task

The budget, the measurement method, other performance items, status files and the ledger.
