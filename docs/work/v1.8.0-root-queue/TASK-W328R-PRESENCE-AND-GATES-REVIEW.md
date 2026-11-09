# TASK W328R: independent review of W328 (unknown file presence) and W329 (protective escrow reads)

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write these changes. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Changes under review | Branch `codex/v1.8.0-w329` at `237424e33`, checked out read-only and detached at `D:/GitHub/arce-v180-w328r`. It holds W328's commits `000ae1854` to `41ebd5485` (on base `369383c26`), a Root merge `f847da444` that only brings in docs, and W329's commits `f3f102c3c` to `237424e33`. W329 may add its record and test fixes after `237424e33`; Root reviews that delta |
| Implementers' records | `docs/work/v1.8.0-root-queue/records/W328.md` and `records/W328-DESIGN.md`; `records/W329-DESIGN.md` (W329's record is not written yet). Logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W328/` and `W329/` |
| Tasks they implement | [TASK-W328](TASK-W328-PRESENCE-UNKNOWN.md) (Root decisions R390 and R391) and [TASK-W329](TASK-W329-LEDGER-NOT-LOADED-GATES.md) (R393), from W327's audit `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md` |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W328R/REVIEW.md`, plus your logs in that folder |
| Why a review | Both change startup refusal and save or work refusal for persisted data (AGENTS.md sections 3.3 and 13) |

## What to check

1. **Pre-start check (W328, `WorldDataMigrationService`, `BetaWorldMigrationEvents`).** Every managed file and the
   `data` directory: only a proven absence is skipped, and any other status failure refuses startup before anything
   is read, backed up, staged or committed. Check the order of checks: when the `data` directory's status is unknown,
   the rejoin file is checked first, so the refusal may carry the rejoin hint; is the hint the operator sees then
   right, and is it safe? Check the dangling-symlink case and that a later clean start proceeds over the same bytes.
2. **Escrow ledger startup (W328, `EscrowLedgerStartup`, `EscrowRuntime.starting`).** NO_FILE only for a proven
   absence. Under NOT_LOADED, confirm that nothing in the session can create, replace or reconcile a ledger (creation,
   fills, cargo admission, the scheduler, settlement, pruning), and that the WARN is one line per start.
3. **Bay receipts WARN (W328).** The two reasons; the NOT_LOADED policy itself (R390) must be unchanged.
4. **Protective reads (W329).** Check the inventory in `W329-DESIGN.md` section 1 for completeness: every production
   read of `ResourceEscrowSavedData.existing`, and any other way production code reaches the ledger (another lookup, a
   cached reference, a coordinator or index that outlives the root). For each site, is the classification right? Is a
   site missing whose null answer still permits work that the ledger could forbid?
5. **`EscrowLedgerLookup` (W329).** LOADED, NO_FILE, NOT_LOADED and UNCLASSIFIED: is each classified correctly,
   including a LOADED delegate without a root, off-thread reads and the window before `ServerStartingEvent`? Is the
   design's claim right that UNCLASSIFIED keeping today's answers is harmless (spawn-chunk entity joins, journal fence
   classification, the release manifest filter)?
6. **No stuck state or noise under NOT_LOADED.** No tick path throws (for example `updateFlightData` while
   `flightFrozen`); a player can still dismount; no unbounded retry or log line; the fence WARNs stay within the
   design's bounds.
7. **NO_FILE is unchanged.** On a world without a ledger, which is every production world while admission is off,
   every changed read gives today's answer. This matters most.
8. **Tests.** The new tests cover the points above and fail without their fixes; the test seam
   (`Delegate.unloadForTests` or whatever it is named) cannot be reached by production code; the two new GameTest
   batches restore the server-wide state after they run.
9. **Register text.** Are the proposed risk-register texts in both design notes (section 5 of each) accurate?

Run, in your checkout, the new and affected unit test classes (`persistence.migration.*`, the new `resourceescrow`
startup and lookup tests, `resourceescrow.EscrowAdmission*`). Under the heavy-JVM lock (backslash path from
WORKER-RULES: `D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`; W325 takes it per native run, so wait your turn), run
the `w329_ledger_not_loaded` and `w329_ledger_no_file` batches and the escrow and bay startup batches once, if the
build supports a batch filter as earlier reviews used; otherwise say so. A full GameTest run is not required. Work in
your own Temp folder for any scratch export, and delete it when you finish.

## Report

Write REVIEW.md in English: a verdict for W328 and one for W329 (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED),
each finding with its severity, the file and line, what is wrong and what would fix it, your conclusion on each point
above, and the commands you ran with their results. The last line of your answer is the REVIEW.md path.
