# TASK W11b2, continuation 11: fix the continuation 09 review findings

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md),
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) and [TASK-W11B2-CONTINUE-10](TASK-W11B2-CONTINUE-10.md)
first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `6193d339` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/REVIEW.md`, SHA-256 `c5e829e233128d4029213579a5296d6ab42a2f8c6bb7a3eaf924c1710760f8c6`. Verdict FIX-FIRST |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md`, SHA-256 `2a87993811b2889b7c2a062465a4614632ef1c8c862e16d301f9d4b2f8ab9869` |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`) |
| Record | Add a "Continuation 11" section to `records/W11B2.md`. Keep the existing text |

This continuation does **only** the review fixes. Continuation 10's flight-authority checkpoint
(`79a4757b`) stays as it is, except where a fix touches it. Steps 3 to 7 of design 2 continue in
continuation 12.

## What to do

Read the review and Root's decisions in full. Then fix each finding as the decisions say, each in its
own commit with its test, in this order: H1, H2, M4, M1 with M2, M3, M5, L1, L3, then I1 and I4 if they are
cheap.

- **H1:** a two-slot native floor settlement reaches O4. Rerun the native SR_FLOOR cuts with two
  restarts each, pinned to the committed source and its jar.
- **H2:** the settlement-runner Python oracle passes with its requirements unchanged. Run every Python
  test that imports a changed script.
- **M1 with M2:** the admission limit and the reservation.
  - Find the largest per-rocket pending count whose worst-case settlement fits, counting retired
    markers. Do this by measurement, with a test that pins it: n passes, n+1 is refused.
  - Keep `EscrowLimits.PER_ROCKET` (8) as the decode bound.
  - State the measured number in the record.
- **M4:** the baseline readback moves into the pre-start managed-data check. Show both paths:
  - a pre-start refusal that leaves the file untouched;
  - a later failure that marks escrow unavailable without throwing through a tick.

  Each path logs one bounded line.
- **M3, M5, L1, L3:** as the decisions say, each with a unit test.

If a fix needs a change to an acknowledgement order, a proof, a hold or the replay exclusion, stop and
report. Do not change any byte, node or depth bound.

## Commands

- The whole unit suite once, on the final committed SHA.
- `runGameTestServer` under the lock on a **committed** SHA, then the unchanged log checker. N1's three
  warnings may remain; nothing else may.
- The native SR_FLOOR cuts (H1) under the lock, with evidence under 100 MB in
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C11/`.
- At the end, delete your generated `build/gametest/world` and Temp copies with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- Design 2 steps 3 to 7 beyond what a fix needs (continuation 12).
- Production admission, W11b3, the bay slices, W27f reconstruction, and signed per-fluid accounting.
- ADRs, the risk register, status files and the ledger (Root writes the R-035 rows and the ADR-065 text
  that the decisions list).
