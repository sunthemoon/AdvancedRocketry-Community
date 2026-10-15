# TASK W11b2, continuation 20: the rest of step 3, then steps 4 to 7

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `35adb7b2` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 19" (the F1 launch preparation, journal reservation and deferred-callback checks are done; where step 3 resumes) |
| Root decisions | R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`) |
| Record | Add a "Continuation 20" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C20/` |

Root removed continuation 19's three refused directories.

**Root's decision on the `PF_T_ACK` native failure.** Restart 1 failed only on a vanilla ERROR from
`mojang/YggdrasilServicesKeyInfo` ("Failed to request yggdrasil public key", `AuthenticationUnavailableException`):
the Mojang authentication server was unreachable at that moment. It is not an escrow failure.
- First, rerun the whole `PF_T_ACK` cut (the forced stop and both restarts) on your first committed source, with the
  unchanged runner. Keep continuation 19's failed evidence.
- If the same line appears again, add to the native runner one declared exception for exactly that logger and
  message, at most once per JVM start, and nothing else. Test it in the runner's Python tests: that line once passes,
  twice fails, and any other ERROR still fails. Record it as Root's decision.

## What to do (commit each step with its tests)

1. **Finish step 3**, where continuation 19 says it resumes: the live dispatch, the action-specific permits and
   recovery fences, deferred execution, cancellation and failed landing.
2. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that closes N1, so
   that the unchanged log checker passes; the A0 cases and the S2 cuts; F6 to F11 with the eight-flight benchmark as
   a measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a hold or the
replay exclusion needs Root first: stop, commit what you have with your record, and report. If you run short of
time, stop at a committed step boundary with every test of that step passing, and say exactly where the next
continuation starts.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C20/`. If your cleanup
command is refused, record the exact paths; Root deletes them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge).
- ADRs, the risk register, status files and the ledger (Root).
