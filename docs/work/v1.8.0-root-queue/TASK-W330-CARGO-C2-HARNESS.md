# TASK W330: cargo harness additions for the next candidate (C2)

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w330`, branch `codex/v1.8.0-w330`, created by Root from the shared head (W326's wrapper merged) |
| Decision | Root decisions R394 and R395 (QUEUE row W330), from W326's result `records/W326.md` and W327's audit `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md` (section 4) |
| Record | `docs/work/v1.8.0-root-queue/records/W330.md` and the cut list `records/W330-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W330/` |
| Design to follow | `records/W11CD-DESIGN.md` section 6 (the cargo cut and A1 tables), `records/W326-MATRIX.md` and `records/W326.md` |

## Why

W326 ran all 120 cargo cuts on candidate C1 (all PASS), but these rows stayed NOT_COVERED because C1 has no fixture
for them. A production fix (W328, W329) already requires a new candidate C2, and every case then reruns on C2 (R386),
so the missing fixtures are built now and run there. Production admission stays off throughout: every new fixture is
a gated test probe or GameTest, reachable only on a GameTest server or a gated release-test server, as the existing
cargo probes are.

## What to do

1. **Cut list first** (`records/W330-DESIGN.md`, committed before the code): for each item below, the cases, the
   boundary each cut stops at, the oracle (what must hold after each of the two restarts) and the runner family it
   joins. Derive the cases from W11CD-DESIGN section 6; where the design has no row, say so and use the closest
   accepted analogue.
2. **Native per-owner cap, one case per kind (R394, W326 rows Q01 to Q04).** For each of the four cargo kinds: one
   owner holds 16 pending operations (the per-owner cap of the shared 64/8/16 pool); that owner's cargo attempt in
   this direction is refused with no resource moved and no ledger or device change, across both restarts; then an
   independent owner's attempt is admitted. Build the gated cap fixture the runners need (W238's
   `W238OwnerPendingTest` is the unit-level reference; W325 seeded capped receipts for the bay in
   `scripts/run_v180_bay_forced_stop.py`, which you may read but not edit).
3. **The four hosted A1 gaps (R394, rows C-E-A09, C-E-A12, C-E-A13, C-E-A14)** as GameTests with real cargo devices:
   fluid ABANDON proving the missing device and the surviving rocket (the item twin is
   `ItemCargoSettlementGameTests.abandonProvesTheMissingDeviceAndSurvivingRocket`); real cargo with a stale PREPARED
   record and both physical entities, including the protected-keeper and mandatory-snapshot-identity refusals; a
   disembarked passenger's reconnect after cargo; and exact-source cancellation after cargo.
4. **Load-direction settlement, native (R395).** C-S4 and D-S2 cover settlement in the unload direction only, so
   item load has six native cuts and no settlement cut. Add native settlement cuts in the load direction for fluid
   and item, mirroring the C-S4 and D-S2 boundaries that apply to a load (for example O1 to O4, pruning, binding, the
   veto and both ABANDON variants); the cut list says which apply and why the others do not.
5. **Shared-infrastructure reuse (R394, rows M02, M05, M07, M08).** Make the integrated wrapper able to run the
   existing fuel-loader native cases that cargo shares (B3-S1 N0, N2 and N3; B3-S4; B3-S5 missing, replaced and
   behind ledger and distinct and coalesced floors; B3-S6; P1-N2; P1-F9; G2) on a candidate, from
   `scripts/run_v180_loader_cuts.py` and `scripts/run_v180_loader_s6.py`, without changing their oracles, timeouts or
   fixtures.
6. **The wrapper takes the candidate as an argument.** `scripts/run_v180_cargo_integrated.py` hard-codes C1. Make
   the candidate commit and JAR SHA-256 required arguments, recorded in each pin, and keep the production-equality
   check against that commit. Update `tests/test_cargo_integrated_runner.py` to match and cover the new families.

## Verification

- Unit tests for the wrapper and runners (`python -B -m unittest discover -s tests -p 'test_*cargo*'` and the loader
  runner tests); `./gradlew build` with all unit tests.
- The new GameTests alone, then the full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock
  (backslash lock path, as WORKER-RULES says; W325 and W329 also take it, so wait your turn).
- **A native smoke on a locally built JAR** (not a candidate): one cut per new native family, both restarts, to show
  that each fixture and oracle works. Show at least once per new family that the oracle fails when its protection is
  disabled in a scratch copy (for example the cap check), then discard the scratch copy. These smokes are not
  candidate evidence; the real runs happen on C2.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production behaviour and admission; the satellite bay's cases and runners (W325's area); the fuel loader's own
qualification; the risk register, the status file and QUEUE; running the full matrix on a candidate.
