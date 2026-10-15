# TASK W11b2, continuation 21: verify the merge, the rest of step 3, then steps 4 to 7

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `263186a1` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 20" (the exact-source recovery and return permits are done; where step 3 resumes) |
| Root decisions | R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`) |
| Record | Add a "Continuation 21" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C21/` |

Root removed continuation 20's two refused directories and merged the shared branch into this branch (`263186a1`, no
conflicts). The merge brings W77 (part-1 F9: a rejected respawn reuses the acknowledged destination UUID), W78 (the
sealed sleep-room fixture fix, which explains continuation 20's failure of that test), W81 (the loader codecs) and the
frozen W11b3 and cargo design notes.

## What to do (commit each step with its tests)

1. **Verify the merge first:** the whole unit suite, then `runGameTestServer` under the lock on `263186a1` plus this
   task (or your first commit), and the unchanged log checker. This also server-verifies continuation 20's corrected
   `exactSourceResumeAndReturnKeepNativeResourcesAndHold` fixture. If W77's change to `RocketTransferRecoveryService`
   interacts with this branch's governed exclusions, fix it in its own commit and say how.
2. **Finish step 3**, where continuation 20 says it resumes: connect live F1 preparation and F2 acknowledgement to the
   governed dispatcher and its action-specific boundaries, then destination publication, passengers, source removal,
   post-F4 return and failed landing, deferred callbacks and restart clock rebasing.
3. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that closes N1, so
   that the unchanged log checker passes; the A0 cases and the S2 cuts; F6 to F11 with the eight-flight benchmark as
   a measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a hold or the
replay exclusion needs Root first: stop, commit what you have with your record, and report. If you run short of
time, stop at a committed step boundary with every test of that step passing, and say exactly where the next
continuation starts.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C21/`. If your cleanup
command is refused, record the exact paths; Root deletes them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge).
- ADRs, the risk register, status files and the ledger (Root).
