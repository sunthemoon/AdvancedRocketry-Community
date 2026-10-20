# TASK W107C: the loader cuts E1, E2, R1 and R3 under Root decision R43

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W107B](TASK-W107B-REMAINING-LOADER-CUTS.md)
(Root decision R36), [records/W107B.md](records/W107B.md) (especially "Coverage findings for the S4/S5 families"),
[records/W107.md](records/W107.md), [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) section 5 and
[records/W11A-DESIGN.md](records/W11A-DESIGN.md) section 7 (the cut-E and cut-R rows) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w107c` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w107c` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W107C.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W107C/` |
| Base harness | `scripts/run_v180_loader_cuts.py`, `scripts/loader_cut_oracle.py`, the gated `EscrowLoaderCutProbe`, `EscrowLoaderStorageProbe`, `EscrowLoaderRecoveryProbe` and `EscrowReleaseProbe` |

## Root decision R43 (recorded here, 2026-10-20), on W107B's five findings

1. **E1, superseding store:** covered by W107's native `E2_CAPTURE` cut. Closed; no new work.
2. **E1, own-store IO failure:** one gated, test-only fault seam is authorized at the barrier's own-store future
   (`EscrowChunkStore` or the call that creates the future), outside the release-probe family, because no other seam
   exists. It completes that one future exceptionally for the fixture's slot only when both the release-test hook
   property and its case property are set; it never changes a decision, an order, a saved format, a proof or a lease,
   and a unit test shows it is inert with the gate off. Expected outcome: `BARRIER_IO`, no L2 and no unlock, then
   recovery on restart.
3. **E2:** W107B's reason for not running it assumed chunk NBT always compresses well. Incompressible data does not: a
   gated fixture may add random bytes, within the barrier's 4 MiB decoded bound, to a fixture entity's or block entity's
   data in the captured chunk, so that vanilla writes it as an external `.mcc`. Expected outcome: `BARRIER_EXTERNAL`.
   If vanilla, the capture bound or another check refuses first, record exactly what happens; that observation, plus
   `AnvilRecordReaderTest`, closes E2. The over-bound decoded case stays covered by the capture cap and the unit test.
4. **E4, stale writer callback:** an in-process invariant with no restart-observable state (the R-032 class), so it is
   not an S2 cut. Add a unit test that pins `EscrowLedgerWriter.tick`'s "Stale or foreign writer callback" refusal if
   none exists; record it as a residual, not a PASS.
5. **R1 and R3** proceed exactly as R36 item 2 authorized: a gated fixture that seeds 255 valid floors and a named
   journal transfer for the fixture rocket (R1: 255 and 256 floors, distinct and coalesced keys), and offline edits
   of the disposable server copy between the stop and the restart (R3: the ledger removed, replaced or behind; a loader
   endpoint foreign, missing, OTHER or relocated), each recorded with before and after SHA-256. Add the recovery
   observation branch and oracle expectations each case needs (a saturation refusal, a hold, a repair reason or an
   orphan state), separate from the settlement-expecting `recover()`.
6. Unchanged from R36: R-032's boundary, no historical fallback, no forced chunk loads by recovery, two restarts per
   cut, raw native comparisons against the control totals, and no relaxed oracle, limit or timeout.

## Verification

The new and changed unit and Python test classes; one full GameTest with the unchanged log checker on committed source;
then each cut natively, two restarts each, under the heavy-JVM lock (take and release it per cut or per batch, so other
workers can run), releasing the lock and deleting every disposable server copy and `build/gametest/world` in the
`finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above 10 GiB first, and that 6 GiB of
memory is free before each Gradle run or native start.

## Not in this task

B3-S6, production admission, any change to production behaviour beyond the gated seams above, ADRs, the risk register,
status files and the ledger (Root).
