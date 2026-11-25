# TASK W480: phase 2 round 5, continue W479's night from J12 on C6

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w480`, branch `codex/v1.8.0-w480` from the shared branch |
| Decision | Root decision R700 (QUEUE row W480), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W479](TASK-W479-PHASE2-ROUND4-NIGHT.md) and [TASK W477](TASK-W477-PHASE2-ROUND3-NIGHT.md) applies, except where this task changes it |
| Sources | [W479](records/W479.md) and round 04 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-04/`, which W479 sealed and which must not change |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-05/`, with its own `tools/` copy and evidence. Tracked: the record `docs/work/v1.8.0-root-queue/records/W480.md` |
| Window | Start rows as soon as the preflight passes. Keep the 07:30 last-start guard and the 07:43 input deadline, and stop everything by 07:45 |

## Where W479 stopped

W479 completed the input queues of J13, B02, J16, D05, D06, C04 and A14. At 02:32, J12's first session stopped
before joining: the client showed "Failed to connect to the server" / "Timed out" while the server was bound and
answering on loopback (F-W479-06). W479 then stopped the whole night.

That symptom is the known first-connection timeout
[KI-V180-CONNECT-01](../../releases/v1.8.0/KNOWN-ISSUES.md). The known issue records that a retry gets in, and that
every timeout is a pending finding with no tolerated rate. Root decision R700: a connection timeout is recorded and
counted, and it does not end the night.

## What to do

1. **Before Java:**
   - copy `round-04/tools/` to `round-05/tools/` and check every copied file against round 04's `SHA256SUMS.txt`;
   - bind the copy to this worktree, as W479 did;
   - check the JAR hash, the fixture seals, free space and that no Java runs;
   - run the dry validator, the runner's dry check, the operator tests and the harness tests.
2. **Continue the night order from J12**, with W479's operator corrections. Skip the seven rows W479 completed.
   Then run the mapped steps of the partly mapped rows. Run `clip_holds.py` before each row as TASK W479 says.
3. **Connection timeouts (KI-V180-CONNECT-01):** a session that stops before joining, with the client showing the
   connection timeout while the server is bound and answering, is a timeout.
   - Keep the attempt, record it as a KI-V180-CONNECT-01 occurrence with its log lines and native frame, and rerun
     that row once on a fresh copy.
   - If the rerun times out as well, leave the row NOT_RUN with that reason and continue with the next row.
   - Stop the night only after three timeouts in a row, counted across rows.
   - Do not raise any timeout or change the join gate.
4. **Every other failure and stop** follows TASK W477's steps 4 and 5: an operator fault is fixed and rerun on a
   fresh copy, a product defect is a finding for Root that is not rerun, and a cleanup error or retained lock is
   resolved before the next row.
5. **Where a check's expected value changes,** quote the checklist line that shows the check was written wrong.
6. **Score each step on its own** (R690). Label captured visual-oracle `[H]` steps P_PENDING, as W479's record does.
7. **Collect and record** everything TASK W477 lists, plus each timeout and clip probe. Seal round 05 with its own
   `SHA256SUMS.txt`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- No change under `src/` (frozen at C6, R600). No owner account, launcher profile or credential. No production
  admission switch. Never close, change or block a program you did not start.
- Do not edit rounds 01 to 04.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
