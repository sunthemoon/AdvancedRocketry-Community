# Integrated test report on candidate C1: the satellite bay and the four cargo kinds

Root: Claude. Date: 2026-11-09. This answers the owner's request of 2026-11-09 recorded in
[OWNER-DECISIONS-20261109](OWNER-DECISIONS-20261109.md): test the bay and the four cargo kinds together on one candidate
JAR, and report each scenario as PASS, FAIL or NOT_COVERED with the remaining work, plus the checks of R-042, R-043 and
R-044.

**What this report is not.** It approves no Gate, version or release, accepts no risk, and enables nothing. Production
admission stayed off for the bay, all four cargo kinds and the fuel loader throughout. The per-switch risks and
evidence for the owner's decision come after the rerun on the next candidate (section 6).

## 1. The candidate and the sources

| Item | Value |
|---|---|
| Candidate C1 | The hosted build of run 42499122646 at `70dd5d43e`, SHA-256 `fa5ec1812995ce3cca9d74e447301c66b6d5c9117789c2daae71cac4598e4832`. Every native case ran this JAR; no local build was substituted. |
| Bay | W325, record [W325](W325.md) and matrix [W325-MATRIX](W325-MATRIX.md); evidence `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/bay/` (manifest SHA-256 `c08b1926…de53f`) |
| Cargo | W326, record [W326](W326.md) and matrix [W326-MATRIX](W326-MATRIX.md); evidence `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/cargo/` (manifest SHA-256 `d18cdf53…07ad`) |
| Risk checks | W327's read-only audit `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md` (0 High, 6 Medium, 5 Low) |
| Hosted GameTests | Run 42499122646 passed all 1,802 GameTests at C1's commit. A GameTest row is PASS only where a named test covers it. |

Every native case ran with two restarts. A PASS establishes only its named row. Unit tests and nearby native cases
were never counted as a GameTest or native PASS.

## 2. Summary per switch

| Switch | Native on C1 | Hosted GameTests on C1 | NOT_COVERED | FAIL |
|---|---|---|---|---|
| Satellite bay (`loader/1`) | 84 cases cover 88 rows: all PASS | 13 rows PASS | 27 native rows, 31 GameTest rows | 0 |
| FLUID_CARGO_LOAD (`loader/5`) | 44 cuts PASS | 6 rows PASS | 1 GameTest row; the per-owner cap (Q01); no native load-direction settlement cut | 0 |
| FLUID_CARGO_UNLOAD (`loader/4`) | 46 cuts PASS | 9 rows PASS | 3 GameTest rows; the per-owner cap (Q02) | 0 |
| ITEM_CARGO_LOAD (`loader/3`) | 6 cuts PASS | 6 rows PASS | The per-owner cap (Q03); no native load-direction settlement cut | 0 |
| ITEM_CARGO_UNLOAD (`loader/2`) | 24 cuts PASS | 11 rows PASS | The per-owner cap (Q04) | 0 |
| All four cargo kinds | — | — | The fuel-loader cases that cargo shares (M02, M05, M07, M08) were not rerun on C1 | 0 |

The cargo GameTest counts overlap: one test can serve several kinds. No case failed because of production code.
Every failed attempt was a harness or fixture defect, kept with its evidence, corrected test-side, and rerun
(section 3).

## 3. The satellite bay (W325)

159 requirement rows: **101 PASS, 0 FAIL, 58 NOT_COVERED.** 94 native attempts led to 84 passing cases. Five
attempts were rejected and rerun:

- the capacity fill used one owner, which the per-owner cap of 16 correctly limited;
- a rider's random spawn moved player tickets;
- an oracle wrongly required the journal to be removed for an offline crew, which the code keeps waiting;
- a D7 fixture aimed at a stall that never reaches D7;
- four seeds whose first start logged a world-generator ERROR, caught by a new first-start ERROR audit.

Root checked the corrections: the spawn fix changed no ticket assertion, the capacity seeding respects the 64, 16 and
8 caps and claims only loading and refusal, and the new offline-crew outcome is stricter than before and matches
`RocketOrdinaryRetirement`'s `CREW_WAITING` wait.

**Native rows not covered (27).** They need fixtures that C1 does not contain:

- B4: a decommission before the first kill, and online cuts without a data satellite (B4.3, B4.6 to B4.10);
- B6.5: explicit deletion and reconstruction at the destination;
- B6.8, B7.3 and CAP.3: covered by GameTests, but not natively;
- B8.7, B8.8 and B17.3 to B17.5: groups of several packages, partial groups and several bays;
- B10.6 and B11.2: released or pooled operations with conflicting authority;
- B11.7 to B11.9: the admission union across consumers, release and re-arm of capacity, and a mixed fill;
- B14.3 and B14.4: request refusal natively, and a stale transaction record;
- B15.3 to B15.5: an exception thrown after a replace;
- B18.6 and B18.7: a stop with a successor, and a stop while UNKNOWN is active.

