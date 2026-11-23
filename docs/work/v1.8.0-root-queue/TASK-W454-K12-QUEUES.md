# TASK W454: package K12, the cargo and queue fixtures on C6

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w454`, branch `codex/v1.8.0-w454` from the shared branch |
| Decision | Root decision R644 (QUEUE row W454) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K12, rows J-1, J-5, J-6 and J-8, plan choices C10, C36, C37 and C39; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI28; checklist [J](records/CAMPAIGN-CHECKLISTS-J.md) rows J05, J08 to J11 and J17; [W325-MATRIX](records/W325-MATRIX.md); [C4-SWITCH-REPORT](records/C4-SWITCH-REPORT.md) and [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md); the [integrated runner](../../../scripts/run_v180_integrated_campaign.py) and the [cargo native benchmark](../../../scripts/run_v180_cargo_native_benchmark.py); K04's [record](records/W434.md) and K15's [record](records/W403.md) with `scripts/campaign_observe/`; QUEUE row W445 (R638); C6's identity in QUEUE row "C6 run" (R581); C6's code under `src/main/` (read only) |
| Inputs | K04's sealed rockets under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K04/` and the C6 campaign's native outputs under `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/` (read only: work on copies and check each parent's `SHA256SUMS.txt` first) |
| Outputs | Outside Git under `.../fixtures/K12/`: each fixture as a source copy with a manifest and `SHA256SUMS.txt`, plus the qualification runs. Tracked: `scripts/campaign_prep/k12_queues.py`, `tests/test_campaign_prep_k12.py`, and the record `docs/work/v1.8.0-root-queue/records/W454.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next, no hold over two hours. Detached launchers start with no console window (see WORKER-RULES). Run only the new tests and the validator (R614). |
| Not allowed | Any change under `src/`, to the cells, to an existing script or sealed fixture, or to a budget or config value. Enabling any production admission switch. Starting any client. Editing NBT, SavedData or region files by hand or by script, and `data merge` or any other NBT-writing command (R638). Running a first-event H cut with a connected player (C39). Treating Windows figures as REF evidence. |

## Root's choices for the open points

1. **Reference queues (J05, J08 to J10, C10).** Build the larger REF queue fixtures that the checklist rows name, from
   K04's rockets and the C6 campaign's families, with the queue sizes read from C6's own bounds; record each queue's
   composition. Comparisons follow C10 (same copy, back to back, baseline twice).
2. **Wait 23 (C36).** An isolated world with no other ticket owner for wait 23; no sweep with other owners is
   compared with the 400-tick expiry. No latency maximum exists: the raw waits are the result.
3. **First-event tick (J11, C37).** Prepare the measurement fixture and the procedure for REF; a Windows run is a
   qualification only. If REF later shows it over budget, Root opens the persistence task R245 requires.
4. **Save and deferral recipes (J17, C39).** Write the fourteen recipes with their award probes. First-event H cuts
   run without a connected player; X cuts use external stops only.
5. **Sampler.** Use K15's raw tick sampler and queue observer; add only what a row needs, with tests.

## What to do

1. Write the driver and the tests (Linux-safe: governance runs them on Linux).
2. Build each fixture on C6 under the lock, read back its state, save, stop, copy it out with a manifest (parents and
   their checksum lists, C6 JAR hash and commit, every command, queue compositions, readbacks, file inventory and
   hashes), then load a copy once more on C6 and record that the readbacks match. Qualify the drivers once with
   short labelled windows.
3. Map each fixture and recipe to J05, J08 to J11 and J17 step by step in the record; list anything NOT_COVERED with
   the reason. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
