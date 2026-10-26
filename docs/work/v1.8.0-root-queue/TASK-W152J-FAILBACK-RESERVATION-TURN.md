# TASK W152J: fail-back reservations take part in the sealed hand-off (W152H-R1)

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W152I](TASK-W152I-CROSS-GUARD-CONTROL-AND-BOUND.md)
(R112), the W152H and W152I sections of [records/W152.md](records/W152.md) and the review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W152H-review/REVIEW.md`, SHA-256 `1685ad9a…8b70`, verdict BLOCK) first. Its
evidence-only probe, `probe-src/.../SealedArrivalReviewTest.java`, holds the failing arrival sequences. You are a new
session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w152j` |
| Branch | `codex/v1.8.0-w152j` from the shared head that records this task (local commits only; Root merges) |
| Record | append a "W152J" section to `docs/work/v1.8.0-root-queue/records/W152.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152J/` |

## Root decision R114 (2026-10-26)

W152H and W152I are merged at `787c28e52`, and W160 after them, with W152H-R1 open. Like W152-R4 under R110, the
finding needs sealed records, which only test hooks create while player admission (N3-B3) is off. Player admission
stays off until this task is merged after a fresh review. W152H-R1 is accepted as the review states it: a `FAIL_BACK`
entry keeps its caller-held reservation for its creation tick after the turn check returns `BUSY`;
`activeAttempts()` counts that token, so sealed `canStart` refuses. A fail-back arriving every tick, or nudges that
renew a waiting return's token, keep the sealed candidate waiting without any ordinary proof starting.

## Work

1. Write a short design paragraph in the record first. New and renewed caller-held fail-back reservations take part
   in the same admission arbitration as proof starts. While a sealed candidate holds the turn under R112, they must
   not occupy capacity in a way that keeps it from starting.
   - Keep reservation before mutation, the fail-back callback and the `JOURNAL_PENDING` boundary.
   - Do not remove the token protection, and do not stop counting tokens in the capacity check.
   - A fail-back that cannot reserve gets the existing refusal and retry path, with no source, record or pad change
     (N2).
2. Regressions in the repository, using the production classes as the review's probe does, both callback orders:
   - one fail-back arrival per tick through tick 10: the sealed first capture meets the R112 bound;
   - alternating nudges of two pending fail-backs through tick 300: the sealed candidate starts within the bound and
     not only after the nudges stop;
   - the `RECOVERED_SOURCE` control and the existing hand-off cases stay green;
   - a waiting fail-back still gets its own turn after the sealed attempt ends (no starvation the other way).

   The first two fail on the current shared head.
3. No change to quotas, the 100-tick proof deadline, the 200-tick retry, the 64-pending bound, the inspection
   allowance, saved formats or the protocol.

## Verification

- The new tests alone, failing before the fix.
- The W152, W152F, W152G and W152H unit classes, unchanged, then the `resourceescrow`, `rocket.server` and
  `rocket.sealed` packages and the whole unit suite.
- One complete GameTest that passes with the unchanged log checker.
- The eight W152 native cases with their two restarts each, if production scheduling code changes.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; W161 shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes. A fresh Codex session reviews afterwards.

## Not in this task

- Enabling player admission (N3-B3) or I6.
- The W80 command and view, and W161's tests.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.
