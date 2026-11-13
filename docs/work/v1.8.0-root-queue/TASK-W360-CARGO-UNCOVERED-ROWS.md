# TASK W360: the remaining uncovered cargo and ledger rows

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w360`, branch `codex/v1.8.0-w360`, at the shared head |
| Decision | Root decision R438 ([OWNER-DECISIONS-20261113](records/OWNER-DECISIONS-20261113.md); QUEUE row W360) |
| Record | `docs/work/v1.8.0-root-queue/records/W360.md` |
| Write scope | Gated release-test fixtures, probes and seams under `src/main` that do nothing without the release-test property; new GameTest methods; the cargo, item and R-042 native runners and oracles under `scripts/`; new cases or families in `scripts/run_v180_integrated_campaign.py`; their tests under `tests/`; the record. No production behaviour, register, ADR, status file, QUEUE, build or workflow change. |

## Why

The owner asked for the rows that candidate C3 left NOT_COVERED to be covered before deciding on any switch
(2026-11-13). [C3-SWITCH-REPORT](records/C3-SWITCH-REPORT.md) lists them for cargo and the shared ledger in sections
4.4, 5.4, 8.4, 9.1 and 10. W358 covers the bay rows in parallel; expect to merge with its campaign-driver changes.

## What to do

1. **Item settlement cuts that do not exist yet, both directions.** The fluid families have before-write cuts and a
   destination store-veto cut (`FS_O1_BEFORE`, `FS_BIND_BEFORE`, `FS_O3_BEFORE`, `FS_O3J_BEFORE`, `FS_O4_BEFORE`,
   `FS_PRUNE_BEFORE`, `FS_VETO_DESTINATION_SAVED`, and their `FS_LOAD_` forms). The item families `cargo-D-S2` and
   `cargo-D-S2-LOAD` have none of them ([W330-DESIGN](records/W330-DESIGN.md), load settlement). Add the item
   counterparts for ITEM_CARGO_UNLOAD and ITEM_CARGO_LOAD, with the same oracle rules as the fluid ones and the item
   settlement oracle's conservation checks. If a counterpart cannot exist for items, explain why in the record.
2. **R-031: a future or invalid ledger root refuses world start.** Add native cases that seed an escrow ledger with a
   future root version and with an invalid root. Check the whole-world pre-start refusal, the logs, and that every
   managed file stays byte-identical. Apply the `level.dat` boundary of Root decision R416, as `r042-U_LEDGER` does.
   Compute the future version from the code's supported version plus one, not as a fixed number: W357's planned
   change moves the ledger to root 3.
3. **The vanilla-dismount row of `r042-L_LEDGER`.** The register says players can still dismount with the vanilla
   control while the ledger is NOT_LOADED. W332 and W343 found that a rider's login under the hold is not seated,
   because the hold vetoes every mount. Find out whether a player can be seated at all during a NOT_LOADED session.
   If one can, build the case. If none can, write the argument in the record, with the code locations, so that Root
   can correct the register's claim.
4. **Presence that becomes unknown after the pre-start check.** For the bay receipts and the ledger, try a gated
   seam, in the style of W332's `ReleaseTestLoadFailure`, that makes the presence check fail after the pre-start
   check deterministically. Then check the `reason=PRESENCE_UNKNOWN` hold natively. If no such seam can be
   deterministic, say why and leave both rows unit-only.
5. **The item devices' hold.** W329's device-hold GameTest uses only a fluid cargo device. Add GameTests for the item
   loader and the item unloader under ledger NOT_LOADED, beside `EscrowLedgerNotLoadedGameTests`.
6. **Smokes.** Run each new native case on a locally built JAR, with the forced stop and both restarts where the case
   has them, under the heavy-JVM lock (WORKER-RULES). A smoke is not candidate evidence. Where practical, show that
   each new oracle rejects a scratch JAR with the protection disabled. Run the new GameTests with their batch.
7. **Production equality.** Show with `git diff --stat` over `src/main`, `src/generated` and the Gradle files that only
   gated release-test classes and GameTests changed.

## Rules

- Do not change a production rule, an existing oracle's expectation or an existing case's selection. Do not delete
  tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Run the targeted unit tests and the Python tests you touched. Keep evidence under
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W360/`, and delete server copies after each run.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives each row's result (PASS in smoke, NOT_COVERED with the reason, or unreachable with the argument),
  the commands and the evidence paths. Its last line, and the last line of your answer, is the path of
  `records/W360.md`.
