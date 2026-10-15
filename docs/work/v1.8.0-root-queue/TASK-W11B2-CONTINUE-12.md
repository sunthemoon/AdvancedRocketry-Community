# TASK W11b2, continuation 12: design 2 steps 3 to 7, from continuation 10's checkpoint

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md),
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md), [TASK-W11B2-CONTINUE-10](TASK-W11B2-CONTINUE-10.md) and
[TASK-W11B2-CONTINUE-11](TASK-W11B2-CONTINUE-11.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `afdce472` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`); Root's decisions in TASK-W11B2-CONTINUE-09 (`f83103fa`) and `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` |
| Input | `records/W11B2.md`, sections "Continuation 10" and "Continuation 11": what is done and the exact remaining scope |
| Record | Add a "Continuation 12" section to `records/W11B2.md`. Keep the existing text |

Continuation 10 delivered the bounded serial, replay and outcome transitions and the batched checked
writes (`79a4757b`). They are package-private and nothing calls them yet. Continuation 11 fixed every
review finding: the admission limit is 5 including retired markers, and the decode bound stays 8.
The only remaining log checker failure is N1's three warnings.

## What to do: steps 3 to 7 of TASK-W11B2-CONTINUE-10, in order

Commit each step with its tests.

3. Finish step 3. Add the F1 source checkpoint adapter (own-store, force, stable readback) before
   ARMED. Wire the governed record-3 dispatcher and the action-specific permits into the boundary
   table of design section 1. Route every root-growing write through continuation 11's combined
   reservation.
4. Implement the flight order F0 to F8 (section 5.4) and the credited-world reconciliation C0 to C8
   (section 5.3).
5. Write the required test. It closes N1, and the unchanged log checker must pass.
6. Add the A0 cases and the S2 cuts. Run each cut with two restarts, pinned to the committed source
   and its jar. Include native held-B or unloaded-C pruning and the O4 replacement-before-callback
   cut. List what is left.
7. Implement F6 to F11: the console-origin `inspect` and `retry` commands, retry, status and fairness,
   and the eight-governed-flight benchmark. Record the benchmark as a measurement.

The design is the contract. A change to an acknowledgement order, a proof, a hold or the replay
exclusion needs Root first. In that case stop, commit what you have with your record, and report. If
you run short of time, stop at a committed step boundary with every test of that step passing, and
say exactly where the next continuation starts.

## Commands

- Run the whole unit suite once.
- Run `runData` under the lock if data generation changes.
- Run `runGameTestServer` under the lock on a **committed** SHA, then the unchanged log checker.
- Run the native cuts under the lock. Keep the evidence under 100 MB in
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C12/`.
- At the end, delete your generated `build/gametest/world` and Temp copies with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, and signed per-fluid accounting.
- ADRs, the risk register, status files and the ledger (Root).

## Root decision (2026-10-15): the proof scope of a pre-F2 cancellation

This answers the clarification in `records/W11B2.md`, "Continuation 12" (`696b99ae`).

1. **Before F2, cancellation needs no destination-slot proof.** An ARMED entry whose journal holds no
   record T for it is cancelled by:
   - the exact unchanged source E;
   - a checked no-T state: an acknowledged journal baseline with no record for this logical rocket and
     none naming this serial.

   This is the F1 row's own rule. It is not an exception to F6.
2. **Why it is sound.** No destination effect can exist before F2:
   - F1 forbids COUNTDOWN and every resource change before F2;
   - the destination UUID is fixed fresh for each serial;
   - spawning at the destination needs the PREPARED record T, which only F2 writes.

   So "destination absent" follows from "no T" before F2.
3. **From F2 onward** (T exists), cancellation follows F6 to F8 with the proved absent destination, as
   the paragraph below F8 and section 1's cancellation row say.
4. **What stays unchanged:**
   - UNKNOWN is never no-T;
   - a journal that is not checked or acknowledged is a hold;
   - no field is added to `Armed`.
5. **Tests.** Add both of these with their S2 cuts (two restarts each), pinned to the committed source
   and its jar:
   - A0 and native: a cut on each side of ARMED replacement, then a restart that acknowledges CANCELLED
     for E with no T;
   - the negative case: with T written, the same restart must not take the pre-F2 path.

Then continue the task from step 3 in its order.
