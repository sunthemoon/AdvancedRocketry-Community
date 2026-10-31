# TASK W220: a failed or vetoed governed destination publication with a real loader

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w220` (new; from the shared head named in your prompt, which contains W211) |
| Branch | `codex/v1.8.0-w220` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W220.md` (new; the cut design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W220/` |
| Finding | W211-R1 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W211-review/REVIEW.md` (SHA-256 `8456bc10…0d65d580`) |
| Code | `resourceescrow/EscrowGovernedDestination.java` lines 177 to 205 (a null `create` gives `CREATE_FAILED` or a held retry; `addFreshEntity` returning false gives `SPAWN_FAILED` or HELD, by `EscrowGovernedReturnAction.phase`); `EscrowGovernedLiveReturnGameTests` (lines 397 to 428, the existing synthetic-bank coverage) |
| Harness | W211's S6 harness on the shared head: `scripts/run_v180_loader_s6.py`, `scripts/loader_s6_*.py` and the `EscrowLoaderS6*` probes (especially `EscrowLoaderS6FlightProbe`), with R190 to R205's rules in [TASK-W211](TASK-W211-B3S6-LOADER-REUSED-FAMILIES.md) |

**Root decision R207.** W211's final review found that the purpose of B3-S6's F9 (a failed destination publication
and its recovery) is not covered for governed flights: W211's F4 rows pause around a successful publication, and its
ordinary F9 form is out of reach for loader-funded rockets (R205). This task supplies the governed analogue with a
genuinely loader-funded governed flight.

## What to do

1. **Cut design first, committed before any native run:** a real loader fill, then a governed flight whose destination
   publication fails in a product-reachable way, for each phase the code distinguishes: a refused `addFreshEntity`
   (a spawn veto, as another mod can cause by cancelling Forge's entity join event) in a return-eligible phase and in
   a held phase; and, if it can be reached without bypassing the product, a null entity creation. Name the mechanism
   for each (a release-gated listener that cancels the join event for exactly the selected rocket is acceptable, the
   way an add-on would; no change to the executor's decisions). For each row: the stops, two restarts, and the oracle
   (the fixed transfer and destination identity, the complete typed debit and authority, the source and its
   envelope, the return or the held retry, and later recovery to a completed state).
2. **The fixtures and oracle**, test-side, with tests that fail first, reusing W211's S6 oracle (UNKNOWN never read as
   zero, record-backed held amounts, complete snapshot and typed fuel across restarts).
3. **Run natively** under the heavy-JVM lock, on a JAR built from committed source: each row with two restarts. R190
   and R201's renewal rule applies; a production cause or an unexplained image stops for Root.

## Verification

The new tests alone, failing first; the whole unit suite; the loader S6 and escrow Python tests; the escrow governed
return GameTest batches; the native rows. Root's hosted run on the pushed branch stands in for a complete local
GameTest. An independent review follows.

## Not in this task

Governed named pads (W221), the ordinary F9 form, production changes, budgets, timeouts, ADRs, status files and the
ledger.