W331 builds these (section 6).

**GameTest rows not covered (31).** F2.2 to F2.8, F3.2 to F3.7, F4.3 to F4.6, F4.8, F5.5 to F5.7, RT.4 to RT.7 and N2.1
to N2.6. Each needs a dedicated GameTest cross-product. Unit tests cover parts of them, but they do not count.

**Waiting times.** C1's logs have no paired start and end ticks for D0 to D7, the D6 proof turns or the claim queue, so
those durations are NOT_OBSERVABLE on C1. The safe-exit cuts did record ticks: 0 to 5 ticks from exit to closure, 13
ticks to a teleport revocation, and expiry at exactly 200 ticks.

## 4. The four cargo kinds (W326)

All 120 native cuts PASS: fluid load 44, fluid unload 46, item load 6 and item unload 24. The earlier residual cases
(E3, E5, R1_256, R3_CURSOR, F6, GOVERNED_TERMINAL, TWO_FLIGHTS, CANCEL, IR_CONTAINER, IR_LOOT) also pass afresh.

**Not covered:**

- **The per-owner cap, per kind (Q01 to Q04).** C1 has no gated fixture that fills one owner's 16 pending operations in
  a cargo direction. The shared unit tests (`W238OwnerPendingTest`) pass but are not native cargo evidence.
- **Four GameTest rows (C-E-A09, A12, A13, A14), all fluid.** Fluid ABANDON with the device missing; a real cargo device
  with a stale PREPARED record; a passenger's reconnect after cargo; and exact-source cancellation after cargo.
- **Shared fuel-loader cases (M02, M05, M07, M08).** The fuel loader's native cases that cargo reuses were not rerun on
  C1.
- **Load-direction settlement.** Native settlement cuts exist for unloading only; item load has six native cuts in all.
  Root decision R395 adds load-direction settlement cuts.

**Residuals for the owner, not accepted (R394):**

- X01: OS crash and power loss (a process stop does not simulate them);
- X02: cache and store behaviour that cannot be observed;
- X03: the operator's whole-world repair after REBUILD_SOURCE for fluid unload.

## 5. Problems found, fixed, and retested

The integrated work found two production problems. Neither was a failed case: the audit found the first, and the
first fix's design found the second. The fixes need a new candidate, C2, and the whole bay and cargo matrix reruns on
it (R386).

| Problem | Fix | State |
|---|---|---|
| The escrow ledger, shared by the fuel loader and all four cargo kinds, read a failed file-status check as "no file". A later first fill could then create a new ledger that might replace the real one (audit L2). | W328: every managed file's presence is checked before startup, and an unknown presence refuses startup (`ARCE-BETA-2000`) with nothing written. Bay receipts already refused safely; their warning now names the reason. | Delivered; full GameTest and unit suites pass. |
| While the ledger is present but not loaded, several read-only checks treated it as absent, so a rocket could pass as idle or unprotected. | W329: under that state each such check answers protectively (busy, protected, not neutral, locked, frozen). Worlds without a ledger are unchanged. | Delivered; 1,818 GameTests and 6,435 unit tests pass. |
| A cross-engine review of both (W328R) found no data-loss path, but the startup check reads files in the wrong order and some design text overstated things. | W328F: check every file's presence before reading any; storage-first refusal hint; the lookup's thread guard; corrected notes. | Delivered; 6,445 unit tests and the selected GameTests pass. A second cross-engine review (W328FR) approved W328 and W329 and found no production defect in W328F; one wording fix in the notes and two pinning tests follow (W328G, running), then a hosted run and the merge. |

## 6. Remaining work and estimates

| Work | Covers | Estimate | State |
|---|---|---|---|
| W330 | Cargo: per-owner cap Q01 to Q04, the four GameTest rows, load-direction settlement, the shared fuel-loader cases | About 0.5 to 1 more day | Running |
| W328G, then the merge | Section 5 | About 0.5 day | Running |
| W331 | The bay's 27 native rows | 1 to 3 hours per fixture family by composition; 0.5 to 2 days per family that needs a new gated fixture; about 2 to 4 days in all | Starts after W325's merge, on the Claude slot |
| W332 | The audit's proposed native cases: both NOT_LOADED triggers and their server-wide effects; pool exhaustion by released and REPAIR receipts across restarts; D6 timing by game-time brackets; a 256-coordinate saturation across device families; a downgrade with the bay and cargo devices placed | About 1.5 to 3 days | Queued after W330 |
| W333 | The bay's 31 GameTest rows | About 2 to 5 days in all | Queued |
| Candidate C2 | One hosted build with all of the above merged, then the whole bay and cargo matrix on it | About 1 to 1.5 days of serialized machine time | After the harness work |

