# TASK W158F: fix the W158 review findings (W158-R1 and W158-R2)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W158](TASK-W158-W80-RELEASE.md) (with R104),
W158's record (`records/W158.md` on the branch) and the review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W158-review/REVIEW.md`, SHA-256 `3cc42c6d…a34b`, verdict BLOCK) first. You are
a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w158` (W158's; it has finished) |
| Branch | `codex/v1.8.0-w158` at `f4415ba4` (local commits only; Root merges) |
| Record | append a "W158F" section to `docs/work/v1.8.0-root-queue/records/W158.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W158F/` (W158's evidence stays as it is) |

## What to fix (each in its own commit)

1. **W158-R1 (High): kill the first recovery.** Add a test-gated recovery cut that can be selected on its own and that
   is armed for restored batches, which enter at S1, not B0. Kill the first recovery externally at the selected release
   boundary, inspect the world, and restart it. Cover at least: reacquiring B1 from the persisted census after J1
   removed T (the C5 continuation), and the recovered S2 write while it is pending. Apply it to C2 to C6 where they
   apply. Keep the existing clean-restart cases and their exact log checks. Do not increase timeouts or relax any
   check. Assert the resource and seat census at each cut.
2. **W158-R2 (Medium): board for real.** Add an integration GameTest through the real boarding and mounting boundary:
   - a new assignment is refused while the release fence is held;
   - the same player in the same seat is accepted after the acknowledged S2;
   - after a save and reload, that new assignment survives, while the pre-release seats in the other recorded copies
     and in T stay revoked;
   - a retained passenger the release does not name leaves.

   No placement, login protection or command (W80-rejoin). Correct the W158 record's coverage claim with an additive
   note.

## Verification

Each fix's tests alone (failing before the fix where that applies), the runner's Python tests, the whole unit suite, the
new native cuts with their restarts, and one complete GameTest with the unchanged log checker. Heavy runs go under the
heavy-JVM lock with the atomic pattern and the 90-second hand-off rule. Check C: and D: above 10 GiB and 6 GiB free
memory first. Keep the evidence under 100,000,000 bytes. The W158 reviewer re-reviews afterwards.

## Not in this task

W80-rejoin, governed records, merging with W152 (Root resolves those conflicts at integration), ADRs, the risk register,
status files and the ledger (Root).
