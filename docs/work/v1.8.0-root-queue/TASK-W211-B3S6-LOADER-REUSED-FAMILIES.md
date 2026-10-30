# TASK W211: B3-S6, real fuel-loader evidence for the reused families

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w211` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w211` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W211.md` (new; the matrix and cut design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W211/` |
| Plan | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md` (SHA-256 `0667fe44…8f555e`), section 3, "W203-P2", with section 1.2's rows for the existing evidence; its write scope, tests and exclusions are part of this TASK |
| Background | the plan's sources S7 (`records/W11B3-DESIGN.md`, sections 5 and 6) and S1 (`docs/status/CURRENT_VERSION.md`, "What is still open"); R-035 in `docs/11-RISK-REGISTER.md`; the records of W97, W105, W107, W125, W130, W133, W176 and W204 (N3-C, which supplies the N2 representative) |

**Root decision R182.** N3-C (W204) is merged, which unblocks W203-P2. B3-S6 needs a real fuel-loader representative
for each reused family that S7 names: E3, E5, R2, O1 to O4 (including O3J), O-veto, G1, G3, and D2-F0 to F8 (with
F8/N2's crash windows, the failed respawn F9, and refill only after F8). Much of this has evidence already; the task is
to map it honestly and run only what is missing.

## What to do

1. **Matrix and cut design first, committed before any native run:** for every required representative, the existing
   evidence (record, commit, case, restarts) that covers it with a real loader, or "missing", or a labelled residual
   (E4's unobservable failures stay residual). A chest, armour stand or cargo device is not a fuel-loader
   representative. Map W176's applicable G1 and G2 rows without turning its three failed downgrade rows into passes.
   For each missing row: the cut sides (around durable replacement and acknowledgement), the two restarts, the oracle
   checks and the expected outcome. Commit a mapping and count ledger before the first launch.
2. **The oracle and runner** (`scripts/run_v180_loader_s6.py`, `scripts/loader_s6_oracle.py`,
   `tests/test_loader_s6_oracle.py`): the oracle rejects a changed quantity, marker, epoch or identity and an UNKNOWN
   read as zero, with tests that fail first. Observation-only additions to `EscrowLoaderReleaseProbe` or
   `EscrowLoaderFollowUpProbe`, or new test fixtures, only if the existing hooks cannot observe a row; name each in the
   record. Existing loader and escrow runners are read-only imports.
3. **Run the missing cuts natively** under the heavy-JVM lock, in counted tranches, on a JAR built from your committed
   source: each cut with two restarts; include loader fairness behind held or unloaded records, shutdown with queued,
   in-flight, UNKNOWN and failed-close writes, a failed respawn, and successor refill only after F8. Keep exact mono and
   two-loader totals and the required bipropellant representatives. Stop and report if a row fails twice; a production
   cause is reported for a separate fix, never hidden in a fixture.

Other workers use the same lock (W209 now); wait for it, never remove it, and release only your own.

## Verification

The oracle tests failing first; the new unit tests alone and the whole unit suite; the loader and escrow batches that
the matrix names; the native tranches with their counted starts and failures. Root's hosted run on the pushed branch
stands in for a complete local GameTest. An independent review of the matrix, the oracle and the results follows.

## Not in this task

The bay's B1 to B18, C or D acceptance by analogy, the settlement accounting schema, a forced save to repair an oracle,
escrow writer or recovery logic, any production change, enabling admission, budgets, timeouts, ADRs, status files and
the ledger.
