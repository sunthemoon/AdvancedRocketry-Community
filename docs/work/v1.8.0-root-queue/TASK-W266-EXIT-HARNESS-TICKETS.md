# TASK W266: the bay exit harness's ticket comparison and one V2 command (W262-R1, R2)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w266` (new; branch `codex/v1.8.0-w266` from the shared head named in your prompt, which includes W254, W257 and W262) |
| Findings | W262-R1 and R2 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W262-review/REVIEW.md` (SHA-256 `79a49357d2df0dfebf533215a6176714fd2daf51b4d873a29bf478296daeeec8`), with its `probe_ticket_counts.py` |
| Record | `docs/work/v1.8.0-root-queue/records/W266.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W266/` |

**Root decision R294**, after the S2 merge:

1. **W262-R1 (Medium).** In `scripts/run_v180_bay_exit_cuts.py` and the gated report in
   `BayPassengerExitReleaseDrive`, ignore only vanilla's `TicketType.UNKNOWN` (timeout 1) across X2's descent and X5's
   wait, and compare every other ticket by chunk, type, level and key, normalizing refresh timestamps if needed. Add
   negative harness tests that an added PORTAL, POST_TELEPORT, `arce_rocket_flight` or custom timed ticket, and a
   same-total replacement of one ticket type by another, are each rejected; keep the UNKNOWN churn control and the
   synchronous map assertions. Rerun X2 and X5 natively on the reobfuscated JAR under the heavy-JVM lock.
2. **W262-R2 (Low).** In `records/W254-V2-CHECKLIST.md` step 3.5, use `/execute at <B> run tp <B> ~0.1 ~ ~` (or make B the
   executing operator) and ask the tester to record the actual coordinates.

No production code changes.

## Verification

- The harness's Python tests with the new negative cases failing first on the current comparison; the native X2 and X5
  reruns; the repository validator.

Commit on the branch; do not push. Root pushes for a sharded run and reviews the diff.

## Not in this task

The bay exit's production code, other cut cases, status files and the ledger.
