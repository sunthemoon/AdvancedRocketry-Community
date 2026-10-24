# TASK W155-review: independent read-only review of W155 (the transfer history in journal leases, W153-R7)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w155` at `d53465b4` (base `f2905bff`; fix `26750a99`). Review worktree `D:/GitHub/arce-v180-w155-review`, a detached checkout of `d53465b4`; read `git diff f2905bff..d53465b4` |
| Task it answers | [TASK-W155](TASK-W155-TRANSFER-LEASE-HISTORY.md), with Root decision R103 |
| The finding | W153-R7 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W153-review/REREVIEW.md`, with its probe in `rereview-evidence/` |
| Record | `docs/work/v1.8.0-root-queue/records/W155.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W155/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W155-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W153-R7 closed.** A lease binds T's mutation history at acquisition. A publication or removal of T by any
   rocket after acquisition refuses it, including the unsampled sequence (published, removed, acknowledged, with no
   lease read between). Rerun the reviewer's probe against this branch if it applies.
2. **No false refusals beyond the stated bound.** Unrelated rockets and transfers stay independent. The record says
   that conservative overflow may refuse old leases: check the bound, when it is reached in practice, and that a
   refusal there is retried or reported by each caller as before. Look for any path that would now refuse forever.
3. **Callers unchanged for an unchanged T:** F1's preparation lease, the governed cleanup and launch checks, the pre-F2
   cancellation, and the members leases of reconciliation and settlement.
4. **Constraints.** RAM only; no saved format, schema, journal bound, budget or timeout change; per-call work bounded;
   nothing weakened; the four new tests fail on the old code.
5. **Merge** with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and with W152
   (`codex/v1.8.0-w152`, in progress): shared files and conflicts.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the changed unit classes alone, then the
`rocket.flight.persistence` and `resourceescrow` packages. No GameTest or server; no heavy-JVM lock. Check 6 GiB free
memory first. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W155-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
