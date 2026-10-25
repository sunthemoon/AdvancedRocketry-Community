# TASK W152I: the two open W152H items

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md),
[TASK-W152H](TASK-W152H-HANDOFF-EXPIRY-AND-CROSS-GUARD.md) (R110), the W152H section of
[records/W152.md](records/W152.md) on the branch below (its "delivery checkpoint" lists both items) and
`D:/GitHub/ARCE-Task-Evidence/root-queue/W152H/BOUND-CLARIFICATION.md` first. You are a new session; W152H's session
has grown too large to resume.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w152h` |
| Branch | `codex/v1.8.0-w152h`, continuing from `0092fee8f` (local commits only; Root merges) |
| Record | append a "W152I" section to `docs/work/v1.8.0-root-queue/records/W152.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152I/` (W152H's evidence stays as it is) |

## Root decision R112 (2026-10-26)

1. **The R4 bound is relative to native capture availability.** W152H showed that the unchanged native Level quota
   lets a 20-slot attempt capture at ticks 0–3, 20–23, 40–43, 60–63 and 80–83, so after a final callback at 84 the
   next native opportunity is tick 100. The literal "2 ticks after the sealed attempt ends" cannot hold without
   delaying the sealed attempt, which is not authorized. The bounds now read:
   - a waiting ordinary return gets its first capture at the first native capture opportunity of its Level at or
     after the end of the sealed attempt that was active when it asked, allowing the existing 2-tick service lag, and
     no sealed attempt reserves or captures before it;
   - the reverse holds for a waiting sealed candidate after the ordinary attempts admitted in that turn end;
   - each attempt keeps its own unchanged deadline.

   With the unchanged quota the wait after a sealed attempt is at most 16 ticks plus the service lag; that is
   accepted. Make the hand-off regressions assert this restated bound against the real `EscrowBarrierBudget`, and turn
   W152H's `NativeQuotaBound` probe (the 84-to-100 case) into a repository unit test that uses the production
   classes. Correct the record's wording. No quota, deadline, retry or budget changes.
2. **The cross-guard GameTests' positive control.** In W152H's complete GameTest
   (`W152H/full-gametest-final-3/`), both `SealedReleaseGuardGameTests` stall with
   `actionable=true; tested=false`, because `RocketReleaseWorld.plan` keeps returning null; both pass in a focused
   world.
   - Find which condition in `plan` refuses in the complete suite. Use a read-only test-side diagnostic or an
     uncommitted local probe; add no production log line.
   - If production refuses a state that the W80 design says must be planned, that is a production defect: fix it
     with a regression that fails first, and say so plainly in the record.
   - If the complete-suite world legitimately differs (for example state that an earlier test leaves in a
     server-wide store, a replaced `settled` or `live` set, or another loaded rocket), make the fixture establish its
     own precondition. Keep the guard assertions, the positive control and the 200-tick limit; no batch moves to
     hide the cause.
   - If neither gives a reliable pass in the complete suite, stop and report the diagnosis. Do not remove the
     GameTests.

## Verification

- The new and changed tests alone; the R112 regressions fail on `0092fee8f` where they check something new.
- The W152, W152F, W152G and W152H unit classes, then the whole unit suite.
- One complete GameTest that passes with the unchanged log checker.
- Rerun the eight W152 native cases only if production code changes.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; W160F shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep
the evidence under 100,000,000 bytes. A fresh Codex session reviews W152H and W152I together afterwards.

## Not in this task

- Player admission (N3-B3) or I6.
- The W160 command and view.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.
