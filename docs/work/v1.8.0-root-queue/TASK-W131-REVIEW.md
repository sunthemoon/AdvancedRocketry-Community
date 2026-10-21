# TASK W131-review: independent read-only review of W131 (cargo leaf C-E3, fluid cargo settlement) before merge

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. Claude
wrote W131, so this is a fresh Codex session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w131` at `5f3fcb01` (base `ae37eac8`; tested source `4bec423a`). Your review worktree `D:/GitHub/arce-v180-w131-review` is a detached checkout of `5f3fcb01`; read the diff with `git diff ae37eac8..5f3fcb01` there |
| Task it answers | [TASK-W131](TASK-W131-CARGO-C-E3-SETTLEMENT.md) |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) section 3.3 (normal settlement, the O1 to O4 table, receipts and accounting, reservations), section 4 (disassembly and launch: the latest cargo, exactly once), section 6's C-E row, and the cut mapping's "Neutral survivor normalization and veto" and "Latest-cargo disassembly" rows |
| Models it builds on | [records/W117.md](records/W117.md) (C-E1), [records/W124.md](records/W124.md) (C-E2, with W124F), [records/W99.md](records/W99.md) (C-I) and [records/W100.md](records/W100.md) (C-K) |
| Record | `docs/work/v1.8.0-root-queue/records/W131.md` on the branch (the plan is at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W131/` (`final-gametest/`, `base-planetary/`, `alone-planetary/`, `nc-item4/`, `checks/final-4bec423a/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W131-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **O1 for the cargo device:** W131 says C-E2 already routes the cargo device's observation through its endpoint
   adapter, so item 1 needed only a pinning test. Confirm that no cargo device still falls into the fixture branch of
   `EscrowSettlementObserver` or `EscrowSettlementDisk`, and that the rocket's observation stays `FUEL_VECTOR_V1` with
   the invariant-envelope digest.
2. **Normalization and veto** with cargo, as the "Neutral survivor" row says, for a load and an unload.
3. **O2:** one bounded native decode gives both projections (the cargo composite for prefix classification, the fuel
   vector and invariant for the frozen settlement observation); only the server thread confirms; O2 never forces the
   operation's AFTER state and compares composite with composite, never with a fuel string; both unload survivor proofs
   (rocket source, device destination) and ABANDON's exact surviving and absent endpoints. Can any crash or restart
   between O1 and O4 credit, debit or lose a quantity, or settle a receipt twice?
4. **R39 before normalization and O2:** the fluid check runs before either acts; a hold affects only that operation; the
   one WARN per held operation and the 200-tick recheck keep the log bounded; if any GameTest drives that WARN, it has
   its own exact entry in `scripts/gametest_expected_log.json` (W131 says no log expectation changed).
5. **O3, O3J and O4** keep their order; settlement pruning's cargo-device branch uses the coordinator's ledger identity;
   O4 READY then a governed launch. W131 reports that without its pruning branch a released cargo device crashed the
   server tick with a `NullPointerException` when the escrow hooks are on. Find out whether that path is reachable on
   the shared head today (C-E2 merged, production admission disabled), in production or only in GameTest worlds, and
   rate it; say whether any other endpoint type would still reach it.
6. **The end-to-end cases:** C-I's governed missing and terminal-ARMED authority (the terminal case is checked against a
   ledger value, not written into the live ledger: is that enough to show the behaviour?), cancellation, two successive
   cargo-mutated flights with a mutation by the successor, and exactly-once latest-cargo disassembly on P1-F8. Is the
   synthetic same-dimension governed plan an acceptable stand-in for a cross-dimension flight here?
7. **F1:** named (transfer-guarded) cargo stays held with nothing lost, pinned by a test; W131 adds no variant of the F1
   fix (R48's D4 stays separate).
8. **Nothing weaker:** list every edited pre-existing test or fixture file and say whether any assertion, bound, budget
   or timeout became weaker; the fill path's settlement tests are unchanged; C-K's fuel and floor receipt bytes and
   inspector output stay pinned; `EscrowCoordinator` (576 lines) and `RocketTransferService` (796 lines) did not grow;
   new classes stay under 500 lines; no saved format, schema, hash or dictionary change.
9. **The one failing GameTest:** W131's full run failed only
   `PlanetaryAdmissionGameTests.closedOrbitStationCanDepartButStillRequiresLiveMembership`, also on its base. W122 has
   since fixed that test on the shared branch (merge `94129668`). Confirm from W131's logs that the failure matches
   W122's cause (the departure refused before COUNTDOWN) and that W131 changed nothing in that test's path.
10. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W131's
    base added W129R (documents), W122 (test-only, `gametest/PlanetaryAdmissionGameTests`, `EntitySections`,
    `EarthReturnPadFixture`) and Root's records: preview with `git merge-tree` and say what the merge must keep. W125
    (`codex/v1.8.0-w125`, the F1 fix on the fuel side) and W132 (`codex/v1.8.0-w132` at `4bf9ad87`, item admission and
    reconciliation) are not merged yet; list the files W131 shares with either as a merge risk only.
11. **The record** against the code and the evidence.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W131's
new unit classes, each alone, then the `resourceescrow` and `rocket.cargo` packages. You may add a probe test to check a
finding; keep each probe in the output folder. Before each Gradle run, check that at least 6 GiB of memory is free and
wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no heavy-JVM lock. Delete the
worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W131-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
