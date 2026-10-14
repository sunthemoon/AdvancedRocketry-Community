# TASK W11b2, continuation 03: off-thread journal writes, then finish the integration

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session. Your context is
the W11b2 record, [records/W11B2.md](records/W11B2.md) (at `e22544cc`). Read it in full,
including the Continuation 01 and 02 sections.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (continue on top of `e22544cc`) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Record | Add a "Continuation 03" section to `records/W11B2.md`. Keep the existing text |

## 1. Root decision: the checked journal write leaves the server thread (first)

**The problem.** Continuation 02 measured each checked transfer-journal write on the server
thread at 86 to 122 ms in the 8-transfer cycle, with 48 writes per cycle. Launches and phases
would therefore exceed the docs/17 tick budgets (P95 50 ms, P99 100 ms) in normal play.

**Root's decision (round-1 design review F6 also applies to the journal):**
- **Writer.** Every checked `ROCKET_TRANSFERS` write goes through one bounded off-thread
  writer, which works on immutable snapshots. Reuse W11b1's ledger writer machinery or its
  pattern, with generation ordering and exact snapshot acknowledgement. Completion is posted
  back to the server thread.
- **Server thread.** It only captures the snapshot. Encoding, digest, staging, force, readback
  and replacement run on the writer.
- **Dependent effects.** They wait for the acknowledgement of the exact generation, as they do
  now. Failed or unknown outcomes keep authority, under the nine-site table.
- **Autosave and explicit commits** use the same writer. Drain the writer at shutdown, as
  W11b1F fixed for the ledger.
- **The capture of a maximum-size journal** must cost no more on the server thread than the
  existing vanilla save of the same journal. Record both.
- **Measure.** Re-run `RocketJournalBenchmark`, splitting main-thread cost from writer time.
  The 8-transfer cycle's main-thread p95 must be inside the docs/17 budgets. Never drop a force
  or readback, and never raise a budget.

## 2. Then finish the record's open items

- **Credited-rocket reconciliation (real rockets).** Close "credited-world recovery remains
  unsafe", or state exactly why it stays unsafe.
- **Durable operator settlement and tombstones.** `accept-current` and `abandon` never release
  an unproved credit. Run forced-stop cuts before and after each acknowledgement.
- **The full native matrix** the record lists: R1 to R3, O1 to O4 and the O-veto, G1 and G2,
  the failed committers, collection, and the repeated integrated K and C cuts. Each runs with
  two restarts, pinned to the source and jar.

## Cleanup

Root removed the generated test world. Use plain commands that end by themselves. If a
deletion is rejected, record it and continue.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker.
- The journal benchmark, and the native cuts above, with evidence under 100 MB.

## Not in this task

- W11b3 (`fluid_fuel_loader`, the linker) and production admission.
- ADRs and the risk register (Root).
- Status files and the ledger.
