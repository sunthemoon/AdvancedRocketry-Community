# TASK W152F: fix the W152 review findings (W152-R1 and W152-R2)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W152B](TASK-W152B-SEALED-STAGING-IMPLEMENTATION.md)
(R96 to R101), the top section of W152's record (`records/W152.md` on the branch) and the review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W152-review/REVIEW.md`, SHA-256 `4f38a9f3…338e`, verdict BLOCK) first. You
are a new session; W152's own session has grown too large to resume.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w152` (W152's; it has finished) |
| Branch | `codex/v1.8.0-w152` at `ca8cfa98` (local commits only; Root merges). `ca8cfa98` is Root's tag-overlay integration on top of W152's `d208bca8` |
| Record | append a "W152F" section to `docs/work/v1.8.0-root-queue/records/W152.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152F/` (W152's evidence stays as it is) |

## Root decision R107 (2026-10-25)

Both findings are accepted as the review states them. Fix each in its own commit, with a regression that fails before
the fix.

1. **W152-R1 (High): no record may starve another.** Recovery selection must advance only when the selected record
   actually gets a chance to work. Alternatively, lazy discovery and selected recovery share the existing budget of
   256 fairly within a tick. Do not raise any budget or timeout, and do not discard or skip a held record. A record
   that is held by an unloaded or foreign footprint must not block a ready one. Regression:
   - two concurrent disjoint U records, one permanently held and one ready, with lazy discovery enabled;
   - the ready record finishes on its own;
   - cover both sorted positions and at least three candidates.

   Extend the review's `RecoveryBudgetProbe` scenario into a repository test.
2. **W152-R2 (Medium): a failed preparation is latched, not retried.** Separate transient holds from terminal
   preparation failures:
   - Transient holds (readiness, an unloaded footprint, IO, a delayed writer) keep their 200-tick retry.
   - Terminal failures (the adapter throws, needs a Level, mismatches on recapture, or exceeds its time check) latch
     for the loaded carrier before any discovery or direct attempt can call the adapter again.

   Keep C's original root, the discarded target's removal and emptying (R96), and the one log line. The latch lives in
   RAM on the loaded carrier, with no new saved schema. So "no retry" means at most one attempt per load of the
   carrier: when the carrier loads again (a chunk reload or a restart), one new attempt and one WARN are allowed. That
   matches R100's one WARN per carrier per load. Root adds this to ADR-065 and R-035. Regression, driven by the
   coordinator past 200 ticks for every failure class: count the provider calls (exactly one per load, including
   after an unload and reload), the retained resources and the diagnostics.
3. **Idle cost (report only).** The review found that loaded discovery walks loaded chunks and block entities every
   tick, even with no active U and an empty hint queue. Measure that idle cost on a GameTest world, per tick, with no
   U and no carriers, and record it. Change it only if the change keeps every carrier discoverable after a crash: the
   carrier's load hint, plus a sweep after a hint overflow. Otherwise leave it for Root.

## Verification

- Each fix's tests alone, failing before the fix.
- W152's changed unit classes alone.
- The `resourceescrow` and `rocket.flight.persistence` packages.
- The native-oracle Python tests.
- The whole unit suite.
- One complete GameTest with the unchanged log checker.
- The native cases that the scheduler change touches, with their two restarts.

Heavy runs go under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule. W159 shares the lock.
Check C: and D: above 10 GiB and 6 GiB free memory first. Keep the evidence under 100,000,000 bytes. The W152 reviewer
re-reviews afterwards.

## Not in this task

Merging with the shared head or W158 (Root resolves those five hunks), the W128 checkpoint cost (an existing open
performance item), admission or I6, ADRs, the risk register, `build.gradle`, status files and the ledger (Root).
