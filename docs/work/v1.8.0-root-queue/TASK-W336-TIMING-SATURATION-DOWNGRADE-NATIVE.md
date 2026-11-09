# TASK W336: native cases for R-043 and R-044 (bay waits, shared saturation, downgrade)

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w336`, branch `codex/v1.8.0-w336`, created by Root from the shared head |
| Decision | Root decisions R392 and R404 (QUEUE row W336), from W327's audit `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md` (sections 2, 3 and 4) and the C1 report [INTEGRATED-C1-REPORT-20261109](records/INTEGRATED-C1-REPORT-20261109.md) section 7 |
| Builds on | W325's bay runners and `scripts/bay_integrated_evidence.py`, W330's cargo wrapper, and W176's downgrade harness (`records/W176.md`) |
| Record | `docs/work/v1.8.0-root-queue/records/W336.md` and the cut list `records/W336-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W336/` |

## Why

The owner asked Root to check R-043's waiting times and who owns each R-044 risk. The audit found that no run has
measured a bay deployment step: R-043's "about 36 minutes" is an estimate. It also found that the 256-coordinate save
refusal pool is shared by every device family in a Level, and that W176's downgrade failure is world-level. These cases
observe all three on a dedicated server. They are development-host observations, not the reference node's figures,
which the final campaign measures. Production admission stays off.

## What to do

1. **Cut list first** (`records/W336-DESIGN.md`, committed before the code).
2. **Bay waits, measured in ticks (audit section 2.3).** Bracket each case with the vanilla console command
   `time query gametime` at arming and after the terminal line, so that the result is in ticks whether or not the
   server keeps 20 TPS. Add no production instrumentation.
   - D6 proof turns: N gated deployments that reach D6 together; the game time at each claim's first
     `ARCE_BAY_CLAIM ... reason=BAY_PROOF_TURN` and at its `state=READY_SOURCE`; ticks per turn, and per slot when divided
     by the census size. Use at least two census sizes. A 64-claim, 34-slot run is not required.
   - The spacing of D6 retries (`ARCE_BAY_PROOF_REFUSED ... retry=`), of D3's REGISTRY_UNPROVED transitions and the
     registry lines (B13), and the D4 and D7 store waits (B12 and the B16_GUARD pause).
   - The reconnect expiry against `ARCE_PASSENGER_RECONNECT_EXPIRED`, and ticket expiry after READY_SOURCE or
     RELEASED_TO_ORDINARY by polling `execute if loaded` on the census columns once a second.
   - Unbounded waits are shown to persist through a chosen window and two restarts; they cannot be measured to a bound.
3. **The shared 256-coordinate pool (R-044, audit M5).** In one Level, fill the save-refusal pool with denials from
   another device family to 255 coordinates. Then show that one denial from each of the bay and the four cargo devices,
   in turn, saturates the Level, and that a terrain change in a chunk with no device is then not saved. Use the fuel
   loader as a control. Show what a restart does to the saturation. Build the denials from unsupported roots written
   into a stopped world, as the existing protection tests do.
4. **A downgrade with the bay and the four cargo devices placed (R-044, audit M4).** Repeat W176's downgrade row on a
   world that holds a satellite bay, the four cargo devices, bay receipts and an escrow ledger: open it with the old JAR
   W176 used, and record which files change (`level.dat`, the registry entries removed) and which stay byte-identical
   (device roots, receipts, ledger, terrain). Then restore the backup and show that the candidate JAR starts it cleanly.
   Keep W176's oracle; do not loosen it.
5. **The candidate as an argument**, as W330 did.
6. **A native smoke on a locally built JAR** for each case family, both restarts where the case restarts, and for the
   saturation family one run with the protection disabled in a scratch copy that the oracle rejects; discard the
   scratch copy. These smokes are not candidate evidence.

## Verification

- Python tests for every runner and oracle you add or change; existing runner tests unchanged.
- `./gradlew build` with every unit test if any Java changes; any new GameTest alone, then the full GameTest run with
  `scripts/check_gametest_log.py`.
- All heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), taken per step and released
  between steps. Call Python by its full path in any detached script.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production behaviour and admission; NOT_LOADED and the held pending pool (W332); reference-node or performance
measurements; the risk register, the status file and QUEUE.
