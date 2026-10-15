# TASK W11b2, continuation 15: step 3's dispatcher, permits and reservations, then steps 4 to 7

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `baf55eaa` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 14" (the record-3 codec, serial-aware no-T checks, and the legacy-recovery exclusion are done) |
| Record | Add a "Continuation 15" section to `records/W11B2.md`. Keep the existing text |

The task is TASK-W11B2-CONTINUE-14's, unchanged: finish step 3, then continue with steps 4 to 7. Step 3
still needs:
- the governed record-3 dispatcher;
- the action-specific permits;
- the combined reservation on every root-growing write.

**Root's triage of continuation 14's GameTest failure.**
`SleepRespawnGameTests.sealedBreathableRoomsAdmitSleepButAPendingRoomDoesNot` ("The large room was
not pending after one production scan tick") is an intermittent failure that is not yours. It also
failed once in continuation 09's run, and it touches nothing in escrow.
- Root is tracking it with the six-host bed test (W74).
- If it fails in your final run, record it with the log line and say that your change does not touch
  it. Do not retry it until it passes, and do not edit it.
- The unchanged log checker still has to show only N1's three warnings plus, if it fails, that single
  test.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a
hold or the replay exclusion needs Root first: stop and report. If you run short of time, stop at a
committed step boundary with every test of that step passing, and say exactly where the next
continuation starts.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C15/`.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- ADRs, the risk register, status files and the ledger (Root).
