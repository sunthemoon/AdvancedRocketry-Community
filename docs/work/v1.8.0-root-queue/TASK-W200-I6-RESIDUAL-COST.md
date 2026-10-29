# TASK W200: the I6 costs still over docs/17 after W199

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w200` (new; from the shared head named in your prompt, which includes W199) |
| Branch | `codex/v1.8.0-w200` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W200.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W200/` |
| Background | `docs/work/v1.8.0-root-queue/records/W197.md` and `records/W199.md`; the W199 review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W199-review/REVIEW.md`, its section "Remaining 138.5663 ms work tick"; [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) |

After W199, two worst-case ticks still exceed docs/17's 100 ms:
- **the admission burst (138.57 ms):** 64 `entry.confirm` calls in one tick, through `RocketI6Entry.begin`,
  `RocketDisassemblyService.preflight` and `RocketDisassemblyTransaction.preflight` (adapter restoration checks and the
  snapshot occupancy walk);
- **the write-back tick (341.16 ms):** write-back callbacks encode the candidate for validation and again in `put`,
  with a GC pause overlapping.

## What to do

1. **Design note first.** For the burst: a global admission and preflight work allowance, or a resumable bounded
   cursor, so a burst of confirmations spreads over ticks. Deferred work must not gain destructive authority, refresh
   consent, skip preflight, force chunks or reset the 200-tick request age, and it must revalidate identity,
   authorization and world state before use. For the write-back tick: reuse the checked encoding from validation in
   `put`, within W199's encodability witness, so the candidate is encoded once. Say what each changes for a player.
2. **Implement both**, each in its own commit with a test that pins the new bound and fails first.
3. **Measure** with W197's harness through the same command entry point (a real burst, not 64 commands spread by the
   harness), before and after, under the heavy-JVM lock, and compare with docs/17.

No budget, proof bound, request age or timeout changes.

## Verification

- the new tests alone, failing first; the whole unit suite;
- the I6 and sealed batches three times; W197's measurement before and after.

Root's hosted run on the pushed branch stands in for a complete local GameTest. An independent review follows.

## Not in this task

The terrain matrix (W198), other performance work, ADRs, status files and the ledger.