**Before the per-switch report: about 4 to 7 working days**, with up to three workers in parallel, if C2 shows no new
defect. The bay GameTests (W333) and the bay fixtures (W331) set the pace.

**The final campaign** waits for the owner's start confirmation, as decided:

- bay: about 0.5 to 1 day to prepare;
- cargo: a joint V1 and V2 session of about 2 to 3 hours, and a benchmark of about 3 hours plus 1 item hour;
- the reference node: a soak of at least 4 hours;
- R-043's step timing: a measurement plan of about half a day.

The residuals X01 to X03 need owner decisions and their own work:

- X01: a power-loss storage campaign plan, about 1 to 2 days, then a destructive run;
- X02: a diagnostic plan, about 0.5 to 2 days;
- X03: an operator repair procedure, about 1 to 2 days, with operator authorization.

The fuel loader is handled separately and is not in this report.

## 7. The risk checks

### R-042: effects on the whole server

- **Bay receipts not loaded.** For the whole session:
  - every rocket stops, including ordinary flights that never used a bay;
  - every satellite launch and decommission on the server answers DEPLOYMENT_PENDING;
  - assembly and disassembly recovery is fenced;
  - package insertion, fuel and all four cargo kinds are refused.
  - The only logs are one WARN and one INFO per start.
- **What triggers it.** On C1, either a receipt file that exists but did not load, or a file whose presence cannot be
  proved; the second needs no receipt file, so it could hit any world. From C2 on, the second trigger refuses startup
  instead (W328): the server does not start until storage access is fixed. That is also server-wide, and it writes
  nothing.
- **The escrow ledger.** From C2 on, a ledger that is present but not loaded holds every rocket's resources, launches
  and retirement server-wide for the session (W329), instead of the C1 behaviour in section 5.
- **The shared pending pool** (64 per server, 8 per rocket, 16 per owner) serves the fuel loader, all four cargo kinds
  and the bay.
  - Released and REPAIR bay receipts keep their slots across every restart until offline repair, and no online
    release exists.
  - Eight such rockets refuse every new fuel fill, cargo operation and bay deployment on the server.
  - Two fully loaded rockets of one owner refuse that owner everywhere.
- **Register.** R-042 understates all of the above (audit M1 to M3).

### R-043: waiting times

- Almost every wait from D3 to D7 and in recovery has **no upper bound**. The bounded ones are the 200-tick reconnect
  queue, the 200-tick retry spacing and the per-tick step budgets.
- **"About 36 minutes" and "about 1 minute" are Root's arithmetic estimates**, not bounds and not measurements
  (turns × slots × 20 ticks, where 20 ticks is the minimum spacing of forced batches).
  - Without failures, 36 minutes is closer to a lower estimate.
  - Under contention, there is no bound.
- **No run has measured a D step.** C1 cannot observe them (section 3). W332 measures them on a development host with
  game-time brackets; the reference node in the campaign gives the real figures.
- **Register.** R-043 must label the figures as estimates (M6). It must also record that bay recovery can delay cargo
  preparation as well as the reverse (L3).

### R-044: who each risk belongs to

- **Correctly attributed.** R-044's own triggers (an unsupported root, a refused chunk save, a disabled device) belong
  to the fuel loader. The same rules for each cargo device are in R-035's cargo paragraph, and the bay's in R-038.
- **The 256-coordinate denial pool is one per Level**, shared by about 15 device families, including the bay and the
  four cargo devices. Each entry reads as if the pool were its own (M5). A shared-pool statement is needed.
- **The downgrade failure in W176** is world-level. The old JAR rewrote `level.dat` and removed the registry IDs of all
  12 newer blocks, including the bay and the four cargo devices; the cause was its transfer-journal refusal (R-035). It
  is filed under the fuel loader only (M4).
- **Device protection is live with every switch off**, because all five devices are craftable.

**Register corrections.** Root corrects the register text for M1 to M6 and L3 at the merge of W328, W329 and W328F.
That merge also adds the W328 and W329 text, and W328 has already fixed L1, L2 and L5. Correcting a description
accepts no risk; acceptance stays with the owner.
