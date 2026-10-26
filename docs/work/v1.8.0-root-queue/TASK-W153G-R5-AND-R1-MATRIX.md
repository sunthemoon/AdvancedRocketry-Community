# TASK W153G: finish W153F (W153-R5 and the W153-R1 matrix, Root decision R102)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W153F](TASK-W153F-REVIEW-FIXES.md) (with R99),
the review (`D:/GitHub/ARCE-Task-Evidence/root-queue/W153-review/REVIEW.md`) and the W153F section of
`records/W153.md` on the branch first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w153` |
| Branch | `codex/v1.8.0-w153` at `669c9b57` (local commits only; Root merges) |
| Record | append a "W153G" section to `docs/work/v1.8.0-root-queue/records/W153.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W153G/` |

## Root decision R102 (2026-10-24)

1. **W153-R5 stays on protocol 2.** `classic_controls` protocol 2 and the kind-4 layout exist only on this unmerged
   branch, so amending kind 4 before merge changes no merged or released contract. The monitor's three required-cost
   fields get minimum -1, meaning unavailable; every other range and kinds 0 to 3 are unchanged. Root updates ADR-065's
   R92 note. A refused quote fills each known bank cost from a bounded, non-authoritative required-cost vector and
   sends -1 for an unknown one. The screen shows -1 as unavailable, never as zero. Golden bytes and the protocol pin
   cover the new minimum.
2. **The W153-R1 matrix.** Add GameTests that revoke a live station membership at each exact cut: before PREPARED
   submission (refused, no PREPARED, no debit) and after it (launches, reported as launched, arming invalidated).
   R99's cutoff adds no persisted state. A restart after the cutoff resumes the existing governed PREPARED recovery,
   which earlier slices already verified natively. No packaged-server restart is required for this finding; the
   journal-reload unit test stays.

## Verification

Each change's tests alone (failing before the change), the whole unit suite, runData twice with a clean tree, and one
complete GameTest with the unchanged log checker, under the heavy-JVM lock with the atomic pattern and the 90-second
hand-off rule. Check C: and D: above 10 GiB and 6 GiB free memory first. The W153 reviewer re-reviews afterwards.

## Not in this task

Anything outside W153-R1 and W153-R5, ADRs, the risk register, status files and the ledger (Root).
