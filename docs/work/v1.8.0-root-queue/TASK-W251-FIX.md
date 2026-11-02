# TASK W251-fix: bound the escrow capture-veto diagnostic

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w251` (existing; branch `codex/v1.8.0-w251` at `f82947656`) |
| Task it answers | [TASK-W251](TASK-W251-CHEST-TEST-POSITION.md), Root decisions R274 and R280 |
| Record | Append a "W251-fix" section to `docs/work/v1.8.0-root-queue/records/W251.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W251-fix/` |

W251 found the double-chest test's cause (ambient water refilling a cleared cell at its new position) and fixed the
fixture. Its permanent diagnostic, `ARCE_ESCROW_CAPTURE_VETO` in `resourceescrow/EscrowBarrier.captureReason`, logs
the full exception at INFO on every capture veto. In W251's failing experiment one stuck test logged it 47 times in
400 ticks; on a real server a veto that keeps recurring would log thousands of stack traces an hour.

**Root decision R280.** Keep the diagnostic, but bound it: log the full exception once per distinct exception class
and message per server session, in a bounded set (for example 64 entries, with one line when that set is full),
and log nothing for later repeats. The persisted reason and every refusal stay as they are. A unit test shows that two
identical vetoes log once, a different cause logs again, and the bound holds. State the worst-case log volume in the
record, as AGENTS.md section 3.3 asks. The state must not be a static collection without a lifecycle: tie it to the
server session (for example the escrow runtime's lifetime) and clear it on server stop.

## Verification

- The new unit test failing first, then passing; the escrow unit tests and the whole unit suite.
- The focused `w152_special_containers` and `bay_deployment_lock` batches under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run and reviews the diff.

## Not in this task

The fixture, the escrow rules, status files and the ledger.
