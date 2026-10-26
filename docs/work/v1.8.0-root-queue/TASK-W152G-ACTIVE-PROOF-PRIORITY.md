# TASK W152G: fix W152-R3 (an active proof attempt must finish within its deadline)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W152F](TASK-W152F-SCHEDULING-AND-FAILURE-LATCH.md)
(R107), the W152F section of `records/W152.md` on the branch, and the re-review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W152-rereview/REVIEW.md`, SHA-256 `b52d8fab…87e`, verdict BLOCK) first. You
are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w152` (W152F's; it has finished) |
| Branch | `codex/v1.8.0-w152` at `c35ab3bea` (local commits only; Root merges) |
| Record | append a "W152G" section to `docs/work/v1.8.0-root-queue/records/W152.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152G/` |

## Root decision R109 (2026-10-25)

W152-R3 (High) is accepted as the re-review states it. W152-R2 is closed. Fix W152-R3 in its own commit, with a
regression that fails before the fix.

1. **An active proof attempt has priority.** Once a record has started a native proof attempt, it is served every
   tick, within the unchanged 256-inspection budget, until the attempt completes or expires. Lazy discovery and the
   fair rotation share what the active attempts leave.
2. **Held records wait for their own retry time.** A record whose state machine is held or backing off is not eligible
   for selection until then. The fair rotation runs only among eligible records, and no held record is discarded,
   skipped for good, or given a renewed deadline.
3. **Prove the worst case.** Show that with the supported concurrency (64 pending records and the existing native
   quotas), every active attempt gets enough service to finish within the 100-tick deadline. If the budget cannot
   carry a new attempt, defer starting that attempt; never extend a running one.
4. **Nothing else changes.** The 256 inspections, the 100-tick attempt deadline, the 200-tick retry, the native quotas
   and the pending bound stay as they are, and so do the protocol and the saved format.

## Tests

- Turn the re-review's `RecoveryProofSchedulingReviewTest` (in its `probe-src` folder) into a repository test that
  uses the real `SealedProofBatch`, a two-slot plan and immediate valid proofs. Cover 2, 3, 51 and 64 records, with
  the ready record in first, middle and last sorted position, and the others permanently held by CONFLICT and by
  UNLOADED. Every case must seal, and the held records must stay retained.
- A worst-case test with the most concurrent active attempts the native quotas allow. Each attempt finishes before
  its deadline, or is never started.
- The existing W152F regressions stay as they are: the two-, three- and four-record cases, the 240-visit census, and
  the latch and transient fixtures.

## Verification

- The new tests alone, failing before the fix.
- The W152 and W152F unit classes.
- The `resourceescrow` and `rocket.flight.persistence` packages.
- The whole unit suite.
- One complete GameTest with the unchanged log checker.
- The W152 native cases that the scheduler touches, with their two restarts.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; W159F shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes. A fresh Codex session re-reviews afterwards.

## Not in this task

- Merging with the shared head; Root does that.
- The W128 checkpoint cost.
- Admission or I6.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.