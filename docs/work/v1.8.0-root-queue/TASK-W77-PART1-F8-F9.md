# TASK W77: W11b2 part-1 findings F8 and F9 (open before W11b3 admission)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w77-part1-f8-f9` (from the shared branch) |
| Branch | `codex/v1.8.0-w77-part1-f8-f9` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-review/REVIEW.md` (SHA-256 `8c1da51138a655581c8a3256841615fd556c627eaed46edcf421b4fcc67de499`), F8 and F9 |
| Context | ADR-065, the paragraph "Open before W11b3 or any admission"; the W11b3 design note, section 1 (branch `codex/v1.8.0-w75-w11b3-design`, `docs/work/v1.8.0-root-queue/records/W11B3-DESIGN.md`) |
| Record | `docs/work/v1.8.0-root-queue/records/W77.md` (new) |

W11b2 is still being built on its own branch (`codex/v1.8.0-w11b2-rocket-integration`). Its branch does
not touch `RocketDisassemblyService` and changes one line of `RocketTransferRecoveryService`. Keep your
change small and inside these two findings, so that Root can merge both branches.

## Write first: a one-page design section in your record

These are transfer-journal and reservation changes (AGENTS.md section 13), so before code, write in
`records/W77.md`, for each finding: the current order of writes and releases (cite lines), the new
order, each crash point and what a restart finds there, and the tests. Then implement.

## F8: the landed reservation is released before disassembly runs

`RocketDisassemblyService` (review: lines 136 and 164 at the time) releases the landed reservation
before `execute`. If disassembly then fails, the arrival has lost its reservation. The base released it
only after a successful disassembly, and the first attempt now always answers REGION_BUSY.

- Keep the claim through a failed disassembly. Release it only after a successful one.
- A successful restoration must fence recovery of the old transfer before the checked removal and the
  release, so that recovery cannot recreate the rocket over the restored blocks.
- Tests: an obstructed or rolled-back disassembly keeps the claim; a successful one releases it once;
  the first attempt no longer answers REGION_BUSY when nothing is busy. Add a GameTest if the unit
  level cannot reach the real service.

## F9: a failing respawn rewrites the journal on every recovery attempt

`RocketTransferRecoveryService` (review: line 340 and its continuation) rebinds and writes the journal
(`journal.put(resumed)`) before the deferred `addFreshEntity`. When the spawn keeps failing, each pass
creates a new destination UUID and another full checked journal write. The base answered RETRY_LATER
without writing.

- Retry the one acknowledged identity and phase. No new UUID and no new journal write while the spawn
  keeps failing.
- Tests: repeated spawn rejection leaves the journal write count, sequence and destination UUID
  unchanged; a later success completes once; a restart in between does not duplicate the rocket.

## Rules

- Do not change an acknowledgement order, a proof, a hold or the replay exclusion of the W11b2 design
  (`records/W11B2-DESIGN-2.md` on the W11b2 branch). If a fix needs that, stop and report.
- AGENTS.md section 5: no weakened assertion, no longer timeout.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- F3 (per-record scheduling) and N2 (`failBackToSource`): they touch the code W11b2 is changing now and
  follow W11b2's merge.
- Production admission, the loader, ADRs, the risk register, status files and the ledger.
