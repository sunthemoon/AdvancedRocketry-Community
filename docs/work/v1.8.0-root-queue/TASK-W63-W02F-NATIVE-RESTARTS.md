# TASK W63: native restart verification of the W02F rocket recovery fixes (F1, F2)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w63-w02f-restarts` (from the shared branch) |
| Branch | `codex/v1.8.0-w63-w02f-restarts` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W63.md` (new) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W02F-review/REVIEW.md`, findings F1 and F2. Both are fixed in code, but the native clean-restart and repeated-restart verification are still open |
| Records | `records/W02.md`, `records/W02F.md` |

W02F fixed two Critical rocket recovery defects. It verified them only with GameTests that round-trip NBT through fresh managers, not with actual server stops and starts:

- **F1:** a landed rocket whose fuel was withdrawn after arrival must not be rebuilt with the journal's historical fuel on restart. Disassembling it must release the retained COMMITTED record.
- **F2:** while a PREPARED source waits for an offline passenger, every fuel mutation is refused. A clean recovery therefore cannot reinstall the journal's fuel over a changed balance. Once the passenger reconnects, mutation is allowed again.

## What to do

1. Build native runs for both scenarios with the existing native harness pattern. The W44 and W11b2
   runners (`scripts/run_v180_*`) are examples: a pinned committed source and jar, real dedicated
   server stops and starts, and per-restart inspection of the saved files and the live state.
   - **F1:**
     - land a MONO rocket and withdraw fuel with two buckets;
     - stop and start the server twice, and check after each start that exactly one rocket exists, with
       its withdrawn balance;
     - disassemble it, stop and start again, and check that the COMMITTED record is gone and that
       nothing is rebuilt.
   - **F2:**
     - make a PREPARED source with an offline recorded passenger;
     - try a hand withdrawal, a hand fill and a loader operation, and check that each is refused with
       the recovery-pending message;
     - stop and start three times without the passenger, and check after each start that the bank
       balances equal the journal's source vector and that no fuel was duplicated or lost;
     - then let the passenger reconnect, and check that mutation is allowed and the PREPARED entry is
       gone.
   - Add one F2 variant with two recorded passengers who reconnect in sessions that do not overlap,
     and record what happens. The review notes that this case is untested. If the rocket stays
     locked, that is the known limitation, not a failure. Record it.
2. If a run fails because of production code, stop and report with the evidence. Do not change the
   recovery or admission rules; that needs Root.
3. Keep fixture code gated and inert in production, as the existing release-test hooks are.

## Commands

- Run the whole unit suite once.
- Run `runGameTestServer` under the lock on a **committed** SHA, then the log checker (only if you
  change GameTest code).
- Run the native runs under the lock. Keep the evidence under 100 MB in
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W63/`, with an inventory and SHA-256 values.
- At the end, delete your generated worlds, server copies and older jars with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- An abandonment or admin recovery for a rocket whose passenger never returns. That is a separate
  design; note what you observe.
- The C17a loader's mB-precise follow-up (W02F's F3).
- ADRs, the risk register, status files and the ledger (Root).

## Root decision (2026-10-15): the loader's diagnostic

This answers the F2 stop in `records/W63.md` (`efe50eb7`).

- **What decides the case.** The loader excludes the locked rocket and changes neither bank. The
  refusal and the unchanged balances are what F2 requires. The task's wording, "refused with the
  recovery-pending message", over-specified the loader's status line.
- **For the native runs:** accept `waiting_for_rocket` as the loader's status. Assert the exclusion,
  the unchanged loader buffer, and the unchanged source vector.
- **Follow-up for Root's queue, not this task:** a loader status that names the recovery lock.
  Record it as a known gap.
- **Next:** run the rest of F2:
  - the three offline restarts;
  - the reconnect and unlock;
  - the two-passenger variant whose sessions do not overlap.

  Then deliver as the task says.
- **Cleanup:** Root deleted `D:/GitHub/ARCE-Task-Evidence/Temp/W63-native-01` and
  `D:/GitHub/ARCE-Task-Evidence/Temp/W63-native-02`. If tool policy refuses your cleanup again,
  record the paths again.
